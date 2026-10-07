package com.cooptest.client;

public class CoopRadialBlurHandler {
   private static final long DURATION_MS = 200L;
   private static boolean active = false;
   private static long startMs = 0L;

   public static void start() {
      active = true;
      startMs = System.currentTimeMillis();
   }

   public static void tick() {
      if (active && System.currentTimeMillis() - startMs > 200L) {
         active = false;
      }
   }

   public static boolean isActive() {
      return active;
   }
}
