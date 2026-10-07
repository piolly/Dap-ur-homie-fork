package com.cooptest.client;

import com.cooptest.BlackHoodNetworking;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class BlackHoodClientHandler {
   private static final Set<UUID> hoodedPlayers = new HashSet<>();

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(BlackHoodNetworking.HoodStatePayload.ID, (payload, context) -> context.client().execute(() -> {
         if (payload.hasHood()) {
            hoodedPlayers.add(payload.targetId());
         } else {
            hoodedPlayers.remove(payload.targetId());
         }
      }));
      HudRenderCallback.EVENT.register((HudRenderCallback)(drawContext, tickCounter) -> {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            if (hoodedPlayers.contains(client.player.getUUID())) {
               int w = client.getWindow().getGuiScaledWidth();
               int h = client.getWindow().getGuiScaledHeight();
               drawContext.fill(0, 0, w, h, -16777216);
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> hoodedPlayers.clear());
   }

   public static boolean isLocalPlayerHooded() {
      Minecraft client = Minecraft.getInstance();
      return client.player == null ? false : hoodedPlayers.contains(client.player.getUUID());
   }
}
