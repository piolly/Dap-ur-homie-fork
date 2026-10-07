package com.cooptest;

import com.cooptest.bros.BrosHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class KickHandler {
   public static final float KICK_RANGE = 2.0F;
   public static final float DROP_KICK_RANGE = 3.0F;
   public static final float KICK_DAMAGE = 2.0F;
   public static final float DROP_KICK_DAMAGE = 10.0F;
   public static final double KICK_KB_STRENGTH = 1.5;
   public static final double DROP_KICK_KB_STRENGTH = 3.5;
   public static final long CHARGE_TIME_MS = 3000L;
   public static final long KICK_COOLDOWN_MS = 2000L;
   private static final double KICK_SLOW_AMOUNT = -0.4;
   private static final double DROP_KICK_SLOW_AMOUNT = -0.9;
   private static final long KICK_ANIM_MS = 1000L;
   private static final long DROP_KICK_ANIM_MS = 1750L;
   private static final long DROP_KICK_SLOW_DELAY = 830L;
   private static final Identifier KICK_SLOW_ID = Identifier.fromNamespaceAndPath("testcoop", "kick_slow");
   private static final int ANIM_KICK = 61;
   private static final int ANIM_DROP_KICK = 62;
   private static final Map<UUID, Long> chargeStart = new HashMap<>();
   private static final Map<UUID, Long> cooldownEnd = new HashMap<>();
   private static final Map<UUID, Integer> lastSyncTick = new HashMap<>();
   private static final Map<UUID, Long> slowApplyAt = new HashMap<>();
   private static final Map<UUID, Long> slowRemoveAt = new HashMap<>();
   private static final Map<UUID, Double> slowAmount = new HashMap<>();
   private static final Map<UUID, Long> kickPushWindowEnd = new HashMap<>();
   private static final Map<UUID, Vec3> kickPushFwd = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(KickHandler.KickStartPayload.ID, KickHandler.KickStartPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(KickHandler.KickReleasePayload.ID, KickHandler.KickReleasePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(KickHandler.KickChargeSyncPayload.ID, KickHandler.KickChargeSyncPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(KickHandler.KickCooldownPayload.ID, KickHandler.KickCooldownPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(KickHandler.KickResultPayload.ID, KickHandler.KickResultPayload.CODEC);
   }

   public static boolean isBusy(ServerPlayer player) {
      UUID id = player.getUUID();
      if (PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE) != PoseState.NONE) {
         return true;
      } else if (GrabMechanic.isHolding(player)) {
         return true;
      } else if (FallCatchHandler.isInCatchReadyMode(id)) {
         return true;
      } else {
         return BrosHandler.isEngaged(id) ? true : SpearStrikeHandler.isFlying(player);
      }
   }

   public static void register() {
      registerPayloads();
      ServerPlayNetworking.registerGlobalReceiver(KickHandler.KickStartPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         UUID id = player.getUUID();
         if (CoopMovesConfig.get().enableKick) {
            if (!isOnCooldown(id)) {
               if (!isBusy(player)) {
                  if (payload.dropKickMode()) {
                     if (!CoopMovesConfig.get().enableDropKick) {
                        executeKick(player, false);
                        return;
                     }

                     chargeStart.put(id, System.currentTimeMillis());
                     broadcastChargeSync(player, true, 0.0F);
                  } else {
                     executeKick(player, false);
                  }
               }
            }
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(KickHandler.KickReleasePayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         UUID id = player.getUUID();
         Long start = chargeStart.remove(id);
         lastSyncTick.remove(id);
         if (start != null) {
            if (!isBusy(player)) {
               boolean fullCharge = System.currentTimeMillis() - start >= 3000L;
               broadcastChargeSync(player, false, 0.0F);
               executeKick(player, fullCharge);
            }
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         long now = System.currentTimeMillis();
         int tick = server.getTickCount();
         Iterator<Entry<UUID, Long>> it = chargeStart.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Long> entry = it.next();
            UUID id = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
               it.remove();
               lastSyncTick.remove(id);
            } else if (!player.isSprinting()) {
               it.remove();
               lastSyncTick.remove(id);
               broadcastChargeSync(player, false, 0.0F);
               ServerPlayNetworking.send(player, new KickHandler.KickCooldownPayload(0L));
            } else {
               int lastTick = lastSyncTick.getOrDefault(id, -999);
               if (tick - lastTick >= 2) {
                  lastSyncTick.put(id, tick);
                  float pct = Math.min(1.0F, (float)(now - entry.getValue()) / 3000.0F);
                  broadcastChargeSync(player, true, pct);
               }
            }
         }

         Iterator<Entry<UUID, Long>> pushWindowIt = kickPushWindowEnd.entrySet().iterator();

         while (pushWindowIt.hasNext()) {
            Entry<UUID, Long> entry = pushWindowIt.next();
            if (now >= entry.getValue()) {
               pushWindowIt.remove();
               kickPushFwd.remove(entry.getKey());
            } else {
               UUID id = entry.getKey();
               ServerPlayer player = server.getPlayerList().getPlayer(id);
               Vec3 fwd = kickPushFwd.get(id);
               if (player != null && fwd != null) {
                  float reach = 2.5F;
                  AABB box = player.getBoundingBox().inflate(reach + 0.3);

                  for (Entity target : player.level().getEntities(player, box, e -> e instanceof LivingEntity && !e.isRemoved())) {
                     double dx = target.getX() - player.getX();
                     double dz = target.getZ() - player.getZ();
                     double dist = Math.sqrt(dx * dx + dz * dz);
                     if (!(dist > reach)) {
                        double dot = dist < 0.01 ? 1.0 : (fwd.x * dx + fwd.z * dz) / dist;
                        if (!(dot < 0.2)) {
                           Vec3 vel = target.getDeltaMovement();
                           target.setDeltaMovement(vel.add(fwd.x * 0.12, 0.06, fwd.z * 0.12));
                           ((LivingEntity)target).syncVelocity = true;
                        }
                     }
                  }
               } else {
                  pushWindowIt.remove();
               }
            }
         }

         Iterator<Entry<UUID, Long>> applyIt = slowApplyAt.entrySet().iterator();

         while (applyIt.hasNext()) {
            Entry<UUID, Long> entry = applyIt.next();
            if (now >= entry.getValue()) {
               applyIt.remove();
               UUID id = entry.getKey();
               ServerPlayer player = server.getPlayerList().getPlayer(id);
               if (player != null) {
                  applySlowdown(player, slowAmount.getOrDefault(id, -0.9));
               }
            }
         }

         Iterator<Entry<UUID, Long>> removeIt = slowRemoveAt.entrySet().iterator();

         while (removeIt.hasNext()) {
            Entry<UUID, Long> entry = removeIt.next();
            if (now >= entry.getValue()) {
               removeIt.remove();
               slowAmount.remove(entry.getKey());
               ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
               if (player != null) {
                  removeSlowdown(player);
               }
            }
         }
      });
   }

   private static void executeKick(ServerPlayer player, boolean isDropKick) {
      UUID id = player.getUUID();
      ServerLevel world = player.level();
      float reach = isDropKick ? 3.0F : 2.0F;
      float damage = isDropKick ? 10.0F : 2.0F;
      double kbStrength = isDropKick ? 3.5 : 1.5;
      double upwardPop = isDropKick ? 0.55 : 0.35;
      float yaw = player.getYRot();
      double fwdX = -Math.sin(Math.toRadians(yaw));
      double fwdZ = Math.cos(Math.toRadians(yaw));
      AABB searchBox = player.getBoundingBox().inflate(reach + 0.5);
      List<Entity> candidates = world.getEntities(player, searchBox, e -> e instanceof LivingEntity && !e.isRemoved() && !e.isSpectator());
      boolean hitAny = false;
      List<ServerPlayer> playerHits = new ArrayList<>();

      for (Entity target : candidates) {
         double dx = target.getX() - player.getX();
         double dz = target.getZ() - player.getZ();
         double dist = Math.sqrt(dx * dx + dz * dz);
         if (!(dist > reach)) {
            double dot = dist < 0.01 ? 1.0 : (fwdX * dx + fwdZ * dz) / dist;
            if (!(dot < 0.25)) {
               target.hurtServer(world, world.damageSources().playerAttack(player), damage);
               if (target instanceof LivingEntity living) {
                  if (isDropKick) {
                     living.setDeltaMovement(fwdX * 4.0, 0.8, fwdZ * 4.0);
                  } else {
                     com.cooptest.CoopKnockback.apply(living, kbStrength, -dx, -dz);
                     Vec3 vel2 = living.getDeltaMovement();
                     living.setDeltaMovement(vel2.x, upwardPop, vel2.z);
                  }

                  living.syncVelocity = true;
               }

               if (target instanceof ServerPlayer hitPlayer) {
                  playerHits.add(hitPlayer);
               }

               Vec3 hitPos = player.position().add(target.position()).scale(0.5).add(0.0, player.getBbHeight() * 0.55, 0.0);
               if (isDropKick) {
                  world.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y, hitPos.z, 1, 0.2, 0.2, 0.2, 0.0);
                  world.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 5, 0.3, 0.3, 0.3, 0.15);
                  world.sendParticles(ParticleTypes.ENCHANTED_HIT, hitPos.x, hitPos.y, hitPos.z, 4, 0.2, 0.2, 0.2, 0.1);
               } else {
                  world.sendParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 5, 0.2, 0.2, 0.2, 0.1);
                  world.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 8, 0.2, 0.2, 0.2, 0.1);
               }

               hitAny = true;
            }
         }
      }

      if (isDropKick && playerHits.size() == 1) {
         ServerPlayer other = playerHits.get(0);
         Vec3 mid = player.position().add(other.position()).scale(0.5).add(0.0, 1.0, 0.0);
         world.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 2, 0.3, 0.3, 0.3, 0.0);
         world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 1, 0.2, 0.2, 0.2, 0.0);
         world.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 8, 0.4, 0.4, 0.4, 0.2);
         world.sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 6, 0.3, 0.3, 0.3, 0.15);
         world.sendParticles(ParticleTypes.LARGE_SMOKE, mid.x, mid.y, mid.z, 4, 0.3, 0.3, 0.3, 0.02);

         for (int i = 0; i < 8; i++) {
            double angle = (Math.PI / 4) * i;
            double rx = mid.x + Math.cos(angle) * 1.5;
            double rz = mid.z + Math.sin(angle) * 1.5;
            world.sendParticles(ParticleTypes.SWEEP_ATTACK, rx, mid.y - 0.8, rz, 1, 0.0, 0.0, 0.0, 0.0);
         }

         world.playSound(null, mid.x, mid.y, mid.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.8F);
         world.playSound(null, mid.x, mid.y, mid.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.0F, 0.7F);
         hitAny = false;
      }

      if (hitAny && isDropKick) {
         world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 1.0F);
         world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 0.9F, 0.9F);
      } else if (!hitAny) {
         world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.4F, 1.2F);
      }

      PoseNetworking.broadcastAnimState(player, isDropKick ? 62 : 61);
      long now = System.currentTimeMillis();
      kickPushWindowEnd.put(id, now + 540L);
      kickPushFwd.put(id, new Vec3(fwdX, 0.0, fwdZ));
      if (!isDropKick) {
         player.push(fwdX * 0.3, 0.0, fwdZ * 0.3);
         player.syncVelocity = true;
         applySlowdown(player, -0.4);
         slowRemoveAt.put(id, now + 1000L);
      } else {
         player.push(fwdX * 0.5, 0.0, fwdZ * 0.5);
         player.syncVelocity = true;
         slowApplyAt.put(id, now + 830L);
         slowRemoveAt.put(id, now + 1750L);
         slowAmount.put(id, -0.9);
      }

      ServerPlayNetworking.send(player, new KickHandler.KickResultPayload(isDropKick, hitAny));
      cooldownEnd.put(id, now + 2000L);
      ServerPlayNetworking.send(player, new KickHandler.KickCooldownPayload(2000L));
   }

   private static void applySlowdown(ServerPlayer player, double amount) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(KICK_SLOW_ID);
         attr.addTransientModifier(new AttributeModifier(KICK_SLOW_ID, amount, Operation.ADD_MULTIPLIED_TOTAL));
      }
   }

   private static void removeSlowdown(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(KICK_SLOW_ID);
      }
   }

   private static boolean isOnCooldown(UUID id) {
      Long end = cooldownEnd.get(id);
      return end != null && System.currentTimeMillis() < end;
   }

   private static void broadcastChargeSync(ServerPlayer player, boolean isCharging, float pct) {
      KickHandler.KickChargeSyncPayload pkt = new KickHandler.KickChargeSyncPayload(player.getUUID(), isCharging, pct);

      for (ServerPlayer other : PlayerLookup.around(player.level(), player.position(), 30.0)) {
         ServerPlayNetworking.send(other, pkt);
      }

      ServerPlayNetworking.send(player, pkt);
   }

   public static void cleanup(UUID id) {
      chargeStart.remove(id);
      cooldownEnd.remove(id);
      lastSyncTick.remove(id);
      slowApplyAt.remove(id);
      slowRemoveAt.remove(id);
      slowAmount.remove(id);
      kickPushWindowEnd.remove(id);
      kickPushFwd.remove(id);
   }

   public record KickChargeSyncPayload(UUID playerId, boolean isCharging, float chargePercent) implements CustomPacketPayload {
      public static final Type<KickHandler.KickChargeSyncPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "kick_charge_sync"));
      public static final StreamCodec<FriendlyByteBuf, KickHandler.KickChargeSyncPayload> CODEC = StreamCodec.ofMember((val, buf) -> {
         buf.writeUUID(val.playerId());
         buf.writeBoolean(val.isCharging());
         buf.writeFloat(val.chargePercent());
      }, buf -> new KickHandler.KickChargeSyncPayload(buf.readUUID(), buf.readBoolean(), buf.readFloat()));

      public Type<KickHandler.KickChargeSyncPayload> type() {
         return ID;
      }
   }

   public record KickCooldownPayload(long cooldownMs) implements CustomPacketPayload {
      public static final Type<KickHandler.KickCooldownPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "kick_cooldown"));
      public static final StreamCodec<FriendlyByteBuf, KickHandler.KickCooldownPayload> CODEC = StreamCodec.ofMember(
         (val, buf) -> buf.writeLong(val.cooldownMs()), buf -> new KickHandler.KickCooldownPayload(buf.readLong())
      );

      public Type<KickHandler.KickCooldownPayload> type() {
         return ID;
      }
   }

   public record KickReleasePayload() implements CustomPacketPayload {
      public static final Type<KickHandler.KickReleasePayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "kick_release"));
      public static final StreamCodec<FriendlyByteBuf, KickHandler.KickReleasePayload> CODEC = StreamCodec.unit(new KickHandler.KickReleasePayload());

      public Type<KickHandler.KickReleasePayload> type() {
         return ID;
      }
   }

   public record KickResultPayload(boolean isDropKick, boolean hit) implements CustomPacketPayload {
      public static final Type<KickHandler.KickResultPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "kick_result"));
      public static final StreamCodec<FriendlyByteBuf, KickHandler.KickResultPayload> CODEC = StreamCodec.ofMember((val, buf) -> {
         buf.writeBoolean(val.isDropKick());
         buf.writeBoolean(val.hit());
      }, buf -> new KickHandler.KickResultPayload(buf.readBoolean(), buf.readBoolean()));

      public Type<KickHandler.KickResultPayload> type() {
         return ID;
      }
   }

   public record KickStartPayload(boolean dropKickMode) implements CustomPacketPayload {
      public static final Type<KickHandler.KickStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "kick_start"));
      public static final StreamCodec<FriendlyByteBuf, KickHandler.KickStartPayload> CODEC = StreamCodec.ofMember(
         (val, buf) -> buf.writeBoolean(val.dropKickMode()), buf -> new KickHandler.KickStartPayload(buf.readBoolean())
      );

      public Type<KickHandler.KickStartPayload> type() {
         return ID;
      }
   }
}
