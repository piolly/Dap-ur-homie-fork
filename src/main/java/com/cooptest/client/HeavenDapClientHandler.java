package com.cooptest.client;

import com.cooptest.HeavenDapPayloads;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BeforeEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public class HeavenDapClientHandler {
   private static boolean wasPlayingLastTick = false;
   private static final RenderPipeline INVERT_GUI = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.GUI_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("cooptest", "pipeline/invert_gui"))
         .withBlend(new BlendFunction(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ZERO, SourceFactor.ZERO, DestFactor.ONE))
         .build()
   );

   public static void register() {
      WorldRenderEvents.BEFORE_ENTITIES.register((BeforeEntities)ctx -> {
         if (CoopImpactHandler.playing) {
            int argb = switch (CoopImpactHandler.currentFrameType) {
               case BLACK -> -16777216;
               case INVERT -> -16777216;
               case WHITE -> -1;
               case RED -> -65536;
               case CYAN -> -16711681;
            };
            Minecraft client = Minecraft.getInstance();
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(client.getMainRenderTarget().getColorTexture(), argb);
         }
      });
      WorldRenderEvents.END_MAIN.register(CoopShockwaveRenderer::render);
      HudRenderCallback.EVENT.register((HudRenderCallback)(context, tickCounter) -> {
         HeavenWhiteOverlay.render(context, tickCounter.getGameTimeDeltaTicks());
         if (CoopImpactHandler.playing) {
            int w = context.guiWidth();
            int h = context.guiHeight();
            switch (CoopImpactHandler.currentFrameType) {
               case BLACK:
                  context.fill(0, 0, w, h, -16777216);
                  break;
               case INVERT:
                  context.fill(INVERT_GUI, 0, 0, w, h, -1);
            }

            CoopSpeedLinesRenderer.render(context);
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         boolean wasPlaying = wasPlayingLastTick;
         wasPlayingLastTick = CoopImpactHandler.playing;
         CoopImpactHandler.tick();
         HeavenWhiteOverlay.tick();
         CoopCameraShakeHandler.tick();
         CoopChromaHandler.tick();
         CoopRadialBlurHandler.tick();
         CoopSpeedLinesRenderer.tick();
         if (wasPlaying && !CoopImpactHandler.playing && !CoopImpactHandler.suppressOverlay) {
            HeavenWhiteOverlay.start();
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(HeavenDapPayloads.HeavenDapStartPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         if (!HeavenWhiteOverlay.isActive()) {
            HeavenWhiteOverlay.start();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(HeavenDapPayloads.HeavenDapEndPayload.ID, (payload, ctx) -> ctx.client().execute(HeavenWhiteOverlay::stop));
      ClientPlayNetworking.registerGlobalReceiver(HeavenDapPayloads.HeavenImpactPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         CoopImpactHandler.start(CoopImpactHandler.HEAVEN_DAP_SEQUENCE, 33L, false);
         CoopCameraShakeHandler.shake(1.5F, CoopImpactHandler.HEAVEN_DAP_SEQUENCE.length * 33L);
         CoopChromaHandler.start();
         CoopRadialBlurHandler.start();
         CoopSpeedLinesRenderer.start();
         CoopScreenSquishHandler.trigger();
      }));
   }

   public record QTEButtonPressPayload(String button) implements CustomPacketPayload {
      public static final Identifier QTE_BUTTON_PRESS_ID = Identifier.fromNamespaceAndPath("cooptest", "qte_button_press");
      public static final Type<HeavenDapClientHandler.QTEButtonPressPayload> ID = new Type(QTE_BUTTON_PRESS_ID);
      public static final StreamCodec<RegistryFriendlyByteBuf, HeavenDapClientHandler.QTEButtonPressPayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.STRING_UTF8, HeavenDapClientHandler.QTEButtonPressPayload::button, HeavenDapClientHandler.QTEButtonPressPayload::new
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
