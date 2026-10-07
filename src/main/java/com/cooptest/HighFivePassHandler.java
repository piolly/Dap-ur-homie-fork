package com.cooptest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.Vec3;

public class HighFivePassHandler {
   public static final long IMPACT_DELAY_MS = 210L;
   public static final long SLOW_START_MS = 290L;
   public static final long ANIM_END_MS = 1958L;
   public static final float FLINCH_STRENGTH = 0.06F;
   public static final boolean ENABLE_FLINCH = false;
   public static final double SLOW_MULTIPLIER = -0.25;
   public static final double MAX_REPOSITION = 1.5;
   public static final long REPOSITION_DURATION_MS = 250L;
   public static final boolean PASS_TP_DEBUG = false;
   private static final Identifier PASS_SLOW_ID = Identifier.fromNamespaceAndPath("testcoop", "highfive_pass_slow");
   private static final List<HighFivePassHandler.PendingPass> pendingFlinch = new ArrayList<>();
   private static final List<HighFivePassHandler.PendingPass> pendingSlow = new ArrayList<>();
   private static final List<HighFivePassHandler.PendingPass> pendingCleanup = new ArrayList<>();
   private static final List<HighFivePassHandler.PendingReposition> pendingReposition = new ArrayList<>();
   public static final Identifier PASS_START_ID = Identifier.fromNamespaceAndPath("testcoop", "highfive_pass_start");

   public static void registerPayloads() {
      PayloadTypeRegistry.playS2C().register(HighFivePassHandler.HighFivePassStartPayload.ID, HighFivePassHandler.HighFivePassStartPayload.CODEC);
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         long now = System.currentTimeMillis();
         Iterator<HighFivePassHandler.PendingReposition> reposIt = pendingReposition.iterator();

         while (reposIt.hasNext()) {
            HighFivePassHandler.PendingReposition r = reposIt.next();
            if (r.remainingTicks <= 0) {
               reposIt.remove();
            } else {
               Vec3 step = r.remainingDelta.scale(1.0 / r.remainingTicks);
               Vec3 newPos = r.player.position().add(step);
               r.player.teleportTo(newPos.x, newPos.y, newPos.z);
               r.remainingDelta = r.remainingDelta.subtract(step);
               r.remainingTicks--;
               if (r.remainingTicks <= 0) {
                  reposIt.remove();
               }
            }
         }

         Iterator<HighFivePassHandler.PendingPass> flinchIt = pendingFlinch.iterator();

         while (flinchIt.hasNext()) {
            HighFivePassHandler.PendingPass pass = flinchIt.next();
            if (now >= pass.fireTime()) {
               spawnImpactEffects(pass.p1(), pass.p2());
               flinchIt.remove();
            }
         }

         Iterator<HighFivePassHandler.PendingPass> slowIt = pendingSlow.iterator();

         while (slowIt.hasNext()) {
            HighFivePassHandler.PendingPass pass = slowIt.next();
            if (now >= pass.fireTime()) {
               applySlow(pass.p1());
               applySlow(pass.p2());
               slowIt.remove();
            }
         }

         Iterator<HighFivePassHandler.PendingPass> cleanupIt = pendingCleanup.iterator();

         while (cleanupIt.hasNext()) {
            HighFivePassHandler.PendingPass pass = cleanupIt.next();
            if (now >= pass.fireTime()) {
               removeSlow(pass.p1());
               removeSlow(pass.p2());
               cleanupIt.remove();
            }
         }
      });
   }

   public static void executePassHighFive(ServerPlayer p1, ServerPlayer p2, Vec3 targetP1, Vec3 targetP2) {
      long now = System.currentTimeMillis();
      repositionForTouch(p1, p2, targetP1, targetP2);
      PoseNetworking.broadcastAnimState(p1, 105);
      PoseNetworking.broadcastAnimState(p2, 105);
      HighFivePassHandler.HighFivePassStartPayload startPayload = new HighFivePassHandler.HighFivePassStartPayload();
      ServerPlayNetworking.send(p1, startPayload);
      ServerPlayNetworking.send(p2, startPayload);
      pendingFlinch.add(new HighFivePassHandler.PendingPass(p1, p2, now + 210L));
      pendingSlow.add(new HighFivePassHandler.PendingPass(p1, p2, now + 290L));
      pendingCleanup.add(new HighFivePassHandler.PendingPass(p1, p2, now + 1958L));
   }

   private static void repositionForTouch(ServerPlayer p1, ServerPlayer p2, Vec3 targetP1, Vec3 targetP2) {
      Vec3 pos1 = p1.position();
      Vec3 pos2 = p2.position();
      double dist1 = Math.hypot(targetP1.x - pos1.x, targetP1.z - pos1.z);
      double dist2 = Math.hypot(targetP2.x - pos2.x, targetP2.z - pos2.z);
      boolean skipped = dist1 > 1.5 || dist2 > 1.5;
      if (!skipped) {
         int totalTicks = (int)Math.max(1L, 5L);
         Vec3 delta1 = new Vec3(targetP1.x - pos1.x, 0.0, targetP1.z - pos1.z);
         Vec3 delta2 = new Vec3(targetP2.x - pos2.x, 0.0, targetP2.z - pos2.z);
         pendingReposition.add(new HighFivePassHandler.PendingReposition(p1, delta1, totalTicks));
         pendingReposition.add(new HighFivePassHandler.PendingReposition(p2, delta2, totalTicks));
      }
   }

   private static String fmt(Vec3 v) {
      return "(" + String.format("%.2f", v.x) + "," + String.format("%.2f", v.z) + ")";
   }

   private static void spawnImpactEffects(ServerPlayer p1, ServerPlayer p2) {
      ServerLevel world = p1.level();
      Vec3 pos = p1.position().add(p2.position()).scale(0.5).add(0.0, 1.4, 0.0);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 1.1F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.8F);
      spawnStarBurst(world, pos, 10, 0.3);
      world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.08);
      world.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.02);
   }

   private static void spawnStarBurst(ServerLevel world, Vec3 pos, int rays, double spread) {
      for (int i = 0; i < rays; i++) {
         double angle = (Math.PI * 2) * i / rays;
         double dx = Math.cos(angle) * spread;
         double dz = Math.sin(angle) * spread;
         world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
      }
   }

   private static void applyFlinch(ServerPlayer player) {
      double yawRad = Math.toRadians(player.getYRot());
      Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
      Vec3 current = player.getDeltaMovement();
      player.setDeltaMovement(current.x - forward.x * 0.06F, current.y, current.z - forward.z * 0.06F);
      player.hurtMarked = true;
   }

   private static void applySlow(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(PASS_SLOW_ID);
         attr.addTransientModifier(new AttributeModifier(PASS_SLOW_ID, -0.25, Operation.ADD_MULTIPLIED_TOTAL));
      }
   }

   private static void removeSlow(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(PASS_SLOW_ID);
      }
   }

   public static void cleanup(UUID playerId) {
   }

   public record HighFivePassStartPayload() implements CustomPacketPayload {
      public static final Type<HighFivePassHandler.HighFivePassStartPayload> ID = new Type(HighFivePassHandler.PASS_START_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFivePassHandler.HighFivePassStartPayload> CODEC = StreamCodec.unit(
         new HighFivePassHandler.HighFivePassStartPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private record PendingPass(ServerPlayer p1, ServerPlayer p2, long fireTime) {
   }

   private static final class PendingReposition {
      final ServerPlayer player;
      Vec3 remainingDelta;
      int remainingTicks;

      PendingReposition(ServerPlayer player, Vec3 remainingDelta, int remainingTicks) {
         this.player = player;
         this.remainingDelta = remainingDelta;
         this.remainingTicks = remainingTicks;
      }
   }
}
