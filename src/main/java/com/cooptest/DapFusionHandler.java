package com.cooptest;

import com.cooptest.client.CoopAnimationHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.Vec3;

public class DapFusionHandler {
   public static final long FUSION_G_WINDOW_START = 830L;
   public static final long FUSION_G_WINDOW_END = 2200L;
   private static final long WALK_QTE_WINDOW_STAGE_1 = 450L;
   private static final long WALK_QTE_WINDOW_STAGE_2 = 300L;
   private static final long WALK_QTE_WINDOW_STAGE_3 = 200L;
   public static final long TIMING_BAR_TOTAL_MS = 1800L;
   public static final long TIMING_BAR_TOTAL_MS_CLIENT = 1800L;
   private static final long TIMING_HIT_WINDOW_WALK = 350L;
   private static final long[] TIMING_HIT_WINDOW_FUSION = new long[]{300L, 260L, 220L};
   private static final long TIMING_GRACE_MS = 80L;
   private static final double WALK_START_DISTANCE = 6.0;
   private static final double WALK_STEP_DISTANCE = 1.5;
   private static final double WALK_STOP_DISTANCE = 1.3;
   private static final int SMOOTH_TP_TICKS = 5;
   private static final long[] FUSION_QTE_WINDOWS = new long[]{500L, 450L, 400L, 350L, 300L, 250L, 200L, 175L, 150L, 100L};
   private static final long FUSION_STAGE_GAP_MS = 600L;
   private static final Map<UUID, DapFusionHandler.FusionSession> sessions = new HashMap<>();
   private static final Map<UUID, UUID> fusedPairs = new HashMap<>();
   private static final Map<UUID, Vec3[]> smoothTpTargets = new HashMap<>();
   private static final Map<UUID, Integer> smoothTpProgress = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.playS2C().register(DapFusionHandler.FusionPhasePayload.ID, DapFusionHandler.FusionPhasePayload.CODEC);
      PayloadTypeRegistry.playS2C().register(DapFusionHandler.FusionQTEPayload.ID, DapFusionHandler.FusionQTEPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(DapFusionHandler.FusionBlackScreenPayload.ID, DapFusionHandler.FusionBlackScreenPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(DapFusionHandler.FusionFusedPayload.ID, DapFusionHandler.FusionFusedPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DapFusionHandler.FusionGPressPayload.ID, DapFusionHandler.FusionGPressPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(DapFusionHandler.FusionUnfusePayload.ID, DapFusionHandler.FusionUnfusePayload.CODEC);
   }

   public static void registerClientPayloads() {
      try {
         PayloadTypeRegistry.playS2C().register(DapFusionHandler.FusionPhasePayload.ID, DapFusionHandler.FusionPhasePayload.CODEC);
      } catch (Exception var6) {
      }

      try {
         PayloadTypeRegistry.playS2C().register(DapFusionHandler.FusionQTEPayload.ID, DapFusionHandler.FusionQTEPayload.CODEC);
      } catch (Exception var5) {
      }

      try {
         PayloadTypeRegistry.playS2C().register(DapFusionHandler.FusionBlackScreenPayload.ID, DapFusionHandler.FusionBlackScreenPayload.CODEC);
      } catch (Exception var4) {
      }

      try {
         PayloadTypeRegistry.playS2C().register(DapFusionHandler.FusionFusedPayload.ID, DapFusionHandler.FusionFusedPayload.CODEC);
      } catch (Exception var3) {
      }

      try {
         PayloadTypeRegistry.playC2S().register(DapFusionHandler.FusionGPressPayload.ID, DapFusionHandler.FusionGPressPayload.CODEC);
      } catch (Exception var2) {
      }

      try {
         PayloadTypeRegistry.playC2S().register(DapFusionHandler.FusionUnfusePayload.ID, DapFusionHandler.FusionUnfusePayload.CODEC);
      } catch (Exception var1) {
      }
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(
         DapFusionHandler.FusionGPressPayload.ID, (payload, context) -> context.server().execute(() -> handleGPressFromClient(context.player()))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         DapFusionHandler.FusionUnfusePayload.ID, (payload, context) -> context.server().execute(() -> handleUnfuseRequest(context.player()))
      );
      ServerTickEvents.END_SERVER_TICK.register(DapFusionHandler::tick);
   }

   public static void openFusionWindow(ServerPlayer p1, ServerPlayer p2) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      if (!sessions.containsKey(id1) && !sessions.containsKey(id2)) {
         long now = System.currentTimeMillis();
         DapFusionHandler.FusionSession session = new DapFusionHandler.FusionSession(p1, p2, now);
         sessions.put(id1, session);
         sessions.put(id2, session);
         broadcast(session, new DapFusionHandler.FusionPhasePayload(id1, id2, 0));
      }
   }

   public static void cancelForJCombo(UUID playerId) {
      DapFusionHandler.FusionSession s = sessions.get(playerId);
      if (s != null) {
         if (s.phase == DapFusionHandler.FusionPhase.AWAITING_G) {
            cleanupSession(s);
         }
      }
   }

   public static boolean onQTEButtonPress(ServerPlayer player, String button) {
      DapFusionHandler.FusionSession s = sessions.get(player.getUUID());
      if (s == null) {
         return false;
      } else if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE && s.walkQteOpen) {
         handleWalkQTEPress(s, player.getUUID(), button);
         return true;
      } else if (s.phase == DapFusionHandler.FusionPhase.FUSION_QTE && s.fusionQteOpen) {
         handleFusionQTEPress(s, player.getUUID(), button);
         return true;
      } else {
         return false;
      }
   }

   public static boolean isInFusion(UUID playerId) {
      return sessions.containsKey(playerId);
   }

   public static void cleanup(UUID playerId) {
      DapFusionHandler.FusionSession s = sessions.get(playerId);
      if (s != null) {
         cleanupSession(s);
      }
   }

   public static void handleGPressFromClient(ServerPlayer player) {
      onGPress(player);
   }

   private static void onGPress(ServerPlayer player) {
      DapFusionHandler.FusionSession s = sessions.get(player.getUUID());
      if (s != null && s.phase == DapFusionHandler.FusionPhase.AWAITING_G) {
         long now = System.currentTimeMillis();
         long elapsed = now - s.gWindowOpenTime;
         if (elapsed >= 830L && elapsed <= 2200L) {
            if (player.getUUID().equals(s.p1Id)) {
               s.p1PressedG = true;
               if (s.isSolo()) {
                  s.p2PressedG = true;
               }
            } else {
               s.p2PressedG = true;
            }

            if (s.p1PressedG && s.p2PressedG) {
               startWalkPhase(s);
            }
         } else {
            player.displayClientMessage(Component.literal("§cToo early/late for fusion!"), true);
         }
      }
   }

   private static void startWalkPhase(DapFusionHandler.FusionSession s) {
      s.phase = DapFusionHandler.FusionPhase.WALK_QTE;
      s.walkStage = 0;
      DapSessionManager.removeSessionForPlayer(s.p1Id);
      DapSessionManager.removeSessionForPlayer(s.p2Id);
      PoseNetworking.poseStates.put(s.p1Id, PoseState.NONE);
      PoseNetworking.poseStates.put(s.p2Id, PoseState.NONE);
      Vec3 p1Start;
      Vec3 p2Start;
      if (s.isSolo()) {
         Vec3 base = s.p1Ref.position();
         p1Start = base.add(2.0, 0.0, 0.0);
         p2Start = base.add(-2.0, 0.0, 0.0);
      } else {
         Vec3 mid = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5);
         Vec3 rawDir = s.p1Ref.position().subtract(s.p2Ref.position()).normalize();
         if (rawDir.lengthSqr() < 0.001) {
            rawDir = new Vec3(1.0, 0.0, 0.0);
         }

         p1Start = mid.add(rawDir.scale(3.0));
         p2Start = mid.subtract(rawDir.scale(3.0));
      }

      s.p1WalkPos = p1Start;
      s.p2WalkPos = p2Start;
      facePlayers(s.p1Ref, s.p2Ref, s.p1WalkPos, s.p2WalkPos);
      Vec3 mid = s.p1WalkPos.add(s.p2WalkPos).scale(0.5);
      s.world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y + 1.0, mid.z, 2, 0.0, 0.0, 0.0, 0.0);
      s.world.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y + 1.0, mid.z, 20, 0.5, 0.5, 0.5, 0.3);
      s.world.playSound(null, mid.x, mid.y, mid.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 1.5F);
      freezeBoth(s, true);
      broadcast(s, new DapFusionHandler.FusionPhasePayload(s.p1Id, s.p2Id, 1));
      int auraOrdinal = CoopAnimationHandler.AnimState.AURA_WALK.ordinal();

      try {
         ServerPlayNetworking.send(s.p1Ref, new PoseNetworking.AnimStateSyncPayload(s.p1Id, auraOrdinal));
      } catch (Exception var7) {
      }

      try {
         ServerPlayNetworking.send(s.p2Ref, new PoseNetworking.AnimStateSyncPayload(s.p2Id, auraOrdinal));
      } catch (Exception var6) {
      }

      s.world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5F, 1.8F);
      broadcastServer(s, "§d§l✨ FUSION RITUAL BEGUN! §7Hit the QTE to walk forward!");
      scheduleWalkQTE(s);
   }

   private static void facePlayers(ServerPlayer p1, ServerPlayer p2, Vec3 pos1, Vec3 pos2) {
      double dx = pos2.x - pos1.x;
      double dz = pos2.z - pos1.z;
      float yaw1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
      float yaw2 = yaw1 + 180.0F;
      p1.setYRot(yaw1);
      p1.setYBodyRot(yaw1);
      p1.setYHeadRot(yaw1);
      p1.yRotO = yaw1;
      p1.yBodyRotO = yaw1;
      p1.yHeadRotO = yaw1;
      p2.setYRot(yaw2);
      p2.setYBodyRot(yaw2);
      p2.setYHeadRot(yaw2);
      p2.yRotO = yaw2;
      p2.yBodyRotO = yaw2;
      p2.yHeadRotO = yaw2;
      p1.teleportTo(p1.level(), pos1.x, pos1.y, pos1.z, Set.of(), yaw1, 0.0F, false);
      p2.teleportTo(p2.level(), pos2.x, pos2.y, pos2.z, Set.of(), yaw2, 0.0F, false);
   }

   private static void scheduleWalkQTE(DapFusionHandler.FusionSession s) {
      new Thread(() -> {
         try {
            Thread.sleep(300L);
         } catch (InterruptedException var2) {
         }

         if (s.p1Ref.level().getServer() != null) {
            s.p1Ref.level().getServer().execute(() -> openWalkQTE(s));
         }
      }).start();
   }

   private static void openWalkQTE(DapFusionHandler.FusionSession s) {
      if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE) {
         long now = System.currentTimeMillis();
         s.walkQteOpen = true;
         s.p1WalkPressed = false;
         s.p2WalkPressed = false;
         s.walkQteOpenTime = now;
         s.walkExpectedButton = s.randomButton();
         boolean isTimingBar = s.walkStage == 1 && !CoopMovesConfig.get().easyFusionTest;
         s.walkIsTimingBar = isTimingBar;
         long windowStartMs;
         long windowEndMs;
         int type;
         if (isTimingBar) {
            windowStartMs = s.randomGreenZoneStart(350L);
            windowEndMs = windowStartMs + 350L;
            type = 1;
         } else {
            windowStartMs = 0L;
            windowEndMs = s.walkQteWindow();
            type = 0;
         }

         s.walkHitWindowStart = now + windowStartMs - 80L;
         s.walkHitWindowEnd = now + windowEndMs + 80L;
         sendFusionQTE(s.p1Ref, s.walkExpectedButton, s.walkStage + 1, windowStartMs, windowEndMs, true, type);
         sendFusionQTE(s.p2Ref, s.walkExpectedButton, s.walkStage + 1, windowStartMs, windowEndMs, true, type);
      }
   }

   private static void handleWalkQTEPress(DapFusionHandler.FusionSession s, UUID presserId, String button) {
      if (s.walkQteOpen) {
         if (!button.equals(s.walkExpectedButton)) {
            failWalkPhase(s, "§cWrong button! Fusion cancelled!");
         } else {
            if (s.walkIsTimingBar) {
               long now = System.currentTimeMillis();
               if (now < s.walkHitWindowStart) {
                  failWalkPhase(s, "§c✗ Too early! Fusion cancelled!");
                  return;
               }

               if (now > s.walkHitWindowEnd) {
                  failWalkPhase(s, "§c✗ Too late! Fusion cancelled!");
                  return;
               }
            }

            if (presserId.equals(s.p1Id)) {
               s.p1WalkPressed = true;
               if (s.isSolo()) {
                  s.p2WalkPressed = true;
               }
            } else {
               s.p2WalkPressed = true;
            }

            if (s.p1WalkPressed && s.p2WalkPressed) {
               s.walkQteOpen = false;
               closeFusionQTE(s.p1Ref, s.walkExpectedButton, s.walkStage + 1);
               closeFusionQTE(s.p2Ref, s.walkExpectedButton, s.walkStage + 1);
               walkSuccess(s);
            }
         }
      }
   }

   private static void walkSuccess(DapFusionHandler.FusionSession s) {
      s.walkStage++;
      Vec3 mid = s.p1WalkPos.add(s.p2WalkPos).scale(0.5);
      Vec3 dirP1 = mid.subtract(s.p1WalkPos);
      Vec3 dirP2 = mid.subtract(s.p2WalkPos);
      if (dirP1.lengthSqr() < 0.001) {
         dirP1 = new Vec3(-1.0, 0.0, 0.0);
      }

      if (dirP2.lengthSqr() < 0.001) {
         dirP2 = new Vec3(1.0, 0.0, 0.0);
      }

      dirP1 = dirP1.normalize();
      dirP2 = dirP2.normalize();
      double currentDist = s.p1WalkPos.distanceTo(s.p2WalkPos);
      double step = Math.min(1.5, Math.max(0.1, (currentDist - 1.3) / 2.0));
      Vec3 newP1 = s.p1WalkPos.add(dirP1.scale(step));
      Vec3 newP2 = s.p2WalkPos.add(dirP2.scale(step));
      smoothTpTargets.put(s.p1Id, new Vec3[]{s.p1WalkPos, newP1});
      smoothTpTargets.put(s.p2Id, new Vec3[]{s.p2WalkPos, newP2});
      smoothTpProgress.put(s.p1Id, 0);
      smoothTpProgress.put(s.p2Id, 0);
      s.p1WalkPos = newP1;
      s.p2WalkPos = newP2;
      facePlayers(s.p1Ref, s.p2Ref, s.p1WalkPos, s.p2WalkPos);
      spawnWalkAura(s, s.walkStage);
      if (s.walkStage >= 3) {
         PoseNetworking.broadcastAnimState(s.p1Ref, CoopAnimationHandler.AnimState.FUSION_START_P1.ordinal());
         PoseNetworking.broadcastAnimState(s.p2Ref, CoopAnimationHandler.AnimState.FUSION_START_P2.ordinal());
         new Thread(() -> {
            try {
               Thread.sleep(670L);
            } catch (InterruptedException var2x) {
            }

            if (s.p1Ref.level().getServer() != null) {
               s.p1Ref.level().getServer().execute(() -> triggerMeetupExplosion(s));
            }
         }).start();
      } else {
         scheduleWalkQTE(s);
      }
   }

   private static void failWalkPhase(DapFusionHandler.FusionSession s, String reason) {
      s.phase = DapFusionHandler.FusionPhase.FAILED;
      s.walkQteOpen = false;
      closeFusionQTE(s.p1Ref, "", 0);
      closeFusionQTE(s.p2Ref, "", 0);
      PoseNetworking.broadcastAnimState(s.p1Ref, 0);
      PoseNetworking.broadcastAnimState(s.p2Ref, 0);
      freezeBoth(s, false);
      Vec3 mid = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5);
      Vec3 away1 = s.p1Ref.position().subtract(mid).normalize().scale(4.0).add(0.0, 0.8, 0.0);
      Vec3 away2 = s.p2Ref.position().subtract(mid).normalize().scale(4.0).add(0.0, 0.8, 0.0);
      s.p1Ref.push(away1.x, away1.y, away1.z);
      s.p2Ref.push(away2.x, away2.y, away2.z);
      s.p1Ref.hurtMarked = true;
      s.p2Ref.hurtMarked = true;
      broadcast(s, new DapFusionHandler.FusionPhasePayload(s.p1Id, s.p2Id, 99));
      s.p1Ref.displayClientMessage(Component.literal(reason), true);
      s.p2Ref.displayClientMessage(Component.literal(reason), true);
      cleanupSession(s);
   }

   private static void triggerMeetupExplosion(DapFusionHandler.FusionSession s) {
      if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE) {
         PoseNetworking.broadcastAnimState(s.p1Ref, CoopAnimationHandler.AnimState.FUSION_HIT_P1.ordinal());
         PoseNetworking.broadcastAnimState(s.p2Ref, CoopAnimationHandler.AnimState.FUSION_HIT_P2.ordinal());
         new Thread(() -> {
            try {
               Thread.sleep(350L);
            } catch (InterruptedException var2) {
            }

            if (s.p1Ref.level().getServer() != null) {
               s.p1Ref.level().getServer().execute(() -> {
                  if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE) {
                     Vec3 mid = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5).add(0.0, 1.0, 0.0);
                     s.world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, 60, 0.8, 0.8, 0.8, 0.3);
                     s.world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), mid.x, mid.y, mid.z, 5, 0.0, 0.0, 0.0, 0.0);
                     s.world.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 30, 0.4, 0.4, 0.4, 0.3);
                     s.world.playSound(null, mid.x, mid.y, mid.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 3.0F, 0.8F);
                     s.world.playSound(null, mid.x, mid.y, mid.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
                  }
               });
            }
         }).start();
         new Thread(() -> {
            try {
               Thread.sleep(620L);
            } catch (InterruptedException var2) {
            }

            if (s.p1Ref.level().getServer() != null) {
               s.p1Ref.level().getServer().execute(() -> {
                  if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE) {
                     broadcastServer(s, "§6§l⚡ THE FUSION BEGINS! ⚡ §7Complete the 10-stage QTE!");
                     s.phase = DapFusionHandler.FusionPhase.FUSION_QTE;
                     s.fusionStage = 0;
                     s.lastFusionStageEnd = System.currentTimeMillis();
                     broadcast(s, new DapFusionHandler.FusionPhasePayload(s.p1Id, s.p2Id, 2));
                     PoseNetworking.broadcastAnimState(s.p1Ref, CoopAnimationHandler.AnimState.FUSION_IDLE_P1.ordinal());
                     PoseNetworking.broadcastAnimState(s.p2Ref, CoopAnimationHandler.AnimState.FUSION_IDLE_P2.ordinal());
                     openNextFusionQTE(s);
                  }
               });
            }
         }).start();
      }
   }

   private static void openNextFusionQTE(DapFusionHandler.FusionSession s) {
      if (s.phase == DapFusionHandler.FusionPhase.FUSION_QTE) {
         if (s.fusionStage >= 10) {
            triggerFusion(s);
         } else {
            long now = System.currentTimeMillis();
            s.fusionQteOpen = true;
            s.p1FusionPressed = false;
            s.p2FusionPressed = false;
            s.fusionQteOpenTime = now;
            s.fusionExpectedButton = s.randomButton();
            boolean isTimingBar = s.fusionStage == 3 || s.fusionStage == 6 || s.fusionStage == 9;
            s.fusionIsTimingBar = isTimingBar;
            long windowStartMs;
            long windowEndMs;
            int type;
            if (isTimingBar) {
               int timingIdx = s.fusionStage == 3 ? 0 : (s.fusionStage == 6 ? 1 : 2);
               long hitWindow = TIMING_HIT_WINDOW_FUSION[timingIdx];
               windowStartMs = s.randomGreenZoneStart(hitWindow);
               windowEndMs = windowStartMs + hitWindow;
               type = 1;
            } else {
               windowStartMs = 0L;
               windowEndMs = s.fusionQteWindow();
               type = 0;
            }

            s.fusionHitWindowStart = now + windowStartMs - 80L;
            s.fusionHitWindowEnd = now + windowEndMs + 80L;
            sendFusionQTE(s.p1Ref, s.fusionExpectedButton, s.fusionStage + 1, windowStartMs, windowEndMs, true, type);
            sendFusionQTE(s.p2Ref, s.fusionExpectedButton, s.fusionStage + 1, windowStartMs, windowEndMs, true, type);
            spawnFusionAura(s, s.fusionStage);
            Vec3 mid2 = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5);
            if (s.fusionStage == 7) {
               s.world.playSound(null, mid2.x, mid2.y, mid2.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 1.5F, 1.4F);
            } else if (s.fusionStage == 8) {
               s.world.playSound(null, mid2.x, mid2.y, mid2.z, ModSounds.FIRE_IMPACT, SoundSource.PLAYERS, 1.5F, 1.2F);
            } else if (s.fusionStage == 9) {
               s.world.playSound(null, mid2.x, mid2.y, mid2.z, ModSounds.GALACTIC_DAP, SoundSource.PLAYERS, 2.0F, 0.9F);
            }
         }
      }
   }

   private static void handleFusionQTEPress(DapFusionHandler.FusionSession s, UUID presserId, String button) {
      if (s.fusionQteOpen) {
         if (!button.equals(s.fusionExpectedButton)) {
            failFusion(s, "§c✗ Wrong button! FUSION FAILED!");
         } else {
            if (s.fusionIsTimingBar) {
               long now = System.currentTimeMillis();
               if (now < s.fusionHitWindowStart) {
                  failFusion(s, "§c✗ Too early! FUSION FAILED!");
                  return;
               }

               if (now > s.fusionHitWindowEnd) {
                  failFusion(s, "§c✗ Too late! FUSION FAILED!");
                  return;
               }
            }

            if (presserId.equals(s.p1Id)) {
               s.p1FusionPressed = true;
               if (s.isSolo()) {
                  s.p2FusionPressed = true;
               }
            } else {
               s.p2FusionPressed = true;
            }

            if (s.p1FusionPressed && s.p2FusionPressed) {
               s.fusionQteOpen = false;
               closeFusionQTE(s.p1Ref, s.fusionExpectedButton, s.fusionStage + 1);
               closeFusionQTE(s.p2Ref, s.fusionExpectedButton, s.fusionStage + 1);
               s.fusionStage++;
               s.lastFusionStageEnd = System.currentTimeMillis();
               String progress = s.fusionStage >= 10 ? "§6§l★ 10/10 ★" : "§a" + s.fusionStage + "/10 §7— §6Keep going!";
               s.p1Ref.displayClientMessage(Component.literal(progress), true);
               if (!s.isSolo()) {
                  s.p2Ref.displayClientMessage(Component.literal(progress), true);
               }

               if (s.fusionStage >= 10) {
                  triggerFusion(s);
               } else {
                  ServerPlayer p1 = s.p1Ref;
                  new Thread(() -> {
                     try {
                        Thread.sleep(600L);
                     } catch (InterruptedException var3x) {
                     }

                     if (p1.level().getServer() != null) {
                        p1.level().getServer().execute(() -> openNextFusionQTE(s));
                     }
                  }).start();
               }
            }
         }
      }
   }

   private static void failFusion(DapFusionHandler.FusionSession s, String reason) {
      s.phase = DapFusionHandler.FusionPhase.FAILED;
      s.fusionQteOpen = false;
      closeFusionQTE(s.p1Ref, "", 0);
      closeFusionQTE(s.p2Ref, "", 0);
      PoseNetworking.broadcastAnimState(s.p1Ref, 0);
      PoseNetworking.broadcastAnimState(s.p2Ref, 0);
      freezeBoth(s, false);
      Vec3 mid = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5);
      Vec3 away1 = s.p1Ref.position().subtract(mid).normalize().scale(4.0).add(0.0, 1.0, 0.0);
      Vec3 away2 = s.p2Ref.position().subtract(mid).normalize().scale(4.0).add(0.0, 1.0, 0.0);
      s.p1Ref.push(away1.x, away1.y, away1.z);
      s.p2Ref.push(away2.x, away2.y, away2.z);
      s.p1Ref.hurtMarked = true;
      s.p2Ref.hurtMarked = true;
      broadcast(s, new DapFusionHandler.FusionPhasePayload(s.p1Id, s.p2Id, 99));
      s.p1Ref.displayClientMessage(Component.literal(reason), false);
      s.p2Ref.displayClientMessage(Component.literal(reason), false);

      for (ServerPlayer p : s.p1Ref.level().getServer().getPlayerList().getPlayers()) {
         p.displayClientMessage(
            Component.literal("§c✗ " + s.p1Ref.getName().getString() + " §7and §c" + s.p2Ref.getName().getString() + " §7failed the fusion!"), false
         );
      }

      cleanupSession(s);
   }

   private static void triggerFusion(DapFusionHandler.FusionSession s) {
      s.phase = DapFusionHandler.FusionPhase.FUSED;
      PoseNetworking.broadcastAnimState(s.p1Ref, 0);
      PoseNetworking.broadcastAnimState(s.p2Ref, 0);
      freezeBoth(s, false);
      MobEffectInstance invuln1 = new MobEffectInstance(MobEffects.RESISTANCE, 300, 255, false, false);
      MobEffectInstance invuln2 = new MobEffectInstance(MobEffects.RESISTANCE, 300, 255, false, false);
      s.p1Ref.addEffect(invuln1);
      s.p2Ref.addEffect(invuln2);
      s.p1Ref.setInvulnerable(true);
      s.p2Ref.setInvulnerable(true);
      fusedPairs.put(s.p1Id, s.p2Id);
      fusedPairs.put(s.p2Id, s.p1Id);

      try {
         ServerPlayNetworking.send(s.p1Ref, new DapFusionHandler.FusionFusedPayload(true));
      } catch (Exception var7) {
      }

      try {
         ServerPlayNetworking.send(s.p2Ref, new DapFusionHandler.FusionFusedPayload(true));
      } catch (Exception var6) {
      }

      try {
         ServerPlayNetworking.send(s.p1Ref, new DapFusionHandler.FusionBlackScreenPayload(true));
      } catch (Exception var5) {
      }

      try {
         ServerPlayNetworking.send(s.p2Ref, new DapFusionHandler.FusionBlackScreenPayload(true));
      } catch (Exception var4) {
      }

      broadcast(s, new DapFusionHandler.FusionPhasePayload(s.p1Id, s.p2Id, 3));
      Vec3 mid = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5);
      new Thread(
            () -> {
               try {
                  s.p1Ref.level().getServer().execute(() -> {
                     if (!CoopMovesConfig.get().noGriefMode) {
                        float power = 10.0F;

                        for (int dx = -8; dx <= 8; dx += 8) {
                           for (int dz = -8; dz <= 8; dz += 8) {
                              s.world.explode(null, mid.x + dx, mid.y, mid.z + dz, power, true, ExplosionInteraction.MOB);
                           }
                        }
                     } else {
                        s.world.explode(null, mid.x, mid.y, mid.z, 10.0F, false, ExplosionInteraction.MOB);
                     }

                     s.p1Ref.setDeltaMovement(Vec3.ZERO);
                     s.p2Ref.setDeltaMovement(Vec3.ZERO);
                     s.p1Ref.hurtMarked = true;
                     s.p2Ref.hurtMarked = true;
                  });

                  for (int i = 0; i < 10; i++) {
                     Thread.sleep(500L);
                     int burst = i;
                     if (s.p1Ref.level().getServer() == null) {
                        return;
                     }

                     s.p1Ref
                        .level()
                        .getServer()
                        .execute(
                           () -> {
                              float spread = 3.0F + burst * 1.5F;
                              int count = 60 + burst * 20;

                              for (int p = 0; p < 5; p++) {
                                 double ox = (Math.random() - 0.5) * spread * 2.0;
                                 double oz = (Math.random() - 0.5) * spread * 2.0;
                                 double oy = Math.random() * 4.0;
                                 s.world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x + ox, mid.y + oy, mid.z + oz, 1, 0.0, 0.0, 0.0, 0.0);
                              }

                              s.world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y + 2.0, mid.z, count, spread, spread, spread, 0.5);
                              s.world
                                 .sendParticles(
                                    ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y + 2.0, mid.z, count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.6
                                 );
                              s.world
                                 .sendParticles(
                                    PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
                                    mid.x,
                                    mid.y + 1.0,
                                    mid.z,
                                    count / 3,
                                    spread,
                                    spread,
                                    spread,
                                    0.3
                                 );
                              s.world.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y + 1.0, mid.z, count / 2, spread, spread, spread, 0.4);
                              s.world
                                 .playSound(
                                    null,
                                    mid.x,
                                    mid.y,
                                    mid.z,
                                    (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(),
                                    SoundSource.PLAYERS,
                                    2.0F,
                                    0.4F + (float)(Math.random() * 0.4F)
                                 );
                              if (burst % 3 == 0) {
                                 s.world.playSound(null, mid.x, mid.y, mid.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 2.5F, 0.6F);
                              }
                           }
                        );
                  }
               } catch (InterruptedException var4x) {
               }

               if (s.p1Ref.level().getServer() != null) {
                  s.p1Ref
                     .level()
                     .getServer()
                     .execute(
                        () -> {
                           s.world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), mid.x, mid.y + 1.0, mid.z, 20, 0.0, 0.0, 0.0, 0.0);
                           s.world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y + 1.0, mid.z, 200, 4.0, 4.0, 4.0, 0.6);
                           s.world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y + 1.0, mid.z, 8, 3.0, 3.0, 3.0, 0.0);
                           s.world.playSound(null, mid.x, mid.y, mid.z, ModSounds.GALACTIC_DAP, SoundSource.PLAYERS, 4.0F, 0.8F);
                           s.world.playSound(null, mid.x, mid.y, mid.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 3.0F, 0.5F);
                           s.p1Ref.setInvulnerable(false);
                           s.p2Ref.setInvulnerable(false);

                           try {
                              ServerPlayNetworking.send(s.p1Ref, new DapFusionHandler.FusionBlackScreenPayload(false));
                           } catch (Exception var6x) {
                           }

                           try {
                              ServerPlayNetworking.send(s.p2Ref, new DapFusionHandler.FusionBlackScreenPayload(false));
                           } catch (Exception var5x) {
                           }

                           try {
                              broadcast(s, new DapFusionHandler.FusionPhasePayload(s.p1Id, s.p2Id, 4));
                           } catch (Exception var4xx) {
                           }

                           for (ServerPlayer p : s.p1Ref.level().getServer().getPlayerList().getPlayers()) {
                              p.displayClientMessage(
                                 Component.literal(
                                    "§c§l☄ "
                                       + s.p1Ref.getName().getString()
                                       + " §eand §c"
                                       + s.p2Ref.getName().getString()
                                       + " §c§lUNLOCKED METEOR STRIKE! §7Press G to fire!"
                                 ),
                                 false
                              );
                           }

                           ServerPlayer freshP1 = s.p1Ref.level().getServer().getPlayerList().getPlayer(s.p1Id);
                           ServerPlayer freshP2 = s.p1Ref.level().getServer().getPlayerList().getPlayer(s.p2Id);
                           if (freshP1 != null && freshP2 != null) {
                              MeteorStrikeHandler.grantAbility(freshP1, freshP2);
                           }

                           silentCleanup(s);
                        }
                     );
               }
            }
         )
         .start();
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      Set<DapFusionHandler.FusionSession> processed = new HashSet<>();

      for (DapFusionHandler.FusionSession s : new ArrayList<>(sessions.values())) {
         if (!processed.contains(s)) {
            processed.add(s);
            s.p1Ref = server.getPlayerList().getPlayer(s.p1Id);
            s.p2Ref = server.getPlayerList().getPlayer(s.p2Id);
            if (s.p1Ref != null && s.p2Ref != null) {
               tickSmoothTP(s, server);
               if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE) {
                  faceEachOther(s.p1Ref, s.p2Ref);
               }

               if ((s.phase == DapFusionHandler.FusionPhase.WALK_QTE || s.phase == DapFusionHandler.FusionPhase.FUSION_QTE) && server.getTickCount() % 7 == 0) {
                  sendSwingToOthers(server, s.p1Ref);
                  sendSwingToOthers(server, s.p2Ref);
               }

               if (s.phase == DapFusionHandler.FusionPhase.AWAITING_G && now - s.gWindowOpenTime > 2700L) {
                  cleanupSession(s);
               }

               if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE && s.walkQteOpen && now - s.walkQteOpenTime > s.walkQteWindow() + 200L) {
                  failWalkPhase(s, "§c✗ Time's up! Fusion cancelled!");
               }

               if (s.phase == DapFusionHandler.FusionPhase.FUSION_QTE && s.fusionQteOpen) {
                  long timeout = s.fusionIsTimingBar ? 2300L : s.fusionQteWindow() + 200L;
                  if (now - s.fusionQteOpenTime > timeout) {
                     failFusion(s, "§c✗ Too slow! FUSION FAILED!");
                  }
               }

               if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE && s.walkQteOpen) {
                  boolean oneMissed = s.p1WalkPressed != s.p2WalkPressed;
                  if (oneMissed && now - s.walkQteOpenTime > s.walkQteWindow()) {
                     String missName = !s.p1WalkPressed ? s.p1Ref.getName().getString() : s.p2Ref.getName().getString();
                     failWalkPhase(s, "§c✗ " + missName + " missed! Fusion cancelled!");
                  }
               }

               if (s.phase == DapFusionHandler.FusionPhase.FUSION_QTE && s.fusionQteOpen) {
                  boolean oneMissed = s.p1FusionPressed != s.p2FusionPressed;
                  if (oneMissed && now - s.fusionQteOpenTime > s.fusionQteWindow()) {
                     String missName = !s.p1FusionPressed ? s.p1Ref.getName().getString() : s.p2Ref.getName().getString();
                     failFusion(s, "§c✗ " + missName + " missed! FUSION FAILED!");
                  }
               }
            } else {
               cleanupSession(s);
            }
         }
      }
   }

   private static void tickSmoothTP(DapFusionHandler.FusionSession s, MinecraftServer server) {
      tickPlayerSmoothTP(s.p1Id, server);
      tickPlayerSmoothTP(s.p2Id, server);
   }

   private static void tickPlayerSmoothTP(UUID id, MinecraftServer server) {
      Vec3[] targets = smoothTpTargets.get(id);
      Integer progress = smoothTpProgress.get(id);
      if (targets != null && progress != null) {
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player == null) {
            smoothTpTargets.remove(id);
            smoothTpProgress.remove(id);
         } else {
            int tick = progress + 1;
            float t = tick / 5.0F;
            t = Math.min(1.0F, t);
            Vec3 start = targets[0];
            Vec3 end = targets[1];
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;
            player.teleportTo(player.level(), x, y, z, Set.of(), player.getYRot(), player.getXRot(), false);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            if (tick >= 5) {
               smoothTpTargets.remove(id);
               smoothTpProgress.remove(id);
            } else {
               smoothTpProgress.put(id, tick);
            }
         }
      }
   }

   private static void freezeBoth(DapFusionHandler.FusionSession s, boolean freeze) {
      ServerPlayNetworking.send(s.p1Ref, new ChargedDapHandler.PerfectDapFreezePayload(freeze));
      ServerPlayNetworking.send(s.p2Ref, new ChargedDapHandler.PerfectDapFreezePayload(freeze));
   }

   private static void faceEachOther(ServerPlayer p1, ServerPlayer p2) {
      Vec3 pos1 = p1.position();
      Vec3 pos2 = p2.position();
      double dx = pos2.x - pos1.x;
      double dz = pos2.z - pos1.z;
      if (!(dx * dx + dz * dz < 0.001)) {
         float yaw1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
         float yaw2 = yaw1 + 180.0F;
         p1.setYRot(yaw1);
         p1.setYBodyRot(yaw1);
         p1.setYHeadRot(yaw1);
         p1.yRotO = yaw1;
         p1.yBodyRotO = yaw1;
         p1.yHeadRotO = yaw1;
         p2.setYRot(yaw2);
         p2.setYBodyRot(yaw2);
         p2.setYHeadRot(yaw2);
         p2.yRotO = yaw2;
         p2.yBodyRotO = yaw2;
         p2.yHeadRotO = yaw2;
      }
   }

   private static void sendSwingToOthers(MinecraftServer server, ServerPlayer player) {
      ClientboundAnimatePacket swingPacket = new ClientboundAnimatePacket(player, 0);

      for (ServerPlayer other : server.getPlayerList().getPlayers()) {
         if (!other.getUUID().equals(player.getUUID())) {
            other.connection.send(swingPacket);
         }
      }

      player.setYBodyRot(player.getYHeadRot());
      player.yBodyRotO = player.getYHeadRot();
   }

   private static void snapBodyToHead(ServerPlayer player) {
      float headYaw = player.getYHeadRot();
      player.setYBodyRot(headYaw);
      player.yBodyRotO = headYaw;
      player.setYRot(headYaw);
      player.yRotO = headYaw;
   }

   private static void spawnWalkAura(DapFusionHandler.FusionSession s, int stage) {
      Vec3 mid = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5).add(0.0, 1.0, 0.0);
      switch (stage) {
         case 1:
            s.world.sendParticles(ParticleTypes.FLAME, mid.x, mid.y, mid.z, 20, 0.5, 0.5, 0.5, 0.05);
            s.world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.5F);
            break;
         case 2:
            s.world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mid.x, mid.y, mid.z, 30, 0.5, 0.5, 0.5, 0.1);
            s.world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 1.2F);
            break;
         case 3:
            s.world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, 40, 0.6, 0.6, 0.6, 0.2);
            s.world.playSound(null, mid.x, mid.y, mid.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 1.5F, 1.3F);
      }
   }

   private static void spawnFusionAura(DapFusionHandler.FusionSession s, int stage) {
      Vec3 mid = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5).add(0.0, 1.0, 0.0);
      int count = 5 + stage * 3;
      float intensity = 0.1F + stage * 0.05F;
      s.world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, count, intensity, intensity, intensity, 0.1 + stage * 0.02);
      s.world.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, count / 2, intensity, intensity, intensity, 0.2);
      if (stage >= 7) {
         s.world
            .sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), mid.x, mid.y, mid.z, count, intensity, intensity, intensity, 0.15);
      }
   }

   private static void sendFusionQTE(ServerPlayer player, String button, int stage, long windowStartMs, long windowEndMs, boolean open, int type) {
      if (player != null) {
         try {
            ServerPlayNetworking.send(player, new DapFusionHandler.FusionQTEPayload(player.getUUID(), button, stage, windowStartMs, windowEndMs, open, type));
         } catch (Exception var10) {
         }
      }
   }

   private static void closeFusionQTE(ServerPlayer player, String button, int stage) {
      sendFusionQTE(player, button, stage, 0L, 0L, false, 0);
   }

   private static void broadcast(DapFusionHandler.FusionSession s, CustomPacketPayload payload) {
      for (ServerPlayer p : s.p1Ref.level().getServer().getPlayerList().getPlayers()) {
         try {
            ServerPlayNetworking.send(p, payload);
         } catch (Exception var5) {
         }
      }
   }

   private static void broadcastServer(DapFusionHandler.FusionSession s, String msg) {
      for (ServerPlayer p : s.p1Ref.level().getServer().getPlayerList().getPlayers()) {
         p.displayClientMessage(Component.literal(msg), false);
      }
   }

   public static void autoPressBothCorrect(ServerPlayer player) {
      DapFusionHandler.FusionSession s = sessions.get(player.getUUID());
      if (s == null) {
         player.displayClientMessage(Component.literal("§cNo active fusion session."), true);
      } else {
         if (s.phase == DapFusionHandler.FusionPhase.WALK_QTE && s.walkQteOpen) {
            handleWalkQTEPress(s, s.p1Id, s.walkExpectedButton);
            if (s.walkQteOpen) {
               handleWalkQTEPress(s, s.p2Id, s.walkExpectedButton);
            }
         } else if (s.phase == DapFusionHandler.FusionPhase.FUSION_QTE && s.fusionQteOpen) {
            handleFusionQTEPress(s, s.p1Id, s.fusionExpectedButton);
            if (s.fusionQteOpen) {
               handleFusionQTEPress(s, s.p2Id, s.fusionExpectedButton);
            }
         } else if (s.phase == DapFusionHandler.FusionPhase.AWAITING_G) {
            handleGPressFromClient(player);
            handleGPressFromClient(player);
         } else {
            player.displayClientMessage(Component.literal("§cNo QTE window currently open. Phase: " + s.phase), true);
         }
      }
   }

   public static void debugSkipToFusionQTE(ServerPlayer player) {
      cleanup(player.getUUID());
      DapFusionHandler.FusionSession s = new DapFusionHandler.FusionSession(player, player, System.currentTimeMillis());
      sessions.put(player.getUUID(), s);
      s.p1WalkPos = player.position().add(2.0, 0.0, 0.0);
      s.p2WalkPos = player.position().add(-2.0, 0.0, 0.0);
      freezeBoth(s, true);
      broadcast(s, new DapFusionHandler.FusionPhasePayload(s.p1Id, s.p2Id, 2));
      s.phase = DapFusionHandler.FusionPhase.FUSION_QTE;
      s.fusionStage = 0;
      s.lastFusionStageEnd = System.currentTimeMillis();

      try {
         ServerPlayNetworking.send(player, new PoseNetworking.AnimStateSyncPayload(player.getUUID(), CoopAnimationHandler.AnimState.FUSION_IDLE_P1.ordinal()));
      } catch (Exception var3) {
      }

      openNextFusionQTE(s);
   }

   public static String getDebugStatus(UUID playerId) {
      DapFusionHandler.FusionSession s = sessions.get(playerId);
      if (s == null) {
         return "No active fusion session.";
      }

      String extra = switch (s.phase) {
         case WALK_QTE -> " | Walk stage " + s.walkStage + "/3 | QTE open=" + s.walkQteOpen + (s.walkQteOpen ? " button=" + s.walkExpectedButton : "");
         case FUSION_QTE -> " | Fusion stage "
            + s.fusionStage
            + "/10 | QTE open="
            + s.fusionQteOpen
            + (s.fusionQteOpen ? " button=" + s.fusionExpectedButton : "");
         default -> "";
      };
      return "Phase=" + s.phase + extra;
   }

   private static void handleUnfuseRequest(ServerPlayer player) {
      UUID partnerId = fusedPairs.get(player.getUUID());
      if (partnerId != null) {
         ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
         defuse(player, partner);
      }
   }

   public static void defuse(ServerPlayer p1, ServerPlayer p2) {
      if (p1 != null) {
         UUID id1 = p1.getUUID();
         UUID id2 = p2 != null ? p2.getUUID() : id1;
         fusedPairs.remove(id1);
         fusedPairs.remove(id2);
         p1.setInvulnerable(false);
         if (p2 != null) {
            p2.setInvulnerable(false);
         }

         Vec3 mid = p2 != null ? p1.position().add(p2.position()).scale(0.5) : p1.position();
         Vec3 away1 = p1.position().subtract(mid).normalize();
         if (away1.lengthSqr() < 0.001) {
            away1 = new Vec3(1.0, 0.0, 0.0);
         }

         away1 = away1.scale(2.5).add(0.0, 0.6, 0.0);
         p1.push(away1.x, away1.y, away1.z);
         p1.hurtMarked = true;
         if (p2 != null) {
            Vec3 away2 = p2.position().subtract(mid).normalize();
            if (away2.lengthSqr() < 0.001) {
               away2 = new Vec3(-1.0, 0.0, 0.0);
            }

            away2 = away2.scale(2.5).add(0.0, 0.6, 0.0);
            p2.push(away2.x, away2.y, away2.z);
            p2.hurtMarked = true;
         }

         p1.level().playSound(null, mid.x, mid.y, mid.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 1.5F);
         p1.level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y + 1.0, mid.z, 2, 0.5, 0.5, 0.5, 0.0);

         try {
            ServerPlayNetworking.send(p1, new DapFusionHandler.FusionFusedPayload(false));
         } catch (Exception var11) {
         }

         if (p2 != null) {
            try {
               ServerPlayNetworking.send(p2, new DapFusionHandler.FusionFusedPayload(false));
            } catch (Exception var10) {
            }
         }

         p1.displayClientMessage(Component.literal("§7Fusion dissolved."), true);
         if (p2 != null) {
            p2.displayClientMessage(Component.literal("§7Fusion dissolved."), true);
         }

         String name1 = p1.getName().getString();
         String name2 = p2 != null ? p2.getName().getString() : name1;

         for (ServerPlayer p : p1.level().getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal("§7" + name1 + " and " + name2 + " have defused."), false);
         }
      }
   }

   public static void cleanupFused(UUID playerId) {
      UUID partnerId = fusedPairs.remove(playerId);
      if (partnerId != null) {
         fusedPairs.remove(partnerId);
      }
   }

   private static void cleanupSession(DapFusionHandler.FusionSession s) {
      if (s.p1Ref != null && s.p2Ref != null) {
         try {
            broadcast(s, new DapFusionHandler.FusionPhasePayload(s.p1Id, s.p2Id, 99));
         } catch (Exception var2) {
         }
      }

      removeSessionData(s);
   }

   private static void silentCleanup(DapFusionHandler.FusionSession s) {
      removeSessionData(s);
   }

   private static void removeSessionData(DapFusionHandler.FusionSession s) {
      sessions.remove(s.p1Id);
      sessions.remove(s.p2Id);
      smoothTpTargets.remove(s.p1Id);
      smoothTpTargets.remove(s.p2Id);
      smoothTpProgress.remove(s.p1Id);
      smoothTpProgress.remove(s.p2Id);
   }

   public record FusionBlackScreenPayload(boolean active) implements CustomPacketPayload {
      public static final Type<DapFusionHandler.FusionBlackScreenPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "fusion_black_screen"));
      public static final StreamCodec<FriendlyByteBuf, DapFusionHandler.FusionBlackScreenPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> buf.writeBoolean(p.active), buf -> new DapFusionHandler.FusionBlackScreenPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FusionFusedPayload(boolean fused) implements CustomPacketPayload {
      public static final Type<DapFusionHandler.FusionFusedPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "fusion_fused"));
      public static final StreamCodec<FriendlyByteBuf, DapFusionHandler.FusionFusedPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> buf.writeBoolean(p.fused), buf -> new DapFusionHandler.FusionFusedPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FusionGPressPayload() implements CustomPacketPayload {
      public static final Type<DapFusionHandler.FusionGPressPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "fusion_g_press"));
      public static final StreamCodec<FriendlyByteBuf, DapFusionHandler.FusionGPressPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> {}, buf -> new DapFusionHandler.FusionGPressPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public enum FusionPhase {
      AWAITING_G,
      WALK_QTE,
      FUSION_QTE,
      FUSED,
      FAILED;
   }

   public record FusionPhasePayload(UUID p1, UUID p2, int phase) implements CustomPacketPayload {
      public static final Type<DapFusionHandler.FusionPhasePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "fusion_phase"));
      public static final StreamCodec<FriendlyByteBuf, DapFusionHandler.FusionPhasePayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.p1);
         buf.writeUUID(payload.p2);
         buf.writeInt(payload.phase);
      }, buf -> new DapFusionHandler.FusionPhasePayload(buf.readUUID(), buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FusionQTEPayload(UUID playerId, String button, int stage, long windowStartMs, long windowEndMs, boolean open, int type)
      implements CustomPacketPayload {
      public static final Type<DapFusionHandler.FusionQTEPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "fusion_qte"));
      public static final StreamCodec<FriendlyByteBuf, DapFusionHandler.FusionQTEPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> {
            buf.writeUUID(p.playerId);
            buf.writeUtf(p.button);
            buf.writeInt(p.stage);
            buf.writeLong(p.windowStartMs);
            buf.writeLong(p.windowEndMs);
            buf.writeBoolean(p.open);
            buf.writeInt(p.type);
         },
         buf -> new DapFusionHandler.FusionQTEPayload(
            buf.readUUID(), buf.readUtf(), buf.readInt(), buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readInt()
         )
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public static class FusionSession {
      public final UUID p1Id;
      public final UUID p2Id;
      public ServerPlayer p1Ref;
      public ServerPlayer p2Ref;
      public final ServerLevel world;
      public DapFusionHandler.FusionPhase phase = DapFusionHandler.FusionPhase.AWAITING_G;
      public boolean p1PressedG = false;
      public boolean p2PressedG = false;
      public long gWindowOpenTime;
      public int walkStage = 0;
      public Vec3 p1WalkPos;
      public Vec3 p2WalkPos;
      public Vec3 p1SmoothStart;
      public Vec3 p2SmoothStart;
      public int smoothTpTick = 0;
      public boolean walkQteOpen = false;
      public boolean p1WalkPressed = false;
      public boolean p2WalkPressed = false;
      public long walkQteOpenTime = 0L;
      public String walkExpectedButton;
      public int fusionStage = 0;
      public boolean p1FusionPressed = false;
      public boolean p2FusionPressed = false;
      public long fusionQteOpenTime = 0L;
      public String fusionExpectedButton;
      public boolean fusionQteOpen = false;
      public long lastFusionStageEnd = 0L;
      public long walkHitWindowStart = 0L;
      public long walkHitWindowEnd = 0L;
      public long fusionHitWindowStart = 0L;
      public long fusionHitWindowEnd = 0L;
      public boolean walkIsTimingBar = false;
      public boolean fusionIsTimingBar = false;
      private static final String[] BUTTONS = new String[]{"G", "H"};
      private static final Random RNG = new Random();

      public boolean isSolo() {
         return this.p1Id.equals(this.p2Id);
      }

      FusionSession(ServerPlayer p1, ServerPlayer p2, long now) {
         this.p1Id = p1.getUUID();
         this.p2Id = p2.getUUID();
         this.p1Ref = p1;
         this.p2Ref = p2;
         this.world = p1.level();
         this.gWindowOpenTime = now;
      }

      String randomButton() {
         return CoopMovesConfig.get().easyFusionTest ? "G" : BUTTONS[RNG.nextInt(2)];
      }

      long randomGreenZoneStart(long hitWindowMs) {
         long minStart = 360L;
         long maxStart = 1080L;
         long range = maxStart - minStart - hitWindowMs;
         return range <= 0L ? minStart : minStart + (long)(RNG.nextDouble() * range);
      }

      long walkQteWindow() {
         if (CoopMovesConfig.get().easyFusionTest) {
            return 800L;
         }

         return switch (this.walkStage) {
            case 0 -> 450L;
            case 1 -> 2100L;
            default -> 200L;
         };
      }

      long fusionQteWindow() {
         return CoopMovesConfig.get().easyFusionTest ? 800L : DapFusionHandler.FUSION_QTE_WINDOWS[Math.min(this.fusionStage, 9)];
      }
   }

   public record FusionUnfusePayload() implements CustomPacketPayload {
      public static final Type<DapFusionHandler.FusionUnfusePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "fusion_unfuse"));
      public static final StreamCodec<FriendlyByteBuf, DapFusionHandler.FusionUnfusePayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> {}, buf -> new DapFusionHandler.FusionUnfusePayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
