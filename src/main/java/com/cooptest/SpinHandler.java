package com.cooptest;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public class SpinHandler {
    private static final float  YAW_PER_TICK       = 22.0f;
    private static final double GRAVITY_CAP        = -0.15;
    private static final long   MAX_SPIN_MS        = 30000L;
    private static final int    SOUND_INTERVAL     = 12;
    private static final double HELICOPTER_H_RANGE = 2.5;
    private static final double HELICOPTER_V_RANGE = 2.0;
    private static final int    ANIM_SPIN          = 64;
    private static final int    ANIM_NONE          = 0;
    public record SpinStartPayload() implements CustomPacketPayload {
        public static final Type<SpinStartPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("testcoop", "spin_start"));
        public static final StreamCodec<FriendlyByteBuf, SpinStartPayload> CODEC = StreamCodec.unit(new SpinStartPayload());
        @Override public Type<SpinStartPayload> type() { return ID; }
    }
    public record SpinStopPayload() implements CustomPacketPayload {
        public static final Type<SpinStopPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("testcoop", "spin_stop"));
        public static final StreamCodec<FriendlyByteBuf, SpinStopPayload> CODEC = StreamCodec.unit(new SpinStopPayload());
        @Override public Type<SpinStopPayload> type() { return ID; }
    }
    public record SpinSyncPayload(UUID playerId, boolean spinning) implements CustomPacketPayload {
        public static final Type<SpinSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("testcoop", "spin_sync"));
        public static final StreamCodec<FriendlyByteBuf, SpinSyncPayload> CODEC =
                StreamCodec.ofMember(
                        (val, buf) -> { buf.writeUUID(val.playerId()); buf.writeBoolean(val.spinning()); },
                        buf -> new SpinSyncPayload(buf.readUUID(), buf.readBoolean())
                );
        @Override public Type<SpinSyncPayload> type() { return ID; }
    }
    public record HelicopterLaunchPayload(UUID spinnerId, UUID riderId) implements CustomPacketPayload {
        public static final Type<HelicopterLaunchPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("testcoop", "helicopter_launch"));
        public static final StreamCodec<FriendlyByteBuf, HelicopterLaunchPayload> CODEC =
                StreamCodec.ofMember(
                        (val, buf) -> { buf.writeUUID(val.spinnerId()); buf.writeUUID(val.riderId()); },
                        buf -> new HelicopterLaunchPayload(buf.readUUID(), buf.readUUID())
                );
        @Override public Type<HelicopterLaunchPayload> type() { return ID; }
    }
    private static final Set<UUID>           activeSpin         = new HashSet<>();
    private static final Map<UUID, Long>     spinStartTime      = new HashMap<>();
    private static final Map<UUID, Float>    spinYaw            = new HashMap<>();
    private static final Map<UUID, Integer>  spinTick           = new HashMap<>();
    private static final Map<UUID, UUID>     helicopterRider    = new HashMap<>();
    private static final Map<UUID, UUID>     helicopterSpinner  = new HashMap<>();
    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(SpinStartPayload.ID,       SpinStartPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpinStopPayload.ID,        SpinStopPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpinSyncPayload.ID,        SpinSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HelicopterLaunchPayload.ID, HelicopterLaunchPayload.CODEC);
    }
    public static void register() {
        registerPayloads();
        ServerPlayNetworking.registerGlobalReceiver(SpinStartPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (!CoopMovesConfig.get().enableSpin) return;
                UUID id = player.getUUID();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                if (pose != PoseState.GRABBED) return;
                if (GrabMechanic.heldBy.containsKey(id)) return;
                if (activeSpin.contains(id)) return;
                activeSpin.add(id);
                spinStartTime.put(id, System.currentTimeMillis());
                spinYaw.put(id, player.getVisualRotationYInDegrees());
                spinTick.put(id, 0);
                DapHoldHandler.forceUnfreeze(player.level().getServer(), id);
                PoseNetworking.broadcastAnimState(player, ANIM_NONE);
                PoseNetworking.broadcastAnimState(player, ANIM_SPIN);
                broadcastSpinSync(player.level().getServer(), id, true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.6f);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SpinStopPayload.ID, (payload, context) -> {
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
            if (player == null) { it.remove(); cleanupSpinMaps(id); continue; }
            PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            if (GrabMechanic.heldBy.containsKey(id)) {
                it.remove(); cleanupSpinMaps(id);
                detachHelicopterRider(server, id);
                broadcastSpinSync(server, id, false);
                PoseNetworking.broadcastAnimState(player, ANIM_NONE);
                continue;
            }
            if (player.onGround() || player.isInWater()) {
                it.remove(); cleanupSpinMaps(id);
                detachHelicopterRider(server, id);
                broadcastSpinSync(server, id, false);
                PoseNetworking.broadcastAnimState(player, ANIM_NONE);
                continue;
            }
            long elapsed = now - spinStartTime.getOrDefault(id, now);
            if (elapsed >= MAX_SPIN_MS) {
                it.remove(); cleanupSpinMaps(id);
                detachHelicopterRider(server, id);
                broadcastSpinSync(server, id, false);
                PoseNetworking.broadcastAnimState(player, ANIM_NONE);
                continue;
            }
            boolean hasRider = helicopterRider.containsKey(id);
            float currentYaw = spinYaw.getOrDefault(id, player.getVisualRotationYInDegrees());
            if (!hasRider) {
                float newYaw = currentYaw + YAW_PER_TICK;
                if (newYaw > 180f) newYaw -= 360f;
                spinYaw.put(id, newYaw);
                currentYaw = newYaw;
                player.setYRot(currentYaw);
                player.setYBodyRot(currentYaw);
                player.setYHeadRot(currentYaw);
            }
            Vec3 vel = player.getDeltaMovement();
            if (vel.y < GRAVITY_CAP) {
                player.setDeltaMovement(vel.x, GRAVITY_CAP, vel.z);
                player.hurtMarked = true;
            }
            Vec3 pos = player.position().add(0, 0.9, 0);
            double angle = Math.toRadians(currentYaw);
            player.level().sendParticles(
                    ParticleTypes.CLOUD,
                    pos.x + Math.cos(angle) * 0.5, pos.y, pos.z + Math.sin(angle) * 0.5,
                    2, 0.05, 0.05, 0.05, 0.01);
            int tck = spinTick.merge(id, 1, Integer::sum);
            if (tck % SOUND_INTERVAL == 0) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.5f, 1.4f);
            }
            if (!hasRider) {
                checkHelicopterSweep(server, player);
            }
        }
    }
    private static void checkHelicopterSweep(MinecraftServer server, ServerPlayer spinner) {
        UUID spinnerId = spinner.getUUID();
        Vec3 sPos = spinner.position();
        ServerLevel world = spinner.level();
        AABB sweepBox = new AABB(
                sPos.x - HELICOPTER_H_RANGE, sPos.y - 0.5, sPos.z - HELICOPTER_H_RANGE,
                sPos.x + HELICOPTER_H_RANGE, sPos.y + HELICOPTER_V_RANGE, sPos.z + HELICOPTER_H_RANGE
        );
        for (var entity : world.getEntitiesOfClass(ServerPlayer.class, sweepBox)) {
            if (entity == spinner) continue;
            ServerPlayer target = entity;
            UUID targetId = target.getUUID();
            if (!target.onGround()) continue;
            if (helicopterSpinner.containsKey(targetId)) continue;
            if (GrabMechanic.holding.containsKey(spinnerId)) continue;
            if (GrabMechanic.heldBy.containsKey(targetId)) continue;
            target.startRiding(spinner);
            helicopterRider.put(spinnerId, targetId);
            helicopterSpinner.put(targetId, spinnerId);
            ClientboundSetPassengersPacket pkt = new ClientboundSetPassengersPacket(spinner);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.connection.send(pkt);
            }
            spinner.setDeltaMovement(0, 4.0, 0);
            spinner.hurtMarked = true;
            GroundPoundHandler.markMegaPound(spinnerId);
            HelicopterLaunchPayload launchPkt = new HelicopterLaunchPayload(spinnerId, targetId);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, launchPkt);
            }
            world.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    sPos.x, sPos.y + 1.2, sPos.z, 8, 0.4, 0.2, 0.4, 0.1);
            world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    sPos.x, sPos.y + 0.5, sPos.z, 15, 0.5, 0.5, 0.5, 0.3);
            world.sendParticles(ParticleTypes.EXPLOSION,
                    sPos.x, sPos.y + 0.5, sPos.z, 3, 0.3, 0.3, 0.3, 0);
            world.playSound(null, sPos.x, sPos.y, sPos.z,
                    SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2f, 0.7f);
            world.playSound(null, sPos.x, sPos.y, sPos.z,
                    ModSounds.HELI, SoundSource.PLAYERS, 1.0f, 1.0f);
            world.playSound(null, sPos.x, sPos.y, sPos.z,
                    ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 0.8f, 0.5f);
            spinner.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🚀 HELICOPTER! Press SHIFT for MEGA GROUND POUND!"));
            target.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🚀 You're riding the helicopter!"));
            break;
        }
    }
    private static void doHelicopterLaunch(MinecraftServer server, ServerPlayer spinner) {
        UUID spinnerId = spinner.getUUID();
        UUID riderId   = helicopterRider.get(spinnerId);
        if (riderId == null) return;
        ServerPlayer rider = server.getPlayerList().getPlayer(riderId);
        if (rider != null) rider.stopRiding();
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
        Vec3 upVel = new Vec3(0, 3.0, 0);
        spinner.setDeltaMovement(upVel);
        spinner.hurtMarked = true;
        if (rider != null) {
            rider.setDeltaMovement(upVel.add(0, 0.15, 0));
            rider.hurtMarked = true;
            GroundPoundHandler.markMegaPound(spinnerId);
        }
        HelicopterLaunchPayload launchPkt = new HelicopterLaunchPayload(spinnerId, riderId != null ? riderId : spinnerId);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, launchPkt);
        }
        ServerLevel world = spinner.level();
        Vec3 pos = spinner.position().add(0, 1, 0);
        world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.3, 0.3, 0.3, 0);
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 20, 0.5, 0.5, 0.5, 0.3);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2f, 0.7f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 0.8f, 0.5f);
        spinner.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🚀 HELICOPTER LAUNCH!"));
        if (rider != null) rider.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🚀 Helicopter launched!"));
    }
    private static void detachHelicopterRider(MinecraftServer server, UUID spinnerId) {
        UUID riderId = helicopterRider.remove(spinnerId);
        if (riderId == null) return;
        helicopterSpinner.remove(riderId);
        ServerPlayer rider   = server.getPlayerList().getPlayer(riderId);
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
    static final Map<UUID, UUID> pendingGroundPoundRider = new HashMap<>();
    public static void stopSpinKeepRider(MinecraftServer server, UUID id) {
        if (!activeSpin.remove(id)) return;
        UUID riderId = helicopterRider.remove(id);
        if (riderId != null) {
            helicopterSpinner.remove(riderId);
            pendingGroundPoundRider.put(id, riderId);
        }
        cleanupSpinMaps(id);
        broadcastSpinSync(server, id, false);
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) PoseNetworking.broadcastAnimState(player, ANIM_NONE);
    }
    public static void stopSpin(MinecraftServer server, UUID id) {
        if (!activeSpin.remove(id)) return;
        cleanupSpinMaps(id);
        detachHelicopterRider(server, id);
        broadcastSpinSync(server, id, false);
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) PoseNetworking.broadcastAnimState(player, ANIM_NONE);
    }
    public static void detachRiderByIds(MinecraftServer server, UUID spinnerId, UUID riderId) {
        helicopterSpinner.remove(riderId);
        ServerPlayer rider   = server.getPlayerList().getPlayer(riderId);
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
    public static boolean isSpinning(UUID id) { return activeSpin.contains(id); }
    public static boolean hasHelicopterRider(UUID spinnerId) { return helicopterRider.containsKey(spinnerId); }
    private static void cleanupSpinMaps(UUID id) {
        spinStartTime.remove(id);
        spinYaw.remove(id);
        spinTick.remove(id);
    }
    private static void broadcastSpinSync(MinecraftServer server, UUID id, boolean spinning) {
        SpinSyncPayload pkt = new SpinSyncPayload(id, spinning);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, pkt);
        }
    }
    public static void cleanup(UUID id) {
        activeSpin.remove(id);
        cleanupSpinMaps(id);
        UUID riderId = helicopterRider.remove(id);
        if (riderId != null) helicopterSpinner.remove(riderId);
        helicopterSpinner.remove(id);
        pendingGroundPoundRider.remove(id);
    }
}