package com.cooptest.meme;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SpinYeetGrabPayload(UUID targetUUID) implements CustomPacketPayload {
   public static final Type<SpinYeetGrabPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spin_yeet_grab"));
   public static final StreamCodec<FriendlyByteBuf, SpinYeetGrabPayload> CODEC = StreamCodec.ofMember(
      (payload, buf) -> buf.writeUUID(payload.targetUUID()), buf -> new SpinYeetGrabPayload(buf.readUUID())
   );

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
