package com.cooptest.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class CoopImpactRenderType {

    public static boolean isReady() { return true; } // no shader loading needed

    public static RenderType getWhiteLayer(Identifier texture) {
        return RenderType.create("cooptest_entity_white",
                RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).createRenderSetup());
    }

    public static RenderType getBlackLayer(Identifier texture) {
        return RenderType.create("cooptest_entity_black",
                RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).createRenderSetup());
    }

    public static net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener createReloadListener() {
        return new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath("cooptest", "impact_shaders");
            }
            @Override
            public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {}
        };
    }
}

// note! this will most likely look wrong because of DEBUG_FILLED_BOX used as a stand-in