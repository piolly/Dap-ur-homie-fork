package com.cooptest.mixin.client;

import com.cooptest.highfive.client.ReadyHugClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class HandSpinCameraLockMixin {
   @Shadow
   private double accumulatedDX;
   @Unique
   private static long coopmod$lastFlyNanos = -1L;
   @Unique
   private static final float FLY_SPIN_DEG_PER_SEC = 360.0F;

   @Inject(method = "turnPlayer", at = @At("HEAD"))
   private void coopmod$eatHorizontalLook(double timeDelta, CallbackInfo ci) {
      if (HandSpinClientHandler.isLocalPlayerSpinning() || HandSpinClientHandler.isLocalPlayerMonkeFlying() || ReadyHugClientHandler.isLocalPlayerInHug()) {
         this.accumulatedDX = 0.0;
      }
   }

   @Inject(method = "turnPlayer", at = @At("TAIL"))
   private void coopmod$faceCenter(double timeDelta, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         if (HandSpinClientHandler.isLocalPlayerMonkeFlying()) {
            long nowNanos = System.nanoTime();
            if (coopmod$lastFlyNanos > 0L) {
               float dt = (float)(nowNanos - coopmod$lastFlyNanos) / 1.0E9F;
               float yaw = Mth.wrapDegrees(player.getYRot() + 360.0F * dt);
               HandSpinClientHandler.applyLockedYaw(player, yaw);
            }

            coopmod$lastFlyNanos = nowNanos;
         } else {
            coopmod$lastFlyNanos = -1L;
            if (!ReadyHugClientHandler.isLocalPlayerInHug()) {
               if (HandSpinClientHandler.isLocalPlayerSpinning()) {
                  Vec3 center = HandSpinClientHandler.getLockedCenter();
                  if (center != null) {
                     if (client.level != null) {
                        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
                        Vec3 rp = player.getPosition(tickDelta);
                        double dx = rp.x - center.x;
                        double dz = rp.z - center.z;
                        if (!(dx * dx + dz * dz < 1.0E-6)) {
                           float targetYaw = (float)Math.toDegrees(Math.atan2(dx, -dz));
                           HandSpinClientHandler.applyLockedYaw(player, targetYaw);
                        }
                     }
                  }
               }
            } else {
               UUID partner = ReadyHugClientHandler.getPartnerId();
               if (partner != null && client.level != null) {
                  Player other = null;

                  for (Player p : client.level.players()) {
                     if (p.getUUID().equals(partner)) {
                        other = p;
                        break;
                     }
                  }

                  if (other != null) {
                     double hdx = other.getX() - player.getX();
                     double hdz = other.getZ() - player.getZ();
                     if (!(hdx * hdx + hdz * hdz < 1.0E-6)) {
                        float hugYaw = (float)Math.toDegrees(Math.atan2(-hdx, hdz));
                        HandSpinClientHandler.applyLockedYaw(player, hugYaw);
                     }
                  }
               }
            }
         }
      }
   }
}
