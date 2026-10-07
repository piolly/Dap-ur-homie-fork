package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

public class HighfiveDapHandler {
   public static final double FACING_DOT_THRESHOLD = 0.5;
   public static final double PUSH_FAIL_DISTANCE = 0.5;
   public static final double TARGET_DISTANCE = 1.3;
   public static final double MAX_REPOSITION = 3.0;
   public static final long REPOSITION_MS = 380L;
   public static final long IMPACT_MS = 330L;
   public static final long IDLE_MS = 460L;
   public static final long MAX_IDLE_MS = 30000L;
   public static final long END_PUSH_MS = 290L;
   public static final long CLEANUP_MS = 840L;
   public static final double CANCEL_PUSH_DISTANCE = 0.25;
   public static final float IMPACT_SHAKE_AMOUNT = 2.5F;
   public static final long IMPACT_SHAKE_MS = 200L;
   public static final float CANCEL_SHAKE_AMOUNT = 1.5F;
   public static final long CANCEL_SHAKE_MS = 120L;
   public static final long IDLE_SHAKE_INTERVAL_MS = 2000L;
   public static final float IDLE_SHAKE_AMOUNT = 0.25F;
   public static final long IDLE_SHAKE_DURATION_MS = 80L;
   public static final double MOVEMENT_LOCK_THRESHOLD = 0.08;
   public static final int RESISTANCE_TICKS_TO_CANCEL = 15;
   public static final int MINIGAME_WIN_SCORE = 10;
   public static final float MINIGAME_LOSER_DAMAGE = 10.0F;
   public static final long SPACE_PRESS_COOLDOWN_MS = 50L;
   public static final double SURVIVOR_LAUNCH_HORIZONTAL = 0.5;
   public static final double SURVIVOR_LAUNCH_VERTICAL = 0.5;
   private static final Map<UUID, UUID> activePairs = new HashMap<>();
   private static final Map<UUID, Long> pairStartTime = new HashMap<>();
   private static final Set<UUID> impactFired = new HashSet<>();
   private static final Set<UUID> idleFired = new HashSet<>();
   private static final Set<UUID> inEndPhase = new HashSet<>();
   private static final Map<UUID, Long> endStartTime = new HashMap<>();
   private static final Set<UUID> cancelPushFired = new HashSet<>();
   private static final Map<UUID, Vec3> lockedPosHf = new HashMap<>();
   private static final Map<UUID, Vec3> lockedPosDap = new HashMap<>();
   private static final Map<UUID, Integer> resistanceTicks = new HashMap<>();
   private static final Map<UUID, Long> lastIdleShakeTime = new HashMap<>();
   private static final Map<UUID, Integer> hfScoreMap = new HashMap<>();
   private static final Map<UUID, Integer> dapScoreMap = new HashMap<>();
   private static final Map<UUID, Long> lastSpacePressHf = new HashMap<>();
   private static final Map<UUID, Long> lastSpacePressDap = new HashMap<>();
   private static final Set<UUID> minigameResultApplied = new HashSet<>();
   private static final Map<UUID, UUID> minigameLoserMap = new HashMap<>();
   private static final List<HighfiveDapHandler.PendingReposition> pendingRepositions = new ArrayList<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.playS2C().register(HighfiveDapHandler.HfDapStartPayload.ID, HighfiveDapHandler.HfDapStartPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(HighfiveDapHandler.HfDapIdlePayload.ID, HighfiveDapHandler.HfDapIdlePayload.CODEC);
      PayloadTypeRegistry.playS2C().register(HighfiveDapHandler.HfDapEndPayload.ID, HighfiveDapHandler.HfDapEndPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(HighfiveDapHandler.HfDapShakePayload.ID, HighfiveDapHandler.HfDapShakePayload.CODEC);
      PayloadTypeRegistry.playS2C().register(HighfiveDapHandler.HfDapScorePayload.ID, HighfiveDapHandler.HfDapScorePayload.CODEC);
      PayloadTypeRegistry.playC2S().register(HighfiveDapHandler.HfDapCancelPayload.ID, HighfiveDapHandler.HfDapCancelPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(HighfiveDapHandler.HfDapSpacePayload.ID, HighfiveDapHandler.HfDapSpacePayload.CODEC);
   }

   public static void register() {
      registerPayloads();
      ServerPlayNetworking.registerGlobalReceiver(
         HighfiveDapHandler.HfDapCancelPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onCancelRequest(ctx.player()))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         HighfiveDapHandler.HfDapSpacePayload.ID, (payload, ctx) -> ctx.server().execute(() -> onSpacePress(ctx.player()))
      );
      ServerTickEvents.END_SERVER_TICK.register(HighfiveDapHandler::onServerTick);
   }

   public static boolean tryStart(ServerPlayer dapPlayer, ServerPlayer hfPlayer) {
      if (!CoopMovesConfig.get().enableHighfiveDap) {
         return false;
      } else {
         UUID dapId = dapPlayer.getUUID();
         UUID hfId = hfPlayer.getUUID();
         if (isInHighfiveDap(hfId) || isInHighfiveDap(dapId)) {
            return false;
         } else if (!areFacingEachOther(hfPlayer, dapPlayer)) {
            long now = System.currentTimeMillis();
            pushApart(hfPlayer, dapPlayer, 0.5);
            HighFiveHandler.handRaisedTime.remove(hfId);
            HighFiveHandler.startAnimTime.remove(hfId);
            HighFiveHandler.lastHoldRefresh.remove(hfId);
            HighFiveHandler.syncHandRaised(hfPlayer, false);
            HighFiveHandler.highFiveCooldown.put(hfId, now);
            ChargedDapHandler.cooldowns.put(dapId, now + 500L);
            PoseNetworking.broadcastAnimState(hfPlayer, 0);
            PoseNetworking.broadcastAnimState(dapPlayer, 0);
            hfPlayer.displayClientMessage(Component.literal("§c✗ Not facing each other!"), true);
            dapPlayer.displayClientMessage(Component.literal("§c✗ Not facing each other!"), true);
            return true;
         } else {
            HighFiveHandler.handRaisedTime.remove(hfId);
            HighFiveHandler.startAnimTime.remove(hfId);
            HighFiveHandler.lastHoldRefresh.remove(hfId);
            HighFiveHandler.syncHandRaised(hfPlayer, false);
            faceEachOther(hfPlayer, dapPlayer);
            long now = System.currentTimeMillis();
            activePairs.put(hfId, dapId);
            pairStartTime.put(hfId, now);
            scheduleReposition(hfPlayer, dapPlayer);
            sendToAll(hfPlayer.level().getServer(), new HighfiveDapHandler.HfDapStartPayload(hfId, dapId, 0));
            sendToAll(hfPlayer.level().getServer(), new HighfiveDapHandler.HfDapStartPayload(hfId, dapId, 1));
            PoseNetworking.broadcastAnimState(hfPlayer, 108);
            PoseNetworking.broadcastAnimState(dapPlayer, 109);
            hfPlayer.swing(InteractionHand.MAIN_HAND);
            dapPlayer.swing(InteractionHand.MAIN_HAND);
            return true;
         }
      }
   }

   public static void onPlayerLeave(UUID leavingId, MinecraftServer server) {
      pendingRepositions.removeIf(r -> r.player.getUUID().equals(leavingId));
      UUID hfId = getHfId(leavingId);
      if (hfId != null) {
         UUID dapId = activePairs.get(hfId);
         UUID survivorId = leavingId.equals(hfId) ? dapId : hfId;
         if (survivorId != null) {
            ServerPlayer survivor = server.getPlayerList().getPlayer(survivorId);
            if (survivor != null && survivor.isAlive()) {
               launchSurvivor(survivor);
               PoseNetworking.broadcastAnimState(survivor, 0);
            }
         }

         applyMinigameResult(hfId, server);
         sendToAll(server, new HighfiveDapHandler.HfDapEndPayload(hfId, dapId != null ? dapId : leavingId));
         cleanupPairImmediate(hfId, server);
      }
   }

   public static void cleanup(UUID id) {
      pendingRepositions.removeIf(r -> r.player.getUUID().equals(id));
   }

   public static boolean isInHighfiveDap(UUID id) {
      return activePairs.containsKey(id) || activePairs.containsValue(id);
   }

   private static void onCancelRequest(ServerPlayer player) {
      UUID hfId = getHfId(player.getUUID());
      if (hfId != null) {
         if (!inEndPhase.contains(hfId)) {
            UUID dapId = activePairs.get(hfId);
            if (dapId != null) {
               startEndSequence(hfId, dapId, player.level().getServer());
            }
         }
      }
   }

   private static void onSpacePress(ServerPlayer player) {
      UUID id = player.getUUID();
      UUID hfId = getHfId(id);
      if (hfId != null) {
         if (!inEndPhase.contains(hfId)) {
            if (idleFired.contains(hfId)) {
               UUID dapId = activePairs.get(hfId);
               if (dapId != null) {
                  boolean isHf = id.equals(hfId);
                  long now = System.currentTimeMillis();
                  Map<UUID, Long> lastMap = isHf ? lastSpacePressHf : lastSpacePressDap;
                  Long last = lastMap.get(hfId);
                  if (last == null || now - last >= 50L) {
                     lastMap.put(hfId, now);
                     Map<UUID, Integer> scoreMap = isHf ? hfScoreMap : dapScoreMap;
                     int newScore = scoreMap.merge(hfId, 1, Integer::sum);
                     int hfS = hfScoreMap.getOrDefault(hfId, 0);
                     int dapS = dapScoreMap.getOrDefault(hfId, 0);
                     ServerPlayer hfP = player.level().getServer().getPlayerList().getPlayer(hfId);
                     ServerPlayer dapP = player.level().getServer().getPlayerList().getPlayer(dapId);
                     if (hfP != null) {
                        ServerPlayNetworking.send(hfP, new HighfiveDapHandler.HfDapScorePayload(hfId, dapId, hfS, dapS));
                     }

                     if (dapP != null) {
                        ServerPlayNetworking.send(dapP, new HighfiveDapHandler.HfDapScorePayload(hfId, dapId, hfS, dapS));
                     }

                     if (newScore >= 10) {
                        applyMinigameResult(hfId, player.level().getServer());
                        startEndSequence(hfId, dapId, player.level().getServer());
                     }
                  }
               }
            }
         }
      }
   }

   private static void applyMinigameResult(UUID hfId, MinecraftServer server) {
      if (!minigameResultApplied.contains(hfId)) {
         minigameResultApplied.add(hfId);
         UUID dapId = activePairs.get(hfId);
         if (dapId != null) {
            int hfS = hfScoreMap.getOrDefault(hfId, 0);
            int dapS = dapScoreMap.getOrDefault(hfId, 0);
            if (hfS != dapS) {
               UUID winnerId = hfS > dapS ? hfId : dapId;
               UUID loserId = hfS > dapS ? dapId : hfId;
               minigameLoserMap.put(hfId, loserId);
               ServerPlayer winner = server.getPlayerList().getPlayer(winnerId);
               ServerPlayer loser = server.getPlayerList().getPlayer(loserId);
               if (winner != null) {
                  winner.displayClientMessage(Component.literal("§6⚡ §lWINNER! §r§6You win the dap!"), true);
               }

               if (loser != null && loser.isAlive()) {
                  loser.hurtServer(loser.level(), loser.level().damageSources().magic(), 10.0F);
                  loser.displayClientMessage(Component.literal("§c☠ §lLOSER! §r§c-5 ❤"), true);
               }
            }
         }
      }
   }

   private static void onServerTick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      Iterator<HighfiveDapHandler.PendingReposition> it = pendingRepositions.iterator();

      while (it.hasNext()) {
         HighfiveDapHandler.PendingReposition r = it.next();
         if (r.ticks <= 0) {
            it.remove();
         } else {
            Vec3 step = r.remaining.scale(1.0 / r.ticks);
            Vec3 pos = r.player.position().add(step);
            r.player.teleportTo(pos.x, pos.y, pos.z);
            r.remaining = r.remaining.subtract(step);
            r.ticks--;
            if (r.ticks <= 0) {
               it.remove();
            }
         }
      }

      Set<UUID> toCleanup = new HashSet<>();

      for (Entry<UUID, UUID> entry : new HashMap<>(activePairs).entrySet()) {
         UUID hfId = entry.getKey();
         UUID dapId = entry.getValue();
         ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
         ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
         if (hfP == null || dapP == null) {
            toCleanup.add(hfId);
         } else if (hfP.isAlive() && dapP.isAlive()) {
            Long startMs = pairStartTime.get(hfId);
            if (startMs == null) {
               toCleanup.add(hfId);
            } else if (inEndPhase.contains(hfId)) {
               Long endMs = endStartTime.get(hfId);
               if (endMs == null) {
                  toCleanup.add(hfId);
               } else {
                  long endElapsed = now - endMs;
                  if (!cancelPushFired.contains(hfId) && endElapsed >= 290L) {
                     cancelPushFired.add(hfId);
                     UUID loserId = minigameLoserMap.get(hfId);
                     if (loserId != null) {
                        boolean loserIsHf = loserId.equals(hfId);
                        ServerPlayer loser = loserIsHf ? hfP : dapP;
                        ServerPlayer winner = loserIsHf ? dapP : hfP;
                        pushLoserBack(loser, winner, 0.25);
                     } else {
                        pushApart(hfP, dapP, 0.25);
                     }

                     ServerPlayNetworking.send(hfP, new HighfiveDapHandler.HfDapShakePayload(1.5F, 120L));
                     ServerPlayNetworking.send(dapP, new HighfiveDapHandler.HfDapShakePayload(1.5F, 120L));
                  }

                  if (endElapsed >= 840L) {
                     toCleanup.add(hfId);
                  }
               }
            } else {
               long elapsed = now - startMs;
               if (!impactFired.contains(hfId) && elapsed >= 330L) {
                  impactFired.add(hfId);
                  spawnImpactEffects(hfP, dapP);
                  ServerPlayNetworking.send(hfP, new HighfiveDapHandler.HfDapShakePayload(2.5F, 200L));
                  ServerPlayNetworking.send(dapP, new HighfiveDapHandler.HfDapShakePayload(2.5F, 200L));
               }

               if (!idleFired.contains(hfId) && elapsed >= 460L) {
                  idleFired.add(hfId);
                  sendToAll(server, new HighfiveDapHandler.HfDapIdlePayload(hfId, dapId));
                  PoseNetworking.broadcastAnimState(hfP, 110);
                  PoseNetworking.broadcastAnimState(dapP, 110);
                  lockedPosHf.put(hfId, hfP.position());
                  lockedPosDap.put(hfId, dapP.position());
                  hfScoreMap.put(hfId, 0);
                  dapScoreMap.put(hfId, 0);
                  ServerPlayNetworking.send(hfP, new HighfiveDapHandler.HfDapScorePayload(hfId, dapId, 0, 0));
                  ServerPlayNetworking.send(dapP, new HighfiveDapHandler.HfDapScorePayload(hfId, dapId, 0, 0));
               }

               if (idleFired.contains(hfId) && elapsed >= 30460L) {
                  System.out.println("[HighfiveDap] idle watchdog fired for " + hfId);
                  startEndSequence(hfId, dapId, server);
               } else if (idleFired.contains(hfId)) {
                  boolean hfCancel = handlePositionLock(hfP, lockedPosHf.get(hfId));
                  boolean dapCancel = handlePositionLock(dapP, lockedPosDap.get(hfId));
                  if (!hfCancel && !dapCancel) {
                     long lastShake = lastIdleShakeTime.getOrDefault(hfId, 0L);
                     if (now - lastShake >= 2000L) {
                        lastIdleShakeTime.put(hfId, now);
                        ServerPlayNetworking.send(hfP, new HighfiveDapHandler.HfDapShakePayload(0.25F, 80L));
                        ServerPlayNetworking.send(dapP, new HighfiveDapHandler.HfDapShakePayload(0.25F, 80L));
                     }
                  } else {
                     startEndSequence(hfId, dapId, server);
                  }
               }
            }
         } else {
            ServerPlayer alive = hfP.isAlive() ? hfP : dapP;
            if (alive.isAlive()) {
               launchSurvivor(alive);
            }

            toCleanup.add(hfId);
         }
      }

      toCleanup.forEach(hfIdx -> cleanupPair(hfIdx, server));
   }

   private static boolean handlePositionLock(ServerPlayer player, Vec3 lockedPos) {
      if (lockedPos == null) {
         return false;
      } else {
         UUID pid = player.getUUID();
         double xzDist = Math.hypot(player.getX() - lockedPos.x, player.getZ() - lockedPos.z);
         if (xzDist > 0.08) {
            int ticks = resistanceTicks.merge(pid, 1, Integer::sum);
            player.teleportTo(lockedPos.x, player.getY(), lockedPos.z);
            Vec3 vel = player.getDeltaMovement();
            player.setDeltaMovement(0.0, vel.y, 0.0);
            player.hurtMarked = true;
            return ticks >= 15;
         } else {
            resistanceTicks.put(pid, 0);
            return false;
         }
      }
   }

   private static void startEndSequence(UUID hfId, UUID dapId, MinecraftServer server) {
      if (!inEndPhase.contains(hfId)) {
         applyMinigameResult(hfId, server);
         inEndPhase.add(hfId);
         endStartTime.put(hfId, System.currentTimeMillis());
         pendingRepositions.removeIf(r -> r.player.getUUID().equals(hfId) || r.player.getUUID().equals(dapId));
         sendToAll(server, new HighfiveDapHandler.HfDapEndPayload(hfId, dapId));
         ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
         ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
         if (hfP != null) {
            PoseNetworking.broadcastAnimState(hfP, 111);
         }

         if (dapP != null) {
            PoseNetworking.broadcastAnimState(dapP, 111);
         }
      }
   }

   private static void cleanupPair(UUID hfId, MinecraftServer server) {
      UUID dapId = activePairs.remove(hfId);
      clearPairMaps(hfId, dapId);
      ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
      ServerPlayer dapP = dapId != null ? server.getPlayerList().getPlayer(dapId) : null;
      if (hfP != null) {
         PoseNetworking.broadcastAnimState(hfP, 0);
      }

      if (dapP != null) {
         PoseNetworking.broadcastAnimState(dapP, 0);
      }

      applyCooldowns(hfId, dapId);
   }

   private static void cleanupPairImmediate(UUID hfId, MinecraftServer server) {
      UUID dapId = activePairs.remove(hfId);
      clearPairMaps(hfId, dapId);
      ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
      ServerPlayer dapP = dapId != null ? server.getPlayerList().getPlayer(dapId) : null;
      if (hfP != null && hfP.isAlive()) {
         PoseNetworking.broadcastAnimState(hfP, 0);
      }

      if (dapP != null && dapP.isAlive()) {
         PoseNetworking.broadcastAnimState(dapP, 0);
      }

      applyCooldowns(hfId, dapId);
   }

   private static void clearPairMaps(UUID hfId, UUID dapId) {
      pairStartTime.remove(hfId);
      impactFired.remove(hfId);
      idleFired.remove(hfId);
      inEndPhase.remove(hfId);
      endStartTime.remove(hfId);
      cancelPushFired.remove(hfId);
      lockedPosHf.remove(hfId);
      lockedPosDap.remove(hfId);
      lastIdleShakeTime.remove(hfId);
      hfScoreMap.remove(hfId);
      dapScoreMap.remove(hfId);
      lastSpacePressHf.remove(hfId);
      lastSpacePressDap.remove(hfId);
      minigameResultApplied.remove(hfId);
      minigameLoserMap.remove(hfId);
      if (hfId != null) {
         resistanceTicks.remove(hfId);
      }

      if (dapId != null) {
         resistanceTicks.remove(dapId);
      }

      if (dapId != null) {
         UUID fd = dapId;
         pendingRepositions.removeIf(r -> r.player.getUUID().equals(hfId) || r.player.getUUID().equals(fd));
      }
   }

   private static void applyCooldowns(UUID hfId, UUID dapId) {
      long now = System.currentTimeMillis();
      HighFiveHandler.highFiveCooldown.put(hfId, now);
      ChargedDapHandler.cooldowns.put(hfId, now + 1000L);
      if (dapId != null) {
         HighFiveHandler.highFiveCooldown.put(dapId, now);
         ChargedDapHandler.cooldowns.put(dapId, now + 1000L);
      }
   }

   private static UUID getHfId(UUID id) {
      if (activePairs.containsKey(id)) {
         return id;
      }

      for (Entry<UUID, UUID> e : activePairs.entrySet()) {
         if (e.getValue().equals(id)) {
            return e.getKey();
         }
      }

      return null;
   }

   private static boolean areFacingEachOther(ServerPlayer p1, ServerPlayer p2) {
      Vec3 d = p2.position().subtract(p1.position());
      double len = Math.hypot(d.x, d.z);
      if (len < 1.0E-6) {
         return true;
      }

      Vec3 h12 = new Vec3(d.x / len, 0.0, d.z / len);
      return horizontalLook(p1).dot(h12) >= 0.5 && horizontalLook(p2).dot(h12.reverse()) >= 0.5;
   }

   private static Vec3 horizontalLook(ServerPlayer p) {
      double r = Math.toRadians(p.getYRot());
      return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
   }

   private static void faceEachOther(ServerPlayer a, ServerPlayer b) {
      Vec3 diff = b.position().subtract(a.position());
      if (!(diff.horizontalDistanceSqr() < 1.0E-6)) {
         float yawA = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
         a.setYRot(yawA);
         a.setYBodyRot(yawA);
         a.setYHeadRot(yawA);
         b.setYRot(yawA + 180.0F);
         b.setYBodyRot(yawA + 180.0F);
         b.setYHeadRot(yawA + 180.0F);
         a.swing(InteractionHand.MAIN_HAND);
         b.swing(InteractionHand.MAIN_HAND);
      }
   }

   private static void launchSurvivor(ServerPlayer survivor) {
      double yawRad = Math.toRadians(survivor.getYRot());
      survivor.setDeltaMovement(Math.sin(yawRad) * 0.5, 0.5, -Math.cos(yawRad) * 0.5);
      survivor.hurtMarked = true;
   }

   private static void pushLoserBack(ServerPlayer loser, ServerPlayer winner, double amount) {
      Vec3 delta = loser.position().subtract(winner.position());
      double len = Math.hypot(delta.x, delta.z);
      Vec3 dir;
      if (len < 1.0E-6) {
         double yaw = Math.toRadians(loser.getYRot());
         dir = new Vec3(Math.sin(yaw), 0.0, -Math.cos(yaw));
      } else {
         dir = new Vec3(delta.x / len, 0.0, delta.z / len);
      }

      loser.teleportTo(loser.getX() + dir.x * amount, loser.getY(), loser.getZ() + dir.z * amount);
   }

   private static void scheduleReposition(ServerPlayer hf, ServerPlayer dap) {
      Vec3 posHf = hf.position();
      Vec3 posDap = dap.position();
      double dist = Math.hypot(posHf.x - posDap.x, posHf.z - posDap.z);
      if (!(dist < 1.0E-6) && !(dist <= 1.3)) {
         Vec3 mid = posHf.add(posDap).scale(0.5);
         Vec3 norm = new Vec3((posDap.x - posHf.x) / dist, 0.0, (posDap.z - posHf.z) / dist);
         double h = 0.65;
         Vec3 tHf = new Vec3(mid.x - norm.x * h, posHf.y, mid.z - norm.z * h);
         Vec3 tDap = new Vec3(mid.x + norm.x * h, posDap.y, mid.z + norm.z * h);
         Vec3 dHf = new Vec3(tHf.x - posHf.x, 0.0, tHf.z - posHf.z);
         Vec3 dDap = new Vec3(tDap.x - posDap.x, 0.0, tDap.z - posDap.z);
         if (!(dHf.length() > 3.0) && !(dDap.length() > 3.0)) {
            int ticks = Math.max(1, 7);
            pendingRepositions.add(new HighfiveDapHandler.PendingReposition(hf, dHf, ticks));
            pendingRepositions.add(new HighfiveDapHandler.PendingReposition(dap, dDap, ticks));
         }
      }
   }

   private static void pushApart(ServerPlayer p1, ServerPlayer p2, double amount) {
      Vec3 d = p2.position().subtract(p1.position());
      double len = Math.hypot(d.x, d.z);
      Vec3 dir = len < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(d.x / len, 0.0, d.z / len);
      p1.teleportTo(p1.getX() - dir.x * amount, p1.getY(), p1.getZ() - dir.z * amount);
      p2.teleportTo(p2.getX() + dir.x * amount, p2.getY(), p2.getZ() + dir.z * amount);
   }

   private static void spawnImpactEffects(ServerPlayer hf, ServerPlayer dap) {
      Vec3 pos = hf.position().add(dap.position()).scale(0.5).add(0.0, 1.4, 0.0);
      ServerLevel world = hf.level();
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_HIT, SoundSource.PLAYERS, 1.5F, 1.0F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 1.1F);
      world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), pos.x, pos.y, pos.z, 3, 0.0, 0.0, 0.0, 0.0);
      world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 40, 0.3, 0.3, 0.3, 0.18);
      world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 25, 0.25, 0.25, 0.25, 0.14);
      world.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 20, 0.2, 0.2, 0.2, 0.1);
      world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 12, 0.15, 0.15, 0.15, 0.1);
      double groundY = hf.getY() + 0.1;

      for (double angle = 0.0; angle < 360.0; angle += 15.0) {
         double rad = Math.toRadians(angle);

         for (double r = 0.4; r <= 2.0; r += 0.6) {
            world.sendParticles(ParticleTypes.END_ROD, pos.x + Math.cos(rad) * r, groundY, pos.z + Math.sin(rad) * r, 1, 0.02, 0.03, 0.02, 0.01);
         }
      }
   }

   private static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   public record HfDapCancelPayload() implements CustomPacketPayload {
      public static final Type<HighfiveDapHandler.HfDapCancelPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "hfdap_cancel"));
      public static final StreamCodec<FriendlyByteBuf, HighfiveDapHandler.HfDapCancelPayload> CODEC = StreamCodec.unit(
         new HighfiveDapHandler.HfDapCancelPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HfDapEndPayload(UUID hfId, UUID dapId) implements CustomPacketPayload {
      public static final Type<HighfiveDapHandler.HfDapEndPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "hfdap_end"));
      public static final StreamCodec<FriendlyByteBuf, HighfiveDapHandler.HfDapEndPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeUUID(p.hfId());
         buf.writeUUID(p.dapId());
      }, buf -> new HighfiveDapHandler.HfDapEndPayload(buf.readUUID(), buf.readUUID()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HfDapIdlePayload(UUID hfId, UUID dapId) implements CustomPacketPayload {
      public static final Type<HighfiveDapHandler.HfDapIdlePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "hfdap_idle"));
      public static final StreamCodec<FriendlyByteBuf, HighfiveDapHandler.HfDapIdlePayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeUUID(p.hfId());
         buf.writeUUID(p.dapId());
      }, buf -> new HighfiveDapHandler.HfDapIdlePayload(buf.readUUID(), buf.readUUID()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HfDapScorePayload(UUID hfId, UUID dapId, int hfScore, int dapScore) implements CustomPacketPayload {
      public static final Type<HighfiveDapHandler.HfDapScorePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "hfdap_score"));
      public static final StreamCodec<FriendlyByteBuf, HighfiveDapHandler.HfDapScorePayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeUUID(p.hfId());
         buf.writeUUID(p.dapId());
         buf.writeInt(p.hfScore());
         buf.writeInt(p.dapScore());
      }, buf -> new HighfiveDapHandler.HfDapScorePayload(buf.readUUID(), buf.readUUID(), buf.readInt(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HfDapShakePayload(float amount, long durationMs) implements CustomPacketPayload {
      public static final Type<HighfiveDapHandler.HfDapShakePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "hfdap_shake"));
      public static final StreamCodec<FriendlyByteBuf, HighfiveDapHandler.HfDapShakePayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeFloat(p.amount());
         buf.writeLong(p.durationMs());
      }, buf -> new HighfiveDapHandler.HfDapShakePayload(buf.readFloat(), buf.readLong()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HfDapSpacePayload() implements CustomPacketPayload {
      public static final Type<HighfiveDapHandler.HfDapSpacePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "hfdap_space"));
      public static final StreamCodec<FriendlyByteBuf, HighfiveDapHandler.HfDapSpacePayload> CODEC = StreamCodec.unit(
         new HighfiveDapHandler.HfDapSpacePayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HfDapStartPayload(UUID hfId, UUID dapId, int role) implements CustomPacketPayload {
      public static final Type<HighfiveDapHandler.HfDapStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "hfdap_start"));
      public static final StreamCodec<FriendlyByteBuf, HighfiveDapHandler.HfDapStartPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeUUID(p.hfId());
         buf.writeUUID(p.dapId());
         buf.writeInt(p.role());
      }, buf -> new HighfiveDapHandler.HfDapStartPayload(buf.readUUID(), buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static class PendingReposition {
      final ServerPlayer player;
      Vec3 remaining;
      int ticks;

      PendingReposition(ServerPlayer p, Vec3 rem, int t) {
         this.player = p;
         this.remaining = rem;
         this.ticks = t;
      }
   }
}
