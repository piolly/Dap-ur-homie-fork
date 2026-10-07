package com.cooptest.mixin.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.CoopImpactHandler;
import com.cooptest.client.CoopRenderStateData;
import com.cooptest.client.SpearStrikeClientHandler;
import java.util.HashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {
   @Unique
   private static final HashMap<UUID, Float> lockedYaw = new HashMap<>();

   @Inject(
      method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
      at = @At("TAIL")
   )
   private void coop$captureState(Avatar player, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
      FabricRenderState fabricState = state;
      Entity vehicle = player.getVehicle();
      boolean vehicleIsPlayer = vehicle instanceof Player;
      fabricState.setData(
         CoopRenderStateData.PLAYER_SNAPSHOT,
         new CoopRenderStateData.PlayerSnapshot(
            player.getUUID(),
            player instanceof LivingEntity le && le.isSwinging(),
            player instanceof LivingEntity le2 && le2.isUsingItem(),
            player.onGround(),
            player.isPassenger(),
            vehicleIsPlayer,
            vehicleIsPlayer ? vehicle.getYRot() : player.getYRot(),
            player.getYRot()
         )
      );
      CoopImpactHandler.registerPlayerModel(((LivingModelAccessor)(Object)this).coop$getModel());
      UUID uuid = player.getUUID();
      PoseState pose = PoseNetworking.poseStates.getOrDefault(uuid, PoseState.NONE);
      boolean spearFlying = SpearStrikeClientHandler.isFlying(player.getId())
         || player == Minecraft.getInstance().player && SpearStrikeClientHandler.isSteering();
      if (spearFlying) {
         fabricState.setData(CoopRenderStateData.GRAB_FACING_YAW, player.getYRot());
      } else {
         if (pose == PoseState.GRABBED) {
            CoopAnimationHandler.AnimState animState = CoopAnimationHandler.getAnimState(uuid);
            if (animState == CoopAnimationHandler.AnimState.SPIN || animState == CoopAnimationHandler.AnimState.GROUND_POUND_DIVE) {
               fabricState.setData(CoopRenderStateData.GRAB_FACING_YAW, null);
               return;
            }

            float facingYaw;
            if (vehicle instanceof Player holder) {
               facingYaw = holder.getYRot();
               lockedYaw.put(uuid, facingYaw);
            } else if (lockedYaw.containsKey(uuid)) {
               facingYaw = lockedYaw.get(uuid);
            } else {
               facingYaw = player.getYRot();
               lockedYaw.put(uuid, facingYaw);
            }

            fabricState.setData(CoopRenderStateData.GRAB_FACING_YAW, facingYaw);
         } else {
            lockedYaw.remove(uuid);
            fabricState.setData(CoopRenderStateData.GRAB_FACING_YAW, null);
         }
      }
   }
}
