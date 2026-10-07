package com.cooptest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.phys.Vec3;

public class DuoPoseHandler {
   private static final long WINDOW_MS = 3000L;
   private static final long HOLD_REQUIRED_MS = 200L;
   private static final double RANGE = 5.0;
   private static final long DUO_POSE_MS = 1125L;
   private static final long PARTICLE_HIT_MS = 670L;
   private static final long AURA_INTERVAL_MS = 300L;
   private static final long DUO_POSE_END_MS = 1625L;
   private static final long RELEASE_GRACE_MS = 500L;
   public static final int ANIM_DUO_POSE = 95;
   public static final int ANIM_DUO_POSE_IDLE = 96;
   public static final int ANIM_DUO_POSE_END = 97;
   public static final int ANIM_NONE = 0;
   private static final Map<UUID, DuoPoseHandler.DuoSession> sessions = new HashMap<>();
   private static final Map<UUID, DuoPoseHandler.DuoSession> playerIndex = new HashMap<>();
   private static final Map<UUID, Long> windowOpenAt = new HashMap<>();
   private static final Map<UUID, UUID> windowPartner = new HashMap<>();
   private static final Map<UUID, Long> fHoldSince = new HashMap<>();
   private static final Map<UUID, Long> huddleBlockUntil = new HashMap<>();

   public static boolean isBlockingHuddle(UUID id) {
      Long until = huddleBlockUntil.get(id);
      if (until == null) {
         return false;
      } else if (System.currentTimeMillis() > until) {
         huddleBlockUntil.remove(id);
         return false;
      } else {
         return true;
      }
   }

   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(DuoPoseHandler.DuoPoseFreezePayload.ID, DuoPoseHandler.DuoPoseFreezePayload.CODEC);
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(DuoPoseHandler::tick);
   }

   public static void onDapEnded(ServerPlayer p1, ServerPlayer p2) {
      openWindow(p1, p2);
   }

   public static void onComboEnded(ServerPlayer p1, ServerPlayer p2) {
      new Thread(() -> {
         try {
            Thread.sleep(100L);
         } catch (InterruptedException var3) {
         }

         if (p1.level().getServer() != null) {
            p1.level().getServer().execute(() -> openWindow(p1, p2));
         }
      }).start();
   }

   private static void openWindow(ServerPlayer p1, ServerPlayer p2) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      if (!playerIndex.containsKey(id1) && !playerIndex.containsKey(id2)) {
         long now = System.currentTimeMillis();
         windowOpenAt.put(id1, now);
         windowOpenAt.put(id2, now);
         windowPartner.put(id1, id2);
         windowPartner.put(id2, id1);
         huddleBlockUntil.put(id1, now + 3000L);
         huddleBlockUntil.put(id2, now + 3000L);
      }
   }

   public static void onFHold(ServerPlayer player, boolean holding) {
      UUID id = player.getUUID();
      DuoPoseHandler.DuoSession active = playerIndex.get(id);
      if (active != null && active.stage == DuoPoseHandler.Stage.IDLE) {
         if (holding) {
            active.holdingF.add(id);
         } else {
            active.holdingF.remove(id);
            if (active.firstRelease == null) {
               active.firstRelease = id;
               active.firstReleaseMs = System.currentTimeMillis();
            }
         }
      } else if (windowOpenAt.containsKey(id)) {
         if (holding) {
            fHoldSince.putIfAbsent(id, System.currentTimeMillis());
         } else {
            fHoldSince.remove(id);
         }
      }
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      List<UUID> expiredWindows = new ArrayList<>();
      Set<UUID> processed = new HashSet<>();

      for (UUID id : new ArrayList<>(windowOpenAt.keySet())) {
         if (!processed.contains(id)) {
            Long openAt = windowOpenAt.get(id);
            if (openAt != null) {
               if (now - openAt > 3000L) {
                  expiredWindows.add(id);
                  UUID partner = windowPartner.get(id);
                  if (partner != null) {
                     expiredWindows.add(partner);
                  }
               } else {
                  UUID partnerId = windowPartner.get(id);
                  if (partnerId != null) {
                     processed.add(id);
                     processed.add(partnerId);
                     Long holdA = fHoldSince.get(id);
                     Long holdB = fHoldSince.get(partnerId);
                     if (holdA != null && holdB != null && now - holdA >= 200L && now - holdB >= 200L) {
                        ServerPlayer p1 = server.getPlayerList().getPlayer(id);
                        ServerPlayer p2 = server.getPlayerList().getPlayer(partnerId);
                        if (p1 != null && p2 != null) {
                           if (!(p1.position().distanceTo(p2.position()) > 5.0)) {
                              expiredWindows.add(id);
                              expiredWindows.add(partnerId);
                              fHoldSince.remove(id);
                              fHoldSince.remove(partnerId);
                              startDuoPose(server, p1, p2, now);
                           }
                        } else {
                           expiredWindows.add(id);
                           expiredWindows.add(partnerId);
                        }
                     }
                  }
               }
            }
         }
      }

      expiredWindows.forEach(idx -> {
         windowOpenAt.remove(idx);
         windowPartner.remove(idx);
         fHoldSince.remove(idx);
      });
      Set<DuoPoseHandler.DuoSession> seen = Collections.newSetFromMap(new IdentityHashMap<>());

      for (DuoPoseHandler.DuoSession s : new ArrayList<>(sessions.values())) {
         if (seen.add(s)) {
            tickSession(s, server, now);
         }
      }
   }

   private static void startDuoPose(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, long now) {
      DuoPoseHandler.DuoSession session = new DuoPoseHandler.DuoSession(p1.getUUID(), p2.getUUID());
      sessions.put(p1.getUUID(), session);
      playerIndex.put(p1.getUUID(), session);
      playerIndex.put(p2.getUUID(), session);
      sendFreeze(p1, true);
      sendFreeze(p2, true);
      PoseNetworking.broadcastAnimState(p1, 95);
      PoseNetworking.broadcastAnimState(p2, 95);
      session.holdingF.add(p1.getUUID());
      session.holdingF.add(p2.getUUID());
      DuoPoseHandler.DuoSession fs = session;
      new Thread(() -> {
         try {
            Thread.sleep(670L);
         } catch (InterruptedException ignored) {
            return;
         }

         server.execute(() -> {
            if (sessions.containsKey(fs.p1)) {
               spawnArmParticles(server, fs);
            }
         });
      }).start();
      new Thread(() -> {
         try {
            Thread.sleep(1125L);
         } catch (InterruptedException ignored) {
            return;
         }

         server.execute(() -> {
            if (sessions.containsKey(fs.p1)) {
               fs.stage = DuoPoseHandler.Stage.IDLE;
               ServerPlayer a = server.getPlayerList().getPlayer(fs.p1);
               ServerPlayer b = server.getPlayerList().getPlayer(fs.p2);
               if (a != null) {
                  PoseNetworking.broadcastAnimState(a, 96);
               }

               if (b != null) {
                  PoseNetworking.broadcastAnimState(b, 96);
               }
            }
         });
      }).start();
   }

   private static void tickSession(DuoPoseHandler.DuoSession s, MinecraftServer server, long now) {
      ServerPlayer p1 = server.getPlayerList().getPlayer(s.p1);
      ServerPlayer p2 = server.getPlayerList().getPlayer(s.p2);
      if (p1 != null && p2 != null) {
         if (s.stage == DuoPoseHandler.Stage.IDLE) {
            p1.setDeltaMovement(Vec3.ZERO);
            p1.hurtMarked = true;
            p2.setDeltaMovement(Vec3.ZERO);
            p2.hurtMarked = true;
            if (now - s.lastAuraMs >= 300L) {
               s.lastAuraMs = now;
               spawnAuraParticles(server, s);
            }

            boolean p1Holds = s.holdingF.contains(s.p1);
            boolean p2Holds = s.holdingF.contains(s.p2);
            if (!p1Holds && !p2Holds) {
               boolean simul = s.firstRelease != null && now - s.firstReleaseMs <= 500L;
               s.simultaneous = simul;
               endDuoPose(server, s, p1, p2);
            } else if ((!p1Holds || !p2Holds) && s.firstRelease != null && now - s.firstReleaseMs > 500L) {
               s.simultaneous = false;
               endDuoPose(server, s, p1, p2);
            }
         }
      } else {
         cleanupSession(s);
      }
   }

   private static void endDuoPose(MinecraftServer server, DuoPoseHandler.DuoSession s, ServerPlayer p1, ServerPlayer p2) {
      s.stage = DuoPoseHandler.Stage.ENDING;
      PoseNetworking.broadcastAnimState(p1, 97);
      PoseNetworking.broadcastAnimState(p2, 97);
      if (s.simultaneous) {
         Vec3 mid = p1.position().add(p2.position()).scale(0.5).add(0.0, 1.0, 0.0);
         ServerLevel world = p1.level();
         world.sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 30, 0.5, 0.5, 0.5, 0.15);
         world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), mid.x, mid.y, mid.z, 5, 0.3, 0.3, 0.3, 0.0);
         world.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 20, 0.4, 0.4, 0.4, 0.2);
         world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5F, 1.2F);
         world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
         p1.sendOverlayMessage(Component.literal("§6§l✨ Perfect sync!"));
         p2.sendOverlayMessage(Component.literal("§6§l✨ Perfect sync!"));
      }

      new Thread(() -> {
         try {
            Thread.sleep(1625L);
         } catch (InterruptedException var3) {
         }

         server.execute(() -> {
            ServerPlayer a = server.getPlayerList().getPlayer(s.p1);
            ServerPlayer b = server.getPlayerList().getPlayer(s.p2);
            if (a != null) {
               PoseNetworking.broadcastAnimState(a, 0);
               sendFreeze(a, false);
            }

            if (b != null) {
               PoseNetworking.broadcastAnimState(b, 0);
               sendFreeze(b, false);
            }

            cleanupSession(s);
         });
      }).start();
   }

   private static void spawnArmParticles(MinecraftServer server, DuoPoseHandler.DuoSession s) {
      for (UUID id : new UUID[]{s.p1, s.p2}) {
         ServerPlayer p = server.getPlayerList().getPlayer(id);
         if (p != null) {
            ServerLevel world = p.level();
            Vec3 look = p.getViewVector(1.0F);
            Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
            Vec3 armTip = p.getEyePosition().add(right.scale(0.55)).add(look.scale(0.4)).subtract(0.0, 0.4, 0.0);
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, armTip.x, armTip.y, armTip.z, 10, 0.12, 0.12, 0.12, 0.1);
            world.sendParticles(ParticleTypes.CRIT, armTip.x, armTip.y, armTip.z, 8, 0.1, 0.1, 0.1, 0.15);
            world.playSound(null, armTip.x, armTip.y, armTip.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.3F);
         }
      }
   }

   private static void spawnAuraParticles(MinecraftServer server, DuoPoseHandler.DuoSession s) {
      for (UUID id : new UUID[]{s.p1, s.p2}) {
         ServerPlayer p = server.getPlayerList().getPlayer(id);
         if (p != null) {
            ServerLevel world = p.level();
            Vec3 pos = p.position().add(0.0, 1.0, 0.0);
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 3, 0.3, 0.5, 0.3, 0.02);
            world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 0.25, 0.6, 0.25, 0.01);
         }
      }
   }

   private static void cleanupSession(DuoPoseHandler.DuoSession s) {
      sessions.remove(s.p1);
      playerIndex.remove(s.p1);
      playerIndex.remove(s.p2);
   }

   public static void onPlayerLeave(UUID id) {
      UUID partner = windowPartner.remove(id);
      if (partner != null) {
         windowOpenAt.remove(partner);
         windowPartner.remove(partner);
         fHoldSince.remove(partner);
      }

      windowOpenAt.remove(id);
      fHoldSince.remove(id);
      DuoPoseHandler.DuoSession s = playerIndex.remove(id);
      if (s != null) {
         s.stage = DuoPoseHandler.Stage.ENDING;
         sessions.remove(s.p1);
         playerIndex.remove(s.p1 == id ? s.p2 : s.p1);
      }
   }

   public static boolean isInDuoPose(UUID id) {
      return playerIndex.containsKey(id);
   }

   private static void sendFreeze(ServerPlayer player, boolean frozen) {
      ServerPlayNetworking.send(player, new DuoPoseHandler.DuoPoseFreezePayload(player.getUUID(), frozen));
   }

   public record DuoPoseFreezePayload(UUID playerId, boolean frozen) implements CustomPacketPayload {
      public static final Type<DuoPoseHandler.DuoPoseFreezePayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "duo_pose_freeze"));
      public static final StreamCodec<FriendlyByteBuf, DuoPoseHandler.DuoPoseFreezePayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeUUID(v.playerId());
         buf.writeBoolean(v.frozen());
      }, buf -> new DuoPoseHandler.DuoPoseFreezePayload(buf.readUUID(), buf.readBoolean()));

      public Type<DuoPoseHandler.DuoPoseFreezePayload> type() {
         return ID;
      }
   }

   private static class DuoSession {
      final UUID p1;
      final UUID p2;
      DuoPoseHandler.Stage stage = DuoPoseHandler.Stage.ENTRY;
      final Set<UUID> holdingF = new HashSet<>();
      UUID firstRelease = null;
      long firstReleaseMs = 0L;
      boolean simultaneous = false;
      long lastAuraMs = 0L;

      DuoSession(UUID p1, UUID p2) {
         this.p1 = p1;
         this.p2 = p2;
      }

      boolean bothHoldingF() {
         return this.holdingF.contains(this.p1) && this.holdingF.contains(this.p2);
      }
   }

   private enum Stage {
      ENTRY,
      IDLE,
      ENDING;
   }
}
