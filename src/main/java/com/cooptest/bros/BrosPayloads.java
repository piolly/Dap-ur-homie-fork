package com.cooptest.bros;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public final class BrosPayloads {
   private static final String NS = "cooptest";
   public static final byte PHASE_STOP = 0;
   public static final byte PHASE_ENTRY = 1;
   public static final byte PHASE_WALK = 2;
   public static final byte ABILITY_RUSH = 0;
   public static final byte ABILITY_PULSE = 1;
   public static final byte ABILITY_CHARGE = 2;
   public static final byte ABILITY_BUNKER = 3;
   public static final int HUD_RUSH_A = 1;
   public static final int HUD_RUSH_B = 2;
   public static final int HUD_PULSE_A = 4;
   public static final int HUD_PULSE_B = 8;
   public static final int HUD_HOLD_A = 16;
   public static final int HUD_HOLD_B = 32;
   public static final int HUD_FILLING = 64;
   public static final int HUD_COMBAT = 128;
   public static final int HUD_LOW_HP = 256;
   public static final int HUD_PULSE_OK = 512;
   public static final int HUD_RUSH_OK = 1024;
   public static final int HUD_BUNKER = 2048;

   private BrosPayloads() {
   }

   public static void register() {
      PayloadTypeRegistry.playC2S().register(BrosPayloads.Arm.ID, BrosPayloads.Arm.CODEC);
      PayloadTypeRegistry.playC2S().register(BrosPayloads.Input.ID, BrosPayloads.Input.CODEC);
      PayloadTypeRegistry.playC2S().register(BrosPayloads.End.ID, BrosPayloads.End.CODEC);
      PayloadTypeRegistry.playC2S().register(BrosPayloads.Ability.ID, BrosPayloads.Ability.CODEC);
      PayloadTypeRegistry.playS2C().register(BrosPayloads.State.ID, BrosPayloads.State.CODEC);
   }

   public record Ability(byte kind, boolean down) implements CustomPacketPayload {
      public static final Type<BrosPayloads.Ability> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "bros_ability"));
      public static final StreamCodec<FriendlyByteBuf, BrosPayloads.Ability> CODEC = StreamCodec.ofMember(
         BrosPayloads.Ability::write, BrosPayloads.Ability::read
      );

      private void write(FriendlyByteBuf buf) {
         buf.writeByte(this.kind);
         buf.writeBoolean(this.down);
      }

      private static BrosPayloads.Ability read(FriendlyByteBuf buf) {
         return new BrosPayloads.Ability(buf.readByte(), buf.readBoolean());
      }

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record Arm(boolean armed) implements CustomPacketPayload {
      public static final Type<BrosPayloads.Arm> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "bros_arm"));
      public static final StreamCodec<FriendlyByteBuf, BrosPayloads.Arm> CODEC = StreamCodec.ofMember(BrosPayloads.Arm::write, BrosPayloads.Arm::read);

      private void write(FriendlyByteBuf buf) {
         buf.writeBoolean(this.armed);
      }

      private static BrosPayloads.Arm read(FriendlyByteBuf buf) {
         return new BrosPayloads.Arm(buf.readBoolean());
      }

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record End() implements CustomPacketPayload {
      public static final BrosPayloads.End INSTANCE = new BrosPayloads.End();
      public static final Type<BrosPayloads.End> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "bros_end"));
      public static final StreamCodec<FriendlyByteBuf, BrosPayloads.End> CODEC = StreamCodec.unit(INSTANCE);

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record Input(float forward, float right, float turn, boolean sprint) implements CustomPacketPayload {
      public static final Type<BrosPayloads.Input> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "bros_input"));
      public static final StreamCodec<FriendlyByteBuf, BrosPayloads.Input> CODEC = StreamCodec.ofMember(BrosPayloads.Input::write, BrosPayloads.Input::read);

      private void write(FriendlyByteBuf buf) {
         buf.writeFloat(this.forward);
         buf.writeFloat(this.right);
         buf.writeFloat(this.turn);
         buf.writeBoolean(this.sprint);
      }

      private static BrosPayloads.Input read(FriendlyByteBuf buf) {
         return new BrosPayloads.Input(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
      }

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record State(
      int idA,
      int idB,
      byte phase,
      boolean aLeft,
      double cx,
      double cz,
      float heading,
      float turnRate,
      double vx,
      double vz,
      double ya,
      double yb,
      int entryTicks,
      float shield,
      int hud,
      int rushCd,
      int pulseCd,
      float shieldMax,
      float domeRadius
   ) implements CustomPacketPayload {
      public static final Type<BrosPayloads.State> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "bros_state"));
      public static final StreamCodec<FriendlyByteBuf, BrosPayloads.State> CODEC = StreamCodec.ofMember(BrosPayloads.State::write, BrosPayloads.State::read);

      private void write(FriendlyByteBuf buf) {
         buf.writeVarInt(this.idA);
         buf.writeVarInt(this.idB);
         buf.writeByte(this.phase);
         buf.writeBoolean(this.aLeft);
         buf.writeDouble(this.cx);
         buf.writeDouble(this.cz);
         buf.writeFloat(this.heading);
         buf.writeFloat(this.turnRate);
         buf.writeDouble(this.vx);
         buf.writeDouble(this.vz);
         buf.writeDouble(this.ya);
         buf.writeDouble(this.yb);
         buf.writeVarInt(this.entryTicks);
         buf.writeFloat(this.shield);
         buf.writeVarInt(this.hud);
         buf.writeVarInt(this.rushCd);
         buf.writeVarInt(this.pulseCd);
         buf.writeFloat(this.shieldMax);
         buf.writeFloat(this.domeRadius);
      }

      private static BrosPayloads.State read(FriendlyByteBuf buf) {
         return new BrosPayloads.State(
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readByte(),
            buf.readBoolean(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readFloat(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readFloat(),
            buf.readFloat()
         );
      }

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
