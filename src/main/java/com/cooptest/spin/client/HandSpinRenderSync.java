package com.cooptest.spin.client;

import com.cooptest.spin.HandSpinHandler;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class HandSpinRenderSync {
   private static final int OBSERVER_STALE_TICKS = 6;
   private static final double SYNC_BLEND = 0.5;
   private static boolean active = false;
   private static UUID p1Id = null;
   private static UUID p2Id = null;
   private static Vec3 center = null;
   private static double angle = 0.0;
   private static double angularVel = 0.0;
   private static double p1Lift = 0.0;
   private static double p2Lift = 0.0;
   private static double correctionRemaining = 0.0;
   private static int ticksSinceSync = 999;
   private static Vec3 lastP1Pos = null;
   private static Vec3 lastP2Pos = null;
   private static Float lastP1Yaw = null;
   private static Float lastP2Yaw = null;
   private static ClientLevel sessionWorld = null;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinObserverPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         HandSpinClientHandler.noteServerSignal();
         p1Id = payload.p1();
         p2Id = payload.p2();
         center = new Vec3(payload.centerX(), payload.centerY(), payload.centerZ());
         p1Lift = payload.p1Lift();
         p2Lift = payload.p2Lift();
         if (active && sessionWorld == client.level) {
            correctionRemaining = payload.angle() - angle;
         } else {
            angle = payload.angle();
            correctionRemaining = 0.0;
            lastP1Pos = null;
            lastP2Pos = null;
            lastP1Yaw = null;
            lastP2Yaw = null;
            sessionWorld = client.level;
            active = true;
         }

         angularVel = payload.angularVel();
         ticksSinceSync = 0;
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (active) {
            if (client.level != null && client.player != null && sessionWorld == client.level) {
               if (++ticksSinceSync > 6) {
                  clear();
               } else {
                  double step = correctionRemaining * 0.5 / Math.max(1, 3);
                  correctionRemaining -= step;
                  angle = angle + (angularVel + step);
                  apply(client);
               }
            } else {
               clear();
            }
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> clear());
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> clear());
   }

   public static void clear() {
      active = false;
      p1Id = null;
      p2Id = null;
      center = null;
      angle = 0.0;
      angularVel = 0.0;
      p1Lift = 0.0;
      p2Lift = 0.0;
      correctionRemaining = 0.0;
      ticksSinceSync = 999;
      lastP1Pos = null;
      lastP2Pos = null;
      lastP1Yaw = null;
      lastP2Yaw = null;
      sessionWorld = null;
   }

   private static void apply(Minecraft client) {
      if (client.level != null && center != null) {
         Player e1 = null;
         Player e2 = null;

         for (Player p : client.level.players()) {
            UUID id = p.getUUID();
            if (id.equals(p1Id)) {
               e1 = p;
            } else if (id.equals(p2Id)) {
               e2 = p;
            }
         }

         if (e1 != null) {
            HandSpinRenderSync.Placement r = place(client, e1, angle, p1Lift, lastP1Pos, lastP1Yaw);
            lastP1Pos = r.pos;
            lastP1Yaw = r.yaw;
         }

         if (e2 != null) {
            HandSpinRenderSync.Placement r = place(client, e2, angle + Math.PI, p2Lift, lastP2Pos, lastP2Yaw);
            lastP2Pos = r.pos;
            lastP2Yaw = r.yaw;
         }
      }
   }

   private static HandSpinRenderSync.Placement place(Minecraft client, Player p, double a, double lift, Vec3 prevPos, Float prevYaw) {
      double x = center.x + Math.cos(a) * 0.75;
      double z = center.z + Math.sin(a) * 0.75;
      double y = center.y + lift;
      float yaw = (float)Math.toDegrees(Math.atan2(Math.cos(a), -Math.sin(a)));
      p.getInterpolation().cancel();
      p.setPos(x, y, z);
      Vec3 from = prevPos != null ? prevPos : new Vec3(x, y, z);
      p.xo = from.x;
      p.yo = from.y;
      p.zo = from.z;
      p.xOld = from.x;
      p.yOld = from.y;
      p.zOld = from.z;
      if (p != client.player) {
         float fromYaw = prevYaw != null ? prevYaw : yaw;
         float wrappedFrom = yaw - Mth.wrapDegrees(yaw - fromYaw);
         p.setYRot(yaw);
         p.yRotO = wrappedFrom;
         p.setYHeadRot(yaw);
         p.yHeadRotO = wrappedFrom;
         p.setYBodyRot(yaw);
         p.yBodyRotO = wrappedFrom;
      }

      return new HandSpinRenderSync.Placement(new Vec3(x, y, z), yaw);
   }

   public static boolean isActive() {
      return active;
   }

   private record Placement(Vec3 pos, float yaw) {
   }
}
