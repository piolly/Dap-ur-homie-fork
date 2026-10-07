package com.cooptest;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public class HeavenDapPayloads {
    public record HeavenDapStartPayload() implements CustomPacketPayload {
        public static final Type<HeavenDapStartPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "heaven_dap_start"));
        public static final StreamCodec<FriendlyByteBuf, HeavenDapStartPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {},
                        buf -> new HeavenDapStartPayload()
                );
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
    public record HeavenDapEndPayload() implements CustomPacketPayload {
        public static final Type<HeavenDapEndPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "heaven_dap_end"));
        public static final StreamCodec<FriendlyByteBuf, HeavenDapEndPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {},
                        buf -> new HeavenDapEndPayload()
                );
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
    public record RestoreVolumePayload() implements CustomPacketPayload {
        public static final Type<RestoreVolumePayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "restore_volume"));
        public static final StreamCodec<FriendlyByteBuf, RestoreVolumePayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {},
                        buf -> new RestoreVolumePayload()
                );
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
    public record HeavenImpactPayload() implements CustomPacketPayload {
        public static final Type<HeavenImpactPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "heaven_impact"));
        public static final StreamCodec<FriendlyByteBuf, HeavenImpactPayload> CODEC =
                StreamCodec.ofMember((payload, buf) -> {}, buf -> new HeavenImpactPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(HeavenDapStartPayload.ID, HeavenDapStartPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HeavenDapEndPayload.ID, HeavenDapEndPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RestoreVolumePayload.ID, RestoreVolumePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HeavenImpactPayload.ID, HeavenImpactPayload.CODEC);
    }
}