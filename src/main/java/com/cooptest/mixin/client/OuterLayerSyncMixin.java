package com.cooptest.mixin.client;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, priority = 2100)
public abstract class OuterLayerSyncMixin {
   @Inject(method = "setupAnim(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("RETURN"))
   private void coop$forceSyncOuterLayer(AvatarRenderState state, CallbackInfo ci) {
      PlayerModel model = (PlayerModel)this;
      model.jacket.loadPose(model.body.storePose());
      model.rightSleeve.loadPose(model.rightArm.storePose());
      model.leftSleeve.loadPose(model.leftArm.storePose());
      model.rightPants.loadPose(model.rightLeg.storePose());
      model.leftPants.loadPose(model.leftLeg.storePose());
      model.hat.loadPose(model.head.storePose());
   }
}
