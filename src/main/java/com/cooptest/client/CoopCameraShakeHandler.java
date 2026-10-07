package com.cooptest.client;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

public class CoopCameraShakeHandler {
   private static final ImprovedNoise NOISE = new ImprovedNoise(RandomSource.create());
   private static final float AMPLITUDE = 4.0F;
   private static final float SHAKE_SPEED = 0.7F;
   private static float trauma = 0.0F;
   private static float noiseY = 0.0F;
   private static long endAtMs = -1L;
   public static float pitchOffset = 0.0F;
   public static float yawOffset = 0.0F;
   public static float rollOffset = 0.0F;

   public static void shake(float amount, long durationMs) {
      if (CoopClientSettings.get().cameraShakeEnabled) {
         trauma = Math.min(3.0F, amount);
         endAtMs = System.currentTimeMillis() + durationMs;
         float t2 = trauma * trauma;
         pitchOffset = (float)(4.0F * t2 * NOISE.noise(3.0, noiseY, 0.0));
         yawOffset = (float)(4.0F * t2 * NOISE.noise(25.0, noiseY, 0.0));
         rollOffset = (float)(4.0F * t2 * NOISE.noise(75.0, noiseY, 0.0));
      }
   }

   public static void tick() {
      if (!CoopClientSettings.get().cameraShakeEnabled && trauma > 0.0F) {
         trauma = 0.0F;
         pitchOffset = 0.0F;
         yawOffset = 0.0F;
         rollOffset = 0.0F;
         endAtMs = -1L;
      } else if (endAtMs >= 0L && System.currentTimeMillis() >= endAtMs) {
         trauma = 0.0F;
         pitchOffset = 0.0F;
         yawOffset = 0.0F;
         rollOffset = 0.0F;
         endAtMs = -1L;
      } else if (trauma <= 0.0F) {
         pitchOffset = 0.0F;
         yawOffset = 0.0F;
         rollOffset = 0.0F;
      } else {
         noiseY += 0.7F;
         if (noiseY > 1000.0F) {
            noiseY = 0.0F;
         }

         float t2 = trauma * trauma;
         pitchOffset = (float)(4.0F * t2 * NOISE.noise(3.0, noiseY, 0.0));
         yawOffset = (float)(4.0F * t2 * NOISE.noise(25.0, noiseY, 0.0));
         rollOffset = (float)(4.0F * t2 * NOISE.noise(75.0, noiseY, 0.0));
      }
   }

   public static boolean isActive() {
      return trauma > 0.0F && endAtMs >= 0L;
   }
}
