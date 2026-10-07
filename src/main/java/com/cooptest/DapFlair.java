package com.cooptest;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DapFlair {
   public static final double TIER_BASE_MULT = 1.6;
   public static final double TIER_MULT_STEP = 0.9;
   public static final int BASE_PARTICLE_COUNT = 34;
   public static final double BASE_SPREAD = 0.4;
   public static final long PRE_IMPACT_MS = 180L;
   public static final long POST_IMPACT_MS = 340L;
   public static final long MIN_WINDOW_MS = 520L;
   public static final float FLAIR_SHAKE = 0.55F;
   public static final long FLAIR_SHAKE_MS = 200L;
   public static final int CLEAN_REGEN_TICKS = 280;
   public static final int CLEAN_REGEN_AMP = 0;
   public static final int CLEAN_RESISTANCE_TICKS = 160;
   public static final int CLEAN_RESISTANCE_AMP = 0;
   public static final int PERFECT_BUFF_TICKS = 700;
   public static final int PERFECT_HASTE_AMP = 2;
   public static final int PERFECT_SPEED_AMP = 2;
   public static final int PERFECT_STRENGTH_AMP = 1;
   public static final float BLAST_MAX_DAMAGE = 16.0F;
   public static final double BLAST_RADIUS = 8.0;
   public static final double BLAST_KNOCKBACK = 2.1;
   public static final double BLAST_KNOCKUP = 0.7;
   public static final float CLEAN_BLAST_SHAKE = 1.4F;
   public static final int CLEAN_BLAST_SHAKE_MS = 260;
   public static final double SHOCKWAVE_RADIUS = 10.0;
   public static final int SHOCKWAVE_STEPS = 12;
   public static final float RARE_CHANCE = 0.12F;
   private static final Map<UUID, DapFlair.Flair> windows = new HashMap<>();
   public static final String[] RARE_LABELS = new String[]{
      "§d§lINSANE DAP!!", "§5§lIMMACULATE!!", "§c§lCERTIFIED!!", "§a§lLEGENDARY DAP!!", "§9§lUNREAL!!", "§6§l✦ GOATED ✦"
   };

   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(DapFlair.FlairWindowPayload.ID, DapFlair.FlairWindowPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(DapFlair.FlairPressPayload.ID, DapFlair.FlairPressPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DapFlair.FlairSuccessPayload.ID, DapFlair.FlairSuccessPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(DapFlair.FlairShakePayload.ID, DapFlair.FlairShakePayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(DapFlair.FlairPressPayload.ID, (payload, ctx) -> {
         ServerPlayer player = ctx.player();
         ctx.server().execute(() -> onButtonPress(player, "G"));
      });
   }

   public static void open(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2, long impactInMs) {
      open(world, pos, p1, p2, impactInMs, 1);
   }

   public static void open(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2, long impactInMs, int tier) {
      open(world, pos, p1, p2, impactInMs, tier, false);
   }

   public static void open(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2, long impactInMs, int tier, boolean clean) {
      open(world, pos, p1, p2, impactInMs, tier, clean, false);
   }

   public static void openDapRun(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2, long impactInMs, int runTier) {
      open(world, pos, p1, p2, impactInMs, 3, false, false);
      DapFlair.Flair f = windows.get(p1.getUUID());
      if (f != null) {
         f.dapRun = runTier;
      }
   }

   public static void open(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2, long impactInMs, int tier, boolean clean, boolean perfect) {
      long now = System.currentTimeMillis();
      long impactAt = now + Math.max(0L, impactInMs);
      long opens = Math.max(now, impactAt - 180L);
      long closes = Math.max(impactAt + 340L, opens + 520L);
      DapFlair.Flair f = new DapFlair.Flair(p1.getUUID(), p2.getUUID(), opens, closes);
      f.tier = tier;
      f.clean = clean;
      f.perfect = perfect;
      windows.put(p1.getUUID(), f);
      windows.put(p2.getUUID(), f);
      MinecraftServer server = p1.level().getServer();
      if (server != null) {
         UUID id1 = p1.getUUID();
         UUID id2 = p2.getUUID();
         long delay = Math.max(1L, closes - now);
         int armedMs = (int)(closes - now);
         int promptDelay = (int)Math.max(0L, opens - now);
         DapFlair.FlairWindowPayload armed = new DapFlair.FlairWindowPayload(true, armedMs, promptDelay, tier);
         ServerPlayNetworking.send(p1, armed);
         ServerPlayNetworking.send(p2, armed);
         new Thread(() -> {
            try {
               Thread.sleep(delay);
            } catch (InterruptedException ignored) {
               return;
            }

            server.execute(() -> resolve(server, world, pos, id1, id2));
         }).start();
      }
   }

   public static boolean onButtonPress(ServerPlayer player, String button) {
      if (!"G".equals(button)) {
         return false;
      }

      UUID id = player.getUUID();
      DapFlair.Flair f = windows.get(id);
      if (f == null) {
         return false;
      }

      long now = System.currentTimeMillis();
      if (now < f.opensAt) {
         f.burned = true;
         player.sendOverlayMessage(Component.literal("§8too early"));
         return true;
      }

      if (now > f.closesAt) {
         return true;
      }

      boolean isA = id.equals(f.a);
      boolean already = isA ? f.pressedA : f.pressedB;
      if (!already && !f.burned) {
         if (isA) {
            f.pressedA = true;
         } else {
            f.pressedB = true;
         }

         return true;
      } else {
         f.burned = true;
         player.sendOverlayMessage(Component.literal("§8flair burned"));
         return true;
      }
   }

   private static boolean resolve(MinecraftServer server, ServerLevel world, Vec3 pos, UUID id1, UUID id2) {
      DapFlair.Flair f = windows.remove(id1);
      windows.remove(id2);
      if (f == null) {
         return false;
      }

      if (!f.burned && f.pressedA && f.pressedB) {
         ServerPlayer p1 = server.getPlayerList().getPlayer(id1);
         ServerPlayer p2 = server.getPlayerList().getPlayer(id2);
         double m = 1.6 + 0.9 * Math.max(0, f.tier - 1);
         int n = (int)Math.round(34.0 * m);
         double spread = 0.4 * m;
         world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, (int)(n * 1.6), spread, spread, spread, 0.22 * m);
         world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, (int)(n * 1.2), spread, spread, spread, 0.18 * m);
         world.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, n, spread, spread, spread, 0.14 * m);
         world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, n, spread, spread, spread, 0.14 * m);
         world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, (int)(n * 0.8), spread, spread, spread, 0.2 * m);
         if (f.tier >= 2) {
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, (int)(n * 0.7), 0.18 * m, 0.55 * m, 0.18 * m, 0.3 * m);
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, (int)(n * 0.4), 0.2 * m, 0.4 * m, 0.2 * m, 0.14 * m);
         }

         if (f.tier >= 3) {
            world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.4, 0.4, 0.4, 0.0);
            ring(world, pos, 1.6 * m, (int)Math.round(28.0 * m), ParticleTypes.END_ROD);
         }

         if (f.tier >= 4) {
            world.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, (int)(n * 1.1), spread, spread, spread, 0.24 * m);
            world.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, (int)(n * 0.3), spread * 0.6, spread * 0.6, spread * 0.6, 0.0);
            ring(world, pos, 2.2 * m, (int)Math.round(36.0 * m), ParticleTypes.FLAME);
         }

         if (f.tier >= 5) {
            for (int y = 0; y < 20; y++) {
               world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + y * 0.45, pos.z, 4, 0.18, 0.04, 0.18, 0.02);
               world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + y * 0.45, pos.z, 2, 0.25, 0.04, 0.25, 0.03);
            }

            world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y + 1.0, pos.z, (int)(n * 2.0), 1.2, 1.2, 1.2, 0.35);
         }

         ring(world, pos, 1.0 * m, (int)Math.round(20.0 * m), ParticleTypes.ELECTRIC_SPARK);
         ring(world, pos, 2.6 * m, (int)Math.round(40.0 * m), ParticleTypes.FIREWORK);

         for (int y = 0; y < 7; y++) {
            double yy = pos.y + y * 0.28;
            world.sendParticles(ParticleTypes.WAX_OFF, pos.x, yy, pos.z, (int)(n * 0.22), 0.3 * m, 0.05, 0.3 * m, 0.1 * m);
            world.sendParticles(ParticleTypes.GLOW, pos.x, yy, pos.z, (int)(n * 0.18), 0.26 * m, 0.05, 0.26 * m, 0.07 * m);
         }

         world.sendParticles(ParticleTypes.SCRAPE, pos.x, pos.y + 0.4, pos.z, (int)(n * 0.5), spread * 1.4, spread * 0.7, spread * 1.4, 0.26 * m);
         world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.9, pos.z, (int)(n * 0.6), 0.1, 0.55 * m, 0.1, 0.42 * m);
         if (f.clean) {
            shockwave(world, pos);
            if (CoopMovesConfig.get().enableFlairBlast) {
               blast(server, world, pos, id1, id2);
            }

            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_FOLLOW, SoundSource.PLAYERS, 1.0F, 1.0F);
         } else {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.PERFECT_DAP, SoundSource.PLAYERS, 1.0F, 1.0F);
         }

         world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 1.0F, 1.0F);
         world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 1.7F);
         if (CoopMovesConfig.get().enableFlairBuffs) {
            grantRewards(p1, f);
            grantRewards(p2, f);
         }

         float shakeAmt = (float)(0.55F * m) * (f.clean ? 1.8F : 1.0F);
         DapFlair.FlairSuccessPayload ok = new DapFlair.FlairSuccessPayload(shakeAmt, 200);
         if (p1 != null) {
            ServerPlayNetworking.send(p1, ok);
            p1.sendOverlayMessage(Component.literal("§b§lFLAIR!"));
         }

         if (p2 != null) {
            ServerPlayNetworking.send(p2, ok);
            p2.sendOverlayMessage(Component.literal("§b§lFLAIR!"));
         }

         return true;
      } else {
         return false;
      }
   }

   private static void grantRewards(ServerPlayer p, DapFlair.Flair f) {
      if (p != null) {
         if (f.dapRun > 0) {
            CoopMovesConfig c = CoopMovesConfig.get();
            boolean perfectRun = f.dapRun >= 3;
            int secs = perfectRun ? c.dapRunFlairPerfectSpeedSec : c.dapRunFlairSprintSpeedSec;
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, Math.max(1, secs) * 20, perfectRun ? 1 : 0, false, true));
            DapRunHandler.onRunFlair(p, perfectRun);
            p.sendOverlayMessage(Component.literal(perfectRun ? "§b§lRUN FLAIR §7— speed II" : "§b§lRUN FLAIR §7— speed I"));
         } else {
            if (f.perfect) {
               DapHearts.flair(p, false);
               p.addEffect(new MobEffectInstance(MobEffects.HASTE, 700, 2, false, true));
               p.addEffect(new MobEffectInstance(MobEffects.SPEED, 700, 2, false, true));
               p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 700, 1, false, true));
               p.sendOverlayMessage(Component.literal("§e§lPERFECT FLAIR §7— haste III + speed III + strength II"));
            } else if (f.clean) {
               DapHearts.flair(p, true);
               p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 280, 0, false, true));
               p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 0, false, true));
               p.sendOverlayMessage(Component.literal("§6§lCLEAN FLAIR §7— regen + resistance"));
            }
         }
      }
   }

   private static void blast(MinecraftServer server, ServerLevel world, Vec3 pos, UUID id1, UUID id2) {
      AABB box = new AABB(pos.x - 8.0, pos.y - 8.0, pos.z - 8.0, pos.x + 8.0, pos.y + 8.0, pos.z + 8.0);

      for (LivingEntity victim : world.getEntitiesOfClass(LivingEntity.class, box, e -> {
         if (!e.isAlive()) {
            return false;
         }

         UUID eid = e.getUUID();
         return !eid.equals(id1) && !eid.equals(id2);
      })) {
         Vec3 away = victim.position().subtract(pos);
         double dist = away.length();
         if (!(dist > 8.0)) {
            float dmg = 16.0F;
            double kbScale = 0.45 + 0.55 * (1.0 - dist / 8.0);
            Vec3 dir = dist < 0.001 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            victim.setDeltaMovement(dir.x * 2.1 * kbScale, 0.7 * kbScale, dir.z * 2.1 * kbScale);
            victim.hurtMarked = true;
            if (dmg > 0.5F) {
               victim.hurtServer(world, world.damageSources().generic(), dmg);
            }
         }
      }

      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.4F, 0.8F);

      for (int ring = 0; ring < 3; ring++) {
         double r = 1.5 + ring * 2.2;
         int pts = 24 + ring * 10;

         for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2) * i / pts;
            world.sendParticles(ParticleTypes.CRIT, pos.x + Math.cos(a) * r, pos.y + 0.15, pos.z + Math.sin(a) * r, 1, 0.03, 0.03, 0.03, 0.01);
            world.sendParticles(ParticleTypes.END_ROD, pos.x + Math.cos(a) * r, pos.y + 0.4, pos.z + Math.sin(a) * r, 1, 0.02, 0.1, 0.02, 0.0);
         }
      }

      world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.3, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
      DapFlair.FlairShakePayload blastShake = new DapFlair.FlairShakePayload(1.4F, 260);
      ServerPlayer sa = server.getPlayerList().getPlayer(id1);
      ServerPlayer sb = server.getPlayerList().getPlayer(id2);
      if (sa != null) {
         ServerPlayNetworking.send(sa, blastShake);
      }

      if (sb != null) {
         ServerPlayNetworking.send(sb, blastShake);
      }
   }

   private static void ring(ServerLevel world, Vec3 pos, double radius, int points, ParticleOptions effect) {
      for (int i = 0; i < points; i++) {
         double ang = (Math.PI * 2) * i / points;
         world.sendParticles(effect, pos.x + Math.cos(ang) * radius, pos.y, pos.z + Math.sin(ang) * radius, 1, 0.0, 0.02, 0.0, 0.01);
      }
   }

   public static void shockwave(ServerLevel world, Vec3 pos) {
      MinecraftServer server = world.getServer();
      if (server != null) {
         new Thread(() -> {
            for (int step = 1; step <= 12; step++) {
               double r = 10.0 * (step / 12.0);
               server.execute(() -> {
                  int points = (int)Math.max(12.0, r * 10.0);

                  for (int i = 0; i < points; i++) {
                     double ang = (Math.PI * 2) * i / points;
                     double x = pos.x + Math.cos(ang) * r;
                     double z = pos.z + Math.sin(ang) * r;
                     world.sendParticles(ParticleTypes.CLOUD, x, pos.y, z, 1, 0.0, 0.02, 0.0, 0.01);
                     if ((i & 1) == 0) {
                        world.sendParticles(ParticleTypes.END_ROD, x, pos.y + 0.1, z, 1, 0.0, 0.03, 0.0, 0.02);
                     }
                  }
               });

               try {
                  Thread.sleep(50L);
               } catch (InterruptedException ignored) {
                  return;
               }
            }
         }).start();
      }
   }

   public static Object[] flavour(Random rng, boolean clean) {
      String[] common = clean
         ? new String[]{"§b§lCLEAN DAP!", "§b§lCRISP!", "§3§lSMOOTH!", "§b§lNICE ONE!"}
         : new String[]{"§6§lGREAT DAP!", "§e§lSOLID!", "§6§lNOT BAD!", "§e§lTHAT'LL DO!"};
      return rng.nextFloat() < 0.12F
         ? new Object[]{RARE_LABELS[rng.nextInt(RARE_LABELS.length)], true}
         : new Object[]{common[rng.nextInt(common.length)], false};
   }

   public static void playRareEffect(ServerLevel world, Vec3 pos, String label) {
      int idx = 0;

      for (int i = 0; i < RARE_LABELS.length; i++) {
         if (RARE_LABELS[i].equals(label)) {
            idx = i;
            break;
         }
      }

      switch (idx) {
         case 0:
            world.sendParticles(ParticleTypes.WITCH, pos.x, pos.y, pos.z, 90, 0.8, 0.8, 0.8, 0.35);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.35F, 1.9F);
            break;
         case 1:
            for (int y = 0; y < 14; y++) {
               world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + y * 0.35, pos.z, 6, 0.12, 0.02, 0.12, 0.01);
            }

            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 2.0F);
            break;
         case 2:
            world.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 24, 0.4, 0.4, 0.4, 0.02);
            world.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 70, 0.6, 0.6, 0.6, 0.12);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
            break;
         case 3:
            for (int i = 0; i < 60; i++) {
               double a = i * 0.45;
               double r = 0.15 + i * 0.02;
               world.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x + Math.cos(a) * r, pos.y + i * 0.05, pos.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }

            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.2F);
            break;
         case 4:
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 70, 0.7, 0.7, 0.7, 0.16);
            world.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 120, 0.9, 0.9, 0.9, 0.6);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.6F);
            break;
         default:
            world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 140, 0.9, 1.0, 0.9, 0.45);

            for (int y = 0; y < 18; y++) {
               world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y + y * 0.4, pos.z, 4, 0.2, 0.05, 0.2, 0.03);
            }

            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.0F, 1.0F);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 0.7F);
      }
   }

   public static boolean hasWindow(UUID id) {
      return windows.containsKey(id);
   }

   public static void cleanup(UUID id) {
      DapFlair.Flair f = windows.remove(id);
      if (f != null) {
         windows.remove(f.a);
         windows.remove(f.b);
      }
   }

   private static final class Flair {
      final UUID a;
      final UUID b;
      final long opensAt;
      final long closesAt;
      int tier;
      boolean clean;
      boolean perfect;
      int dapRun;
      boolean pressedA;
      boolean pressedB;
      boolean burned;

      Flair(UUID a, UUID b, long opensAt, long closesAt) {
         this.a = a;
         this.b = b;
         this.opensAt = opensAt;
         this.closesAt = closesAt;
      }
   }

   public record FlairPressPayload() implements CustomPacketPayload {
      public static final Type<DapFlair.FlairPressPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "dap_flair_press"));
      public static final StreamCodec<FriendlyByteBuf, DapFlair.FlairPressPayload> CODEC = StreamCodec.unit(new DapFlair.FlairPressPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FlairShakePayload(float shake, int shakeMs) implements CustomPacketPayload {
      public static final Type<DapFlair.FlairShakePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "dap_flair_shake"));
      public static final StreamCodec<FriendlyByteBuf, DapFlair.FlairShakePayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeFloat(p.shake);
         buf.writeInt(p.shakeMs);
      }, buf -> new DapFlair.FlairShakePayload(buf.readFloat(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FlairSuccessPayload(float shake, int shakeMs) implements CustomPacketPayload {
      public static final Type<DapFlair.FlairSuccessPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "dap_flair_success"));
      public static final StreamCodec<FriendlyByteBuf, DapFlair.FlairSuccessPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeFloat(p.shake);
         buf.writeInt(p.shakeMs);
      }, buf -> new DapFlair.FlairSuccessPayload(buf.readFloat(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FlairWindowPayload(boolean open, int durationMs, int promptDelayMs, int tier) implements CustomPacketPayload {
      public static final Type<DapFlair.FlairWindowPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "dap_flair_window"));
      public static final StreamCodec<FriendlyByteBuf, DapFlair.FlairWindowPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeBoolean(p.open);
         buf.writeInt(p.durationMs);
         buf.writeInt(p.promptDelayMs);
         buf.writeInt(p.tier);
      }, buf -> new DapFlair.FlairWindowPayload(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
