package com.cooptest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FlairBarrierHandler {
   public static final long BARRIER_DURATION_MS = 5000L;
   public static final double BARRIER_RADIUS = 4.0;
   public static final double BARRIER_HEIGHT = 3.0;
   public static final float HEAL_PER_TICK = 1.0F;
   public static final int HEAL_INTERVAL_TICKS = 20;
   public static final int REGEN_TICKS = 60;
   public static final int REGEN_AMPLIFIER = 0;
   public static final int RING_POINTS = 34;
   public static final int RING_INTERVAL_TICKS = 2;
   private static final List<FlairBarrierHandler.Barrier> barriers = new ArrayList<>();

   private static boolean enabled() {
      return CoopMovesConfig.get().enableFlairBarrier;
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(FlairBarrierHandler::tick);
   }

   public static void raise(ServerLevel world, Vec3 centre, boolean clean, boolean perfect) {
      if (enabled()) {
         CoopMovesConfig cfg = CoopMovesConfig.get();
         long ms = perfect ? cfg.flairBarrierPerfectMs : (clean ? cfg.flairBarrierCleanMs : cfg.flairBarrierOtherMs);
         raise(world, centre, ms);
      }
   }

   public static void raise(ServerLevel world, Vec3 centre, long durationMs) {
      if (enabled()) {
         barriers.add(new FlairBarrierHandler.Barrier(world, centre, System.currentTimeMillis() + durationMs));
         world.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.6F);
         world.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);

         for (int i = 0; i < 68; i++) {
            double a = (Math.PI * 2) * i / 68.0;

            for (double y = 0.0; y <= 3.0; y += 0.4) {
               world.sendParticles(ParticleTypes.END_ROD, centre.x + Math.cos(a) * 4.0, centre.y + y, centre.z + Math.sin(a) * 4.0, 1, 0.0, 0.0, 0.0, 0.0);
            }
         }
      }
   }

   private static void tick(MinecraftServer server) {
      if (!barriers.isEmpty()) {
         long now = System.currentTimeMillis();
         Iterator<FlairBarrierHandler.Barrier> it = barriers.iterator();

         while (it.hasNext()) {
            FlairBarrierHandler.Barrier b = it.next();
            if (now > b.expiresAt) {
               b.world.playSound(null, b.centre.x, b.centre.y, b.centre.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);
               b.world.sendParticles(ParticleTypes.CLOUD, b.centre.x, b.centre.y + 1.0, b.centre.z, 30, 2.0, 0.5, 2.0, 0.05);
               it.remove();
            } else {
               b.ticks++;
               AABB box = new AABB(b.centre.x - 4.0, b.centre.y - 1.0, b.centre.z - 4.0, b.centre.x + 4.0, b.centre.y + 3.0 + 1.0, b.centre.z + 4.0);

               for (Projectile proj : b.world.getEntitiesOfClass(Projectile.class, box, p -> true)) {
                  double d = Math.hypot(proj.position().x - b.centre.x, proj.position().z - b.centre.z);
                  if (!(d > 4.0)) {
                     b.world.sendParticles(ParticleTypes.ELECTRIC_SPARK, proj.position().x, proj.position().y, proj.position().z, 10, 0.1, 0.1, 0.1, 0.12);
                     b.world
                        .playSound(
                           null, proj.position().x, proj.position().y, proj.position().z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.7F
                        );
                     proj.discard();
                  }
               }

               if (b.ticks % 20 == 0) {
                  for (LivingEntity e : b.world.getEntitiesOfClass(LivingEntity.class, box, x -> true)) {
                     double d = Math.hypot(e.position().x - b.centre.x, e.position().z - b.centre.z);
                     if (!(d > 4.0)) {
                        e.heal(1.0F);
                        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
                     }
                  }
               }

               if (b.ticks % 2 == 0) {
                  double spin = b.ticks / 40.0;

                  for (int i = 0; i < 34; i++) {
                     double a = (Math.PI * 2) * i / 34.0 + spin;
                     double px = b.centre.x + Math.cos(a) * 4.0;
                     double pz = b.centre.z + Math.sin(a) * 4.0;

                     for (double y = 0.0; y <= 3.0; y += 0.75) {
                        b.world.sendParticles(ParticleTypes.END_ROD, px, b.centre.y + y, pz, 1, 0.02, 0.02, 0.02, 0.0);
                     }

                     if (i % 4 == 0) {
                        b.world.sendParticles(ParticleTypes.GLOW, px, b.centre.y + 0.4, pz, 1, 0.05, 0.3, 0.05, 0.01);
                     }
                  }
               }
            }
         }
      }
   }

   public static void clearAll() {
      barriers.clear();
   }

   public static boolean isProtected(Entity e) {
      for (FlairBarrierHandler.Barrier b : barriers) {
         if (b.world == e.level()) {
            double d = Math.hypot(e.position().x - b.centre.x, e.position().z - b.centre.z);
            if (d <= 4.0) {
               return true;
            }
         }
      }

      return false;
   }

   private static final class Barrier {
      final ServerLevel world;
      final Vec3 centre;
      final long expiresAt;
      int ticks = 0;

      Barrier(ServerLevel world, Vec3 centre, long expiresAt) {
         this.world = world;
         this.centre = centre;
         this.expiresAt = expiresAt;
      }
   }
}
