package com.cooptest.meme;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SpinYeetEndPayload(UUID grabbedUUID, boolean wasYeeted) implements CustomPacketPayload {
   public static final Type<SpinYeetEndPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spin_yeet_end"));
   public static final StreamCodec<FriendlyByteBuf, SpinYeetEndPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
      buf.writeUUID(payload.grabbedUUID());
      buf.writeBoolean(payload.wasYeeted());
   }, buf -> new SpinYeetEndPayload(buf.readUUID(), buf.readBoolean()));

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
