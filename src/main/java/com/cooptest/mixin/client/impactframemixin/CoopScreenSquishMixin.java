package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopScreenSquishHandler;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class CoopScreenSquishMixin {
   @Inject(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V")
   )
   private void coopApplySquish(DeltaTracker tickCounter, CallbackInfo ci, @Local PoseStack matrixStack) {
      long startMs = CoopScreenSquishHandler.getStartMs();
      if (startMs >= 0L) {
         long elapsed = System.currentTimeMillis() - startMs;
         if (elapsed > 66L) {
            CoopScreenSquishHandler.reset();
         } else {
            float t = (float)elapsed / 66.0F;
            float sx = 1.0F + 0.08000004F * (1.0F - t);
            float sy = 1.0F + -0.06999999F * (1.0F - t);
            matrixStack.translate(0.5F, 0.5F, 0.0F);
            matrixStack.scale(sx, sy, 1.0F);
            matrixStack.translate(-0.5F, -0.5F, 0.0F);
         }
      }
   }
}
