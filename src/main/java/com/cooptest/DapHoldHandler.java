package com.cooptest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public class DapHoldHandler {
   private static final long ANIM_LENGTH_MS = 1042L;
   private static final long J_WINDOW_START_MS = 330L;
   private static final long IMPACT_MS = 420L;
   private static final long J_WINDOW_END_MS = 1330L;
   private static final double STOP_DISTANCE = 1.5;
   private static final double TP_SPEED = 0.08;
   private static final Map<UUID, UUID> activePairs = new HashMap<>();
   private static final Map<UUID, Long> pairStartTime = new HashMap<>();
   private static final Set<UUID> windowOpen = new HashSet<>();
   private static final Set<UUID> impactFired = new HashSet<>();
   private static final Set<UUID> looping = new HashSet<>();
   private static final Set<UUID> endingAnimation = new HashSet<>();
   private static final Map<UUID, Long> jHoldLastTick = new HashMap<>();
   private static final Map<UUID, Long> loopStartTime = new HashMap<>();
   private static final Map<UUID, ArmorStand> handStands = new HashMap<>();
   private static final Set<UUID> tpComplete = new HashSet<>();
   private static final Map<UUID, Set<UUID>> groupJoiners = new HashMap<>();
   private static final Map<UUID, UUID> joinerGroup = new HashMap<>();
   private static final Map<UUID, Long> joinerJLast = new HashMap<>();
   private static final Map<UUID, Long> releaseFirst = new HashMap<>();
   private static final Map<UUID, Set<UUID>> releasedSet = new HashMap<>();
   private static final double GROUP_JOIN_RADIUS = 2.5;
   private static final long RELEASE_WINDOW_MS = 500L;

   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(DapHoldHandler.DapHoldStartPayload.ID, DapHoldHandler.DapHoldStartPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DapHoldHandler.DapHoldWindowPayload.ID, DapHoldHandler.DapHoldWindowPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DapHoldHandler.DapHoldLoopPayload.ID, DapHoldHandler.DapHoldLoopPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DapHoldHandler.DapHoldEndPayload.ID, DapHoldHandler.DapHoldEndPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DapHoldHandler.DapHoldFreezePayload.ID, DapHoldHandler.DapHoldFreezePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DapHoldHandler.GroupJoinedPayload.ID, DapHoldHandler.GroupJoinedPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DapHoldHandler.GroupResultPayload.ID, DapHoldHandler.GroupResultPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(DapHoldHandler.DapHoldJHoldPayload.ID, DapHoldHandler.DapHoldJHoldPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(DapHoldHandler.DapHoldJReleasePayload.ID, DapHoldHandler.DapHoldJReleasePayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(DapHoldHandler.GroupJoinPayload.ID, DapHoldHandler.GroupJoinPayload.CODEC);
   }

   public static void register() {
      registerPayloads();
      ServerPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldJHoldPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onJHold(ctx.player())));
      ServerPlayNetworking.registerGlobalReceiver(
         DapHoldHandler.DapHoldJReleasePayload.ID, (payload, ctx) -> ctx.server().execute(() -> onJRelease(ctx.player()))
      );
      ServerPlayNetworking.registerGlobalReceiver(DapHoldHandler.GroupJoinPayload.ID, (payload, ctx) -> ctx.server().execute(() -> {
         ServerPlayer player = ctx.player();
         UUID id = player.getUUID();
         if (!isInDapHold(id)) {
            tryJoinGroup(player, System.currentTimeMillis());
         }
      }));
      ServerTickEvents.END_SERVER_TICK.register(DapHoldHandler::onServerTick);
   }

   private static void makeFaceEachOther(ServerPlayer p1, ServerPlayer p2) {
      Vec3 p1Pos = p1.position();
      Vec3 p2Pos = p2.position();
      p1.swing(InteractionHand.MAIN_HAND);
      p2.swing(InteractionHand.MAIN_HAND);
      System.out.println("[DapHold]  Left click swing - body rotation synced!");
      double dx = p2Pos.x - p1Pos.x;
      double dz = p2Pos.z - p1Pos.z;
      float yawP1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
      float yawP2 = yawP1 + 180.0F;
      p1.setYRot(yawP1);
      p1.setYBodyRot(yawP1);
      p1.setYHeadRot(yawP1);
      p1.teleportTo(p1.level(), p1Pos.x, p1Pos.y, p1Pos.z, Set.of(), yawP1, 0.0F, false);
      p2.setYRot(yawP2);
      p2.setYBodyRot(yawP2);
      p2.setYHeadRot(yawP2);
      p2.teleportTo(p2.level(), p2Pos.x, p2Pos.y, p2Pos.z, Set.of(), yawP2, 0.0F, false);
   }

   private static boolean arePlayersFacingEachOther(ServerPlayer p1, ServerPlayer p2) {
      Vec3 p1Pos = p1.position();
      Vec3 p2Pos = p2.position();
      Vec3 directionTo = p2Pos.subtract(p1Pos).normalize();
      Vec3 p1Looking = p1.getLookAngle();
      double dot1 = p1Looking.dot(directionTo);
      if (dot1 < 0.85) {
         return false;
      }

      Vec3 directionBack = p1Pos.subtract(p2Pos).normalize();
      Vec3 p2Looking = p2.getLookAngle();
      double dot2 = p2Looking.dot(directionBack);
      return dot2 >= 0.85;
   }

   public static void startDapHold(ServerPlayer hfPlayer, ServerPlayer dapPlayer) {
      UUID hfId = hfPlayer.getUUID();
      UUID dapId = dapPlayer.getUUID();
      if (!isInDapHold(hfId) && !isInDapHold(dapId)) {
         if (!arePlayersFacingEachOther(hfPlayer, dapPlayer)) {
            hfPlayer.sendOverlayMessage(Component.literal("§cNot facing each other!"));
            dapPlayer.sendOverlayMessage(Component.literal("§cNot facing each other!"));
            System.out.println("[DapHold]  FAILED - Players not facing each other!");
         } else {
            System.out.println("[DapHold]  Facing check passed! START! HF=" + hfPlayer.getName().getString() + " DAP=" + dapPlayer.getName().getString());
            HighFiveHandler.handRaisedTime.remove(hfId);
            HighFiveHandler.startAnimTime.remove(hfId);
            HighFiveHandler.syncHandRaised(hfPlayer, false);
            System.out.println("[DapHold] Removed HF player from HighFiveHandler control");
            DapSession session = DapSessionManager.createSession(hfId, dapId, 1.5, DapSession.DapType.PERFECT_DAP);
            activePairs.put(hfId, dapId);
            pairStartTime.put(hfId, System.currentTimeMillis());
            sendFreeze(hfPlayer.level().getServer(), hfId, true);
            sendFreeze(hfPlayer.level().getServer(), dapId, true);
            System.out.println("[DapHold] Sent freeze to both players");
            spawnHandStand(hfPlayer, dapPlayer);
            System.out.println("[DapHold] Sending DapHoldStartPayload:");
            System.out.println("  - HF player (" + hfPlayer.getName().getString() + "): role=0 (highfive_dap)");
            System.out.println("  - DAP player (" + dapPlayer.getName().getString() + "): role=1 (dap_high)");
            sendToAll(hfPlayer.level().getServer(), new DapHoldHandler.DapHoldStartPayload(hfId, dapId, 0));
            sendToAll(hfPlayer.level().getServer(), new DapHoldHandler.DapHoldStartPayload(dapId, hfId, 1));
            PoseNetworking.broadcastAnimState(hfPlayer, 38);
            PoseNetworking.broadcastAnimState(dapPlayer, 39);
         }
      }
   }

   private static void onServerTick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      Set<UUID> toCleanup = new HashSet<>();

      for (Entry<UUID, UUID> entry : activePairs.entrySet()) {
         UUID hfId = entry.getKey();
         UUID dapId = entry.getValue();
         ServerPlayer hfPlayer = server.getPlayerList().getPlayer(hfId);
         ServerPlayer dapPlayer = server.getPlayerList().getPlayer(dapId);
         if (hfPlayer != null && dapPlayer != null) {
            Long startMs = pairStartTime.get(hfId);
            if (startMs == null) {
               toCleanup.add(hfId);
            } else {
               long elapsed = now - startMs;
               if (!tpComplete.contains(hfId)) {
                  tpComplete.add(hfId);
               }

               updateHandStand(hfPlayer, dapPlayer, hfId);
               if (elapsed % 500L < 50L) {
                  hfPlayer.swing(InteractionHand.MAIN_HAND);
                  dapPlayer.swing(InteractionHand.MAIN_HAND);
               }

               if (!windowOpen.contains(hfId) && elapsed >= 330L) {
                  windowOpen.add(hfId);
                  sendToAll(server, new DapHoldHandler.DapHoldWindowPayload(true));
                  hfPlayer.sendOverlayMessage(Component.literal("§e⚡ HOLD J "));
                  dapPlayer.sendOverlayMessage(Component.literal("§e⚡ HOLD J "));
               }

               if (!impactFired.contains(hfId) && elapsed >= 420L) {
                  impactFired.add(hfId);
                  spawnImpactParticles(hfPlayer, dapPlayer, hfId);
               }

               if (windowOpen.contains(hfId) && !looping.contains(hfId) && !endingAnimation.contains(hfId) && elapsed >= 1330L) {
                  if (isHoldingJ(hfId, now) && isHoldingJ(dapId, now)) {
                     looping.add(hfId);
                     loopStartTime.put(hfId, now);
                     DapSessionManager.removeSession(hfId);
                     sendToAll(server, new DapHoldHandler.DapHoldLoopPayload(true));
                     if (hfPlayer != null && dapPlayer != null) {
                        PoseNetworking.broadcastAnimState(hfPlayer, 40);
                        PoseNetworking.broadcastAnimState(dapPlayer, 40);
                     }

                     System.out.println("[DapHold] BOTH HELD J → DAPPING LOOP!");
                  } else {
                     endingAnimation.add(hfId);
                     sendToAll(server, new DapHoldHandler.DapHoldWindowPayload(false));
                     doUnfreeze(server, hfId, dapId);
                  }
               }

               if (endingAnimation.contains(hfId) && elapsed >= 1042L) {
                  sendToAll(server, new DapHoldHandler.DapHoldEndPayload(false));
                  toCleanup.add(hfId);
               }
            }
         } else {
            toCleanup.add(hfId);
         }
      }

      Set<UUID> groupResultNeeded = new HashSet<>();

      for (UUID hfId : looping) {
         ServerPlayer hfPlayer = server.getPlayerList().getPlayer(hfId);
         UUID dapId = activePairs.get(hfId);
         ServerPlayer dapPlayer = server.getPlayerList().getPlayer(dapId);
         if (hfPlayer != null && dapPlayer != null) {
            ServerLevel world = hfPlayer.level();
            ArmorStand stand = handStands.get(hfId);
            if (stand != null && !stand.isRemoved()) {
               Vec3 impactPos = stand.position();
               world.sendParticles(ParticleTypes.CRIT, impactPos.x, impactPos.y, impactPos.z, 2, 0.1, 0.1, 0.1, 0.02);
            }

            Set<UUID> joiners = groupJoiners.get(hfId);
            if (joiners != null && !joiners.isEmpty()) {
               Long first = releaseFirst.get(hfId);
               if (first != null && now - first > 500L) {
                  groupResultNeeded.add(hfId);
               } else {
                  Set<UUID> toEvict = new HashSet<>();

                  for (UUID jId : joiners) {
                     Long lastJ = joinerJLast.get(jId);
                     if (lastJ == null || now - lastJ > 300L) {
                        toEvict.add(jId);
                     }
                  }

                  for (UUID jId : toEvict) {
                     joiners.remove(jId);
                     joinerGroup.remove(jId);
                     joinerJLast.remove(jId);
                     sendFreeze(server, jId, false);
                     ServerPlayer jp = server.getPlayerList().getPlayer(jId);
                     if (jp != null) {
                        PoseNetworking.broadcastAnimState(jp, 41);
                        jp.sendOverlayMessage(Component.literal("§7Left the group"));
                     }
                  }

                  if (server.getTickCount() % 4 == 0) {
                     faceGroupCenter(hfId, server);
                     ServerPlayer hfP2 = server.getPlayerList().getPlayer(hfId);
                     UUID dapId2 = activePairs.get(hfId);
                     ServerPlayer dapP2 = server.getPlayerList().getPlayer(dapId2);
                     if (hfP2 != null) {
                        hfP2.setYHeadRot(hfP2.yBodyRot);
                     }

                     if (dapP2 != null) {
                        dapP2.setYHeadRot(dapP2.yBodyRot);
                     }

                     for (UUID jId : joiners) {
                        ServerPlayer jp = server.getPlayerList().getPlayer(jId);
                        if (jp != null) {
                           jp.setYHeadRot(jp.yBodyRot);
                        }
                     }
                  }

                  Vec3 mid = getGroupMidpoint(hfId, server);
                  int chargeParticles = joiners.size() + 1;
                  world.sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y + 1.2, mid.z, chargeParticles, 0.3, 0.2, 0.3, 0.05);
               }
            }
         }
      }

      for (UUID hfId : groupResultNeeded) {
         if (looping.contains(hfId)) {
            doGroupResult(hfId, server, false);
         }
      }

      toCleanup.forEach(hfIdx -> cleanupPair(hfIdx, server));
   }

   private static void onJHold(ServerPlayer player) {
      UUID id = player.getUUID();
      long now = System.currentTimeMillis();
      UUID hfId = getPairHfId(id);
      if (hfId != null && windowOpen.contains(hfId)) {
         jHoldLastTick.put(id, now);
      } else if (joinerGroup.containsKey(id)) {
         joinerJLast.put(id, now);
      } else {
         if (hfId == null) {
            tryJoinGroup(player, now);
         }
      }
   }

   private static void onJRelease(ServerPlayer player) {
      UUID id = player.getUUID();
      jHoldLastTick.remove(id);
      joinerJLast.remove(id);
      UUID joinerHfId = joinerGroup.get(id);
      if (joinerHfId != null) {
         logGroupRelease(id, joinerHfId, player.level().getServer());
      } else {
         UUID hfId = getPairHfId(id);
         if (hfId != null && looping.contains(hfId)) {
            MinecraftServer server = player.level().getServer();
            if (server != null) {
               if (groupJoiners.containsKey(hfId) && !groupJoiners.get(hfId).isEmpty()) {
                  logGroupRelease(id, hfId, server);
               } else {
                  UUID dapId = activePairs.get(hfId);
                  looping.remove(hfId);
                  loopStartTime.remove(hfId);
                  doUnfreeze(server, hfId, dapId);
                  sendToAll(server, new DapHoldHandler.DapHoldEndPayload(true));
                  ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
                  ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
                  if (hfP != null) {
                     PoseNetworking.broadcastAnimState(hfP, 41);
                  }

                  if (dapP != null) {
                     PoseNetworking.broadcastAnimState(dapP, 41);
                  }

                  pairStartTime.put(hfId, System.currentTimeMillis() + 100L - 1042L);
               }
            }
         }
      }
   }

   private static void tryJoinGroup(ServerPlayer player, long now) {
      UUID id = player.getUUID();

      for (UUID hfId : looping) {
         UUID dapId = activePairs.get(hfId);
         if (!hfId.equals(id) && (dapId == null || !dapId.equals(id))) {
            Vec3 mid = getGroupMidpoint(hfId, player.level().getServer());
            if (!(player.position().distanceTo(mid) > 2.5)) {
               addGroupJoiner(player, hfId);
               return;
            }
         }
      }
   }

   private static void addGroupJoiner(ServerPlayer joiner, UUID hfId) {
      UUID id = joiner.getUUID();
      MinecraftServer server = joiner.level().getServer();
      groupJoiners.computeIfAbsent(hfId, k -> new HashSet<>()).add(id);
      joinerGroup.put(id, hfId);
      joinerJLast.put(id, System.currentTimeMillis());
      sendFreeze(server, id, true);
      PoseNetworking.broadcastAnimState(joiner, 38);
      int total = 2 + groupJoiners.get(hfId).size();
      DapHoldHandler.GroupJoinedPayload pkt = new DapHoldHandler.GroupJoinedPayload(id, hfId, total);
      sendToAll(server, pkt);
      faceGroupCenter(hfId, server);
      joiner.sendOverlayMessage(Component.literal("§a§l⚡ JOINED GROUP DAP! (" + total + " players)"));
      ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
      if (hfP != null) {
         hfP.sendOverlayMessage(Component.literal("§e§l+" + joiner.getName().getString() + " joined! (" + total + " total)"));
      }
   }

   private static void logGroupRelease(UUID id, UUID hfId, MinecraftServer server) {
      if (server != null) {
         releasedSet.computeIfAbsent(hfId, k -> new HashSet<>()).add(id);
         if (!releaseFirst.containsKey(hfId)) {
            releaseFirst.put(hfId, System.currentTimeMillis());
         }

         checkGroupRelease(hfId, server);
      }
   }

   private static void checkGroupRelease(UUID hfId, MinecraftServer server) {
      Set<UUID> joiners = groupJoiners.getOrDefault(hfId, Collections.emptySet());
      int total = 2 + joiners.size();
      int released = releasedSet.getOrDefault(hfId, Collections.emptySet()).size();
      long elapsed = System.currentTimeMillis() - releaseFirst.getOrDefault(hfId, Long.MAX_VALUE);
      if (released >= total) {
         doGroupResult(hfId, server, elapsed <= 500L);
      }
   }

   private static void doGroupResult(UUID hfId, MinecraftServer server, boolean perfect) {
      UUID dapId = activePairs.get(hfId);
      Set<UUID> joiners = new HashSet<>(groupJoiners.getOrDefault(hfId, Collections.emptySet()));
      int memberCount = 2 + joiners.size();
      List<ServerPlayer> all = new ArrayList<>();
      ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
      ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
      if (hfP != null) {
         all.add(hfP);
      }

      if (dapP != null) {
         all.add(dapP);
      }

      for (UUID jId : joiners) {
         ServerPlayer jp = server.getPlayerList().getPlayer(jId);
         if (jp != null) {
            all.add(jp);
         }
      }

      Vec3 center = all.stream().<Vec3>map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / Math.max(1, all.size()));
      ServerLevel world = hfP != null ? hfP.level() : server.overworld();
      if (perfect) {
         for (ServerPlayer p : all) {
            PoseNetworking.broadcastAnimState(p, 68);
         }

         for (ServerPlayer p : all) {
            sendFreeze(server, p.getUUID(), false);
         }

         List<ServerPlayer> allFinal = all;
         Vec3 centerFinal = center;
         ServerLevel worldFinal = world;
         int mc = memberCount;
         new Thread(
               () -> {
                  try {
                     Thread.sleep(1670L);
                  } catch (InterruptedException var6x) {
                  }

                  server.execute(
                     () -> {
                        for (ServerPlayer p : allFinal) {
                           if (p.isAlive()) {
                              p.push(0.0, 0.4 + mc * 0.1, 0.0);
                              p.syncVelocity = true;
                              p.addEffect(new MobEffectInstance(MobEffects.SPEED, 120, Math.min(2, mc - 1)));
                              p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 120, 0));
                              p.sendOverlayMessage(Component.literal("§6§l✨ PERFECT GROUP DAP! §e" + mc + " players!"));
                           }
                        }

                        for (int i = 0; i < mc * 3; i++) {
                           double ox = (worldFinal.getRandom().nextDouble() - 0.5) * 3.0;
                           double oz = (worldFinal.getRandom().nextDouble() - 0.5) * 3.0;
                           worldFinal.sendParticles(
                              ParticleTypes.FIREWORK, centerFinal.x + ox, centerFinal.y + 2.0 + i * 0.5, centerFinal.z + oz, 6, 0.3, 0.1, 0.3, 0.12
                           );
                        }

                        worldFinal.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, centerFinal.x, centerFinal.y + 1.5, centerFinal.z, mc * 5, 0.6, 0.6, 0.6, 0.3);
                        worldFinal.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centerFinal.x, centerFinal.y + 1.0, centerFinal.z, mc, 0.4, 0.3, 0.4, 0.0);
                        worldFinal.playSound(
                           null, centerFinal.x, centerFinal.y, centerFinal.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 1.5F, 0.9F + mc * 0.05F
                        );
                        worldFinal.playSound(
                           null, centerFinal.x, centerFinal.y, centerFinal.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2F, 0.8F
                        );
                     }
                  );
               }
            )
            .start();
      } else {
         for (ServerPlayer p : all) {
            Vec3 dir = p.position().subtract(center).normalize();
            if (dir.lengthSqr() < 0.01) {
               dir = new Vec3(1.0, 0.0, 0.0);
            }

            p.push(dir.x * 0.9, 0.3, dir.z * 0.9);
            p.syncVelocity = true;
            p.sendOverlayMessage(Component.literal("§c❌ Release not synced!"));
         }

         world.sendParticles(ParticleTypes.POOF, center.x, center.y + 1.0, center.z, 12, 0.4, 0.3, 0.4, 0.05);
         world.playSound(null, center.x, center.y, center.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.8F);
      }

      sendToAll(server, new DapHoldHandler.GroupResultPayload(perfect, memberCount));
      if (!perfect) {
         sendToAll(server, new DapHoldHandler.DapHoldEndPayload(false));
         if (hfP != null) {
            PoseNetworking.broadcastAnimState(hfP, 41);
         }

         if (dapP != null) {
            PoseNetworking.broadcastAnimState(dapP, 41);
         }

         for (UUID jId : joiners) {
            ServerPlayer jp = server.getPlayerList().getPlayer(jId);
            if (jp != null) {
               PoseNetworking.broadcastAnimState(jp, 41);
            }
         }
      }

      for (UUID jId : joiners) {
         sendFreeze(server, jId, false);
         joinerGroup.remove(jId);
         joinerJLast.remove(jId);
      }

      groupJoiners.remove(hfId);
      releaseFirst.remove(hfId);
      releasedSet.remove(hfId);
      looping.remove(hfId);
      loopStartTime.remove(hfId);
      if (dapId != null) {
         doUnfreeze(server, hfId, dapId);
      }

      pairStartTime.put(hfId, System.currentTimeMillis() + 100L - 1042L);
   }

   private static Vec3 getGroupMidpoint(UUID hfId, MinecraftServer server) {
      List<Vec3> positions = new ArrayList<>();
      ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
      UUID dapId = activePairs.get(hfId);
      ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
      if (hfP != null) {
         positions.add(hfP.position());
      }

      if (dapP != null) {
         positions.add(dapP.position());
      }

      for (UUID jId : groupJoiners.getOrDefault(hfId, Collections.emptySet())) {
         ServerPlayer jp = server.getPlayerList().getPlayer(jId);
         if (jp != null) {
            positions.add(jp.position());
         }
      }

      return positions.isEmpty() ? Vec3.ZERO : positions.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / positions.size());
   }

   private static void faceGroupCenter(UUID hfId, MinecraftServer server) {
      List<ServerPlayer> members = new ArrayList<>();
      ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
      UUID dapId = activePairs.get(hfId);
      ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
      if (hfP != null) {
         members.add(hfP);
      }

      if (dapP != null) {
         members.add(dapP);
      }

      for (UUID jId : groupJoiners.getOrDefault(hfId, Collections.emptySet())) {
         ServerPlayer jp = server.getPlayerList().getPlayer(jId);
         if (jp != null) {
            members.add(jp);
         }
      }

      if (members.size() >= 2) {
         Vec3 center = members.stream().<Vec3>map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / members.size());

         for (ServerPlayer p : members) {
            Vec3 diff = center.subtract(p.position());
            if (!(diff.horizontalDistanceSqr() < 0.001)) {
               float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
               p.setYRot(yaw);
               p.setYBodyRot(yaw);
               p.setYHeadRot(yaw);
            }
         }
      }
   }

   public static void forceUnfreeze(MinecraftServer server, UUID id) {
      sendFreeze(server, id, false);
   }

   public static UUID getPairHfId(UUID id) {
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

   private static UUID getPairHfIdPrivate(UUID id) {
      return getPairHfId(id);
   }

   private static boolean isHoldingJ(UUID id, long now) {
      Long last = jHoldLastTick.get(id);
      return last != null && now - last < 200L;
   }

   private static void smoothTP(ServerPlayer hf, ServerPlayer dap, UUID hfId) {
      double dist = hf.position().distanceTo(dap.position());
      if (dist <= 1.5) {
         tpComplete.add(hfId);
         faceEachOther(hf, dap);
      } else {
         double move = Math.min(0.08, (dist - 1.5) / 2.0);
         Vec3 dir = dap.position().subtract(hf.position()).normalize();
         Vec3 newHf = hf.position().add(dir.scale(move));
         Vec3 newDap = dap.position().add(dir.reverse().scale(move));
         hf.teleportTo(hf.level(), newHf.x, newHf.y, newHf.z, Set.of(), hf.getYRot(), hf.getXRot(), false);
         dap.teleportTo(dap.level(), newDap.x, newDap.y, newDap.z, Set.of(), dap.getYRot(), dap.getXRot(), false);
      }
   }

   private static void faceEachOther(ServerPlayer a, ServerPlayer b) {
      Vec3 diff = b.position().subtract(a.position());
      float yawA = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
      a.teleportTo(a.level(), a.getX(), a.getY(), a.getZ(), Set.of(), yawA, a.getXRot(), false);
      b.teleportTo(b.level(), b.getX(), b.getY(), b.getZ(), Set.of(), yawA + 180.0F, b.getXRot(), false);
   }

   private static void spawnHandStand(ServerPlayer hf, ServerPlayer dap) {
      ServerLevel world = hf.level();
      Vec3 mid = hf.position().add(0.0, 1.4, 0.0).add(dap.position().add(0.0, 1.4, 0.0)).scale(0.5);
      ArmorStand stand = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, world);
      stand.setPos(mid.x, mid.y, mid.z);
      stand.setInvisible(true);
      stand.setNoGravity(true);
      stand.setPermanentlyInvulnerable(true);
      stand.setSilent(true);
      world.addFreshEntity(stand);
      handStands.put(hf.getUUID(), stand);
   }

   private static void updateHandStand(ServerPlayer hf, ServerPlayer dap, UUID hfId) {
      ArmorStand stand = handStands.get(hfId);
      if (stand != null && !stand.isRemoved()) {
         Vec3 mid = hf.position().add(0.0, 1.4, 0.0).add(dap.position().add(0.0, 1.4, 0.0)).scale(0.5);
         stand.setPos(mid.x, mid.y, mid.z);
      }
   }

   private static void spawnImpactParticles(ServerPlayer hf, ServerPlayer dap, UUID hfId) {
      ServerLevel world = hf.level();
      ArmorStand stand = handStands.get(hfId);
      double x;
      double y;
      double z;
      if (stand != null && !stand.isRemoved()) {
         x = stand.getX();
         y = stand.getY();
         z = stand.getZ();
      } else {
         Vec3 mid = hf.position().add(dap.position()).scale(0.5).add(0.0, 1.4, 0.0);
         x = mid.x;
         y = mid.y;
         z = mid.z;
      }

      world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), x, y, z, 3, 0.0, 0.0, 0.0, 0.0);
      world.sendParticles(ParticleTypes.END_ROD, x, y, z, 40, 0.4, 0.4, 0.4, 0.15);
      world.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 80, 0.6, 0.6, 0.6, 0.08);
      world.sendParticles(ParticleTypes.CLOUD, x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
      world.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 5, 0.3, 0.3, 0.3, 0.0);
      double groundY = hf.getY() + 0.1;

      for (double angle = 0.0; angle < 360.0; angle += 8.0) {
         double rad = Math.toRadians(angle);

         for (double r = 0.5; r <= 3.0; r += 0.5) {
            world.sendParticles(ParticleTypes.END_ROD, x + Math.cos(rad) * r, groundY, z + Math.sin(rad) * r, 2, 0.05, 0.05, 0.05, 0.02);
         }
      }

      world.playSound(null, x, y, z, ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0F, 1.0F);
   }

   private static void sendFreeze(MinecraftServer server, UUID targetId, boolean freeze) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(p, new DapHoldHandler.DapHoldFreezePayload(targetId, freeze));
      }
   }

   private static void doUnfreeze(MinecraftServer server, UUID hfId, UUID dapId) {
      sendFreeze(server, hfId, false);
      sendFreeze(server, dapId, false);
      DapSessionManager.removeSession(hfId);
      ArmorStand stand = handStands.remove(hfId);
      if (stand != null && !stand.isRemoved()) {
         stand.discard();
      }
   }

   private static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   private static void cleanupPair(UUID hfId, MinecraftServer server) {
      UUID dapId = activePairs.remove(hfId);
      pairStartTime.remove(hfId);
      windowOpen.remove(hfId);
      impactFired.remove(hfId);
      looping.remove(hfId);
      endingAnimation.remove(hfId);
      tpComplete.remove(hfId);
      loopStartTime.remove(hfId);
      jHoldLastTick.remove(hfId);
      if (dapId != null) {
         jHoldLastTick.remove(dapId);
      }

      ArmorStand stand = handStands.remove(hfId);
      if (stand != null && !stand.isRemoved()) {
         stand.discard();
      }

      Set<UUID> joiners = groupJoiners.remove(hfId);
      if (joiners != null) {
         for (UUID jId : joiners) {
            joinerGroup.remove(jId);
            joinerJLast.remove(jId);
            sendFreeze(server, jId, false);
            ServerPlayer jp = server.getPlayerList().getPlayer(jId);
            if (jp != null) {
               PoseNetworking.broadcastAnimState(jp, 41);
            }
         }
      }

      releaseFirst.remove(hfId);
      releasedSet.remove(hfId);
      DapSessionManager.removeSession(hfId);
      sendFreeze(server, hfId, false);
      if (dapId != null) {
         sendFreeze(server, dapId, false);
      }

      long now = System.currentTimeMillis();
      ChargedDapHandler.cooldowns.put(hfId, now + 1000L);
      if (dapId != null) {
         ChargedDapHandler.cooldowns.put(dapId, now + 1000L);
      }

      HighFiveHandler.highFiveCooldown.put(hfId, now);
      if (dapId != null) {
         HighFiveHandler.highFiveCooldown.put(dapId, now);
      }

      System.out.println("[DapHold] Cleaned up: " + hfId + " (1s cooldown applied)");
   }

   public static boolean tryDetect(ServerPlayer player, ServerPlayer partner) {
      boolean playerHF = HighFiveHandler.hasHandRaised(player.getUUID());
      boolean partnerHF = HighFiveHandler.hasHandRaised(partner.getUUID());
      if (playerHF && !partnerHF) {
         startDapHold(player, partner);
         return true;
      } else if (partnerHF && !playerHF) {
         startDapHold(partner, player);
         return true;
      } else {
         return false;
      }
   }

   public static boolean isInDapHold(UUID playerId) {
      return activePairs.containsKey(playerId) || activePairs.containsValue(playerId) || joinerGroup.containsKey(playerId);
   }

   public record DapHoldEndPayload(boolean wasLooping) implements CustomPacketPayload {
      public static final Type<DapHoldHandler.DapHoldEndPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_end"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.DapHoldEndPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> buf.writeBoolean(p.wasLooping()), buf -> new DapHoldHandler.DapHoldEndPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record DapHoldFreezePayload(UUID playerId, boolean frozen) implements CustomPacketPayload {
      public static final Type<DapHoldHandler.DapHoldFreezePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_freeze"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.DapHoldFreezePayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeUUID(p.playerId());
         buf.writeBoolean(p.frozen());
      }, buf -> new DapHoldHandler.DapHoldFreezePayload(buf.readUUID(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record DapHoldJHoldPayload() implements CustomPacketPayload {
      public static final Type<DapHoldHandler.DapHoldJHoldPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_jhold"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.DapHoldJHoldPayload> CODEC = StreamCodec.unit(new DapHoldHandler.DapHoldJHoldPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record DapHoldJReleasePayload() implements CustomPacketPayload {
      public static final Type<DapHoldHandler.DapHoldJReleasePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_jrelease"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.DapHoldJReleasePayload> CODEC = StreamCodec.unit(
         new DapHoldHandler.DapHoldJReleasePayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record DapHoldLoopPayload(boolean looping) implements CustomPacketPayload {
      public static final Type<DapHoldHandler.DapHoldLoopPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_loop"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.DapHoldLoopPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> buf.writeBoolean(p.looping()), buf -> new DapHoldHandler.DapHoldLoopPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record DapHoldStartPayload(UUID playerId, UUID partnerId, int role) implements CustomPacketPayload {
      public static final Type<DapHoldHandler.DapHoldStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_start"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.DapHoldStartPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeUUID(p.playerId());
         buf.writeUUID(p.partnerId());
         buf.writeInt(p.role());
      }, buf -> new DapHoldHandler.DapHoldStartPayload(buf.readUUID(), buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record DapHoldWindowPayload(boolean open) implements CustomPacketPayload {
      public static final Type<DapHoldHandler.DapHoldWindowPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_window"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.DapHoldWindowPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> buf.writeBoolean(p.open()), buf -> new DapHoldHandler.DapHoldWindowPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record GroupJoinPayload() implements CustomPacketPayload {
      public static final Type<DapHoldHandler.GroupJoinPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_group_join_req"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.GroupJoinPayload> CODEC = StreamCodec.unit(new DapHoldHandler.GroupJoinPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record GroupJoinedPayload(UUID joinerId, UUID hfId, int memberCount) implements CustomPacketPayload {
      public static final Type<DapHoldHandler.GroupJoinedPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_group_join"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.GroupJoinedPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeUUID(p.joinerId());
         buf.writeUUID(p.hfId());
         buf.writeInt(p.memberCount());
      }, buf -> new DapHoldHandler.GroupJoinedPayload(buf.readUUID(), buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record GroupResultPayload(boolean perfect, int memberCount) implements CustomPacketPayload {
      public static final Type<DapHoldHandler.GroupResultPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "daphold_group_result"));
      public static final StreamCodec<FriendlyByteBuf, DapHoldHandler.GroupResultPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeBoolean(p.perfect());
         buf.writeInt(p.memberCount());
      }, buf -> new DapHoldHandler.GroupResultPayload(buf.readBoolean(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
