package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public class GrabNetworking {

    // ===== PAYLOADS SKBIDI =====

    public record ThrowRequestPayload(float power) implements CustomPacketPayload {
        public static final Type<ThrowRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "throw_request"));
        public static final StreamCodec<FriendlyByteBuf, ThrowRequestPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> buf.writeFloat(payload.power),
                buf -> new ThrowRequestPayload(buf.readFloat())
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record DropRequestPayload() implements CustomPacketPayload {
        public static final Type<DropRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "drop_request"));
        public static final StreamCodec<FriendlyByteBuf, DropRequestPayload> CODEC = StreamCodec.unit(new DropRequestPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record EscapeRequestPayload() implements CustomPacketPayload {
        public static final Type<EscapeRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "escape_request"));
        public static final StreamCodec<FriendlyByteBuf, EscapeRequestPayload> CODEC = StreamCodec.unit(new EscapeRequestPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ElytraBoostRequestPayload() implements CustomPacketPayload {
        public static final Type<ElytraBoostRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "elytra_boost"));
        public static final StreamCodec<FriendlyByteBuf, ElytraBoostRequestPayload> CODEC = StreamCodec.unit(new ElytraBoostRequestPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record AirMovementPayload(float forward, float strafe) implements CustomPacketPayload {
        public static final Type<AirMovementPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "air_movement"));
        public static final StreamCodec<FriendlyByteBuf, AirMovementPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeFloat(payload.forward);
                    buf.writeFloat(payload.strafe);
                },
                buf -> new AirMovementPayload(buf.readFloat(), buf.readFloat())
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record GrabStatePayload(UUID holderUuid, UUID heldUuid, boolean isStart) implements CustomPacketPayload {
        public static final Type<GrabStatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "grab_state"));
        public static final StreamCodec<FriendlyByteBuf, GrabStatePayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.holderUuid);
                    buf.writeUUID(payload.heldUuid);
                    buf.writeBoolean(payload.isStart);
                },
                buf -> new GrabStatePayload(buf.readUUID(), buf.readUUID(), buf.readBoolean())
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ShieldTogglePayload() implements CustomPacketPayload {
        public static final Type<ShieldTogglePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "shield_toggle"));
        public static final StreamCodec<FriendlyByteBuf, ShieldTogglePayload> CODEC = StreamCodec.unit(new ShieldTogglePayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ===== REGISTRATION =====

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ThrowRequestPayload.ID, ThrowRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DropRequestPayload.ID, DropRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EscapeRequestPayload.ID, EscapeRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ElytraBoostRequestPayload.ID, ElytraBoostRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AirMovementPayload.ID, AirMovementPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShieldTogglePayload.ID, ShieldTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GrabStatePayload.ID, GrabStatePayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ThrowRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            float power = payload.power();
            context.server().execute(() -> {
                if (GrabMechanic.isHolding(player)) {
                    GrabMechanic.tryThrow(player, power);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DropRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (GrabMechanic.isHolding(player)) {
                    GrabMechanic.tryDrop(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(EscapeRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (GrabMechanic.isBeingHeld(player)) {
                    GrabMechanic.tryEscape(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ElytraBoostRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                GrabMechanic.requestElytraBoost(player.getUUID());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AirMovementPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                GrabMechanic.setAirMovementInput(player.getUUID(), payload.forward(), payload.strafe());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ShieldTogglePayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                GrabMechanic.toggleShieldMode(player);
            });
        });
    }



    public static void broadcastGrabState(MinecraftServer server, UUID holderUuid, UUID heldUuid, boolean isStart) {
        GrabStatePayload payload = new GrabStatePayload(holderUuid, heldUuid, isStart);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}