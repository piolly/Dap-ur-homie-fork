package com.cooptest.bully;

import com.cooptest.ChargedDapHandler;
import com.cooptest.ModSounds;
import com.cooptest.PoseNetworking;
import com.cooptest.client.CoopAnimationHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BullyDapHandler {
   public static final float PRIMED_MIN_HEIGHT = 5.0F;
   public static final float PRIMED_MAX_HEIGHT = 20.0F;
   public static final int HEIGHT_SCAN_BLOCKS = 26;
   public static final float PRIMED_BOOST = 0.5F;
   public static final long RELEASE_WINDOW_MS = 500L;
   public static final double TRIGGER_RANGE = 8.0;
   public static final double LOOK_DOT_THRESHOLD = 0.5;
   public static final int FALL_TICKS = 6;
   private static final int HAND_SWING_EVERY = 2;
   private static final long T_HIT_P1 = 542L;
   private static final long T_PARTICLES = 792L;
   private static final long T_CLEANUP = 3925L;
   private static final Set<UUID> primedVisual = new HashSet<>();
   private static final Map<UUID, BullyDapHandler.PendingRelease> pending = new HashMap<>();
   private static final Map<UUID, BullyDapHandler.FallSession> fallSessions = new HashMap<>();
   private static final Map<UUID, Long> cooldowns = new HashMap<>();
   private static final Set<UUID> inBullyDap = new HashSet<>();
   private static final Map<UUID, BullyDapHandler.ActiveDap> activeDaps = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.playS2C().register(BullyDapHandler.BullyDapEffectsPayload.ID, BullyDapHandler.BullyDapEffectsPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(BullyDapHandler.BullySimpleFlashPayload.ID, BullyDapHandler.BullySimpleFlashPayload.CODEC);
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {});
   }

   private static void tickPendingExpiry(MinecraftServer server) {
      long now = System.currentTimeMillis();
      pending.entrySet().removeIf(e -> {
         if (now - e.getValue().timestamp > 500L) {
            return true;
         }

         ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
         return p == null || p.onGround();
      });
   }

   private static void tickPrimedVisual(MinecraftServer server) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         UUID id = player.getUUID();
         if (!inBullyDap.contains(id) && !isOnCooldown(id) && !fallSessions.containsKey(id)) {
            boolean onGround = player.onGround();
            boolean charging = ChargedDapHandler.chargeStartTime.containsKey(id);
            if (onGround) {
               if (primedVisual.remove(id)) {
                  pending.remove(id);
                  ChargedDapHandler.executeWhiff(player);
                  ChargedDapHandler.chargeStartTime.remove(id);
                  ChargedDapHandler.fireLevel.remove(id);
                  ChargedDapHandler.broadcastChargeCancel(player);
               }
            } else if (!charging) {
               if (primedVisual.remove(id)) {
                  PoseNetworking.broadcastAnimState(player, 0);
               }
            } else if (!primedVisual.contains(id)) {
               double height = getHeightAboveGround(player);
               if (!(height < 5.0)) {
                  primedVisual.add(id);
                  Vec3 fwd = player.getViewVector(1.0F);
                  Vec3 vel = player.getDeltaMovement();
                  player.setDeltaMovement(vel.x + fwd.x * 0.5, vel.y, vel.z + fwd.z * 0.5);
                  player.hurtMarked = true;
                  PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.BULLY_DAP_P1.ordinal());
                  UUID fid = id;
                  ServerPlayer fp = player;
                  new Thread(() -> {
                     try {
                        Thread.sleep(542L);
                     } catch (InterruptedException var3x) {
                     }

                     fp.level().getServer().execute(() -> {
                        if (primedVisual.contains(fid) && !inBullyDap.contains(fid) && !fallSessions.containsKey(fid)) {
                           PoseNetworking.broadcastAnimState(fp, CoopAnimationHandler.AnimState.BULLY_DAP_IDLE.ordinal());
                        }
                     });
                  }).start();
               }
            }
         }
      }
   }

   public static double getHeightAboveGround(ServerPlayer player) {
      ServerLevel world = player.level();
      double py = player.getY();
      int px = (int)Math.floor(player.getX());
      int pz = (int)Math.floor(player.getZ());
      int startY = (int)Math.floor(py) - 1;

      for (int dy = 0; dy < 26; dy++) {
         BlockPos pos = new BlockPos(px, startY - dy, pz);
         BlockState state = world.getBlockState(pos);
         if (!state.isAir() && !state.getCollisionShape(world, pos).isEmpty()) {
            return py - (pos.getY() + 1.0);
         }
      }

      return 26.0;
   }

   public static float calcNormalized(double height) {
      return (float)Math.min(Math.max((height - 5.0) / 15.0, 0.0), 1.0);
   }

   public static boolean tryBullyDap(ServerPlayer player, double height) {
      long now = System.currentTimeMillis();
      UUID id = player.getUUID();
      player.displayClientMessage(Component.literal("§7[Bully] tryBullyDap — pending size=" + pending.size()), true);
      Iterator<Entry<UUID, BullyDapHandler.PendingRelease>> it = pending.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, BullyDapHandler.PendingRelease> e = it.next();
         UUID otherId = e.getKey();
         if (!otherId.equals(id)) {
            BullyDapHandler.PendingRelease pr = e.getValue();
            long age = now - pr.timestamp;
            if (age > 500L) {
               player.displayClientMessage(Component.literal("§7[Bully] candidate expired, age=" + age + "ms"), true);
               it.remove();
            } else {
               ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(otherId);
               if (other == null) {
                  it.remove();
               } else {
                  double distSq = player.distanceToSqr(other);
                  boolean inRange = distSq <= 64.0;
                  boolean facing = areFacingEachOtherHorizontal(player, other);
                  player.displayClientMessage(
                     Component.literal(
                        "§7[Bully] candidate dist="
                           + String.format("%.1f", Math.sqrt(distSq))
                           + " inRange="
                           + inRange
                           + " facing="
                           + facing
                           + " age="
                           + age
                           + "ms"
                     ),
                     true
                  );
                  if (inRange && facing) {
                     it.remove();
                     float h1 = (float)height;
                     float h2 = (float)pr.height;
                     float normalized = calcNormalized(Math.max(h1, h2));
                     startBullyDap(player, other, normalized);
                     return true;
                  }
               }
            }
         }
      }

      pending.put(id, new BullyDapHandler.PendingRelease(now, height));
      return false;
   }

   private static boolean areFacingEachOtherHorizontal(ServerPlayer a, ServerPlayer b) {
      Vec3 posA = a.position();
      Vec3 posB = b.position();
      Vec3 lookA = a.getViewVector(1.0F);
      Vec3 lookB = b.getViewVector(1.0F);
      Vec3 lookAFlat = new Vec3(lookA.x, 0.0, lookA.z).normalize();
      Vec3 lookBFlat = new Vec3(lookB.x, 0.0, lookB.z).normalize();
      Vec3 toBFlat = new Vec3(posB.x - posA.x, 0.0, posB.z - posA.z);
      Vec3 toAFlat = new Vec3(posA.x - posB.x, 0.0, posA.z - posB.z);
      if (toBFlat.lengthSqr() < 1.0E-4) {
         return true;
      }

      toBFlat = toBFlat.normalize();
      toAFlat = toAFlat.normalize();
      double dotA = lookAFlat.dot(toBFlat);
      double dotB = lookBFlat.dot(toAFlat);
      return dotA >= 0.5 && dotB >= 0.5;
   }

   private static void startBullyDap(ServerPlayer p1, ServerPlayer p2, float normalized) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      if (!inBullyDap.contains(id1) && !inBullyDap.contains(id2)) {
         if (!fallSessions.containsKey(id1) && !fallSessions.containsKey(id2)) {
            long now = System.currentTimeMillis();
            inBullyDap.add(id1);
            inBullyDap.add(id2);
            primedVisual.remove(id1);
            primedVisual.remove(id2);
            cooldowns.put(id1, now + 3000L);
            cooldowns.put(id2, now + 3000L);
            ChargedDapHandler.cooldowns.put(id1, now + 3000L);
            ChargedDapHandler.cooldowns.put(id2, now + 3000L);
            String msg = normalized < 0.33F
               ? "§e⚡ Bully Dap!"
               : (normalized < 0.67F ? "§6§l⚡⚡ Hard Bully Dap!" : (normalized < 1.0F ? "§c§l⚡⚡⚡ Heavy Bully Dap!" : "§4§l\ud83d\udca5 MAX BULLY DAP!"));
            p1.displayClientMessage(Component.literal(msg), true);
            p2.displayClientMessage(Component.literal(msg), true);
            Vec3 pos1 = p1.position();
            Vec3 pos2 = p2.position();
            double dx = pos1.x - pos2.x;
            double dz = pos1.z - pos2.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.001) {
               dx = 0.0;
               dz = 1.0;
               len = 1.0;
            }

            double ndx = dx / len;
            double ndz = dz / len;
            Vec3 targetPos = new Vec3(pos2.x + ndx * 1.5, pos2.y, pos2.z + ndz * 1.5);
            float targetYaw = (float)(-Math.toDegrees(Math.atan2(-ndx, -ndz)));
            float p2Yaw = targetYaw + 180.0F;
            BullyDapHandler.FallSession fall = new BullyDapHandler.FallSession(id1, id2, pos1, targetPos, pos2, p1.getYRot(), targetYaw, normalized);
            fallSessions.put(id1, fall);
            p2.setYRot(p2Yaw);
            p2.setYBodyRot(p2Yaw);
            p2.setYHeadRot(p2Yaw);
            p2.setDeltaMovement(Vec3.ZERO);
            p2.hurtMarked = true;
            p2.fallDistance = 0.0;
            showBothHands(p1, p2);
         }
      }
   }

   private static void tickFallSessions(MinecraftServer server) {
      for (BullyDapHandler.FallSession f : new ArrayList<>(fallSessions.values())) {
         ServerPlayer p1 = server.getPlayerList().getPlayer(f.p1id);
         ServerPlayer p2 = server.getPlayerList().getPlayer(f.p2id);
         if (p1 != null && p2 != null) {
            ServerLevel world = p1.level();
            f.tick++;
            double t = Math.min(1.0, f.tick / 6.0);
            double eased = 1.0 - Math.pow(1.0 - t, 2.0);
            Vec3 newPos = new Vec3(lerp(f.startPos.x, f.targetPos.x, eased), lerp(f.startPos.y, f.targetPos.y, eased), lerp(f.startPos.z, f.targetPos.z, eased));
            float newYaw = (float)lerpAngle(f.startYaw, f.targetYaw, eased);
            p1.teleportTo(world, newPos.x, newPos.y, newPos.z, Set.of(), newYaw, 0.0F, false);
            p1.setYRot(newYaw);
            p1.setYBodyRot(newYaw);
            p1.setYHeadRot(newYaw);
            p1.setDeltaMovement(Vec3.ZERO);
            p1.hurtMarked = true;
            p1.fallDistance = 0.0;
            if (f.tick % 2 == 0) {
               p1.swing(InteractionHand.MAIN_HAND);
            }

            p2.teleportTo(world, f.p2FrozenPos.x, f.p2FrozenPos.y, f.p2FrozenPos.z, Set.of(), p2.getYRot(), 0.0F, false);
            p2.setDeltaMovement(Vec3.ZERO);
            p2.hurtMarked = true;
            p2.fallDistance = 0.0;
            if (f.tick >= 6) {
               fallSessions.remove(f.p1id);
               finishFall(p1, p2, f.normalized);
            }
         } else {
            fallSessions.remove(f.p1id);
            inBullyDap.remove(f.p1id);
            inBullyDap.remove(f.p2id);
         }
      }
   }

   private static void finishFall(ServerPlayer p1, ServerPlayer p2, float normalized) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.BULLY_DAP_P1.ordinal());
      PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.BULLY_DAP_HIT_P2.ordinal());
      BullyDapHandler.ActiveDap dap = new BullyDapHandler.ActiveDap(id1, id2, normalized);
      dap.ready = true;
      dap.startMs = System.currentTimeMillis();
      activeDaps.put(id1, dap);
      activeDaps.put(id2, dap);
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static double lerpAngle(double a, double b, double t) {
      double diff = (b - a + 540.0) % 360.0 - 180.0;
      return a + diff * t;
   }

   private static void tickActiveDaps(MinecraftServer server) {
      for (BullyDapHandler.ActiveDap s : new ArrayList<>(activeDaps.values())) {
         if (activeDaps.containsKey(s.p1id) && s.ready) {
            long elapsed = s.elapsed();
            ServerPlayer p1 = server.getPlayerList().getPlayer(s.p1id);
            ServerPlayer p2 = server.getPlayerList().getPlayer(s.p2id);
            if (p1 != null && p2 != null) {
               ServerLevel world = p1.level();
               if (!s.hitP1Done && elapsed >= 542L) {
                  s.hitP1Done = true;
                  PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.BULLY_DAP_HIT_P1.ordinal());
                  Vec3 cp = p1.position().add(p2.position()).scale(0.5);
                  fireConnect(p1, p2, s.normalized, cp, world);
               }

               if (!s.particlesDone && elapsed >= 792L) {
                  s.particlesDone = true;
                  spawnHandMeetParticles(world, p1.position().add(p2.position()).scale(0.5), s.normalized);
               }

               if (!s.cleanupDone && elapsed >= 3925L) {
                  s.cleanupDone = true;
                  PoseNetworking.broadcastAnimState(p1, 0);
                  PoseNetworking.broadcastAnimState(p2, 0);
                  hideHands(p1, p2);
                  inBullyDap.remove(s.p1id);
                  inBullyDap.remove(s.p2id);
                  removeActiveDap(s);
               }
            } else {
               removeActiveDap(s);
            }
         }
      }
   }

   private static void removeActiveDap(BullyDapHandler.ActiveDap s) {
      activeDaps.remove(s.p1id);
      activeDaps.remove(s.p2id);
   }

   private static void fireConnect(ServerPlayer p1, ServerPlayer p2, float n, Vec3 pos, ServerLevel world) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();

      for (ServerPlayer pl : p1.level().getServer().getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(pl, new BullyDapHandler.BullyDapEffectsPayload(n, pos.x, pos.y + 1.3, pos.z));
      }

      int base = 10 + (int)(n * 20.0F);
      world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 1.3, pos.z, base, 0.3, 0.3, 0.3, 0.12);
      world.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 1.3, pos.z, base / 2, 0.3, 0.3, 0.3, 0.08);
      if (n >= 0.33F) {
         world.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.0, pos.z, (int)(n * 8.0F), 0.4, 0.2, 0.4, 0.0);
      }

      if (n >= 0.67F) {
         world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), pos.x, pos.y + 1.0, pos.z, 1 + (int)(n * 2.0F), 0.1, 0.1, 0.1, 0.0);
         world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.3, pos.z, (int)(n * 15.0F), 0.5, 0.4, 0.5, 0.12);
      }

      if (n >= 1.0F) {
         world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5, pos.z, 1, 0.3, 0.3, 0.3, 0.0);
      }

      float vol = 1.0F + n * 0.8F;
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_HIT, SoundSource.PLAYERS, vol, 1.0F);
      if (n >= 0.33F) {
         world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, vol, 0.9F);
      }

      if (n >= 0.67F) {
         world.playSound(null, pos.x, pos.y, pos.z, ModSounds.IMPACT, SoundSource.PLAYERS, vol * 1.2F, 1.0F);
      }

      if (n >= 0.33F) {
         world.explode(null, pos.x, pos.y, pos.z, 2.0F + n * 4.0F, false, ExplosionInteraction.NONE);
      }

      double kbR = 4.0 + n * 8.0;
      double kbS = 0.4 + n * 1.2;
      AABB box = new AABB(pos.x - kbR, pos.y - kbR, pos.z - kbR, pos.x + kbR, pos.y + kbR, pos.z + kbR);

      for (Entity e : world.getEntities(null, box)) {
         if (!e.getUUID().equals(id1) && !e.getUUID().equals(id2)) {
            double d = e.position().distanceTo(pos);
            if (!(d < 0.5) && !(d > kbR)) {
               Vec3 dr = e.position().subtract(pos).normalize();
               double s = (1.0 - d / kbR) * kbS;
               e.push(dr.x * s, s * 0.4, dr.z * s);
               e.hurtMarked = true;
            }
         }
      }
   }

   private static void spawnHandMeetParticles(ServerLevel world, Vec3 pos, float n) {
      Vec3 h = pos.add(0.0, 1.3, 0.0);
      int tier = n < 0.33F ? 1 : (n < 0.67F ? 2 : (n < 1.0F ? 3 : 4));
      switch (tier) {
         case 1:
            world.sendParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 12, 0.2, 0.2, 0.2, 0.1);
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, h.x, h.y, h.z, 8, 0.2, 0.2, 0.2, 0.08);
            break;
         case 2:
            world.sendParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 20, 0.3, 0.2, 0.3, 0.12);
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, h.x, h.y, h.z, 12, 0.2, 0.2, 0.2, 0.1);
            world.sendParticles(ParticleTypes.SWEEP_ATTACK, h.x, h.y, h.z, 4, 0.3, 0.1, 0.3, 0.0);
            break;
         case 3:
            world.sendParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 30, 0.3, 0.3, 0.3, 0.15);
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, h.x, h.y, h.z, 18, 0.3, 0.3, 0.3, 0.12);
            world.sendParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 10, 0.4, 0.3, 0.4, 0.1);
            world.sendParticles(ParticleTypes.SWEEP_ATTACK, h.x, h.y, h.z, 6, 0.4, 0.1, 0.4, 0.0);
            world.playSound(null, h.x, h.y, h.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 1.1F);
            break;
         case 4:
            world.sendParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 45, 0.4, 0.3, 0.4, 0.18);
            world.sendParticles(ParticleTypes.ENCHANTED_HIT, h.x, h.y, h.z, 25, 0.3, 0.3, 0.3, 0.14);
            world.sendParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 18, 0.5, 0.4, 0.5, 0.12);
            world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), h.x, h.y, h.z, 3, 0.1, 0.1, 0.1, 0.0);
            world.sendParticles(ParticleTypes.SWEEP_ATTACK, h.x, h.y, h.z, 10, 0.5, 0.1, 0.5, 0.0);
            world.playSound(null, h.x, h.y, h.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5F, 1.0F);
            world.playSound(null, h.x, h.y, h.z, ModSounds.IMPACT, SoundSource.PLAYERS, 1.5F, 1.2F);
      }
   }

   private static void showBothHands(ServerPlayer p1, ServerPlayer p2) {
      for (ServerPlayer pl : p1.level().getServer().getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(pl, new ChargedDapHandler.FireDapFirstPersonPayload(p1.getUUID(), true));
         ServerPlayNetworking.send(pl, new ChargedDapHandler.FireDapFirstPersonPayload(p2.getUUID(), true));
      }
   }

   private static void hideHands(ServerPlayer p1, ServerPlayer p2) {
      for (ServerPlayer pl : p1.level().getServer().getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(pl, new ChargedDapHandler.FireDapFirstPersonPayload(p1.getUUID(), false));
         ServerPlayNetworking.send(pl, new ChargedDapHandler.FireDapFirstPersonPayload(p2.getUUID(), false));
      }
   }

   private static boolean isOnCooldown(UUID id) {
      Long e = cooldowns.get(id);
      if (e == null) {
         return false;
      } else if (System.currentTimeMillis() >= e) {
         cooldowns.remove(id);
         return false;
      } else {
         return true;
      }
   }

   public static boolean isInBullyDap(UUID id) {
      return inBullyDap.contains(id) || fallSessions.containsKey(id);
   }

   public static boolean wasPrimed(UUID id) {
      return primedVisual.contains(id);
   }

   public static boolean isPrimed(UUID id) {
      return false;
   }

   public static void cleanup(UUID id) {
      primedVisual.remove(id);
      inBullyDap.remove(id);
      cooldowns.remove(id);
      pending.remove(id);
      BullyDapHandler.FallSession f = fallSessions.remove(id);
      if (f != null) {
         inBullyDap.remove(f.p1id);
         inBullyDap.remove(f.p2id);
      }

      BullyDapHandler.ActiveDap s = activeDaps.remove(id);
      if (s != null) {
         activeDaps.remove(s.p1id);
         activeDaps.remove(s.p2id);
      }
   }

   private static class ActiveDap {
      final UUID p1id;
      final UUID p2id;
      final float normalized;
      boolean ready = false;
      long startMs = 0L;
      boolean hitP1Done = false;
      boolean particlesDone = false;
      boolean cleanupDone = false;

      ActiveDap(UUID a, UUID b, float n) {
         this.p1id = a;
         this.p2id = b;
         this.normalized = n;
      }

      long elapsed() {
         return System.currentTimeMillis() - this.startMs;
      }
   }

   public record BullyDapEffectsPayload(float normalized, double x, double y, double z) implements CustomPacketPayload {
      public static final Type<BullyDapHandler.BullyDapEffectsPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "bully_dap_effects"));
      public static final StreamCodec<FriendlyByteBuf, BullyDapHandler.BullyDapEffectsPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeFloat(p.normalized());
         buf.writeDouble(p.x());
         buf.writeDouble(p.y());
         buf.writeDouble(p.z());
      }, buf -> new BullyDapHandler.BullyDapEffectsPayload(buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record BullySimpleFlashPayload() implements CustomPacketPayload {
      public static final Type<BullyDapHandler.BullySimpleFlashPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "bully_simple_flash"));
      public static final StreamCodec<FriendlyByteBuf, BullyDapHandler.BullySimpleFlashPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> {}, buf -> new BullyDapHandler.BullySimpleFlashPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static class FallSession {
      final UUID p1id;
      final UUID p2id;
      final Vec3 startPos;
      final Vec3 targetPos;
      final Vec3 p2FrozenPos;
      final float startYaw;
      final float targetYaw;
      final float normalized;
      int tick = 0;

      FallSession(UUID p1, UUID p2, Vec3 startPos, Vec3 targetPos, Vec3 p2FrozenPos, float startYaw, float targetYaw, float normalized) {
         this.p1id = p1;
         this.p2id = p2;
         this.startPos = startPos;
         this.targetPos = targetPos;
         this.p2FrozenPos = p2FrozenPos;
         this.startYaw = startYaw;
         this.targetYaw = targetYaw;
         this.normalized = normalized;
      }
   }

   private static class PendingRelease {
      final long timestamp;
      final double height;

      PendingRelease(long t, double h) {
         this.timestamp = t;
         this.height = h;
      }
   }
}
