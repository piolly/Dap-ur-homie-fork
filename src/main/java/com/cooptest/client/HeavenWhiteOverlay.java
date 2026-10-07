package com.cooptest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundSource;

public class HeavenWhiteOverlay {
   private static boolean active = false;
   private static float opacity = 0.0F;
   private static long phaseStartTime = 0L;
   private static HeavenWhiteOverlay.HeavenPhase currentPhase = HeavenWhiteOverlay.HeavenPhase.NONE;
   private static float originalMasterVolume = 1.0F;
   private static float originalMusicVolume = 1.0F;
   private static boolean soundsMuted = false;

   public static void start() {
      active = true;
      opacity = 1.0F;
      currentPhase = HeavenWhiteOverlay.HeavenPhase.FULL_WHITE;
      phaseStartTime = System.currentTimeMillis();
      muteSounds();
   }

   public static void stop() {
      active = false;
      opacity = 0.0F;
      currentPhase = HeavenWhiteOverlay.HeavenPhase.NONE;
      unmuteSounds();
   }

   public static void render(GuiGraphicsExtractor context, float tickDelta) {
      if (active && !(opacity <= 0.0F)) {
         int alpha = (int)(opacity * 255.0F);
         int color = alpha << 24 | 16777215;
         int screenWidth = context.guiWidth();
         int screenHeight = context.guiHeight();
         context.fill(0, 0, screenWidth, screenHeight, color);
      }
   }

   public static void tick() {
      if (active) {
         long elapsed = System.currentTimeMillis() - phaseStartTime;
         switch (currentPhase) {
            case FULL_WHITE:
               opacity = 1.0F;
               if (elapsed >= 3000L) {
                  currentPhase = HeavenWhiteOverlay.HeavenPhase.FADE_TO_THIRTY;
                  phaseStartTime = System.currentTimeMillis();
                  System.out.println("[Heaven Overlay] Fading to 30%");
               }
               break;
            case FADE_TO_THIRTY:
               float progressxx = Math.min((float)elapsed / 500.0F, 1.0F);
               opacity = 1.0F - progressxx * 0.7F;
               if (progressxx >= 1.0F) {
                  opacity = 0.3F;
                  currentPhase = HeavenWhiteOverlay.HeavenPhase.HEAVEN;
                  phaseStartTime = System.currentTimeMillis();
               }
               break;
            case HEAVEN:
               opacity = 0.3F;
               if (elapsed >= 6000L) {
                  currentPhase = HeavenWhiteOverlay.HeavenPhase.FADE_OUT;
                  phaseStartTime = System.currentTimeMillis();
               }
               break;
            case FADE_OUT:
               float progressx = Math.min((float)elapsed / 2000.0F, 1.0F);
               opacity = 0.3F + progressx * 0.7F;
               if (progressx >= 1.0F) {
                  opacity = 1.0F;
                  currentPhase = HeavenWhiteOverlay.HeavenPhase.FADE_TO_NORMAL;
                  phaseStartTime = System.currentTimeMillis();
               }
               break;
            case FADE_TO_NORMAL:
               float progress = Math.min((float)elapsed / 5000.0F, 1.0F);
               opacity = 1.0F - progress;
               if (progress >= 1.0F) {
                  opacity = 0.0F;
                  currentPhase = HeavenWhiteOverlay.HeavenPhase.DONE;
               }
               break;
            case DONE:
               opacity = 0.0F;
         }
      }
   }

   private static void muteSounds() {
      if (!soundsMuted) {
         Minecraft client = Minecraft.getInstance();
         if (client.options != null && client.getSoundManager() != null) {
            originalMasterVolume = ((Double)client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get()).floatValue();
            originalMusicVolume = ((Double)client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get()).floatValue();
            client.getSoundManager().stop();
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            soundsMuted = true;
         }
      }
   }

   private static void unmuteSounds() {
      if (soundsMuted) {
         Minecraft client = Minecraft.getInstance();
         if (client.options != null) {
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double)originalMasterVolume);
            client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set((double)originalMusicVolume);
            soundsMuted = false;
         }
      }
   }

   public static boolean isActive() {
      return active;
   }

   public static HeavenWhiteOverlay.HeavenPhase getCurrentPhase() {
      return currentPhase;
   }

   public static float getOpacity() {
      return opacity;
   }

   public enum HeavenPhase {
      NONE,
      FULL_WHITE,
      FADE_TO_THIRTY,
      HEAVEN,
      FADE_OUT,
      FADE_TO_NORMAL,
      DONE;
   }
}
