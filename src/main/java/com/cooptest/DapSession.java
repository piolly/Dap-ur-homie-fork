package com.cooptest;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

public class DapSession {
   private final UUID playerAId;
   private final UUID playerBId;
   private Vec3 targetA;
   private Vec3 targetB;
   private final long startTime;
   private int tickCount;
   private boolean positioningComplete;
   private final double targetDistance;
   private final double lerpSpeed;
   private final DapSession.DapType type;
   private Runnable onPositioningComplete;
   private boolean cancelled = false;

   public DapSession(UUID playerA, UUID playerB, double targetDistance, DapSession.DapType type) {
      this.playerAId = playerA;
      this.playerBId = playerB;
      this.targetDistance = targetDistance;
      this.type = type;
      this.startTime = System.currentTimeMillis();
      this.tickCount = 0;
      this.positioningComplete = false;
      if (type == DapSession.DapType.PERFECT_DAP) {
         this.lerpSpeed = 0.95;
      } else {
         this.lerpSpeed = 0.75;
      }
   }

   public void onComplete(Runnable callback) {
      this.onPositioningComplete = callback;
   }

   public void tick(MinecraftServer server) {
      if (!this.cancelled) {
         ServerPlayer playerA = server.getPlayerList().getPlayer(this.playerAId);
         ServerPlayer playerB = server.getPlayerList().getPlayer(this.playerBId);
         if (playerA != null && playerB != null && !playerA.isRemoved() && !playerB.isRemoved()) {
            if (this.positioningComplete) {
               this.tickCount++;
            } else if (this.tickCount > 100) {
               this.forceComplete(playerA, playerB);
            } else {
               this.freezePlayers(playerA, playerB);
               this.computeTargets(playerA, playerB);
               this.smoothMoveToTargets(playerA, playerB);
               this.makeFaceEachOther(playerA, playerB);
               playerA.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
               playerB.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
               if (!this.positioningComplete) {
                  this.checkPositioningComplete(playerA, playerB);
               }

               this.tickCount++;
            }
         }
      }
   }

   private void freezePlayers(ServerPlayer playerA, ServerPlayer playerB) {
      playerA.setDeltaMovement(Vec3.ZERO);
      playerB.setDeltaMovement(Vec3.ZERO);
      playerA.syncVelocity = true;
      playerB.syncVelocity = true;
      playerA.fallDistance = 0.0;
      playerB.fallDistance = 0.0;
   }

   private void computeTargets(ServerPlayer playerA, ServerPlayer playerB) {
      Vec3 posA = playerA.position();
      Vec3 posB = playerB.position();
      Vec3 midpoint = posA.add(posB).scale(0.5);
      Vec3 direction = posB.subtract(posA);
      if (direction.length() < 0.001) {
         direction = new Vec3(1.0, 0.0, 0.0);
      }

      direction = direction.normalize();
      double halfDistance = this.targetDistance / 2.0;
      this.targetA = midpoint.subtract(direction.scale(halfDistance));
      this.targetB = midpoint.add(direction.scale(halfDistance));
      double targetY = Math.max(posA.y, posB.y);
      ServerLevel world = playerA.level();
      BlockPos groundPos = new BlockPos((int)midpoint.x, (int)targetY - 1, (int)midpoint.z);
      if (world.getBlockState(groundPos).isAir()) {
         targetY = Math.min(posA.y, posB.y);
      }

      this.targetA = new Vec3(this.targetA.x, targetY, this.targetA.z);
      this.targetB = new Vec3(this.targetB.x, targetY, this.targetB.z);
   }

   private void smoothMoveToTargets(ServerPlayer playerA, ServerPlayer playerB) {
      Vec3 currentA = playerA.position();
      Vec3 currentB = playerB.position();
      Vec3 newPosA = new Vec3(
         this.lerp(currentA.x, this.targetA.x, this.lerpSpeed),
         this.lerp(currentA.y, this.targetA.y, this.lerpSpeed),
         this.lerp(currentA.z, this.targetA.z, this.lerpSpeed)
      );
      Vec3 newPosB = new Vec3(
         this.lerp(currentB.x, this.targetB.x, this.lerpSpeed),
         this.lerp(currentB.y, this.targetB.y, this.lerpSpeed),
         this.lerp(currentB.z, this.targetB.z, this.lerpSpeed)
      );
      playerA.teleportTo(playerA.level(), newPosA.x, newPosA.y, newPosA.z, Set.of(), playerA.getYRot(), playerA.getXRot(), false);
      playerB.teleportTo(playerB.level(), newPosB.x, newPosB.y, newPosB.z, Set.of(), playerB.getYRot(), playerB.getXRot(), false);
   }

   private void makeFaceEachOther(ServerPlayer playerA, ServerPlayer playerB) {
      Vec3 posA = playerA.position();
      Vec3 posB = playerB.position();
      double dx = posB.x - posA.x;
      double dz = posB.z - posA.z;
      float yawA = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
      float yawB = yawA + 180.0F;
      playerA.setYRot(yawA);
      playerA.setYBodyRot(yawA);
      playerA.setYHeadRot(yawA);
      playerA.yRotO = yawA;
      playerB.setYRot(yawB);
      playerB.setYBodyRot(yawB);
      playerB.setYHeadRot(yawB);
      playerB.yRotO = yawB;
      playerA.teleportTo(playerA.level(), posA.x, posA.y, posA.z, Set.of(), yawA, playerA.getXRot(), false);
      playerB.teleportTo(playerB.level(), posB.x, posB.y, posB.z, Set.of(), yawB, playerB.getXRot(), false);
   }

   private void checkPositioningComplete(ServerPlayer playerA, ServerPlayer playerB) {
      double distA = playerA.position().distanceTo(this.targetA);
      double distB = playerB.position().distanceTo(this.targetB);
      double threshold = this.type == DapSession.DapType.PERFECT_DAP ? 0.35 : 0.25;
      if (distA < threshold && distB < threshold) {
         this.positioningComplete = true;
         if (this.onPositioningComplete != null) {
            this.onPositioningComplete.run();
         }

         this.tickCount = 99;
      }
   }

   private void forceComplete(ServerPlayer playerA, ServerPlayer playerB) {
      if (!this.positioningComplete) {
         this.positioningComplete = true;
         if (this.onPositioningComplete != null) {
            this.onPositioningComplete.run();
         }
      }
   }

   private double lerp(double current, double target, double factor) {
      return current + (target - current) * factor;
   }

   public UUID getPlayerAId() {
      return this.playerAId;
   }

   public UUID getPlayerBId() {
      return this.playerBId;
   }

   public boolean isPositioningComplete() {
      return this.positioningComplete;
   }

   public void cancel() {
      this.cancelled = true;
   }

   public int getTickCount() {
      return this.tickCount;
   }

   public DapSession.DapType getType() {
      return this.type;
   }

   public long getStartTime() {
      return this.startTime;
   }

   public Vec3 getTargetA() {
      return this.targetA;
   }

   public Vec3 getTargetB() {
      return this.targetB;
   }

   public enum DapType {
      NORMAL_DAP,
      PERFECT_DAP,
      FIRE_DAP,
      FIRE_COMBO,
      DAP_HOLD;
   }
}
