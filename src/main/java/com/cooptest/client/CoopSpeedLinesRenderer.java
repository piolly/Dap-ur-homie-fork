package com.cooptest.client;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

public class CoopSpeedLinesRenderer {
   private static final int LINE_COUNT = 80;
   private static final float MIN_LENGTH = 0.08F;
   private static final float MAX_LENGTH = 0.35F;
   private static final float LINE_WIDTH = 1.5F;
   private static final long DURATION_MS = 300L;
   private static final Random RANDOM = new Random();
   private static final float[] ANGLES = new float[80];
   private static final float[] LENGTHS = new float[80];
   private static final float[] OFFSETS = new float[80];
   private static boolean linesGenerated = false;
   private static boolean active = false;
   private static long startMs = 0L;

   public static void start() {
      active = true;
      startMs = System.currentTimeMillis();
      generateLines();
   }

   private static void generateLines() {
      for (int i = 0; i < 80; i++) {
         ANGLES[i] = RANDOM.nextFloat() * 360.0F;
         LENGTHS[i] = 0.08F + RANDOM.nextFloat() * 0.26999998F;
         OFFSETS[i] = 0.15F + RANDOM.nextFloat() * 0.25F;
      }

      linesGenerated = true;
   }

   public static void tick() {
      if (active && System.currentTimeMillis() - startMs > 300L) {
         active = false;
      }
   }

   public static void render(GuiGraphics context) {
      if (active) {
         long elapsed = System.currentTimeMillis() - startMs;
         float progress = (float)elapsed / 300.0F;
         float alpha = (1.0F - progress) * 0.85F;
         int w = context.guiWidth();
         int h = context.guiHeight();
         float cx = w * 0.5F;
         float cy = h * 0.5F;
         float diag = (float)Math.sqrt(w * w + h * h) * 0.5F;
         float r = 1.0F;
         float g = 1.0F;
         float b = 1.0F;
         if (CoopImpactHandler.playing) {
            switch (CoopImpactHandler.currentFrameType) {
               case WHITE:
               case RED:
                  r = 0.0F;
                  g = 0.0F;
                  b = 0.0F;
                  break;
               case BLACK:
               case CYAN:
                  r = 1.0F;
                  g = 1.0F;
                  b = 1.0F;
                  break;
               case INVERT:
                  r = 1.0F;
                  g = 1.0F;
                  b = 0.0F;
            }
         }

         int baseColor = colorArgb(r, g, b, alpha);
         int tipColor = colorArgb(r, g, b, 0.0F);
         Matrix3x2fStack matrices = context.pose();
         int halfWidth = Math.max(1, Math.round(0.75F));

         for (int i = 0; i < 80; i++) {
            float startDist = OFFSETS[i] * diag;
            float endDist = (OFFSETS[i] + LENGTHS[i]) * diag;
            float animStart = startDist + progress * diag * 0.3F;
            float animEnd = endDist + progress * diag * 0.3F;
            matrices.pushMatrix();
            matrices.translate(cx, cy);
            matrices.rotate((float)Math.toRadians(ANGLES[i] - 90.0F));
            context.fillGradient(-halfWidth, Math.round(animStart), halfWidth, Math.round(animEnd), baseColor, tipColor);
            matrices.popMatrix();
         }
      }
   }

   private static int colorArgb(float r, float g, float b, float a) {
      int ai = (int)(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F);
      int ri = (int)(r * 255.0F);
      int gi = (int)(g * 255.0F);
      int bi = (int)(b * 255.0F);
      return ai << 24 | ri << 16 | gi << 8 | bi;
   }

   public static boolean isActive() {
      return active;
   }
}
