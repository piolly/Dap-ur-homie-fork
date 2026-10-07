package com.cooptest;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class CoopKnockback {
   // 26.3 port: LivingEntity.knockback now needs a DamageSource, so push the target directly.
   public static void apply(LivingEntity living, double strength, double x, double z) {
      if (strength <= 0.0) {
         return;
      }
      double len = Math.sqrt(x * x + z * z);
      if (len < 1.0E-4) {
         return;
      }
      Vec3 cur = living.getDeltaMovement();
      living.setDeltaMovement(cur.x / 2.0 - x / len * strength, cur.y, cur.z / 2.0 - z / len * strength);
      living.syncVelocity = true;
   }
}
