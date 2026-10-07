package com.cooptest.client;

import com.cooptest.BonkHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public class BonkClientHandler {
   private static KeyMapping keyBonk;
   private static boolean movementLocked = false;
   private static float pendingYawDelta = 0.0F;
   private static float pendingPitchDelta = 0.0F;
   private static boolean jerkPending = false;

   public static void register() {
      keyBonk = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cooptest.bonk", 76, CoopKeyCategories.COOPTEST));
      ClientPlayNetworking.registerGlobalReceiver(
         BonkHandler.BonkMoveLockPayload.ID, (payload, context) -> context.client().execute(() -> movementLocked = payload.locked())
      );
      ClientPlayNetworking.registerGlobalReceiver(BonkHandler.BonkCameraPayload.ID, (payload, context) -> context.client().execute(() -> {
         pendingYawDelta = payload.deltaYaw();
         pendingPitchDelta = payload.deltaPitch();
         jerkPending = true;
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            while (keyBonk.consumeClick()) {
               ClientPlayNetworking.send(new BonkHandler.BonkLKeyPayload());
            }

            if (movementLocked) {
               client.player.setDeltaMovement(0.0, client.player.getDeltaMovement().y, 0.0);
            }

            if (jerkPending && client.player != null) {
               jerkPending = false;
               client.player.setYRot(client.player.getYRot() + pendingYawDelta);
               client.player.setXRot(Math.max(-90.0F, Math.min(90.0F, client.player.getXRot() + pendingPitchDelta)));
            }
         }
      });
   }

   public static boolean isMovementLocked() {
      return movementLocked;
   }

   public static void reset() {
      movementLocked = false;
      jerkPending = false;
   }
}
