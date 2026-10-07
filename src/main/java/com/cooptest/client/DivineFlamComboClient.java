package com.cooptest.client;

import com.cooptest.DivineFlamCombo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public class DivineFlamComboClient {
   private static KeyMapping divineFlameKey;
   private static long divineFlameEndTime = 0L;

   public static void register() {
      divineFlameKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cooptest.divine_flame", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 74, CoopKeyCategories.COOPTEST));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (divineFlameKey.consumeClick()) {
            ClientPlayNetworking.send(new DivineFlamCombo.DivineJPressPayload());
            divineFlameEndTime = System.currentTimeMillis() + 3000L;
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(
         DivineFlamCombo.DivineStartPayload.ID, (payload, context) -> context.client().execute(() -> divineFlameEndTime = System.currentTimeMillis() + 1460L)
      );
   }

   public static boolean isLocalPlayerInCombo() {
      return System.currentTimeMillis() < divineFlameEndTime;
   }

   public static boolean onHighFivePress() {
      return false;
   }
}
