package com.cooptest.client;

import com.cooptest.SpearStrikeHandler;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SpearStrikeClientHandler {
   private static final Set<Integer> flying = new HashSet<>();
   private static int targetId = -2;
   private static float speed;
   private static float turn;
   private static boolean missile;
   private static long lastAckMs;

   private SpearStrikeClientHandler() {
   }

   public static boolean isSteering() {
      return targetId != -2;
   }

   public static boolean isFlying(int entityId) {
      return flying.contains(entityId);
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(SpearStrikeHandler.SpearLockPayload.ID, (p, ctx) -> {
         targetId = p.targetId();
         speed = p.speed();
         turn = p.turn();
         missile = p.missile();
      });
      ClientPlayNetworking.registerGlobalReceiver(SpearStrikeHandler.SpearPosePayload.ID, (p, ctx) -> {
         if (p.flying()) {
            flying.add(p.entityId());
         } else {
            flying.remove(p.entityId());
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register(SpearStrikeClientHandler::tick);
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> clear());
   }

   private static void clear() {
      targetId = -2;
      flying.clear();
   }

   private static void ack() {
      long now = System.currentTimeMillis();
      if (now - lastAckMs >= 200L) {
         lastAckMs = now;
         ClientPlayNetworking.send(new SpearStrikeHandler.SpearAckPayload(true));
      }
   }

   private static void tick(Minecraft mc) {
      if (mc.player == null || mc.level == null) {
         clear();
      } else if (targetId != -2) {
         if (!mc.player.onGround() && !mc.player.isPassenger()) {
            Vec3 want;
            if (targetId >= 0) {
               Entity t = mc.level.getEntity(targetId);
               if (t == null || !t.isAlive()) {
                  clear();
                  return;
               }

               want = t.getBoundingBox().getCenter().subtract(mc.player.getBoundingBox().getCenter());
               if (want.lengthSqr() < 1.0E-6) {
                  return;
               }

               want = want.normalize();
            } else {
               if (!missile) {
                  return;
               }

               want = mc.player.getLookAngle().normalize();
            }

            Vec3 v = mc.player.getDeltaMovement();
            double sp = missile ? speed : Math.max(v.length(), speed);
            Vec3 cur = v.lengthSqr() > 1.0E-6 ? v.normalize() : want;
            Vec3 dir = cur.scale(1.0 - turn).add(want.scale(turn)).normalize();
            mc.player.setDeltaMovement(dir.scale(sp));
            mc.player.resetFallDistance();
            ack();
         } else {
            clear();
         }
      }
   }
}
