package com.cooptest;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public class GrabInteractionHandler {

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer clicker)) return InteractionResult.PASS;
            if (!(entity instanceof ServerPlayer target)) return InteractionResult.PASS;

            PoseState targetEntityPose = PoseNetworking.poseStates.getOrDefault(target.getUUID(), PoseState.NONE);
            if (targetEntityPose != PoseState.GRAB_READY) return InteractionResult.PASS;

            PoseState clickerPose = PoseNetworking.poseStates.getOrDefault(clicker.getUUID(), PoseState.NONE);
            if (clickerPose == PoseState.GRAB_HOLDING || clickerPose == PoseState.GRABBED) return InteractionResult.PASS;

            if (target.distanceTo(clicker) > 3.0f) return InteractionResult.PASS;

            boolean success = GrabMechanic.tryGrab(target, clicker);
            return success ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }
}
