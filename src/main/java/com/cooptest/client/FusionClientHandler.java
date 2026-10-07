package com.cooptest.client;

import com.cooptest.DapFusionHandler;
import com.cooptest.QTEButtonPressPayload;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

public class FusionClientHandler {
   private static int currentPhase = -1;
   private static String phaseLabel = "";
   private static boolean blackScreenActive = false;
   private static long blackScreenStartTime = 0L;
   private static boolean gWindowActive = false;
   private static long gWindowStart = 0L;
   private static long gWindowEnd = 0L;
   private static boolean gPressed = false;
   private static boolean qteActive = false;
   private static long greenZoneStart = 0L;
   private static long greenZoneEnd = 0L;
   private static String expectedButton = null;
   private static int currentStage = 0;
   private static int maxStages = 3;
   private static long windowStart = 0L;
   private static long windowEnd = 0L;
   private static boolean pressedThisWindow = false;
   private static int qteType = 0;
   private static long receiveTime = 0L;
   private static long bouncePeriodMs = 1800L;
   private static float greenCenterFrac = 0.5F;
   private static boolean waitingForGreenUpdate = false;
   private static long flashEndTime = 0L;
   private static long failFlashEndTime = 0L;
   public static boolean isFused = false;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(
         DapFusionHandler.FusionPhasePayload.ID, (payload, ctx) -> ctx.client().execute(() -> onPhase(payload, ctx.client()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         DapFusionHandler.FusionQTEPayload.ID, (payload, ctx) -> ctx.client().execute(() -> onQTE(payload, ctx.client()))
      );
      ClientPlayNetworking.registerGlobalReceiver(DapFusionHandler.FusionBlackScreenPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         blackScreenActive = payload.active();
         if (payload.active()) {
            blackScreenStartTime = System.currentTimeMillis();
         }

         if (!payload.active() && currentPhase == 4) {
            resetState();
         }
      }));
      HudRenderCallback.EVENT.register(FusionClientHandler::renderHUD);
   }

   private static void onPhase(DapFusionHandler.FusionPhasePayload p, Minecraft client) {
      if (client.player != null) {
         UUID myId = client.player.getUUID();
         if (myId.equals(p.p1()) || myId.equals(p.p2())) {
            currentPhase = p.phase();
            switch (p.phase()) {
               case 0:
                  long now = System.currentTimeMillis();
                  gWindowActive = true;
                  gWindowStart = now + 830L;
                  gWindowEnd = now + 2200L;
                  gPressed = false;
                  phaseLabel = "§6FUSION AVAILABLE";
                  maxStages = 3;
                  qteActive = false;
                  break;
               case 1:
                  phaseLabel = "§eWALK FORWARD";
                  gWindowActive = false;
                  maxStages = 3;
                  break;
               case 2:
                  phaseLabel = "§c§lFUSION QTE";
                  maxStages = 10;
                  break;
               case 3:
                  gWindowActive = false;
                  qteActive = false;
                  phaseLabel = "";
                  flashEndTime = System.currentTimeMillis() + 800L;
                  break;
               case 4:
                  gWindowActive = false;
                  qteActive = false;
                  phaseLabel = "";
                  break;
               case 99:
                  gWindowActive = false;
                  qteActive = false;
                  phaseLabel = "§c✗ FAILED";
                  failFlashEndTime = System.currentTimeMillis() + 500L;
                  scheduleReset(1200L);
            }
         }
      }
   }

   private static void onQTE(DapFusionHandler.FusionQTEPayload p, Minecraft client) {
      if (client.player != null) {
         if (p.playerId().equals(client.player.getUUID())) {
            if (!p.open()) {
               qteActive = false;
               expectedButton = null;
               waitingForGreenUpdate = false;
               if (currentPhase == 0 && !gWindowActive) {
                  currentPhase = -1;
               }
            } else {
               long now = System.currentTimeMillis();
               qteActive = true;
               expectedButton = p.button();
               currentStage = p.stage();
               qteType = p.type();
               receiveTime = now;
               pressedThisWindow = false;
               if (currentPhase < 0) {
                  currentPhase = 0;
               }

               if (p.type() == 1) {
                  greenZoneStart = now + p.windowStartMs();
                  greenZoneEnd = now + p.windowEndMs();
                  windowStart = now;
                  windowEnd = now + 1800L;
               } else if (p.type() == 2) {
                  long wEnd = p.windowEndMs();
                  if (wEnd < 0L) {
                     greenZoneStart = p.windowStartMs();
                     bouncePeriodMs = Math.abs(wEnd);
                     greenCenterFrac = Math.max(0.05F, Math.min(0.95F, p.stage() / 100.0F));
                     waitingForGreenUpdate = false;
                     expectedButton = p.button();
                  } else {
                     greenZoneStart = p.windowStartMs();
                     greenZoneEnd = 0L;
                     bouncePeriodMs = wEnd;
                     greenCenterFrac = Math.max(0.05F, Math.min(0.95F, p.stage() / 100.0F));
                     windowStart = now;
                     windowEnd = now + bouncePeriodMs * 20L;
                     waitingForGreenUpdate = false;
                     pressedThisWindow = false;
                  }
               } else {
                  greenZoneStart = 0L;
                  greenZoneEnd = 0L;
                  windowStart = now;
                  windowEnd = now + p.windowEndMs();
               }
            }
         }
      }
   }

   public static boolean handleGKeyPress() {
      if (!gWindowActive) {
         return false;
      } else {
         long now = System.currentTimeMillis();
         if (now >= gWindowStart && now <= gWindowEnd) {
            ClientPlayNetworking.send(new DapFusionHandler.FusionGPressPayload());
            gPressed = true;
            gWindowActive = false;
            return true;
         } else {
            return false;
         }
      }
   }

   public static boolean handleQTEGPress() {
      if (!qteActive) {
         return false;
      }

      long now = System.currentTimeMillis();
      if (qteType == 1) {
         boolean inGreen = now >= greenZoneStart && now <= greenZoneEnd;
         qteActive = false;
         if (inGreen) {
            flashEndTime = now + 200L;
            pressedThisWindow = true;
            ClientPlayNetworking.send(new QTEButtonPressPayload("G"));
         } else {
            failFlashEndTime = now + 200L;
            ClientPlayNetworking.send(new QTEButtonPressPayload("FAIL"));
         }
      } else if (qteType == 2) {
         if (waitingForGreenUpdate) {
            return true;
         }

         float dotPos = getBouncePos(now);
         float halfFrac = bouncePeriodMs > 0L ? (float)greenZoneStart / (float)bouncePeriodMs : 0.1F;
         boolean inGreen = Math.abs(dotPos - greenCenterFrac) <= halfFrac;
         if (inGreen) {
            flashEndTime = now + 200L;
            pressedThisWindow = true;
            waitingForGreenUpdate = true;
            ClientPlayNetworking.send(new QTEButtonPressPayload("G"));
         } else {
            failFlashEndTime = now + 200L;
            ClientPlayNetworking.send(new QTEButtonPressPayload("FAIL"));
         }
      } else {
         registerPress();
         ClientPlayNetworking.send(new QTEButtonPressPayload("G"));
      }

      return true;
   }

   private static float getBouncePos(long now) {
      if (bouncePeriodMs <= 0L) {
         return 0.5F;
      }

      long elapsed = now - windowStart;
      double t = (double)(elapsed % bouncePeriodMs) / bouncePeriodMs;
      return (float)(t < 0.5 ? t * 2.0 : (1.0 - t) * 2.0);
   }

   public static boolean handleQTEHPress() {
      if (!qteActive) {
         return false;
      }

      registerPress();
      ClientPlayNetworking.send(new QTEButtonPressPayload("H"));
      return true;
   }

   private static void registerPress() {
      long now = System.currentTimeMillis();
      boolean inWindow = qteType == 1 ? now >= greenZoneStart && now <= greenZoneEnd : now >= windowStart && now <= windowEnd;
      if (inWindow) {
         flashEndTime = now + 200L;
         pressedThisWindow = true;
      } else {
         failFlashEndTime = now + 200L;
      }
   }

   public static boolean isActive() {
      return currentPhase >= 0;
   }

   public static boolean isQTEOpen() {
      return qteActive;
   }

   public static boolean isGWindowOpen() {
      return gWindowActive;
   }

   private static void renderHUD(GuiGraphics ctx, DeltaTracker ticker) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         if (blackScreenActive) {
            long elapsed = System.currentTimeMillis() - blackScreenStartTime;
            float alpha = clamp((float)elapsed / 500.0F);
            int a = (int)(alpha * 255.0F);
            int sw = client.getWindow().getGuiScaledWidth();
            int sh = client.getWindow().getGuiScaledHeight();
            Matrix3x2fStack mat = ctx.pose();
            mat.pushMatrix();
            ctx.fill(0, 0, sw, sh, a << 24 | 0);
            mat.popMatrix();
         } else if (currentPhase >= 0) {
            long now = System.currentTimeMillis();
            int sw = client.getWindow().getGuiScaledWidth();
            int sh = client.getWindow().getGuiScaledHeight();
            Matrix3x2fStack mat = ctx.pose();
            mat.pushMatrix();
            if (now < flashEndTime) {
               float p = 1.0F - (float)(now - (flashEndTime - 800L)) / 800.0F;
               int a = (int)(clamp(p) * 120.0F);
               ctx.fill(0, 0, sw, sh, a << 24 | 16755200);
            } else if (now < failFlashEndTime) {
               float p = 1.0F - (float)(now - (failFlashEndTime - 500L)) / 500.0F;
               int a = (int)(clamp(p) * 100.0F);
               ctx.fill(0, 0, sw, sh, a << 24 | 16720418);
            }

            if (gWindowActive) {
               long gsNow = System.currentTimeMillis();
               if (gsNow >= gWindowStart && gsNow <= gWindowEnd) {
                  float rem = 1.0F - (float)(gsNow - gWindowStart) / (float)(gWindowEnd - gWindowStart);
                  int bw = 50;
                  int bh = 3;
                  int bx = (sw - bw) / 2;
                  int by = sh - 55;
                  ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, -1442840576);
                  ctx.fill(bx, by, bx + bw, by + bh, -13421773);
                  int fw = (int)(bw * clamp(rem));
                  if (fw > 0) {
                     ctx.fill(bx, by, bx + fw, by + bh, gPressed ? -12272828 : -22016);
                  }

                  String lbl = gPressed ? "§a✓" : "§6[G]";
                  int lw = client.font.width(lbl);
                  ctx.drawString(client.font, lbl, (sw - lw) / 2, by - 9, -1, true);
               }
            }

            if (qteActive) {
               int bw = 60;
               int bh = 4;
               int bx = (sw - bw) / 2;
               int by = sh - 85;
               ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, -1442840576);
               ctx.fill(bx, by, bx + bw, by + bh, -14540254);
               if (qteType == 1) {
                  long elapsed = now - windowStart;
                  float dotPos = clamp((float)elapsed / 1800.0F);
                  float gzStartFrac = clamp((float)(greenZoneStart - windowStart) / 1800.0F);
                  float gzEndFrac = clamp((float)(greenZoneEnd - windowStart) / 1800.0F);
                  int gzX1 = bx + (int)(gzStartFrac * bw);
                  int gzX2 = bx + (int)(gzEndFrac * bw);
                  ctx.fill(gzX1, by, gzX2, by + bh, -16729344);
                  int dotX = Math.max(bx, Math.min(bx + bw - 2, bx + (int)(dotPos * bw) - 1));
                  ctx.fill(dotX, by - 1, dotX + 2, by + bh + 1, -1);
                  if (now < flashEndTime) {
                     ctx.fill(bx, by, bx + bw, by + bh, -1157562624);
                  } else if (now < failFlashEndTime) {
                     ctx.fill(bx, by, bx + bw, by + bh, -1140907486);
                  }
               } else if (qteType == 2) {
                  float dotPos = getBouncePos(now);
                  float halfFrac = bouncePeriodMs > 0L ? (float)greenZoneStart / (float)bouncePeriodMs : 0.1F;
                  int gzX1 = bx + (int)((greenCenterFrac - halfFrac) * bw);
                  int gzX2 = bx + (int)((greenCenterFrac + halfFrac) * bw);
                  ctx.fill(bx, by, bx + bw, by + bh, -13421773);
                  ctx.fill(Math.max(bx, gzX1), by, Math.min(bx + bw, gzX2), by + bh, -16729344);
                  int dotX = Math.max(bx, Math.min(bx + bw - 3, bx + (int)(dotPos * bw) - 1));
                  ctx.fill(dotX, by - 1, dotX + 3, by + bh + 1, -1);
                  if (now < flashEndTime) {
                     ctx.fill(bx, by, bx + bw, by + bh, -1157562624);
                  } else if (now < failFlashEndTime) {
                     ctx.fill(bx, by, bx + bw, by + bh, -1140907486);
                  }
               } else {
                  int fw;
                  int barColor;
                  if (now < flashEndTime) {
                     fw = bw;
                     barColor = -1;
                  } else if (now < failFlashEndTime) {
                     fw = bw;
                     barColor = -56798;
                  } else if (now < windowStart) {
                     fw = bw;
                     barColor = -48060;
                  } else if (now <= windowEnd) {
                     float rem = clamp(1.0F - (float)(now - windowStart) / (float)(windowEnd - windowStart));
                     fw = (int)(bw * rem);
                     barColor = pressedThisWindow ? -16711936 : -16720640;
                  } else {
                     fw = 0;
                     barColor = -12303292;
                  }

                  if (fw > 0) {
                     ctx.fill(bx, by, bx + fw, by + bh, barColor);
                  }
               }

               if (expectedButton != null && !pressedThisWindow) {
                  boolean inZone;
                  if (qteType == 1) {
                     inZone = now >= greenZoneStart && now <= greenZoneEnd;
                  } else if (qteType == 2) {
                     float dp = getBouncePos(now);
                     float hf = bouncePeriodMs > 0L ? (float)greenZoneStart / (float)bouncePeriodMs : 0.1F;
                     inZone = Math.abs(dp - greenCenterFrac) <= hf;
                  } else {
                     inZone = now >= windowStart && now <= windowEnd;
                  }

                  float alpha = inZone ? (float)(Math.sin(now / 60.0) * 0.4 + 0.6) : (float)(Math.sin(now / 120.0) * 0.2 + 0.8);
                  int a = (int)(alpha * 255.0F);
                  String keyText = QTEClientHandler.resolveKeyName(expectedButton);
                  int kw = client.font.width(keyText);
                  ctx.drawString(client.font, keyText, (sw - kw) / 2, by - 9, a << 24 | 16777215, true);
               }

               if (maxStages > 1) {
                  int ds = 3;
                  int dg = 2;
                  int totalW = maxStages * ds + (maxStages - 1) * dg;
                  int dx = (sw - totalW) / 2;
                  int dy = by + bh + 3;

                  for (int i = 1; i <= maxStages; i++) {
                     int color = i < currentStage ? -16711936 : (i == currentStage ? -256 : -13421773);
                     ctx.fill(dx, dy, dx + ds, dy + ds, color);
                     dx += ds + dg;
                  }
               }
            }

            mat.popMatrix();
         }
      }
   }

   private static float clamp(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   private static void scheduleReset(long delayMs) {
      new Thread(() -> {
         try {
            Thread.sleep(delayMs);
         } catch (InterruptedException var3) {
         }

         resetState();
      }).start();
   }

   private static void resetState() {
      currentPhase = -1;
      phaseLabel = "";
      gWindowActive = false;
      qteActive = false;
      expectedButton = null;
      pressedThisWindow = false;
      blackScreenActive = false;
   }
}
