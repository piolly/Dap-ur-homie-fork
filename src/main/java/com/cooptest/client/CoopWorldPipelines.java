package com.cooptest.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class CoopWorldPipelines {
   public static final RenderPipeline TRANSLUCENT_QUADS_NO_CULL = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.DEBUG_FILLED_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("cooptest", "pipeline/translucent_quads_no_cull"))
         .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.QUADS)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderType TRAJECTORY_LAYER = RenderType.create(
      "cooptest_trajectory", RenderSetup.builder(TRANSLUCENT_QUADS_NO_CULL).bufferSize(1536).createRenderSetup()
   );

   private CoopWorldPipelines() {
   }
}
