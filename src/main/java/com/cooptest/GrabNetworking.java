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

public class GrabNetworking {
   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(GrabNetworking.ThrowRequestPayload.ID, GrabNetworking.ThrowRequestPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(GrabNetworking.DropRequestPayload.ID, GrabNetworking.DropRequestPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(GrabNetworking.EscapeRequestPayload.ID, GrabNetworking.EscapeRequestPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(GrabNetworking.ElytraBoostRequestPayload.ID, GrabNetworking.ElytraBoostRequestPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(GrabNetworking.AirMovementPayload.ID, GrabNetworking.AirMovementPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(GrabNetworking.ShieldTogglePayload.ID, GrabNetworking.ShieldTogglePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(GrabNetworking.GrabStatePayload.ID, GrabNetworking.GrabStatePayload.CODEC);
   }

   public static void registerServerReceivers() {
      ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.ThrowRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         float power = payload.power();
         context.server().execute(() -> {
            if (GrabMechanic.isHolding(player)) {
               GrabMechanic.tryThrow(player, power);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.DropRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> {
            if (GrabMechanic.isHolding(player)) {
               GrabMechanic.tryDrop(player);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.EscapeRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> {
            if (GrabMechanic.isBeingHeld(player)) {
               GrabMechanic.tryEscape(player);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.ElytraBoostRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> GrabMechanic.requestElytraBoost(player.getUUID()));
      });
      ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.AirMovementPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> GrabMechanic.setAirMovementInput(player.getUUID(), payload.forward(), payload.strafe()));
      });
      ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.ShieldTogglePayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> GrabMechanic.toggleShieldMode(player));
      });
   }

   public static void broadcastGrabState(MinecraftServer server, UUID holderUuid, UUID heldUuid, boolean isStart) {
      GrabNetworking.GrabStatePayload payload = new GrabNetworking.GrabStatePayload(holderUuid, heldUuid, isStart);

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public record AirMovementPayload(float forward, float strafe) implements CustomPacketPayload {
      public static final Type<GrabNetworking.AirMovementPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "air_movement"));
      public static final StreamCodec<FriendlyByteBuf, GrabNetworking.AirMovementPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeFloat(payload.forward);
         buf.writeFloat(payload.strafe);
      }, buf -> new GrabNetworking.AirMovementPayload(buf.readFloat(), buf.readFloat()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record DropRequestPayload() implements CustomPacketPayload {
      public static final Type<GrabNetworking.DropRequestPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "drop_request"));
      public static final StreamCodec<FriendlyByteBuf, GrabNetworking.DropRequestPayload> CODEC = StreamCodec.unit(new GrabNetworking.DropRequestPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ElytraBoostRequestPayload() implements CustomPacketPayload {
      public static final Type<GrabNetworking.ElytraBoostRequestPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "elytra_boost"));
      public static final StreamCodec<FriendlyByteBuf, GrabNetworking.ElytraBoostRequestPayload> CODEC = StreamCodec.unit(
         new GrabNetworking.ElytraBoostRequestPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record EscapeRequestPayload() implements CustomPacketPayload {
      public static final Type<GrabNetworking.EscapeRequestPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "escape_request"));
      public static final StreamCodec<FriendlyByteBuf, GrabNetworking.EscapeRequestPayload> CODEC = StreamCodec.unit(new GrabNetworking.EscapeRequestPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record GrabStatePayload(UUID holderUuid, UUID heldUuid, boolean isStart) implements CustomPacketPayload {
      public static final Type<GrabNetworking.GrabStatePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "grab_state"));
      public static final StreamCodec<FriendlyByteBuf, GrabNetworking.GrabStatePayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.holderUuid);
         buf.writeUUID(payload.heldUuid);
         buf.writeBoolean(payload.isStart);
      }, buf -> new GrabNetworking.GrabStatePayload(buf.readUUID(), buf.readUUID(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShieldTogglePayload() implements CustomPacketPayload {
      public static final Type<GrabNetworking.ShieldTogglePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "shield_toggle"));
      public static final StreamCodec<FriendlyByteBuf, GrabNetworking.ShieldTogglePayload> CODEC = StreamCodec.unit(new GrabNetworking.ShieldTogglePayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ThrowRequestPayload(float power) implements CustomPacketPayload {
      public static final Type<GrabNetworking.ThrowRequestPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "throw_request"));
      public static final StreamCodec<FriendlyByteBuf, GrabNetworking.ThrowRequestPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> buf.writeFloat(payload.power), buf -> new GrabNetworking.ThrowRequestPayload(buf.readFloat())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
