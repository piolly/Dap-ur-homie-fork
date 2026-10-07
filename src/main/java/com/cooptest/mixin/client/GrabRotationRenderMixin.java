package com.cooptest.mixin.client;

import com.cooptest.client.CoopRenderStateData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class GrabRotationRenderMixin {
   @Unique
   private static final HashMap<UUID, Boolean> coop$matrixPushed = new HashMap<>();

   @Inject(
      method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at = @At("HEAD")
   )
   private void coop$rotateGrabbedPlayer(
      LivingEntityRenderState baseState, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci
   ) {
      if (baseState instanceof AvatarRenderState state) {
         CoopRenderStateData.PlayerSnapshot snap = (CoopRenderStateData.PlayerSnapshot)state.getData(CoopRenderStateData.PLAYER_SNAPSHOT);
         if (snap != null) {
            Float facingYaw = (Float)state.getData(CoopRenderStateData.GRAB_FACING_YAW);
            if (facingYaw != null) {
               matrices.pushPose();
               float counterRotation = -state.bodyRot + facingYaw;
               matrices.rotate(Axis.YP.rotationDegrees(counterRotation));
               matrices.translate(0.0, 0.9, 0.0);
               matrices.rotate(Axis.XP.rotationDegrees(90.0F));
               matrices.translate(0.0, -0.9, 0.0);
               coop$matrixPushed.put(snap.uuid(), true);
            } else {
               coop$matrixPushed.put(snap.uuid(), false);
            }
         }
      }
   }

   @Inject(
      method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at = @At("RETURN")
   )
   private void coop$restoreMatrix(
      LivingEntityRenderState baseState, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci
   ) {
      if (baseState instanceof AvatarRenderState state) {
         CoopRenderStateData.PlayerSnapshot snap = (CoopRenderStateData.PlayerSnapshot)state.getData(CoopRenderStateData.PLAYER_SNAPSHOT);
         if (snap != null) {
            Boolean pushed = coop$matrixPushed.get(snap.uuid());
            if (pushed != null && pushed) {
               matrices.popPose();
               coop$matrixPushed.put(snap.uuid(), false);
            }
         }
      }
   }
}
