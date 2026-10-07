package com.cooptest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.server.level.ServerPlayer;

public class LaunchedPlayerTracker {
   private static final HashMap<UUID, Integer> launchedTicks = new HashMap<>();
   private static final int TRAIL_DURATION = 20;

   public static void markPlayerAsLaunched(UUID playerId) {
      launchedTicks.put(playerId, 0);
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         Iterator<Entry<UUID, Integer>> iterator = launchedTicks.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<UUID, Integer> entry = iterator.next();
            UUID id = entry.getKey();
            int ticks = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
               iterator.remove();
            } else if (ticks < 20) {
               PoseEffects.playLaunchTrailEffects(player);
               entry.setValue(ticks + 1);
            } else {
               iterator.remove();
            }
         }
      });
   }
}
