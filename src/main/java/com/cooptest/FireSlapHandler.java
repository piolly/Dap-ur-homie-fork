package com.cooptest;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class FireSlapHandler {
   private static final float MIN_FIRE_LEVEL = 0.95F;
   private static final double SLAP_KNOCKBACK = 2.5;
   private static final double SLAP_VERTICAL = 0.5;

   public static void register() {
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer serverPlayer) {
            if (entity instanceof LivingEntity target) {
               if (entity instanceof Player) {
                  return InteractionResult.PASS;
               }

               float fireLevel = ChargedDapHandler.fireLevel.getOrDefault(player.getUUID(), 0.0F);
               if (fireLevel < 0.95F) {
                  return InteractionResult.PASS;
               }

               executeFireSlap(serverPlayer, target);
               return InteractionResult.PASS;
            } else {
               return InteractionResult.PASS;
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void executeFireSlap(ServerPlayer player, LivingEntity target) {
      ServerLevel world = player.level();
      Vec3 playerPos = player.position();
      Vec3 targetPos = target.position();
      Vec3 direction = targetPos.subtract(playerPos).normalize();
      target.setDeltaMovement(direction.x * 2.5, 0.5, direction.z * 2.5);
      target.hurtMarked = true;
      target.igniteForSeconds(2.0F);
      double x = target.getX();
      double y = target.getY() + target.getBbHeight() / 2.0F;
      double z = target.getZ();
      world.sendParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.05);
      world.sendParticles(ParticleTypes.SMOKE, x, y, z, 5, 0.2, 0.2, 0.2, 0.02);
      world.sendParticles(ParticleTypes.CRIT, x, y, z, 6, 0.3, 0.3, 0.3, 0.1);
      world.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
      world.playSound(null, x, y, z, SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 0.5F, 1.2F);
      player.displayClientMessage(Component.literal("§c FIRE SLAP! "), true);
      ChargedDapHandler.fireLevel.put(player.getUUID(), 0.5F);
   }
}
