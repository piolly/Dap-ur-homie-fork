package com.cooptest;

import com.cooptest.client.CoopAnimationHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class FallDapHandler {
   private static final Map<UUID, FallDapHandler.FallDapState> fallDapPlayers = new HashMap<>();
   private static final Map<UUID, Long> fallChargeStartTime = new HashMap<>();
   private static final Map<UUID, Long> squashedPlayers = new HashMap<>();
   private static final long SQUASHED_DURATION_MS = 25000L;
   private static final Map<UUID, Double> fallStartY = new HashMap<>();
   private static final double REQUIRED_FALL_BLOCKS = 20.0;
   private static final long FALL_CHARGE_DURATION_MS = 750L;

   public static void register() {
      PayloadTypeRegistry.clientboundPlay().register(FallDapHandler.FallDapAnimPayload.ID, FallDapHandler.FallDapAnimPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(FallDapHandler.SquashAnimPayload.ID, FallDapHandler.SquashAnimPayload.CODEC);
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> tick(server));
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      Iterator<Entry<UUID, Long>> squashIt = squashedPlayers.entrySet().iterator();

      while (squashIt.hasNext()) {
         Entry<UUID, Long> entry = squashIt.next();
         UUID playerId = entry.getKey();
         ServerPlayer player = server.getPlayerList().getPlayer(playerId);
         if (now >= entry.getValue()) {
            squashIt.remove();
            if (player != null) {
               PoseNetworking.broadcastAnimState(player, 0);
               player.removeEffect(MobEffects.SLOWNESS);
               player.removeEffect(MobEffects.JUMP_BOOST);
            }
         } else if (player != null) {
            if (!player.hasEffect(MobEffects.SLOWNESS) || player.getEffect(MobEffects.SLOWNESS).getDuration() < 40) {
               player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, false, false));
            }

            if (!player.hasEffect(MobEffects.JUMP_BOOST) || player.getEffect(MobEffects.JUMP_BOOST).getDuration() < 40) {
               player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 250, false, false));
            }
         }
      }

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         UUID playerId = player.getUUID();
         if (!isSquashed(playerId)) {
            if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
               cleanup(playerId);
            } else if (player.isSprinting() && ChargedDapHandler.isFullyCharged(playerId)) {
               cleanup(playerId);
            } else {
               boolean isChargingDap = ChargedDapHandler.isCharging(playerId);
               boolean isOnGround = player.onGround();
               boolean isFalling = player.getDeltaMovement().y < -0.1;
               FallDapHandler.FallDapState currentState = fallDapPlayers.getOrDefault(playerId, FallDapHandler.FallDapState.NONE);
               if (currentState == FallDapHandler.FallDapState.NONE) {
                  if (isChargingDap && isFalling && !isOnGround) {
                     if (!fallStartY.containsKey(playerId)) {
                        fallStartY.put(playerId, player.getY());
                     }

                     double startY = fallStartY.get(playerId);
                     double fallen = startY - player.getY();
                     if (fallen >= 20.0 && ChargedDapHandler.isFullyCharged(playerId)) {
                        startFallDapCharge(player);
                     }
                  } else if (isOnGround) {
                     fallStartY.remove(playerId);
                  }
               } else if (currentState == FallDapHandler.FallDapState.CHARGING) {
                  Long chargeStart = fallChargeStartTime.get(playerId);
                  if (chargeStart != null && now - chargeStart >= 750L) {
                     fallDapPlayers.put(playerId, FallDapHandler.FallDapState.FALLING);
                     broadcastFallDapAnim(player, 2);
                     player.sendOverlayMessage(Component.literal("§c§l FALL DAP READY! "));
                  }

                  if (isOnGround) {
                     if (isChargingDap) {
                        resetToNormalCharge(player);
                     } else {
                        cleanup(playerId);
                        PoseNetworking.broadcastAnimState(player, 0);
                     }
                  }
               } else if (currentState == FallDapHandler.FallDapState.FALLING) {
                  ServerPlayer victim = findSquashTarget(player, 3.0);
                  if (victim != null) {
                     squashPlayer(player, victim);
                     cleanup(playerId);
                  } else if (isOnGround) {
                     if (isChargingDap) {
                        resetToNormalCharge(player);
                     } else {
                        cleanup(playerId);
                        PoseNetworking.broadcastAnimState(player, 0);
                     }
                  }
               }
            }
         }
      }
   }

   private static void startFallDapCharge(ServerPlayer player) {
      UUID playerId = player.getUUID();
      fallDapPlayers.put(playerId, FallDapHandler.FallDapState.CHARGING);
      fallChargeStartTime.put(playerId, System.currentTimeMillis());
      broadcastFallDapAnim(player, 1);
      player.sendOverlayMessage(Component.literal("§e§l⚡ FALL DAP CHARGING! ⚡"));
   }

   private static void resetToNormalCharge(ServerPlayer player) {
      UUID playerId = player.getUUID();
      fallDapPlayers.remove(playerId);
      fallStartY.remove(playerId);
      fallChargeStartTime.remove(playerId);
      broadcastFallDapAnim(player, 0);
      PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE.ordinal());
      player.sendOverlayMessage(Component.literal("§7Fall dap reset - touched ground"));
   }

   public static boolean isInFallDapState(UUID playerId) {
      FallDapHandler.FallDapState state = fallDapPlayers.get(playerId);
      return state == FallDapHandler.FallDapState.CHARGING || state == FallDapHandler.FallDapState.FALLING;
   }

   public static boolean isReadyToFallDap(UUID playerId) {
      return fallDapPlayers.get(playerId) == FallDapHandler.FallDapState.FALLING;
   }

   public static void executeFallDapHit(ServerLevel world, Vec3 pos, ServerPlayer attacker, ServerPlayer victim) {
      UUID attackerId = attacker.getUUID();
      broadcastFallDapAnim(attacker, 3);
      cleanup(attackerId);
   }

   private static void squashPlayer(ServerPlayer attacker, ServerPlayer victim) {
      ServerLevel world = attacker.level();
      Vec3 pos = victim.position();
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0F, 0.5F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.8F);
      world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 1.0, pos.z, 30, 0.5, 0.3, 0.5, 0.2);
      world.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 20, 0.5, 0.2, 0.5, 0.05);
      dropHandItems(victim, world, pos);
      victim.hurtServer(world, world.damageSources().playerAttack(attacker), 10.0F);
      victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 420, 2, false, false));
      victim.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 420, 250, false, false));
      victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0, false, false));
      victim.setDeltaMovement(0.0, 0.0, 0.0);
      victim.syncVelocity = true;
      squashedPlayers.put(victim.getUUID(), System.currentTimeMillis() + 25000L);

      for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(p, new FallDapHandler.SquashAnimPayload(victim.getUUID()));
      }

      attacker.sendOverlayMessage(Component.literal("§c§l\ud83d\udc80 SQUASHED! \ud83d\udc80"));
      victim.sendOverlayMessage(Component.literal("§c§lYOU GOT SQUASHED FOR 25 SECONDS!"));
   }

   private static void dropHandItems(ServerPlayer player, ServerLevel world, Vec3 pos) {
      ItemStack mainStack = player.getMainHandItem();
      if (!mainStack.isEmpty()) {
         ItemEntity mainItem = new ItemEntity(world, pos.x, pos.y + 0.5, pos.z, mainStack.copy());
         mainItem.setDeltaMovement((world.random.nextDouble() - 0.5) * 0.3, world.random.nextDouble() * 0.2 + 0.1, (world.random.nextDouble() - 0.5) * 0.3);
         world.addFreshEntity(mainItem);
         player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      }

      ItemStack offStack = player.getOffhandItem();
      if (!offStack.isEmpty()) {
         ItemEntity offItem = new ItemEntity(world, pos.x, pos.y + 0.5, pos.z, offStack.copy());
         offItem.setDeltaMovement((world.random.nextDouble() - 0.5) * 0.3, world.random.nextDouble() * 0.2 + 0.1, (world.random.nextDouble() - 0.5) * 0.3);
         world.addFreshEntity(offItem);
         player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      }

      ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
      if (!helmet.isEmpty()) {
         ItemEntity helmetItem = new ItemEntity(world, pos.x, pos.y + 1.0, pos.z, helmet.copy());
         helmetItem.setDeltaMovement((world.random.nextDouble() - 0.5) * 0.4, world.random.nextDouble() * 0.4 + 0.3, (world.random.nextDouble() - 0.5) * 0.4);
         world.addFreshEntity(helmetItem);
         player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      }
   }

   private static ServerPlayer findSquashTarget(ServerPlayer attacker, double horizontalRange) {
      double attackerY = attacker.getY();

      for (ServerPlayer other : attacker.level().players()) {
         if (other != attacker && !isSquashed(other.getUUID())) {
            double otherY = other.getY();
            double heightDiff = attackerY - otherY;
            if (!(heightDiff < 0.5) && !(heightDiff > 3.0)) {
               double dx = attacker.getX() - other.getX();
               double dz = attacker.getZ() - other.getZ();
               double horizontalDist = Math.sqrt(dx * dx + dz * dz);
               if (horizontalDist <= horizontalRange) {
                  return other;
               }
            }
         }
      }

      return null;
   }

   private static ServerPlayer findNearbyPlayer(ServerPlayer player, double range) {
      for (ServerPlayer other : player.level().players()) {
         if (other != player && other.distanceToSqr(player) <= range * range) {
            return other;
         }
      }

      return null;
   }

   public static boolean isSquashed(UUID playerId) {
      Long endTime = squashedPlayers.get(playerId);
      if (endTime == null) {
         return false;
      } else if (System.currentTimeMillis() >= endTime) {
         squashedPlayers.remove(playerId);
         return false;
      } else {
         return true;
      }
   }

   private static void broadcastFallDapAnim(ServerPlayer player, int state) {
      MinecraftServer server = player.level().getServer();
      if (server != null) {
         FallDapHandler.FallDapAnimPayload payload = new FallDapHandler.FallDapAnimPayload(player.getUUID(), state);

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
         }
      }
   }

   public static void cleanup(UUID playerId) {
      fallDapPlayers.remove(playerId);
      fallStartY.remove(playerId);
      fallChargeStartTime.remove(playerId);
      squashedPlayers.remove(playerId);
   }

   public record FallDapAnimPayload(UUID playerId, int state) implements CustomPacketPayload {
      public static final Type<FallDapHandler.FallDapAnimPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "fall_dap_anim"));
      public static final StreamCodec<FriendlyByteBuf, FallDapHandler.FallDapAnimPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.playerId);
         buf.writeInt(payload.state);
      }, buf -> new FallDapHandler.FallDapAnimPayload(buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public enum FallDapState {
      NONE,
      CHARGING,
      FALLING;
   }

   public record SquashAnimPayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<FallDapHandler.SquashAnimPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "squash_anim"));
      public static final StreamCodec<FriendlyByteBuf, FallDapHandler.SquashAnimPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> buf.writeUUID(payload.playerId), buf -> new FallDapHandler.SquashAnimPayload(buf.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
