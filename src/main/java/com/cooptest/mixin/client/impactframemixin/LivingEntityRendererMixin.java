package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopImpactHandler;
import com.cooptest.client.CoopImpactRenderType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<S extends LivingEntityRenderState> {
   @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
   private void coopSwapRenderLayer(S state, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderType> cir) {
      if (CoopImpactHandler.playing) {
         if (CoopImpactRenderType.isReady()) {
            Identifier texture = ((LivingEntityRenderer)this).getTextureLocation(state);
            switch (CoopImpactHandler.currentFrameType) {
               case WHITE:
               case RED:
                  cir.setReturnValue(CoopImpactRenderType.getBlackLayer(texture));
                  break;
               case BLACK:
               case CYAN:
                  cir.setReturnValue(CoopImpactRenderType.getWhiteLayer(texture));
                  break;
               case INVERT:
                  cir.setReturnValue(CoopImpactRenderType.getInvertLayer(texture));
            }
         }
      }
   }
}
