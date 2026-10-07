package com.cooptest;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class BlackHoodNetworking {
   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(BlackHoodNetworking.HoodStatePayload.ID, BlackHoodNetworking.HoodStatePayload.CODEC);
   }

   public static void broadcastHoodState(MinecraftServer server, UUID targetId, boolean hasHood) {
      BlackHoodNetworking.HoodStatePayload payload = new BlackHoodNetworking.HoodStatePayload(targetId, hasHood);

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   public record HoodStatePayload(UUID targetId, boolean hasHood) implements CustomPacketPayload {
      public static final Type<BlackHoodNetworking.HoodStatePayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "hood_state"));
      public static final StreamCodec<FriendlyByteBuf, BlackHoodNetworking.HoodStatePayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeUUID(p.targetId());
         buf.writeBoolean(p.hasHood());
      }, buf -> new BlackHoodNetworking.HoodStatePayload(buf.readUUID(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
