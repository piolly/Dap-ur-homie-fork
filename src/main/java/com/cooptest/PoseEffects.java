package com.cooptest;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class PoseEffects {

    public static void playIdleEffects(ServerPlayer player) {
        ServerLevel world = player.level();
        Vec3 pos = player.position();

        // Whoosh sound
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0f, 1.2f);


    }

    public static void playActionEffects(ServerPlayer pusher, ServerPlayer target) {
        ServerLevel world = pusher.level();
        Vec3 pusherPos = pusher.position();
        Vec3 targetEntityPos = target.position();

        float yaw = pusher.getYRot();
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians) * 0.8;  // 0.8 blocks in front
        double forwardZ = Math.cos(radians) * 0.8;
        double rightX = Math.cos(radians) * 0.3;
        double rightZ = Math.sin(radians) * 0.3;
        double leftX = -rightX;
        double leftZ = -rightZ;
        double handY = pusherPos.y + 1.0;  // Chest height
        double rightHandX = pusherPos.x + forwardX + rightX;
        double rightHandZ = pusherPos.z + forwardZ + rightZ;
        double leftHandX = pusherPos.x + forwardX + leftX;
        double leftHandZ = pusherPos.z + forwardZ + leftZ;

        //  sound
        world.playSound(null, pusherPos.x, pusherPos.y, pusherPos.z,
                SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 1.0f, 0.8f);

        // Sweep sound
        world.playSound(null, pusherPos.x, pusherPos.y, pusherPos.z,
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0f, 1.0f);

        // Cloud particles at right hand
        for (int i = 0; i < 5; i++) {
            world.sendParticles(ParticleTypes.CLOUD,
                    rightHandX, handY, rightHandZ,
                    1, 0.1, 0.1, 0.1, 0.02);
        }

        // Cloud particles at left hand
        for (int i = 0; i < 5; i++) {
            world.sendParticles(ParticleTypes.CLOUD,
                    leftHandX, handY, leftHandZ,
                    1, 0.1, 0.1, 0.1, 0.02);
        }

        // Poof at hands
        world.sendParticles(ParticleTypes.POOF,
                rightHandX, handY, rightHandZ,
                3, 0.1, 0.1, 0.1, 0.02);
        world.sendParticles(ParticleTypes.POOF,
                leftHandX, handY, leftHandZ,
                3, 0.1, 0.1, 0.1, 0.02);

        // Swoosh for target
        world.playSound(null, targetEntityPos.x, targetEntityPos.y, targetEntityPos.z,
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.8f, 1.5f);

        // Small poof at target impact point
        world.sendParticles(ParticleTypes.POOF,
                targetEntityPos.x, targetEntityPos.y + 0.5, targetEntityPos.z,
                3, 0.2, 0.2, 0.2, 0.02);

        // Push pusher down slightly
        pusher.setDeltaMovement(pusher.getDeltaMovement().add(0, -0.15, 0));
        pusher.hurtMarked = true;
    }

    public static void playLaunchTrailEffects(ServerPlayer target) {
        ServerLevel world = target.level();
        Vec3 pos = target.position();

        world.sendParticles(ParticleTypes.CLOUD,
                pos.x, pos.y + 0.5, pos.z,
                2, 0.2, 0.2, 0.2, 0.02);
    }
}