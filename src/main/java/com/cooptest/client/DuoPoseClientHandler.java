package com.cooptest.client;

import com.cooptest.DuoPoseHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class DuoPoseClientHandler {
   private static boolean localPlayerFrozen = false;

   public static boolean isLocalPlayerFrozen() {
      return localPlayerFrozen;
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(DuoPoseHandler.DuoPoseFreezePayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         if (ctx.client().player != null) {
            if (ctx.client().player.getUUID().equals(payload.playerId())) {
               localPlayerFrozen = payload.frozen();
            }
         }
      }));
   }
}
