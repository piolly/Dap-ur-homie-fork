package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class SikeFollowUpHandler {
   private static final double TP_TARGET_DIST = 0.9;
   private static final double TP_LERP = 0.45;
   private static final int TP_MAX_TICKS = 60;
   private static final long QTE_WINDOW_MS = 1000L;
   private static final long DMG_START_MS = 290L;
   private static final long DMG_END_MS = 2500L;
   private static final long FREE_MS = 2960L;
   private static final long DMG_INTERVAL_MS = 200L;
   private static final long SPAM_INTERVAL_MS = 90L;
   private static final float DMG_PER_TICK = 2.0F;
   private static final float LAUNCH_POWER = 1.1F;
   private static final int ANIM_NOYA = 94;
   private static final Map<UUID, SikeFollowUpHandler.NoyaSession> sessions = new HashMap<>();
   private static final Map<UUID, UUID> pendingQTE = new HashMap<>();
   private static final Map<UUID, Long> qteOpenedAt = new HashMap<>();

   public static void register() {
      PayloadTypeRegistry.clientboundPlay().register(SikeFollowUpHandler.NoyaFlickerPayload.ID, SikeFollowUpHandler.NoyaFlickerPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SikeFollowUpHandler.SikeWindowPayload.ID, SikeFollowUpHandler.SikeWindowPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(SikeFollowUpHandler.SikePressPayload.ID, SikeFollowUpHandler.SikePressPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(SikeFollowUpHandler.SikePressPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> onSikePress(player));
      });
      ServerTickEvents.END_SERVER_TICK.register(SikeFollowUpHandler::tick);
   }

   public static void onSikeExecuted(ServerPlayer siker, ServerPlayer victim) {
      UUID sid = siker.getUUID();
      UUID vid = victim.getUUID();
      pendingQTE.put(sid, vid);
      qteOpenedAt.put(sid, System.currentTimeMillis());
      ServerPlayNetworking.send(siker, new SikeFollowUpHandler.SikeWindowPayload(true, 1000, 0));
   }

   private static void onSikePress(ServerPlayer player) {
      UUID sid = player.getUUID();
      UUID vid = pendingQTE.get(sid);
      if (vid != null) {
         pendingQTE.remove(sid);
         qteOpenedAt.remove(sid);
         ServerPlayNetworking.send(player, new SikeFollowUpHandler.SikeWindowPayload(false, 0, 0));
         MinecraftServer server = player.level().getServer();
         if (server != null) {
            ServerPlayer victim = server.getPlayerList().getPlayer(vid);
            if (victim != null) {
               startNoyaSequence(player, victim);
            }
         }
      }
   }

   private static void startNoyaSequence(ServerPlayer siker, ServerPlayer victim) {
      SikeFollowUpHandler.NoyaSession session = new SikeFollowUpHandler.NoyaSession(siker.getUUID(), victim.getUUID());
      sessions.put(siker.getUUID(), session);
      Vec3 toVictim = victim.position().subtract(siker.position());
      float yawAwayFromVictim = 0.0F;
      if (toVictim.lengthSqr() > 0.001) {
         yawAwayFromVictim = (float)Math.toDegrees(Math.atan2(-toVictim.x, toVictim.z)) + 180.0F;
         siker.setYRot(yawAwayFromVictim);
         siker.setYBodyRot(yawAwayFromVictim);
         siker.setYHeadRot(yawAwayFromVictim);
         siker.yBodyRotO = yawAwayFromVictim;
      }

      freezePlayer(siker, true);
      freezePlayer(victim, true);
      StrongSlapHandler.sendLookLockAtYaw(siker, yawAwayFromVictim);
      siker.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      List<UUID> expired = new ArrayList<>();

      for (Entry<UUID, Long> entry : qteOpenedAt.entrySet()) {
         if (now - entry.getValue() > 1000L) {
            expired.add(entry.getKey());
         }
      }

      expired.forEach(id -> {
         pendingQTE.remove(id);
         qteOpenedAt.remove(id);
         ServerPlayer sikerx = server.getPlayerList().getPlayer(id);
         if (sikerx != null) {
            ServerPlayNetworking.send(sikerx, new SikeFollowUpHandler.SikeWindowPayload(false, 0, 0));
         }
      });
      List<UUID> done = new ArrayList<>();

      for (Entry<UUID, SikeFollowUpHandler.NoyaSession> entry : new ArrayList<>(sessions.entrySet())) {
         SikeFollowUpHandler.NoyaSession s = entry.getValue();
         if (!s.cancelled && !s.animRunning) {
            ServerPlayer siker = server.getPlayerList().getPlayer(s.sikerId);
            ServerPlayer victim = server.getPlayerList().getPlayer(s.victimId);
            if (siker != null && victim != null) {
               siker.setDeltaMovement(Vec3.ZERO);
               siker.syncVelocity = true;
               victim.setDeltaMovement(Vec3.ZERO);
               victim.syncVelocity = true;
               double dist = siker.position().distanceTo(victim.position());
               if (s.tpTick <= 60 && !(dist <= 0.9)) {
                  Vec3 cur = siker.position();
                  Vec3 target = victim.position();
                  double nx = cur.x + (target.x - cur.x) * 0.45;
                  double nz = cur.z + (target.z - cur.z) * 0.45;
                  siker.teleportTo(nx, cur.y, nz);
                  s.tpTick++;
               } else {
                  Vec3 toVictim = victim.position().subtract(siker.position());
                  if (toVictim.lengthSqr() > 0.001) {
                     Vec3 dir = toVictim.normalize();
                     Vec3 finalPos = victim.position().subtract(dir.scale(0.9));
                     siker.teleportTo(finalPos.x, siker.getY(), finalPos.z);
                  }

                  s.animRunning = true;
                  done.add(entry.getKey());
                  launchAnimation(server, s, siker, victim);
               }
            } else {
               s.cancelled = true;
               done.add(entry.getKey());
            }
         }
      }

      done.forEach(sessions::remove);
   }

   private static void launchAnimation(MinecraftServer server, SikeFollowUpHandler.NoyaSession session, ServerPlayer siker, ServerPlayer victim) {
      siker.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
      siker.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
      PoseNetworking.broadcastAnimState(siker, 94);
      UUID sikerUUID = session.sikerId;
      UUID victimUUID = session.victimId;
      new Thread(() -> runDamageLoop(server, session, sikerUUID, victimUUID)).start();
   }

   private static void runDamageLoop(MinecraftServer server, SikeFollowUpHandler.NoyaSession session, UUID sikerUUID, UUID victimUUID) {
      try {
         Thread.sleep(290L);
      } catch (InterruptedException ignored) {
         return;
      }

      if (!session.cancelled) {
         server.execute(() -> {
            ServerPlayer v = server.getPlayerList().getPlayer(victimUUID);
            if (v != null) {
               ServerPlayNetworking.send(v, new SikeFollowUpHandler.NoyaFlickerPayload(victimUUID, true, 2210L));
            }
         });
         long windowEnd = System.currentTimeMillis() + 2210L;
         long lastDmg = 0L;
         long lastSpam = 0L;

         while (System.currentTimeMillis() < windowEnd) {
            if (session.cancelled) {
               return;
            }

            long now = System.currentTimeMillis();
            if (now - lastSpam >= 90L) {
               lastSpam = now;
               server.execute(
                  () -> {
                     if (!session.cancelled) {
                        ServerPlayer v = server.getPlayerList().getPlayer(victimUUID);
                        if (v != null) {
                           ServerLevel world = v.level();
                           Vec3 head = v.position().add(0.0, 1.7, 0.0);
                           world.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 4, 0.15, 0.1, 0.15, 0.2);
                           world.sendParticles(ParticleTypes.ENCHANTED_HIT, head.x, head.y, head.z, 3, 0.1, 0.1, 0.1, 0.1);
                           world.playSound(
                              null, head.x, head.y, head.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6F, 1.0F + (float)(Math.random() * 0.4F)
                           );
                        }
                     }
                  }
               );
            }

            if (now - lastDmg >= 200L) {
               lastDmg = now;
               server.execute(() -> {
                  if (!session.cancelled) {
                     ServerPlayer v = server.getPlayerList().getPlayer(victimUUID);
                     if (v != null) {
                        v.hurtServer(v.level(), v.level().damageSources().generic(), 2.0F);
                     }
                  }
               });
            }

            try {
               Thread.sleep(15L);
            } catch (InterruptedException ignored) {
               return;
            }
         }

         server.execute(() -> {
            if (!session.cancelled) {
               ServerPlayer v = server.getPlayerList().getPlayer(victimUUID);
               if (v != null) {
                  ServerPlayNetworking.send(v, new SikeFollowUpHandler.NoyaFlickerPayload(victimUUID, false, 0L));
               }
            }
         });

         try {
            Thread.sleep(460L);
         } catch (InterruptedException ignored) {
            return;
         }

         if (!session.cancelled) {
            server.execute(() -> {
               if (!session.cancelled) {
                  ServerPlayer siker = server.getPlayerList().getPlayer(sikerUUID);
                  ServerPlayer victim = server.getPlayerList().getPlayer(victimUUID);
                  if (victim != null) {
                     freezePlayer(victim, false);
                  }

                  if (siker != null) {
                     freezePlayer(siker, false);
                     StrongSlapHandler.sendLookLockPublic(siker, false);
                  }

                  if (victim != null) {
                     ServerLevel world = victim.level();
                     Vec3 head = victim.position().add(0.0, 1.7, 0.0);
                     world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, head.x, head.y, head.z, 3, 0.2, 0.2, 0.2, 0.0);
                     world.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 20, 0.3, 0.3, 0.3, 0.25);
                     world.sendParticles(ParticleTypes.ENCHANTED_HIT, head.x, head.y, head.z, 15, 0.25, 0.25, 0.25, 0.15);
                     world.sendParticles(ParticleTypes.SWEEP_ATTACK, head.x, head.y, head.z, 6, 0.2, 0.1, 0.2, 0.05);
                     world.playSound(null, head.x, head.y, head.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.9F);
                     world.playSound(null, head.x, head.y, head.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.6F);
                     Vec3 launchDir;
                     if (siker != null) {
                        launchDir = victim.position().subtract(siker.position()).normalize();
                        if (launchDir.lengthSqr() < 0.001) {
                           launchDir = new Vec3(1.0, 0.0, 0.0);
                        }
                     } else {
                        launchDir = victim.getViewVector(1.0F);
                     }

                     victim.setDeltaMovement(launchDir.x * 1.1F, 0.6, launchDir.z * 1.1F);
                     victim.syncVelocity = true;
                     victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 1, false, true));
                     victim.hurtServer(world, world.damageSources().generic(), 4.0F);
                  }

                  if (siker != null) {
                     PoseNetworking.broadcastAnimState(siker, 0);
                  }

                  cleanupSession(sikerUUID, victimUUID);
               }
            });
         }
      }
   }

   private static void cleanupSession(UUID sikerId, UUID victimId) {
      sessions.remove(sikerId);
      pendingQTE.remove(sikerId);
      qteOpenedAt.remove(sikerId);
   }

   public static void onPlayerLeave(UUID id) {
      SikeFollowUpHandler.NoyaSession s = sessions.remove(id);
      if (s != null) {
         s.cancelled = true;
      }

      pendingQTE.remove(id);
      qteOpenedAt.remove(id);

      for (Entry<UUID, SikeFollowUpHandler.NoyaSession> entry : new ArrayList<>(sessions.entrySet())) {
         if (entry.getValue().victimId.equals(id)) {
            entry.getValue().cancelled = true;
            sessions.remove(entry.getKey());
         }
      }
   }

   private static void freezePlayer(ServerPlayer player, boolean frozen) {
      for (ServerPlayer other : PlayerLookup.all(player.level().getServer())) {
         ServerPlayNetworking.send(other, new HighFiveHandler.FreezeStatePayload(player.getUUID(), frozen));
      }
   }

   public record NoyaFlickerPayload(UUID victimId, boolean start, long durationMs) implements CustomPacketPayload {
      public static final Type<SikeFollowUpHandler.NoyaFlickerPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "noya_flicker"));
      public static final StreamCodec<FriendlyByteBuf, SikeFollowUpHandler.NoyaFlickerPayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeUUID(v.victimId());
         buf.writeBoolean(v.start());
         buf.writeLong(v.durationMs());
      }, buf -> new SikeFollowUpHandler.NoyaFlickerPayload(buf.readUUID(), buf.readBoolean(), buf.readLong()));

      public Type<SikeFollowUpHandler.NoyaFlickerPayload> type() {
         return ID;
      }
   }

   private static class NoyaSession {
      final UUID sikerId;
      final UUID victimId;
      int tpTick = 0;
      boolean tpDone = false;
      boolean animRunning = false;
      boolean cancelled = false;

      NoyaSession(UUID siker, UUID victim) {
         this.sikerId = siker;
         this.victimId = victim;
      }
   }

   public record SikePressPayload() implements CustomPacketPayload {
      public static final Type<SikeFollowUpHandler.SikePressPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "sike_press"));
      public static final StreamCodec<FriendlyByteBuf, SikeFollowUpHandler.SikePressPayload> CODEC = StreamCodec.unit(
         new SikeFollowUpHandler.SikePressPayload()
      );

      public Type<SikeFollowUpHandler.SikePressPayload> type() {
         return ID;
      }
   }

   public record SikeWindowPayload(boolean open, int durationMs, int promptDelayMs) implements CustomPacketPayload {
      public static final Type<SikeFollowUpHandler.SikeWindowPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "sike_window"));
      public static final StreamCodec<FriendlyByteBuf, SikeFollowUpHandler.SikeWindowPayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeBoolean(v.open());
         buf.writeInt(v.durationMs());
         buf.writeInt(v.promptDelayMs());
      }, buf -> new SikeFollowUpHandler.SikeWindowPayload(buf.readBoolean(), buf.readInt(), buf.readInt()));

      public Type<SikeFollowUpHandler.SikeWindowPayload> type() {
         return ID;
      }
   }
}
