package com.cooptest.highfive.client;

import com.cooptest.client.CoopCameraShakeHandler;
import com.cooptest.highfive.ReadyFiveHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ReadyFiveClientHandler {
   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(
         ReadyFiveHandler.ReadyFiveImpactPayload.ID,
         (payload, context) -> context.client().execute(() -> CoopCameraShakeHandler.shake(payload.amount(), payload.durationMs()))
      );
   }
}
