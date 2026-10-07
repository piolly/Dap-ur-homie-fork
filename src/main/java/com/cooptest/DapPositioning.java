package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class DapPositioning {
   public static final double DAP_SEPARATION = 1.35;
   public static final double DAP_RIGHT_OFFSET = 0.0;
   public static final double DAP_DISTANCE_DEADZONE = 0.35;
   public static final long DAP_SLIDE_MS = 200L;
   public static final double WINDUP_BLOCKS = 0.0;
   public static final long WINDUP_BEFORE_MS = 100L;
   public static final long WINDUP_SLIDE_MS = 80L;
   public static final int YAW_LERP_TICKS = 5;
   public static final long IMPACT_AT_MS = 250L;
   public static final long PERFECT_IMPACT_AT_MS = 210L;
   public static final long MOVE_RELEASE_AT_MS = 540L;
   public static final long PERFECT_MOVE_RELEASE_AT_MS = 1210L;
   public static final long RELEASE_WINDOW_MS = 1200L;
   public static final double MAX_SLIDE = 2.0;
   public static final double MAX_FORWARD_PUSH = 0.4;
   public static final int ANIM_DAP_END = 125;
   public static final int ANIM_NONE = 0;
   private static final List<DapPositioning.PendingSlide> pendingSlide = new ArrayList<>();
   private static final Map<UUID, UUID> releasePartner = new HashMap<>();
   private static final Map<UUID, Long> releaseOpensAt = new HashMap<>();
   private static final Map<UUID, Long> releaseExpiry = new HashMap<>();
   private static final List<DapPositioning.YawStep> yawSteps = new ArrayList<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(DapPositioning.DapReleasePayload.ID, DapPositioning.DapReleasePayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(DapPositioning.DapReleasePayload.ID, (payload, ctx) -> {
         ServerPlayer player = ctx.player();
         ctx.server().execute(() -> onRelease(player));
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         drainSlides();
         expireWindows();
      });
   }

   public static void closeIn(ServerPlayer p1, ServerPlayer p2) {
      long now = System.currentTimeMillis();
      double dist = Math.hypot(p2.position().x - p1.position().x, p2.position().z - p1.position().z);
      if (!(Math.abs(dist - 1.35) <= 0.35)) {
         Vec3[] targets = computeTargets(p1, p2, 1.35, 0.0);
         long closeAt = now + 100L;
         queueTo(p1, p2, targets[0], msToTicks(200L), closeAt);
         queueTo(p2, p1, targets[1], msToTicks(200L), closeAt);
      }
   }

   public static void faceEachOther(ServerPlayer p1, ServerPlayer p2) {
      scheduleYawLerp(p1, p2);
      scheduleYawLerp(p2, p1);
   }

   private static void scheduleYawLerp(ServerPlayer player, ServerPlayer target) {
      Vec3 to = target.position().subtract(player.position());
      float targetYaw = (float)Math.toDegrees(Math.atan2(-to.x, to.z));
      float startYaw = player.getYRot();
      float delta = Mth.wrapDegrees(targetYaw - startYaw);

      for (int i = 1; i <= 5; i++) {
         float t = i / 5.0F;
         int tick = i;
         yawSteps.add(new DapPositioning.YawStep(player, startYaw + delta * t, System.currentTimeMillis() + tick * 50L));
      }
   }

   public static void openReleaseWindow(ServerPlayer p1, ServerPlayer p2, boolean perfect) {
      long opensAt = System.currentTimeMillis() + (perfect ? 1210L : 540L);
      long expiry = opensAt + 1200L;

      for (ServerPlayer p : new ServerPlayer[]{p1, p2}) {
         UUID id = p.getUUID();
         releasePartner.put(id, id.equals(p1.getUUID()) ? p2.getUUID() : p1.getUUID());
         releaseOpensAt.put(id, opensAt);
         releaseExpiry.put(id, expiry);
      }
   }

   public static void openReleaseWindow(ServerPlayer p1, ServerPlayer p2) {
      openReleaseWindow(p1, p2, false);
   }

   public static boolean impactHappened(UUID playerId) {
      Long opensAt = releaseOpensAt.get(playerId);
      return opensAt != null && System.currentTimeMillis() >= opensAt;
   }

   private static void onRelease(ServerPlayer player) {
      UUID id = player.getUUID();
      UUID partnerId = releasePartner.get(id);
      if (partnerId != null) {
         long now = System.currentTimeMillis();
         Long opensAt = releaseOpensAt.get(id);
         if (opensAt != null && now >= opensAt) {
            Long expiry = releaseExpiry.get(id);
            if (expiry != null && now <= expiry) {
               ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
               PoseNetworking.broadcastAnimState(player, 125);
               if (partner != null) {
                  PoseNetworking.broadcastAnimState(partner, 125);
               }

               clear(id);
               clear(partnerId);
            } else {
               clear(id);
            }
         }
      }
   }

   private static void expireWindows() {
      long now = System.currentTimeMillis();
      releaseExpiry.entrySet().removeIf(e -> {
         if (now > e.getValue()) {
            releasePartner.remove(e.getKey());
            return true;
         } else {
            return false;
         }
      });
   }

   private static void clear(UUID id) {
      releasePartner.remove(id);
      releaseOpensAt.remove(id);
      releaseExpiry.remove(id);
   }

   public static void cleanup(UUID playerId) {
      UUID partner = releasePartner.get(playerId);
      clear(playerId);
      if (partner != null) {
         clear(partner);
      }

      pendingSlide.removeIf(s -> s.player.getUUID().equals(playerId));
      yawSteps.removeIf(s -> s.player().getUUID().equals(playerId));
   }

   private static void drainSlides() {
      long now = System.currentTimeMillis();
      Iterator<DapPositioning.PendingSlide> it = pendingSlide.iterator();

      while (it.hasNext()) {
         DapPositioning.PendingSlide s = it.next();
         if (now >= s.startAtMs) {
            if (s.remainingTicks <= 0) {
               it.remove();
            } else {
               if (s.partner != null) {
                  Vec3 a = s.player.position();
                  Vec3 b = s.partner.position();
                  if (Math.hypot(b.x - a.x, b.z - a.z) <= 1.35) {
                     it.remove();
                     continue;
                  }
               }

               Vec3 step = s.remainingDelta.scale(1.0 / s.remainingTicks);
               Vec3 pos = s.player.position();
               s.player.teleportTo(pos.x + step.x, pos.y, pos.z + step.z);
               s.remainingDelta = s.remainingDelta.subtract(step);
               s.remainingTicks--;
               if (s.remainingTicks <= 0) {
                  it.remove();
               }
            }
         }
      }

      Iterator<DapPositioning.YawStep> yit = yawSteps.iterator();

      while (yit.hasNext()) {
         DapPositioning.YawStep y = yit.next();
         if (now >= y.atMs()) {
            float yaw = Mth.wrapDegrees(y.yaw());
            y.player().setYRot(yaw);
            y.player().setYHeadRot(yaw);
            y.player().setYBodyRot(yaw);
            yit.remove();
         }
      }
   }

   private static Vec3 between(ServerPlayer p1, ServerPlayer p2) {
      Vec3 d = p2.position().subtract(p1.position());
      double dist = Math.hypot(d.x, d.z);
      return dist < 0.01 ? HighFiveHandler.horizontalForward(p1) : new Vec3(d.x / dist, 0.0, d.z / dist);
   }

   private static Vec3[] computeTargets(ServerPlayer p1, ServerPlayer p2, double separation, double rightOffset) {
      Vec3 pos1 = p1.position();
      Vec3 pos2 = p2.position();
      Vec3 toP2 = pos2.subtract(pos1);
      double dist = Math.hypot(toP2.x, toP2.z);
      if (dist < 0.01) {
         toP2 = HighFiveHandler.horizontalForward(p1);
         dist = 1.0;
      }

      Vec3 dir12 = new Vec3(toP2.x / dist, 0.0, toP2.z / dist);
      Vec3 dir21 = dir12.reverse();
      double fwdStep = Math.max(0.0, Math.min((dist - separation) / 2.0, 0.4));
      Vec3 right1 = rightOffset == 0.0 ? Vec3.ZERO : rightOf(HighFiveHandler.horizontalForward(p1));
      Vec3 right2 = rightOffset == 0.0 ? Vec3.ZERO : rightOf(HighFiveHandler.horizontalForward(p2));
      return new Vec3[]{
         new Vec3(pos1.x + dir12.x * fwdStep + right1.x * rightOffset, pos1.y, pos1.z + dir12.z * fwdStep + right1.z * rightOffset),
         new Vec3(pos2.x + dir21.x * fwdStep + right2.x * rightOffset, pos2.y, pos2.z + dir21.z * fwdStep + right2.z * rightOffset)
      };
   }

   private static Vec3 rightOf(Vec3 fwd) {
      return new Vec3(fwd.z, 0.0, -fwd.x);
   }

   public static void slideBy(ServerPlayer player, Vec3 delta, int ticks) {
      queue(player, null, new Vec3(delta.x, 0.0, delta.z), ticks, System.currentTimeMillis());
   }

   private static void queueTo(ServerPlayer player, ServerPlayer partner, Vec3 to, int ticks, long startAtMs) {
      Vec3 from = player.position();
      queue(player, partner, new Vec3(to.x - from.x, 0.0, to.z - from.z), ticks, startAtMs);
   }

   private static void queue(ServerPlayer player, Vec3 delta, int ticks, long startAtMs) {
      queue(player, null, delta, ticks, startAtMs);
   }

   private static void queue(ServerPlayer player, ServerPlayer partner, Vec3 delta, int ticks, long startAtMs) {
      if (!(Math.hypot(delta.x, delta.z) > 2.0)) {
         pendingSlide.add(new DapPositioning.PendingSlide(player, partner, delta, ticks, startAtMs));
      }
   }

   private static int msToTicks(long ms) {
      return (int)Math.max(1L, ms / 50L);
   }

   public record DapReleasePayload() implements CustomPacketPayload {
      public static final Type<DapPositioning.DapReleasePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "dap_release"));
      public static final StreamCodec<FriendlyByteBuf, DapPositioning.DapReleasePayload> CODEC = StreamCodec.unit(new DapPositioning.DapReleasePayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static final class PendingSlide {
      final ServerPlayer player;
      final ServerPlayer partner;
      Vec3 remainingDelta;
      int remainingTicks;
      long startAtMs;

      PendingSlide(ServerPlayer player, ServerPlayer partner, Vec3 totalDelta, int ticks, long startAtMs) {
         this.player = player;
         this.partner = partner;
         this.remainingDelta = totalDelta;
         this.remainingTicks = Math.max(1, ticks);
         this.startAtMs = startAtMs;
      }
   }

   private record YawStep(ServerPlayer player, float yaw, long atMs) {
   }
}
