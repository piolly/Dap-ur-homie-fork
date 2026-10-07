package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
import net.minecraft.world.phys.Vec3;
import java.util.*;


public class DivineFlamCombo {

    private static final Map<UUID, Long> comboWindowStart = new HashMap<>();
    private static final Map<UUID, UUID> comboPartner = new HashMap<>();

    private static final Map<UUID, Long> comboFreezeEnd = new HashMap<>();

    private static final long COMBO_WINDOW_MS = 1460;
    private static final long COMBO_FREEZE_MS = 3800;

    public static final Identifier DIVINE_J_PRESS_ID = Identifier.fromNamespaceAndPath("cooptest", "divine_j_press");
    public static final Identifier DIVINE_START_ID = Identifier.fromNamespaceAndPath("cooptest", "divine_start");

    public record DivineJPressPayload() implements CustomPacketPayload {
        public static final Type<DivineJPressPayload> ID = new Type<>(DIVINE_J_PRESS_ID);
        public static final StreamCodec<FriendlyByteBuf, DivineJPressPayload> CODEC =
                StreamCodec.unit(new DivineJPressPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record DivineStartPayload() implements CustomPacketPayload {
        public static final Type<DivineStartPayload> ID = new Type<>(DIVINE_START_ID);
        public static final StreamCodec<FriendlyByteBuf, DivineStartPayload> CODEC =
                StreamCodec.unit(new DivineStartPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(DivineJPressPayload.ID, DivineJPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DivineStartPayload.ID, DivineStartPayload.CODEC);
        System.out.println("[Divine Flame] Payloads registered");
    }

    public static void register() {
        // Listen for J key press
        ServerPlayNetworking.registerGlobalReceiver(DivineJPressPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> onPlayerPressJ(player));
        });
        System.out.println("[Divine Flame] Handlers registered");
    }


    public static void startDivineFlame(ServerPlayer p1, ServerPlayer p2, Vec3 midpoint) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();

        System.out.println("[Divine Flame] ===== STARTING DIVINE FLAME =====");

        comboWindowStart.put(id1, now);
        comboWindowStart.put(id2, now);
        comboPartner.put(id1, id2);
        comboPartner.put(id2, id1);

        System.out.println("[Divine Flame] Broadcasting ordinal 36 to both players");
        PoseNetworking.broadcastAnimState(p1, 36);
        PoseNetworking.broadcastAnimState(p2, 36);

        comboFreezeEnd.put(id1, now + COMBO_WINDOW_MS);
        comboFreezeEnd.put(id2, now + COMBO_WINDOW_MS);

        ServerPlayNetworking.send(p1, new DivineStartPayload());
        ServerPlayNetworking.send(p2, new DivineStartPayload());

        ServerLevel world = p1.level();
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.EPIC_DAP, SoundSource.PLAYERS, 1.5f, 1.1f);

        System.out.println("[Divine Flame] Divine Flame window opened! Players have 1.46s to press J");
    }


    private static void onPlayerPressJ(ServerPlayer player) {
        UUID playerId = player.getUUID();


        Long windowStart = comboWindowStart.get(playerId);
        if (windowStart == null) return;

        long elapsed = System.currentTimeMillis() - windowStart;
        if (elapsed > COMBO_WINDOW_MS) {
            comboWindowStart.remove(playerId);
            comboPartner.remove(playerId);
            return;
        }

        // Get partner
        UUID partnerId = comboPartner.get(playerId);
        if (partnerId == null) return;

        ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
        if (partner == null) return;

        // Check if partner also in window
        if (!comboWindowStart.containsKey(partnerId)) return;

        executeCombo(player, partner);
    }

   
    private static void executeCombo(ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();

        System.out.println("[Divine Flame] ===== EXECUTING COMBO =====");
        System.out.println("[Divine Flame] Both players pressed J!");

        // Clear window
        comboWindowStart.remove(id1);
        comboWindowStart.remove(id2);
        comboPartner.remove(id1);
        comboPartner.remove(id2);

        comboFreezeEnd.put(id1, now + COMBO_FREEZE_MS);
        comboFreezeEnd.put(id2, now + COMBO_FREEZE_MS);

        System.out.println("[Divine Flame] Broadcasting ordinal 37 (P1) and 38 (P2)");
        PoseNetworking.broadcastAnimState(p1, 37);
        PoseNetworking.broadcastAnimState(p2, 38);

        ServerLevel world = p1.level();
        Vec3 midpoint = p1.position().add(p2.position()).scale(0.5);

        System.out.println("[Divine Flame] Spawning Divine Flame vortex at " + midpoint);

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 10.0f, 0.2f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 5.0f, 0.5f);

        System.out.println("[Divine Flame] Spawning particles...");

        // Particles
        world.sendParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y + 1, midpoint.z, 100, 1.0, 1.0, 1.0, 0.2);
        world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y + 1, midpoint.z, 50, 0.8, 0.8, 0.8, 0.15);
        world.sendParticles(ParticleTypes.LAVA, midpoint.x, midpoint.y + 1, midpoint.z, 30, 0.5, 0.5, 0.5, 0.1);

        System.out.println("[Divine Flame] DIVINE FLAME SPAWNED! Frozen for 3.8 seconds");
    }

 
    public static void tick(ServerLevel world) {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, UUID> entry : comboPartner.entrySet()) {
            UUID id1 = entry.getKey();
            UUID id2 = entry.getValue();

            ServerPlayer p1 = world.getServer().getPlayerList().getPlayer(id1);
            ServerPlayer p2 = world.getServer().getPlayerList().getPlayer(id2);

            if (p1 != null && p2 != null) {
                double distance = p1.position().distanceTo(p2.position());

                if (distance > 1.0) {
                    Vec3 p1Pos = p1.position();
                    Vec3 p2Pos = p2.position();

                    Vec3 direction = p2Pos.subtract(p1Pos).normalize();

                    double targetDistance = 0.8;

                    Vec3 midpoint = p1Pos.add(p2Pos).scale(0.5);
                    Vec3 offset = direction.scale(targetDistance / 2.0);

                    Vec3 targetP1 = midpoint.subtract(offset);
                    Vec3 targetP2 = midpoint.add(offset);

                    double dx = p2Pos.x - p1Pos.x;
                    double dz = p2Pos.z - p1Pos.z;
                    float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                    float yawP2 = yawP1 + 180; // Face opposite direction

                    p1.teleportTo(p1.level(), targetP1.x, targetP1.y, targetP1.z, java.util.Set.of(), yawP1, 0.0f, false);
                    p2.teleportTo(p2.level(), targetP2.x, targetP2.y, targetP2.z, java.util.Set.of(), yawP2, 0.0f, false);
                }
            }
        }

        comboWindowStart.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > COMBO_WINDOW_MS) {
                UUID id = entry.getKey();
                comboPartner.remove(id);
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0);
                }
                return true;
            }
            return false;
        });

        comboFreezeEnd.entrySet().removeIf(entry -> {
            if (now >= entry.getValue()) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0);
                }
                return true;
            }
            return false;
        });
    }

   
    public static void cleanup(UUID playerId) {
        comboWindowStart.remove(playerId);
        comboPartner.remove(playerId);
        comboFreezeEnd.remove(playerId);
    }

    
    public static boolean isInComboFreeze(UUID playerId) {
        return comboFreezeEnd.containsKey(playerId);
    }
}
