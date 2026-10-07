package com.cooptest.mixin.client;

import com.cooptest.client.CoopCameraShakeHandler;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class CoopCameraRollMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V")
   )
   private void coopApplyRoll(CallbackInfo ci, @Local PoseStack matrixStack) {
      if (CoopCameraShakeHandler.isActive()) {
         if (this.minecraft.player != null) {
            matrixStack.rotate(Axis.ZP.rotationDegrees(CoopCameraShakeHandler.rollOffset));
         }
      }
   }
}
