package com.cooptest.mixin.client;

import com.cooptest.client.HighFivePassClientHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class HighFivePassCameraLockMixin {
   @Inject(method = "turnPlayer", at = @At("TAIL"))
   private void onUpdateMouseTail(CallbackInfo ci) {
      HighFivePassClientHandler.clampCameraYaw();
   }
}
