package com.cooptest.highfive.client;

import com.cooptest.client.CoopCameraShakeHandler;
import com.cooptest.highfive.HighFiveShakeHandler;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class HighFiveShakeClientHandler {
   private static final long FLASH_DURATION_MS = 400L;
   private static final long KEY_FLASH_DURATION_MS = 350L;
   private static boolean inActiveSession = false;
   private static boolean isArmed = false;
   private static int currentStreak = 0;
   private static boolean lastResultSuccess = false;
   private static boolean lastResultPerfect = false;
   private static long lastResultTime = 0L;
   private static final Map<HighFiveShakeHandler.Dir, String> keyFlashName = new HashMap<>();
   private static final Map<HighFiveShakeHandler.Dir, Long> keyFlashTime = new HashMap<>();
   private static long animStartMs = 0L;
   private static long animDurationMs = 0L;
   private static long lastAmbientShakeTime = 0L;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(
         HighFiveShakeHandler.ShakeArmedStatePayload.ID, (payload, context) -> context.client().execute(() -> isArmed = payload.armed())
      );
      ClientPlayNetworking.registerGlobalReceiver(HighFiveShakeHandler.ShakeSessionPayload.ID, (payload, context) -> context.client().execute(() -> {
         inActiveSession = payload.active();
         if (!inActiveSession) {
            currentStreak = 0;
            lastResultTime = 0L;
            isArmed = false;
            keyFlashName.clear();
            keyFlashTime.clear();
            animStartMs = 0L;
            animDurationMs = 0L;
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(HighFiveShakeHandler.ShakeResultPayload.ID, (payload, context) -> context.client().execute(() -> {
         currentStreak = payload.streak();
         lastResultSuccess = payload.success();
         lastResultPerfect = payload.perfect();
         lastResultTime = System.currentTimeMillis();
         if (!payload.success()) {
            CoopCameraShakeHandler.shake(0.5F, 150L);
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(HighFiveShakeHandler.ShakeKeyPressPayload.ID, (payload, context) -> context.client().execute(() -> {
         HighFiveShakeHandler.Dir dir = HighFiveShakeHandler.Dir.values()[payload.dirOrdinal()];
         keyFlashName.put(dir, payload.playerName());
         keyFlashTime.put(dir, System.currentTimeMillis());
      }));
      ClientPlayNetworking.registerGlobalReceiver(HighFiveShakeHandler.ShakeAnimStartPayload.ID, (payload, context) -> context.client().execute(() -> {
         long serverOffset = System.currentTimeMillis() - payload.serverStartMs();
         animStartMs = System.currentTimeMillis() - Math.max(0L, serverOffset);
         animDurationMs = payload.durationMs();
         keyFlashName.clear();
         keyFlashTime.clear();
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         HighFiveShakeHandler.ShakeImpactPayload.ID,
         (payload, context) -> context.client().execute(() -> CoopCameraShakeHandler.shake(payload.amount(), payload.durationMs()))
      );
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         inActiveSession = false;
         isArmed = false;
         currentStreak = 0;
         keyFlashName.clear();
         keyFlashTime.clear();
         animStartMs = 0L;
         animDurationMs = 0L;
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (inActiveSession) {
            long now = System.currentTimeMillis();
            if (now - lastAmbientShakeTime >= 100L) {
               CoopCameraShakeHandler.shake(0.08F, 120L);
               lastAmbientShakeTime = now;
            }
         }
      });
      net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("cooptest", "highfiveshakeclienthandler_hud"), HighFiveShakeClientHandler::renderHud);
   }

   private static void renderHud(GuiGraphicsExtractor ctx, DeltaTracker tc) {
      Minecraft client = Minecraft.getInstance();
      int sw = client.getWindow().getGuiScaledWidth();
      long now = System.currentTimeMillis();
      if (inActiveSession) {
         renderSessionHud(ctx, client, sw, now);
      } else if (isArmed) {
         String txt = "\ud83e\udd1d ARMED — find your partner!";
         ctx.text(client.font, Component.literal(txt), (sw - client.font.width(txt)) / 2, 20, 5635925, true);
      }
   }

   private static void renderSessionHud(GuiGraphicsExtractor ctx, Minecraft client, int sw, long now) {
      int streakColor = 16777215;
      long sinceResult = now - lastResultTime;
      if (lastResultTime != 0L && sinceResult < 400L) {
         if (lastResultSuccess) {
            streakColor = lastResultPerfect ? 16766720 : 5635925;
         } else {
            streakColor = 16733525;
         }
      }

      String streakTxt = "\ud83e\udd1d " + currentStreak;
      ctx.text(client.font, Component.literal(streakTxt), (sw - client.font.width(streakTxt)) / 2, 20, streakColor, true);
      int cx = sw / 2;
      int top = 36;
      int keySize = 14;
      int gap = 2;
      drawKey(ctx, client, HighFiveShakeHandler.Dir.W, cx - keySize / 2, top, keySize, now);
      drawKey(ctx, client, HighFiveShakeHandler.Dir.A, cx - keySize - keySize / 2 - gap, top + keySize + gap, keySize, now);
      drawKey(ctx, client, HighFiveShakeHandler.Dir.S, cx - keySize / 2, top + keySize + gap, keySize, now);
      drawKey(ctx, client, HighFiveShakeHandler.Dir.D, cx + keySize / 2 + gap, top + keySize + gap, keySize, now);
      if (animDurationMs > 0L) {
         long elapsed = now - animStartMs;
         float progress = Math.min(1.0F, (float)elapsed / (float)animDurationMs);
         long perfectWindowMs = 300L;
         boolean inPerfectZone = animDurationMs - elapsed <= perfectWindowMs && elapsed < animDurationMs;
         int barW = 80;
         int barH = 4;
         int barX = (sw - barW) / 2;
         int barY = top + keySize * 2 + gap * 2 + 6;
         int barColor = inPerfectZone ? -1426073856 : -1438366652;
         int fillColor = inPerfectZone ? -10496 : -11154177;
         ctx.fill(barX, barY, barX + barW, barY + barH, barColor);
         ctx.fill(barX, barY, barX + (int)(barW * progress), barY + barH, fillColor);
         if (inPerfectZone) {
            String pressHint = "PRESS!";
            int hintColor = (int)(System.currentTimeMillis() / 100L) % 2 == 0 ? -10496 : -1;
            ctx.text(client.font, Component.literal(pressHint), (sw - client.font.width(pressHint)) / 2, barY + barH + 3, hintColor, true);
         }
      }
   }

   private static void drawKey(GuiGraphicsExtractor ctx, Minecraft client, HighFiveShakeHandler.Dir dir, int x, int y, int size, long now) {
      String label = dir.name();
      boolean flashing = false;
      String presserName = null;
      Long ft = keyFlashTime.get(dir);
      if (ft != null && now - ft < 350L) {
         flashing = true;
         presserName = keyFlashName.get(dir);
      }

      int bgColor = flashing ? -1437204651 : 1428300322;
      int txtColor = flashing ? 0 : 13421772;
      ctx.fill(x, y, x + size, y + size, bgColor);
      ctx.text(client.font, Component.literal(label), x + size / 2 - client.font.width(label) / 2, y + size / 2 - 4, txtColor, false);
      if (flashing && presserName != null) {
         int nw = client.font.width(presserName);
         ctx.text(client.font, Component.literal(presserName), x + size / 2 - nw / 2, y - 11, 16777215, true);
      }
   }

   public static boolean isLocalPlayerInHandshake() {
      return inActiveSession;
   }

   public static boolean isLocalPlayerArmed() {
      return isArmed;
   }
}
