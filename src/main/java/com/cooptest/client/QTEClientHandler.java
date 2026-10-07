package com.cooptest.client;

import com.cooptest.QTEButtonPressPayload;
import com.cooptest.QTEClearPayload;
import com.cooptest.QTEWindowPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public class QTEClientHandler {
   private static boolean active = false;
   private static String expectedButton = null;
   private static int stage = 0;
   private static int maxStages = 1;
   private static long windowStart = 0L;
   private static long windowEnd = 0L;
   private static long receiveTime = 0L;
   private static long flashEndTime = 0L;
   private static long failFlashEndTime = 0L;
   private static boolean pressedThisWindow = false;

   public static void registerReceivers() {
      ClientPlayNetworking.registerGlobalReceiver(QTEWindowPayload.ID, (payload, context) -> context.client().execute(() -> {
         long now = System.currentTimeMillis();
         active = true;
         expectedButton = payload.button();
         stage = payload.stage();
         windowStart = now + payload.windowStart();
         windowEnd = now + payload.windowEnd();
         receiveTime = now;
         pressedThisWindow = false;
      }));
      ClientPlayNetworking.registerGlobalReceiver(QTEClearPayload.ID, (payload, context) -> context.client().execute(() -> {
         active = false;
         expectedButton = null;
         pressedThisWindow = false;
      }));
   }

   public static boolean handleKeyPress(String button) {
      if (!active) {
         return false;
      }

      if (button.equals(expectedButton)) {
         long now = System.currentTimeMillis();
         if (now >= windowStart && now <= windowEnd) {
            flashEndTime = now + 200L;
            pressedThisWindow = true;
         } else if (now < windowStart) {
            failFlashEndTime = now + 150L;
         } else {
            failFlashEndTime = now + 150L;
         }

         ClientPlayNetworking.send(new QTEButtonPressPayload(button));
      } else {
         failFlashEndTime = System.currentTimeMillis() + 150L;
      }

      return true;
   }

   public static void renderHUD(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
      if (active) {
         long now = System.currentTimeMillis();
         Matrix3x2fStack matrices = context.pose();
         matrices.pushMatrix();
         int barWidth = 60;
         int barHeight = 4;
         int barX = (screenWidth - barWidth) / 2;
         int barY = screenHeight - 45;
         context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, -16777216);
         context.fill(barX, barY, barX + barWidth, barY + barHeight, -14540254);
         boolean inWindow = now >= windowStart && now <= windowEnd;
         boolean beforeWindow = now < windowStart;
         long totalWindowDuration = windowEnd - windowStart;
         boolean successFlash = now < flashEndTime;
         boolean failFlash = now < failFlashEndTime;
         int barColor;
         int filledWidth;
         if (successFlash) {
            barColor = -1;
            filledWidth = barWidth;
         } else if (failFlash) {
            barColor = -52429;
            filledWidth = barWidth;
         } else if (beforeWindow) {
            long countdownDuration = windowStart - receiveTime;
            long elapsed = now - receiveTime;
            float progress = countdownDuration > 0L ? Math.min(1.0F, (float)elapsed / (float)countdownDuration) : 1.0F;
            filledWidth = (int)(barWidth * progress);
            barColor = -48060;
         } else if (inWindow) {
            long elapsed = now - windowStart;
            float remaining = 1.0F - (float)elapsed / (float)totalWindowDuration;
            remaining = Math.max(0.0F, remaining);
            filledWidth = (int)(barWidth * remaining);
            if (pressedThisWindow) {
               barColor = -16711936;
               filledWidth = barWidth;
            } else {
               barColor = -16711936;
            }
         } else {
            filledWidth = 0;
            barColor = -10066330;
         }

         if (filledWidth > 0) {
            context.fill(barX, barY, barX + filledWidth, barY + barHeight, barColor);
         }

         if (expectedButton != null && !pressedThisWindow) {
            Minecraft client = Minecraft.getInstance();
            String keyText;
            if (stage < 0) {
               int remaining = -stage;
               keyText = "[" + resolveKeyName(expectedButton) + "] ×" + remaining;
            } else {
               keyText = "[" + resolveKeyName(expectedButton) + "]";
            }

            int textWidth = client.font.width(keyText);
            int textX = (screenWidth - textWidth) / 2;
            int textY = barY - 12;
            int alpha = 255;
            if (inWindow) {
               float pulse = (float)(Math.sin(now / 100.0) * 0.3 + 0.7);
               alpha = (int)(pulse * 255.0F);
            }

            context.text(client.font, keyText, textX, textY, alpha << 24 | 16777215, true);
         }

         if (stage > 0 && maxStages > 1) {
            int dotY = barY + barHeight + 3;
            int totalDotsWidth = maxStages * 4 + (maxStages - 1) * 3;
            int dotStartX = (screenWidth - totalDotsWidth) / 2;

            for (int i = 1; i <= maxStages; i++) {
               int dotX = dotStartX + (i - 1) * 7;
               int dotColor;
               if (i < stage) {
                  dotColor = -16711936;
               } else if (i == stage) {
                  dotColor = inWindow ? -256 : -1;
               } else {
                  dotColor = -11184811;
               }

               context.fill(dotX, dotY, dotX + 4, dotY + 4, dotColor);
            }
         }

         matrices.popMatrix();
      }
   }

   public static boolean isActive() {
      return active;
   }

   public static String getExpectedButton() {
      return expectedButton;
   }

   public static long getWindowStart() {
      return windowStart;
   }

   public static long getWindowEnd() {
      return windowEnd;
   }

   public static int getStage() {
      return stage;
   }

   public static void setMaxStages(int max) {
      maxStages = max;
   }

   public static String resolveKeyName(String serverButton) {
      Minecraft client = Minecraft.getInstance();
      if (client == null) {
         return serverButton;
      }

      KeyMapping match = switch (serverButton) {
         case "G" -> ChargedDapClientHandler.getChargeKey();
         case "H" -> HighFiveClientHandler.getHighFiveKey();
         case "J" -> ChargedDapClientHandler.getComboKey();
         case "F" -> MeteorStrikeClientHandler.getBurstKey();
         default -> null;
      };
      if (match == null) {
         return serverButton;
      }

      String boundName = match.getTranslatedKeyMessage().getString();
      return boundName.length() > 6 ? boundName : boundName.toUpperCase();
   }
}
