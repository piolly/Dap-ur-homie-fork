package com.cooptest;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class SlapHandler {
   private static final double SLAP_RANGE = 1.2;
   private static final double SNAP_DISTANCE = 0.9;
   private static final double BACK_THRESHOLD = 0.7;
   private static final double FRONT_THRESHOLD = -0.5;
   private static final double AIM_THRESHOLD = 0.8;
   private static final long IMPACT_DELAY_MS = 130L;
   private static final long FRONT_IMPACT_MS = 250L;
   private static final int ANIM_SLAP = 67;
   private static final int ANIM_SLAP_FRONT = 82;

   public static void register() {
      PayloadTypeRegistry.clientboundPlay().register(SlapHandler.CameraFlickPayload.ID, SlapHandler.CameraFlickPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SlapHandler.CameraYawFlickPayload.ID, SlapHandler.CameraYawFlickPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SlapHandler.ScreenClosePayload.ID, SlapHandler.ScreenClosePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SlapHandler.FishSlapPayload.ID, SlapHandler.FishSlapPayload.CODEC);
   }

   public static boolean checkSlapOnRelease(ServerPlayer attacker, long chargeDurationMs) {
      if (StrongSlapCommand.isArmed(attacker.getUUID()) && StrongSlapHandler.tryStrongSlap(attacker, chargeDurationMs)) {
         return true;
      }

      ItemStack held = attacker.getMainHandItem();
      boolean holdingFish = held.is(Items.COD) || held.is(Items.SALMON) || held.is(Items.PUFFERFISH) || held.is(Items.TROPICAL_FISH);
      Vec3 aEye = attacker.position().add(0.0, attacker.getEyeHeight(attacker.getPose()), 0.0);
      Vec3 aLook = attacker.getViewVector(1.0F);
      ServerPlayer victim = null;
      double closest = 1.2009999999999998;

      for (ServerPlayer candidate : attacker.level().players()) {
         if (candidate != attacker) {
            double dist = attacker.position().distanceTo(candidate.position());
            if (!(dist >= closest)) {
               Vec3 victimLook = candidate.getViewVector(1.0F);
               double lookDot = aLook.dot(victimLook);
               boolean isBack = lookDot >= 0.7;
               boolean isFront = lookDot <= -0.5;
               if (isBack || isFront) {
                  Vec3 victimHead = candidate.position().add(0.0, 1.6, 0.0);
                  Vec3 toHead = victimHead.subtract(aEye);
                  double distToHead = toHead.length();
                  if (!(distToHead < 0.01)) {
                     double aimDot = toHead.normalize().dot(aLook);
                     if (!(aimDot < 0.8)) {
                        victim = candidate;
                        closest = dist;
                     }
                  }
               }
            }
         }
      }

      if (victim == null) {
         return false;
      }

      if (holdingFish) {
         executeFishSlap(attacker, victim);
         return true;
      }

      boolean isJumpSlap = !attacker.onGround();
      Vec3 victimLookFinal = victim.getViewVector(1.0F);
      if (aLook.dot(victimLookFinal) <= -0.5) {
         executeFrontSlap(attacker, victim);
      } else {
         executeSlap(attacker, victim, isJumpSlap);
      }

      return true;
   }

   private static void executeSlap(ServerPlayer attacker, ServerPlayer victim, boolean isJumpSlap) {
      ServerLevel world = attacker.level();
      Vec3 victimPos = victim.position();
      Vec3 victimFwd = victim.getViewVector(1.0F);
      Vec3 victimFwdH = new Vec3(victimFwd.x, 0.0, victimFwd.z).normalize();
      if (victimFwdH.lengthSqr() < 0.001) {
         victimFwdH = new Vec3(1.0, 0.0, 0.0);
      }

      if (CoopMovesConfig.get().enableNormalSlapSmoothTp) {
         Vec3 snapPos = victimPos.subtract(victimFwdH.scale(0.9));
         double safeY = snapPos.y;

         for (int dy = 0; dy <= 2; dy++) {
            BlockPos check = BlockPos.containing(snapPos.x, snapPos.y - dy, snapPos.z);
            if (!world.getBlockState(check).isAir()) {
               safeY = check.getY() + 1.0;
               break;
            }
         }

         snapPos = new Vec3(snapPos.x, safeY, snapPos.z);
         attacker.teleportTo(snapPos.x, snapPos.y, snapPos.z);
      }

      Vec3 diff = victimPos.subtract(attacker.position());
      float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
      attacker.setYRot(yaw);
      attacker.setYBodyRot(yaw);
      attacker.setYHeadRot(yaw);
      attacker.yBodyRotO = yaw;
      attacker.swing(InteractionHand.MAIN_HAND, true);
      attacker.swing(InteractionHand.MAIN_HAND, true);
      PoseNetworking.broadcastAnimState(attacker, 67);
      if (isJumpSlap) {
         Vec3 impactPos = victimPos.add(0.0, 1.2, 0.0);
         world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impactPos.x, impactPos.y, impactPos.z, 2, 0.1, 0.1, 0.1, 0.0);
         world.sendParticles(ParticleTypes.CRIT, impactPos.x, impactPos.y, impactPos.z, 25, 0.35, 0.35, 0.35, 0.2);
         world.sendParticles(ParticleTypes.SWEEP_ATTACK, impactPos.x, impactPos.y, impactPos.z, 8, 0.2, 0.1, 0.2, 0.05);
         world.sendParticles(ParticleTypes.ENCHANTED_HIT, impactPos.x, impactPos.y, impactPos.z, 12, 0.2, 0.2, 0.2, 0.1);
         world.playSound(null, impactPos.x, impactPos.y, impactPos.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 1.1F);
         world.playSound(null, impactPos.x, impactPos.y, impactPos.z, ModSounds.SLAP, SoundSource.PLAYERS, 2.2F, 0.8F);
      }

      UUID victimId = victim.getUUID();
      new Thread(() -> {
         try {
            Thread.sleep(130L);
         } catch (InterruptedException var5x) {
         }

         attacker.level().getServer().execute(() -> {
            ServerPlayer v = attacker.level().getServer().getPlayerList().getPlayer(victimId);
            if (v != null) {
               Vec3 hitPos = v.position().add(0.0, 1.7, 0.0);
               if (isJumpSlap) {
                  v.hurtServer(v.level(), v.level().damageSources().playerAttack(attacker), 10.0F);
                  v.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 1, false, true));
                  world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5F, 0.9F);
                  if (v.isPassenger()) {
                     v.stopRiding();
                     Vec3 crushPos = v.position().subtract(0.0, 0.5, 0.0);
                     v.teleportTo(crushPos.x, crushPos.y, crushPos.z);
                  }

                  StrongSlapHandler.sendLookLockPublic(v, true);
                  UUID finalVicId = victimId;
                  new Thread(() -> {
                     try {
                        Thread.sleep(3000L);
                     } catch (InterruptedException var3x) {
                     }

                     attacker.level().getServer().execute(() -> {
                        ServerPlayer vv = attacker.level().getServer().getPlayerList().getPlayer(finalVicId);
                        if (vv != null) {
                           StrongSlapHandler.sendLookLockPublic(vv, false);
                        }
                     });
                  }).start();
               } else {
                  v.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, false, true));
               }

               v.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 6, false, true));
               float reactYaw = v.getYHeadRot() + 90.0F;
               v.setYHeadRot(reactYaw);
               SlapHandler.CameraFlickPayload flick = new SlapHandler.CameraFlickPayload(victimId, 70.0F);

               for (ServerPlayer p : attacker.level().getServer().getPlayerList().getPlayers()) {
                  ServerPlayNetworking.send(p, flick);
               }

               ServerPlayNetworking.send(v, new SlapHandler.ScreenClosePayload(victimId));
               v.sendOverlayMessage(Component.literal(isJumpSlap ? "§c§l✋ AERIAL SLAP!" : "§c§l✋ I like ya cut G"));
               attacker.sendOverlayMessage(Component.literal(isJumpSlap ? "§6§l✋ JUMP SLAP!" : "§6§l✋ SLAP!"));
               world.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 10, 0.15, 0.1, 0.15, 0.15);
               world.sendParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 4, 0.1, 0.05, 0.1, 0.05);
               world.sendParticles(ParticleTypes.ENCHANTED_HIT, hitPos.x, hitPos.y, hitPos.z, 6, 0.1, 0.1, 0.1, 0.08);
               world.playSound(null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP, SoundSource.PLAYERS, 1.4F, 1.0F);
               world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 2.0F);
            }
         });
      }).start();
   }

   private static void executeFishSlap(ServerPlayer attacker, ServerPlayer victim) {
      ServerLevel world = attacker.level();
      Vec3 diff = victim.position().subtract(attacker.position());
      float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
      attacker.setYRot(yaw);
      attacker.setYBodyRot(yaw);
      attacker.setYHeadRot(yaw);
      attacker.swing(InteractionHand.MAIN_HAND, true);
      attacker.swing(InteractionHand.MAIN_HAND, true);
      PoseNetworking.broadcastAnimState(attacker, 67);
      UUID victimId = victim.getUUID();
      new Thread(() -> {
         try {
            Thread.sleep(130L);
         } catch (InterruptedException var4x) {
         }

         attacker.level().getServer().execute(() -> {
            ServerPlayer v = attacker.level().getServer().getPlayerList().getPlayer(victimId);
            if (v != null) {
               Vec3 hitPos = v.position().add(0.0, 1.6, 0.0);
               world.sendParticles(ParticleTypes.SPLASH, hitPos.x, hitPos.y, hitPos.z, 60, 0.5, 0.3, 0.5, 0.2);
               world.sendParticles(ParticleTypes.DRIPPING_WATER, hitPos.x, hitPos.y, hitPos.z, 40, 0.4, 0.4, 0.4, 0.06);
               world.sendParticles(ParticleTypes.BUBBLE_POP, hitPos.x, hitPos.y, hitPos.z, 30, 0.3, 0.2, 0.3, 0.15);
               world.sendParticles(ParticleTypes.RAIN, hitPos.x, hitPos.y + 1.0, hitPos.z, 50, 0.6, 0.5, 0.6, 0.0);
               world.sendParticles(ParticleTypes.FISHING, hitPos.x, hitPos.y, hitPos.z, 20, 0.3, 0.1, 0.3, 0.12);
               world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 2.5F, 0.7F);
               world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 2.0F, 1.1F);
               world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.5F, 0.9F);
               world.playSound(null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP, SoundSource.PLAYERS, 1.4F, 0.6F);
               Cod cod = (Cod)EntityType.COD.create(world, EntitySpawnReason.MOB_SUMMONED);
               if (cod != null) {
                  cod.setPos(hitPos);
                  cod.setDeltaMovement((Math.random() - 0.5) * 0.4, 0.4 + Math.random() * 0.3, (Math.random() - 0.5) * 0.4);
                  world.addFreshEntity(cod);
               }

               SlapHandler.CameraFlickPayload flick = new SlapHandler.CameraFlickPayload(victimId, 70.0F);

               for (ServerPlayer p : attacker.level().getServer().getPlayerList().getPlayers()) {
                  ServerPlayNetworking.send(p, flick);
               }

               v.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 1, false, true));
               v.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3, false, true));
               ServerPlayNetworking.send(v, new SlapHandler.FishSlapPayload(victimId));
               ServerPlayNetworking.send(v, new SlapHandler.ScreenClosePayload(victimId));
               v.sendOverlayMessage(Component.literal("§b§l\ud83d\udc1f YOU JUST GOT FISH SLAPPED"));
               attacker.sendOverlayMessage(Component.literal("§b§l\ud83d\udc1f FISH SLAP!"));
            }
         });
      }).start();
   }

   private static void executeFrontSlap(ServerPlayer attacker, ServerPlayer victim) {
      ServerLevel world = attacker.level();
      Vec3 diff = victim.position().subtract(attacker.position());
      float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
      attacker.setYRot(yaw);
      attacker.setYBodyRot(yaw);
      attacker.setYHeadRot(yaw);
      attacker.swing(InteractionHand.MAIN_HAND, true);
      attacker.swing(InteractionHand.MAIN_HAND, true);
      PoseNetworking.broadcastAnimState(attacker, 82);
      UUID victimId = victim.getUUID();
      new Thread(() -> {
         try {
            Thread.sleep(250L);
         } catch (InterruptedException var4x) {
         }

         attacker.level().getServer().execute(() -> {
            ServerPlayer v = attacker.level().getServer().getPlayerList().getPlayer(victimId);
            if (v != null) {
               Vec3 hitPos = v.position().add(0.0, 1.7, 0.0);
               v.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 5, false, true));
               SlapHandler.CameraYawFlickPayload yawFlick = new SlapHandler.CameraYawFlickPayload(victimId, 45.0F);

               for (ServerPlayer p : attacker.level().getServer().getPlayerList().getPlayers()) {
                  ServerPlayNetworking.send(p, yawFlick);
               }

               ServerPlayNetworking.send(v, new SlapHandler.ScreenClosePayload(victimId));
               world.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 10, 0.15, 0.1, 0.15, 0.15);
               world.sendParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 4, 0.1, 0.05, 0.1, 0.05);
               world.playSound(null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP, SoundSource.PLAYERS, 1.4F, 0.9F);
               world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.8F);
            }
         });
      }).start();
   }

   public record CameraFlickPayload(UUID playerId, float pitchDelta) implements CustomPacketPayload {
      public static final Type<SlapHandler.CameraFlickPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "camera_flick"));
      public static final StreamCodec<FriendlyByteBuf, SlapHandler.CameraFlickPayload> CODEC = StreamCodec.ofMember((val, buf) -> {
         buf.writeUUID(val.playerId());
         buf.writeFloat(val.pitchDelta());
      }, buf -> new SlapHandler.CameraFlickPayload(buf.readUUID(), buf.readFloat()));

      public Type<SlapHandler.CameraFlickPayload> type() {
         return ID;
      }
   }

   public record CameraYawFlickPayload(UUID playerId, float yawDelta) implements CustomPacketPayload {
      public static final Type<SlapHandler.CameraYawFlickPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "camera_yaw_flick"));
      public static final StreamCodec<FriendlyByteBuf, SlapHandler.CameraYawFlickPayload> CODEC = StreamCodec.ofMember((val, buf) -> {
         buf.writeUUID(val.playerId());
         buf.writeFloat(val.yawDelta());
      }, buf -> new SlapHandler.CameraYawFlickPayload(buf.readUUID(), buf.readFloat()));

      public Type<SlapHandler.CameraYawFlickPayload> type() {
         return ID;
      }
   }

   public record FishSlapPayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<SlapHandler.FishSlapPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "fish_slap"));
      public static final StreamCodec<FriendlyByteBuf, SlapHandler.FishSlapPayload> CODEC = StreamCodec.ofMember(
         (val, buf) -> buf.writeUUID(val.playerId()), buf -> new SlapHandler.FishSlapPayload(buf.readUUID())
      );

      public Type<SlapHandler.FishSlapPayload> type() {
         return ID;
      }
   }

   public record ScreenClosePayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<SlapHandler.ScreenClosePayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "screen_close"));
      public static final StreamCodec<FriendlyByteBuf, SlapHandler.ScreenClosePayload> CODEC = StreamCodec.ofMember(
         (val, buf) -> buf.writeUUID(val.playerId()), buf -> new SlapHandler.ScreenClosePayload(buf.readUUID())
      );

      public Type<SlapHandler.ScreenClosePayload> type() {
         return ID;
      }
   }
}
