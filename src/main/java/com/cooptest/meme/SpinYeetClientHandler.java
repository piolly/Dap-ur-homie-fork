package com.cooptest.meme;

import com.cooptest.client.CoopImpactHandler;
import com.cooptest.client.CoopKeyCategories;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public class SpinYeetClientHandler {
   private static KeyMapping spinYeetKey;
   private static boolean wasHeld = false;
   private static boolean weAreGrabber = false;
   private static boolean weAreGrabbed = false;
   private static UUID localGrabberUUID = null;
   private static UUID localGrabbedUUID = null;
   private static volatile float cameraRollDegrees = 0.0F;

   public static void register() {
      spinYeetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coopmoves.spin_yeet", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 77, CoopKeyCategories.COOPMOVES));
      ClientPlayNetworking.registerGlobalReceiver(SpinYeetGrabberYawPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         if (ctx.client().player != null && weAreGrabber) {
            ctx.client().player.setYRot(ctx.client().player.getYRot() + payload.yawDelta());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         SpinYeetImpactPayload.ID, (payload, ctx) -> ctx.client().execute(() -> CoopImpactHandler.start(CoopImpactHandler.PERFECT_DAP_SEQUENCE, 33L, true))
      );
      ClientPlayNetworking.registerGlobalReceiver(SpinYeetStartPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         if (ctx.client().player != null) {
            UUID localUUID = ctx.client().player.getUUID();
            localGrabberUUID = payload.grabberUUID();
            localGrabbedUUID = payload.grabbedUUID();
            weAreGrabber = localUUID.equals(localGrabberUUID);
            weAreGrabbed = localUUID.equals(localGrabbedUUID);
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(SpinYeetCameraPayload.ID, (payload, ctx) -> cameraRollDegrees = payload.rollDegrees());
      ClientPlayNetworking.registerGlobalReceiver(SpinYeetEndPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         cameraRollDegrees = 0.0F;
         weAreGrabber = false;
         weAreGrabbed = false;
         localGrabberUUID = null;
         localGrabbedUUID = null;
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.level != null) {
            if (client.gui.screen() == null) {
               boolean isHeld = InputConstants.isKeyDown(InputConstants.KEY_M);
               if (isHeld && !wasHeld) {
                  System.out.println("[SpinYeet-DEBUG] M pressed");
                  onMPressed(client);
               } else if (!isHeld && wasHeld) {
                  System.out.println("[SpinYeet-DEBUG] M released, weAreGrabber=" + weAreGrabber);
                  onMReleased();
               }

               wasHeld = isHeld;
            }
         }
      });
   }

   private static void onMPressed(Minecraft client) {
      if (client.player != null && client.level != null) {
         AABB searchBox = client.player.getBoundingBox().inflate(2.0);
         List<LivingEntity> nearby = client.level.getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != client.player && e.isAlive());
         System.out.println("[SpinYeet-DEBUG] M pressed — nearby entities in 2-block box: " + nearby.size());
         if (nearby.isEmpty()) {
            System.out.println("[SpinYeet-DEBUG] No target found, aborting grab");
         } else {
            nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(client.player)));
            Entity target = (Entity)nearby.get(0);
            System.out
               .println(
                  "[SpinYeet-DEBUG] Sending grab for target="
                     + target.getName().getString()
                     + " dist="
                     + String.format("%.2f", Math.sqrt(target.distanceToSqr(client.player)))
               );
            ClientPlayNetworking.send(new SpinYeetGrabPayload(target.getUUID()));
         }
      }
   }

   private static void onMReleased() {
      if (weAreGrabber) {
         ClientPlayNetworking.send(new SpinYeetReleasePayload());
      }
   }

   public static float getCameraRoll() {
      return cameraRollDegrees;
   }
}
