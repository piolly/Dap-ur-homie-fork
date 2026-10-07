package com.cooptest.bros;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

public final class BrosShield {
   public static double RADIUS = 2.2;
   public static final double HEIGHT = 3.0;
   public static float SHIELD_HP = 40.0F;
   public static final float PROJECTILE_DRAIN = 2.0F;
   public static float HEAL_AMOUNT = 1.0F;
   public static int HEAL_INTERVAL_TICKS = 15;
   public static float MOB_DAMAGE_THROUGH = 0.65F;
   public static final double MOB_HIT_BUMP = 0.35;
   public static int HEAL_COMBAT_PAUSE_TICKS = 80;
   public static float DAMAGE_REDUCTION = 0.4F;
   public static float PARTNER_SHARE = 0.5F;
   public static final double SHATTER_PUSH = 0.8;
   public static final double SHATTER_UP = 0.35;
   public static float SHATTER_DAMAGE = 18.0F;
   public static int REGEN_APART_SECONDS = 45;
   private static final Map<UUID, BrosShield.Memory> memory = new HashMap<>();
   private static boolean reapplying = false;

   private BrosShield() {
   }

   static float remembered(UUID id) {
      BrosShield.Memory m = memory.get(id);
      if (m == null) {
         return 1.0F;
      } else {
         float regenMs = Math.max(1, REGEN_APART_SECONDS) * 1000.0F;
         float frac = m.frac() + (float)(System.currentTimeMillis() - m.atMs()) / regenMs;
         if (frac >= 1.0F) {
            memory.remove(id);
            return 1.0F;
         } else {
            return Math.max(0.0F, frac);
         }
      }
   }

   static void remember(UUID id, float frac) {
      frac = Math.max(0.0F, Math.min(1.0F, frac));
      if (frac >= 1.0F) {
         memory.remove(id);
      } else {
         memory.put(id, new BrosShield.Memory(frac, System.currentTimeMillis()));
      }
   }

   static void clearMemory() {
      memory.clear();
   }

   static void register() {
      ServerLivingEntityEvents.ALLOW_DAMAGE.register(BrosShield::onDamage);
   }

   static void onStart(BrosHandler.Session s) {
      double[] c = center(s);
      s.world.playSound(null, c[0], c[1], c[2], SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
      s.world.playSound(null, c[0], c[1], c[2], SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
   }

   static void onGentleEnd(BrosHandler.Session s) {
      if (s.world instanceof ServerLevel sw) {
         double[] var3 = center(s);
         sw.playSound(null, var3[0], var3[1], var3[2], SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.5F);
         sw.sendParticles(ParticleTypes.CLOUD, var3[0], var3[1] + 1.0, var3[2], 8, RADIUS * 0.4, 0.4, RADIUS * 0.4, 0.03);
      }
   }

   static void onShatter(BrosHandler.Session s, boolean penalty) {
      if (s.world instanceof ServerLevel sw) {
         double[] var9 = center(s);
         sw.playSound(null, var9[0], var9[1], var9[2], SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
         sw.playSound(null, var9[0], var9[1], var9[2], SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);

         for (int i = 0; i < 48; i++) {
            double a = (Math.PI * 2) * i / 48.0;
            sw.sendParticles(
               ParticleTypes.ELECTRIC_SPARK,
               var9[0] + Math.cos(a) * RADIUS,
               var9[1] + 1.0 + i % 4 * 0.5,
               var9[2] + Math.sin(a) * RADIUS,
               2,
               0.1,
               0.1,
               0.1,
               0.15
            );
         }

         sw.sendParticles(ParticleTypes.EXPLOSION, var9[0], var9[1] + 1.0, var9[2], 1, 0.0, 0.0, 0.0, 0.0);
         pushApart(s.a, var9);
         pushApart(s.b, var9);
         if (penalty) {
            for (LivingEntity e : new LivingEntity[]{s.a, s.b}) {
               if (e instanceof ServerPlayer p && p.isAlive() && !p.isRemoved()) {
                  p.hurtServer(sw, sw.damageSources().magic(), SHATTER_DAMAGE);
               }
            }
         }
      }
   }

   static void tick(MinecraftServer server, BrosHandler.Session s) {
      if (s.world instanceof ServerLevel sw) {
         double[] var8 = center(s);
         AABB box = new AABB(var8[0] - RADIUS, var8[1] - 1.0, var8[2] - RADIUS, var8[0] + RADIUS, var8[1] + 3.0 + 1.0, var8[2] + RADIUS);

         for (Projectile proj : sw.getEntitiesOfClass(Projectile.class, box, p -> !p.isRemoved())) {
            if (insideDome(var8, proj) && !(proj.getDeltaMovement().lengthSqr() < 0.0025)) {
               Entity owner = proj.getOwner();
               if (owner == null || owner instanceof Enemy || !insideDome(var8, owner)) {
                  sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, proj.getX(), proj.getY(), proj.getZ(), 10, 0.1, 0.1, 0.1, 0.12);
                  sw.playSound(null, proj.getX(), proj.getY(), proj.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.7F);
                  proj.discard();
                  if (drain(server, s, 2.0F)) {
                     return;
                  }
               }
            }
         }

         boolean inCombat = s.ticks - s.lastHitTick < HEAL_COMBAT_PAUSE_TICKS;
         if (!inCombat && !s.charging && s.ticks % HEAL_INTERVAL_TICKS == 0) {
            for (LivingEntity e : sw.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !(x instanceof Enemy) && !(x instanceof ArmorStand))) {
               if (insideDome(var8, e) && !(e.getHealth() >= e.getMaxHealth())) {
                  e.heal(HEAL_AMOUNT);
                  sw.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
               }
            }
         }
      }
   }

   private static boolean onDamage(LivingEntity target, DamageSource source, float amount) {
      if (reapplying) {
         return true;
      }

      if (target.level() instanceof ServerLevel sw) {
         BrosHandler.Session s = BrosHandler.domeAt(target);
         if (s == null) {
            return true;
         }

         MinecraftServer server = sw.getServer();
         Entity attacker = source.getEntity();
         Entity direct = source.getDirectEntity();
         boolean isBro = target == s.a || target == s.b;
         LivingEntity partner = target == s.a ? s.b : s.a;
         if (isBro && attacker == partner) {
            BrosHandler.shatter(server, s);
            return true;
         }

         boolean projectile = direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
         if (projectile) {
            if (attacker != null && !(attacker instanceof Enemy) && insideDome(center(s), attacker)) {
               return true;
            }

            hitFeedback(sw, target);
            drain(server, s, amount);
            return false;
         } else if (s.bunkering) {
            s.lastHitTick = s.ticks;
            float through = amount * BrosAbilities.BUNKER_DAMAGE_THROUGH;
            hitFeedback(sw, target);
            if (drain(server, s, amount - through)) {
               return true;
            }

            if (attacker instanceof Enemy || direct instanceof Enemy) {
               bump(attacker, target);
            }

            applyReduced(sw, s, target, partner, isBro, source, through);
            return false;
         } else if (!(attacker instanceof Enemy) && !(direct instanceof Enemy)) {
            if (isBro && attacker != null) {
               s.lastHitTick = s.ticks;
               float absorbed = amount * DAMAGE_REDUCTION;
               hitFeedback(sw, target);
               if (drain(server, s, absorbed)) {
                  return true;
               }

               applyReduced(sw, s, target, partner, isBro, source, amount - absorbed);
               return false;
            } else {
               return true;
            }
         } else {
            if (isBro) {
               s.lastHitTick = s.ticks;
            }

            float absorbed = amount * (1.0F - MOB_DAMAGE_THROUGH);
            hitFeedback(sw, target);
            if (drain(server, s, absorbed)) {
               return true;
            }

            Entity mob = direct instanceof Enemy ? direct : attacker;
            bump(mob, target);
            applyReduced(sw, s, target, partner, isBro, source, amount - absorbed);
            return false;
         }
      } else {
         return true;
      }
   }

   private static void applyReduced(
      ServerLevel sw, BrosHandler.Session s, LivingEntity target, LivingEntity partner, boolean isBro, DamageSource source, float rest
   ) {
      if (!(rest <= 0.0F)) {
         float toPartner = isBro && !(partner instanceof ArmorStand) && partner.isAlive() ? rest * PARTNER_SHARE : 0.0F;
         float toTarget = rest - toPartner;
         reapplying = true;

         try {
            if (toTarget > 0.0F) {
               target.hurtServer(sw, source, toTarget);
            }

            if (toPartner > 0.0F) {
               partner.hurtServer(sw, source, toPartner);
            }
         } finally {
            reapplying = false;
         }
      }
   }

   private static void bump(Entity mob, LivingEntity target) {
      if (mob != null && !mob.isRemoved()) {
         double dx = mob.getX() - target.getX();
         double dz = mob.getZ() - target.getZ();
         double d = Math.hypot(dx, dz);
         if (!(d < 0.001)) {
            mob.push(dx / d * 0.35, 0.1, dz / d * 0.35);
         }
      }
   }

   private static boolean drain(MinecraftServer server, BrosHandler.Session s, float amount) {
      if (s.ended) {
         return true;
      } else {
         s.shieldHp = s.shieldHp - Math.max(0.0F, amount);
         if (s.shieldHp <= 0.0F) {
            BrosHandler.shatter(server, s, true);
            return true;
         } else {
            return false;
         }
      }
   }

   private static void hitFeedback(ServerLevel sw, LivingEntity target) {
      sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
      sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.9F, 1.4F);
   }

   private static void pushApart(LivingEntity e, double[] c) {
      if (!e.isRemoved() && e.isAlive()) {
         double dx = e.getX() - c[0];
         double dz = e.getZ() - c[2];
         double d = Math.hypot(dx, dz);
         if (!(d < 0.001)) {
            e.setDeltaMovement(dx / d * 0.8, 0.35, dz / d * 0.8);
            if (e instanceof ServerPlayer p) {
               p.connection.send(new ClientboundSetEntityMotionPacket(p));
            }
         }
      }
   }

   static double[] center(BrosHandler.Session s) {
      return new double[]{s.cx, Math.min(s.ya, s.yb), s.cz};
   }

   static boolean insideDome(double[] c, Entity e) {
      double y = e.getY();
      return !(y < c[1] - 1.0) && !(y > c[1] + 3.0) ? Math.hypot(e.getX() - c[0], e.getZ() - c[2]) <= RADIUS : false;
   }

   static boolean insideDome(BrosHandler.Session s, Entity e) {
      return s.world == e.level() && insideDome(center(s), e);
   }

   private record Memory(float frac, long atMs) {
   }
}
