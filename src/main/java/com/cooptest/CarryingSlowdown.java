package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public class CarryingSlowdown {
   public static final float CARRY_SPEED_MULTIPLIER = 0.6F;
   private static final double SLOWDOWN_AMOUNT = -0.039999997615814215;
   private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath("cooptest", "carrying_slowdown");

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updateSlowdown(player);
         }
      });
   }

   private static void updateSlowdown(ServerPlayer player) {
      PoseState pose = PoseNetworking.poseStates.getOrDefault(player.getUUID(), PoseState.NONE);
      boolean isCarrying = pose == PoseState.GRAB_HOLDING;
      AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speedAttr != null) {
         AttributeModifier existingModifier = speedAttr.getModifier(MODIFIER_ID);
         if (isCarrying) {
            if (existingModifier == null) {
               speedAttr.addTransientModifier(new AttributeModifier(MODIFIER_ID, -0.039999997615814215, Operation.ADD_MULTIPLIED_TOTAL));
            }
         } else if (existingModifier != null) {
            speedAttr.removeModifier(MODIFIER_ID);
         }
      }
   }
}
