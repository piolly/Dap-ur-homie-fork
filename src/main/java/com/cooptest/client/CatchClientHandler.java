package com.cooptest.client;

import com.cooptest.FallCatchHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;

public class CatchClientHandler {
   private static final Map<UUID, Long> catcherAnimStart = new HashMap<>();
   private static final Map<UUID, Long> caughtAnimStart = new HashMap<>();
   private static final long CATCHER_ANIM_DURATION = 500L;
   private static final long CAUGHT_ANIM_DURATION = 400L;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(FallCatchHandler.CatchAnimPayload.ID, (payload, context) -> context.client().execute(() -> {
         long now = System.currentTimeMillis();
         catcherAnimStart.put(payload.catcherId(), now);
         caughtAnimStart.put(payload.caughtId(), now);
         if (context.client().level != null) {
            for (Player player : context.client().level.players()) {
               if (player.getUUID().equals(payload.catcherId())) {
                  CoopAnimationHandler.playCatchAnimation(player);
                  break;
               }
            }
         }
      }));
   }

   public static float getCatcherAnimProgress(UUID playerId) {
      Long startTime = catcherAnimStart.get(playerId);
      if (startTime == null) {
         return -1.0F;
      } else {
         long elapsed = System.currentTimeMillis() - startTime;
         if (elapsed > 500L) {
            catcherAnimStart.remove(playerId);
            return -1.0F;
         } else {
            return (float)elapsed / 500.0F;
         }
      }
   }

   public static float getCaughtAnimProgress(UUID playerId) {
      Long startTime = caughtAnimStart.get(playerId);
      if (startTime == null) {
         return -1.0F;
      } else {
         long elapsed = System.currentTimeMillis() - startTime;
         if (elapsed > 400L) {
            caughtAnimStart.remove(playerId);
            return -1.0F;
         } else {
            return (float)elapsed / 400.0F;
         }
      }
   }
}
