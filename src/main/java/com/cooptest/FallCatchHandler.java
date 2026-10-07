package com.cooptest;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;


public class FallCatchHandler {
    public static final double CATCH_RANGE_HORIZONTAL = 3.0;  
    public static final double CATCH_RANGE_VERTICAL = 4.0;   
    public static final long CATCH_WINDOW_MS = 500;          
    public static final long CATCH_COOLDOWN_MS = 1000;        

    private static final Map<UUID, Long> catchReadyTime = new HashMap<>();
    private static final Map<UUID, Long> catchCooldowns = new HashMap<>();
    private static final Map<UUID, Boolean> successfulCatch = new HashMap<>();
    public static final Identifier CATCH_ANIM_ID = Identifier.fromNamespaceAndPath("cooptest", "catch_anim");
    public record CatchAnimPayload(UUID catcherId, UUID caughtId) implements CustomPacketPayload {
        public static final Type<CatchAnimPayload> ID = new Type<>(CATCH_ANIM_ID);
        public static final StreamCodec<FriendlyByteBuf, CatchAnimPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUUID(payload.catcherId);
                            buf.writeUUID(payload.caughtId);
                        },
                        buf -> new CatchAnimPayload(buf.readUUID(), buf.readUUID())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(CatchAnimPayload.ID, CatchAnimPayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            Iterator<Map.Entry<UUID, Long>> it = catchReadyTime.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID playerId = entry.getKey();
                long readyTime = entry.getValue();

                PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
                if (pose != PoseState.GRAB_READY) {
                    if (!successfulCatch.getOrDefault(playerId, false)) {
                        catchCooldowns.put(playerId, now + CATCH_COOLDOWN_MS);

                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c✗ Catch missed! 1 sec cooldown"));
                        }
                    }
                    it.remove();
                    successfulCatch.remove(playerId);
                }
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID playerId = player.getUUID();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);

                if (pose == PoseState.GRAB_READY && !catchReadyTime.containsKey(playerId)) {
                    catchReadyTime.put(playerId, now);
                    successfulCatch.put(playerId, false);
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer fallingPlayer)) return true;
            if (!source.is(DamageTypes.FALL)) return true;

            ServerPlayer catcher = findCatcher(fallingPlayer);
            if (catcher != null) {
                playCatchEffects(fallingPlayer, catcher);

                successfulCatch.put(catcher.getUUID(), true);

                return false; 
            }

            return true; 
        });
    }

   
    public static boolean isInCatchReadyMode(UUID playerId) {
        PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
        return pose == PoseState.GRAB_READY;
    }

    /**
     * Check if player is on catch cooldown
     */
    public static boolean isOnCatchCooldown(UUID playerId) {
        Long cooldownEnd = catchCooldowns.get(playerId);
        if (cooldownEnd == null) return false;
        if (System.currentTimeMillis() >= cooldownEnd) {
            catchCooldowns.remove(playerId);
            return false;
        }
        return true;
    }

  
    public static boolean canEnterCatchReady(UUID playerId) {
        return !isOnCatchCooldown(playerId);
    }

    private static ServerPlayer findCatcher(ServerPlayer fallingPlayer) {
        ServerLevel world = fallingPlayer.level();
        long now = System.currentTimeMillis();

        // Search in a generous area around the falling player
        AABB searchBox = new AABB(
                fallingPlayer.getX() - CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getY() - 2, // Below
                fallingPlayer.getZ() - CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getX() + CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getY() + 1, // Slightly above
                fallingPlayer.getZ() + CATCH_RANGE_HORIZONTAL
        );

        for (Player player : world.players()) {
            if (player == fallingPlayer) continue;
            if (!(player instanceof ServerPlayer catcher)) continue;

            UUID catcherId = catcher.getUUID();

            // Check if player is in GRAB_READY pose
            PoseState pose = PoseNetworking.poseStates.getOrDefault(catcherId, PoseState.NONE);
            if (pose != PoseState.GRAB_READY) continue;

            if (isOnCatchCooldown(catcherId)) continue;

            Long readyTime = catchReadyTime.get(catcherId);
            if (readyTime == null) continue;

            if (!searchBox.contains(catcher.position())) continue;

            double dx = fallingPlayer.getX() - catcher.getX();
            double dz = fallingPlayer.getZ() - catcher.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDist <= CATCH_RANGE_HORIZONTAL) {
                return catcher;
            }
        }

        return null;
    }

    private static void playCatchEffects(ServerPlayer caught, ServerPlayer catcher) {
        ServerLevel world = catcher.level();
        double x = (caught.getX() + catcher.getX()) / 2;
        double y = catcher.getY() + 1.5;
        double z = (caught.getZ() + catcher.getZ()) / 2;

        world.playSound(null, x, y, z,
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2f, 0.7f);
        world.playSound(null, x, y, z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        world.playSound(null, x, y, z,
                SoundEvents.WOOL_FALL, SoundSource.PLAYERS, 1.0f, 0.8f);

        world.sendParticles(ParticleTypes.CLOUD,
                x, y, z, 12, 0.4, 0.3, 0.4, 0.02);
        world.sendParticles(ParticleTypes.CRIT,
                x, y, z, 8, 0.3, 0.3, 0.3, 0.1);
        world.sendParticles(ParticleTypes.WAX_ON,
                x, y, z, 6, 0.2, 0.2, 0.2, 0.02);

        caught.setDeltaMovement(0, 0, 0);
        caught.syncVelocity = true;

        CatchAnimPayload payload = new CatchAnimPayload(catcher.getUUID(), caught.getUUID());
        for (ServerPlayer nearby : PlayerLookup.tracking(catcher)) {
            ServerPlayNetworking.send(nearby, payload);
        }
        ServerPlayNetworking.send(catcher, payload);
        ServerPlayNetworking.send(caught, payload);

        PoseNetworking.poseStates.put(catcher.getUUID(), PoseState.NONE);
        PoseNetworking.broadcastPoseChange(catcher.level().getServer(), catcher.getUUID(), PoseState.NONE);

        caught.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§a§l✓ " + catcher.getName().getString() + " caught you!"));
        catcher.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§a§l✓ PERFECT CATCH! " + caught.getName().getString()));
    }

   
    public static void cleanup(UUID playerId) {
        catchReadyTime.remove(playerId);
        catchCooldowns.remove(playerId);
        successfulCatch.remove(playerId);
    }
}
