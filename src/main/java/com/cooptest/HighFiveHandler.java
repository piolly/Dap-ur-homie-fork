package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public class HighFiveHandler {
    public static final float HIGH_FIVE_RANGE = 1.6f;
    public static final long HAND_RAISED_DURATION = 2500;
    public static final long COOLDOWN_MS = 1000;
    public static final long HIGH_FIVE_ANIM_DURATION = 1500;
    public static final long START_ANIM_DELAY_MS = 0;
    public static final long HIT_EFFECT_DELAY_MS = 100;
    public static final long END_ANIM_DURATION_MS = 1500;
    public static final double SPEED_TIER_1 = 5.0;
    public static final double SPEED_TIER_2 = 7.5;
    public static final double SPEED_TIER_3 = 12.0;
    public static final int SPEED_HISTORY_TICKS = 30;
    public static final Map<UUID, Long> handRaisedTime = new HashMap<>();
    public static final Map<UUID, Long> highFiveCooldown = new HashMap<>();
    public static final Map<UUID, Long> highFiveAnimStart = new HashMap<>();
    public static final Map<UUID, Long> startAnimTime = new HashMap<>();
    public static final Map<UUID, Long> endAnimTime = new HashMap<>();
    private static final Map<UUID, Long> comboWindowStart = new HashMap<>();
    private static final Map<UUID, UUID> comboPartner = new HashMap<>();
    private static final Map<UUID, Long> comboRequested = new HashMap<>();
    private static final Map<UUID, Long> comboFreezeEnd = new HashMap<>();
    private static final Map<UUID, ComboImpact> pendingComboImpacts = new HashMap<>();
    private static final long COMBO_WINDOW_MS = 1000;
    private static final long COMBO_FREEZE_MS = 2250;
    private static final long COMBO_SECOND_HIT_MS = 1290;
    private static final Map<UUID, Vec3> frozenPositions = new HashMap<>();
    private static final Map<BlockPos, BeaconRemoval> pendingBeaconRemovals = new HashMap<>();
    private static class BeaconRemoval {
        BlockPos beaconPos, glassPos;
        ServerLevel world;
        long removeTime;
        BeaconRemoval(BlockPos beaconPos, BlockPos glassPos, ServerLevel world, long removeTime) {
            this.beaconPos = beaconPos;
            this.glassPos = glassPos;
            this.world = world;
            this.removeTime = removeTime;
        }
    }
    private static final List<ParticleBeam> activeParticleBeams = new ArrayList<>();
    private static class ParticleBeam {
        ServerLevel world;
        Vec3 startPos;
        boolean isYellow;
        long endTime;
        ParticleBeam(ServerLevel world, Vec3 startPos, boolean isYellow, long endTime) {
            this.world = world;
            this.startPos = startPos;
            this.isYellow = isYellow;
            this.endTime = endTime;
        }
    }
    private static void spawnParticleBeam(ServerLevel world, Vec3 playerPos, boolean isYellow) {
        long now = System.currentTimeMillis();
        long endTime = now + 1750;
        activeParticleBeams.add(new ParticleBeam(world, playerPos.add(0, 0.5, 0), isYellow, endTime));
    }
    private static void tickParticleBeams(long now) {
        Iterator<ParticleBeam> it = activeParticleBeams.iterator();
        while (it.hasNext()) {
            ParticleBeam beam = it.next();
            if (now >= beam.endTime) {
                it.remove();
                continue;
            }
            for (int i = 0; i < 30; i++) {
                double y = beam.startPos.y + i * 0.5;
                if (beam.isYellow) {
                    beam.world.sendParticles(
                            ParticleTypes.END_ROD,
                            beam.startPos.x, y, beam.startPos.z,
                            3, 0.15, 0, 0.15, 0
                    );
                    beam.world.sendParticles(
                            ParticleTypes.FLAME,
                            beam.startPos.x, y, beam.startPos.z,
                            2, 0.1, 0, 0.1, 0
                    );
                    double spiralAngle = i * 0.3;
                    double spiralRadius = 0.5;
                    double spiralX = beam.startPos.x + Math.cos(spiralAngle) * spiralRadius;
                    double spiralZ = beam.startPos.z + Math.sin(spiralAngle) * spiralRadius;
                    beam.world.sendParticles(
                            ParticleTypes.END_ROD,
                            spiralX, y, spiralZ,
                            1, 0, 0, 0, 0
                    );
                } else {
                    beam.world.sendParticles(
                            ParticleTypes.SQUID_INK,
                            beam.startPos.x, y, beam.startPos.z,
                            3, 0.15, 0, 0.15, 0
                    );
                    beam.world.sendParticles(
                            ParticleTypes.LARGE_SMOKE,
                            beam.startPos.x, y, beam.startPos.z,
                            2, 0.1, 0, 0.1, 0
                    );
                    double spiralAngle = i * 0.3;
                    double spiralRadius = 0.5;
                    double spiralX = beam.startPos.x + Math.cos(spiralAngle) * spiralRadius;
                    double spiralZ = beam.startPos.z + Math.sin(spiralAngle) * spiralRadius;
                    beam.world.sendParticles(
                            ParticleTypes.SQUID_INK,
                            spiralX, y, spiralZ,
                            1, 0, 0, 0, 0
                    );
                }
            }
        }
    }
    private static class ComboImpact {
        ServerPlayer p1, p2;
        long impactTime;
        ComboImpact(ServerPlayer p1, ServerPlayer p2, long impactTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.impactTime = impactTime;
        }
    }
    private static final Map<UUID, PendingHighFive> pendingEffects = new HashMap<>();
    private static class PendingHighFive {
        ServerPlayer p1, p2;
        Vec3 pos;
        int tier;
        long effectTime;
        PendingHighFive(ServerPlayer p1, ServerPlayer p2, Vec3 pos, int tier, long effectTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.pos = pos;
            this.tier = tier;
            this.effectTime = effectTime;
        }
    }
    public static final Map<UUID, LinkedList<Double>> speedHistory = new HashMap<>();
    public static final Identifier HIGH_FIVE_REQUEST_ID = Identifier.fromNamespaceAndPath("cooptest", "high_five_request");
    public record HighFiveRequestPayload() implements CustomPacketPayload {
        public static final Type<HighFiveRequestPayload> ID = new Type<>(HIGH_FIVE_REQUEST_ID);
        public static final StreamCodec<FriendlyByteBuf, HighFiveRequestPayload> CODEC =
                StreamCodec.unit(new HighFiveRequestPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static final Identifier HAND_RAISED_SYNC_ID = Identifier.fromNamespaceAndPath("cooptest", "hand_raised_sync");
    public record HandRaisedSyncPayload(UUID playerId, boolean raised) implements CustomPacketPayload {
        public static final Type<HandRaisedSyncPayload> ID = new Type<>(HAND_RAISED_SYNC_ID);
        public static final StreamCodec<FriendlyByteBuf, HandRaisedSyncPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUUID(payload.playerId);
                            buf.writeBoolean(payload.raised);
                        },
                        buf -> new HandRaisedSyncPayload(buf.readUUID(), buf.readBoolean())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static final Identifier HIGH_FIVE_SUCCESS_ID = Identifier.fromNamespaceAndPath("cooptest", "high_five_success");
    public record HighFiveSuccessPayload(double x, double y, double z, UUID player1, UUID player2, int tier) implements CustomPacketPayload {
        public static final Type<HighFiveSuccessPayload> ID = new Type<>(HIGH_FIVE_SUCCESS_ID);
        public static final StreamCodec<FriendlyByteBuf, HighFiveSuccessPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeDouble(payload.x);
                            buf.writeDouble(payload.y);
                            buf.writeDouble(payload.z);
                            buf.writeUUID(payload.player1);
                            buf.writeUUID(payload.player2);
                            buf.writeInt(payload.tier);
                        },
                        buf -> new HighFiveSuccessPayload(
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readUUID(), buf.readUUID(), buf.readInt()
                        )
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static final Identifier HIGH_FIVE_ANIM_ID = Identifier.fromNamespaceAndPath("cooptest", "high_five_anim");
    public record HighFiveAnimPayload(UUID playerId, int animState) implements CustomPacketPayload {
        public static final Type<HighFiveAnimPayload> ID = new Type<>(HIGH_FIVE_ANIM_ID);
        public static final StreamCodec<FriendlyByteBuf, HighFiveAnimPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUUID(payload.playerId);
                            buf.writeInt(payload.animState);
                        },
                        buf -> new HighFiveAnimPayload(buf.readUUID(), buf.readInt())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static final Identifier COMBO_REQUEST_ID = Identifier.fromNamespaceAndPath("cooptest", "highfive_combo_request");
    public static final Identifier COMBO_WINDOW_ID = Identifier.fromNamespaceAndPath("cooptest", "highfive_combo_window");
    public static final Identifier COMBO_WINDOW_CLOSE_ID = Identifier.fromNamespaceAndPath("cooptest", "highfive_combo_window_close");
    public record ComboRequestPayload() implements CustomPacketPayload {
        public static final Type<ComboRequestPayload> ID = new Type<>(COMBO_REQUEST_ID);
        public static final StreamCodec<FriendlyByteBuf, ComboRequestPayload> CODEC =
                StreamCodec.unit(new ComboRequestPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record ComboWindowPayload(UUID playerId) implements CustomPacketPayload {
        public static final Type<ComboWindowPayload> ID = new Type<>(COMBO_WINDOW_ID);
        public static final StreamCodec<FriendlyByteBuf, ComboWindowPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> buf.writeUUID(payload.playerId),
                        buf -> new ComboWindowPayload(buf.readUUID())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record ComboWindowClosePayload(UUID playerId) implements CustomPacketPayload {
        public static final Type<ComboWindowClosePayload> ID = new Type<>(COMBO_WINDOW_CLOSE_ID);
        public static final StreamCodec<FriendlyByteBuf, ComboWindowClosePayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> buf.writeUUID(payload.playerId),
                        buf -> new ComboWindowClosePayload(buf.readUUID())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static final Identifier FREEZE_STATE_ID = Identifier.fromNamespaceAndPath("testcoop", "freeze_state");
    public record FreezeStatePayload(UUID playerId, boolean frozen) implements CustomPacketPayload {
        public static final Type<FreezeStatePayload> ID = new Type<>(FREEZE_STATE_ID);
        public static final StreamCodec<FriendlyByteBuf, FreezeStatePayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUUID(payload.playerId);
                            buf.writeBoolean(payload.frozen);
                        },
                        buf -> new FreezeStatePayload(buf.readUUID(), buf.readBoolean())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static final int ANIM_START = 1;
    public static final int ANIM_END   = 2;
    public static final int ANIM_HIT   = 3;
    public static final int ANIM_SIKE  = 4;
    public record SikeRequestPayload() implements CustomPacketPayload {
        public static final Type<SikeRequestPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "highfive_sike_request"));
        public static final StreamCodec<FriendlyByteBuf, SikeRequestPayload> CODEC =
                StreamCodec.unit(new SikeRequestPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    private static final Set<UUID> sikeMode = new HashSet<>();
    private static final Map<UUID, Long> sikeStunEnd = new HashMap<>();
    private static final Map<UUID, Long> sikeSlowEnd = new HashMap<>();
    private static final long SIKE_ANIM_MS = 1458L;
    private static final long SIKE_SLOW_MS = 2000L;
    private static final Identifier SIKE_SLOW_ID = Identifier.fromNamespaceAndPath("testcoop", "sike_slow");
    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(HighFiveRequestPayload.ID, HighFiveRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HighFiveSuccessPayload.ID, HighFiveSuccessPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HandRaisedSyncPayload.ID, HandRaisedSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HighFiveAnimPayload.ID, HighFiveAnimPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ComboRequestPayload.ID, ComboRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ComboWindowPayload.ID, ComboWindowPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ComboWindowClosePayload.ID, ComboWindowClosePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FreezeStatePayload.ID, FreezeStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SikeRequestPayload.ID, SikeRequestPayload.CODEC);
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HighFiveRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (!CoopMovesConfig.get().enableHighFive) {
                    return;
                }
                onHighFiveRequest(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ComboRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (!CoopMovesConfig.get().enableHighFiveCombo) {
                    return;
                }
                onComboRequest(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SikeRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (!CoopMovesConfig.get().enableHighFive) return;
                UUID id = player.getUUID();
                sikeMode.add(id);
                onHighFiveRequest(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            tickParticleBeams(now);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID id = player.getUUID();
                Vec3 velocity = player.getDeltaMovement();
                double speed = velocity.length() * 20.0;
                LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());
                history.addLast(speed);
                while (history.size() > SPEED_HISTORY_TICKS) {
                    history.removeFirst();
                }
            }
            Iterator<Map.Entry<UUID, PendingHighFive>> pendingIt = pendingEffects.entrySet().iterator();
            while (pendingIt.hasNext()) {
                Map.Entry<UUID, PendingHighFive> entry = pendingIt.next();
                PendingHighFive pending = entry.getValue();
                if (now >= pending.effectTime) {
                    executeHighFiveEffects(pending.p1, pending.p2, pending.pos, pending.tier);
                    pendingIt.remove();
                }
            }
            java.util.Set<String> processedComboPairs = new java.util.HashSet<>();
            Iterator<Map.Entry<UUID, Long>> comboWindowIt = comboWindowStart.entrySet().iterator();
            while (comboWindowIt.hasNext()) {
                Map.Entry<UUID, Long> entry = comboWindowIt.next();
                UUID playerId = entry.getKey();
                long windowStart = entry.getValue();
                if (now - windowStart > COMBO_WINDOW_MS) {
                    boolean playerPressed = comboRequested.containsKey(playerId);
                    UUID partnerId = comboPartner.get(playerId);
                    if (playerPressed && partnerId != null) {
                        String pairKey = playerId.compareTo(partnerId) < 0
                                ? playerId + ":" + partnerId
                                : partnerId + ":" + playerId;
                        if (!processedComboPairs.contains(pairKey)) {
                            processedComboPairs.add(pairKey);
                            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                            ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
                            if (player != null && partner != null) {
                                boolean partnerPressed = comboRequested.containsKey(partnerId);
                                if (!partnerPressed) {
                                    player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c✗ " + partner.getName().getString() + " missed the combo!"));
                                    partner.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c✗ You missed the combo! " + player.getName().getString() + " pressed H!"));
                                }
                            }
                        }
                    }
                    comboWindowIt.remove();
                    comboPartner.remove(playerId);
                    comboRequested.remove(playerId);
                }
            }
            Iterator<Map.Entry<UUID, ComboImpact>> comboImpactIt = pendingComboImpacts.entrySet().iterator();
            while (comboImpactIt.hasNext()) {
                Map.Entry<UUID, ComboImpact> entry = comboImpactIt.next();
                ComboImpact impact = entry.getValue();
                if (now >= impact.impactTime) {
                    executeSecondImpact(impact.p1, impact.p2);
                    comboImpactIt.remove();
                }
            }
            Iterator<Map.Entry<UUID, Long>> freezeIt = comboFreezeEnd.entrySet().iterator();
            while (freezeIt.hasNext()) {
                Map.Entry<UUID, Long> entry = freezeIt.next();
                if (now >= entry.getValue()) {
                    UUID playerId = entry.getKey();
                    freezeIt.remove();
                    frozenPositions.remove(playerId);
                    handRaisedTime.remove(playerId);
                    startAnimTime.remove(playerId);
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        syncHandRaised(player, false);
                        PoseNetworking.broadcastAnimState(player, 0);
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                            ServerPlayNetworking.send(p, new FreezeStatePayload(playerId, false));
                        }
                        ServerPlayNetworking.send(player, new ComboWindowClosePayload(playerId));
                        System.out.println("[HighFive] Combo ended - cleared hand raised and reset anim for " + player.getName().getString());
                    }
                }
            }
            for (Map.Entry<UUID, Vec3> entry : frozenPositions.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    Vec3 frozenPos = entry.getValue();
                    Vec3 currentPos = player.position();
                    if (currentPos.distanceToSqr(frozenPos) > 0.01) {
                        player.teleportTo(frozenPos.x, frozenPos.y, frozenPos.z);
                        player.setDeltaMovement(Vec3.ZERO);
                        player.hurtMarked = true;
                    }
                }
            }
            Iterator<Map.Entry<UUID, Long>> animIt = highFiveAnimStart.entrySet().iterator();
            while (animIt.hasNext()) {
                Map.Entry<UUID, Long> entry = animIt.next();
                if (now - entry.getValue() > HIGH_FIVE_ANIM_DURATION) {
                    UUID playerId = entry.getKey();
                    boolean inCombo   = comboFreezeEnd.containsKey(playerId);
                    boolean inHug     = HighFiveQTEHugHandler.isInHugSession(playerId);
                    boolean inDapCombo = DapComboChain.isInCombo(playerId);
                    if (!inCombo && !inHug && !inDapCombo) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            PoseNetworking.broadcastAnimState(player, 0);
                        }
                    }
                    animIt.remove();
                }
            }
            Iterator<Map.Entry<BlockPos, BeaconRemoval>> beaconIt = pendingBeaconRemovals.entrySet().iterator();
            while (beaconIt.hasNext()) {
                Map.Entry<BlockPos, BeaconRemoval> entry = beaconIt.next();
                BeaconRemoval removal = entry.getValue();
                if (now >= removal.removeTime) {
                    removal.world.setBlockAndUpdate(removal.beaconPos, Blocks.AIR.defaultBlockState());
                    removal.world.setBlockAndUpdate(removal.glassPos, Blocks.AIR.defaultBlockState());
                    beaconIt.remove();
                }
            }
            Iterator<Map.Entry<UUID, Long>> sikeStunIt = sikeStunEnd.entrySet().iterator();
            while (sikeStunIt.hasNext()) {
                Map.Entry<UUID, Long> entry = sikeStunIt.next();
                if (now >= entry.getValue()) {
                    UUID victimId = entry.getKey();
                    sikeStunIt.remove();
                    ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
                    if (victim != null) {
                        for (ServerPlayer p : PlayerLookup.all(server)) {
                            ServerPlayNetworking.send(p, new FreezeStatePayload(victimId, false));
                        }
                        frozenPositions.remove(victimId);
                        PoseNetworking.broadcastAnimState(victim, 0);
                        syncHandRaised(victim, false);
                        applySikeSlow(victim);
                        sikeSlowEnd.put(victimId, now + SIKE_SLOW_MS);
                    }
                }
            }
            Iterator<Map.Entry<UUID, Long>> sikeSlowIt = sikeSlowEnd.entrySet().iterator();
            while (sikeSlowIt.hasNext()) {
                Map.Entry<UUID, Long> entry = sikeSlowIt.next();
                if (now >= entry.getValue()) {
                    UUID victimId = entry.getKey();
                    sikeSlowIt.remove();
                    ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
                    if (victim != null) removeSikeSlow(victim);
                }
            }
            Iterator<Map.Entry<UUID, Long>> endIt = endAnimTime.entrySet().iterator();
            while (endIt.hasNext()) {
                Map.Entry<UUID, Long> entry = endIt.next();
                if (now >= entry.getValue()) {
                    UUID playerId = entry.getKey();
                    endIt.remove();
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        PoseNetworking.broadcastAnimState(player, 0);
                    }
                }
            }
            Iterator<Map.Entry<UUID, Long>> it = handRaisedTime.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID playerId = entry.getKey();
                long startAnimStartTime = startAnimTime.getOrDefault(playerId, entry.getValue());
                if (now - startAnimStartTime > START_ANIM_DELAY_MS + HAND_RAISED_DURATION) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    it.remove();
                    startAnimTime.remove(playerId);
                    if (player != null) {
                        executeEndAnimation(player);
                        syncHandRaised(player, false);
                    }
                }
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID playerId = player.getUUID();
                if (!handRaisedTime.containsKey(playerId)) continue;
                if (isOnCooldown(playerId)) continue;
                if (isInBlockingState(playerId)) continue;
                Long startTime = startAnimTime.get(playerId);
                if (startTime != null && now - startTime < START_ANIM_DELAY_MS) {
                    continue;
                }
                ServerPlayer partner = findHighFivePartner(player);
                if (partner != null) {
                    Long partnerStartTime = startAnimTime.get(partner.getUUID());
                    if (partnerStartTime != null && now - partnerStartTime < START_ANIM_DELAY_MS) {
                        continue;
                    }
                    executeHighFive(player, partner);
                }
            }
        });
    }
    public static boolean isInBlockingState(UUID playerId) {
        return isInBlockingAnimation(playerId)
                || FallDapHandler.isSquashed(playerId)
                || sikeStunEnd.containsKey(playerId)
                || sikeSlowEnd.containsKey(playerId);
    }
    public static boolean isInHighFiveMode(UUID playerId) {
        return handRaisedTime.containsKey(playerId) || startAnimTime.containsKey(playerId);
    }
    public static boolean isInAnyHighFiveState(UUID playerId) {
        return isInHighFiveMode(playerId)
                || isInBlockingState(playerId)
                || highFiveAnimStart.containsKey(playerId);
    }
    public static boolean canPerformAction(UUID playerId) {
        return !isInBlockingState(playerId);
    }
    private static void executeEndAnimation(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        endAnimTime.put(playerId, now + END_ANIM_DURATION_MS);
        broadcastHighFiveAnim(player, ANIM_END);
        ServerLevel world = player.level();
        Vec3 pos = player.position().add(0, 1.6, 0);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.4f, 1.2f);
        world.sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 6, 0.15, 0.15, 0.15, 0.01);
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§7*left hanging*"));
    }
    private static void broadcastHighFiveAnim(ServerPlayer player, int animState) {
        var server = player.level().getServer();
        if (server == null) return;
        HighFiveAnimPayload payload = new HighFiveAnimPayload(player.getUUID(), animState);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
    private static double getMaxRecentSpeed(UUID playerId) {
        LinkedList<Double> history = speedHistory.get(playerId);
        if (history == null || history.isEmpty()) return 0.0;
        double maxSpeed = 0.0;
        for (Double speed : history) {
            if (speed > maxSpeed) maxSpeed = speed;
        }
        return maxSpeed;
    }
    private static void onHighFiveRequest(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!handRaisedTime.containsKey(uuid) && ChargedDapHandler.isCharging(uuid)) {
            System.out.println("[HighFive] Blocked H raise - G is active!");
            syncHandRaised(player, false);
            return;
        }
        if (ChargedDapHandler.isInComboCooldown(uuid)) {
            System.out.println("[HighFive] Blocked H raise - combo cooldown active!");
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§cWait 1 second after combo!"));
            syncHandRaised(player, false);
            return;
        }
        if (isInBlockingState(uuid)) return;
        if (FallCatchHandler.isInCatchReadyMode(uuid)) return;
        if (isOnCooldown(uuid)) return;
        if (!player.getMainHandItem().isEmpty()) {
            return;
        }
        if (handRaisedTime.containsKey(uuid)) {
            handRaisedTime.remove(uuid);
            startAnimTime.remove(uuid);
            syncHandRaised(player, false);
            executeEndAnimation(player);
        } else {
            long now = System.currentTimeMillis();
            handRaisedTime.put(uuid, now);
            startAnimTime.put(uuid, now);
            syncHandRaised(player, true);
            broadcastHighFiveAnim(player, ANIM_START);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3f, 1.5f);
        }
    }
    public static void syncHandRaised(ServerPlayer player, boolean raised) {
        if (player == null) return;
        System.out.println("[HighFive Server] syncHandRaised: " + player.getName().getString() + " raised=" + raised);
        HandRaisedSyncPayload payload = new HandRaisedSyncPayload(player.getUUID(), raised);
        for (ServerPlayer other : PlayerLookup.all(player.level().getServer())) {
            ServerPlayNetworking.send(other, payload);
        }
    }
    private static ServerPlayer findHighFivePartner(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(HIGH_FIVE_RANGE);
        long now = System.currentTimeMillis();
        for (ServerPlayer other : player.level().players()) {
            if (other == player) continue;
            if (!handRaisedTime.containsKey(other.getUUID())) continue;
            if (isOnCooldown(other.getUUID())) continue;
            if (isInBlockingState(other.getUUID())) continue;
            Long otherStartTime = startAnimTime.get(other.getUUID());
            if (otherStartTime != null && now - otherStartTime < START_ANIM_DELAY_MS) {
                continue;
            }
            if (searchBox.intersects(other.getBoundingBox())) {
                return other;
            }
        }
        return null;
    }
    private static void executeHighFive(ServerPlayer player1, ServerPlayer player2) {
        boolean p1Siking = sikeMode.remove(player1.getUUID());
        boolean p2Siking = sikeMode.remove(player2.getUUID());
        if (p1Siking && p2Siking) { executeMutualSike(player1, player2); return; }
        if (p1Siking) { executeSike(player1, player2); return; }
        if (p2Siking) { executeSike(player2, player1); return; }
        long now = System.currentTimeMillis();
        highFiveCooldown.put(player1.getUUID(), now);
        highFiveCooldown.put(player2.getUUID(), now);
        handRaisedTime.remove(player1.getUUID());
        handRaisedTime.remove(player2.getUUID());
        startAnimTime.remove(player1.getUUID());
        startAnimTime.remove(player2.getUUID());
        syncHandRaised(player1, false);
        syncHandRaised(player2, false);
        System.out.println("[HighFive] Regular highfive - cleared hand raised for both players");
        highFiveAnimStart.put(player1.getUUID(), now);
        highFiveAnimStart.put(player2.getUUID(), now);
        broadcastHighFiveAnim(player1, ANIM_HIT);
        broadcastHighFiveAnim(player2, ANIM_HIT);
        PoseNetworking.broadcastAnimState(player1, 20);
        PoseNetworking.broadcastAnimState(player2, 20);
        double speed1 = getMaxRecentSpeed(player1.getUUID());
        double speed2 = getMaxRecentSpeed(player2.getUUID());
        double maxSpeed = Math.max(speed1, speed2);
        int tier = 0;
        if (maxSpeed >= SPEED_TIER_3) tier = 3;
        else if (maxSpeed >= SPEED_TIER_2) tier = 2;
        else if (maxSpeed >= SPEED_TIER_1) tier = 1;
        Vec3 pos1 = player1.position();
        Vec3 pos2 = player2.position();
        Vec3 highFivePos = pos1.add(pos2).scale(0.5).add(0, 1.4, 0);
        pendingEffects.put(player1.getUUID(), new PendingHighFive(
                player1, player2, highFivePos, tier, now + HIT_EFFECT_DELAY_MS
        ));
        speedHistory.remove(player1.getUUID());
        speedHistory.remove(player2.getUUID());
        syncHandRaised(player1, false);
        syncHandRaised(player2, false);
        HighFiveSuccessPayload successPayload = new HighFiveSuccessPayload(
                highFivePos.x, highFivePos.y, highFivePos.z,
                player1.getUUID(), player2.getUUID(), tier
        );
        for (ServerPlayer other : PlayerLookup.all(player1.level().getServer())) {
            ServerPlayNetworking.send(other, successPayload);
        }
        comboWindowStart.put(player1.getUUID(), now);
        comboWindowStart.put(player2.getUUID(), now);
        comboPartner.put(player1.getUUID(), player2.getUUID());
        comboPartner.put(player2.getUUID(), player1.getUUID());
        final ServerPlayer fp1 = player1, fp2 = player2;
        new Thread(() -> {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            fp1.level().getServer().execute(() -> {
                ServerPlayNetworking.send(fp1, new ComboWindowPayload(fp1.getUUID()));
                ServerPlayNetworking.send(fp2, new ComboWindowPayload(fp2.getUUID()));
                HighFiveQTEHugHandler.startHugQTE(fp1, fp2);
            });
        }).start();
        if (CoopMovesConfig.get().enableHighFiveHug) {
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                fp1.level().getServer().execute(() -> {
                    if (!HighFiveQTEHugHandler.isInHugSession(fp1.getUUID())
                            && !HighFiveQTEHugHandler.isInHugSession(fp2.getUUID())) {
                        HighFiveHugHandler.startHugHold(fp1, fp2);
                    }
                });
            }).start();
        }
    }
    private static void executeSike(ServerPlayer siker, ServerPlayer victim) {
        long now = System.currentTimeMillis();
        UUID sikerId = siker.getUUID();
        UUID victimId = victim.getUUID();
        boolean mutualSike = sikeMode.contains(victimId);
        sikeMode.remove(victimId);
        handRaisedTime.remove(sikerId);
        handRaisedTime.remove(victimId);
        startAnimTime.remove(sikerId);
        startAnimTime.remove(victimId);
        syncHandRaised(siker, false);
        syncHandRaised(victim, false);
        if (mutualSike) {
            siker.hurtClient(siker.level().damageSources().genericKill());
            victim.hurtClient(victim.level().damageSources().genericKill());
            Vec3 toVictim = victim.position().subtract(siker.position()).normalize();
            if (toVictim.lengthSqr() < 0.001) toVictim = new Vec3(1, 0, 0);
            siker.push(toVictim.reverse().x * 0.6, 0.5, toVictim.reverse().z * 0.6);
            siker.hurtMarked = true;
            victim.push(toVictim.x * 0.6, 0.5, toVictim.z * 0.6);
            victim.hurtMarked = true;
            broadcastHighFiveAnim(siker,  ANIM_SIKE);
            broadcastHighFiveAnim(victim, ANIM_SIKE);
            PoseNetworking.broadcastAnimState(siker,  63);
            PoseNetworking.broadcastAnimState(victim, 63);
            sikeStunEnd.put(sikerId,  now + SIKE_ANIM_MS);
            sikeStunEnd.put(victimId, now + SIKE_ANIM_MS);
            for (ServerPlayer p : PlayerLookup.all(siker.level().getServer())) {
                ServerPlayNetworking.send(p, new FreezeStatePayload(sikerId,  true));
                ServerPlayNetworking.send(p, new FreezeStatePayload(victimId, true));
            }
            frozenPositions.put(sikerId,  siker.position());
            frozenPositions.put(victimId, victim.position());
            ServerLevel world = siker.level();
            Vec3 mid = siker.position().add(victim.position()).scale(0.5).add(0, 1, 0);
            world.playSound(null, mid.x, mid.y, mid.z,
                    SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.2f, 0.7f);
            world.playSound(null, mid.x, mid.y, mid.z,
                    SoundEvents.DONKEY_ANGRY, SoundSource.PLAYERS, 0.9f, 0.8f);
            world.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 2, 0.3, 0.3, 0.3, 0);
            world.sendParticles(ParticleTypes.ANGRY_VILLAGER, mid.x, mid.y + 1, mid.z, 8, 0.4, 0.3, 0.4, 0.05);
            siker.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§4§l💥 MUTUAL SIKE! You both suffer!"));
            victim.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§4§l💥 MUTUAL SIKE! You both suffer!"));
            highFiveCooldown.put(sikerId,  now);
            highFiveCooldown.put(victimId, now);
            return;
        }
        PoseNetworking.broadcastAnimState(siker, 0);
        broadcastHighFiveAnim(victim, ANIM_SIKE);
        PoseNetworking.broadcastAnimState(victim, 63);
        sikeStunEnd.put(victimId, now + SIKE_ANIM_MS);
        frozenPositions.put(victimId, victim.position());
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;
        for (ServerPlayer p : PlayerLookup.all(siker.level().getServer())) {
            ServerPlayNetworking.send(p, new FreezeStatePayload(victimId, true));
        }
        siker.level().playSound(null,
                siker.getX(), siker.getY(), siker.getZ(),
                SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        ServerLevel world = victim.level();
        Vec3 vp = victim.position().add(0, 1.8, 0);
        world.playSound(null, vp.x, vp.y, vp.z,
                SoundEvents.VILLAGER_NO,  SoundSource.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, vp.x, vp.y, vp.z,
                SoundEvents.UI_TOAST_OUT,        SoundSource.PLAYERS, 0.7f, 0.6f);
        world.sendParticles(ParticleTypes.FALLING_WATER, vp.x - 0.15, vp.y, vp.z,  6, 0.1, 0.05, 0.1, 0.01);
        world.sendParticles(ParticleTypes.FALLING_WATER, vp.x + 0.15, vp.y, vp.z,  6, 0.1, 0.05, 0.1, 0.01);
        world.sendParticles(ParticleTypes.SPLASH,        vp.x, vp.y - 0.5, vp.z,  10, 0.2, 0.05, 0.2, 0.02);
        world.sendParticles(ParticleTypes.POOF, vp.x, vp.y + 0.3, vp.z, 8, 0.2, 0.1, 0.2, 0.03);
        world.sendParticles(ParticleTypes.ANGRY_VILLAGER, vp.x, vp.y + 0.6, vp.z, 4, 0.3, 0.2, 0.3, 0.05);
        world.sendParticles(ParticleTypes.LARGE_SMOKE, vp.x, vp.y, vp.z, 5, 0.15, 0.2, 0.15, 0.01);
        siker.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l😂 SIKE!"));
        victim.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§lSIKE!"));
        highFiveCooldown.put(sikerId, now);
    }
    private static void executeHighFiveEffects(ServerPlayer player1, ServerPlayer player2,
                                               Vec3 highFivePos, int tier) {
        ServerLevel world = player1.level();
        switch (tier) {
            case 0 -> executeTier0(world, highFivePos, player1, player2);
            case 1 -> executeTier1(world, highFivePos, player1, player2);
            case 2 -> executeTier2(world, highFivePos, player1, player2);
            case 3 -> executeTier3(world, highFivePos, player1, player2);
        }
    }
    private static void executeTier0(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 1.1f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.8f);
        spawnStarBurst(world, pos, 10, 0.3);
        world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.08);
        world.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.02);
        applyKnockback(p1, p2, pos, 0.1);
    }
    private static void executeTier1(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 2.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.8f, 1.2f);
        spawnStarBurst(world, pos, 16, 0.5);
        world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 15, 0.15, 0.15, 0.15, 0.12);
        world.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 10, 0.25, 0.25, 0.25, 0.03);
        world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0.05);
        applyKnockback(p1, p2, pos, 0.4);
    }
    private static void executeTier2(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, 0.9f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.2f, 2.0f);
        spawnStarBurst(world, pos, 24, 0.7);
        world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 25, 0.2, 0.2, 0.2, 0.18);
        world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.1);
        world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f), pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.05);
        applyKnockback(p1, p2, pos, 0.8);
    }
    private static void executeTier3(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EPIC_DAP, SoundSource.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.3f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0f, 0.5f);
        spawnStarBurst(world, pos, 32, 1.0);
        world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.5, 0.5, 0.5, 0);
        world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.3, 0.3, 0.3, 0.25);
        world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 25, 0.5, 0.5, 0.5, 0.15);
        world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f), pos.x, pos.y, pos.z, 2, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 20, 0.4, 0.4, 0.4, 0.1);
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.5, 0.5, 0.5, 0.3);
        createBattleShockwave(world, pos, p1, p2, 10.0);
        ChargedDapHandler.applyImpactFreeze(p1, p2, 3);
        applyKnockback(p1, p2, pos, 0.3);
        p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l⚡ SHOCKWAVE! ⚡"));
        p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l⚡ SHOCKWAVE! ⚡"));
    }
    private static void createBattleShockwave(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2, double radius) {
        for (int ring = 1; ring <= 5; ring++) {
            double r = ring * 2.0;
            int points = (int)(r * 8);
            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians((360.0 / points) * i);
                double x = pos.x + Math.cos(angle) * r;
                double z = pos.z + Math.sin(angle) * r;
                if (ring <= 2) {
                    world.sendParticles(ParticleTypes.CLOUD, x, pos.y, z, 1, 0.1, 0.2, 0.1, 0.02);
                } else if (ring <= 4) {
                    world.sendParticles(ParticleTypes.SWEEP_ATTACK, x, pos.y + 0.5, z, 1, 0, 0, 0, 0);
                } else {
                    world.sendParticles(ParticleTypes.CRIT, x, pos.y + 0.5, z, 2, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }
        AABB pushBox = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );
        for (Entity entity : world.getEntities(null, pushBox)) {
            if (entity == p1 || entity == p2) continue;
            double dist = entity.position().distanceTo(pos);
            if (dist > radius || dist < 0.5) continue;
            double strength = (1.0 - (dist / radius)) * 4.0 + 1.0;
            Vec3 dir = entity.position().subtract(pos).normalize();
            if (dir.lengthSqr() < 0.01) {
                dir = new Vec3(Math.random() - 0.5, 0, Math.random() - 0.5).normalize();
            }
            entity.push(dir.x * strength, strength * 0.6, dir.z * strength);
            entity.hurtMarked = true;
            world.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    5, 0.2, 0.2, 0.2, 0.1);
        }
    }
    private static void spawnStarBurst(ServerLevel world, Vec3 pos, int rays, double spread) {
        for (int i = 0; i < rays; i++) {
            double angle = (2 * Math.PI * i) / rays;
            double dx = Math.cos(angle) * spread;
            double dz = Math.sin(angle) * spread;
            world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
        }
    }
    private static void applyKnockback(ServerPlayer p1, ServerPlayer p2, Vec3 center, double strength) {
        Vec3 dir1 = p1.position().subtract(center).normalize();
        Vec3 dir2 = p2.position().subtract(center).normalize();
        if (dir1.lengthSqr() < 0.01) dir1 = new Vec3(1, 0, 0);
        if (dir2.lengthSqr() < 0.01) dir2 = new Vec3(-1, 0, 0);
        double push = 0.15 * strength;
        p1.setDeltaMovement(dir1.x * push, 0.05, dir1.z * push);
        p2.setDeltaMovement(dir2.x * push, 0.05, dir2.z * push);
        p1.hurtMarked = true;
        p2.hurtMarked = true;
    }
    private static void createHighFiveExplosion(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        double radius = 4.0;
        AABB damageBox = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );
        for (Entity entity : world.getEntities(null, damageBox)) {
            if (entity == p1 || entity == p2) continue;
            double dist = entity.position().distanceTo(pos);
            if (dist > radius) continue;
            double knockbackStrength = (1.0 - dist / radius) * 2.0;
            Vec3 knockDir = entity.position().subtract(pos).normalize();
            entity.push(knockDir.x * knockbackStrength, knockbackStrength * 0.5, knockDir.z * knockbackStrength);
            entity.hurtMarked = true;
            if (entity instanceof ServerPlayer target) {
                float damage = (float)((1.0 - dist / radius) * 8.0);
                target.hurtClient(world.damageSources().explosion(null));
            }
        }
    }
    private static boolean isOnCooldown(UUID uuid) {
        Long cooldownStart = highFiveCooldown.get(uuid);
        if (cooldownStart == null) return false;
        return System.currentTimeMillis() - cooldownStart < COOLDOWN_MS;
    }
    public static boolean hasHandRaised(UUID uuid) {
        return handRaisedTime.containsKey(uuid);
    }
    public static boolean isInBlockingAnimation(UUID uuid) {
        if (highFiveAnimStart.containsKey(uuid)) return true;
        if (endAnimTime.containsKey(uuid)) return true;
        if (comboFreezeEnd.containsKey(uuid)) return true;
        if (ChargedDapHandler.isInBlockingAnimation(uuid)) return true;
        return false;
    }
    public static float getHighFiveAnimProgress(UUID uuid) {
        Long startTime = highFiveAnimStart.get(uuid);
        if (startTime == null) return -1f;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > HIGH_FIVE_ANIM_DURATION) {
            highFiveAnimStart.remove(uuid);
            return -1f;
        }
        return (float) elapsed / HIGH_FIVE_ANIM_DURATION;
    }
    private static void onComboRequest(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();
        Long windowStart = comboWindowStart.get(playerId);
        if (windowStart == null) return;
        long elapsed = now - windowStart;
        if (elapsed > COMBO_WINDOW_MS) {
            comboWindowStart.remove(playerId);
            comboPartner.remove(playerId);
            comboRequested.remove(playerId);
            return;
        }
        comboRequested.put(playerId, now);
        UUID partnerId = comboPartner.get(playerId);
        if (partnerId == null) return;
        ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
        if (partner == null) return;
        if (!comboWindowStart.containsKey(partnerId)) {
            return;
        }
        if (comboRequested.containsKey(partnerId)) {
            executeCombo(player, partner);
        }
    }
    private static void executeCombo(ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();
        comboWindowStart.remove(id1);
        comboWindowStart.remove(id2);
        comboPartner.remove(id1);
        comboPartner.remove(id2);
        comboRequested.remove(id1);
        comboRequested.remove(id2);
        handRaisedTime.remove(id1);
        handRaisedTime.remove(id2);
        startAnimTime.remove(id1);
        startAnimTime.remove(id2);
        syncHandRaised(p1, false);
        syncHandRaised(p2, false);
        System.out.println("[HighFive] Combo started - cleared hand raised for both players");
        comboFreezeEnd.put(id1, now + COMBO_FREEZE_MS);
        comboFreezeEnd.put(id2, now + COMBO_FREEZE_MS);
        frozenPositions.put(id1, p1.position());
        frozenPositions.put(id2, p2.position());
        p1.setDeltaMovement(Vec3.ZERO);
        p2.setDeltaMovement(Vec3.ZERO);
        p1.hurtMarked = true;
        p2.hurtMarked = true;
        for (ServerPlayer p : PlayerLookup.all(p1.level().getServer())) {
            ServerPlayNetworking.send(p, new FreezeStatePayload(id1, true));
            ServerPlayNetworking.send(p, new FreezeStatePayload(id2, true));
        }
        PoseNetworking.broadcastAnimState(p1, 21);
        PoseNetworking.broadcastAnimState(p2, 21);
        pendingComboImpacts.put(id1, new ComboImpact(p1, p2, now + COMBO_SECOND_HIT_MS));
        p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l✨ COMBO! ✨"));
        p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l✨ COMBO! ✨"));
    }
    private static void executeSecondImpact(ServerPlayer p1, ServerPlayer p2) {
        Vec3 pos = p1.position().add(p2.position()).scale(0.5).add(0, 0.5, 0);
        ServerLevel world = p1.level();
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5f, 1.0f);
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.3, 0.3, 0.3, 0.1);
        world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 20, 0.3, 0.3, 0.3, 0.15);
        p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§e⚡ PERFECT! ⚡"));
        p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§e⚡ PERFECT! ⚡"));
    }
    private static void spawnComboAura(ServerLevel world, Vec3 pos1, Vec3 pos2) {
        int particleCount = 20;
        double radius = 1.5;
        for (int i = 0; i < particleCount; i++) {
            double angle = (2 * Math.PI * i) / particleCount;
            double x1 = pos1.x + Math.cos(angle) * radius;
            double z1 = pos1.z + Math.sin(angle) * radius;
            world.sendParticles(ParticleTypes.SQUID_INK,
                    x1, pos1.y + 1, z1,
                    1, 0.1, 0.3, 0.1, 0.02);
            double innerRadius = radius * 0.7;
            double x1Inner = pos1.x + Math.cos(angle) * innerRadius;
            double z1Inner = pos1.z + Math.sin(angle) * innerRadius;
            world.sendParticles(ParticleTypes.END_ROD,
                    x1Inner, pos1.y + 1, z1Inner,
                    1, 0.1, 0.3, 0.1, 0.02);
            double x2 = pos2.x + Math.cos(angle) * radius;
            double z2 = pos2.z + Math.sin(angle) * radius;
            world.sendParticles(ParticleTypes.SQUID_INK,
                    x2, pos2.y + 1, z2,
                    1, 0.1, 0.3, 0.1, 0.02);
            double x2Inner = pos2.x + Math.cos(angle) * innerRadius;
            double z2Inner = pos2.z + Math.sin(angle) * innerRadius;
            world.sendParticles(ParticleTypes.END_ROD,
                    x2Inner, pos2.y + 1, z2Inner,
                    1, 0.1, 0.3, 0.1, 0.02);
        }
    }
    public static boolean isInComboFreeze(UUID playerId) {
        Long freezeEnd = comboFreezeEnd.get(playerId);
        if (freezeEnd == null) return false;
        return System.currentTimeMillis() < freezeEnd;
    }
    public static void cleanup(UUID playerId) {
        handRaisedTime.remove(playerId);
        highFiveCooldown.remove(playerId);
        highFiveAnimStart.remove(playerId);
        startAnimTime.remove(playerId);
        endAnimTime.remove(playerId);
        speedHistory.remove(playerId);
        pendingEffects.remove(playerId);
        comboWindowStart.remove(playerId);
        comboPartner.remove(playerId);
        comboRequested.remove(playerId);
        comboFreezeEnd.remove(playerId);
        pendingComboImpacts.remove(playerId);
        frozenPositions.remove(playerId);
        sikeMode.remove(playerId);
        sikeStunEnd.remove(playerId);
        sikeSlowEnd.remove(playerId);
    }
    private static void applySikeSlow(ServerPlayer player) {
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(SIKE_SLOW_ID);
        attr.addPermanentModifier(new AttributeModifier(
                SIKE_SLOW_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }
    private static void removeSikeSlow(ServerPlayer player) {
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(SIKE_SLOW_ID);
    }
    private static void executeMutualSike(ServerPlayer p1, ServerPlayer p2) {
        long now = System.currentTimeMillis();
        for (ServerPlayer p : List.of(p1, p2)) {
            UUID id = p.getUUID();
            handRaisedTime.remove(id);
            startAnimTime.remove(id);
            syncHandRaised(p, false);
            PoseNetworking.broadcastAnimState(p, 0);
            highFiveCooldown.put(id, now);
        }
        Vec3 toP2 = p2.position().subtract(p1.position()).normalize();
        if (toP2.lengthSqr() < 0.01) toP2 = new Vec3(1, 0, 0);
        ServerLevel world = p1.level();
        p1.hurtClient(world.damageSources().genericKill());
        p2.hurtClient(world.damageSources().genericKill());
        p1.setDeltaMovement(toP2.reverse().scale(0.65).add(0, 0.5, 0));
        p1.hurtMarked = true;
        p2.setDeltaMovement(toP2.scale(0.65).add(0, 0.5, 0));
        p2.hurtMarked = true;
        Vec3 mid = p1.position().add(p2.position()).scale(0.5).add(0, 1.0, 0);
        world.sendParticles(ParticleTypes.CRIT,          mid.x, mid.y, mid.z, 24, 0.4, 0.4, 0.4, 0.2);
        world.sendParticles(ParticleTypes.SMOKE,         mid.x, mid.y, mid.z, 12, 0.3, 0.3, 0.3, 0.02);
        world.sendParticles(ParticleTypes.FALLING_WATER, mid.x, mid.y + 0.5, mid.z, 20, 0.3, 0.2, 0.3, 0.02);
        world.playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 0.7f);
        world.playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.VILLAGER_NO,        SoundSource.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.WITCH_CELEBRATE,    SoundSource.PLAYERS, 0.8f, 1.2f);
        p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l💥 MUTUAL SIKE! You both played dirty!"));
        p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l💥 MUTUAL SIKE! You both played dirty!"));
    }
}