package com.cooptest;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record QTEClearPayload(UUID playerId) implements CustomPacketPayload {
   public static final Identifier QTE_CLEAR_ID = Identifier.fromNamespaceAndPath("cooptest", "qte_clear");
   public static final Type<QTEClearPayload> ID = new Type(QTE_CLEAR_ID);
   public static final StreamCodec<FriendlyByteBuf, QTEClearPayload> CODEC = StreamCodec.ofMember(
      (payload, buf) -> buf.writeUUID(payload.playerId), buf -> new QTEClearPayload(buf.readUUID())
   );

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
