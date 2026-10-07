package com.cooptest.bros;

import com.cooptest.CoopMovesConfig;

public final class BrosConfig {
   private BrosConfig() {
   }

   public static void apply() {
      CoopMovesConfig c = CoopMovesConfig.get();
      BrosHandler.WALK_SPEED = c.brosWalkSpeed;
      BrosHandler.MAX_Y_SPLIT = Math.max(c.brosStepHeight, c.brosMaxYSplit);
      BrosPhysics.STEP_HEIGHT = Math.max(0.5, c.brosStepHeight);
      BrosHandler.SPRINT_MULT = c.brosSprintMult;
      BrosShield.SHIELD_HP = Math.max(1.0F, c.brosShieldHp);
      BrosShield.RADIUS = c.brosDomeRadius;
      BrosShield.MOB_DAMAGE_THROUGH = clamp01(c.brosMobDamageThrough);
      BrosShield.DAMAGE_REDUCTION = clamp01(c.brosOtherDamageReduction);
      BrosShield.PARTNER_SHARE = clamp01(c.brosPartnerShare);
      BrosShield.HEAL_AMOUNT = c.brosHealAmount;
      BrosShield.HEAL_INTERVAL_TICKS = Math.max(1, c.brosHealIntervalTicks);
      BrosShield.HEAL_COMBAT_PAUSE_TICKS = Math.max(0, c.brosHealCombatPauseTicks);
      BrosShield.SHATTER_DAMAGE = c.brosShatterDamage;
      BrosShield.REGEN_APART_SECONDS = Math.max(1, c.brosShieldRegenSeconds);
      BrosAbilities.RUSH_DAMAGE = c.brosRushDamage;
      BrosAbilities.RUSH_TICKS = Math.max(1, c.brosRushTicks);
      BrosAbilities.RUSH_SPEED = c.brosRushSpeed;
      BrosAbilities.RUSH_COOLDOWN_TICKS = Math.max(0, c.brosRushCooldownTicks);
      BrosAbilities.RUSH_SHIELD_COST = Math.max(0.0F, c.brosRushShieldCost);
      BrosAbilities.RUSH_BREAKS_BLOCKS = c.brosRushBreaksBlocks;
      BrosAbilities.RUSH_MAX_HARDNESS = c.brosRushMaxHardness;
      BrosAbilities.PULSE_RADIUS = c.brosPulseRadius;
      BrosAbilities.PULSE_DAMAGE = c.brosPulseDamage;
      BrosAbilities.PULSE_KNOCKBACK = c.brosPulseKnockback;
      BrosAbilities.PULSE_SHIELD_COST = c.brosPulseShieldCost;
      BrosAbilities.PULSE_COOLDOWN_TICKS = Math.max(0, c.brosPulseCooldownTicks);
      BrosAbilities.RECHARGE_FULL_TICKS = Math.max(1, c.brosRechargeFullTicks);
      BrosAbilities.RECHARGE_HEALTH_COST = Math.max(0.0F, c.brosRechargeHealthCost);
      BrosAbilities.RECHARGE_MIN_HEALTH = c.brosRechargeMinHealth;
      BrosAbilities.BUNKER_DRAIN = Math.max(0.0F, c.brosBunkerDrain);
      BrosAbilities.BUNKER_DAMAGE_THROUGH = clamp01(c.brosBunkerDamageThrough);
   }

   private static float clamp01(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }
}
