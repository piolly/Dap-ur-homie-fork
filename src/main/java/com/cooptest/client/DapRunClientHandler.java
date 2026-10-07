package com.cooptest.client;

import com.cooptest.DapRunHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class DapRunClientHandler {
   private static final long IMPACT_MS = 210L;
   private static final float SHAKE_T1 = 0.3F;
   private static final float SHAKE_T2 = 0.55F;
   private static final float SHAKE_T3 = 0.8F;
   private static final long SHAKE_MS = 110L;
   private static final long IMPACT_FRAME_MS = 22L;
   private static boolean pending = false;
   private static long fireAt = 0L;
   private static int tier = 1;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(DapRunHandler.DapRunStartPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         tier = payload.tier();
         fireAt = System.currentTimeMillis() + 210L;
         pending = true;
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (pending) {
            if (System.currentTimeMillis() >= fireAt) {
               pending = false;
               fire();
            }
         }
      });
   }

   private static void fire() {
      float amount = switch (tier) {
         case 2 -> 0.55F;
         case 3 -> 0.8F;
         default -> 0.3F;
      };
      CoopCameraShakeHandler.shake(amount, 110L);
      if (tier >= 2) {
         CoopSpeedLinesRenderer.start();
      }

      if (tier >= 3) {
         CoopImpactHandler.start(CoopImpactHandler.REGULAR_DAP_SEQUENCE, 22L, true);
         CoopScreenSquishHandler.trigger();
      }
   }

   public static void reset() {
      pending = false;
      tier = 1;
   }
}
