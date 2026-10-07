package com.cooptest.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class ThrowPowerHUD {
   private static final int BAR_WIDTH = 100;
   private static final int BAR_HEIGHT = 8;
   private static final int BAR_Y_OFFSET = 60;
   private static final int BG_COLOR = -1442840576;
   private static final int BORDER_COLOR = -1;
   private static final int LOW_COLOR = -16711936;
   private static final int MID_COLOR = -256;
   private static final int HIGH_COLOR = -65536;
   private static final int READY_COLOR = -65281;
   private static float displayedCharge = 0.0F;
   private static final float LERP_SPEED = 0.15F;

   public static void register() {
      HudRenderCallback.EVENT.register(ThrowPowerHUD::render);
   }

   private static void render(GuiGraphics context, DeltaTracker tickCounter) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUUID(), PoseState.NONE);
         if (pose != PoseState.GRAB_HOLDING) {
            displayedCharge = 0.0F;
         } else {
            float targetCharge = GrabClientState.getChargeProgress(client.player.getUUID());
            if (!(targetCharge <= 0.0F) || !(displayedCharge <= 0.01F)) {
               displayedCharge = displayedCharge + (targetCharge - displayedCharge) * 0.15F;
               int screenWidth = client.getWindow().getGuiScaledWidth();
               int screenHeight = client.getWindow().getGuiScaledHeight();
               int barX = (screenWidth - 100) / 2;
               int barY = screenHeight / 2 + 60;
               context.fill(barX - 2, barY - 2, barX + 100 + 2, barY + 8 + 2, -1442840576);
               int bx = barX - 2;
               int by = barY - 2;
               int bw = 104;
               int bh = 12;
               context.fill(bx, by, bx + bw, by + 1, -1);
               context.fill(bx, by + bh - 1, bx + bw, by + bh, -1);
               context.fill(bx, by + 1, bx + 1, by + bh - 1, -1);
               context.fill(bx + bw - 1, by + 1, bx + bw, by + bh - 1, -1);
               int fillWidth = (int)(100.0F * displayedCharge);
               int fillColor = getChargeColor(displayedCharge);
               if (fillWidth > 0) {
                  context.fill(barX, barY, barX + fillWidth, barY + 8, fillColor);
               }

               if (displayedCharge >= 0.99F) {
                  String text = "RELEASE TO THROW!";
                  int textWidth = client.font.width(text);
                  int textX = (screenWidth - textWidth) / 2;
                  int textY = barY - 12;
                  float pulse = (float)(Math.sin(System.currentTimeMillis() / 100.0) * 0.5 + 0.5);
                  int alpha = (int)(155.0F + pulse * 100.0F);
                  int textColor = alpha << 24 | 16777215;
                  context.drawString(client.font, text, textX, textY, textColor, true);
               }
            }
         }
      }
   }

   private static int getChargeColor(float charge) {
      if (charge >= 0.99F) {
         return -65281;
      } else if (charge >= 0.7F) {
         return -65536;
      } else {
         return charge >= 0.4F ? -256 : -16711936;
      }
   }
}
