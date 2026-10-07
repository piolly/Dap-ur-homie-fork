package com.cooptest.mixin.client;

import com.cooptest.bros.client.BrosClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class BrosCameraLockMixin {
   @Shadow
   private double accumulatedDX;

   @Inject(method = "turnPlayer", at = @At("HEAD"))
   private void cooptest$brosTurnVote(CallbackInfo ci) {
      if (BrosClientHandler.isEngaged()) {
         try {
            Minecraft mc = Minecraft.getInstance();
            double s = (Double)mc.options.sensitivity().get() * 0.6 + 0.2;
            double deg = this.accumulatedDX * s * s * s * 8.0 * 0.15;
            BrosClientHandler.addTurnVote(deg);
         } catch (Throwable var7) {
         }

         this.accumulatedDX = 0.0;
      }
   }

   @Inject(method = "turnPlayer", at = @At("TAIL"))
   private void cooptest$brosLockYaw(CallbackInfo ci) {
      try {
         BrosClientHandler.applyLocalYaw();
      } catch (Throwable var3) {
      }
   }
}
