package com.cooptest.client;

import com.cooptest.SitHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class SitClientHandler {
   private static boolean wasFHeld = false;
   private static net.minecraft.client.KeyMapping sitKey;

   public static void register() {
      sitKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new net.minecraft.client.KeyMapping("key.coopmoves.sit", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 90, CoopKeyCategories.COOPMOVES));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.gui.screen() == null) {
            boolean fHeld = sitKey.isDown();
            if (fHeld != wasFHeld) {
               wasFHeld = fHeld;
               ClientPlayNetworking.send(new SitHandler.SitFHoldPayload(fHeld));
            }
         }
      });
   }
}
