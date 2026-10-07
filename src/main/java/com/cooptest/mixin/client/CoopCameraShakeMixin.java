package com.cooptest.mixin.client;

import com.cooptest.client.CoopCameraShakeHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CoopCameraShakeMixin {
   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   public abstract float yRot();

   @Shadow
   public abstract float xRot();

   @Inject(method = "setup", at = @At("TAIL"))
   private void coopApplyShake(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
      if (CoopCameraShakeHandler.isActive()) {
         this.setRotation(this.yRot() + CoopCameraShakeHandler.yawOffset, this.xRot() + CoopCameraShakeHandler.pitchOffset);
      }
   }
}
