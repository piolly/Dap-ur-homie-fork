package com.cooptest.client;

import com.cooptest.SikeFollowUpHandler;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class SikeFollowUpClientHandler {
   private static final Random RNG = new Random();
   private static final int PROMPT_COLOR = -24576;
   private static final float PROMPT_SCALE = 1.0F;
   private static final int PROMPT_ABOVE_HOTBAR = 62;
   private static long armedUntil = 0L;
   private static long promptFrom = 0L;
   private static boolean sentThisWindow = false;
   private static boolean gWasPressed = false;
   private static boolean flickering = false;
   private static long flickerEndMs = 0L;
   private static long lastFlickMs = 0L;
   private static float baseYaw = 0.0F;
   private static float basePitch = 0.0F;
   private static final long FLICKER_INTERVAL_MS = 90L;
   private static final float YAW_MAX_OFFSET = 55.0F;
   private static final float PITCH_MAX_OFFSET = 45.0F;

   public static boolean isArmed() {
      return System.currentTimeMillis() < armedUntil;
   }

   private static boolean promptVisible() {
      long now = System.currentTimeMillis();
      return now >= promptFrom && now < armedUntil;
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(SikeFollowUpHandler.SikeWindowPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         if (payload.open()) {
            long now = System.currentTimeMillis();
            armedUntil = now + payload.durationMs();
            promptFrom = now + payload.promptDelayMs();
            sentThisWindow = false;
         } else {
            armedUntil = 0L;
            promptFrom = 0L;
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(SikeFollowUpHandler.NoyaFlickerPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.victimId())) {
               if (payload.start()) {
                  baseYaw = client.player.getYRot();
                  basePitch = client.player.getXRot();
                  flickerEndMs = System.currentTimeMillis() + payload.durationMs();
                  lastFlickMs = 0L;
                  flickering = true;
               } else {
                  flickering = false;
                  client.player.setYRot(baseYaw);
                  client.player.setXRot(basePitch);
                  float finalPitch = Math.max(-90.0F, basePitch - 55.0F);
                  client.player.setXRot(finalPitch);
               }
            }
         }
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player == null) {
            armedUntil = 0L;
            promptFrom = 0L;
            flickering = false;
         } else {
            long win = client.getWindow().handle();
            boolean gPressed = GLFW.glfwGetKey(win, 71) == 1;
            if (gPressed && !gWasPressed && isArmed()) {
               ClientPlayNetworking.send(new SikeFollowUpHandler.SikePressPayload());
               sentThisWindow = true;
            }

            gWasPressed = gPressed;
            if (flickering) {
               long now = System.currentTimeMillis();
               if (now >= flickerEndMs) {
                  flickering = false;
                  client.player.setYRot(baseYaw);
                  client.player.setXRot(basePitch);
               } else {
                  if (now - lastFlickMs >= 90L) {
                     lastFlickMs = now;
                     float yawOffset = (RNG.nextFloat() - 0.5F) * 2.0F * 55.0F;
                     float pitchOffset = (RNG.nextFloat() - 0.3F) * 45.0F;
                     float newPitch = Math.max(-90.0F, Math.min(90.0F, basePitch + pitchOffset));
                     client.player.setYRot(baseYaw + yawOffset);
                     client.player.setXRot(newPitch);
                  } else {
                     client.player.setYRot(client.player.getYRot());
                     client.player.setXRot(client.player.getXRot());
                  }
               }
            }
         }
      });
      HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickDelta) -> {
         if (promptVisible()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && !client.options.hideGui) {
               int cx = client.getWindow().getGuiScaledWidth() / 2;
               int cy = client.getWindow().getGuiScaledHeight() - 62;
               ctx.pose().pushMatrix();
               ctx.pose().translate(cx, cy);
               ctx.pose().scale(1.0F, 1.0F);
               String label = "G";
               int w = client.font.width(label);
               ctx.drawString(client.font, label, -w / 2, 0, sentThisWindow ? -10027162 : -24576, true);
               ctx.pose().popMatrix();
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> forceClear());
      ClientPlayConnectionEvents.JOIN.register((Join)(h, s, c) -> forceClear());
   }

   private static void forceClear() {
      armedUntil = 0L;
      promptFrom = 0L;
      sentThisWindow = false;
      gWasPressed = false;
      flickering = false;
   }

   public static boolean isFlickering() {
      return flickering;
   }
}
