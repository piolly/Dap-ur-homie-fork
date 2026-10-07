package com.cooptest.client;

import com.cooptest.PushInteractionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;

public class PushClientHandler {
   private static final Map<UUID, Long> pushAnimStart = new HashMap<>();
   private static final long PUSH_ANIM_DURATION = 400L;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(PushInteractionHandler.PushAnimPayload.ID, (payload, context) -> context.client().execute(() -> {
         pushAnimStart.put(payload.playerId(), System.currentTimeMillis());
         Minecraft client = context.client();
         if (client.level != null) {
            for (AbstractClientPlayer player : client.level.players()) {
               if (player.getUUID().equals(payload.playerId())) {
                  CoopAnimationHandler.playPushAnimation(player);
                  break;
               }
            }
         }

         if (client.player != null && client.player.getUUID().equals(payload.playerId())) {
            client.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
         }
      }));
   }

   public static float getPushAnimProgress(UUID playerId) {
      Long start = pushAnimStart.get(playerId);
      if (start == null) {
         return -1.0F;
      } else {
         long elapsed = System.currentTimeMillis() - start;
         if (elapsed > 400L) {
            pushAnimStart.remove(playerId);
            return -1.0F;
         } else {
            return (float)elapsed / 400.0F;
         }
      }
   }

   public static void cleanup(UUID playerId) {
      pushAnimStart.remove(playerId);
   }
}
