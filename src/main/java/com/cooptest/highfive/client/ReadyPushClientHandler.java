package com.cooptest.highfive.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.highfive.ReadyPushHandler;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;

public class ReadyPushClientHandler {
   private static boolean lastShift = false;
   private static boolean lastRight = false;
   private static int refreshTicks = 0;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            UUID id = client.player.getUUID();
            PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            boolean relevant = pose == PoseState.GRAB_READY || pose == PoseState.PUSH_IDLE;
            if (!relevant) {
               if (lastShift || lastRight) {
                  ClientPlayNetworking.send(new ReadyPushHandler.PushInputPayload(false, false));
                  lastShift = false;
                  lastRight = false;
               }
            } else {
               long win = client.getWindow().handle();
               boolean shift = com.mojang.blaze3d.platform.InputConstants.isKeyDown(340) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(344);
               boolean right = GLFW.glfwGetMouseButton(win, 1) == 1;
               boolean changed = shift != lastShift || right != lastRight;
               if (changed || shift && ++refreshTicks >= 5) {
                  if (!changed) {
                     refreshTicks = 0;
                  }

                  ClientPlayNetworking.send(new ReadyPushHandler.PushInputPayload(shift, right));
                  lastShift = shift;
                  lastRight = right;
               }
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> reset());
      ClientPlayConnectionEvents.JOIN.register((Join)(h, s, c) -> reset());
   }

   private static void reset() {
      lastShift = false;
      lastRight = false;
      refreshTicks = 0;
   }
}
