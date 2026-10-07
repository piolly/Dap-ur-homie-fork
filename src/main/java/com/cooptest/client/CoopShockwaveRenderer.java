package com.cooptest.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

public class CoopShockwaveRenderer {
   private static final List<double[]> RINGS = new ArrayList<>();

   public static void start(Vec3 position) {
      if (position == null) return;
      synchronized (RINGS) {
         if (RINGS.size() < 8) RINGS.add(new double[]{position.x, position.y, position.z, 0});
      }
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(client -> {
         if (client.level == null) {
            synchronized (RINGS) { RINGS.clear(); }
            return;
         }
         synchronized (RINGS) {
            for (int i = RINGS.size() - 1; i >= 0; i--) {
               double[] r = RINGS.get(i);
               double radius = 0.6 + r[3] * 0.55;
               for (int k = 0; k < 24; k++) {
                  double a = k * (Math.PI * 2.0 / 24.0);
                  double dx = Math.cos(a);
                  double dz = Math.sin(a);
                  client.level.addParticle(ParticleTypes.CLOUD, r[0] + dx * radius, r[1], r[2] + dz * radius, dx * 0.08, 0.02, dz * 0.08);
               }
               r[3] += 1;
               if (r[3] > 12) RINGS.remove(i);
            }
         }
      });
   }

   // TODO(26.3 port): original ring mesh needs the new render API. Particle ring is a stand-in.
   public static void render(LevelRenderContext context) {
   }
}
