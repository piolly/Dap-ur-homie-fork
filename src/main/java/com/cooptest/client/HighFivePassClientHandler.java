package com.cooptest.client;

import com.cooptest.HighFivePassHandler;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class HighFivePassClientHandler {
   private static final long IMPACT_DELAY_MS = 210L;
   private static final long LOCK_END_MS = 290L;
   private static final float CAMERA_SHAKE_AMOUNT = 0.7F;
   private static final long CAMERA_SHAKE_MS = 80L;
   private static final float YAW_LOCK_RANGE = 15.0F;
   private static boolean lockActive = false;
   private static boolean shakeTriggered = false;
   private static float lockedYaw = 0.0F;
   private static long startTime = 0L;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(HighFivePassHandler.HighFivePassStartPayload.ID, (payload, context) -> {
         Minecraft client = Minecraft.getInstance();
         client.execute(() -> {
            if (client.player != null) {
               lockedYaw = client.player.getYRot();
               lockActive = true;
               shakeTriggered = false;
               startTime = System.currentTimeMillis();
            }
         });
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> tick());
   }

   private static void tick() {
      if (lockActive) {
         long elapsed = System.currentTimeMillis() - startTime;
         if (!shakeTriggered && elapsed >= 210L) {
            CoopCameraShakeHandler.shake(0.7F, 80L);
            CoopSpeedLinesRenderer.start();
            shakeTriggered = true;
         }

         if (elapsed >= 290L) {
            lockActive = false;
         }
      }
   }

   public static void clampCameraYaw() {
      if (lockActive) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            float currentYaw = client.player.getYRot();
            float delta = Mth.wrapDegrees(currentYaw - lockedYaw);
            float clampedDelta = Mth.clamp(delta, -15.0F, 15.0F);
            if (clampedDelta != delta) {
               float newYaw = lockedYaw + clampedDelta;
               client.player.setYRot(newYaw);
               client.player.setYHeadRot(newYaw);
               client.player.setYBodyRot(newYaw);
            }
         }
      }
   }

   public static boolean isYawLocked() {
      return lockActive;
   }

   public static void onAnimationEnd(UUID playerId) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.player.getUUID().equals(playerId)) {
         lockActive = false;
      }
   }
}
