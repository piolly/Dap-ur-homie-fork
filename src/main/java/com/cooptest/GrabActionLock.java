package com.cooptest;

import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

public class GrabActionLock {
   public static boolean isInGrab(UUID playerId) {
      if (playerId == null) {
         return false;
      }

      if (GrabMechanic.holding.containsKey(playerId)) {
         return true;
      }

      if (GrabMechanic.heldBy.containsKey(playerId)) {
         return true;
      }

      PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
      return pose == PoseState.GRAB_HOLDING || pose == PoseState.GRABBED;
   }

   public static boolean isInGrab(Player player) {
      return player != null && isInGrab(player.getUUID());
   }

   public static boolean isGrabbed(UUID playerId) {
      return GrabMechanic.heldBy.containsKey(playerId) || PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE) == PoseState.GRABBED;
   }

   public static boolean isHolding(UUID playerId) {
      return GrabMechanic.holding.containsKey(playerId) || PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE) == PoseState.GRAB_HOLDING;
   }

   public static void register() {
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         } else {
            return (InteractionResult)(isInGrab(player) ? InteractionResult.FAIL : InteractionResult.PASS);
         }
      });
      UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         } else {
            return (InteractionResult)(isInGrab(player) ? InteractionResult.FAIL : InteractionResult.PASS);
         }
      });
      AttackBlockCallback.EVENT.register((AttackBlockCallback)(player, world, hand, pos, direction) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         } else {
            return (InteractionResult)(isInGrab(player) ? InteractionResult.FAIL : InteractionResult.PASS);
         }
      });
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         } else {
            return (InteractionResult)(isInGrab(player) ? InteractionResult.FAIL : InteractionResult.PASS);
         }
      });
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         } else {
            return (InteractionResult)(isInGrab(player) ? InteractionResult.FAIL : InteractionResult.PASS);
         }
      });
      PlayerBlockBreakEvents.BEFORE.register((Before)(world, player, pos, state, blockEntity) -> world.isClientSide() ? true : !isInGrab(player));
   }
}
