package com.cooptest.mixin;

import com.cooptest.meme.SpinYeetClientHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class SpinYeetCameraRollMixin {
   @Shadow
   private Quaternionf rotation;

   @Inject(method = "update", at = @At("RETURN"))
   private void spinYeet$applyRoll(DeltaTracker deltaTracker, CallbackInfo ci) {
      float roll = SpinYeetClientHandler.getCameraRoll();
      if (!(Math.abs(roll) < 0.001F)) {
         this.rotation.rotateZ((float)Math.toRadians(roll));
      }
   }
}
