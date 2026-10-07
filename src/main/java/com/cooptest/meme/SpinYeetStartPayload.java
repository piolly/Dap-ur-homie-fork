package com.cooptest.meme;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SpinYeetStartPayload(UUID grabberUUID, UUID grabbedUUID) implements CustomPacketPayload {
   public static final Type<SpinYeetStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spin_yeet_start"));
   public static final StreamCodec<FriendlyByteBuf, SpinYeetStartPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
      buf.writeUUID(payload.grabberUUID());
      buf.writeUUID(payload.grabbedUUID());
   }, buf -> new SpinYeetStartPayload(buf.readUUID(), buf.readUUID()));

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
