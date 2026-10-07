package com.cooptest.bros;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class BrosPhysics {
   public static double STEP_HEIGHT = 1.05;
   public static final double STEP_PROBE = 0.125;
   public static final double GRAVITY = 0.08;
   public static final double DRAG = 0.98;

   private BrosPhysics() {
   }

   public static BrosPhysics.Result resolve(Level world, Entity e, double x, double z, double y, double vy, boolean allowBlock) {
      if (!free(world, e, x, y, z)) {
         double stepped = Double.NaN;

         for (double s = 0.125; s <= STEP_HEIGHT + 1.0E-6; s += 0.125) {
            if (free(world, e, x, y + s, z)) {
               stepped = y + s;
               break;
            }
         }

         if (Double.isNaN(stepped)) {
            return allowBlock ? new BrosPhysics.Result(false, y, vy) : new BrosPhysics.Result(true, y, 0.0);
         }

         y = stepped;
         vy = 0.0;
      }

      vy = (vy - 0.08) * 0.98;
      double target = y + vy;
      if (free(world, e, x, target, z)) {
         return new BrosPhysics.Result(true, target, vy);
      }

      double lo = target;
      double hi = y;

      for (int i = 0; i < 12; i++) {
         double mid = (lo + hi) * 0.5;
         if (free(world, e, x, mid, z)) {
            hi = mid;
         } else {
            lo = mid;
         }
      }

      return new BrosPhysics.Result(true, hi, 0.0);
   }

   private static boolean free(Level world, Entity e, double x, double y, double z) {
      AABB box = e.getBoundingBox().move(x - e.getX(), y - e.getY(), z - e.getZ()).deflate(1.0E-7);
      return world.noCollision(e, box);
   }

   public record Result(boolean ok, double y, double vy) {
   }
}
