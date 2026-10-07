package com.cooptest;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;


public record QTEButtonPressPayload(String button) implements CustomPacketPayload {

    public static final Identifier QTE_BUTTON_PRESS_ID = Identifier.fromNamespaceAndPath("cooptest", "qte_button_press");
    public static final Type<QTEButtonPressPayload> ID = new Type<>(QTE_BUTTON_PRESS_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, QTEButtonPressPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QTEButtonPressPayload::button,
            QTEButtonPressPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}