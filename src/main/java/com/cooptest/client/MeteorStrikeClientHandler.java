package com.cooptest.client;

import com.cooptest.MeteorStrikeHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public class MeteorStrikeClientHandler {
   private static boolean hasAbility = false;
   private static long abilityLeftMs = 0L;
   private static long countdownMs = -1L;
   private static boolean wasGPressed = false;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(MeteorStrikeHandler.MeteorGrantPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         hasAbility = true;
         abilityLeftMs = payload.expiryMs() - System.currentTimeMillis();
         countdownMs = -1L;
      }));
      ClientPlayNetworking.registerGlobalReceiver(MeteorStrikeHandler.MeteorStatusPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         abilityLeftMs = payload.remainingAbilityMs();
         countdownMs = payload.countdownMs();
         hasAbility = abilityLeftMs > 0L;
      }));
      ClientPlayNetworking.registerGlobalReceiver(MeteorStrikeHandler.MeteorExpiredPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         hasAbility = false;
         abilityLeftMs = 0L;
         countdownMs = -1L;
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (hasAbility && client.player != null) {
            long win = client.getWindow().handle();
            boolean g = com.mojang.blaze3d.platform.InputConstants.isKeyDown(71);
            if (g && !wasGPressed && countdownMs < 0L) {
               ClientPlayNetworking.send(new MeteorStrikeHandler.MeteorFirePayload());
            }

            wasGPressed = g;
         }
      });
      net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("cooptest", "meteorstrikeclienthandler_hud"), MeteorStrikeClientHandler::renderHUD);
   }

   private static void renderHUD(GuiGraphicsExtractor ctx, DeltaTracker ticker) {
      if (hasAbility) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            int sw = client.getWindow().getGuiScaledWidth();
            int sh = client.getWindow().getGuiScaledHeight();
            Matrix3x2fStack mat = ctx.pose();
            mat.pushMatrix();
            if (countdownMs > 0L) {
               String cdText = "☄ METEOR IN " + String.format("%.1f", countdownMs / 1000.0) + "s";
               int tw = client.font.width(cdText);
               int alpha = (int)(180.0 + 75.0 * Math.abs(Math.sin(System.currentTimeMillis() / 300.0)));
               int col = alpha << 24 | 16720384;
               ctx.text(client.font, cdText, (sw - tw) / 2, sh / 2 - 60, col, true);
            } else {
               int bw = 100;
               int bh = 20;
               int bx = sw / 2 - bw / 2;
               int by = sh - 75;
               ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, -16777216);
               long pulse = System.currentTimeMillis() / 500L;
               int bcol = pulse % 2L == 0L ? -3399168 : -48128;
               ctx.fill(bx, by, bx + bw, by + bh, bcol);
               String label = "☄ G — METEOR";
               int lw = client.font.width(label);
               ctx.text(client.font, label, bx + (bw - lw) / 2, by + 6, -1, true);
               String timer = abilityLeftMs / 1000L + "s";
               int tiw = client.font.width(timer);
               ctx.text(client.font, timer, bx + (bw - tiw) / 2, by + bh + 3, -5592406, false);
            }

            mat.popMatrix();
         }
      }
   }

   public static KeyMapping getBurstKey() {
      return null;
   }
}
