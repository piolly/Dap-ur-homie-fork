package com.cooptest.client;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class ImpactFrameOverlay {
   private static final Identifier[] FRAMES = new Identifier[]{
      Identifier.fromNamespaceAndPath("testcoop", "textures/impact/frame1.png"), Identifier.fromNamespaceAndPath("testcoop", "textures/impact/frame2.png")
   };
   private static final RenderPipeline SKETCH_MULTIPLY = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.GUI_TEXTURED_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("cooptest", "pipeline/sketch_multiply"))
         .withBlend(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ZERO, SourceFactor.ONE, DestFactor.ZERO))
         .build()
   );
   private static final RenderPipeline SKETCH_ADDITIVE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.GUI_TEXTURED_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("cooptest", "pipeline/sketch_additive"))
         .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO))
         .build()
   );
   private static final Random RANDOM = new Random();
   private static int currentFrame = 0;
   private static int lastFlipFrame = -1;

   public static void render(GuiGraphicsExtractor context) {
      if (CoopClientSettings.get().impactFramesEnabled) {
         if (CoopImpactHandler.playing) {
            int frameIdx = (int)((System.currentTimeMillis() - CoopImpactHandler.getStartMs()) / 33L);
            if (frameIdx != lastFlipFrame) {
               lastFlipFrame = frameIdx;
               currentFrame = RANDOM.nextInt(FRAMES.length);
            }

            int w = context.guiWidth();
            int h = context.guiHeight();
            Identifier tex = FRAMES[currentFrame % FRAMES.length];
            RenderPipeline pipeline = CoopImpactHandler.whiteFrame ? SKETCH_MULTIPLY : SKETCH_ADDITIVE;
            context.blit(pipeline, tex, 0, 0, 0.0F, 0.0F, w, h, w, h);
         }
      }
   }
}
