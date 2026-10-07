package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopImpactHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Environment(EnvType.CLIENT)
    @Mixin(LivingEntityRenderer.class)
    public class PlayerRenderFlagMixin {
        @Inject(
                method = "submit",
                at = @At("HEAD")
        )
        private void onRenderStart(EntityRenderState state, PoseStack matrices,
                                   MultiBufferSource provider, int light, CallbackInfo ci) {
            if (state instanceof AvatarRenderState) {
                CoopImpactHandler.renderingPlayer = true;
            }
        }

        @Inject(
                method = "submit",
                at = @At("RETURN")
        )
        private void onRenderEnd(EntityRenderState state, PoseStack matrices,
                                 MultiBufferSource provider, int light, CallbackInfo ci) {
            if (state instanceof AvatarRenderState) {
                CoopImpactHandler.renderingPlayer = false;
            }
        }
    }
