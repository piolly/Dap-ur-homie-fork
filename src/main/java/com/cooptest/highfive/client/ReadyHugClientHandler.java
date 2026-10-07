package com.cooptest.highfive.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.highfive.ReadyHugHandler;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;

public class ReadyHugClientHandler {
   private static boolean shiftWasHeld = false;
   private static boolean inHug = false;
   private static UUID partnerId = null;
   private static final int WATCHDOG_TICKS = 100;
   private static int ticksSinceSignal = 0;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(ReadyHugHandler.HugSessionPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         ticksSinceSignal = 0;
         inHug = payload.active();
         partnerId = payload.active() ? payload.partner() : null;
         if (!payload.active()) {
            shiftWasHeld = false;
         }
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player == null) {
            forceRelease();
         } else {
            if (inHug) {
               if (++ticksSinceSignal > 100) {
                  forceRelease();
               }
            } else {
               ticksSinceSignal = 0;
            }

            UUID id = client.player.getUUID();
            PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            if (pose != PoseState.GRAB_READY && !inHug) {
               if (shiftWasHeld) {
                  ClientPlayNetworking.send(new ReadyHugHandler.HugShiftPayload(false, false));
                  shiftWasHeld = false;
               }
            } else {
               long win = client.getWindow().handle();
               boolean held = com.mojang.blaze3d.platform.InputConstants.isKeyDown(340) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(344);
               boolean rightClick = net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown();
               if (held != shiftWasHeld) {
                  ClientPlayNetworking.send(new ReadyHugHandler.HugShiftPayload(held, rightClick));
                  shiftWasHeld = held;
               } else if (held && client.player.tickCount % 5 == 0) {
                  ClientPlayNetworking.send(new ReadyHugHandler.HugShiftPayload(true, rightClick));
               }
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> forceRelease());
      ClientPlayConnectionEvents.JOIN.register((Join)(h, s, c) -> forceRelease());
   }

   public static void forceRelease() {
      inHug = false;
      partnerId = null;
      shiftWasHeld = false;
      ticksSinceSignal = 0;
   }

   public static boolean isLocalPlayerInHug() {
      return inHug;
   }

   public static UUID getPartnerId() {
      return partnerId;
   }
}
