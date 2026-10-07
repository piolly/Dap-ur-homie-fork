package com.cooptest.client;

import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.mojang.blaze3d.buffers.*;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public class TrajectoryRenderer {

    private static final int TRAJECTORY_POINTS = 30;
    private static final float TIME_STEP = 0.1f;
    private static final float GRAVITY = 0.08f;
    private static final float DRAG = 0.02f;

    private static final float DOT_SIZE = 0.08f;
    private static final float MIN_POWER_MULT = 1.5f;
    private static final float MAX_POWER_MULT = 3.5f;
    // BufferAllocator is the correct allocator type — takes a byte size
    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(786432);

    // DEBUG_FILLED_BOX already has blending and no cull — use it directly.
// POSITION_COLOR_SNIPPET is private so you can't build on top of it.
    private static final RenderPipeline TRAJECTORY_PIPELINE = RenderPipelines.DEBUG_FILLED_BOX;
    public static void register() {
        WorldRenderEvents.END_MAIN.register(TrajectoryRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        PoseState pose = PoseNetworking.poseStates.getOrDefault(
            client.player.getUUID(), PoseState.NONE
        );

        if (pose != PoseState.GRAB_HOLDING) return;

        float chargeProgress = GrabInputHandler.getThrowChargeProgress();
        if (chargeProgress <= 0) return;

        float power = MIN_POWER_MULT + (MAX_POWER_MULT - MIN_POWER_MULT) * chargeProgress;

        Vec3 lookVec = client.player.getViewVector(client.getDeltaTracker().getGameTimeDeltaTicks());
        Vec3 startPos = client.player.getEyePosition().add(0, 0.5, 0); // Above head

        Vec3 velocity = lookVec.scale(power);

        Vec3[] points = new Vec3[TRAJECTORY_POINTS];
        Vec3 pos = startPos;
        Vec3 vel = velocity;

        for (int i = 0; i < TRAJECTORY_POINTS; i++) {
            points[i] = pos;

            vel = vel.add(0, -GRAVITY, 0);
            vel = vel.scale(1.0 - DRAG);
            pos = pos.add(vel);

            if (pos.y < client.player.getY() - 10) break;
        }

        renderTrajectoryDots(context, points, chargeProgress);
    }

    private static void renderTrajectoryDots(WorldRenderContext context, Vec3[] points, float charge) {
        Camera camera = context.gameRenderer().getMainCamera();
        Vec3 camPos = camera.position();

        PoseStack matrices = context.matrices();
        matrices.pushPose();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = matrices.last().pose();

        VertexConsumer consumer = context.consumers().getBuffer(
                RenderType.create("trajectory", RenderSetup.builder(
                        RenderPipelines.DEBUG_FILLED_BOX
                ).createRenderSetup())
        );

        int r = (int)(charge * 255);
        int g = (int)((1 - charge) * 255);
        int b = 50;

        for (int i = 0; i < points.length && points[i] != null; i++) {
            Vec3 point = points[i];
            int fadeAlpha = (int)(200 * (1.0f - (float)i / points.length));
            float size = DOT_SIZE * (1.0f - (float)i / points.length * 0.5f);
            float x = (float)point.x;
            float y = (float)point.y;
            float z = (float)point.z;

            consumer.addVertex(matrix, x-size, y-size, z+size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x+size, y-size, z+size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x+size, y+size, z+size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x-size, y+size, z+size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x+size, y-size, z-size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x-size, y-size, z-size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x-size, y+size, z-size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x+size, y+size, z-size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x-size, y+size, z-size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x-size, y+size, z+size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x+size, y+size, z+size).setColor(r, g, b, fadeAlpha);
            consumer.addVertex(matrix, x+size, y+size, z-size).setColor(r, g, b, fadeAlpha);
        }

        matrices.popPose();
        // context.consumers() flushes automatically at end of frame — no manual draw call needed
    }
}