package com.cooptest.client;

import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class GrabClientEffects {
   private static final float CHARGE_SOUND_PITCH_MIN = 0.5F;
   private static final float CHARGE_SOUND_PITCH_MAX = 2.0F;
   private static final int CHARGE_SOUND_INTERVAL = 4;
   private static final float CHARGE_SHAKE_INTENSITY = 0.15F;
   private static final float HELD_SHAKE_INTENSITY = 0.3F;
   private static final float LANDING_SHAKE_INTENSITY = 1.5F;
   private static final long LANDING_SHAKE_DURATION_MS = 2000L;
   private static int chargeSoundTicks = 0;
   private static boolean wasFullyCharged = false;
   private static boolean wasBeingHeld = false;
   private static boolean wasGrabbed = false;
   private static long landingShakeStartTime = 0L;
   private static boolean isLandingShaking = false;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.level != null) {
            LocalPlayer player = client.player;
            UUID playerId = player.getUUID();
            PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
            float chargeProgress = GrabInputHandler.getThrowChargeProgress();
            boolean isCharging = chargeProgress >= 0.0F;
            if (isCharging && pose == PoseState.GRAB_HOLDING) {
               chargeSoundTicks++;
               if (chargeSoundTicks >= 4) {
                  float pitch = 0.5F + 1.5F * chargeProgress;
                  player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_BASS.value(), 0.3F, pitch);
                  chargeSoundTicks = 0;
               }

               if (chargeProgress >= 0.99F && !wasFullyCharged) {
                  player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 2.0F);
                  player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
                  wasFullyCharged = true;
               }

               if (chargeProgress >= 0.99F) {
                  float shakeX = (float)(Math.random() - 0.5) * 0.15F;
                  float shakeY = (float)(Math.random() - 0.5) * 0.15F;
                  player.setXRot(player.getXRot() + shakeX);
                  player.setYRot(player.getYRot() + shakeY);
               }
            } else {
               chargeSoundTicks = 0;
               wasFullyCharged = false;
            }

            boolean isBeingHeld = pose == PoseState.GRABBED && player.isPassenger();
            if (isBeingHeld && !wasBeingHeld) {
               player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
            }

            if (isBeingHeld && player.getVehicle() != null) {
               UUID holderId = player.getVehicle().getUUID();
               float holderCharge = PoseNetworking.chargeProgress.getOrDefault(holderId, -1.0F);
               if (holderCharge >= 0.99F) {
                  float shakeX = (float)(Math.random() - 0.5) * 0.3F;
                  float shakeY = (float)(Math.random() - 0.5) * 0.3F;
                  player.setXRot(player.getXRot() + shakeX);
                  player.setYRot(player.getYRot() + shakeY);
               }
            }

            wasBeingHeld = isBeingHeld;
            boolean wasInGrabbedPose = wasGrabbed;
            boolean nowInNormalPose = pose == PoseState.NONE;
            if (wasInGrabbedPose && nowInNormalPose && !player.isPassenger()) {
               isLandingShaking = true;
               landingShakeStartTime = System.currentTimeMillis();
               player.playSound(SoundEvents.PLAYER_BIG_FALL, 1.0F, 1.0F);
            }

            wasGrabbed = pose == PoseState.GRABBED;
            if (isLandingShaking) {
               long elapsed = System.currentTimeMillis() - landingShakeStartTime;
               if (elapsed < 2000L) {
                  float fadeProgress = 1.0F - (float)elapsed / 2000.0F;
                  float intensity = 1.5F * fadeProgress;
                  float shakeX = (float)(Math.random() - 0.5) * intensity;
                  float shakeY = (float)(Math.random() - 0.5) * intensity;
                  player.setXRot(player.getXRot() + shakeX);
                  player.setYRot(player.getYRot() + shakeY);
               } else {
                  isLandingShaking = false;
               }
            }
         }
      });
   }
}
