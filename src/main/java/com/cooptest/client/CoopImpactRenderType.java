package com.cooptest.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class CoopImpactRenderType {
   private static final RenderPipeline ENTITY_WHITE = pipeline("cooptest_entity_white");
   private static final RenderPipeline ENTITY_BLACK = pipeline("cooptest_entity_black");
   private static final RenderPipeline ENTITY_INVERT = pipeline("cooptest_entity_invert");
   private static final Map<String, RenderType> LAYER_CACHE = new ConcurrentHashMap<>();

   private static RenderPipeline pipeline(String name) {
      return RenderPipelines.register(
         RenderPipeline.builder(new Snippet[]{RenderPipelines.ENTITY_SNIPPET})
            .withLocation(Identifier.fromNamespaceAndPath("cooptest", "pipeline/" + name))
            .withVertexShader(Identifier.fromNamespaceAndPath("cooptest", "core/" + name))
            .withFragmentShader(Identifier.fromNamespaceAndPath("cooptest", "core/" + name))
            .build()
      );
   }

   public static boolean isReady() {
      return true;
   }

   public static RenderType getWhiteLayer(Identifier texture) {
      return buildLayer("cooptest_entity_white", ENTITY_WHITE, texture);
   }

   public static RenderType getBlackLayer(Identifier texture) {
      return buildLayer("cooptest_entity_black", ENTITY_BLACK, texture);
   }

   public static RenderType getInvertLayer(Identifier texture) {
      return buildLayer("cooptest_entity_invert", ENTITY_INVERT, texture);
   }

   private static RenderType buildLayer(String name, RenderPipeline pipeline, Identifier texture) {
      return LAYER_CACHE.computeIfAbsent(
         name + "/" + texture,
         key -> RenderType.create(
            name, RenderSetup.builder(pipeline).withTexture("Sampler0", texture).useLightmap().useOverlay().bufferSize(1536).createRenderSetup()
         )
      );
   }
}
