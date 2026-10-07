package com.cooptest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

public class HighFiveStreakHandler {
   private static final Map<UUID, HighFiveStreakHandler.Streak> streaks = new HashMap<>();

   private static boolean enabled() {
      return CoopMovesConfig.get().enableHighFiveBuffs;
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         try {
            tick(server);
         } catch (Throwable t) {
            System.err.println("[Hype] tick failed, clearing streaks: " + t);
            streaks.clear();
         }
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> streaks.clear());
   }

   public static void onFastFive(ServerPlayer p) {
      if (enabled() && p != null && p.isAlive()) {
         CoopMovesConfig c = CoopMovesConfig.get();
         int max = Math.max(1, c.hypeMaxStacks);
         HighFiveStreakHandler.Streak s = streaks.computeIfAbsent(p.getUUID(), x -> new HighFiveStreakHandler.Streak());
         int before = s.stacks;
         s.stacks = Math.min(max, s.stacks + 1);
         s.lastFiveMs = System.currentTimeMillis();
         applyBuffs(p, s.stacks, c);
         ServerLevel world = p.level();
         world.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 2.1, p.getZ(), 4 + s.stacks * 2, 0.2, 0.15, 0.2, 0.1);
         int tornadoAt = Math.max(1, c.hypeTornadoStacks);
         if (c.enableHypeTornado && before < tornadoAt && s.stacks >= tornadoAt) {
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.3F);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
         }

         String bar = "§6" + "▮".repeat(s.stacks) + "§8" + "▮".repeat(max - s.stacks);
         p.displayClientMessage(Component.literal("§e§lHYPE §7[" + bar + "§7]"), true);
      }
   }

   private static void applyBuffs(ServerPlayer p, int stacks, CoopMovesConfig c) {
      int ticks = (int)Math.max(20L, c.hypeDecayMs / 50L);
      int speedAmp = Math.min(Math.max(0, c.hypeMaxSpeedAmp), stacks - 1);
      p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, speedAmp, false, true));
      if (stacks >= 3) {
         p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, ticks, stacks >= 5 ? 1 : 0, false, true));
      }
   }

   private static void tick(MinecraftServer server) {
      if (!streaks.isEmpty()) {
         CoopMovesConfig c = CoopMovesConfig.get();
         long now = System.currentTimeMillis();
         Iterator<Entry<UUID, HighFiveStreakHandler.Streak>> it = streaks.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, HighFiveStreakHandler.Streak> e = it.next();
            HighFiveStreakHandler.Streak s = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && p.isAlive()) {
               if (now - s.lastFiveMs >= c.hypeDecayMs) {
                  p.displayClientMessage(Component.literal("§8Hype faded"), true);
                  it.remove();
               } else {
                  s.ticks++;
                  if (c.enableHypeTornado && s.stacks >= Math.max(1, c.hypeTornadoStacks)) {
                     tornado(p, s, c);
                  }
               }
            } else {
               it.remove();
            }
         }
      }
   }

   private static void tornado(ServerPlayer p, HighFiveStreakHandler.Streak s, CoopMovesConfig c) {
      if (p.level() instanceof ServerLevel w) {
         int var30 = s.stacks - Math.max(1, c.hypeTornadoStacks);
         double r = c.hypeTornadoRadius + var30 * c.hypeTornadoRadiusPerStack;
         double height = 3.0 + var30 * 0.4;
         double x = p.getX();
         double y = p.getY();
         double z = p.getZ();
         int points = Math.max(1, c.hypeTornadoParticles);
         double spin = s.ticks * 0.55;

         for (int box = 0; box < points; box++) {
            double k = (box + 0.5) / points;
            double a = spin + box * 2.4;
            double rr = r * (0.3 + 0.7 * k);
            w.sendParticles(ParticleTypes.CLOUD, x + Math.cos(a) * rr, y + k * height, z + Math.sin(a) * rr, 1, 0.0, 0.0, 0.0, 0.0);
         }

         if (s.ticks % 10 == 0) {
            w.sendParticles(ParticleTypes.GUST, x, y + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0);
         }

         if (s.ticks % 30 == 0) {
            w.playSound(null, x, y, z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35F, 1.3F);
         }

         AABB box = new AABB(x - r, y - 1.0, z - r, x + r, y + height, z + r);

         for (LivingEntity t : w.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != p)) {
            if (!(t instanceof ArmorStand)
               && !(t instanceof ServerPlayer sp && (sp.isSpectator() || getStacks(sp.getUUID()) > 0))
               && t != p.getVehicle()
               && !t.hasPassenger(p)) {
               double dx = t.getX() - x;
               double dz = t.getZ() - z;
               double d = Math.hypot(dx, dz);
               if (!(d > r)) {
                  if (d < 0.001) {
                     dx = 1.0;
                     dz = 0.0;
                     d = 1.0;
                  }

                  dx /= d;
                  dz /= d;
                  double push = c.hypeTornadoPush * (1.0 - 0.4 * d / r);
                  t.setDeltaMovement(dx * push - dz * push * 0.4, Math.max(t.getDeltaMovement().y, 0.42), dz * push + dx * push * 0.4);
                  t.needsSync = true;
                  if (t instanceof ServerPlayer sp) {
                     sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                  }
               }
            }
         }
      }
   }

   public static void cleanup(UUID id) {
      streaks.remove(id);
   }

   public static int getStacks(UUID id) {
      HighFiveStreakHandler.Streak s = streaks.get(id);
      return s == null ? 0 : s.stacks;
   }

   private static final class Streak {
      int stacks;
      long lastFiveMs;
      int ticks;
   }
}
