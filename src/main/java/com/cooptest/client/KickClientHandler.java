package com.cooptest.client;

import com.cooptest.CoopMovesConfig;
import com.cooptest.KickHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;

public class KickClientHandler {
   private static boolean wasHeld = false;
   private static boolean isCharging = false;
   private static long chargeStartMs = 0L;
   private static long cooldownEndMs = 0L;
   private static boolean hitFlashActive = false;
   private static boolean hitFlashDrop = false;
   private static long hitFlashStart = 0L;
   private static final long HIT_FLASH_MS = 200L;
   private static final Map<UUID, Float> otherCharges = new HashMap<>();
   private static final Map<UUID, Boolean> otherActive = new HashMap<>();
   private static final int BAR_WIDTH = 36;
   private static final int BAR_HEIGHT = 2;
   private static final int BAR_Y_OFFSET = 14;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(KickHandler.KickChargeSyncPayload.ID, (payload, context) -> context.client().execute(() -> {
         UUID pid = payload.playerId();
         Minecraft client = Minecraft.getInstance();
         boolean isLocal = client.player != null && client.player.getUUID().equals(pid);
         if (payload.isCharging()) {
            otherActive.put(pid, true);
            otherCharges.put(pid, payload.chargePercent());
         } else {
            otherActive.put(pid, false);
            otherCharges.put(pid, 0.0F);
            if (isLocal && isCharging) {
               stopLocalCharge();
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(KickHandler.KickCooldownPayload.ID, (payload, context) -> context.client().execute(() -> {
         stopLocalCharge();
         wasHeld = false;
         if (payload.cooldownMs() > 0L) {
            cooldownEndMs = System.currentTimeMillis() + payload.cooldownMs();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(KickHandler.KickResultPayload.ID, (payload, context) -> context.client().execute(() -> {
         if (payload.hit()) {
            hitFlashActive = true;
            hitFlashDrop = payload.isDropKick();
            hitFlashStart = System.currentTimeMillis();
         }
      }));
      HudRenderCallback.EVENT.register(KickClientHandler::renderHUD);
   }

   public static void handleKickTick(Minecraft client, boolean keyHeld, boolean sprinting) {
      boolean justPressed = keyHeld && !wasHeld;
      boolean justReleased = !keyHeld && wasHeld;
      if (justPressed && !isOnCooldown()) {
         if (sprinting) {
            isCharging = true;
            chargeStartMs = System.currentTimeMillis();
            ClientPlayNetworking.send(new KickHandler.KickStartPayload(true));
         } else {
            ClientPlayNetworking.send(new KickHandler.KickStartPayload(false));
         }
      }

      if (isCharging && !sprinting) {
         stopLocalCharge();
      }

      if (justReleased && isCharging) {
         ClientPlayNetworking.send(new KickHandler.KickReleasePayload());
         stopLocalCharge();
      }

      if (justPressed && isOnCooldown() && client.player != null) {
         long rem = cooldownEndMs - System.currentTimeMillis();
         client.player.displayClientMessage(Component.literal("§cKick cooldown! " + String.format("%.1f", rem / 1000.0) + "s"), true);
      }

      wasHeld = keyHeld;
   }

   public static void cancelIfCharging() {
      if (isCharging) {
         stopLocalCharge();
         wasHeld = false;
      }
   }

   private static void renderHUD(GuiGraphics context, DeltaTracker tickCounter) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && !client.options.hideGui) {
         int sw = context.guiWidth();
         int sh = context.guiHeight();
         int centreX = sw / 2;
         int barY = sh / 2 + 14;
         int barX = centreX - 18;
         if (hitFlashActive) {
            long e = System.currentTimeMillis() - hitFlashStart;
            if (e > 200L) {
               hitFlashActive = false;
            } else {
               int a = (int)((1.0F - (float)e / 200.0F) * 90.0F) << 24;
               int c = hitFlashDrop ? a | 16746496 : a | 16777164;
               context.fill(0, 0, sw, 4, c);
               context.fill(0, sh - 4, sw, sh, c);
            }
         }

         if (isCharging && CoopMovesConfig.get().enableKick) {
            long elapsed = System.currentTimeMillis() - chargeStartMs;
            float pct = Math.min(1.0F, (float)elapsed / 3000.0F);
            boolean full = pct >= 0.99F;
            context.fill(barX, barY, barX + 36, barY + 2, 1426063360);
            int fillColor;
            if (full) {
               long t = System.currentTimeMillis();
               int a = (int)((Math.sin(t / 100.0) * 0.25 + 0.75) * 200.0);
               fillColor = a << 24 | 16777215;
            } else {
               fillColor = -855668736;
            }

            context.fill(barX, barY, barX + (int)(36.0F * pct), barY + 2, fillColor);
            if (full) {
               String lbl = "DROP KICK";
               int lx = centreX - client.font.width(lbl) / 2;
               context.drawString(client.font, Component.literal("§f" + lbl), lx, barY - 9, -855638017, false);
            }
         } else if (isOnCooldown()) {
            float pct = (float)(cooldownEndMs - System.currentTimeMillis()) / 2000.0F;
            context.fill(barX, barY, barX + 36, barY + 2, 855638016);
            context.fill(barX, barY, barX + (int)(36.0F * pct), barY + 2, -1996536832);
         }

         for (Entry<UUID, Boolean> entry : otherActive.entrySet()) {
            if (entry.getValue()) {
               UUID pid = entry.getKey();
               if (!client.player.getUUID().equals(pid) && client.level != null) {
                  boolean inRange = false;

                  for (AbstractClientPlayer p : client.level.players()) {
                     if (p.getUUID().equals(pid)) {
                        inRange = client.player.distanceTo(p) <= 20.0;
                        break;
                     }
                  }

                  if (!inRange) {
                  }
               }
            }
         }
      }
   }

   private static void stopLocalCharge() {
      isCharging = false;
      chargeStartMs = 0L;
   }

   public static boolean isOnCooldown() {
      return System.currentTimeMillis() < cooldownEndMs;
   }

   public static boolean isLocalPlayerKickCharging() {
      return isCharging;
   }

   public static void cleanup() {
      stopLocalCharge();
      wasHeld = false;
      cooldownEndMs = 0L;
      hitFlashActive = false;
      otherCharges.clear();
      otherActive.clear();
   }
}
