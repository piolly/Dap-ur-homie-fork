package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public class HeavenDapPayloads {
   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(HeavenDapPayloads.HeavenDapStartPayload.ID, HeavenDapPayloads.HeavenDapStartPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HeavenDapPayloads.HeavenDapEndPayload.ID, HeavenDapPayloads.HeavenDapEndPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HeavenDapPayloads.RestoreVolumePayload.ID, HeavenDapPayloads.RestoreVolumePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HeavenDapPayloads.HeavenImpactPayload.ID, HeavenDapPayloads.HeavenImpactPayload.CODEC);
   }

   public record HeavenDapEndPayload() implements CustomPacketPayload {
      public static final Type<HeavenDapPayloads.HeavenDapEndPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "heaven_dap_end"));
      public static final StreamCodec<FriendlyByteBuf, HeavenDapPayloads.HeavenDapEndPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> {}, buf -> new HeavenDapPayloads.HeavenDapEndPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HeavenDapStartPayload() implements CustomPacketPayload {
      public static final Type<HeavenDapPayloads.HeavenDapStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "heaven_dap_start"));
      public static final StreamCodec<FriendlyByteBuf, HeavenDapPayloads.HeavenDapStartPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> {}, buf -> new HeavenDapPayloads.HeavenDapStartPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HeavenImpactPayload() implements CustomPacketPayload {
      public static final Type<HeavenDapPayloads.HeavenImpactPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "heaven_impact"));
      public static final StreamCodec<FriendlyByteBuf, HeavenDapPayloads.HeavenImpactPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> {}, buf -> new HeavenDapPayloads.HeavenImpactPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record RestoreVolumePayload() implements CustomPacketPayload {
      public static final Type<HeavenDapPayloads.RestoreVolumePayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "restore_volume"));
      public static final StreamCodec<FriendlyByteBuf, HeavenDapPayloads.RestoreVolumePayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> {}, buf -> new HeavenDapPayloads.RestoreVolumePayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
