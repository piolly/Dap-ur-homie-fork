package com.cooptest;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record QTEWindowPayload(UUID playerId, String button, int stage, long windowStart, long windowEnd) implements CustomPacketPayload {
    public static final Identifier QTE_WINDOW_ID = Identifier.fromNamespaceAndPath("cooptest", "qte_window");
    public static final Type<QTEWindowPayload> ID = new Type<>(QTE_WINDOW_ID);
    public static final StreamCodec<FriendlyByteBuf, QTEWindowPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUUID(payload.playerId);
                buf.writeUtf(payload.button);
                buf.writeInt(payload.stage);
                buf.writeLong(payload.windowStart);
                buf.writeLong(payload.windowEnd);
            },
            buf -> new QTEWindowPayload(buf.readUUID(), buf.readUtf(), buf.readInt(), buf.readLong(), buf.readLong())
    );
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}