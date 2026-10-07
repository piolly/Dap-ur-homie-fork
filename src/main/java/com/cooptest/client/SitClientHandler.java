package com.cooptest.client;

import com.cooptest.SitHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class SitClientHandler {
   private static boolean wasFHeld = false;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.screen == null) {
            boolean fHeld = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 70);
            if (fHeld != wasFHeld) {
               wasFHeld = fHeld;
               ClientPlayNetworking.send(new SitHandler.SitFHoldPayload(fHeld));
            }
         }
      });
   }
}
