package com.cooptest;

import java.util.HashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.server.level.ServerPlayer;

public class AnimationTickHandler {
   private static final HashMap<UUID, Integer> animationTicks = new HashMap<>();
   private static final int ACTION_DURATION = 10;
   private static final int RETURN_DURATION = 10;

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            PoseState currentState = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            if (currentState == PoseState.PUSH_ACTION) {
               int ticks = animationTicks.getOrDefault(id, 0) + 1;
               animationTicks.put(id, ticks);
               if (ticks >= 10) {
                  animationTicks.put(id, 0);
                  PoseNetworking.broadcastPoseChange(server, id, PoseState.PUSH_RETURN);
               }
            } else if (currentState == PoseState.PUSH_RETURN) {
               int ticks = animationTicks.getOrDefault(id, 0) + 1;
               animationTicks.put(id, ticks);
               if (ticks >= 10) {
                  animationTicks.put(id, 0);
                  PoseNetworking.broadcastPoseChange(server, id, PoseState.PUSH_IDLE);
               }
            } else {
               animationTicks.put(id, 0);
            }
         }
      });
   }
}
