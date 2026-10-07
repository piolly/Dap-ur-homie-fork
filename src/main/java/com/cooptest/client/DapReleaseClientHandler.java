package com.cooptest.client;

import com.cooptest.DapPositioning;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class DapReleaseClientHandler {
   private static final long RESEND_GAP_MS = 250L;
   private static long lastSentMs = 0L;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            if (isInDapHit(client)) {
               long now = System.currentTimeMillis();
               if (now - lastSentMs >= 250L) {
                  if (wantsOut(client)) {
                     ClientPlayNetworking.send(new DapPositioning.DapReleasePayload());
                     lastSentMs = now;
                  }
               }
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> lastSentMs = 0L);
   }

   private static boolean isInDapHit(Minecraft client) {
      CoopAnimationHandler.AnimState s = CoopAnimationHandler.getAnimState(client.player.getUUID());
      return s == CoopAnimationHandler.AnimState.DAP_HIT
         || s == CoopAnimationHandler.AnimState.DAP_HIT_WEAK
         || s == CoopAnimationHandler.AnimState.PERFECT_DAP_HIT
         || s == CoopAnimationHandler.AnimState.FIRE_DAP_HIT;
   }

   private static boolean wantsOut(Minecraft client) {
      long win = client.getWindow().handle();
      return GLFW.glfwGetKey(win, 87) == 1
         || GLFW.glfwGetKey(win, 65) == 1
         || GLFW.glfwGetKey(win, 83) == 1
         || GLFW.glfwGetKey(win, 68) == 1
         || GLFW.glfwGetKey(win, 32) == 1;
   }
}
