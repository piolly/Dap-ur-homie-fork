package com.cooptest.meme;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SpinYeetReleasePayload() implements CustomPacketPayload {
   public static final Type<SpinYeetReleasePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spin_yeet_release"));
   public static final StreamCodec<FriendlyByteBuf, SpinYeetReleasePayload> CODEC = StreamCodec.ofMember(
      (payload, buf) -> {}, buf -> new SpinYeetReleasePayload()
   );

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
