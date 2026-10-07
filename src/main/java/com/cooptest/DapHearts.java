package com.cooptest;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class DapHearts {
   private DapHearts() {
   }

   public static void clean(ServerPlayer p) {
      CoopMovesConfig c = CoopMovesConfig.get();
      add(p, c.dapHeartsClean);
      if (p != null && p.isAlive() && c.dapCleanHeal > 0.0F) {
         p.heal(c.dapCleanHeal * 2.0F);
      }
   }

   public static void perfect(ServerPlayer p) {
      add(p, CoopMovesConfig.get().dapHeartsPerfect);
   }

   public static void great(ServerPlayer p) {
      add(p, CoopMovesConfig.get().dapHeartsGreat);
   }

   public static void flair(ServerPlayer p, boolean cleanTier) {
      CoopMovesConfig c = CoopMovesConfig.get();
      add(p, cleanTier ? c.dapHeartsFlairClean : c.dapHeartsFlairBig);
   }

   public static void add(ServerPlayer p, float hearts) {
      CoopMovesConfig c = CoopMovesConfig.get();
      if (p != null && c.enableDapHearts && p.isAlive() && !(hearts <= 0.0F)) {
         float addHp = hearts * 2.0F;
         float maxHp = Math.max(addHp, c.dapHeartsMax * 2.0F);
         float before = p.getAbsorptionAmount();
         float target = Math.min(maxHp, before + addHp);
         int amp = Math.max(0, (int)Math.ceil(target / 4.0F) - 1);
         int ticks = Math.max(1, c.dapHeartsSeconds) * 20;
         p.removeEffect(MobEffects.ABSORPTION);
         p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, amp, false, true));
         p.setAbsorptionAmount(target);
         if (p.level() instanceof ServerLevel w) {
            w.sendParticles(ParticleTypes.WAX_ON, p.getX(), p.getY() + 1.9, p.getZ(), 4 + (int)(target / 2.0F), 0.3, 0.15, 0.3, 0.02);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 0.9F + Math.min(1.0F, target / 20.0F));
         }
      }
   }
}
