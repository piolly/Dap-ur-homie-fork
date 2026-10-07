package com.cooptest.client;

import com.cooptest.HeavenDapPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;


public class HeavenDapClientHandler {


    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            HeavenWhiteOverlay.render(context, tickCounter.getGameTimeDeltaTicks());
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HeavenWhiteOverlay.tick();
        });

        ClientPlayNetworking.registerGlobalReceiver(
                HeavenDapPayloads.HeavenDapStartPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    HeavenWhiteOverlay.start();
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                HeavenDapPayloads.HeavenDapEndPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    HeavenWhiteOverlay.stop();
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                HeavenDapPayloads.RestoreVolumePayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (ctx.client().options != null) {
                        ctx.client().options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(1.0);
                    }
                })
        );

    }


    public static record QTEButtonPressPayload(String button) implements CustomPacketPayload {

        public static final Identifier QTE_BUTTON_PRESS_ID = Identifier.fromNamespaceAndPath("cooptest", "qte_button_press");
        public static final Type<QTEButtonPressPayload> ID = new Type<>(QTE_BUTTON_PRESS_ID);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, QTEButtonPressPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, QTEButtonPressPayload::button,
                QTEButtonPressPayload::new
        );
    }
}