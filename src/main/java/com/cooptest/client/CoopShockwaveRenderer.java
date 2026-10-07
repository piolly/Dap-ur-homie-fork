package com.cooptest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public class CoopShockwaveRenderer {
   private static final long DURATION_MS = 450L;
   private static final float MAX_RADIUS = 2.5F;
   private static final int RINGS = 3;
   private static final int SEGMENTS = 48;
   private static final float LINE_WIDTH = 2.0F;
   private static Vec3 center = null;
   private static long startMs = 0L;

   public static void start(Vec3 position) {
      center = position;
      startMs = System.currentTimeMillis();
   }

   public static void render(WorldRenderContext context) {
      if (center != null) {
         long elapsed = System.currentTimeMillis() - startMs;
         if (elapsed > 450L) {
            center = null;
         } else {
            float progress = (float)elapsed / 450.0F;
            float alpha = (1.0F - progress) * (1.0F - progress);
            Vec3 camPos = context.worldState().cameraRenderState.pos;
            PoseStack matrices = context.matrices();
            matrices.pushPose();
            matrices.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
            Pose entry = matrices.last();
            VertexConsumer buf = context.consumers().getBuffer(RenderTypes.lines());

            for (int ring = 0; ring < 3; ring++) {
               float ringProgress = (progress + ring * 0.15F) % 1.0F;
               float radius = ringProgress * 2.5F;
               float ringAlpha = alpha * (1.0F - ring * 0.25F);
               if (!(ringAlpha <= 0.0F)) {
                  float prevX = radius;
                  float prevZ = 0.0F;

                  for (int i = 1; i <= 48; i++) {
                     double angle = i / 48.0 * Math.PI * 2.0;
                     float x = (float)(Math.cos(angle) * radius);
                     float z = (float)(Math.sin(angle) * radius);
                     float dx = x - prevX;
                     float dz = z - prevZ;
                     float len = (float)Math.sqrt(dx * dx + dz * dz);
                     if (len > 1.0E-5F) {
                        dx /= len;
                        dz /= len;
                     }

                     buf.addVertex(entry, prevX, 0.0F, prevZ).setColor(1.0F, 1.0F, 1.0F, ringAlpha).setNormal(entry, dx, 0.0F, dz).setLineWidth(2.0F);
                     buf.addVertex(entry, x, 0.0F, z).setColor(1.0F, 1.0F, 1.0F, ringAlpha).setNormal(entry, dx, 0.0F, dz).setLineWidth(2.0F);
                     prevX = x;
                     prevZ = z;
                  }
               }
            }

            matrices.popPose();
         }
      }
   }
}
