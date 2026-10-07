package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class BonkHandler {
   public static boolean ENABLED = true;
   private static final int ANIM_LAY_DOWN = 79;
   private static final int ANIM_BONK = 80;
   private static final int ANIM_NONE = 0;
   private static final double BONKER_Y_OFFSET = 0.6;
   private static final double BONKER_XZ_OFFSET = 0.6;
   private static final double VICTIM_Y_SINK = 1.0;
   private static final long LOOP_MS = 3125L;
   private static final long HIT_RIGHT_1 = 375L;
   private static final long HIT_LEFT_1 = 667L;
   private static final long HIT_RIGHT_2 = 1417L;
   private static final long HIT_ANVIL = 2542L;
   private static final long HIT_LEFT_2 = 2917L;
   private static final long HIT_WINDOW = 100L;
   private static final float CAM_SIDE = 40.0F;
   private static final float CAM_UP = -55.0F;
   private static final double JOIN_RANGE = 2.5;
   public static final Identifier BONK_CAMERA_ID = Identifier.fromNamespaceAndPath("cooptest", "bonk_camera");
   public static final Identifier BONK_MOVE_LOCK_ID = Identifier.fromNamespaceAndPath("cooptest", "bonk_move_lock");
   public static final Identifier BONK_L_KEY_ID = Identifier.fromNamespaceAndPath("cooptest", "bonk_l_key");
   private static final Map<UUID, Float> layingPlayers = new HashMap<>();
   private static final Map<UUID, UUID> bonkSessions = new HashMap<>();
   private static final Map<UUID, UUID> victimMap = new HashMap<>();
   private static final Map<UUID, Long> sessionStartMs = new HashMap<>();
   private static final Map<UUID, Float> victimLockedYaw = new HashMap<>();
   private static final Map<UUID, Set<Long>> firedThisCycle = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(BonkHandler.BonkCameraPayload.ID, BonkHandler.BonkCameraPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(BonkHandler.BonkMoveLockPayload.ID, BonkHandler.BonkMoveLockPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(BonkHandler.BonkLKeyPayload.ID, BonkHandler.BonkLKeyPayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(BonkHandler.BonkLKeyPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onLKey(ctx.player())));
      ServerTickEvents.END_SERVER_TICK.register(BonkHandler::tick);
   }

   public static void onLKey(ServerPlayer player) {
      if (ENABLED) {
         UUID id = player.getUUID();
         if (bonkSessions.containsKey(id)) {
            stopBonk(id, player.level().getServer());
         } else if (layingPlayers.containsKey(id)) {
            stopLaying(id, player.level().getServer());
         } else {
            UUID victim = findNearbyLaying(player);
            if (victim != null) {
               startBonk(player, victim);
            } else {
               startLaying(player);
            }
         }
      }
   }

   private static void startLaying(ServerPlayer player) {
      UUID id = player.getUUID();
      layingPlayers.put(id, player.getYRot());
      player.teleportTo(player.level(), player.getX(), player.getY() - 1.0, player.getZ(), Set.of(), player.getYRot(), 0.0F, false);
      PoseNetworking.broadcastAnimState(player, 79);
      ServerPlayNetworking.send(player, new BonkHandler.BonkMoveLockPayload(true));
      player.sendOverlayMessage(Component.literal("§7[Laying down — press L to get up]"));
   }

   private static void stopLaying(UUID id, MinecraftServer server) {
      layingPlayers.remove(id);
      sessionStartMs.remove(id);
      ServerPlayer p = server.getPlayerList().getPlayer(id);
      if (p != null) {
         p.teleportTo(p.level(), p.getX(), p.getY() + 1.0, p.getZ(), Set.of(), p.getYRot(), 0.0F, false);
         PoseNetworking.broadcastAnimState(p, 0);
         ServerPlayNetworking.send(p, new BonkHandler.BonkMoveLockPayload(false));
      }
   }

   private static void startBonk(ServerPlayer bonker, UUID victimId) {
      MinecraftServer server = bonker.level().getServer();
      if (server != null) {
         ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
         if (victim != null) {
            UUID bonkerId = bonker.getUUID();
            bonkSessions.put(bonkerId, victimId);
            victimMap.put(victimId, bonkerId);
            sessionStartMs.put(bonkerId, System.currentTimeMillis());
            victimLockedYaw.put(bonkerId, victim.getYRot());
            Vec3 vPos = victim.position();
            float lockedYaw = victim.getYRot();
            double fwdX = -Math.sin(Math.toRadians(lockedYaw));
            double fwdZ = Math.cos(Math.toRadians(lockedYaw));
            double targetX = vPos.x + fwdX * 0.6;
            double targetZ = vPos.z + fwdZ * 0.6;
            double targetY = vPos.y + 0.6;
            float yaw = lockedYaw + 180.0F;
            float pitch = 30.0F;
            bonker.teleportTo(bonker.level(), targetX, targetY, targetZ, Set.of(), yaw, pitch, false);
            bonker.setYRot(yaw);
            bonker.setYBodyRot(yaw);
            bonker.setYHeadRot(yaw);
            bonker.setPermanentlyInvulnerable(true);
            PoseNetworking.broadcastAnimState(bonker, 80);
            ServerPlayNetworking.send(bonker, new BonkHandler.BonkMoveLockPayload(true));
            bonker.sendOverlayMessage(Component.literal("§c§lBONK! §7Press L to stop"));
            victim.sendOverlayMessage(Component.literal("§c§lYou're getting bonked! §7Press L to escape"));
         }
      }
   }

   private static void stopBonk(UUID bonkerId, MinecraftServer server) {
      UUID victimId = bonkSessions.remove(bonkerId);
      if (victimId != null) {
         victimMap.remove(victimId);
      }

      sessionStartMs.remove(bonkerId);
      victimLockedYaw.remove(bonkerId);
      ServerPlayer bonker = server.getPlayerList().getPlayer(bonkerId);
      if (bonker != null) {
         bonker.setPermanentlyInvulnerable(false);
         PoseNetworking.broadcastAnimState(bonker, 0);
         ServerPlayNetworking.send(bonker, new BonkHandler.BonkMoveLockPayload(false));
         bonker.push(0.0, 0.3, 0.0);
         bonker.syncVelocity = true;
      }

      if (victimId != null) {
         layingPlayers.remove(victimId);
         ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
         if (victim != null) {
            victim.teleportTo(victim.level(), victim.getX(), victim.getY() + 1.0, victim.getZ(), Set.of(), victim.getYRot(), 0.0F, false);
            PoseNetworking.broadcastAnimState(victim, 0);
            ServerPlayNetworking.send(victim, new BonkHandler.BonkMoveLockPayload(false));
         }
      }
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();

      for (UUID id : new ArrayList<>(layingPlayers.keySet())) {
         if (!victimMap.containsKey(id)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
               stopLaying(id, server);
            } else {
               p.setDeltaMovement(0.0, 0.0, 0.0);
               p.syncVelocity = true;
            }
         }
      }

      for (UUID bonkerId : new ArrayList<>(bonkSessions.keySet())) {
         UUID victimId = bonkSessions.get(bonkerId);
         ServerPlayer bonker = server.getPlayerList().getPlayer(bonkerId);
         ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
         if (bonker != null && victim != null) {
            Vec3 vPos = victim.position();
            float lockedYaw = victimLockedYaw.getOrDefault(bonkerId, victim.getYRot());
            double fwdX = -Math.sin(Math.toRadians(lockedYaw));
            double fwdZ = Math.cos(Math.toRadians(lockedYaw));
            double bx = vPos.x + fwdX * 0.6;
            double bz = vPos.z + fwdZ * 0.6;
            float bonkerYaw = lockedYaw + 180.0F;
            float bonkerPitch = 30.0F;
            bonker.teleportTo(bonker.level(), bx, vPos.y + 0.6, bz, Set.of(), bonkerYaw, bonkerPitch, false);
            bonker.setPermanentlyInvulnerable(true);
            victim.setDeltaMovement(0.0, 0.0, 0.0);
            victim.syncVelocity = true;
            Long startMs = sessionStartMs.get(bonkerId);
            if (startMs != null) {
               long loopElapsed = (now - startMs) % 3125L;
               ServerLevel world = bonker.level();
               Vec3 handPos = bonker.position().add(0.0, 1.4, 0.0);
               fireIfInWindow(loopElapsed, 375L, bonkerId, () -> {
                  spawnImpactParticles(world, handPos);
                  world.playSound(null, handPos.x, handPos.y, handPos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
                  world.playSound(null, handPos.x, handPos.y, handPos.z, ModSounds.DAP_HIT, SoundSource.PLAYERS, 0.9F, 1.1F);
                  ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(40.0F, 0.0F));
               });
               fireIfInWindow(loopElapsed, 667L, bonkerId, () -> {
                  spawnImpactParticles(world, handPos);
                  world.playSound(null, handPos.x, handPos.y, handPos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
                  world.playSound(null, handPos.x, handPos.y, handPos.z, ModSounds.DAP_HIT, SoundSource.PLAYERS, 0.9F, 0.9F);
                  ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(-40.0F, 0.0F));
               });
               fireIfInWindow(loopElapsed, 1417L, bonkerId, () -> {
                  spawnImpactParticles(world, handPos);
                  world.playSound(null, handPos.x, handPos.y, handPos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.3F);
                  ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(40.0F, 0.0F));
               });
               fireIfInWindow(loopElapsed, 2542L, bonkerId, () -> {
                  Vec3 mid = victim.position().add(0.0, 1.0, 0.0);
                  world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5F, 0.8F);
                  world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.6F);
                  world.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 20, 0.4, 0.3, 0.4, 0.15);
                  world.sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 12, 0.3, 0.2, 0.3, 0.1);
                  world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), mid.x, mid.y, mid.z, 2, 0.0, 0.0, 0.0, 0.0);
                  world.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 3, 0.2, 0.1, 0.2, 0.05);
                  ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(0.0F, -55.0F));
               });
               fireIfInWindow(loopElapsed, 2917L, bonkerId, () -> {
                  spawnImpactParticles(world, handPos);
                  world.playSound(null, handPos.x, handPos.y, handPos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.1F);
                  ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(-40.0F, 0.0F));
               });
            }
         } else {
            stopBonk(bonkerId, server);
         }
      }
   }

   private static void fireIfInWindow(long loopElapsed, long target, UUID bonkerId, Runnable action) {
      if (loopElapsed >= target && loopElapsed < target + 100L) {
         Set<Long> fired = firedThisCycle.computeIfAbsent(bonkerId, k -> new HashSet<>());
         Long sStart = sessionStartMs.get(bonkerId);
         long cycleIndex = sStart != null ? (System.currentTimeMillis() - sStart) / 3125L : 0L;
         long cycleKey = cycleIndex * 100000L + target;
         if (fired.add(cycleKey)) {
            action.run();
            fired.removeIf(k -> k < cycleKey - 3125L);
         }
      }
   }

   private static void spawnImpactParticles(ServerLevel world, Vec3 pos) {
      world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.1);
      world.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 4, 0.15, 0.15, 0.15, 0.06);
   }

   private static UUID findNearbyLaying(ServerPlayer bonker) {
      for (UUID id : layingPlayers.keySet()) {
         if (!victimMap.containsKey(id)) {
            ServerPlayer victim = bonker.level().getServer().getPlayerList().getPlayer(id);
            if (victim != null && bonker.distanceTo(victim) <= 2.5) {
               return id;
            }
         }
      }

      return null;
   }

   public static boolean isLaying(UUID id) {
      return layingPlayers.containsKey(id) || victimMap.containsKey(id);
   }

   public static boolean isInBonkSession(UUID id) {
      return bonkSessions.containsKey(id) || victimMap.containsKey(id) || layingPlayers.containsKey(id);
   }

   public static void cleanup(UUID id) {
      layingPlayers.remove(id);
      firedThisCycle.remove(id);
      UUID victim = bonkSessions.remove(id);
      if (victim != null) {
         victimMap.remove(victim);
      }

      UUID bonker = victimMap.remove(id);
      if (bonker != null) {
         bonkSessions.remove(bonker);
      }

      sessionStartMs.remove(id);
      victimLockedYaw.remove(id);
   }

   public record BonkCameraPayload(float deltaYaw, float deltaPitch) implements CustomPacketPayload {
      public static final Type<BonkHandler.BonkCameraPayload> ID = new Type(BonkHandler.BONK_CAMERA_ID);
      public static final StreamCodec<FriendlyByteBuf, BonkHandler.BonkCameraPayload> CODEC = StreamCodec.ofMember((v, b) -> {
         b.writeFloat(v.deltaYaw);
         b.writeFloat(v.deltaPitch);
      }, b -> new BonkHandler.BonkCameraPayload(b.readFloat(), b.readFloat()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record BonkLKeyPayload() implements CustomPacketPayload {
      public static final Type<BonkHandler.BonkLKeyPayload> ID = new Type(BonkHandler.BONK_L_KEY_ID);
      public static final StreamCodec<FriendlyByteBuf, BonkHandler.BonkLKeyPayload> CODEC = StreamCodec.unit(new BonkHandler.BonkLKeyPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record BonkMoveLockPayload(boolean locked) implements CustomPacketPayload {
      public static final Type<BonkHandler.BonkMoveLockPayload> ID = new Type(BonkHandler.BONK_MOVE_LOCK_ID);
      public static final StreamCodec<FriendlyByteBuf, BonkHandler.BonkMoveLockPayload> CODEC = StreamCodec.ofMember(
         (v, b) -> b.writeBoolean(v.locked), b -> new BonkHandler.BonkMoveLockPayload(b.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
