package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;


public class CarryingSlowdown {

    public static final float CARRY_SPEED_MULTIPLIER = 0.6f;

    private static final double SLOWDOWN_AMOUNT = -(1.0 - CARRY_SPEED_MULTIPLIER) * 0.1;

    private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath("cooptest", "carrying_slowdown");

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                updateSlowdown(player);
            }
        });
    }

    private static void updateSlowdown(ServerPlayer player) {
        PoseState pose = PoseNetworking.poseStates.getOrDefault(player.getUUID(), PoseState.NONE);
        boolean isCarrying = pose == PoseState.GRAB_HOLDING;

        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        AttributeModifier existingModifier = speedAttr.getModifier(MODIFIER_ID);

        if (isCarrying) {
            // Add slowdown if not present
            if (existingModifier == null) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        MODIFIER_ID,
                        SLOWDOWN_AMOUNT,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        } else {
            if (existingModifier != null) {
                speedAttr.removeModifier(MODIFIER_ID);
            }
        }
    }
}