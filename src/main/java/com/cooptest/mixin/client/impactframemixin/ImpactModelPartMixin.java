package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopImpactHandler;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelPart.class)
public abstract class ImpactModelPartMixin {
   private boolean shouldFlash() {
      return CoopImpactHandler.playing && CoopImpactHandler.isPlayerPart((ModelPart)this);
   }

   @ModifyVariable(
      method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int forceLight(int light) {
      return this.shouldFlash() ? 15728880 : light;
   }

   @ModifyVariable(
      method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private int forceOverlay(int overlay) {
      if (!this.shouldFlash()) {
         return overlay;
      }

      return switch (CoopImpactHandler.currentFrameType) {
         case BLACK, CYAN -> 0;
         default -> OverlayTexture.NO_OVERLAY;
      };
   }

   @ModifyVariable(
      method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private VertexConsumer wrapConsumer(VertexConsumer original) {
      return !this.shouldFlash() ? original : new VertexConsumer() {
         public VertexConsumer addVertex(float x, float y, float z) {
            original.addVertex(x, y, z);
            return this;
         }

         public VertexConsumer setColor(int r, int g, int b, int a) {
            return switch (CoopImpactHandler.currentFrameType) {
               case BLACK, CYAN -> original.setColor(255, 255, 255, 255);
               case WHITE, RED -> original.setColor(0, 0, 0, 255);
               case INVERT -> original.setColor(255 - r, 255 - g, 255 - b, a);
            };
         }

         public VertexConsumer setColor(int argb) {
            int a = argb >> 24 & 0xFF;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            return this.setColor(r, g, b, a);
         }

         public VertexConsumer setUv(float u, float v) {
            original.setUv(u, v);
            return this;
         }

         public VertexConsumer setUv1(int u, int v) {
            original.setUv1(u, v);
            return this;
         }

         public VertexConsumer setUv2(int u, int v) {
            original.setUv2(240, 240);
            return this;
         }

         public VertexConsumer setNormal(float x, float y, float z) {
            original.setNormal(x, y, z);
            return this;
         }

         public VertexConsumer setLineWidth(float width) {
            original.setLineWidth(width);
            return this;
         }

         public VertexConsumer addVertexWith2DPose(Matrix3x2fc matrix, float x, float y) {
            original.addVertexWith2DPose(matrix, x, y);
            return this;
         }
      };
   }
}
