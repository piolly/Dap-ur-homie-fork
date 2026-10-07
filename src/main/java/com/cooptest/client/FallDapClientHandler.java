package com.cooptest.client;

import com.cooptest.FallDapHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class FallDapClientHandler {
   private static final Map<UUID, Integer> fallDapStates = new HashMap<>();
   public static final int STATE_NONE = 0;
   public static final int STATE_CHARGING = 1;
   public static final int STATE_FALLING = 2;
   public static final int STATE_HIT = 3;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(FallDapHandler.FallDapAnimPayload.ID, (payload, context) -> context.client().execute(() -> {
         UUID playerId = payload.playerId();
         int state = payload.state();
         fallDapStates.put(playerId, state);
         Minecraft client = context.client();
         if (client.level != null) {
            for (Player player : client.level.players()) {
               if (player.getUUID().equals(playerId)) {
                  triggerFallDapAnimation(player, state);
                  break;
               }
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(FallDapHandler.SquashAnimPayload.ID, (payload, context) -> context.client().execute(() -> {
         UUID playerId = payload.playerId();
         Minecraft client = context.client();
         if (client.level != null) {
            for (Player player : client.level.players()) {
               if (player.getUUID().equals(playerId)) {
                  CoopAnimationHandler.playSquashed(player);
                  break;
               }
            }
         }
      }));
   }

   private static void triggerFallDapAnimation(Player player, int state) {
      UUID playerId = player.getUUID();
      switch (state) {
         case 0:
            fallDapStates.remove(playerId);
            break;
         case 1:
            CoopAnimationHandler.playFallDapChargeStart(player);
            break;
         case 2:
            CoopAnimationHandler.playFallDapFalling(player);
            break;
         case 3:
            CoopAnimationHandler.playFallDapHit(player);
      }
   }

   public static int getFallDapState(UUID playerId) {
      return fallDapStates.getOrDefault(playerId, 0);
   }

   public static boolean isInFallDap(UUID playerId) {
      int state = fallDapStates.getOrDefault(playerId, 0);
      return state == 1 || state == 2;
   }

   public static void cleanup(UUID playerId) {
      fallDapStates.remove(playerId);
   }
}
