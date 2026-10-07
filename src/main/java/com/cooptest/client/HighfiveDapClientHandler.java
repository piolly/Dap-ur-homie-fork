package com.cooptest.client;

import com.cooptest.HighfiveDapHandler;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class HighfiveDapClientHandler {
   private static boolean active = false;
   private static boolean inIdle = false;
   private static boolean ending = false;
   private static boolean cancelSent = false;
   private static boolean gWasHeld = false;
   private static boolean hWasHeld = false;
   private static boolean spaceWasHeld = false;
   private static boolean shiftWasHeld = false;
   private static boolean rWasHeld = false;
   private static UUID pairHfId = null;
   private static UUID pairDapId = null;
   private static UUID myId = null;
   private static int myScore = 0;
   private static int partnerScore = 0;
   private static final Set<UUID> lockedPlayers = new HashSet<>();

   public static boolean isActive() {
      return active;
   }

   public static void requestCancel() {
      if (active && !ending && !cancelSent) {
         cancelSent = true;
         ClientPlayNetworking.send(new HighfiveDapHandler.HfDapCancelPayload());
      }
   }

   public static boolean isAnimationLocked(UUID id) {
      return lockedPlayers.contains(id);
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(HighfiveDapHandler.HfDapStartPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         lockedPlayers.add(payload.hfId());
         lockedPlayers.add(payload.dapId());
         Minecraft client = ctx.client();
         if (client.player != null) {
            UUID local = client.player.getUUID();
            UUID animatee = payload.role() == 0 ? payload.hfId() : payload.dapId();
            if (animatee.equals(local)) {
               active = true;
               inIdle = false;
               ending = false;
               cancelSent = false;
               gWasHeld = false;
               hWasHeld = false;
               spaceWasHeld = false;
               myId = local;
               pairHfId = payload.hfId();
               pairDapId = payload.dapId();
               myScore = 0;
               partnerScore = 0;
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(HighfiveDapHandler.HfDapIdlePayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            UUID local = client.player.getUUID();
            if (local.equals(payload.hfId()) || local.equals(payload.dapId())) {
               inIdle = true;
               cancelSent = false;
               spaceWasHeld = false;
               myScore = 0;
               partnerScore = 0;
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(HighfiveDapHandler.HfDapEndPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            UUID hfId = payload.hfId();
            UUID dapId = payload.dapId();
            UUID local = client.player.getUUID();
            boolean isParticipant = local.equals(hfId) || local.equals(dapId);
            inIdle = false;
            ending = true;
            new Thread(() -> {
               try {
                  Thread.sleep(840L);
               } catch (InterruptedException var5x) {
               }

               client.execute(() -> {
                  lockedPlayers.remove(hfId);
                  lockedPlayers.remove(dapId);
                  if (isParticipant) {
                     FirstPersonAnimationTest.stop();
                     active = false;
                     inIdle = false;
                     ending = false;
                     cancelSent = false;
                     gWasHeld = false;
                     hWasHeld = false;
                     spaceWasHeld = false;
                     myId = null;
                     pairHfId = null;
                     pairDapId = null;
                     myScore = 0;
                     partnerScore = 0;
                  }
               });
            }).start();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         HighfiveDapHandler.HfDapShakePayload.ID,
         (payload, ctx) -> ctx.client().execute(() -> CoopCameraShakeHandler.shake(payload.amount(), payload.durationMs()))
      );
      ClientPlayNetworking.registerGlobalReceiver(HighfiveDapHandler.HfDapScorePayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            UUID local = client.player.getUUID();
            if (local.equals(payload.hfId())) {
               myScore = payload.hfScore();
               partnerScore = payload.dapScore();
            } else if (local.equals(payload.dapId())) {
               myScore = payload.dapScore();
               partnerScore = payload.hfScore();
            }
         }
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && active) {
            long win = client.getWindow().handle();
            boolean g = GLFW.glfwGetKey(win, 71) == 1;
            boolean h = GLFW.glfwGetKey(win, 72) == 1;
            boolean space = GLFW.glfwGetKey(win, 32) == 1;
            boolean shift = GLFW.glfwGetKey(win, 340) == 1 || GLFW.glfwGetKey(win, 344) == 1;
            boolean r = GLFW.glfwGetKey(win, 82) == 1;
            if (g && !gWasHeld || h && !hWasHeld || shift && !shiftWasHeld || r && !rWasHeld) {
               requestCancel();
            }

            shiftWasHeld = shift;
            rWasHeld = r;
            if (inIdle && !cancelSent && space && !spaceWasHeld) {
               ClientPlayNetworking.send(new HighfiveDapHandler.HfDapSpacePayload());
            }

            if (inIdle) {
               String hud = buildScoreHUD();
               client.player.displayClientMessage(Component.literal(hud), true);
            }

            gWasHeld = g;
            hWasHeld = h;
            spaceWasHeld = space;
         } else {
            gWasHeld = false;
            hWasHeld = false;
            spaceWasHeld = false;
         }
      });
      HudRenderCallback.EVENT.register(HighfiveDapClientHandler::renderHud);
   }

   private static String buildScoreHUD() {
      int win = 10;
      String myBar = buildBar(myScore, win, "§e");
      String partnerBar = buildBar(partnerScore, win, "§b");
      return "§7[" + myBar + "§7] §6⚡SPAM SPACE⚡§7 [" + partnerBar + "§7]";
   }

   private static String buildBar(int score, int max, String colour) {
      int filled = Math.min(score, max);
      return colour + "▌".repeat(filled) + "§8▌".repeat(max - filled) + " §f" + score + "/" + max;
   }

   private static void renderHud(GuiGraphics ctx, DeltaTracker tickCounter) {
      if (active && inIdle) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && !client.options.hideGui) {
            int screenW = ctx.guiWidth();
            int win = 10;
            int barW = 80;
            int panelW = barW * 2 + 40;
            int panelH = 24;
            int panelX = (screenW - panelW) / 2;
            int panelY = 20;
            ctx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, -2013265920);
            ctx.drawString(client.font, Component.literal("§eYou  " + myScore + "/" + win), panelX, panelY + 2, 16777215, true);
            ctx.drawString(client.font, Component.literal("§bPart " + partnerScore + "/" + win), panelX + barW + 8, panelY + 2, 16777215, true);
            int barH = 6;
            int barY = panelY + 14;
            int myFill = (int)((float)myScore / win * barW);
            int ptFill = (int)((float)partnerScore / win * barW);
            ctx.fill(panelX, barY, panelX + barW, barY + barH, -13421773);
            ctx.fill(panelX, barY, panelX + myFill, barY + barH, -8960);
            ctx.fill(panelX + barW + 8, barY, panelX + barW * 2 + 8, barY + barH, -13421773);
            ctx.fill(panelX + barW + 8, barY, panelX + barW + 8 + ptFill, barY + barH, -16724737);
         }
      }
   }
}
