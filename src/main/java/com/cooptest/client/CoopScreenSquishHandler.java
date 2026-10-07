package com.cooptest.client;

public class CoopScreenSquishHandler {
   public static final long SQUISH_MS = 66L;
   public static final float SQUISH_X = 1.08F;
   public static final float SQUISH_Y = 0.93F;
   private static long squishStartMs = -1L;

   public static void trigger() {
      squishStartMs = System.currentTimeMillis();
   }

   public static long getStartMs() {
      return squishStartMs;
   }

   public static void reset() {
      squishStartMs = -1L;
   }
}
