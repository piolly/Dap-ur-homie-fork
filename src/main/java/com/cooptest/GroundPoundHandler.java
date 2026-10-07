package com.cooptest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GroundPoundHandler {
   private static final double DIVE_SPEED = -3.5;
   private static final double AOE_RADIUS = 8.0;
   private static final double KB_STRENGTH = 2.2;
   private static final long LAND_STUN_MS = 500L;
   private static final int MIN_HEIGHT_BLOCKS = 3;
   private static final int ANIM_DIVE = 65;
   private static final int ANIM_LAND = 66;
   private static final Set<UUID> diving = new HashSet<>();
   private static final Map<UUID, Double> diveStartY = new HashMap<>();
   private static final Map<UUID, Long> landStunEnd = new HashMap<>();
   private static final Map<UUID, Long> diveStartTime = new HashMap<>();
   private static final long MAX_DIVE_MS = 15000L;
   static final Set<UUID> megaPound = new HashSet<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(GroundPoundHandler.GroundPoundStartPayload.ID, GroundPoundHandler.GroundPoundStartPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(GroundPoundHandler.GroundPoundSyncPayload.ID, GroundPoundHandler.GroundPoundSyncPayload.CODEC);
   }

   public static void register() {
      registerPayloads();
      ServerPlayNetworking.registerGlobalReceiver(
         GroundPoundHandler.GroundPoundStartPayload.ID,
         (payload, context) -> {
            ServerPlayer player = context.player();
            context.server()
               .execute(
                  () -> {
                     if (CoopMovesConfig.get().enableGroundPound) {
                        UUID id = player.getUUID();
                        if (!diving.contains(id)) {
                           if (!GrabMechanic.heldBy.containsKey(id)) {
                              if (!player.onGround() || SpinHandler.isSpinning(id)) {
                                 if (SpinHandler.isSpinning(id)) {
                                    SpinHandler.stopSpinKeepRider(player.level().getServer(), id);
                                 }

                                 diving.add(id);
                                 diveStartY.put(id, player.getY());
                                 diveStartTime.put(id, System.currentTimeMillis());
                                 player.setDeltaMovement(0.0, -3.5, 0.0);
                                 player.syncVelocity = true;
                                 PoseNetworking.broadcastAnimState(player, 65);
                                 broadcastDiveSync(player.level().getServer(), id, true);
                                 player.level()
                                    .playSound(
                                       null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 0.6F
                                    );
                              }
                           }
                        }
                     }
                  }
               );
         }
      );
   }

   public static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      Iterator<Entry<UUID, Long>> stunIt = landStunEnd.entrySet().iterator();

      while (stunIt.hasNext()) {
         Entry<UUID, Long> e = stunIt.next();
         if (now >= e.getValue()) {
            stunIt.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
            }
         }
      }

      Iterator<UUID> it = diving.iterator();

      while (it.hasNext()) {
         UUID id = it.next();
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player == null) {
            it.remove();
            cleanupDiveMaps(id);
         } else {
            player.fallDistance = 0.0;
            Long dStart = diveStartTime.get(id);
            if (dStart != null && System.currentTimeMillis() - dStart > 15000L) {
               it.remove();
               cleanupDiveMaps(id);
               broadcastDiveSync(server, id, false);
               PoseNetworking.broadcastAnimState(player, 0);
            } else {
               boolean landed = player.onGround() || player.isInWater() || isCloseToGroundFalling(player);
               if (landed) {
                  it.remove();
                  double heightFallen = Math.max(0.0, diveStartY.getOrDefault(id, player.getY()) - player.getY());
                  cleanupDiveMaps(id);
                  UUID riderId = SpinHandler.pendingGroundPoundRider.remove(id);
                  if (riderId != null) {
                     SpinHandler.detachRiderByIds(player.level().getServer(), id, riderId);
                  }

                  executeImpact(player, heightFallen);
                  broadcastDiveSync(server, id, false);
               } else {
                  Vec3 vel = player.getDeltaMovement();
                  player.setDeltaMovement(vel.x * 0.1, -3.5, vel.z * 0.1);
                  player.syncVelocity = true;
                  player.setYRot(player.getYRot());
               }
            }
         }
      }
   }

   private static void executeImpact(ServerPlayer player, double heightFallen) {
      ServerLevel world = player.level();
      Vec3 pos = player.position();
      UUID id = player.getUUID();
      boolean isMega = megaPound.remove(id);
      double rawPower = Math.min(1.0, heightFallen / 10.0);
      double scaledPower = 1.0 - Math.exp(-rawPower * 2.5);
      if (heightFallen < 3.0) {
         scaledPower *= 0.3;
      }

      double megaMult = isMega ? 2.5 : 1.0;
      double aoeRadius = isMega ? 14.0 : 8.0;
      double kbMult = (0.5 + scaledPower * 1.5) * megaMult;
      double upwardPop = (0.3 + scaledPower * 0.5) * (isMega ? 1.8 : 1.0);
      AABB aoeBox = player.getBoundingBox().inflate(aoeRadius);

      for (Entity entity : world.getEntities(player, aoeBox, e -> e instanceof LivingEntity && !e.isRemoved())) {
         double dx = entity.getX() - pos.x;
         double dz = entity.getZ() - pos.z;
         double dist = Math.sqrt(dx * dx + dz * dz);
         if (!(dist > aoeRadius)) {
            double falloff = 1.0 - dist / aoeRadius;
            if (entity instanceof LivingEntity living) {
               double nx = dist > 0.01 ? dx / dist : 0.0;
               double nz = dist > 0.01 ? dz / dist : 0.0;
               living.setDeltaMovement(nx * 2.2 * falloff * kbMult, upwardPop * falloff, nz * 2.2 * falloff * kbMult);
               living.syncVelocity = true;
               double dmg = scaledPower * (isMega ? 8.0 : 4.0) * falloff;
               if (dmg > 0.5) {
                  living.hurtServer(world, world.damageSources().playerAttack(player), (float)dmg);
               }
            }
         }
      }

      float explosionPower = isMega ? 6.0F : 3.5F;
      world.explode(player, pos.x, pos.y, pos.z, explosionPower, false, ExplosionInteraction.TNT);
      int rings = (int)(scaledPower * 3.0) + 1;

      for (int ring = 1; ring <= rings; ring++) {
         double radius = ring * 2.0;
         int count = ring * 12;

         for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2) / count * i;
            double rx = pos.x + Math.cos(angle) * radius;
            double rz = pos.z + Math.sin(angle) * radius;
            world.sendParticles(ParticleTypes.EXPLOSION, rx, pos.y + 0.1, rz, 1, 0.0, 0.0, 0.0, 0.0);
            world.sendParticles(ParticleTypes.SWEEP_ATTACK, rx, pos.y + 0.1, rz, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }

      world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.3, pos.z, 2, 0.3, 0.0, 0.3, 0.0);
      world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.5, pos.z, 20, 0.8, 0.5, 0.8, 0.2);
      world.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.2, pos.z, 8, 0.4, 0.1, 0.4, 0.02);

      for (int i = 0; i < 24; i++) {
         double a = (Math.PI / 12) * i;
         double r = 1.5 + scaledPower;
         world.sendParticles(
            ParticleTypes.CLOUD, pos.x + Math.cos(a) * r, pos.y + 0.1, pos.z + Math.sin(a) * r, 1, Math.cos(a) * 0.2, 0.1, Math.sin(a) * 0.2, 0.02
         );
      }

      world.playSound(
         null,
         pos.x,
         pos.y,
         pos.z,
         (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(),
         SoundSource.PLAYERS,
         0.8F + (float)scaledPower * 0.4F,
         0.7F - (float)scaledPower * 0.2F
      );
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 0.6F);
      if (scaledPower > 0.5) {
         world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.0F, 0.8F);
      }

      PoseNetworking.broadcastAnimState(player, 66);
      landStunEnd.put(player.getUUID(), System.currentTimeMillis() + 500L);
      player.setDeltaMovement(0.0, 0.0, 0.0);
      player.syncVelocity = true;
      if (scaledPower >= 0.6) {
         player.sendOverlayMessage(Component.literal("§c§l\ud83d\udca5 GROUND POUND!"));
      } else {
         player.sendOverlayMessage(Component.literal("§e\ud83d\udca5 Ground Pound"));
      }
   }

   private static boolean isCloseToGroundFalling(ServerPlayer player) {
      if (player.getDeltaMovement().y >= 0.0) {
         return false;
      }

      Vec3 pos = player.position();

      for (int i = 1; i <= 3; i++) {
         BlockState block = player.level().getBlockState(BlockPos.containing(pos.x, pos.y - i * 0.5, pos.z));
         if (!block.isAir()) {
            return true;
         }
      }

      return false;
   }

   private static void broadcastDiveSync(MinecraftServer server, UUID id, boolean divingState) {
      GroundPoundHandler.GroundPoundSyncPayload pkt = new GroundPoundHandler.GroundPoundSyncPayload(id, divingState);

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(p, pkt);
      }
   }

   private static void cleanupDiveMaps(UUID id) {
      diveStartY.remove(id);
      diveStartTime.remove(id);
   }

   public static boolean isDiving(UUID id) {
      return diving.contains(id);
   }

   public static void markMegaPound(UUID id) {
      megaPound.add(id);
   }

   public static void cleanup(UUID id) {
      diving.remove(id);
      cleanupDiveMaps(id);
      landStunEnd.remove(id);
      megaPound.remove(id);
      SpinHandler.pendingGroundPoundRider.remove(id);
   }

   public record GroundPoundStartPayload() implements CustomPacketPayload {
      public static final Type<GroundPoundHandler.GroundPoundStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "ground_pound_start"));
      public static final StreamCodec<FriendlyByteBuf, GroundPoundHandler.GroundPoundStartPayload> CODEC = StreamCodec.unit(
         new GroundPoundHandler.GroundPoundStartPayload()
      );

      public Type<GroundPoundHandler.GroundPoundStartPayload> type() {
         return ID;
      }
   }

   public record GroundPoundSyncPayload(UUID playerId, boolean diving) implements CustomPacketPayload {
      public static final Type<GroundPoundHandler.GroundPoundSyncPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "ground_pound_sync"));
      public static final StreamCodec<FriendlyByteBuf, GroundPoundHandler.GroundPoundSyncPayload> CODEC = StreamCodec.ofMember((val, buf) -> {
         buf.writeUUID(val.playerId());
         buf.writeBoolean(val.diving());
      }, buf -> new GroundPoundHandler.GroundPoundSyncPayload(buf.readUUID(), buf.readBoolean()));

      public Type<GroundPoundHandler.GroundPoundSyncPayload> type() {
         return ID;
      }
   }
}
