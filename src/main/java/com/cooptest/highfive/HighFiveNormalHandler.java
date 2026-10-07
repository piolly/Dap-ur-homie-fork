package com.cooptest.highfive;

import com.cooptest.HighFiveHandler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.Vec3;

public class HighFiveNormalHandler {
   private static final Identifier FAST_SLOW_ID = Identifier.fromNamespaceAndPath("testcoop", "highfive_fast_slow");
   private static final double FAST_SLOW_MULT = -0.35;
   private static final long FAST_SLOW_DUR_MS = 400L;
   private static final List<HighFiveNormalHandler.PendingRepos> pendingSlide = new ArrayList<>();
   private static final List<HighFiveNormalHandler.PendingRepos> pendingPull = new ArrayList<>();
   private static final List<HighFiveNormalHandler.PendingPush> pendingPush = new ArrayList<>();
   private static final List<HighFiveNormalHandler.PendingRock> pendingRock = new ArrayList<>();

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         long now = System.currentTimeMillis();
         drainRepos(pendingSlide);
         drainRepos(pendingPull);
         Iterator<HighFiveNormalHandler.PendingPush> pushIt = pendingPush.iterator();

         while (pushIt.hasNext()) {
            HighFiveNormalHandler.PendingPush p = pushIt.next();
            if (now >= p.untilMs()) {
               pushIt.remove();
            } else {
               Vec3 pos = p.player().position();
               p.player().teleportTo(pos.x + p.nudgePerTick().x, pos.y, pos.z + p.nudgePerTick().z);
            }
         }

         Iterator<HighFiveNormalHandler.PendingRock> rockIt = pendingRock.iterator();

         while (rockIt.hasNext()) {
            HighFiveNormalHandler.PendingRock r = rockIt.next();
            Vec3 step = r.phase == 0 ? r.fwdStep : r.backStep;
            Vec3 pos = r.player.position();
            r.player.teleportTo(pos.x + step.x, pos.y, pos.z + step.z);
            if (--r.ticksLeft <= 0) {
               if (r.phase == 0) {
                  r.phase = 1;
                  r.ticksLeft = r.backTicks;
               } else {
                  rockIt.remove();
               }
            }
         }
      });
   }

   private static void drainRepos(List<HighFiveNormalHandler.PendingRepos> list) {
      Iterator<HighFiveNormalHandler.PendingRepos> it = list.iterator();

      while (it.hasNext()) {
         HighFiveNormalHandler.PendingRepos r = it.next();
         if (r.remainingTicks <= 0) {
            it.remove();
         } else {
            Vec3 step = r.remainingDelta.scale(1.0 / r.remainingTicks);
            Vec3 pos = r.player.position();
            r.player.teleportTo(pos.x + step.x, pos.y, pos.z + step.z);
            r.remainingDelta = r.remainingDelta.subtract(step);
            if (--r.remainingTicks <= 0) {
               it.remove();
            }
         }
      }
   }

   public static void executeNormal(ServerPlayer p1, ServerPlayer p2, long now) {
      Vec3[] targets = computeTargets(p1, p2, 1.1, 0.2);
      int slideTicks = msToTicks(290L);
      queueSlide(p1, p1.position(), targets[0], slideTicks);
      queueSlide(p2, p2.position(), targets[1], slideTicks);
      Vec3 back1 = HighFiveHandler.horizontalForward(p1).scale(-0.4);
      Vec3 back2 = HighFiveHandler.horizontalForward(p2).scale(-0.4);
      long delay = 540L;
      new Thread(() -> {
         try {
            Thread.sleep(delay);
         } catch (InterruptedException var7x) {
         }

         p1.level().getServer().execute(() -> {
            pendingPull.add(new HighFiveNormalHandler.PendingRepos(p1, back1, 5));
            pendingPull.add(new HighFiveNormalHandler.PendingRepos(p2, back2, 5));
         });
      }).start();
   }

   public static void executeFast(ServerPlayer p1, ServerPlayer p2, long now) {
      Vec3[] targets = computeTargets(p1, p2, 1.1, 0.2);
      int slideTicks = msToTicks(290L);
      queueSlide(p1, p1.position(), targets[0], slideTicks);
      queueSlide(p2, p2.position(), targets[1], slideTicks);
   }

   public static void executeMixed(ServerPlayer fastP, ServerPlayer stillP, long now) {
      Vec3[] targets = computeTargets(fastP, stillP, 1.1, 0.2);
      int slideTicks = msToTicks(290L);
      queueSlide(fastP, fastP.position(), targets[0], slideTicks);
      queueSlide(stillP, stillP.position(), targets[1], slideTicks);
      Vec3 backStill = HighFiveHandler.horizontalForward(stillP).scale(-0.4);
      long delay = 540L;
      new Thread(() -> {
         try {
            Thread.sleep(delay);
         } catch (InterruptedException var5x) {
         }

         stillP.level().getServer().execute(() -> pendingPull.add(new HighFiveNormalHandler.PendingRepos(stillP, backStill, 5)));
      }).start();
   }

   public static void executeNormalSolo(ServerPlayer player, long now) {
      Vec3 fwd = HighFiveHandler.horizontalForward(player);
      Vec3 right = rightOf(fwd);
      Vec3 target = player.position().add(fwd.scale(0.55)).add(right.scale(0.2));
      queueSlide(player, player.position(), target, msToTicks(290L));
      Vec3 back = fwd.scale(-0.4);
      long delay = 540L;
      new Thread(() -> {
         try {
            Thread.sleep(delay);
         } catch (InterruptedException var5x) {
         }

         player.level().getServer().execute(() -> pendingPull.add(new HighFiveNormalHandler.PendingRepos(player, back, 5)));
      }).start();
   }

   public static void executeFastSolo(ServerPlayer player, long now) {
      Vec3 fwd = HighFiveHandler.horizontalForward(player);
      Vec3 right = rightOf(fwd);
      Vec3 target = player.position().add(fwd.scale(0.45)).add(right.scale(0.2));
      queueSlide(player, player.position(), target, msToTicks(210L));
      long impactMs = now + 210L;
      long animEndMs = now + 1208L;
      Vec3 nudge = fwd.scale(0.04);
      new Thread(() -> {
         try {
            Thread.sleep(impactMs - now);
         } catch (InterruptedException var9) {
         }

         player.level().getServer().execute(() -> pendingPush.add(new HighFiveNormalHandler.PendingPush(player, nudge, animEndMs)));
      }).start();
   }

   public static void executeMixedSoloFast(ServerPlayer player, long now) {
      Vec3 right = rightOf(HighFiveHandler.horizontalForward(player));
      Vec3 target = player.position().add(right.scale(0.2));
      queueSlide(player, player.position(), target, msToTicks(210L));
   }

   private static void applyFastSlow(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(FAST_SLOW_ID);
         attr.addTransientModifier(new AttributeModifier(FAST_SLOW_ID, -0.35, Operation.ADD_MULTIPLIED_TOTAL));
      }
   }

   private static void removeFastSlow(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(FAST_SLOW_ID);
      }
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
      double fwdStep = Math.max(0.0, Math.min((dist - separation) / 2.0, separation * 0.3));
      Vec3 right1 = rightOf(HighFiveHandler.horizontalForward(p1));
      Vec3 right2 = rightOf(HighFiveHandler.horizontalForward(p2));
      Vec3 t1 = new Vec3(pos1.x + dir12.x * fwdStep + right1.x * rightOffset, pos1.y, pos1.z + dir12.z * fwdStep + right1.z * rightOffset);
      Vec3 t2 = new Vec3(pos2.x + dir21.x * fwdStep + right2.x * rightOffset, pos2.y, pos2.z + dir21.z * fwdStep + right2.z * rightOffset);
      return new Vec3[]{t1, t2};
   }

   private static Vec3 rightOf(Vec3 fwd) {
      return new Vec3(fwd.z, 0.0, -fwd.x);
   }

   private static void queueSlide(ServerPlayer player, Vec3 from, Vec3 to, int ticks) {
      Vec3 delta = new Vec3(to.x - from.x, 0.0, to.z - from.z);
      if (!(Math.hypot(delta.x, delta.z) > 2.0)) {
         pendingSlide.add(new HighFiveNormalHandler.PendingRepos(player, delta, ticks));
      }
   }

   private static int msToTicks(long ms) {
      return (int)Math.max(1L, ms / 50L);
   }

   private record PendingPush(ServerPlayer player, Vec3 nudgePerTick, long untilMs) {
   }

   private static final class PendingRepos {
      final ServerPlayer player;
      Vec3 remainingDelta;
      int remainingTicks;

      PendingRepos(ServerPlayer player, Vec3 totalDelta, int ticks) {
         this.player = player;
         this.remainingDelta = totalDelta;
         this.remainingTicks = Math.max(1, ticks);
      }
   }

   private static final class PendingRock {
      final ServerPlayer player;
      int phase;
      int ticksLeft;
      final Vec3 fwdStep;
      final Vec3 backStep;
      final int backTicks;

      PendingRock(ServerPlayer player, Vec3 forward, int fwdTicks, double fwdMag, int backTicks, double backMag) {
         this.player = player;
         this.phase = 0;
         this.fwdStep = forward.scale(fwdMag / fwdTicks);
         this.backStep = forward.scale(-backMag / backTicks);
         this.ticksLeft = fwdTicks;
         this.backTicks = backTicks;
      }
   }
}
