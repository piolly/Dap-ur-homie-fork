package com.cooptest.client;

import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class TrajectoryRenderer {
   private static final int TRAJECTORY_POINTS = 60;
   private static final float GRAVITY = 0.08F;
   private static final float VERTICAL_DRAG = 0.98F;
   private static final float HORIZONTAL_DRAG = 0.91F;
   private static final boolean STOP_ON_BLOCKS = true;
   private static final float DOT_SIZE = 0.08F;
   private static final float MIN_POWER_MULT = 1.5F;
   private static final float MAX_POWER_MULT = 3.5F;

   public static void register() {
      LevelRenderEvents.END_MAIN.register(TrajectoryRenderer::render);
   }

   private static void render(LevelRenderContext context) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null) {
         PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUUID(), PoseState.NONE);
         if (pose == PoseState.GRAB_HOLDING) {
            float chargeProgress = GrabInputHandler.getThrowChargeProgress();
            if (!(chargeProgress <= 0.0F)) {
               float power = 1.5F + 2.0F * chargeProgress;
               Vec3 lookVec = client.player.getViewVector(client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
               Vec3 startPos = client.player.getEyePosition().add(0.0, 0.5, 0.0);
               Vec3 velocity = lookVec.scale(power);
               Vec3[] points = new Vec3[60];
               Vec3 pos = startPos;
               Vec3 vel = velocity;

               for (int i = 0; i < 60; i++) {
                  points[i] = pos;
                  vel = new Vec3(vel.x * 0.91F, (vel.y - 0.08F) * 0.98F, vel.z * 0.91F);
                  Vec3 next = pos.add(vel);
                  BlockHitResult hit = client.level.clip(new ClipContext(pos, next, Block.COLLIDER, Fluid.NONE, client.player));
                  if (hit != null && hit.getType() == Type.BLOCK) {
                     points[i] = hit.getLocation();
                     break;
                  }

                  pos = next;
                  if (pos.y < client.player.getY() - 40.0) {
                     break;
                  }
               }

               renderTrajectoryDots(context, points, chargeProgress);
            }
         }
      }
   }

   private static void renderTrajectoryDots(LevelRenderContext context, Vec3[] points, float charge) {
      Vec3 camPos = context.worldState().cameraRenderState.pos;
      PoseStack matrices = context.matrices();
      matrices.pushPose();
      matrices.translate(-camPos.x, -camPos.y, -camPos.z);
      VertexConsumer buffer = context.consumers().getBuffer(CoopWorldPipelines.TRAJECTORY_LAYER);
      Pose matrix = matrices.last();
      int r = (int)(charge * 255.0F);
      int g = (int)((1.0F - charge) * 255.0F);
      int b = 50;
      int alpha = 200;

      for (int i = 0; i < points.length && points[i] != null; i++) {
         Vec3 point = points[i];
         float fadeAlpha = alpha * (1.0F - (float)i / points.length);
         float size = 0.08F * (1.0F - (float)i / points.length * 0.5F);
         float x = (float)point.x;
         float y = (float)point.y;
         float z = (float)point.z;
         buffer.addVertex(matrix, x - size, y - size, z + size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x + size, y - size, z + size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x + size, y + size, z + size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x - size, y + size, z + size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x + size, y - size, z - size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x - size, y - size, z - size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x - size, y + size, z - size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x + size, y + size, z - size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x - size, y + size, z - size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x - size, y + size, z + size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x + size, y + size, z + size).setColor(r, g, b, (int)fadeAlpha);
         buffer.addVertex(matrix, x + size, y + size, z - size).setColor(r, g, b, (int)fadeAlpha);
      }

      matrices.popPose();
   }
}
