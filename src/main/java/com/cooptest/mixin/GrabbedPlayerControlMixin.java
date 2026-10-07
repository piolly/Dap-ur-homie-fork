package com.cooptest.mixin;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class GrabbedPlayerControlMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void lockGrabbedMovement(Vec3 movementInput, CallbackInfo ci) {
        Player self = (Player)(Object)this;
        PoseState pose = PoseNetworking.poseStates.getOrDefault(self.getUUID(), PoseState.NONE);
        if (pose == PoseState.GRABBED && self.isPassenger()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void lockGrabbedRotation(CallbackInfo ci) {
        Player self = (Player)(Object)this;
        PoseState pose = PoseNetworking.poseStates.getOrDefault(self.getUUID(), PoseState.NONE);
        if (pose == PoseState.GRABBED && self.isPassenger()) {
            Entity vehicle = self.getVehicle();
            if (vehicle instanceof Player holder) {
                float yaw = holder.getYRot();
                float pitch = holder.getXRot();
                self.setYRot(yaw);      self.yRotO = yaw;
                self.setYBodyRot(yaw);  self.setYHeadRot(yaw);
                self.setXRot(pitch);  self.xRotO = pitch;
            }
        }
    }
}