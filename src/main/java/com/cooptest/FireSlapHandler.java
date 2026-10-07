package com.cooptest;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
UNDER CONSTRUCTION
 */
public class FireSlapHandler {

    private static final float MIN_FIRE_LEVEL = 0.95f;

    private static final double SLAP_KNOCKBACK = 2.5;
    private static final double SLAP_VERTICAL = 0.5;

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return InteractionResult.PASS;
            if (entity instanceof Player) return InteractionResult.PASS; // Don't slap players

            float fireLevel = ChargedDapHandler.fireLevel.getOrDefault(player.getUUID(), 0f);
            if (fireLevel < MIN_FIRE_LEVEL) return InteractionResult.PASS;

            executeFireSlap(serverPlayer, target);

            return InteractionResult.PASS;
        });
    }

    private static void executeFireSlap(ServerPlayer player, LivingEntity target) {
        ServerLevel world = player.level();
        Vec3 playerPos = player.position();
        Vec3 targetEntityPos = target.position();

        Vec3 direction = targetEntityPos.subtract(playerPos).normalize();

        target.setDeltaMovement(
                direction.x * SLAP_KNOCKBACK,
                SLAP_VERTICAL,
                direction.z * SLAP_KNOCKBACK
        );
        target.hurtMarked = true;

        target.igniteForSeconds(2);

        // Spawn fire particles at impact
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() / 2;
        double z = target.getZ();

        world.sendParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.05);
        world.sendParticles(ParticleTypes.SMOKE, x, y, z, 5, 0.2, 0.2, 0.2, 0.02);
        world.sendParticles(ParticleTypes.CRIT, x, y, z, 6, 0.3, 0.3, 0.3, 0.1);

        world.playSound(null, x, y, z,
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, x, y, z,
                SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 0.5f, 1.2f);

        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c FIRE SLAP! "), true);

        ChargedDapHandler.fireLevel.put(player.getUUID(), 0.5f);
    }
}