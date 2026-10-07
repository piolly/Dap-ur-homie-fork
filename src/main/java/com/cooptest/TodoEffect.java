package com.cooptest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class TodoEffect extends MobEffect {
   public TodoEffect() {
      super(MobEffectCategory.BENEFICIAL, 3381759);
   }

   public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
      return false;
   }

   public boolean applyEffectTick(ServerLevel world, LivingEntity entity, int amplifier) {
      return true;
   }
}
