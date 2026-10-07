package com.cooptest.mixin.client;

import com.cooptest.ArmPoseTracker;
import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {
   @Unique
   private static final float READY_UP = 1.2F;
   @Unique
   private static final float READY_FORWARD = 0.8F;
   @Unique
   private static final float READY_PITCH = -85.0F;
   @Unique
   private static final float HOLD_UP = 1.5F;
   @Unique
   private static final float HOLD_FORWARD = 0.3F;
   @Unique
   private static final float HOLD_PITCH = -95.0F;
   @Unique
   private static final float CHARGE_UP = 0.8F;
   @Unique
   private static final float CHARGE_FORWARD = -0.5F;
   @Unique
   private static final float CHARGE_PITCH = -60.0F;
   @Unique
   private static final float CHARGE_SHAKE = 0.08F;
   @Unique
   private static final float THROW_DURATION = 300.0F;
   @Unique
   private static final float PUSH_IDLE_UP = 0.4F;
   @Unique
   private static final float PUSH_IDLE_FORWARD = 0.5F;
   @Unique
   private static final float PUSH_IDLE_PITCH = -50.0F;
   @Unique
   private static final float PUSH_ACTION_UP = 0.6F;
   @Unique
   private static final float PUSH_ACTION_FORWARD = 1.0F;
   @Unique
   private static final float PUSH_ACTION_PITCH = -80.0F;
   @Unique
   private static final float LERP_SPEED = 0.25F;
   @Unique
   private static final float FAST_LERP = 0.4F;
   @Unique
   private static float currUp = 0.0F;
   @Unique
   private static float currForward = 0.0F;
   @Unique
   private static float currPitch = 0.0F;
   @Unique
   private static long throwStartTime = 0L;
   @Unique
   private static boolean wasHolding = false;

   @Inject(method = "renderMapHand", at = @At("HEAD"))
   private void onRenderArm(PoseStack matrices, SubmitNodeCollector queue, int light, HumanoidArm arm, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         boolean handsEmpty = client.player.getMainHandItem().isEmpty() && client.player.getOffhandItem().isEmpty();
         UUID playerId = client.player.getUUID();
         PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
         boolean isHolding = pose == PoseState.GRAB_HOLDING;
         if (wasHolding && !isHolding && pose == PoseState.GRAB_READY) {
            throwStartTime = System.currentTimeMillis();
         }

         wasHolding = isHolding;
         Long trackerThrowStart = ArmPoseTracker.throwAnimationStart.get(playerId);
         if (trackerThrowStart != null) {
            throwStartTime = trackerThrowStart;
         }

         float targetUp = 0.0F;
         float targetForward = 0.0F;
         float targetPitch = 0.0F;
         float lerpSpeed = 0.25F;
         float shakeAmount = 0.0F;
         boolean inThrowAnim = false;
         float throwProgress = 0.0F;
         if (throwStartTime > 0L) {
            long elapsed = System.currentTimeMillis() - throwStartTime;
            if ((float)elapsed < 300.0F) {
               inThrowAnim = true;
               throwProgress = (float)elapsed / 300.0F;
            } else {
               throwStartTime = 0L;
            }
         }

         if (inThrowAnim) {
            lerpSpeed = 0.4F;
            if (throwProgress < 0.3F) {
               float p = throwProgress / 0.3F;
               targetUp = lerp(1.5F, 0.3F, p);
               targetForward = lerp(0.3F, 1.2F, p);
               targetPitch = lerp(-95.0F, -100.0F, p);
            } else {
               float p = (throwProgress - 0.3F) / 0.7F;
               targetUp = lerp(0.3F, 0.0F, p);
               targetForward = lerp(1.2F, 0.0F, p);
               targetPitch = lerp(-100.0F, 0.0F, p);
            }
         } else if (handsEmpty && pose != PoseState.NONE && pose != PoseState.GRABBED) {
            float charge = GrabInputHandler.getThrowChargeProgress();
            boolean isCharging = charge >= 0.0F;
            switch (pose) {
               case GRAB_READY:
                  targetUp = 1.2F;
                  targetForward = 0.8F;
                  targetPitch = -85.0F;
                  break;
               case GRAB_HOLDING:
                  if (isCharging) {
                     targetUp = lerp(1.5F, 0.8F, charge);
                     targetForward = lerp(0.3F, -0.5F, charge);
                     targetPitch = lerp(-95.0F, -60.0F, charge);
                     if (charge > 0.5F) {
                        shakeAmount = 0.08F * (charge - 0.5F) * 2.0F;
                     }
                  } else {
                     targetUp = 1.5F;
                     targetForward = 0.3F;
                     targetPitch = -95.0F;
                  }
                  break;
               case PUSH_IDLE:
               case PUSH_RETURN:
                  targetUp = 0.4F;
                  targetForward = 0.5F;
                  targetPitch = -50.0F;
                  break;
               case PUSH_ACTION:
                  targetUp = 0.6F;
                  targetForward = 1.0F;
                  targetPitch = -80.0F;
                  lerpSpeed = 0.4F;
            }
         }

         currUp = lerp(currUp, targetUp, lerpSpeed);
         currForward = lerp(currForward, targetForward, lerpSpeed);
         currPitch = lerp(currPitch, targetPitch, lerpSpeed);
         float shakeOffset = 0.0F;
         if (shakeAmount > 0.0F) {
            shakeOffset = (float)(Math.random() - 0.5) * shakeAmount;
         }

         if (!(Math.abs(currPitch) < 1.0F) || !(Math.abs(currUp) < 0.01F) || !(Math.abs(currForward) < 0.01F)) {
            matrices.translate(0.0, currUp + shakeOffset, -currForward + shakeOffset * 0.5F);
            matrices.mulPose(Axis.XP.rotationDegrees(currPitch + shakeOffset * 20.0F));
         }
      }
   }

   @Unique
   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }
}
