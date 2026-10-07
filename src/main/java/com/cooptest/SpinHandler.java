package com.cooptest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SpinHandler {
   private static final float YAW_PER_TICK = 22.0F;
   private static final double GRAVITY_CAP = -0.15;
   private static final long MAX_SPIN_MS = 30000L;
   private static final int SOUND_INTERVAL = 12;
   private static final double HELICOPTER_H_RANGE = 2.5;
   private static final double HELICOPTER_V_RANGE = 2.0;
   private static final int ANIM_SPIN = 64;
   private static final int ANIM_NONE = 0;
   private static final Set<UUID> activeSpin = new HashSet<>();
   private static final Map<UUID, Long> spinStartTime = new HashMap<>();
   private static final Map<UUID, Float> spinYaw = new HashMap<>();
   private static final Map<UUID, Integer> spinTick = new HashMap<>();
   private static final Map<UUID, UUID> helicopterRider = new HashMap<>();
   private static final Map<UUID, UUID> helicopterSpinner = new HashMap<>();
   static final Map<UUID, UUID> pendingGroundPoundRider = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(SpinHandler.SpinStartPayload.ID, SpinHandler.SpinStartPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(SpinHandler.SpinStopPayload.ID, SpinHandler.SpinStopPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SpinHandler.SpinSyncPayload.ID, SpinHandler.SpinSyncPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SpinHandler.HelicopterLaunchPayload.ID, SpinHandler.HelicopterLaunchPayload.CODEC);
   }

   public static void register() {
      registerPayloads();
      ServerPlayNetworking.registerGlobalReceiver(
         SpinHandler.SpinStartPayload.ID,
         (payload, context) -> {
            ServerPlayer player = context.player();
            context.server()
               .execute(
                  () -> {
                     if (CoopMovesConfig.get().enableSpin) {
                        UUID id = player.getUUID();
                        PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                        if (pose == PoseState.GRABBED) {
                           if (!GrabMechanic.heldBy.containsKey(id)) {
                              if (!activeSpin.contains(id)) {
                                 activeSpin.add(id);
                                 spinStartTime.put(id, System.currentTimeMillis());
                                 spinYaw.put(id, player.yBodyRot);
                                 spinTick.put(id, 0);
                                 DapHoldHandler.forceUnfreeze(player.level().getServer(), id);
                                 PoseNetworking.broadcastAnimState(player, 0);
                                 PoseNetworking.broadcastAnimState(player, 64);
                                 broadcastSpinSync(player.level().getServer(), id, true);
                                 player.level()
                                    .playSound(
                                       null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.6F
                                    );
                              }
                           }
                        }
                     }
                  }
               );
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(SpinHandler.SpinStopPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> stopSpin(player.level().getServer(), player.getUUID()));
      });
   }

   public static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      Iterator<UUID> it = activeSpin.iterator();

      while (it.hasNext()) {
         UUID id = it.next();
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player == null) {
            it.remove();
            cleanupSpinMaps(id);
         } else {
            PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            if (GrabMechanic.heldBy.containsKey(id)) {
               it.remove();
               cleanupSpinMaps(id);
               detachHelicopterRider(server, id);
               broadcastSpinSync(server, id, false);
               PoseNetworking.broadcastAnimState(player, 0);
            } else if (!player.onGround() && !player.isInWater()) {
               long elapsed = now - spinStartTime.getOrDefault(id, now);
               if (elapsed >= 30000L) {
                  it.remove();
                  cleanupSpinMaps(id);
                  detachHelicopterRider(server, id);
                  broadcastSpinSync(server, id, false);
                  PoseNetworking.broadcastAnimState(player, 0);
               } else {
                  boolean hasRider = helicopterRider.containsKey(id);
                  float currentYaw = spinYaw.getOrDefault(id, player.yBodyRot);
                  if (!hasRider) {
                     float newYaw = currentYaw + 22.0F;
                     if (newYaw > 180.0F) {
                        newYaw -= 360.0F;
                     }

                     spinYaw.put(id, newYaw);
                     currentYaw = newYaw;
                     player.setYRot(currentYaw);
                     player.setYBodyRot(currentYaw);
                     player.setYHeadRot(currentYaw);
                  }

                  Vec3 vel = player.getDeltaMovement();
                  if (vel.y < -0.15) {
                     player.setDeltaMovement(vel.x, -0.15, vel.z);
                     player.hurtMarked = true;
                  }

                  Vec3 pos = player.position().add(0.0, 0.9, 0.0);
                  double angle = Math.toRadians(currentYaw);
                  player.level()
                     .sendParticles(ParticleTypes.CLOUD, pos.x + Math.cos(angle) * 0.5, pos.y, pos.z + Math.sin(angle) * 0.5, 2, 0.05, 0.05, 0.05, 0.01);
                  int tck = spinTick.merge(id, 1, Integer::sum);
                  if (tck % 12 == 0) {
                     player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.5F, 1.4F);
                  }

                  if (!hasRider) {
                     checkHelicopterSweep(server, player);
                  }
               }
            } else {
               it.remove();
               cleanupSpinMaps(id);
               detachHelicopterRider(server, id);
               broadcastSpinSync(server, id, false);
               PoseNetworking.broadcastAnimState(player, 0);
            }
         }
      }
   }

   private static void checkHelicopterSweep(MinecraftServer server, ServerPlayer spinner) {
      UUID spinnerId = spinner.getUUID();
      Vec3 sPos = spinner.position();
      ServerLevel world = spinner.level();
      AABB sweepBox = new AABB(sPos.x - 2.5, sPos.y - 0.5, sPos.z - 2.5, sPos.x + 2.5, sPos.y + 2.0, sPos.z + 2.5);

      for (ServerPlayer entity : world.getEntitiesOfClass(ServerPlayer.class, sweepBox)) {
         if (entity != spinner) {
            ServerPlayer target = entity;
            UUID targetId = target.getUUID();
            if (target.onGround()
               && !helicopterSpinner.containsKey(targetId)
               && !GrabMechanic.holding.containsKey(spinnerId)
               && !GrabMechanic.heldBy.containsKey(targetId)) {
               target.startRiding(spinner, true, true);
               helicopterRider.put(spinnerId, targetId);
               helicopterSpinner.put(targetId, spinnerId);
               ClientboundSetPassengersPacket pkt = new ClientboundSetPassengersPacket(spinner);

               for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                  p.connection.send(pkt);
               }

               spinner.setDeltaMovement(0.0, 4.0, 0.0);
               spinner.hurtMarked = true;
               GroundPoundHandler.markMegaPound(spinnerId);
               SpinHandler.HelicopterLaunchPayload launchPkt = new SpinHandler.HelicopterLaunchPayload(spinnerId, targetId);

               for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                  ServerPlayNetworking.send(p, launchPkt);
               }

               world.sendParticles(ParticleTypes.SWEEP_ATTACK, sPos.x, sPos.y + 1.2, sPos.z, 8, 0.4, 0.2, 0.4, 0.1);
               world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sPos.x, sPos.y + 0.5, sPos.z, 15, 0.5, 0.5, 0.5, 0.3);
               world.sendParticles(ParticleTypes.EXPLOSION, sPos.x, sPos.y + 0.5, sPos.z, 3, 0.3, 0.3, 0.3, 0.0);
               world.playSound(null, sPos.x, sPos.y, sPos.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2F, 0.7F);
               world.playSound(null, sPos.x, sPos.y, sPos.z, ModSounds.HELI, SoundSource.PLAYERS, 1.0F, 1.0F);
               world.playSound(null, sPos.x, sPos.y, sPos.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 0.8F, 0.5F);
               spinner.sendOverlayMessage(Component.literal("§c§l\ud83d\ude80 HELICOPTER! Press SHIFT for MEGA GROUND POUND!"));
               target.sendOverlayMessage(Component.literal("§c§l\ud83d\ude80 You're riding the helicopter!"));
               break;
            }
         }
      }
   }

   private static void doHelicopterLaunch(MinecraftServer server, ServerPlayer spinner) {
      UUID spinnerId = spinner.getUUID();
      UUID riderId = helicopterRider.get(spinnerId);
      if (riderId != null) {
         ServerPlayer rider = server.getPlayerList().getPlayer(riderId);
         if (rider != null) {
            rider.stopRiding();
         }

         helicopterRider.remove(spinnerId);
         helicopterSpinner.remove(riderId);
         ClientboundSetPassengersPacket pkt = new ClientboundSetPassengersPacket(spinner);

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(pkt);
         }

         activeSpin.remove(spinnerId);
         cleanupSpinMaps(spinnerId);
         broadcastSpinSync(server, spinnerId, false);
         PoseNetworking.broadcastAnimState(spinner, 0);
         Vec3 upVel = new Vec3(0.0, 3.0, 0.0);
         spinner.setDeltaMovement(upVel);
         spinner.hurtMarked = true;
         if (rider != null) {
            rider.setDeltaMovement(upVel.add(0.0, 0.15, 0.0));
            rider.hurtMarked = true;
            GroundPoundHandler.markMegaPound(spinnerId);
         }

         SpinHandler.HelicopterLaunchPayload launchPkt = new SpinHandler.HelicopterLaunchPayload(spinnerId, riderId != null ? riderId : spinnerId);

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, launchPkt);
         }

         ServerLevel world = spinner.level();
         Vec3 pos = spinner.position().add(0.0, 1.0, 0.0);
         world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.3, 0.3, 0.3, 0.0);
         world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 20, 0.5, 0.5, 0.5, 0.3);
         world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2F, 0.7F);
         world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 0.8F, 0.5F);
         spinner.sendOverlayMessage(Component.literal("§c§l\ud83d\ude80 HELICOPTER LAUNCH!"));
         if (rider != null) {
            rider.sendOverlayMessage(Component.literal("§c§l\ud83d\ude80 Helicopter launched!"));
         }
      }
   }

   private static void detachHelicopterRider(MinecraftServer server, UUID spinnerId) {
      UUID riderId = helicopterRider.remove(spinnerId);
      if (riderId != null) {
         helicopterSpinner.remove(riderId);
         ServerPlayer rider = server.getPlayerList().getPlayer(riderId);
         ServerPlayer spinner = server.getPlayerList().getPlayer(spinnerId);
         if (rider != null) {
            rider.stopRiding();
            rider.teleportTo(rider.getX(), rider.getY(), rider.getZ());
         }

         if (spinner != null) {
            ClientboundSetPassengersPacket pkt = new ClientboundSetPassengersPacket(spinner);

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
               p.connection.send(pkt);
            }
         }

         if (rider != null) {
            ClientboundSetPassengersPacket pkt2 = new ClientboundSetPassengersPacket(rider);

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
               p.connection.send(pkt2);
            }
         }
      }
   }

   public static void stopSpinKeepRider(MinecraftServer server, UUID id) {
      if (activeSpin.remove(id)) {
         UUID riderId = helicopterRider.remove(id);
         if (riderId != null) {
            helicopterSpinner.remove(riderId);
            pendingGroundPoundRider.put(id, riderId);
         }

         cleanupSpinMaps(id);
         broadcastSpinSync(server, id, false);
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player != null) {
            PoseNetworking.broadcastAnimState(player, 0);
         }
      }
   }

   public static void stopSpin(MinecraftServer server, UUID id) {
      if (activeSpin.remove(id)) {
         cleanupSpinMaps(id);
         detachHelicopterRider(server, id);
         broadcastSpinSync(server, id, false);
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player != null) {
            PoseNetworking.broadcastAnimState(player, 0);
         }
      }
   }

   public static void detachRiderByIds(MinecraftServer server, UUID spinnerId, UUID riderId) {
      helicopterSpinner.remove(riderId);
      ServerPlayer rider = server.getPlayerList().getPlayer(riderId);
      ServerPlayer spinner = server.getPlayerList().getPlayer(spinnerId);
      if (rider != null) {
         rider.stopRiding();
         rider.teleportTo(rider.getX(), rider.getY(), rider.getZ());
      }

      if (spinner != null) {
         ClientboundSetPassengersPacket pkt = new ClientboundSetPassengersPacket(spinner);

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(pkt);
         }
      }

      if (rider != null) {
         ClientboundSetPassengersPacket pkt2 = new ClientboundSetPassengersPacket(rider);

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(pkt2);
         }
      }
   }

   public static boolean isSpinning(UUID id) {
      return activeSpin.contains(id);
   }

   public static boolean hasHelicopterRider(UUID spinnerId) {
      return helicopterRider.containsKey(spinnerId);
   }

   private static void cleanupSpinMaps(UUID id) {
      spinStartTime.remove(id);
      spinYaw.remove(id);
      spinTick.remove(id);
   }

   private static void broadcastSpinSync(MinecraftServer server, UUID id, boolean spinning) {
      SpinHandler.SpinSyncPayload pkt = new SpinHandler.SpinSyncPayload(id, spinning);

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(p, pkt);
      }
   }

   public static void cleanup(UUID id) {
      activeSpin.remove(id);
      cleanupSpinMaps(id);
      UUID riderId = helicopterRider.remove(id);
      if (riderId != null) {
         helicopterSpinner.remove(riderId);
      }

      helicopterSpinner.remove(id);
      pendingGroundPoundRider.remove(id);
   }

   public record HelicopterLaunchPayload(UUID spinnerId, UUID riderId) implements CustomPacketPayload {
      public static final Type<SpinHandler.HelicopterLaunchPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "helicopter_launch"));
      public static final StreamCodec<FriendlyByteBuf, SpinHandler.HelicopterLaunchPayload> CODEC = StreamCodec.ofMember((val, buf) -> {
         buf.writeUUID(val.spinnerId());
         buf.writeUUID(val.riderId());
      }, buf -> new SpinHandler.HelicopterLaunchPayload(buf.readUUID(), buf.readUUID()));

      public Type<SpinHandler.HelicopterLaunchPayload> type() {
         return ID;
      }
   }

   public record SpinStartPayload() implements CustomPacketPayload {
      public static final Type<SpinHandler.SpinStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "spin_start"));
      public static final StreamCodec<FriendlyByteBuf, SpinHandler.SpinStartPayload> CODEC = StreamCodec.unit(new SpinHandler.SpinStartPayload());

      public Type<SpinHandler.SpinStartPayload> type() {
         return ID;
      }
   }

   public record SpinStopPayload() implements CustomPacketPayload {
      public static final Type<SpinHandler.SpinStopPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "spin_stop"));
      public static final StreamCodec<FriendlyByteBuf, SpinHandler.SpinStopPayload> CODEC = StreamCodec.unit(new SpinHandler.SpinStopPayload());

      public Type<SpinHandler.SpinStopPayload> type() {
         return ID;
      }
   }

   public record SpinSyncPayload(UUID playerId, boolean spinning) implements CustomPacketPayload {
      public static final Type<SpinHandler.SpinSyncPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "spin_sync"));
      public static final StreamCodec<FriendlyByteBuf, SpinHandler.SpinSyncPayload> CODEC = StreamCodec.ofMember((val, buf) -> {
         buf.writeUUID(val.playerId());
         buf.writeBoolean(val.spinning());
      }, buf -> new SpinHandler.SpinSyncPayload(buf.readUUID(), buf.readBoolean()));

      public Type<SpinHandler.SpinSyncPayload> type() {
         return ID;
      }
   }
}
