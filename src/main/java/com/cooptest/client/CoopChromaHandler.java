package com.cooptest.client;

public class CoopChromaHandler {
   private static final long DURATION_MS = 350L;
   private static boolean active = false;
   private static long startMs = 0L;

   public static void start() {
      active = true;
      startMs = System.currentTimeMillis();
   }

   public static void tick() {
      if (active && System.currentTimeMillis() - startMs > 350L) {
         active = false;
      }
   }

   public static boolean isActive() {
      return active;
   }
}
