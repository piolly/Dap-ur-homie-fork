package com.cooptest.meme;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SpinYeetGrabberYawPayload(float yawDelta) implements CustomPacketPayload {
   public static final Type<SpinYeetGrabberYawPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spin_yeet_grabber_yaw"));
   public static final StreamCodec<FriendlyByteBuf, SpinYeetGrabberYawPayload> CODEC = StreamCodec.ofMember(
      (payload, buf) -> buf.writeFloat(payload.yawDelta()), buf -> new SpinYeetGrabberYawPayload(buf.readFloat())
   );

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
