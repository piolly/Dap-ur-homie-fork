package com.cooptest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;


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
    private final DapType type;           

    private Runnable onPositioningComplete;

    public enum DapType {
        NORMAL_DAP,     
        PERFECT_DAP,     
        FIRE_DAP,
        FIRE_COMBO,
        DAP_HOLD
    }

  
    public DapSession(UUID playerA, UUID playerB, double targetDistance, DapType type) {
        this.playerAId = playerA;
        this.playerBId = playerB;
        this.targetDistance = targetDistance;
        this.type = type;
        this.startTime = System.currentTimeMillis();
        this.tickCount = 0;
        this.positioningComplete = false;

        if (type == DapType.PERFECT_DAP) {
            this.lerpSpeed = 0.95;  
        } else {
            this.lerpSpeed = 0.75;  // Very fast speed (ends at ~0.75 of animation)
        }

        System.out.println("[DapSession] Created session: " + type + " | Distance: " + targetDistance + " | Speed: " + lerpSpeed);
    }

   
    public void onComplete(Runnable callback) {
        this.onPositioningComplete = callback;
    }

 
    public void tick(MinecraftServer server) {
        ServerPlayer playerA = server.getPlayerList().getPlayer(playerAId);
        ServerPlayer playerB = server.getPlayerList().getPlayer(playerBId);

        // Safety checks
        if (playerA == null || playerB == null) {
            System.out.println("[DapSession] Player null - removing session");
            return;
        }

        if (playerA.isRemoved() || playerB.isRemoved()) {
            System.out.println("[DapSession] Player removed - removing session");
            return;
        }

        // Timeout safety (5 seconds)
        if (tickCount > 100) {
            System.out.println("[DapSession] Timeout - forcing completion");
            forceComplete(playerA, playerB);
            return;
        }

        freezePlayers(playerA, playerB);
        computeTargets(playerA, playerB);
        smoothMoveToTargets(playerA, playerB);
        makeFaceEachOther(playerA, playerB);
        playerA.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        playerB.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);

        if (!positioningComplete) {
            checkPositioningComplete(playerA, playerB);
        }

        tickCount++;
    }

   
    private void freezePlayers(ServerPlayer playerA, ServerPlayer playerB) {
        // Stop velocity completely
        playerA.setDeltaMovement(Vec3.ZERO);
        playerB.setDeltaMovement(Vec3.ZERO);
        playerA.syncVelocity = true;
        playerB.syncVelocity = true;

        playerA.fallDistance = 0;
        playerB.fallDistance = 0;
    }

   
    private void computeTargets(ServerPlayer playerA, ServerPlayer playerB) {
        Vec3 posA = playerA.position();
        Vec3 posB = playerB.position();

        Vec3 midpoint = posA.add(posB).scale(0.5);

        Vec3 direction = posB.subtract(posA);
        if (direction.length() < 0.001) {
            direction = new Vec3(1, 0, 0);
        }
        direction = direction.normalize();

        double halfDistance = targetDistance / 2.0;
        targetA = midpoint.subtract(direction.scale(halfDistance));
        targetB = midpoint.add(direction.scale(halfDistance));

        double targetY = Math.max(posA.y, posB.y);

        ServerLevel world = playerA.level();
        BlockPos groundPos = new BlockPos((int)midpoint.x, (int)targetY - 1, (int)midpoint.z);
        if (world.getBlockState(groundPos).isAir()) {
            targetY = Math.min(posA.y, posB.y);
        }

        targetA = new Vec3(targetA.x, targetY, targetA.z);
        targetB = new Vec3(targetB.x, targetY, targetB.z);
    }

    
    private void smoothMoveToTargets(ServerPlayer playerA, ServerPlayer playerB) {
        Vec3 currentA = playerA.position();
        Vec3 currentB = playerB.position();

        Vec3 newPosA = new Vec3(
                lerp(currentA.x, targetA.x, lerpSpeed),
                lerp(currentA.y, targetA.y, lerpSpeed),
                lerp(currentA.z, targetA.z, lerpSpeed)
        );

        Vec3 newPosB = new Vec3(
                lerp(currentB.x, targetB.x, lerpSpeed),
                lerp(currentB.y, targetB.y, lerpSpeed),
                lerp(currentB.z, targetB.z, lerpSpeed)
        );


        playerA.teleportTo(playerA.level(), newPosA.x, newPosA.y, newPosA.z, java.util.Set.of(), playerA.getYRot(), playerA.getXRot(), false);
        playerB.teleportTo(playerB.level(), newPosB.x, newPosB.y, newPosB.z, java.util.Set.of(), playerB.getYRot(), playerB.getXRot(), false);
    }

   
    private void makeFaceEachOther(ServerPlayer playerA, ServerPlayer playerB) {
        Vec3 posA = playerA.position();
        Vec3 posB = playerB.position();

        // Calculate yaw to face each other
        double dx = posB.x - posA.x;
        double dz = posB.z - posA.z;
        float yawA = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawB = yawA + 180;  // Opposite direction

        playerA.setYRot(yawA);
        playerA.setYBodyRot(yawA);
        playerA.setYHeadRot(yawA);
        playerA.yRotO = yawA;  // Prevent interpolation
        playerA.yBodyRotO = yawA;  // Prevent body lag
        playerA.yHeadRotO = yawA;

        playerB.setYRot(yawB);
        playerB.setYBodyRot(yawB);
        playerB.setYHeadRot(yawB);
        playerB.yRotO = yawB;  // Prevent interpolation
        playerB.yBodyRotO = yawB;  // Prevent body lag
        playerB.yHeadRotO = yawB;

        playerA.teleportTo(playerA.level(), posA.x, posA.y, posA.z, java.util.Set.of(), yawA, playerA.getXRot(), false);
        playerB.teleportTo(playerB.level(), posB.x, posB.y, posB.z, java.util.Set.of(), yawB, playerB.getXRot(), false);
    }

    
    private void checkPositioningComplete(ServerPlayer playerA, ServerPlayer playerB) {
        double distA = playerA.position().distanceTo(targetA);
        double distB = playerB.position().distanceTo(targetB);

        double threshold = (type == DapType.PERFECT_DAP) ? 0.35 : 0.25;
        if (distA < threshold && distB < threshold) {
            positioningComplete = true;
            System.out.println("[DapSession] Positioning complete! (" + tickCount + " ticks)");

            if (onPositioningComplete != null) {
                onPositioningComplete.run();
            }

            this.tickCount = 99;  
        }
    }

    
    private void forceComplete(ServerPlayer playerA, ServerPlayer playerB) {
        if (!positioningComplete) {
            positioningComplete = true;
            System.out.println("[DapSession] Force completed");

            if (onPositioningComplete != null) {
                onPositioningComplete.run();
            }
        }
    }

   
    private double lerp(double current, double target, double factor) {
        return current + (target - current) * factor;
    }

    public UUID getPlayerAId() { return playerAId; }
    public UUID getPlayerBId() { return playerBId; }
    public boolean isPositioningComplete() { return positioningComplete; }
    public int getTickCount() { return tickCount; }
    public DapType getType() { return type; }
    public long getStartTime() { return startTime; }
    public Vec3 getTargetA() { return targetA; }
    public Vec3 getTargetB() { return targetB; }
}
