package com.cooptest.client;

import com.cooptest.DapFlair;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class DapFlairClientHandler {
   private static final int PROMPT_COLOR = -24576;
   private static final float PROMPT_SCALE = 1.0F;
   private static final int PROMPT_ABOVE_HOTBAR = 62;
   private static long armedUntil = 0L;
   private static long promptFrom = 0L;
   private static boolean sentThisWindow = false;
   private static boolean wasPressed = false;

   public static boolean isArmed() {
      return System.currentTimeMillis() < armedUntil;
   }

   private static boolean armed() {
      return System.currentTimeMillis() < armedUntil;
   }

   private static boolean promptVisible() {
      long now = System.currentTimeMillis();
      return now >= promptFrom && now < armedUntil;
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(DapFlair.FlairWindowPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
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
      ClientPlayNetworking.registerGlobalReceiver(DapFlair.FlairSuccessPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         CoopCameraShakeHandler.shake(payload.shake(), payload.shakeMs());
         Minecraft c = ctx.client();
         if (c.player != null) {
            c.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 2.0F);
            c.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
         }

         armedUntil = 0L;
         promptFrom = 0L;
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         DapFlair.FlairShakePayload.ID, (payload, ctx) -> ctx.client().execute(() -> CoopCameraShakeHandler.shake(payload.shake(), payload.shakeMs()))
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player == null) {
            armedUntil = 0L;
            promptFrom = 0L;
         } else {
            long win = client.getWindow().handle();
            boolean pressed = GLFW.glfwGetKey(win, 71) == 1;
            if (pressed && !wasPressed && armed()) {
               ClientPlayNetworking.send(new DapFlair.FlairPressPayload());
               sentThisWindow = true;
            }

            wasPressed = pressed;
         }
      });
      HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickDelta) -> {
         if (promptVisible()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
               int cx = client.getWindow().getGuiScaledWidth() / 2;
               int cy = client.getWindow().getGuiScaledHeight() - 62;
               ctx.pose().pushMatrix();
               ctx.pose().translate(cx, cy);
               ctx.pose().scale(1.0F, 1.0F);
               String label = sentThisWindow ? "G" : "G";
               int w = client.font.width(label);
               ctx.drawString(client.font, label, -w / 2, 0, sentThisWindow ? -10027162 : -24576, true);
               ctx.pose().popMatrix();
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> {
         armedUntil = 0L;
         promptFrom = 0L;
      });
   }
}
