package com.cooptest.mixin;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class PlayerEntityPassengerOffsetMixin {
   private static final double GRAB_OFFSET_FORWARD = 0.6;
   private static final double GRAB_OFFSET_UP = 0.8;
   private static final double GRAB_OFFSET_RIGHT = 3.0;

   @Inject(method = "getPassengerRidingPosition", at = @At("RETURN"), cancellable = true)
   private void customPassengerPosition(Entity passenger, CallbackInfoReturnable<Vec3> cir) {
      Entity vehicle = (Entity)this;
      if (vehicle instanceof Player holder) {
         if (passenger instanceof Player) {
            PoseState holderPose = PoseNetworking.poseStates.getOrDefault(holder.getUUID(), PoseState.NONE);
            if (holderPose == PoseState.GRAB_HOLDING) {
               Vec3 base = (Vec3)cir.getReturnValue();
               float yaw = holder.getYRot();
               double yawRad = Math.toRadians(-yaw);
               double rotX = 3.0 * Math.cos(yawRad) + 0.6 * Math.sin(yawRad);
               double rotZ = 3.0 * Math.sin(yawRad) - 0.6 * Math.cos(yawRad);
               cir.setReturnValue(new Vec3(base.x + rotX, base.y + 0.8, base.z + rotZ));
            }
         }
      }
   }
}
