package com.cooptest.mixin.client;

import com.cooptest.bros.client.BrosClientHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class BrosLimbMixin {
   @Inject(method = "updateWalkAnimation", at = @At("HEAD"), cancellable = true)
   private void cooptest$brosLegs(float posDelta, CallbackInfo ci) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (self.level().isClientSide()) {
         if (BrosClientHandler.isLegDriven(self.getId())) {
            ci.cancel();
         }
      }
   }
}
