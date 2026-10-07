package com.cooptest.mixin;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PlayerRidingMixin {
   @Unique
   private static boolean coop$isGrabVehicle(Entity vehicle) {
      if (!(vehicle instanceof Player holder)) {
         return false;
      } else {
         PoseState pose = PoseNetworking.poseStates.getOrDefault(holder.getUUID(), PoseState.NONE);
         return pose == PoseState.GRAB_READY || pose == PoseState.GRAB_HOLDING;
      }
   }

   @Redirect(
      method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z")
   )
   private boolean coop$allowPlayerVehicle(EntityType<?> vehicleType, Entity vehicle, boolean force, boolean emitEvent) {
      return coop$isGrabVehicle(vehicle) || vehicleType.canSerialize();
   }

   @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
   private void coop$allowGrabRiding(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
      if (passenger instanceof Player && coop$isGrabVehicle((Entity)(Object)this)) {
         cir.setReturnValue(true);
      }
   }
}
