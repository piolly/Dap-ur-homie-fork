package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;


public class FallDapHandler {

    private static final Map<UUID, FallDapState> fallDapPlayers = new HashMap<>();

    private static final Map<UUID, Long> fallChargeStartTime = new HashMap<>();

    private static final Map<UUID, Long> squashedPlayers = new HashMap<>();
    private static final long SQUASHED_DURATION_MS = 25000; // 25 sec squashed animation

    private static final Map<UUID, Double> fallStartY = new HashMap<>();
    private static final double REQUIRED_FALL_BLOCKS = 20.0;

    private static final long FALL_CHARGE_DURATION_MS = 750; // 0.75 sec

    public enum FallDapState {
        NONE,
        CHARGING,    // Playing dap_charge_fall_start
        FALLING      // Playing dap_charge_falling (ready to dap/squash)
    }

    public record FallDapAnimPayload(UUID playerId, int state) implements CustomPacketPayload {
        public static final Type<FallDapAnimPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "fall_dap_anim"));

        public static final StreamCodec<FriendlyByteBuf, FallDapAnimPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUUID(payload.playerId);
                            buf.writeInt(payload.state);
                        },
                        buf -> new FallDapAnimPayload(buf.readUUID(), buf.readInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SquashAnimPayload(UUID playerId) implements CustomPacketPayload {
        public static final Type<SquashAnimPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "squash_anim"));

        public static final StreamCodec<FriendlyByteBuf, SquashAnimPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> buf.writeUUID(payload.playerId),
                        buf -> new SquashAnimPayload(buf.readUUID())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(FallDapAnimPayload.ID, FallDapAnimPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SquashAnimPayload.ID, SquashAnimPayload.CODEC);


        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick(server);
        });
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> squashIt = squashedPlayers.entrySet().iterator();
        while (squashIt.hasNext()) {
            Map.Entry<UUID, Long> entry = squashIt.next();
            UUID playerId = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (now >= entry.getValue()) {
                squashIt.remove();

                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0); // NONE
                    player.removeEffect(MobEffects.SLOWNESS);
                    player.removeEffect(MobEffects.JUMP_BOOST);
                }
            } else if (player != null) {

                if (!player.hasEffect(MobEffects.SLOWNESS) ||
                        player.getEffect(MobEffects.SLOWNESS).getDuration() < 40) {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, false, false));
                }

                if (!player.hasEffect(MobEffects.JUMP_BOOST) ||
                        player.getEffect(MobEffects.JUMP_BOOST).getDuration() < 40) {
                    player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 250, false, false));
                }
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();

            if (isSquashed(playerId)) continue;

            if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                cleanup(playerId);
                continue;
            }

            boolean isChargingDap = ChargedDapHandler.isCharging(playerId);
            boolean isOnGround = player.onGround();
            boolean isFalling = player.getDeltaMovement().y < -0.1;

            FallDapState currentState = fallDapPlayers.getOrDefault(playerId, FallDapState.NONE);

            if (currentState == FallDapState.NONE) {
                if (isChargingDap && isFalling && !isOnGround) {
                    if (!fallStartY.containsKey(playerId)) {
                        fallStartY.put(playerId, player.getY());
                    }

                    double startY = fallStartY.get(playerId);
                    double fallen = startY - player.getY();

                    if (fallen >= REQUIRED_FALL_BLOCKS && ChargedDapHandler.isFullyCharged(playerId)) {
                        startFallDapCharge(player);
                    }
                } else if (isOnGround) {
                    fallStartY.remove(playerId);
                }
            } else if (currentState == FallDapState.CHARGING) {
                Long chargeStart = fallChargeStartTime.get(playerId);
                if (chargeStart != null && now - chargeStart >= FALL_CHARGE_DURATION_MS) {
                    fallDapPlayers.put(playerId, FallDapState.FALLING);
                    broadcastFallDapAnim(player, 2); // FALLING state
                    player.sendOverlayMessage(Component.literal("§c§l FALL DAP READY! "));
                }

                if (isOnGround) {
                    if (isChargingDap) {
                        resetToNormalCharge(player);
                    } else {
                        cleanup(playerId);
                        PoseNetworking.broadcastAnimState(player, 0); // NONE
                    }
                }
            } else if (currentState == FallDapState.FALLING) {

                ServerPlayer victim = findSquashTarget(player, 3.0);
                if (victim != null) {
                    // SQUASH!
                    squashPlayer(player, victim);
                    cleanup(playerId);
                    continue;
                }

                // Reset if touched ground
                if (isOnGround) {
                    if (isChargingDap) {
                        resetToNormalCharge(player);
                    } else {
                        cleanup(playerId);
                        PoseNetworking.broadcastAnimState(player, 0); // NONE
                    }
                }
            }
        }
    }

    
    private static void startFallDapCharge(ServerPlayer player) {
        UUID playerId = player.getUUID();
        fallDapPlayers.put(playerId, FallDapState.CHARGING);

        fallChargeStartTime.put(playerId, System.currentTimeMillis());

        // Broadcast animation
        broadcastFallDapAnim(player, 1); // CHARGING state

        player.sendOverlayMessage(Component.literal("§e§l FALL DAP CHARGING! "));
    }

  
    private static void resetToNormalCharge(ServerPlayer player) {
        UUID playerId = player.getUUID();
        fallDapPlayers.remove(playerId);
        fallStartY.remove(playerId);
        fallChargeStartTime.remove(playerId);

        broadcastFallDapAnim(player, 0); 

        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE.ordinal());

        player.sendOverlayMessage(Component.literal("§7Fall dap reset - touched ground"));
    }

    
    public static boolean isInFallDapState(UUID playerId) {
        FallDapState state = fallDapPlayers.get(playerId);
        return state == FallDapState.CHARGING || state == FallDapState.FALLING;
    }

    
    public static boolean isReadyToFallDap(UUID playerId) {
        return fallDapPlayers.get(playerId) == FallDapState.FALLING;
    }

    
    public static void executeFallDapHit(ServerLevel world, Vec3 pos,
                                         ServerPlayer attacker, ServerPlayer victim) {
        UUID attackerId = attacker.getUUID();

        broadcastFallDapAnim(attacker, 3); // FALL_HIT state

        cleanup(attackerId);

    }


    private static void squashPlayer(ServerPlayer attacker, ServerPlayer victim) {
        ServerLevel world = attacker.level();
        Vec3 pos = victim.position();

        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.8f);

        world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 1, pos.z, 30, 0.5, 0.3, 0.5, 0.2);
        world.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 20, 0.5, 0.2, 0.5, 0.05);

        dropHandItems(victim, world, pos);

        victim.hurtClient(world.damageSources().playerAttack((Player)attacker));

        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 420, 2, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 420, 250, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0, false, false));

        victim.setDeltaMovement(0, 0, 0);
        victim.hurtMarked = true;

        squashedPlayers.put(victim.getUUID(), System.currentTimeMillis() + SQUASHED_DURATION_MS);

        // Broadcast squash animation
        for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new SquashAnimPayload(victim.getUUID()));
        }

        // Messages
        attacker.sendOverlayMessage(Component.literal("§c§l💀 SQUASHED! 💀"));
        victim.sendOverlayMessage(Component.literal("§c§lYOU GOT SQUASHED FOR 25 SECONDS!"));
    }

  
    private static void dropHandItems(ServerPlayer player, ServerLevel world, Vec3 pos) {
        // Drop main hand item
        net.minecraft.world.item.ItemStack mainStack = player.getMainHandItem();
        if (!mainStack.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity mainItem = new net.minecraft.world.entity.item.ItemEntity(
                    world, pos.x, pos.y + 0.5, pos.z, mainStack.copy()
            );
            mainItem.setDeltaMovement(
                    (world.getRandom().nextDouble() - 0.5) * 0.3,
                    world.getRandom().nextDouble() * 0.2 + 0.1,
                    (world.getRandom().nextDouble() - 0.5) * 0.3
            );
            world.addFreshEntity(mainItem);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        }

        net.minecraft.world.item.ItemStack offStack = player.getOffhandItem();
        if (!offStack.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity offItem = new net.minecraft.world.entity.item.ItemEntity(
                    world, pos.x, pos.y + 0.5, pos.z, offStack.copy()
            );
            offItem.setDeltaMovement(
                    (world.getRandom().nextDouble() - 0.5) * 0.3,
                    world.getRandom().nextDouble() * 0.2 + 0.1,
                    (world.getRandom().nextDouble() - 0.5) * 0.3
            );
            world.addFreshEntity(offItem);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    
    private static ServerPlayer findSquashTarget(ServerPlayer attacker, double horizontalRange) {
        double attackerY = attacker.getY();

        for (ServerPlayer other : attacker.level().players()) {
            if (other == attacker) continue;

            if (isSquashed(other.getUUID())) continue;

            double otherY = other.getY();

            double heightDiff = attackerY - otherY;
            if (heightDiff < 0.5 || heightDiff > 3.0) continue;

            double dx = attacker.getX() - other.getX();
            double dz = attacker.getZ() - other.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDist <= horizontalRange) {
                return other;
            }
        }
        return null;
    }

    private static ServerPlayer findNearbyPlayer(ServerPlayer player, double range) {
        for (ServerPlayer other : player.level().players()) {
            if (other == player) continue;
            if (other.distanceToSqr(player) <= range * range) {
                return other;
            }
        }
        return null;
    }

  
    public static boolean isSquashed(UUID playerId) {
        Long endTime = squashedPlayers.get(playerId);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            squashedPlayers.remove(playerId);
            return false;
        }
        return true;
    }

  
    private static void broadcastFallDapAnim(ServerPlayer player, int state) {
        var server = player.level().getServer();
        if (server == null) return;

        FallDapAnimPayload payload = new FallDapAnimPayload(player.getUUID(), state);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    
    public static void cleanup(UUID playerId) {
        fallDapPlayers.remove(playerId);
        fallStartY.remove(playerId);
        fallChargeStartTime.remove(playerId);
        squashedPlayers.remove(playerId);
    }
}
