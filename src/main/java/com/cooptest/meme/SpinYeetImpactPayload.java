package com.cooptest.meme;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SpinYeetImpactPayload() implements CustomPacketPayload {
   public static final Type<SpinYeetImpactPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "spin_yeet_impact"));
   public static final StreamCodec<FriendlyByteBuf, SpinYeetImpactPayload> CODEC = StreamCodec.ofMember(
      (payload, buf) -> {}, buf -> new SpinYeetImpactPayload()
   );

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
