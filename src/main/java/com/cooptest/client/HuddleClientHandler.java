package com.cooptest.client;

import com.cooptest.HuddleHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class HuddleClientHandler {
   private static final int BAR_WIDTH = 120;
   private static final int BAR_HEIGHT = 8;
   private static final int BAR_ABOVE_HOTBAR = 84;
   private static boolean fWasHeld = false;
   private static boolean shiftWasHeld = false;
   private static boolean gWasHeld = false;
   private static boolean gHoldSent = false;
   private static int holdingCount = 0;
   private static int holdingOf = 0;
   private static boolean suppressSneak = false;
   private static boolean barActive = false;
   private static float barFill = 0.0F;
   private static int barPlayers = 0;
   private static boolean yawLocked = false;
   private static float lockedYaw = 0.0F;
   private static boolean serverYawPinned = false;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleEndPayload.ID, (payload, ctx) -> ctx.client().execute(HuddleClientHandler::clearLocal));
      ClientPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleBarPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         barFill = payload.fill();
         barPlayers = payload.players();
         barActive = payload.active();
         if (!payload.active()) {
            yawLocked = false;
            serverYawPinned = false;
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleYawPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         lockedYaw = payload.yaw();
         yawLocked = true;
         serverYawPinned = true;
         Minecraft c = Minecraft.getInstance();
         if (c.player != null) {
            c.player.setYRot(lockedYaw);
            c.player.setYHeadRot(lockedYaw);
            c.player.setYBodyRot(lockedYaw);
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleHoldInfoPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         holdingCount = payload.holding();
         holdingOf = payload.players();
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         HuddleHandler.HuddleShakePayload.ID,
         (payload, ctx) -> ctx.client().execute(() -> CoopCameraShakeHandler.shake(payload.amount(), payload.durationMs()))
      );
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (client.player == null) {
                  clearLocal();
               } else {
                  long win = client.getWindow().handle();
                  boolean inHuddle = CoopAnimationHandler.isInHuddleAnim(client.player.getUUID());
                  if (!inHuddle && !serverYawPinned) {
                     yawLocked = false;
                  } else {
                     if (!yawLocked) {
                        lockedYaw = client.player.getYRot();
                        yawLocked = true;
                     }

                     client.player.setYRot(lockedYaw);
                     client.player.setYHeadRot(lockedYaw);
                     client.player.setYBodyRot(lockedYaw);
                  }

                  boolean gHeld = com.mojang.blaze3d.platform.InputConstants.isKeyDown(71);
                  boolean wantHold = inHuddle && !barActive && gHeld;
                  if (wantHold != gHoldSent) {
                     ClientPlayNetworking.send(new HuddleHandler.HuddleHoldOpenPayload(wantHold));
                     gHoldSent = wantHold;
                  }

                  if (inHuddle && barActive && gHeld && !gWasHeld) {
                     ClientPlayNetworking.send(new HuddleHandler.HuddleTapPayload());
                  }

                  gWasHeld = gHeld;
                  boolean shiftHeld = com.mojang.blaze3d.platform.InputConstants.isKeyDown(340) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(344);
                  boolean gForCharge = com.mojang.blaze3d.platform.InputConstants.isKeyDown(71);
                  suppressSneak = shiftHeld && (inHuddle || gForCharge);
                  PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUUID(), PoseState.NONE);
                  boolean poseBlocked = pose == PoseState.GRAB_READY
                     || pose == PoseState.GRAB_HOLDING
                     || pose == PoseState.GRABBED
                     || pose == PoseState.PUSH_IDLE
                     || pose == PoseState.PUSH_ACTION;
                  if (!inHuddle && poseBlocked) {
                     if (shiftWasHeld) {
                        ClientPlayNetworking.send(new HuddleHandler.HuddleShiftPayload(false));
                        shiftWasHeld = false;
                     }
                  } else if (shiftHeld != shiftWasHeld) {
                     ClientPlayNetworking.send(new HuddleHandler.HuddleShiftPayload(shiftHeld));
                     shiftWasHeld = shiftHeld;
                  }

                  boolean fHeld = com.mojang.blaze3d.platform.InputConstants.isKeyDown(70);
                  if (fHeld != fWasHeld) {
                     ClientPlayNetworking.send(new HuddleHandler.HuddleFHoldPayload(fHeld));
                     fWasHeld = fHeld;
                  }
               }
            }
         );
      net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("cooptest", "huddleclienthandler_hud"), (ctx, tickDelta) -> {
         if (!barActive && holdingCount > 0) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && !client.gui.hud.isHidden()) {
               if (CoopAnimationHandler.isInHuddleAnim(client.player.getUUID())) {
                  int screenW = ctx.guiWidth();
                  int y = ctx.guiHeight() - 84;
                  String top = "§e§lHOLDING §7— waiting for §f" + holdingCount + "§7/§f" + holdingOf;
                  String sub = "§8release G to start";
                  int tw = client.font.width(top);
                  int sw2 = client.font.width(sub);
                  ctx.text(client.font, Component.literal(top), (screenW - tw) / 2, y, -1, true);
                  ctx.text(client.font, Component.literal(sub), (screenW - sw2) / 2, y + 11, -5592406, true);
               }
            }
         }
      });
      net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("cooptest", "huddleclienthandler_hud"), (ctx, tickDelta) -> {
         if (barActive) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && !client.gui.hud.isHidden()) {
               int screenW = ctx.guiWidth();
               int screenH = ctx.guiHeight();
               int x = (screenW - 120) / 2;
               int y = screenH - 84;
               ctx.fill(x - 2, y - 2, x + 120 + 2, y + 8 + 2, -872415232);
               ctx.fill(x, y, x + 120, y + 8, -14540254);
               int fillW = (int)(120.0F * Math.max(0.0F, Math.min(1.0F, barFill)));
               int colour = barFill >= 0.68F ? -12288 : (barFill >= 0.34F ? -16718337 : -16736064);
               if (fillW > 0) {
                  ctx.fill(x, y, x + fillW, y + 8, colour);
               }

               int t2 = x + 40;
               int t3 = x + 81;
               ctx.fill(t2, y, t2 + 1, y + 8, -1996488705);
               ctx.fill(t3, y, t3 + 1, y + 8, -1996488705);
               long t = System.currentTimeMillis();
               int jx = (int)(Math.sin(t / 45.0) * 2.0);
               int jy = (int)(Math.cos(t / 37.0) * 2.0);
               String g = "G";
               int gw = client.font.width(g);
               ctx.pose().pushMatrix();
               ctx.pose().translate((screenW - gw) / 2.0F + jx, y - 26.0F + jy);
               ctx.pose().scale(1.6F, 1.6F);
               ctx.text(client.font, Component.literal(g), 0, 0, -15360, true);
               ctx.pose().popMatrix();
               String label = "§7×" + barPlayers + "  §8SHIFT leave";
               int lw = client.font.width(label);
               ctx.text(client.font, Component.literal(label), (screenW - lw) / 2, y - 11, 16777215, true);
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> clearLocal());
      ClientPlayConnectionEvents.JOIN.register((Join)(h, s, c) -> clearLocal());
   }

   private static void clearLocal() {
      barActive = false;
      barFill = 0.0F;
      barPlayers = 0;
      yawLocked = false;
      serverYawPinned = false;
      fWasHeld = false;
      shiftWasHeld = false;
      gWasHeld = false;
      gHoldSent = false;
      holdingCount = 0;
      holdingOf = 0;
      suppressSneak = false;
   }

   public static boolean isBarActive() {
      return barActive;
   }

   public static boolean isSuppressingSneak() {
      return suppressSneak;
   }
}
