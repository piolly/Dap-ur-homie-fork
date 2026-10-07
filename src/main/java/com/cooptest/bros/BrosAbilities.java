package com.cooptest.bros;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class BrosAbilities {
   public static final int SYNC_WINDOW_TICKS = 8;
   public static int RUSH_TICKS = 8;
   public static double RUSH_SPEED = 0.75;
   public static float RUSH_DAMAGE = 18.0F;
   public static final double RUSH_KNOCKBACK = 1.4;
   public static final double RUSH_HIT_RADIUS = 1.6;
   public static int RUSH_COOLDOWN_TICKS = 120;
   public static float RUSH_SHIELD_COST = 6.0F;
   public static boolean RUSH_BREAKS_BLOCKS = true;
   public static float RUSH_MAX_HARDNESS = 3.0F;
   public static final int RUSH_MAX_BLOCKS_TICK = 14;
   public static double PULSE_RADIUS = 10.0;
   public static float PULSE_DAMAGE = 10.0F;
   public static double PULSE_KNOCKBACK = 1.6;
   public static float PULSE_SHIELD_COST = 8.0F;
   public static int PULSE_COOLDOWN_TICKS = 160;
   public static float BUNKER_DRAIN = 0.25F;
   public static float BUNKER_DAMAGE_THROUGH = 0.08F;
   public static int RECHARGE_FULL_TICKS = 80;
   public static float RECHARGE_HEALTH_COST = 0.5F;
   public static float RECHARGE_MIN_HEALTH = 6.0F;

   private BrosAbilities() {
   }

   static void onAbility(MinecraftServer server, ServerPlayer p, byte kind, boolean down) {
      BrosHandler.Session s = BrosHandler.sessionOf(p);
      if (s != null && !s.ended && s.phase == 2) {
         boolean isA = p == s.a;
         switch (kind) {
            case 0:
               if (s.ticks < s.rushCdUntil) {
                  cooldownMsg(p, "Rush", s.rushCdUntil - s.ticks);
                  return;
               }

               if (s.bunkering) {
                  p.sendOverlayMessage(Component.literal("§7Let go of the bunker first"));
                  return;
               }

               if (s.shieldHp <= RUSH_SHIELD_COST) {
                  p.sendOverlayMessage(Component.literal("§cShield too weak to rush"));
                  return;
               }

               if (isA) {
                  s.rushVoteA = s.ticks;
               } else {
                  s.rushVoteB = s.ticks;
               }

               if (agreed(s, s.rushVoteA, s.rushVoteB)) {
                  startRush(s);
               }
               break;
            case 1:
               if (s.ticks < s.pulseCdUntil) {
                  cooldownMsg(p, "Pulse", s.pulseCdUntil - s.ticks);
                  return;
               }

               if (isA) {
                  s.pulseVoteA = s.ticks;
               } else {
                  s.pulseVoteB = s.ticks;
               }

               if (agreed(s, s.pulseVoteA, s.pulseVoteB)) {
                  firePulse(server, s);
               }
               break;
            case 2:
               if (isA) {
                  s.holdA = down;
               } else {
                  s.holdB = down;
               }
               break;
            case 3:
               if (down && s.ticks < s.pulseCdUntil) {
                  cooldownMsg(p, "Bunker", s.pulseCdUntil - s.ticks);
                  if (isA) {
                     s.bunkerA = false;
                  } else {
                     s.bunkerB = false;
                  }

                  return;
               }

               if (isA) {
                  s.bunkerA = down;
               } else {
                  s.bunkerB = down;
               }
         }
      }
   }

   private static boolean agreed(BrosHandler.Session s, int voteA, int voteB) {
      boolean dummy = s.b instanceof ArmorStand;
      return dummy ? voteA >= 0 && s.ticks - voteA <= 8 : voteA >= 0 && voteB >= 0 && Math.abs(voteA - voteB) <= 8 && s.ticks - Math.min(voteA, voteB) <= 8;
   }

   static void tick(MinecraftServer server, BrosHandler.Session s) {
      s.rushVoteA = expire(s, s.rushVoteA, s.a, "G");
      s.rushVoteB = expire(s, s.rushVoteB, s.b, "G");
      s.pulseVoteA = expire(s, s.pulseVoteA, s.a, "H");
      s.pulseVoteB = expire(s, s.pulseVoteB, s.b, "H");
      boolean dummyB = s.b instanceof ArmorStand;
      boolean bothBunker = s.bunkerA && (dummyB || s.bunkerB);
      boolean wasBunkering = s.bunkering;
      s.bunkering = bothBunker && s.rushTicksLeft <= 0 && s.ticks >= s.pulseCdUntil && s.shieldHp > 0.2F;
      if (s.bunkering && s.world instanceof ServerLevel bw) {
         s.shieldHp = Math.max(0.1F, s.shieldHp - BUNKER_DRAIN);
         double by = Math.min(s.ya, s.yb);
         if (!wasBunkering) {
            bw.playSound(null, s.cx, by + 1.0, s.cz, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.6F);
         }

         if (s.ticks % 3 == 0) {
            for (int i = 0; i < 8; i++) {
               double a = (Math.PI * 2) * i / 8.0 + s.ticks * 0.08;
               bw.sendParticles(ParticleTypes.END_ROD, s.cx + Math.cos(a) * 1.4, by + 0.4 + i % 2 * 0.8, s.cz + Math.sin(a) * 1.4, 1, 0.0, 0.0, 0.0, 0.0);
            }
         }

         if (s.shieldHp <= 0.2F) {
            s.bunkering = false;
            bw.playSound(null, s.cx, by + 1.0, s.cz, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
         }
      } else if (wasBunkering && s.world instanceof ServerLevel bw2) {
         bw2.playSound(null, s.cx, Math.min(s.ya, s.yb) + 1.0, s.cz, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.2F);
      }

      boolean dummy = s.b instanceof ArmorStand;
      boolean bothHold = s.holdA && (dummy || s.holdB);
      boolean inCombat = s.ticks - s.lastHitTick < BrosShield.HEAL_COMBAT_PAUSE_TICKS;
      boolean canCharge = bothHold && s.rushTicksLeft <= 0 && !s.bunkering && !inCombat && s.shieldHp < BrosShield.SHIELD_HP && s.phase == 2;
      s.charging = bothHold && s.rushTicksLeft <= 0;
      ServerPlayer pa = s.a instanceof ServerPlayer p1 ? p1 : null;
      ServerPlayer pb = s.b instanceof ServerPlayer p2 ? p2 : null;
      int payers = (pa != null ? 1 : 0) + (pb != null ? 1 : 0);
      float gain = BrosShield.SHIELD_HP / RECHARGE_FULL_TICKS;
      float costEach = payers == 0 ? 0.0F : gain * RECHARGE_HEALTH_COST / payers;
      s.lowHp = pa != null && pa.getHealth() - costEach < RECHARGE_MIN_HEALTH || pb != null && pb.getHealth() - costEach < RECHARGE_MIN_HEALTH;
      s.filling = canCharge && !s.lowHp;
      if (s.filling && s.world instanceof ServerLevel sw) {
         if (pa != null) {
            pa.setHealth(pa.getHealth() - costEach);
         }

         if (pb != null) {
            pb.setHealth(pb.getHealth() - costEach);
         }

         s.shieldHp = Math.min(BrosShield.SHIELD_HP, s.shieldHp + gain);
         double y = Math.min(s.ya, s.yb);
         if (s.ticks % 5 == 0) {
            float fill = s.shieldHp / BrosShield.SHIELD_HP;
            sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.cx, y + 0.2, s.cz, 3, 0.6, 0.05, 0.6, 0.02);
            sw.playSound(null, s.cx, y + 1.0, s.cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 0.8F + fill);
         }

         if (s.shieldHp >= BrosShield.SHIELD_HP) {
            sw.playSound(null, s.cx, y + 1.0, s.cz, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.4F);
            sw.sendParticles(ParticleTypes.END_ROD, s.cx, y + 1.5, s.cz, 10, 0.8, 0.5, 0.8, 0.02);
         }
      }
   }

   static int hudFlags(BrosHandler.Session s) {
      boolean dummy = s.b instanceof ArmorStand;
      boolean rA = pending(s, s.rushVoteA);
      boolean pA = pending(s, s.pulseVoteA);
      boolean rB = dummy ? rA : pending(s, s.rushVoteB);
      boolean pB = dummy ? pA : pending(s, s.pulseVoteB);
      boolean hB = dummy ? s.holdA : s.holdB;
      int f = 0;
      if (rA) {
         f |= 1;
      }

      if (rB) {
         f |= 2;
      }

      if (pA) {
         f |= 4;
      }

      if (pB) {
         f |= 8;
      }

      if (s.holdA) {
         f |= 16;
      }

      if (hB) {
         f |= 32;
      }

      if (s.filling) {
         f |= 64;
      }

      if (s.ticks - s.lastHitTick < BrosShield.HEAL_COMBAT_PAUSE_TICKS) {
         f |= 128;
      }

      if (s.lowHp) {
         f |= 256;
      }

      if (s.shieldHp > PULSE_SHIELD_COST) {
         f |= 512;
      }

      if (s.shieldHp > RUSH_SHIELD_COST) {
         f |= 1024;
      }

      if (s.bunkering) {
         f |= 2048;
      }

      return f;
   }

   private static boolean pending(BrosHandler.Session s, int vote) {
      return vote >= 0 && s.ticks - vote <= 8;
   }

   private static int expire(BrosHandler.Session s, int vote, LivingEntity who, String key) {
      if (vote >= 0 && s.ticks - vote > 8) {
         if (who instanceof ServerPlayer p) {
            p.sendOverlayMessage(Component.literal("§7Your bro didn't press §f" + key));
         }

         return -1;
      } else {
         return vote;
      }
   }

   private static void startRush(BrosHandler.Session s) {
      s.rushVoteA = -1;
      s.rushVoteB = -1;
      s.shieldHp = Math.max(0.1F, s.shieldHp - RUSH_SHIELD_COST);
      s.holdA = false;
      s.holdB = false;
      s.charging = false;
      double[] f = BrosHandler.forward(s.heading);
      s.rushDx = f[0];
      s.rushDz = f[1];
      s.rushTicksLeft = RUSH_TICKS;
      s.rushCdUntil = s.ticks + RUSH_COOLDOWN_TICKS;
      s.rushHit.clear();
      if (s.world instanceof ServerLevel sw) {
         double y = Math.min(s.ya, s.yb) + 1.0;
         sw.playSound(null, s.cx, y, s.cz, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
         sw.playSound(null, s.cx, y, s.cz, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5F, 1.4F);
      }
   }

   static boolean tickRush(MinecraftServer server, BrosHandler.Session s) {
      if (s.rushTicksLeft > 0 && s.world instanceof ServerLevel sw) {
         s.rushTicksLeft--;
         double var10 = s.rushDx * RUSH_SPEED;
         double dz = s.rushDz * RUSH_SPEED;
         if (RUSH_BREAKS_BLOCKS) {
            smashAhead(sw, s, var10, dz);
         }

         hitAhead(sw, s);
         boolean moved = BrosHandler.tryCommit(sw, s, var10, dz, 0.0F);
         if (!moved) {
            s.rushTicksLeft = 0;
            sw.playSound(null, s.cx, Math.min(s.ya, s.yb) + 1.0, s.cz, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0F, 0.7F);
            BrosHandler.tryCommit(sw, s, 0.0, 0.0, 0.0F);
         }

         double y = Math.min(s.ya, s.yb);
         sw.sendParticles(ParticleTypes.CLOUD, s.cx - s.rushDx, y + 0.3, s.cz - s.rushDz, 2, 0.4, 0.1, 0.4, 0.01);
         if (s.rushTicksLeft == 0) {
            s.vx *= 0.3;
            s.vz *= 0.3;
         }

         return true;
      } else {
         return false;
      }
   }

   private static void smashAhead(ServerLevel sw, BrosHandler.Session s, double dx, double dz) {
      int broken = 0;
      double lx = dx * 1.5;
      double lz = dz * 1.5;

      for (LivingEntity e : new LivingEntity[]{s.a, s.b}) {
         double[] slot = BrosHandler.slotAt(s.cx + lx, s.cz + lz, s.heading, e == s.a == s.aLeft);
         double feet = e == s.a ? s.ya : s.yb;
         AABB box = e.getBoundingBox().move(slot[0] - e.getX(), feet - e.getY(), slot[1] - e.getZ()).inflate(0.15, 0.0, 0.15);
         int minY = Mth.floor(box.minY + 0.05);
         int maxY = Mth.floor(box.maxY - 0.01);

         for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), minY, Mth.floor(box.minZ), Mth.floor(box.maxX), maxY, Mth.floor(box.maxZ))) {
            if (broken >= 14) {
               return;
            }

            BlockState st = sw.getBlockState(pos);
            if (!st.isAir() && !st.hasBlockEntity() && !st.getCollisionShape(sw, pos).isEmpty()) {
               float hard = st.getDestroySpeed(sw, pos);
               if (!(hard < 0.0F) && !(hard > RUSH_MAX_HARDNESS)) {
                  sw.destroyBlock(pos.immutable(), true, breaker(s));
                  broken++;
               }
            }
         }
      }
   }

   private static void hitAhead(ServerLevel sw, BrosHandler.Session s) {
      double y = Math.min(s.ya, s.yb);
      double fx = s.cx + s.rushDx * 1.0;
      double fz = s.cz + s.rushDz * 1.0;
      AABB zone = new AABB(fx - 1.6, y - 0.5, fz - 1.6, fx + 1.6, y + 2.5, fz + 1.6);
      ServerPlayer src = breaker(s);
      DamageSource dmg = src != null ? sw.damageSources().playerAttack(src) : sw.damageSources().generic();

      for (LivingEntity t : sw.getEntitiesOfClass(LivingEntity.class, zone, e -> e.isAlive())) {
         if (t != s.a && t != s.b && !(t instanceof ArmorStand) && !(t instanceof ServerPlayer sp && sp.isSpectator()) && s.rushHit.add(t.getId())) {
            t.hurtServer(sw, dmg, RUSH_DAMAGE);
            com.cooptest.CoopKnockback.apply(t, 1.4, -s.rushDx, -s.rushDz);
            t.push(0.0, 0.35, 0.0);
            syncVelocity(t);
            sw.sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1.0, t.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            sw.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.0, t.getZ(), 8, 0.3, 0.4, 0.3, 0.3);
            sw.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
         }
      }
   }

   private static void firePulse(MinecraftServer server, BrosHandler.Session s) {
      s.pulseVoteA = -1;
      s.pulseVoteB = -1;
      if (s.world instanceof ServerLevel sw) {
         double var18 = Math.min(s.ya, s.yb);
         if (s.shieldHp <= PULSE_SHIELD_COST) {
            sw.playSound(null, s.cx, var18 + 1.0, s.cz, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.6F);

            for (LivingEntity e : new LivingEntity[]{s.a, s.b}) {
               if (e instanceof ServerPlayer p) {
                  p.sendOverlayMessage(Component.literal("§cShield too weak to pulse"));
               }
            }
         } else {
            s.shieldHp = s.shieldHp - PULSE_SHIELD_COST;
            s.pulseCdUntil = s.ticks + PULSE_COOLDOWN_TICKS;
            AABB box = new AABB(s.cx - PULSE_RADIUS, var18 - 1.0, s.cz - PULSE_RADIUS, s.cx + PULSE_RADIUS, var18 + 3.5, s.cz + PULSE_RADIUS);
            ServerPlayer src = breaker(s);
            DamageSource dmg = src != null ? sw.damageSources().playerAttack(src) : sw.damageSources().generic();

            for (LivingEntity t : sw.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive())) {
               if (t != s.a && t != s.b && !(t instanceof ArmorStand) && !(t instanceof ServerPlayer sp && sp.isSpectator())) {
                  double dx = t.getX() - s.cx;
                  double dz = t.getZ() - s.cz;
                  double d = Math.hypot(dx, dz);
                  if (!(d > PULSE_RADIUS)) {
                     if (d < 0.001) {
                        dx = 1.0;
                        dz = 0.0;
                        d = 1.0;
                     }

                     t.hurtServer(sw, dmg, PULSE_DAMAGE);
                     double falloff = 1.0 - 0.5 * (d / PULSE_RADIUS);
                     com.cooptest.CoopKnockback.apply(t, PULSE_KNOCKBACK * falloff, -dx / d, -dz / d);
                     t.push(0.0, 0.3, 0.0);
                     syncVelocity(t);
                  }
               }
            }

            for (Projectile proj : sw.getEntitiesOfClass(Projectile.class, box, p -> !p.isRemoved())) {
               if (Math.hypot(proj.getX() - s.cx, proj.getZ() - s.cz) <= PULSE_RADIUS) {
                  proj.discard();
               }
            }

            for (int ring = 0; ring < 2; ring++) {
               double r = ring == 0 ? PULSE_RADIUS * 0.45 : PULSE_RADIUS;
               int n = ring == 0 ? 16 : 28;

               for (int i = 0; i < n; i++) {
                  double a = (Math.PI * 2) * i / n;
                  sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.cx + Math.cos(a) * r, var18 + 0.6, s.cz + Math.sin(a) * r, 1, 0.0, 0.1, 0.0, 0.02);
               }
            }

            sw.sendParticles(ParticleTypes.EXPLOSION, s.cx, var18 + 1.0, s.cz, 1, 0.0, 0.0, 0.0, 0.0);
            sw.playSound(null, s.cx, var18 + 1.0, s.cz, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
            sw.playSound(null, s.cx, var18 + 1.0, s.cz, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
         }
      }
   }

   private static ServerPlayer breaker(BrosHandler.Session s) {
      if (s.a instanceof ServerPlayer p) {
         return p;
      } else {
         return s.b instanceof ServerPlayer p ? p : null;
      }
   }

   private static void syncVelocity(Entity e) {
      if (e instanceof ServerPlayer p) {
         p.connection.send(new ClientboundSetEntityMotionPacket(p));
      }
   }

   private static void cooldownMsg(ServerPlayer p, String what, int ticksLeft) {
      p.sendOverlayMessage(Component.literal(String.format("§7%s ready in §f%.1fs", what, ticksLeft / 20.0F)));
   }
}
