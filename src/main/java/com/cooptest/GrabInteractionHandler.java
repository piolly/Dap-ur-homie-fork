package com.cooptest;

import com.cooptest.bros.BrosHandler;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public class GrabInteractionHandler {
   public static void register() {
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer clicker) {
            if (entity instanceof ServerPlayer target) {
               if (clicker.isShiftKeyDown()) {
                  return InteractionResult.PASS;
               }

               if (BrosHandler.isEngaged(target.getUUID())) {
                  return InteractionResult.PASS;
               }

               if (BrosHandler.isEngaged(clicker.getUUID())) {
                  return InteractionResult.PASS;
               }

               PoseState targetPose = PoseNetworking.poseStates.getOrDefault(target.getUUID(), PoseState.NONE);
               if (targetPose != PoseState.GRAB_READY) {
                  return InteractionResult.PASS;
               }

               PoseState clickerPose = PoseNetworking.poseStates.getOrDefault(clicker.getUUID(), PoseState.NONE);
               if (clickerPose != PoseState.GRAB_HOLDING && clickerPose != PoseState.GRABBED) {
                  if (target.distanceTo(clicker) > 3.0F) {
                     return InteractionResult.PASS;
                  }

                  boolean success = GrabMechanic.tryGrab(target, clicker);
                  return (InteractionResult)(success ? InteractionResult.SUCCESS : InteractionResult.PASS);
               } else {
                  return InteractionResult.PASS;
               }
            } else {
               return InteractionResult.PASS;
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }
}
