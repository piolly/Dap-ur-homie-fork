package com.cooptest.meme;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SpinYeetCameraPayload(float rollDegrees) implements CustomPacketPayload {
   public static final Type<SpinYeetCameraPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spin_yeet_camera"));
   public static final StreamCodec<FriendlyByteBuf, SpinYeetCameraPayload> CODEC = StreamCodec.ofMember(
      (payload, buf) -> buf.writeFloat(payload.rollDegrees()), buf -> new SpinYeetCameraPayload(buf.readFloat())
   );

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
