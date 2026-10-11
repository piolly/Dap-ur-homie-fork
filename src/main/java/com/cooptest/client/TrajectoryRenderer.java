package com.cooptest.client;

import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.mojang.blaze3d.buffers.*;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
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
        LevelRenderEvents.END_MAIN.register(TrajectoryRenderer::render);
    }

    private static void render(LevelRenderContext context) {
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

    private static boolean trajectoryWarned = false;

    private static void renderTrajectoryDots(LevelRenderContext context, Vec3[] points, float charge) {
        try {
            int r = (int)(charge * 255.0F);
            int g = (int)((1.0F - charge) * 255.0F);
            int b = 50;
            int n = 0;
            while (n < points.length && points[n] != null) n++;
            for (int i = 0; i < n; i++) {
                Vec3 p = points[i];
                float t = (float)i / n;
                int a = (int)(200 * (1.0F - t));
                float size = 0.08F * (1.0F - t * 0.5F);
                int color = (a << 24) | (r << 16) | (g << 8) | b;
                net.minecraft.gizmos.Gizmos.cuboid(
                    new net.minecraft.world.phys.AABB(p.x - size, p.y - size, p.z - size, p.x + size, p.y + size, p.z + size),
                    net.minecraft.gizmos.GizmoStyle.fill(color));
            }
        } catch (Throwable t) {
            if (!trajectoryWarned) {
                trajectoryWarned = true;
                System.out.println("[Trajectory] gizmo draw failed: " + t);
            }
        }
    }
}
