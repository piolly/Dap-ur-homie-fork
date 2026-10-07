package com.cooptest;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;


public class MahitoEffect extends MobEffect {

    public MahitoEffect() {
        super(MobEffectCategory.HARMFUL, 0x9932CC);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        return true;
    }
}