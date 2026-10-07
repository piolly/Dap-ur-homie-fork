package com.cooptest.client;

import com.cooptest.SlapHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class SlapClientHandler {
   private static long fishFlashEndTime = 0L;
   private static final long FISH_FLASH_MS = 3000L;
   private static long slapCooldownEnd = 0L;

   public static boolean isOnSlapCooldown() {
      return System.currentTimeMillis() < slapCooldownEnd;
   }

   public static void triggerSlapCooldown() {
      slapCooldownEnd = System.currentTimeMillis() + 500L;
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(SlapHandler.CameraFlickPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.playerId())) {
               float newPitch = Math.min(90.0F, client.player.getXRot() + payload.pitchDelta());
               client.player.setXRot(newPitch);
               triggerSlapCooldown();
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(SlapHandler.CameraYawFlickPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.playerId())) {
               client.player.setYRot(client.player.getYRot() + payload.yawDelta());
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(SlapHandler.ScreenClosePayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.playerId())) {
               if (client.screen != null) {
                  client.setScreen(null);
               }
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(SlapHandler.FishSlapPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.playerId())) {
               fishFlashEndTime = System.currentTimeMillis() + 3000L;
            }
         }
      }));
      HudRenderCallback.EVENT.register(SlapClientHandler::renderHUD);
   }

   private static void renderHUD(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
      if (fishFlashEndTime > 0L) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            long now = System.currentTimeMillis();
            long remaining = fishFlashEndTime - now;
            if (remaining <= 0L) {
               fishFlashEndTime = 0L;
            } else {
               int screenW = ctx.guiWidth();
               int screenH = ctx.guiHeight();
               int alpha = (int)((float)remaining / 3000.0F * 230.0F);
               alpha = Math.min(230, Math.max(0, alpha));
               ctx.fill(0, 0, screenW, screenH, alpha << 24 | 17578);
               int textAlpha = Math.min(255, alpha + 25);
               ctx.drawCenteredString(client.font, Component.literal("§b§l\ud83d\udc1f FISH SLAPPED"), screenW / 2, screenH / 2 - 10, textAlpha << 24 | 5636095);
            }
         }
      }
   }
}
