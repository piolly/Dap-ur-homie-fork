package com.cooptest.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

public class CoopImpactHandler {
   public static final CoopImpactHandler.FrameType[] REGULAR_DAP_SEQUENCE = new CoopImpactHandler.FrameType[]{
      CoopImpactHandler.FrameType.WHITE, CoopImpactHandler.FrameType.BLACK, CoopImpactHandler.FrameType.WHITE
   };
   public static final CoopImpactHandler.FrameType[] PERFECT_DAP_SEQUENCE = new CoopImpactHandler.FrameType[]{
      CoopImpactHandler.FrameType.WHITE,
      CoopImpactHandler.FrameType.BLACK,
      CoopImpactHandler.FrameType.INVERT,
      CoopImpactHandler.FrameType.WHITE,
      CoopImpactHandler.FrameType.INVERT,
      CoopImpactHandler.FrameType.BLACK,
      CoopImpactHandler.FrameType.WHITE
   };
   public static final CoopImpactHandler.FrameType[] HEAVEN_DAP_SEQUENCE = new CoopImpactHandler.FrameType[]{
      CoopImpactHandler.FrameType.WHITE,
      CoopImpactHandler.FrameType.RED,
      CoopImpactHandler.FrameType.CYAN,
      CoopImpactHandler.FrameType.RED,
      CoopImpactHandler.FrameType.WHITE,
      CoopImpactHandler.FrameType.RED,
      CoopImpactHandler.FrameType.CYAN,
      CoopImpactHandler.FrameType.INVERT,
      CoopImpactHandler.FrameType.WHITE,
      CoopImpactHandler.FrameType.RED,
      CoopImpactHandler.FrameType.CYAN,
      CoopImpactHandler.FrameType.INVERT
   };
   public static volatile boolean playing = false;
   public static volatile boolean renderingPlayer = false;
   public static volatile boolean suppressOverlay = false;
   public static volatile CoopImpactHandler.FrameType currentFrameType = CoopImpactHandler.FrameType.WHITE;
   public static volatile boolean whiteFrame = true;
   private static final Set<ModelPart> playerParts = Collections.newSetFromMap(new IdentityHashMap<>());
   private static final Set<Object> registeredModels = Collections.newSetFromMap(new IdentityHashMap<>());
   private static CoopImpactHandler.FrameType[] sequence = REGULAR_DAP_SEQUENCE;
   private static long startMs = 0L;
   private static long frameDurationMs = 33L;

   public static void registerPlayerModel(EntityModel<?> model) {
      if (model != null && registeredModels.add(model)) {
         collectParts(model.root());
      }
   }

   private static void collectParts(ModelPart part) {
      if (part != null && !playerParts.contains(part)) {
         playerParts.add(part);

         for (ModelPart child : part.getAllParts()) {
            playerParts.add(child);
         }
      }
   }

   public static boolean isPlayerPart(ModelPart part) {
      return playerParts.contains(part);
   }

   public static void start(CoopImpactHandler.FrameType[] seq, long durationEach, boolean suppress) {
      sequence = seq;
      frameDurationMs = durationEach;
      startMs = System.currentTimeMillis();
      currentFrameType = seq[0];
      whiteFrame = seq[0] == CoopImpactHandler.FrameType.WHITE || seq[0] == CoopImpactHandler.FrameType.RED;
      playing = true;
      suppressOverlay = suppress;
   }

   public static void start(int frames, long durationEach, boolean suppress) {
      CoopImpactHandler.FrameType[] seq = new CoopImpactHandler.FrameType[frames];

      for (int i = 0; i < frames; i++) {
         seq[i] = i % 2 == 0 ? CoopImpactHandler.FrameType.WHITE : CoopImpactHandler.FrameType.BLACK;
      }

      start(seq, durationEach, suppress);
   }

   public static void start(int frames, long durationEach) {
      start(frames, durationEach, false);
   }

   public static void tick() {
      if (playing) {
         long elapsed = System.currentTimeMillis() - startMs;
         int frameIdx = (int)(elapsed / frameDurationMs);
         if (frameIdx >= sequence.length) {
            playing = false;
            currentFrameType = CoopImpactHandler.FrameType.WHITE;
            whiteFrame = true;
         } else {
            currentFrameType = sequence[frameIdx];
            whiteFrame = currentFrameType == CoopImpactHandler.FrameType.WHITE || currentFrameType == CoopImpactHandler.FrameType.RED;
         }
      }
   }

   public static long getStartMs() {
      return startMs;
   }

   public enum FrameType {
      WHITE,
      BLACK,
      RED,
      CYAN,
      INVERT;
   }
}
