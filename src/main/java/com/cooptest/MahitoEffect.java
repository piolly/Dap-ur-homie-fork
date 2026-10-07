package com.cooptest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class MahitoEffect extends MobEffect {
   public MahitoEffect() {
      super(MobEffectCategory.HARMFUL, 10040012);
   }

   public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
      return duration % 10 == 0;
   }

   public boolean applyEffectTick(ServerLevel world, LivingEntity entity, int amplifier) {
      return true;
   }
}
