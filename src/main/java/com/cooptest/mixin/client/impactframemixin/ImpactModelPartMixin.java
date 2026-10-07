package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopImpactHandler;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelPart.class)
public abstract class ImpactModelPartMixin {
// WASTED 7 HR ON THIS DOSNT WORK AS INTNEDE
    private static boolean shouldFlash() {
        return CoopImpactHandler.playing && CoopImpactHandler.renderingPlayer;
    }

    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0  // light
    )
    private int forceLight(int light) {
        return shouldFlash() ? 15728880 : light;
    }

    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1  // overlay
    )
    private int forceOverlay(int overlay) {
        if (!shouldFlash()) return overlay;
        return CoopImpactHandler.whiteFrame ? OverlayTexture.NO_OVERLAY : 0;
    }

    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private VertexConsumer wrapConsumer(VertexConsumer original) {
        if (!shouldFlash()) return original;
        final boolean white = CoopImpactHandler.whiteFrame;

        return new VertexConsumer() {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                return original.addVertex(x, y, z);
            }
            @Override
            public VertexConsumer setColor(int argb) {
                return white ? original.setColor(0xFF000000) : original.setColor(0xFFFFFFFF);
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                return null;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                return null;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                return null;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                return null;
            }

            @Override
            public VertexConsumer setColor(int r, int g, int b, int a) {
                return white ? original.setColor(0, 0, 0, 255) : original.setColor(255, 255, 255, 255);
            }
            @Override
            public VertexConsumer setLineWidth(float width) {
                return original.setLineWidth(width);
            }
        };
    }
}