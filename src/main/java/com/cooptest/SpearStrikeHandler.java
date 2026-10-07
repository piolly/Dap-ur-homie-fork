package com.cooptest;

import com.cooptest.bros.BrosHandler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpearStrikeHandler {
   private static final Map<UUID, SpearStrikeHandler.State> states = new HashMap<>();
   private static final List<SpearStrikeHandler.NukeStage> nukeStages = new ArrayList<>();
   private static int serverTick;
   private static boolean missileOn = false;
   private static final long THROWN_WINDOW_MS = 6000L;

   private SpearStrikeHandler() {
   }

   public static void registerPayloads() {
      PayloadTypeRegistry.playC2S().register(SpearStrikeHandler.SpearHoldPayload.ID, SpearStrikeHandler.SpearHoldPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(SpearStrikeHandler.SpearAckPayload.ID, SpearStrikeHandler.SpearAckPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(SpearStrikeHandler.SpearLockPayload.ID, SpearStrikeHandler.SpearLockPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(SpearStrikeHandler.SpearPosePayload.ID, SpearStrikeHandler.SpearPosePayload.CODEC);
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isPassenger() && p.getVehicle() instanceof ServerPlayer) {
               states.computeIfAbsent(p.getUUID(), k -> new SpearStrikeHandler.State()).lastRodePlayerMs = System.currentTimeMillis();
            }
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(SpearStrikeHandler.SpearAckPayload.ID, (p, ctx) -> {
         SpearStrikeHandler.State s = states.computeIfAbsent(ctx.player().getUUID(), k -> new SpearStrikeHandler.State());
         if (p.steering()) {
            s.clientSteerAckMs = System.currentTimeMillis();
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(SpearStrikeHandler.SpearHoldPayload.ID, (p, ctx) -> {
         SpearStrikeHandler.State s = states.computeIfAbsent(ctx.player().getUUID(), k -> new SpearStrikeHandler.State());
         s.holding = p.holding();
         if (!s.holding) {
            s.lockedId = -1;
            s.missileSpent = false;
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         serverTick++;

         try {
            tickNukes();
         } catch (Throwable t) {
            System.err.println("[Spear] nuke failed: " + t);
            nukeStages.clear();
         }

         try {
            tick(server);
         } catch (Throwable t) {
            System.err.println("[Spear] tick failed: " + t);
            states.clear();
         }
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
         states.clear();
         nukeStages.clear();
      });
      ServerLifecycleEvents.SERVER_STARTING.register((ServerStarting)server -> missileOn = CoopMovesConfig.get().spearMissileMode);
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, reg, env) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("spearmissile")
                           .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                        .executes(ctx -> {
                           ((CommandSourceStack)ctx.getSource())
                              .sendSuccess(() -> Component.literal("§6Spear missile mode is " + (missileOn ? "§aON" : "§cOFF")), false);
                           return 1;
                        }))
                     .then(Commands.literal("on").executes(ctx -> setMissile((CommandSourceStack)ctx.getSource(), true))))
                  .then(Commands.literal("off").executes(ctx -> setMissile((CommandSourceStack)ctx.getSource(), false)))
            )
         );
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, reg, env) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("testspear")
                     .executes(ctx -> testLaunch(((CommandSourceStack)ctx.getSource()).getPlayer(), false)))
                  .then(Commands.literal("fire").executes(ctx -> testLaunch(((CommandSourceStack)ctx.getSource()).getPlayer(), true)))
            )
         );
   }

   private static int setMissile(CommandSourceStack src, boolean on) {
      missileOn = on;
      src.getServer()
         .getPlayerList()
         .broadcastSystemMessage(
            Component.literal(on ? "§c§l☢ SPEAR MISSILES ARMED §7— jump, hold right-click with a spear, aim at anything" : "§7Spear missiles disarmed"), false
         );
      return 1;
   }

   private static void debug(ServerPlayer p, String why) {
      if (p.tickCount % 10 == 0) {
         p.displayClientMessage(Component.literal("§8[spear] §7" + why), true);
      }
   }

   private static void setPose(ServerPlayer p, SpearStrikeHandler.State s, boolean flying) {
      if (s.poseSent != flying) {
         s.poseSent = flying;
         SpearStrikeHandler.SpearPosePayload payload = new SpearStrikeHandler.SpearPosePayload(p.getId(), flying);

         try {
            ServerPlayNetworking.send(p, payload);

            for (ServerPlayer o : PlayerLookup.tracking(p)) {
               ServerPlayNetworking.send(o, payload);
            }
         } catch (Throwable var6) {
         }
      }
   }

   public static void cleanup(UUID id) {
      states.remove(id);
   }

   public static boolean isFlying(ServerPlayer p) {
      SpearStrikeHandler.State s = states.get(p.getUUID());
      return s != null && s.holding && !p.onGround() && !p.isPassenger();
   }

   public static boolean isSpear(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         CoopMovesConfig c = CoopMovesConfig.get();
         if (c.spearAnyItem) {
            return true;
         }

         if (stack.getItem() == Items.TRIDENT) {
            return true;
         }

         String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

         for (String token : c.spearItemMatches.split(",")) {
            String t = token.trim().toLowerCase();
            if (!t.isEmpty() && path.contains(t)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static int testLaunch(ServerPlayer p, boolean fire) {
      if (p == null) {
         return 0;
      }

      SpearStrikeHandler.State s = states.computeIfAbsent(p.getUUID(), k -> new SpearStrikeHandler.State());
      s.testThrown = true;
      s.testTicks = 0;
      s.hitThisThrow.clear();
      s.chain = 0;
      if (fire) {
         p.igniteForSeconds(8.0F);
      }

      Vec3 look = p.getLookAngle();
      setClientVelocity(p, new Vec3(look.x * 1.4, Math.max(0.6, look.y * 1.4 + 0.5), look.z * 1.4));
      p.displayClientMessage(
         Component.literal(fire ? "§c☢ Test launch (on fire) — hold right-click with a spear" : "§eTest launch — hold right-click with a spear"), true
      );
      return 1;
   }

   private static void tick(MinecraftServer server) {
      if (!states.isEmpty()) {
         CoopMovesConfig c = CoopMovesConfig.get();
         Iterator<Entry<UUID, SpearStrikeHandler.State>> it = states.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, SpearStrikeHandler.State> e = it.next();
            SpearStrikeHandler.State s = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && p.isAlive()) {
               Vec3 pos = new Vec3(p.getX(), p.getY(), p.getZ());
               s.motion = s.lastPos == null ? Vec3.ZERO : pos.subtract(s.lastPos);
               s.lastPos = pos;
               if (p.isPassenger() && p.getVehicle() instanceof ServerPlayer) {
                  s.lastRodePlayerMs = System.currentTimeMillis();
               }

               if (p.onGround()) {
                  s.lastRodePlayerMs = 0L;
               }

               if (s.testThrown && ++s.testTicks > 4 && p.onGround()) {
                  s.testThrown = false;
               }

               boolean airborne = !p.isPassenger() && !p.onGround();
               boolean posing = airborne && s.holding && isSpear(p.getMainHandItem()) && (missileOn || isThrown(p, s));
               setPose(p, s, posing);
               if (missileOn && c.enableSpearStrike && s.holding && airborne && isSpear(p.getMainHandItem()) && !s.missileSpent) {
                  s.ticks++;
                  missile(p, s, c);
               } else {
                  if (!airborne) {
                     s.missileSpent = false;
                  }

                  if (!airborne) {
                     setPose(p, s, false);
                  }

                  if (!isThrown(p, s) && c.spearDebug && s.holding && airborne) {
                     debug(
                        p,
                        "not counted as thrown (pose="
                           + PoseNetworking.poseStates.getOrDefault(e.getKey(), PoseState.NONE)
                           + ", rode "
                           + (s.lastRodePlayerMs == 0L ? "never" : System.currentTimeMillis() - s.lastRodePlayerMs + "ms ago")
                           + ")"
                     );
                  }

                  if (!isThrown(p, s)) {
                     s.hitThisThrow.clear();
                     s.lockedId = -1;
                     sendLock(p, s, -2, 0.0F, 0.0F, false);
                     s.chain = 0;
                     if (!s.holding && !s.testThrown) {
                        it.remove();
                     }
                  } else if (BrosHandler.isEngaged(e.getKey())) {
                     s.lockedId = -1;
                     sendLock(p, s, -2, 0.0F, 0.0F, false);
                  } else if (c.enableSpearStrike && s.holding && isSpear(p.getMainHandItem())) {
                     s.ticks++;
                     strike(p, s, c);
                  } else {
                     if (c.spearDebug && s.holding && airborne) {
                        debug(p, !c.enableSpearStrike ? "disabled in config" : "not a spear: " + BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()));
                     }

                     s.lockedId = -1;
                     sendLock(p, s, -2, 0.0F, 0.0F, false);
                  }
               }
            } else {
               it.remove();
            }
         }
      }
   }

   private static boolean isThrown(ServerPlayer p, SpearStrikeHandler.State s) {
      if (p.isPassenger() || p.onGround()) {
         return false;
      } else if (s.testThrown) {
         return true;
      } else {
         return PoseNetworking.poseStates.getOrDefault(p.getUUID(), PoseState.NONE) == PoseState.GRABBED
            ? true
            : s.lastRodePlayerMs > 0L && System.currentTimeMillis() - s.lastRodePlayerMs < 6000L;
      }
   }

   private static void strike(ServerPlayer p, SpearStrikeHandler.State s, CoopMovesConfig c) {
      if (p.level() instanceof ServerLevel w) {
         boolean nukeArmed = c.enableSpearNuke && p.isOnFire();
         LivingEntity target = pickTarget(w, p, s, c);
         if (target == null) {
            if (c.spearDebug) {
               debug(p, "no target in range " + (int)c.spearAimRange + " / cone " + (int)c.spearAimConeDeg + "°");
            }

            s.lockedId = -1;
            sendLock(p, s, -2, 0.0F, 0.0F, false);
         } else {
            if (s.lockedId != target.getId()) {
               s.lockedId = target.getId();
               w.playSound(
                  null, p.getX(), p.getY(), p.getZ(), (SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5F, nukeArmed ? 0.6F : 1.8F
               );
               if (nukeArmed) {
                  p.displayClientMessage(Component.literal("§4§l☢ NUKE ARMED"), true);
               }
            }

            Vec3 from = p.getBoundingBox().getCenter();
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 toTarget = center.subtract(from);
            double d = toTarget.length();
            Vec3 dir = toTarget.scale(1.0 / Math.max(d, 0.001));
            if (d <= c.spearHitRadius) {
               if (nukeArmed) {
                  nuke(w, p, center, c);
               } else {
                  double speed = s.motion.length();
                  float dmg = (float)Math.min(c.spearMaxDamage, c.spearBaseDamage + speed * c.spearSpeedDamage);
                  target.hurtServer(w, w.damageSources().playerAttack(p), dmg);
                  target.knockback(c.spearKnockback, -dir.x, -dir.z);
                  target.push(0.0, 0.3, 0.0);
                  if (target instanceof ServerPlayer tp) {
                     tp.connection.send(new ClientboundSetEntityMotionPacket(tp));
                  }

                  s.hitThisThrow.add(target.getId());
                  s.lockedId = -1;
                  s.chain++;
                  sendLock(p, s, -2, 0.0F, 0.0F, false);
                  setClientVelocity(p, s.motion.scale(0.45).add(0.0, c.spearBounce, 0.0));
                  w.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
                  w.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 14, 0.3, 0.4, 0.3, 0.4);
                  w.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.1F, 0.8F + s.chain * 0.1F);
                  w.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
                  p.displayClientMessage(
                     Component.literal(String.format("§6§lSPEAR STRIKE §7%.1f❤%s", dmg / 2.0F, s.chain > 1 ? "  §e§lx" + s.chain : "")), true
                  );
               }
            } else {
               sendLock(p, s, target.getId(), (float)c.spearHomingMinSpeed, (float)c.spearHomingTurn, false);
               if (!clientIsSteering(s)) {
                  serverSteer(p, s, dir, c.spearHomingMinSpeed, c.spearHomingTurn);
                  if (c.spearDebug) {
                     debug(p, "server-steering (no client ack)");
                  }
               } else if (c.spearDebug) {
                  debug(p, "locked " + target.getName().getString() + " " + String.format("%.1f", d) + "m");
               }

               if (s.ticks % 2 == 0) {
                  Vec3 mid = from.add(toTarget.scale(0.5));
                  SimpleParticleType fx = nukeArmed ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK;
                  w.sendParticles(fx, mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
                  w.sendParticles(fx, center.x, center.y + 0.6, center.z, 1, 0.1, 0.1, 0.1, 0.0);
               }
            }
         }
      }
   }

   private static void missile(ServerPlayer p, SpearStrikeHandler.State s, CoopMovesConfig c) {
      if (p.level() instanceof ServerLevel w) {
         boolean nukeArmed = c.enableSpearNuke && p.isOnFire();
         p.resetFallDistance();
         LivingEntity target = null;
         if (s.lockedId >= 0 && w.getEntity(s.lockedId) instanceof LivingEntity t && t.isAlive()) {
            target = t;
         } else {
            s.lockedId = -1;
            if (s.ticks % 3 == 0) {
               target = acquire(w, p, c);
            }

            if (target != null) {
               s.lockedId = target.getId();
               w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5F, 0.6F);
               w.playSound(null, p.getX(), p.getY(), p.getZ(), (SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
            }
         }

         Vec3 from = p.getBoundingBox().getCenter();
         Vec3 wantDir;
         if (target != null) {
            if (target.getBoundingBox().inflate(c.spearMissileHitRadius).intersects(p.getBoundingBox())) {
               Vec3 at = target.getBoundingBox().getCenter();
               if (nukeArmed) {
                  nuke(w, p, at, c);
                  return;
               }

               hurt(w, target, w.damageSources().playerAttack(p), c.spearMissileDamage);
               Vec3 kb = at.subtract(from).normalize();
               target.knockback(c.spearKnockback * 2.0, -kb.x, -kb.z);
               w.explode(p, at.x, at.y, at.z, 2.0F, false, ExplosionInteraction.NONE);
               s.lockedId = -1;
               s.missileSpent = true;
               sendLock(p, s, -2, 0.0F, 0.0F, true);
               setClientVelocity(p, new Vec3(0.0, c.spearBounce, 0.0));
               p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
               p.displayClientMessage(Component.literal("§c§l\ud83d\udca5 DIRECT HIT §7" + target.getName().getString()), true);
               return;
            }

            wantDir = target.getBoundingBox().getCenter().subtract(from).normalize();
            if (s.ticks % 5 == 0) {
               double dist = Math.sqrt(target.distanceToSqr(p));
               p.displayClientMessage(Component.literal((nukeArmed ? "§4§l☢ " : "§c➤ ") + target.getName().getString() + " §7" + (int)dist + "m"), true);
            }
         } else {
            wantDir = p.getLookAngle().normalize();
            if (s.ticks % 10 == 0) {
               p.displayClientMessage(Component.literal("§7➤ searching… aim at something"), true);
            }
         }

         sendLock(p, s, target != null ? target.getId() : -1, (float)c.spearMissileSpeed, (float)c.spearMissileTurn, true);
         if (!clientIsSteering(s)) {
            serverSteer(p, s, wantDir, c.spearMissileSpeed, c.spearMissileTurn);
         }

         w.sendParticles(nukeArmed ? ParticleTypes.FLAME : ParticleTypes.FIREWORK, p.getX(), p.getY() + 0.9, p.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
         if (s.ticks % 2 == 0) {
            w.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.9, p.getZ(), 1, 0.05, 0.05, 0.05, 0.005);
         }

         if (s.ticks % 12 == 0) {
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.7F, 0.8F);
         }
      }
   }

   private static LivingEntity acquire(ServerLevel w, ServerPlayer p, CoopMovesConfig c) {
      Vec3 eye = p.getEyePosition();
      Vec3 look = p.getLookAngle().normalize();
      double range = c.spearMissileRange;
      double cosCone = Math.cos(Math.toRadians(c.spearMissileConeDeg));
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;

      for (LivingEntity t : w.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), x -> x.isAlive() && x != p)) {
         if (!(t instanceof ArmorStand) && !(t instanceof ServerPlayer sp && sp.isSpectator())) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (!(dist > range) && !(dist < 0.001)) {
               double cos = to.scale(1.0 / dist).dot(look);
               if (!(cos < cosCone)) {
                  double score = (1.0 - cos) * 1000.0 + dist;
                  if (score < bestScore) {
                     bestScore = score;
                     best = t;
                  }
               }
            }
         }
      }

      return best;
   }

   private static void hurt(ServerLevel w, LivingEntity t, DamageSource src, float amount) {
      if (t instanceof EnderDragon dragon && dragon.getSubEntities().length > 0) {
         dragon.getSubEntities()[0].hurtServer(w, src, amount);
      } else {
         t.hurtServer(w, src, amount);
      }
   }

   private static LivingEntity pickTarget(ServerLevel w, ServerPlayer p, SpearStrikeHandler.State s, CoopMovesConfig c) {
      Vec3 eye = p.getEyePosition();
      Vec3 look = p.getLookAngle().normalize();
      double range = c.spearAimRange;
      double cosCone = Math.cos(Math.toRadians(c.spearAimConeDeg));
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;

      for (LivingEntity t : w.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), x -> x.isAlive() && x != p)) {
         if (!(t instanceof ArmorStand)
            && !(t instanceof ServerPlayer sp && sp.isSpectator())
            && !s.hitThisThrow.contains(t.getId())
            && !t.hasPassenger(p)
            && !p.hasPassenger(t)) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (!(dist > range) && !(dist < 0.001)) {
               double cos = to.scale(1.0 / dist).dot(look);
               boolean locked = t.getId() == s.lockedId;
               if (!(cos < (locked ? cosCone - 0.25 : cosCone))) {
                  double score = dist * (2.0 - cos) * (locked ? 0.6 : 1.0);
                  if (score < bestScore) {
                     bestScore = score;
                     best = t;
                  }
               }
            }
         }
      }

      return best;
   }

   private static void nuke(ServerLevel w, ServerPlayer p, Vec3 at, CoopMovesConfig c) {
      float core = Math.min(c.spearNukePower, 40.0F);
      nukeStages.add(new SpearStrikeHandler.NukeStage(w, at, serverTick, core, true, p));

      for (int i = 0; i < 6; i++) {
         double a = (Math.PI * 2) * i / 6.0;
         nukeStages.add(new SpearStrikeHandler.NukeStage(w, at.add(Math.cos(a) * 4.0, 0.0, Math.sin(a) * 4.0), serverTick + 3, core * 0.6F, false, p));
         double b = a + (Math.PI / 6);
         nukeStages.add(new SpearStrikeHandler.NukeStage(w, at.add(Math.cos(b) * 8.0, 0.0, Math.sin(b) * 8.0), serverTick + 6, core * 0.45F, false, p));
      }

      states.remove(p.getUUID());
   }

   private static void tickNukes() {
      if (!nukeStages.isEmpty()) {
         CoopMovesConfig c = CoopMovesConfig.get();
         ExplosionInteraction type = c.noGriefMode ? ExplosionInteraction.NONE : ExplosionInteraction.TNT;
         Iterator<SpearStrikeHandler.NukeStage> it = nukeStages.iterator();

         while (it.hasNext()) {
            SpearStrikeHandler.NukeStage n = it.next();
            if (serverTick >= n.atTick()) {
               it.remove();
               ServerLevel w = n.world();
               Vec3 at = n.pos();
               w.explode(n.owner(), at.x, at.y, at.z, n.power(), true, type);
               if (n.core()) {
                  double r = c.spearNukeRadius;
                  AABB box = new AABB(at.x - r, at.y - r, at.z - r, at.x + r, at.y + r, at.z + r);

                  for (LivingEntity t : w.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                     if (t != n.owner()) {
                        double dist = Math.sqrt(t.distanceToSqr(at));
                        if (!(dist > r)) {
                           float dmg = (float)(c.spearNukeDamage * (1.0 - 0.7 * dist / r));
                           hurt(w, t, w.damageSources().explosion(n.owner(), n.owner()), dmg);
                        }
                     }
                  }

                  ServerPlayer o = n.owner();
                  if (c.spearNukeKillsStriker && o != null && o.isAlive() && !o.isRemoved()) {
                     o.hurtServer(w, w.damageSources().explosion(o, o), 10000.0F);
                  }

                  w.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), at.x, at.y, at.z, 3, 0.0, 0.0, 0.0, 0.0);
                  w.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 4, 2.0, 1.0, 2.0, 0.0);

                  for (int i = 0; i < 40; i++) {
                     double h = i * 0.4;
                     double rr = i > 28 ? (i - 28) * 0.5 : 0.6;
                     w.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + h, at.z, 2, rr, 0.2, rr, 0.01);
                  }

                  w.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1.0, at.z, 40, 3.0, 1.0, 3.0, 0.2);
                  w.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0F, 0.5F);
                  w.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.6F);
               }
            }
         }
      }
   }

   private static void sendLock(ServerPlayer p, SpearStrikeHandler.State s, int targetId, float speed, float turn, boolean missile) {
      if (s.sentLock != targetId || s.ticks % 10 == 0) {
         s.sentLock = targetId;
         ServerPlayNetworking.send(p, new SpearStrikeHandler.SpearLockPayload(targetId, speed, turn, missile));
      }
   }

   private static boolean clientIsSteering(SpearStrikeHandler.State s) {
      return System.currentTimeMillis() - s.clientSteerAckMs < 500L;
   }

   private static void serverSteer(ServerPlayer p, SpearStrikeHandler.State s, Vec3 want, double minSpeed, double turn) {
      Vec3 v = s.motion;
      double speed = Math.max(v.length(), minSpeed);
      Vec3 cur = v.lengthSqr() > 1.0E-6 ? v.normalize() : want;
      Vec3 dir = cur.scale(1.0 - turn).add(want.scale(turn)).normalize();
      setClientVelocity(p, dir.scale(speed));
   }

   private static void setClientVelocity(ServerPlayer p, Vec3 v) {
      p.setDeltaMovement(v);
      p.connection.send(new ClientboundSetEntityMotionPacket(p));
   }

   private record NukeStage(ServerLevel world, Vec3 pos, int atTick, float power, boolean core, ServerPlayer owner) {
   }

   public record SpearAckPayload(boolean steering) implements CustomPacketPayload {
      public static final Type<SpearStrikeHandler.SpearAckPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spear_ack"));
      public static final StreamCodec<FriendlyByteBuf, SpearStrikeHandler.SpearAckPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeBoolean(v.steering()), buf -> new SpearStrikeHandler.SpearAckPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record SpearHoldPayload(boolean holding) implements CustomPacketPayload {
      public static final Type<SpearStrikeHandler.SpearHoldPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spear_hold"));
      public static final StreamCodec<FriendlyByteBuf, SpearStrikeHandler.SpearHoldPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeBoolean(v.holding()), buf -> new SpearStrikeHandler.SpearHoldPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record SpearLockPayload(int targetId, float speed, float turn, boolean missile) implements CustomPacketPayload {
      public static final Type<SpearStrikeHandler.SpearLockPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spear_lock"));
      public static final StreamCodec<FriendlyByteBuf, SpearStrikeHandler.SpearLockPayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeVarInt(v.targetId());
         buf.writeFloat(v.speed());
         buf.writeFloat(v.turn());
         buf.writeBoolean(v.missile());
      }, buf -> new SpearStrikeHandler.SpearLockPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record SpearPosePayload(int entityId, boolean flying) implements CustomPacketPayload {
      public static final Type<SpearStrikeHandler.SpearPosePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spear_pose"));
      public static final StreamCodec<FriendlyByteBuf, SpearStrikeHandler.SpearPosePayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeVarInt(v.entityId());
         buf.writeBoolean(v.flying());
      }, buf -> new SpearStrikeHandler.SpearPosePayload(buf.readVarInt(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static final class State {
      boolean holding;
      boolean testThrown;
      int testTicks;
      Vec3 lastPos;
      Vec3 motion = Vec3.ZERO;
      final Set<Integer> hitThisThrow = new HashSet<>();
      int lockedId = -1;
      int ticks;
      int sentLock = -2;
      long lastRodePlayerMs;
      int chain;
      boolean missileSpent;
      boolean poseSent;
      long clientSteerAckMs;
   }
}
