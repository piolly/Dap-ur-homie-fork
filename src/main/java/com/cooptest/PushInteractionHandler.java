package com.cooptest;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PushInteractionHandler {

    private static final HashMap<UUID, Long> cooldowns = new HashMap<>();
    public static final HashMap<UUID, Long> pushImmunity = new HashMap<>();
    private static final long PUSH_IMMUNITY_MS = 500;

    // JUMP PUSH PERMSSION
    public static final HashMap<UUID, Long> lastJumpTime = new HashMap<>();
    private static final long JUMP_WINDOW_MS = 1000;

    private static final HashMap<UUID, PushRequest> pendingJumpPush = new HashMap<>();
    private static final long REQUEST_TIMEOUT_MS = 5000;

    private static class PushRequest {
        UUID pusher;
        double velocity;
        long timestamp;

        PushRequest(UUID pusher, double velocity) {
            this.pusher = pusher;
            this.velocity = velocity;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static final Identifier PUSH_ANIM_ID = Identifier.fromNamespaceAndPath("cooptest", "push_anim");

    public record PushAnimPayload(UUID playerId) implements CustomPacketPayload {
        public static final Type<PushAnimPayload> ID = new Type<>(PUSH_ANIM_ID);
        public static final StreamCodec<FriendlyByteBuf, PushAnimPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> buf.writeUUID(payload.playerId),
                        buf -> new PushAnimPayload(buf.readUUID())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(PushAnimPayload.ID, PushAnimPayload.CODEC);
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(entity instanceof Player target)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(target instanceof ServerPlayer serverTarget)) return InteractionResult.PASS;

            long now = System.currentTimeMillis();

            PushRequest request = pendingJumpPush.get(player.getUUID());
            if (request != null && request.pusher.equals(target.getUUID())) {

                if (now - request.timestamp > REQUEST_TIMEOUT_MS) {
                    pendingJumpPush.remove(player.getUUID());
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§cRequest expired!"), true);
                    return InteractionResult.FAIL;
                }

                if (player.distanceTo(target) > 2.5f) {
                    pendingJumpPush.remove(player.getUUID());
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§cToo far away!"), true);
                    return InteractionResult.FAIL;
                }

                pendingJumpPush.remove(player.getUUID());
                serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§a✓ JUMP PUSH accepted!"), true);
                serverTarget.displayClientMessage(net.minecraft.network.chat.Component.literal("§a✓ " + serverPlayer.getName().getString() + " accepted!"), true);

                executePush(serverTarget, serverPlayer, request.velocity, "§6§lJUMP PUSH!", now);
                return InteractionResult.SUCCESS;
            }

            PoseState pose = PoseNetworking.poseStates.getOrDefault(player.getUUID(), PoseState.NONE);

            if (pose == PoseState.GRAB_READY || pose == PoseState.GRAB_HOLDING) return InteractionResult.PASS;
            if (HighFiveHandler.isInBlockingState(player.getUUID())) return InteractionResult.PASS;
            if (pose != PoseState.PUSH_IDLE) return InteractionResult.PASS;
            if (player.distanceTo(target) > 2.5f) return InteractionResult.PASS;

            if (cooldowns.containsKey(player.getUUID()) && now - cooldowns.get(player.getUUID()) < 3000) return InteractionResult.FAIL;
            if (cooldowns.containsKey(target.getUUID()) && now - cooldowns.get(target.getUUID()) < 3000) return InteractionResult.FAIL;

            // DETERMINE PUSH POWER
            double baseLaunchVelocity;
            String pushType;

            Long jumpTime = lastJumpTime.get(player.getUUID());
            boolean jumped = jumpTime != null && (now - jumpTime) < JUMP_WINDOW_MS;

            if (jumped) {
                baseLaunchVelocity = 10.0;  // 40+ blocks!
                pushType = "§6§lJUMP PUSH!";
            } else if (player.isShiftKeyDown()) {
                baseLaunchVelocity = 2.0;  // 4 blocks
                pushType = "§7Gentle Push";
            } else if (player.isSprinting()) {
                baseLaunchVelocity = 8.0;  // 30 blocks
                pushType = "§c§lMEGA PUSH!";
            } else {
                baseLaunchVelocity = 6.0;  // 20 blocks
                pushType = "§ePush";
            }

            // ALL PUSHES REQUIRE PERMISSION!

            // PREVENT SPAM: Check if request already exists
            if (pendingJumpPush.containsKey(target.getUUID())) {
                // Already have a pending request for this target
                return InteractionResult.FAIL;
            }

            double velocity = calculateVelocity(serverTarget, baseLaunchVelocity);
            pendingJumpPush.put(target.getUUID(), new PushRequest(player.getUUID(), velocity));

            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§e⚡ Request sent to " + serverTarget.getName().getString() + "!"), false);
            serverTarget.displayClientMessage(net.minecraft.network.chat.Component.literal("§e⚡ " + serverPlayer.getName().getString() + " wants to push you! §aRight-click them to accept!"), true);

            return InteractionResult.SUCCESS;
        });
    }

    private static double calculateVelocity(ServerPlayer target, double base) {
        int ceiling = getCeilingHeight(target);
        if (ceiling < 10 && ceiling > 0) {
            double maxH = Math.max(2, ceiling - 1);
            double v = Math.sqrt(2 * 0.08 * 20 * maxH);
            return Math.min(v, base);
        }
        return base;
    }

    private static void executePush(ServerPlayer pusher, ServerPlayer target, double velocity, String type, long now) {
        PoseEffects.playActionEffects(pusher, target);
        LaunchedPlayerTracker.markPlayerAsLaunched(target.getUUID());

        PushAnimPayload payload = new PushAnimPayload(pusher.getUUID());
        for (ServerPlayer p : PlayerLookup.tracking(pusher)) {
            ServerPlayNetworking.send(p, payload);
        }
        ServerPlayNetworking.send(pusher, payload);

        UUID carried = GrabMechanic.holding.get(target.getUUID());
        if (carried != null) {
            ServerPlayer c = target.level().getServer().getPlayerList().getPlayer(carried);
            if (c != null) {
                LaunchedPlayerTracker.markPlayerAsLaunched(c.getUUID());
                c.push(0, velocity + 0.2, 0);
                c.hurtMarked = true;
                pushImmunity.put(c.getUUID(), now);
            }
        }

        target.push(0, velocity, 0);
        target.hurtMarked = true;
        pushImmunity.put(target.getUUID(), now);

        cooldowns.put(pusher.getUUID(), now);
        cooldowns.put(target.getUUID(), now);

        PoseNetworking.broadcastPoseChange(
                Objects.requireNonNull(pusher.level().getServer()),
                pusher.getUUID(),
                PoseState.PUSH_ACTION
        );

        pusher.displayClientMessage(net.minecraft.network.chat.Component.literal(type), true);
    }

    public static boolean hasPushImmunity(UUID uuid) {
        Long t = pushImmunity.get(uuid);
        if (t == null) return false;
        if (System.currentTimeMillis() - t < PUSH_IMMUNITY_MS) return true;
        pushImmunity.remove(uuid);
        return false;
    }

    private static int getCeilingHeight(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        for (int y = 1; y <= 15; y++) {
            BlockPos check = pos.above(y);
            BlockState state = player.level().getBlockState(check);
            if (!state.isAir() && state.isRedstoneConductor(player.level(), check)) {
                return y;
            }
        }
        return -1;
    }

    public static void cleanupExpiredImmunity() {
        long now = System.currentTimeMillis();
        pushImmunity.entrySet().removeIf(e -> now - e.getValue() > PUSH_IMMUNITY_MS);
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();

        // Track jumps
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.onGround() && p.getDeltaMovement().y > 0.1) {
                lastJumpTime.put(p.getUUID(), now);
            }
        }

        // Cleanup
        pendingJumpPush.entrySet().removeIf(e -> {
            if (now - e.getValue().timestamp > REQUEST_TIMEOUT_MS) {
                ServerPlayer t = server.getPlayerList().getPlayer(e.getKey());
                if (t != null) t.displayClientMessage(net.minecraft.network.chat.Component.literal("§cRequest expired!"), true);
                return true;
            }
            return false;
        });

        lastJumpTime.entrySet().removeIf(e -> now - e.getValue() > 2000);
    }
}