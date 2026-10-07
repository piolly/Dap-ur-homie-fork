package com.cooptest.meme;

import com.cooptest.PoseNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SpinYeetHandler {
   private static final int ANIM_NONE = 0;
   private static final int ANIM_SPIN_YEET_GRABBER = 106;
   private static final int ANIM_SPIN_YEET_GRABBED = 107;
   private static final Map<UUID, SpinYeetSession> grabberToSession = new HashMap<>();
   private static final Map<UUID, UUID> grabbedToGrabber = new HashMap<>();
   private static final Map<UUID, Integer> yeetedTicks = new HashMap<>();
   private static final Map<UUID, ServerLevel> yeetedWorlds = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(SpinYeetGrabPayload.ID, SpinYeetGrabPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(SpinYeetReleasePayload.ID, SpinYeetReleasePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SpinYeetStartPayload.ID, SpinYeetStartPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SpinYeetCameraPayload.ID, SpinYeetCameraPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SpinYeetEndPayload.ID, SpinYeetEndPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SpinYeetGrabberYawPayload.ID, SpinYeetGrabberYawPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(SpinYeetImpactPayload.ID, SpinYeetImpactPayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(
         SpinYeetGrabPayload.ID,
         (payload, ctx) -> {
            ServerPlayer grabber = ctx.player();
            System.out
               .println(
                  "[SpinYeet-DEBUG] Grab packet received from "
                     + grabber.getName().getString()
                     + " | enabled="
                     + SpinYeetConfig.ENABLED
                     + " | alreadyGrabbing="
                     + grabberToSession.containsKey(grabber.getUUID())
               );
            if (!SpinYeetConfig.ENABLED) {
               System.out.println("[SpinYeet-DEBUG] BLOCKED — set enabled=true in coopmoves_spinyeet.properties");
            } else if (!grabberToSession.containsKey(grabber.getUUID())) {
               ServerLevel world = grabber.level();
               Entity target = world.getEntity(payload.targetUUID());
               if (target == null) {
                  System.out.println("[SpinYeet-DEBUG] BLOCKED — target UUID not found in server world");
               } else {
                  double distSq = target.distanceToSqr(grabber);
                  System.out.println("[SpinYeet-DEBUG] Target=" + target.getName().getString() + " distSq=" + String.format("%.2f", distSq) + " max=4.0");
                  if (target instanceof LivingEntity grabbed) {
                     if (distSq > 4.0) {
                        System.out.println("[SpinYeet-DEBUG] BLOCKED — out of range");
                     } else if (grabbedToGrabber.containsKey(target.getUUID())) {
                        System.out.println("[SpinYeet-DEBUG] BLOCKED — already grabbed");
                     } else {
                        System.out.println("[SpinYeet-DEBUG] Starting spin session!");
                        SpinYeetSession session = new SpinYeetSession(grabber.getUUID(), target.getUUID());
                        session.spinAngle = (float)Math.atan2(target.getZ() - grabber.getZ(), target.getX() - grabber.getX());
                        grabberToSession.put(grabber.getUUID(), session);
                        grabbedToGrabber.put(target.getUUID(), grabber.getUUID());
                        PoseNetworking.broadcastAnimState(grabber, 106);
                        if (grabbed instanceof ServerPlayer sp) {
                           PoseNetworking.broadcastAnimState(sp, 107);
                        }

                        broadcast(world, grabber, new SpinYeetStartPayload(grabber.getUUID(), target.getUUID()));
                     }
                  } else {
                     System.out.println("[SpinYeet-DEBUG] BLOCKED — not a LivingEntity");
                  }
               }
            }
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(SpinYeetReleasePayload.ID, (payload, ctx) -> {
         ServerPlayer grabber = ctx.player();
         SpinYeetSession session = grabberToSession.get(grabber.getUUID());
         if (session != null && session.active) {
            performYeet(grabber.level(), grabber, session);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register(SpinYeetHandler::onTick);
   }

   private static void onTick(MinecraftServer server) {
      for (UUID grabberUuid : new ArrayList<>(grabberToSession.keySet())) {
         SpinYeetSession session = grabberToSession.get(grabberUuid);
         if (session != null && session.active) {
            ServerPlayer grabber = server.getPlayerList().getPlayer(grabberUuid);
            if (grabber == null) {
               cancelSession(server, session);
            } else {
               ServerLevel world = grabber.level();
               Entity grabbed = world.getEntity(session.grabbedUuid);
               if (grabbed != null && grabbed.isAlive()) {
                  session.tickCount++;
                  float t = Math.min(session.tickCount / 100.0F, 1.0F);
                  session.currentSpeed = 0.05F + 0.75F * (t * t);
                  session.spinAngle = session.spinAngle + session.currentSpeed;
                  double orbitX = grabber.getX() + Math.cos(session.spinAngle) * 1.5;
                  double orbitY = grabber.getY() + 0.6F;
                  double orbitZ = grabber.getZ() + Math.sin(session.spinAngle) * 1.5;
                  float grabbedYaw = (float)Math.toDegrees(session.spinAngle) + 90.0F;
                  grabbed.setDeltaMovement(0.0, 0.0, 0.0);
                  grabbed.setSwimming(true);
                  if (grabbed instanceof ServerPlayer sp) {
                     sp.setPos(orbitX, orbitY, orbitZ);
                     sp.connection.teleport(orbitX, orbitY, orbitZ, grabbedYaw, grabbed.getXRot());
                  } else {
                     grabbed.setPos(orbitX, orbitY, orbitZ);
                     grabbed.setYRot(grabbedYaw);
                  }

                  float yawDelta = (float)Math.toDegrees(session.currentSpeed) * 1.0F;
                  ServerPlayNetworking.send(grabber, new SpinYeetGrabberYawPayload(yawDelta));
                  if (session.tickCount % 2 == 0 && grabbed instanceof ServerPlayer sp) {
                     float tiltT = Math.min(session.tickCount / 100.0F, 1.0F);
                     float roll = tiltT * 90.0F;
                     ServerPlayNetworking.send(sp, new SpinYeetCameraPayload(roll));
                  }
               } else {
                  cancelSession(server, session);
               }
            }
         }
      }

      for (UUID yeetedUuid : new ArrayList<>(yeetedTicks.keySet())) {
         int ticksLeft = yeetedTicks.get(yeetedUuid);
         if (ticksLeft <= 0) {
            yeetedTicks.remove(yeetedUuid);
            ServerLevel w = yeetedWorlds.remove(yeetedUuid);
            Entity e = w != null ? w.getEntity(yeetedUuid) : null;
            if (e instanceof ServerPlayer sp) {
               sp.setSwimming(false);
               PoseNetworking.broadcastAnimState(sp, 0);
            } else if (e != null) {
               e.setSwimming(false);
            }
         } else {
            yeetedTicks.put(yeetedUuid, ticksLeft - 1);
            ServerLevel w = yeetedWorlds.get(yeetedUuid);
            if (w == null) {
               yeetedTicks.remove(yeetedUuid);
            } else {
               Entity yeeted = w.getEntity(yeetedUuid);
               if (yeeted != null && yeeted.isAlive()) {
                  Vec3 pos = yeeted.position();
                  BlockPos center = yeeted.blockPosition();
                  int r = (int)Math.ceil(2.0);
                  float rSq = 4.0F;
                  boolean hitAny = false;

                  for (int dx = -r; dx <= r; dx++) {
                     for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                           if (!(dx * dx + dy * dy + dz * dz > rSq)) {
                              BlockPos bp = center.offset(dx, dy, dz);
                              BlockState state = w.getBlockState(bp);
                              if (!state.isAir() && !(state.getDestroySpeed(w, bp) < 0.0F)) {
                                 w.setBlock(bp, Blocks.AIR.defaultBlockState(), 19);
                                 hitAny = true;
                              }
                           }
                        }
                     }
                  }

                  if (hitAny) {
                     w.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 2, 0.4, 0.4, 0.4, 0.0);
                     w.playSound(null, pos.x, pos.y, pos.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
                  }
               } else {
                  yeetedTicks.remove(yeetedUuid);
                  yeetedWorlds.remove(yeetedUuid);
               }
            }
         }
      }
   }

   private static void performYeet(ServerLevel world, ServerPlayer grabber, SpinYeetSession session) {
      session.active = false;
      Entity grabbed = world.getEntity(session.grabbedUuid);
      float speedT = Math.min(session.currentSpeed / 0.8F, 1.0F);
      float yeetT = speedT * speedT;
      float horiz = 0.8F + 6.2F * yeetT;
      float vert = 0.3F + 2.2F * yeetT;
      Vec3 look = grabber.getViewVector(1.0F);
      double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
      double normX = hLen > 0.001 ? look.x / hLen : 0.0;
      double normZ = hLen > 0.001 ? look.z / hLen : 0.0;
      if (grabbed != null && grabbed.isAlive()) {
         grabbed.setSwimming(false);
         grabbed.setDeltaMovement(normX * horiz, vert, normZ * horiz);
         grabbed.hurtMarked = true;
         if (grabbed instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
         }
      }

      PoseNetworking.broadcastAnimState(grabber, 0);
      ServerPlayNetworking.send(grabber, new SpinYeetImpactPayload());
      yeetedTicks.put(session.grabbedUuid, 100);
      yeetedWorlds.put(session.grabbedUuid, world);
      broadcast(world, grabber, new SpinYeetEndPayload(session.grabbedUuid, true));
      removeSession(session);
   }

   private static void cancelSession(MinecraftServer server, SpinYeetSession session) {
      session.active = false;
      SpinYeetEndPayload end = new SpinYeetEndPayload(session.grabbedUuid, false);

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(p, end);
      }

      ServerPlayer grabber = server.getPlayerList().getPlayer(session.grabberUuid);
      if (grabber != null) {
         PoseNetworking.broadcastAnimState(grabber, 0);
      }

      ServerPlayer grabbed = server.getPlayerList().getPlayer(session.grabbedUuid);
      if (grabbed != null) {
         grabbed.setSwimming(false);
         PoseNetworking.broadcastAnimState(grabbed, 0);
      }

      removeSession(session);
   }

   private static void removeSession(SpinYeetSession session) {
      grabberToSession.remove(session.grabberUuid);
      grabbedToGrabber.remove(session.grabbedUuid);
   }

   private static <T extends CustomPacketPayload> void broadcast(ServerLevel world, ServerPlayer near, T payload) {
      for (ServerPlayer p : world.players()) {
         if (p.distanceToSqr(near) < 2500.0) {
            ServerPlayNetworking.send(p, payload);
         }
      }
   }

   public static void onPlayerDisconnect(ServerPlayer player) {
      MinecraftServer server = player.level().getServer();
      if (server != null) {
         SpinYeetSession asGrabber = grabberToSession.get(player.getUUID());
         if (asGrabber != null) {
            cancelSession(server, asGrabber);
         } else {
            UUID grabberUuid = grabbedToGrabber.get(player.getUUID());
            if (grabberUuid != null) {
               SpinYeetSession asGrabbed = grabberToSession.get(grabberUuid);
               if (asGrabbed != null) {
                  cancelSession(server, asGrabbed);
               }
            }
         }
      }
   }

   public static boolean isCurrentlyGrabbed(UUID uuid) {
      return grabbedToGrabber.containsKey(uuid);
   }
}
