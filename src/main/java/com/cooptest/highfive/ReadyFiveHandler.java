package com.cooptest.highfive;

import com.cooptest.HighFiveStreakHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.spin.HandSpinHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ReadyFiveHandler {
   public static final long PAIR_WINDOW_MS = 400L;
   public static final double REACH_FORWARD_OFFSET = 0.7;
   public static final double REACH_RADIUS = 0.9;
   public static final double MAX_REPOSITION = 2.0;
   public static final double MEET_SEPARATION = 1.1;
   public static final double PUSH_BACK = 0.5;
   public static final long SLIDE_START_MS = 170L;
   public static final long IMPACT_MS = 420L;
   public static final long PUSH_BACK_MS = 500L;
   public static final long ANIM_END_MS = 667L;
   public static final int SLIDE_TICKS = 5;
   public static final int PUSH_BACK_TICKS = 4;
   public static final int YAW_LERP_TICKS = 4;
   public static final long COOLDOWN_MS = 300L;
   public static final float SHAKE_GROUND = 0.35F;
   public static final float SHAKE_AIR = 0.55F;
   public static final long SHAKE_MS = 120L;
   public static final int ANIM_JUMP_HIGH = 127;
   private static final int ANIM_NONE = 0;
   private static final boolean DEBUG = true;
   private static final Map<UUID, Long> handRaised = new HashMap<>();
   private static final Map<UUID, Long> active = new HashMap<>();
   private static final Map<UUID, Long> cooldowns = new HashMap<>();
   private static final List<ReadyFiveHandler.PendingRepos> pendingRepos = new ArrayList<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(ReadyFiveHandler.ReadyFiveImpactPayload.ID, ReadyFiveHandler.ReadyFiveImpactPayload.CODEC);
   }

   public static boolean onHandRaise(ServerPlayer player) {
      UUID id = player.getUUID();
      if (active.containsKey(id)) {
         System.out.println("[RF5] onHandRaise " + player.getName().getString() + " -> already active, swallowed");
         return true;
      } else {
         PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
         System.out.println("[RF5] onHandRaise " + player.getName().getString() + " pose=" + pose);
         if (pose != PoseState.GRAB_READY) {
            return false;
         } else {
            long now = System.currentTimeMillis();
            Long cd = cooldowns.get(id);
            if (cd != null && now < cd) {
               System.out.println("[RF5] BAIL cooldown, " + (cd - now) + "ms left");
               return true;
            } else if (HandSpinHandler.isInSession(id)) {
               System.out.println("[RF5] BAIL in a hand spin session");
               return true;
            } else {
               handRaised.put(id, now);
               System.out
                  .println(
                     "[RF5] ARMED "
                        + player.getName().getString()
                        + " | handRaised size now "
                        + handRaised.size()
                        + " | if this never pairs, check ReadyFiveHandler.register() is called in TestCoop"
                  );
               return true;
            }
         }
      }
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         drainPendingRepos();
         expireStale();
         tryPair(server);
      });
   }

   private static void expireStale() {
      long now = System.currentTimeMillis();
      handRaised.entrySet().removeIf(e -> now - e.getValue() > 400L);
      cooldowns.entrySet().removeIf(e -> now >= e.getValue());
      active.entrySet().removeIf(e -> now - e.getValue() > 1667L);
   }

   private static void tryPair(MinecraftServer server) {
      if (handRaised.size() >= 2) {
         long now = System.currentTimeMillis();
         List<UUID> ids = new ArrayList<>(handRaised.keySet());

         for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
               UUID a = ids.get(i);
               UUID b = ids.get(j);
               if (!active.containsKey(a) && !active.containsKey(b)) {
                  ServerPlayer p1 = server.getPlayerList().getPlayer(a);
                  ServerPlayer p2 = server.getPlayerList().getPlayer(b);
                  if (p1 != null && p2 != null && p1.level() == p2.level()) {
                     Long t1 = handRaised.get(a);
                     Long t2 = handRaised.get(b);
                     if (t1 != null && t2 != null) {
                        if (Math.abs(t1 - t2) > 400L) {
                           System.out.println("[RF5] pair rejected: presses " + Math.abs(t1 - t2) + "ms apart, window is 400");
                        } else {
                           Vec3 r1 = reachPoint(p1);
                           Vec3 r2 = reachPoint(p2);
                           double reachDist = Math.hypot(r1.x - r2.x, r1.z - r2.z);
                           System.out
                              .println(
                                 "[RF5] candidate "
                                    + p1.getName().getString()
                                    + " + "
                                    + p2.getName().getString()
                                    + " | reach gap "
                                    + String.format("%.2f", reachDist)
                                    + " (need <= 0.9) | body gap "
                                    + String.format("%.2f", p1.distanceTo(p2))
                              );
                           if (!(reachDist > 0.9)) {
                              handRaised.remove(a);
                              handRaised.remove(b);
                              start(p1, p2, now);
                              return;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void start(ServerPlayer p1, ServerPlayer p2, long now) {
      if (p1.getUUID().compareTo(p2.getUUID()) > 0) {
         ServerPlayer tmp = p1;
         p1 = p2;
         p2 = tmp;
      }

      ServerPlayer a = p1;
      ServerPlayer b = p2;
      UUID ida = a.getUUID();
      UUID idb = b.getUUID();
      MinecraftServer server = a.level().getServer();
      if (server != null) {
         boolean air = !a.onGround() && !b.onGround();
         System.out.println("[RF5] FIRING " + a.getName().getString() + " + " + b.getName().getString() + " | air=" + air + " | anim ordinal 127");
         active.put(ida, now);
         active.put(idb, now);
         PoseNetworking.broadcastAnimState(a, 127);
         PoseNetworking.broadcastAnimState(b, 127);
         scheduleYawLerp(a, b, 4);
         scheduleYawLerp(b, a, 4);
         if (air) {
            equalizeArcs(a, b);
         }

         schedule(server, 170L, () -> {
            Vec3[] deltas = computeMeetDeltas(a, b, air);
            if (deltas != null) {
               pendingRepos.add(new ReadyFiveHandler.PendingRepos(a, deltas[0], 5));
               pendingRepos.add(new ReadyFiveHandler.PendingRepos(b, deltas[1], 5));
            }
         });
         schedule(server, 420L, () -> impact(a, b, air));
         schedule(server, 500L, () -> {
            Vec3 dir = horizontalBetween(a, b);
            if (dir != null) {
               pendingRepos.add(new ReadyFiveHandler.PendingRepos(a, dir.scale(-0.5), 4));
               pendingRepos.add(new ReadyFiveHandler.PendingRepos(b, dir.scale(0.5), 4));
            }
         });
         schedule(server, 667L, () -> {
            restore(a);
            restore(b);
            long end = System.currentTimeMillis();
            active.remove(ida);
            active.remove(idb);
            cooldowns.put(ida, end + 300L);
            cooldowns.put(idb, end + 300L);
         });
      }
   }

   private static void equalizeArcs(ServerPlayer a, ServerPlayer b) {
      double avgY = (a.getY() + b.getY()) / 2.0;
      double avgVY = (a.getDeltaMovement().y + b.getDeltaMovement().y) / 2.0;
      a.teleportTo(a.getX(), avgY, a.getZ());
      b.teleportTo(b.getX(), avgY, b.getZ());
      a.setDeltaMovement(a.getDeltaMovement().x, avgVY, a.getDeltaMovement().z);
      b.setDeltaMovement(b.getDeltaMovement().x, avgVY, b.getDeltaMovement().z);
      a.needsSync = true;
      b.needsSync = true;
   }

   private static Vec3[] computeMeetDeltas(ServerPlayer a, ServerPlayer b, boolean air) {
      Vec3 dir = horizontalBetween(a, b);
      if (dir == null) {
         return null;
      }

      double dist = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
      double step = Mth.clamp((dist - 1.1) / 2.0, 0.0, 0.55);
      Vec3 d1 = dir.scale(step);
      Vec3 d2 = dir.scale(-step);
      return Math.hypot(d1.x, d1.z) > 2.0 ? null : new Vec3[]{d1, d2};
   }

   private static void impact(ServerPlayer a, ServerPlayer b, boolean air) {
      ServerLevel world = a.level();
      double x = (a.getX() + b.getX()) / 2.0;
      double y = (a.getY() + b.getY()) / 2.0 + 1.5;
      double z = (a.getZ() + b.getZ()) / 2.0;
      if (air) {
         world.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.3F, 1.2F);
         world.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.7F, 1.6F);
         world.sendParticles(ParticleTypes.END_ROD, x, y, z, 24, 0.35, 0.35, 0.35, 0.12);
         world.sendParticles(ParticleTypes.FIREWORK, x, y, z, 16, 0.25, 0.25, 0.25, 0.08);
         world.sendParticles(ParticleTypes.CLOUD, x, y, z, 12, 0.3, 0.2, 0.3, 0.04);
      } else {
         world.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 1.0F);
         world.sendParticles(ParticleTypes.CRIT, x, y, z, 14, 0.25, 0.25, 0.25, 0.1);
         world.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 10, 0.2, 0.2, 0.2, 0.06);
      }

      ReadyFiveHandler.ReadyFiveImpactPayload payload = new ReadyFiveHandler.ReadyFiveImpactPayload(air ? 0.55F : 0.35F, 120, air);
      ServerPlayNetworking.send(a, payload);
      ServerPlayNetworking.send(b, payload);

      for (ServerPlayer near : PlayerLookup.tracking(a)) {
         if (near != a && near != b) {
            ServerPlayNetworking.send(near, payload);
         }
      }

      HighFiveStreakHandler.onFastFive(a);
      HighFiveStreakHandler.onFastFive(b);
   }

   private static void restore(ServerPlayer p) {
      if (p != null) {
         MinecraftServer server = p.level().getServer();
         if (server != null) {
            PoseNetworking.broadcastAnimState(p, 0);
            PoseNetworking.poseStates.put(p.getUUID(), PoseState.GRAB_READY);
            PoseNetworking.broadcastPoseChange(server, p.getUUID(), PoseState.GRAB_READY);
         }
      }
   }

   public static Vec3 horizontalForward(ServerPlayer p) {
      double yaw = Math.toRadians(p.getYRot());
      return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();
   }

   public static Vec3 reachPoint(ServerPlayer p) {
      Vec3 fwd = horizontalForward(p);
      Vec3 pos = p.position();
      return new Vec3(pos.x + fwd.x * 0.7, pos.y + 1.3, pos.z + fwd.z * 0.7);
   }

   private static Vec3 horizontalBetween(ServerPlayer a, ServerPlayer b) {
      double dx = b.getX() - a.getX();
      double dz = b.getZ() - a.getZ();
      double len = Math.hypot(dx, dz);
      return len < 1.0E-4 ? null : new Vec3(dx / len, 0.0, dz / len);
   }

   private static void scheduleYawLerp(ServerPlayer player, ServerPlayer target, int ticks) {
      MinecraftServer server = player.level().getServer();
      if (server != null) {
         Vec3 toTarget = target.position().subtract(player.position());
         float targetYaw = (float)Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
         float startYaw = player.getYRot();
         new Thread(() -> {
            for (int i = 1; i <= ticks; i++) {
               float t = (float)i / ticks;
               float newYaw = startYaw + Mth.wrapDegrees(targetYaw - startYaw) * t;

               try {
                  Thread.sleep(50L);
               } catch (InterruptedException ignored) {
                  return;
               }

               server.execute(() -> {
                  player.setYRot(newYaw);
                  player.setYHeadRot(newYaw);
                  player.setYBodyRot(newYaw);
               });
            }
         }).start();
      }
   }

   private static void schedule(MinecraftServer server, long delayMs, Runnable work) {
      new Thread(() -> {
         try {
            Thread.sleep(delayMs);
         } catch (InterruptedException ignored) {
            return;
         }

         server.execute(work);
      }).start();
   }

   private static void drainPendingRepos() {
      Iterator<ReadyFiveHandler.PendingRepos> it = pendingRepos.iterator();

      while (it.hasNext()) {
         ReadyFiveHandler.PendingRepos r = it.next();
         if (r.player != null && !r.player.isRemoved() && r.remainingTicks > 0) {
            Vec3 step = r.remainingDelta.scale(1.0 / r.remainingTicks);
            Vec3 pos = r.player.position();
            r.player.teleportTo(pos.x + step.x, pos.y, pos.z + step.z);
            r.remainingDelta = r.remainingDelta.subtract(step);
            r.remainingTicks--;
            if (r.remainingTicks <= 0) {
               it.remove();
            }
         } else {
            it.remove();
         }
      }
   }

   public static boolean isActive(UUID id) {
      return active.containsKey(id);
   }

   public static void cleanup(UUID id) {
      handRaised.remove(id);
      active.remove(id);
      cooldowns.remove(id);
      pendingRepos.removeIf(r -> r.player != null && r.player.getUUID().equals(id));
   }

   private static final class PendingRepos {
      final ServerPlayer player;
      Vec3 remainingDelta;
      int remainingTicks;

      PendingRepos(ServerPlayer player, Vec3 delta, int ticks) {
         this.player = player;
         this.remainingDelta = delta;
         this.remainingTicks = Math.max(1, ticks);
      }
   }

   public record ReadyFiveImpactPayload(float amount, int durationMs, boolean air) implements CustomPacketPayload {
      public static final Type<ReadyFiveHandler.ReadyFiveImpactPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "readyfive_impact"));
      public static final StreamCodec<FriendlyByteBuf, ReadyFiveHandler.ReadyFiveImpactPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeFloat(payload.amount);
         buf.writeInt(payload.durationMs);
         buf.writeBoolean(payload.air);
      }, buf -> new ReadyFiveHandler.ReadyFiveImpactPayload(buf.readFloat(), buf.readInt(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
