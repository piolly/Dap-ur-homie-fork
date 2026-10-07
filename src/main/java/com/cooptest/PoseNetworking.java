package com.cooptest;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.UUID;

public class PoseNetworking {

    public static final HashMap<UUID, PoseState> poseStates = new HashMap<>();


    public static final HashMap<UUID, Float> chargeProgress = new HashMap<>();

    public record PoseSyncPayload(UUID playerId, int poseOrdinal) implements CustomPacketPayload {
        public static final Type<PoseSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "pose_sync"));

        public static final StreamCodec<FriendlyByteBuf, PoseSyncPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.playerId);
                    buf.writeInt(payload.poseOrdinal);
                },
                buf -> new PoseSyncPayload(buf.readUUID(), buf.readInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public record ChargeSyncPayload(UUID playerId, float progress) implements CustomPacketPayload {
        public static final Type<ChargeSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "charge_sync"));

        public static final StreamCodec<FriendlyByteBuf, ChargeSyncPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.playerId);
                    buf.writeFloat(payload.progress);
                },
                buf -> new ChargeSyncPayload(buf.readUUID(), buf.readFloat())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public record ThrowAnimPayload(UUID playerId) implements CustomPacketPayload {
        public static final Type<ThrowAnimPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "throw_anim"));

        public static final StreamCodec<FriendlyByteBuf, ThrowAnimPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> buf.writeUUID(payload.playerId),
                buf -> new ThrowAnimPayload(buf.readUUID())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record AnimStateSyncPayload(UUID playerId, int animStateOrdinal) implements CustomPacketPayload {
        public static final Type<AnimStateSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "anim_state_sync"));

        public static final StreamCodec<FriendlyByteBuf, AnimStateSyncPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.playerId);
                    buf.writeInt(payload.animStateOrdinal);
                },
                buf -> new AnimStateSyncPayload(buf.readUUID(), buf.readInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(PoseSyncPayload.ID, PoseSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PoseSyncPayload.ID, PoseSyncPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ChargeSyncPayload.ID, ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChargeSyncPayload.ID, ChargeSyncPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ThrowAnimPayload.ID, ThrowAnimPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ThrowAnimPayload.ID, ThrowAnimPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(AnimStateSyncPayload.ID, AnimStateSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AnimStateSyncPayload.ID, AnimStateSyncPayload.CODEC);
    }

    public static void registerServerReceiver() {

        ServerPlayNetworking.registerGlobalReceiver(PoseSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            PoseState state = PoseState.values()[payload.poseOrdinal()];

            context.server().execute(() -> {
                // Check if player is trying to enter GRAB_READY while in blocking state
                if (state == PoseState.GRAB_READY && HighFiveHandler.isInBlockingState(id)) {
                    // Reject - send back NONE
                    ServerPlayer player = context.server().getPlayerList().getPlayer(id);
                    if (player != null) {
                        ServerPlayNetworking.send(player, new PoseSyncPayload(id, PoseState.NONE.ordinal()));
                    }
                    return;
                }

                poseStates.put(id, state);

                for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, new PoseSyncPayload(id, state.ordinal()));
                }
            });
        });

        // Charge progress sync
        ServerPlayNetworking.registerGlobalReceiver(ChargeSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            float progress = payload.progress();

            context.server().execute(() -> {
                chargeProgress.put(id, progress);
                // Broadcast to all OTHER clients (not sender)
                for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(id)) {
                        ServerPlayNetworking.send(player, new ChargeSyncPayload(id, progress));
                    }
                }
            });
        });

        // Throw animation trigger
        ServerPlayNetworking.registerGlobalReceiver(ThrowAnimPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();

            context.server().execute(() -> {
                // Broadcast to all OTHER clients
                for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(id)) {
                        ServerPlayNetworking.send(player, new ThrowAnimPayload(id));
                    }
                }
            });
        });

        // PAL Animation state sync
        ServerPlayNetworking.registerGlobalReceiver(AnimStateSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            int animState = payload.animStateOrdinal();

            context.server().execute(() -> {
                // Broadcast to all OTHER clients
                for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(id)) {
                        ServerPlayNetworking.send(player, new AnimStateSyncPayload(id, animState));
                    }
                }
            });
        });
    }

    public static void registerClientReceiver() {
        // Pose sync
        ClientPlayNetworking.registerGlobalReceiver(PoseSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            PoseState state = PoseState.values()[payload.poseOrdinal()];
            poseStates.put(id, state);

            context.client().execute(() -> {
                if (context.client().level != null) {
                    for (Player player : context.client().level.players()) {
                        if (player.getUUID().equals(id)) {
                            com.cooptest.client.CoopAnimationHandler.updatePlayerAnimation(player, state);
                            break;
                        }
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ChargeSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            float progress = payload.progress();
            chargeProgress.put(id, progress);
        });

        ClientPlayNetworking.registerGlobalReceiver(ThrowAnimPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            // Trigger yeet animation for this player
            ArmPoseTracker.throwAnimationStart.put(id, System.currentTimeMillis());
        });

        // PAL Animation state sync
        ClientPlayNetworking.registerGlobalReceiver(AnimStateSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            int animState = payload.animStateOrdinal();

            // Find the player and apply animation
            context.client().execute(() -> {
                if (context.client().level != null) {
                    var localPlayer = context.client().player;

                    // If NONE state, cleanup all client handlers for this player
                    if (animState == 0) {
                        com.cooptest.client.ChargedDapClientHandler.cleanup(id);
                        com.cooptest.client.CoopAnimationHandler.cleanup(id);
                        com.cooptest.client.HighFiveClientHandler.cleanup(id);
                        com.cooptest.client.PushClientHandler.cleanup(id);
                        com.cooptest.client.MahitoClientHandler.cleanup(id);
                        com.cooptest.client.FallDapClientHandler.cleanup(id);
                        ArmPoseTracker.cleanup(id);
                    }

                    // Find the player entity that matches the UID
                    Player targetPlayer = null;
                    for (Player player : context.client().level.players()) {
                        if (player.getUUID().equals(id)) {
                            targetPlayer = player;
                            break;
                        }
                    }

                    if (targetPlayer != null) {
                        if (localPlayer != null && (animState == 10 || animState == 18)) {
                            boolean isLocalPlayer = id.equals(localPlayer.getUUID());
                            String animName = (animState == 10) ? "DAP_HIT" : "PERFECT_DAP_HIT";
                            String playerName = targetPlayer.getName().getString();

                        /*    if (isLocalPlayer) {
                                localPlayer.sendMessage(
                                        net.minecraft.text.Text.literal("§c[DEBUG] Anim " + animName + " for ME (this should only show if I did the dap!)"),
                                        false
                                );
                            } else {
                                localPlayer.sendMessage(
                                        net.minecraft.text.Text.literal("§e[DEBUG] Anim " + animName + " for " + playerName + " (not me)"), false
                                );
                            } */ // animation debug text stuff
                        }

                        // Apply the animation to THIS SPECIFIC PLAYER ONLY
                        com.cooptest.client.CoopAnimationHandler.setAnimStateFromNetwork(targetPlayer, animState);
                    }
                }
            });
        });
    }

    public static void sendPoseToServer(UUID playerId, PoseState state) {
        ClientPlayNetworking.send(new PoseSyncPayload(playerId, state.ordinal()));
    }

    public static void sendChargeProgress(UUID playerId, float progress) {
        ClientPlayNetworking.send(new ChargeSyncPayload(playerId, progress));
    }

    public static void sendThrowAnimation(UUID playerId) {
        ClientPlayNetworking.send(new ThrowAnimPayload(playerId));
    }

    public static void sendAnimState(UUID playerId, int animStateOrdinal) {
        ClientPlayNetworking.send(new AnimStateSyncPayload(playerId, animStateOrdinal));
    }

    public static void broadcastPoseChange(MinecraftServer server, UUID playerId, PoseState state) {
        poseStates.put(playerId, state);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new PoseSyncPayload(playerId, state.ordinal()));
        }
    }

    /**
     * Broadcast animation state from server to all clients
     */
    public static void broadcastAnimState(ServerPlayer sourcePlayer, int animStateOrdinal) {
        var server = sourcePlayer.level().getServer();
        if (server == null) return;

        UUID playerId = sourcePlayer.getUUID();
        AnimStateSyncPayload payload = new AnimStateSyncPayload(playerId, animStateOrdinal);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}