package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public class HighFiveHugHandler {
    private static final double HUG_DISTANCE = 4.0;
    private static final long HUG_HOLD_TIME_MS = 800;
    private static final long HUG_OPPORTUNITY_MS = 3000;
    private static final Map<UUID, Long> hugHoldStart = new HashMap<>();
    private static final Map<UUID, UUID> hugPartner = new HashMap<>();
    private static final Map<UUID, Long> lastHUpdate = new HashMap<>();
    private static final Map<UUID, HugState> hugState = new HashMap<>();
    private static final Map<UUID, Long> hugStartTime = new HashMap<>();
    private enum HugState {
        NONE,
        START,
        HUGGING,
        ENDING
    }
    public static final Identifier HUG_HOLD_ID = Identifier.fromNamespaceAndPath("cooptest", "hug_hold");
    public record HugHoldPayload() implements CustomPacketPayload {
        public static final Type<HugHoldPayload> ID = new Type<>(HUG_HOLD_ID);
        public static final StreamCodec<FriendlyByteBuf, HugHoldPayload> CODEC =
                StreamCodec.unit(new HugHoldPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(HugHoldPayload.ID, HugHoldPayload.CODEC);
    }
    public static void registerClientPayloads() {
        try { PayloadTypeRegistry.playC2S().register(HugHoldPayload.ID, HugHoldPayload.CODEC); } catch (Exception ignored) {}
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HugHoldPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                onPlayerHoldingH(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server));
    }
    public static void startHugHold(ServerPlayer p1, ServerPlayer p2) {
        long now = System.currentTimeMillis();
        hugHoldStart.put(p1.getUUID(), now);
        hugHoldStart.put(p2.getUUID(), now);
        hugPartner.put(p1.getUUID(), p2.getUUID());
        hugPartner.put(p2.getUUID(), p1.getUUID());
    }
    private static void onPlayerHoldingH(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        lastHUpdate.put(playerId, now);
        Long holdStart = hugHoldStart.get(playerId);
        if (holdStart == null) return;
        long elapsed = now - holdStart;
        if (elapsed < HUG_HOLD_TIME_MS) {
            return;
        }
        UUID partnerId = hugPartner.get(playerId);
        if (partnerId == null) return;
        ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
        if (partner == null) return;
        Long partnerHoldStart = hugHoldStart.get(partnerId);
        if (partnerHoldStart == null) return;
        long partnerElapsed = now - partnerHoldStart;
        if (partnerElapsed < HUG_HOLD_TIME_MS) {
            return;
        }
        Long partnerLastH = lastHUpdate.get(partnerId);
        if (partnerLastH == null || now - partnerLastH > 1000) {
            return;
        }
        double distance = player.position().distanceTo(partner.position());
        if (distance > HUG_DISTANCE) {
            player.displayClientMessage(Component.literal("§c❤ Get closer to hug! ❤"), true);
            return;
        }
        startHug(player, partner);
    }
    private static void startHug(ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();
        hugHoldStart.remove(id1);
        hugHoldStart.remove(id2);
        hugState.put(id1, HugState.START);
        hugState.put(id2, HugState.START);
        hugStartTime.put(id1, now);
        hugStartTime.put(id2, now);
        PoseNetworking.broadcastAnimState(p1, 32);
        PoseNetworking.broadcastAnimState(p2, 32);
        p1.displayClientMessage(Component.literal("§d❤ Hugging... ❤"), true);
        p2.displayClientMessage(Component.literal("§d❤ Hugging... ❤"), true);
    }
    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> hugHoldIt = hugHoldStart.entrySet().iterator();
        while (hugHoldIt.hasNext()) {
            Map.Entry<UUID, Long> entry = hugHoldIt.next();
            if (now - entry.getValue() > HUG_OPPORTUNITY_MS) {
                hugHoldIt.remove();
                hugPartner.remove(entry.getKey());
            }
        }
        Set<UUID> processedPlayers = new HashSet<>();
        Map<UUID, HugState> hugStateCopy = new HashMap<>(hugState);
        List<Runnable> stateChanges = new ArrayList<>();
        for (Map.Entry<UUID, HugState> entry : hugStateCopy.entrySet()) {
            UUID playerId = entry.getKey();
            HugState state = entry.getValue();
            if (processedPlayers.contains(playerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                hugState.remove(playerId);
                hugPartner.remove(playerId);
                hugStartTime.remove(playerId);
                lastHUpdate.remove(playerId);
                continue;
            }
            UUID partnerId = hugPartner.get(playerId);
            if (partnerId == null) {
                hugState.remove(playerId);
                hugStartTime.remove(playerId);
                lastHUpdate.remove(playerId);
                continue;
            }
            ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
            if (partner == null) {
                hugState.remove(playerId);
                hugPartner.remove(playerId);
                hugStartTime.remove(playerId);
                lastHUpdate.remove(playerId);
                continue;
            }
            Long startTime = hugStartTime.get(playerId);
            if (startTime == null) continue;
            long elapsed = now - startTime;
            processedPlayers.add(playerId);
            processedPlayers.add(partnerId);
            if (state == HugState.START) {
                if (elapsed >= 333) {
                    stateChanges.add(() -> transitionToHugging(player, partner));
                }
            } else if (state == HugState.HUGGING) {
                Long lastH1 = lastHUpdate.get(playerId);
                Long lastH2 = lastHUpdate.get(partnerId);
                if (lastH1 == null || lastH2 == null ||
                        now - lastH1 > 1000 || now - lastH2 > 1000) {
                    stateChanges.add(() -> endHug(player, partner));
                    continue;
                }
                double distance = player.position().distanceTo(partner.position());
                if (distance > 1.0) {
                    Vec3 playerPos = player.position();
                    Vec3 partnerPos = partner.position();
                    Vec3 direction = partnerPos.subtract(playerPos).normalize();
                    double targetDistance = 0.8;
                    Vec3 midpoint = playerPos.add(partnerPos).scale(0.5);
                    Vec3 offset = direction.scale(targetDistance / 2.0);
                    Vec3 targetPlayer = midpoint.subtract(offset);
                    Vec3 targetPartner = midpoint.add(offset);
                    player.teleportTo(player.level(), targetPlayer.x, targetPlayer.y, targetPlayer.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                    partner.teleportTo(partner.level(), targetPartner.x, targetPartner.y, targetPartner.z, java.util.Set.of(), partner.getYRot(), partner.getXRot(), false);
                } else if (distance > HUG_DISTANCE + 0.5) {
                    stateChanges.add(() -> endHug(player, partner));
                    continue;
                }
                if (elapsed % 1000 < 50) {
                    applyHugEffects(player, partner);
                }
            } else if (state == HugState.ENDING) {
                if (elapsed >= 542) {
                    hugState.remove(playerId);
                    hugPartner.remove(playerId);
                    hugStartTime.remove(playerId);
                    lastHUpdate.remove(playerId);
                    PoseNetworking.broadcastAnimState(player, 0);
                }
            }
        }
        for (Runnable change : stateChanges) {
            change.run();
        }
    }
    private static void transitionToHugging(ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();
        Random random = new Random();
        boolean useHugging2 = random.nextBoolean();
        hugState.put(id1, HugState.HUGGING);
        hugState.put(id2, HugState.HUGGING);
        hugStartTime.put(id1, now);
        hugStartTime.put(id2, now);
        if (useHugging2) {
            PoseNetworking.broadcastAnimState(p1, 33);
            PoseNetworking.broadcastAnimState(p2, 34);
        } else {
            PoseNetworking.broadcastAnimState(p1, 34);
            PoseNetworking.broadcastAnimState(p2, 33);
        }
        applyHugEffects(p1, p2);
    }
    private static void applyHugEffects(ServerPlayer p1, ServerPlayer p2) {
        p1.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, 40, 1, false, false));
        p2.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, 40, 1, false, false));
        Vec3 pos = p1.position().add(p2.position()).scale(0.5).add(0, 1, 0);
        ServerLevel world = p1.level();
        world.sendParticles(ParticleTypes.HEART,
                pos.x, pos.y, pos.z,
                5, 0.3, 0.3, 0.3, 0.1);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.1f, 1.5f);
    }
    private static void endHug(ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();
        hugState.put(id1, HugState.ENDING);
        hugState.put(id2, HugState.ENDING);
        hugStartTime.put(id1, now);
        hugStartTime.put(id2, now);
        PoseNetworking.broadcastAnimState(p1, 35);
        PoseNetworking.broadcastAnimState(p2, 35);
        p1.displayClientMessage(Component.literal("§e Hug ended "), true);
        p2.displayClientMessage(Component.literal("§e Hug ended "), true);
    }
    public static boolean isInHugFreeze(UUID playerId) {
        HugState state = hugState.get(playerId);
        if (state == null) return false;
        return state == HugState.START || state == HugState.HUGGING;
    }
}