package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static java.util.Collections.emptySet;
public class DapHoldHandler {
    private static final long ANIM_LENGTH_MS    = 1042;
    private static final long J_WINDOW_START_MS = 330;
    private static final long IMPACT_MS         = 420;
    private static final long J_WINDOW_END_MS   = 1330;
    private static final double STOP_DISTANCE   = 1.5;
    private static final double TP_SPEED        = 0.08;
    public record DapHoldStartPayload(UUID playerId, UUID partnerId, int role) implements CustomPacketPayload {
        public static final Type<DapHoldStartPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_start"));
        public static final StreamCodec<FriendlyByteBuf, DapHoldStartPayload> CODEC = StreamCodec.ofMember(
                (p, buf) -> { buf.writeUUID(p.playerId()); buf.writeUUID(p.partnerId()); buf.writeInt(p.role()); },
                buf -> new DapHoldStartPayload(buf.readUUID(), buf.readUUID(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record DapHoldWindowPayload(boolean open) implements CustomPacketPayload {
        public static final Type<DapHoldWindowPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_window"));
        public static final StreamCodec<FriendlyByteBuf, DapHoldWindowPayload> CODEC = StreamCodec.ofMember(
                (p, buf) -> buf.writeBoolean(p.open()), buf -> new DapHoldWindowPayload(buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record DapHoldLoopPayload(boolean looping) implements CustomPacketPayload {
        public static final Type<DapHoldLoopPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_loop"));
        public static final StreamCodec<FriendlyByteBuf, DapHoldLoopPayload> CODEC = StreamCodec.ofMember(
                (p, buf) -> buf.writeBoolean(p.looping()), buf -> new DapHoldLoopPayload(buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record DapHoldEndPayload(boolean wasLooping) implements CustomPacketPayload {
        public static final Type<DapHoldEndPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_end"));
        public static final StreamCodec<FriendlyByteBuf, DapHoldEndPayload> CODEC = StreamCodec.ofMember(
                (p, buf) -> buf.writeBoolean(p.wasLooping()), buf -> new DapHoldEndPayload(buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record DapHoldFreezePayload(UUID playerId, boolean frozen) implements CustomPacketPayload {
        public static final Type<DapHoldFreezePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_freeze"));
        public static final StreamCodec<FriendlyByteBuf, DapHoldFreezePayload> CODEC = StreamCodec.ofMember(
                (p, buf) -> { buf.writeUUID(p.playerId()); buf.writeBoolean(p.frozen()); },
                buf -> new DapHoldFreezePayload(buf.readUUID(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record DapHoldJHoldPayload() implements CustomPacketPayload {
        public static final Type<DapHoldJHoldPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_jhold"));
        public static final StreamCodec<FriendlyByteBuf, DapHoldJHoldPayload> CODEC = StreamCodec.unit(new DapHoldJHoldPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record DapHoldJReleasePayload() implements CustomPacketPayload {
        public static final Type<DapHoldJReleasePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_jrelease"));
        public static final StreamCodec<FriendlyByteBuf, DapHoldJReleasePayload> CODEC = StreamCodec.unit(new DapHoldJReleasePayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record GroupJoinedPayload(UUID joinerId, UUID hfId, int memberCount) implements CustomPacketPayload {
        public static final Type<GroupJoinedPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_group_join"));
        public static final StreamCodec<FriendlyByteBuf, GroupJoinedPayload> CODEC = StreamCodec.ofMember(
                (p, buf) -> { buf.writeUUID(p.joinerId()); buf.writeUUID(p.hfId()); buf.writeInt(p.memberCount()); },
                buf -> new GroupJoinedPayload(buf.readUUID(), buf.readUUID(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record GroupResultPayload(boolean perfect, int memberCount) implements CustomPacketPayload {
        public static final Type<GroupResultPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_group_result"));
        public static final StreamCodec<FriendlyByteBuf, GroupResultPayload> CODEC = StreamCodec.ofMember(
                (p, buf) -> { buf.writeBoolean(p.perfect()); buf.writeInt(p.memberCount()); },
                buf -> new GroupResultPayload(buf.readBoolean(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record GroupJoinPayload() implements CustomPacketPayload {
        public static final Type<GroupJoinPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "daphold_group_join_req"));
        public static final StreamCodec<FriendlyByteBuf, GroupJoinPayload> CODEC = StreamCodec.unit(new GroupJoinPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    private static final Map<UUID, UUID> activePairs   = new HashMap<>();
    private static final Map<UUID, Long> pairStartTime = new HashMap<>();
    private static final Set<UUID> windowOpen          = new HashSet<>();
    private static final Set<UUID> impactFired         = new HashSet<>();
    private static final Set<UUID> looping             = new HashSet<>();
    private static final Set<UUID> endingAnimation     = new HashSet<>();
    private static final Map<UUID, Long> jHoldLastTick = new HashMap<>();
    private static final Map<UUID, Long> loopStartTime = new HashMap<>();
    private static final Map<UUID, ArmorStand> handStands = new HashMap<>();
    private static final Set<UUID> tpComplete          = new HashSet<>();
    private static final Map<UUID, Set<UUID>> groupJoiners  = new HashMap<>();
    private static final Map<UUID, UUID>      joinerGroup   = new HashMap<>();
    private static final Map<UUID, Long>      joinerJLast   = new HashMap<>();
    private static final Map<UUID, Long>      releaseFirst  = new HashMap<>();
    private static final Map<UUID, Set<UUID>> releasedSet   = new HashMap<>();
    private static final double GROUP_JOIN_RADIUS  = 2.5;
    private static final long   RELEASE_WINDOW_MS  = 500L;
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DapHoldStartPayload.ID,    DapHoldStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldWindowPayload.ID,   DapHoldWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldLoopPayload.ID,     DapHoldLoopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldEndPayload.ID,      DapHoldEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldFreezePayload.ID,   DapHoldFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupJoinedPayload.ID,     GroupJoinedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupResultPayload.ID,     GroupResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldJHoldPayload.ID,    DapHoldJHoldPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldJReleasePayload.ID, DapHoldJReleasePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GroupJoinPayload.ID,       GroupJoinPayload.CODEC);
    }
    public static void register() {
        registerPayloads();
        ServerPlayNetworking.registerGlobalReceiver(DapHoldJHoldPayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onJHold(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(DapHoldJReleasePayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onJRelease(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(GroupJoinPayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> {
                    ServerPlayer player = ctx.player();
                    UUID id = player.getUUID();
                    if (isInDapHold(id)) return;
                    tryJoinGroup(player, System.currentTimeMillis());
                }));
        ServerTickEvents.END_SERVER_TICK.register(DapHoldHandler::onServerTick);
    }
    private static void makeFaceEachOther(ServerPlayer p1, ServerPlayer p2) {
        Vec3 p1Pos = p1.position();
        Vec3 p2Pos = p2.position();
        p1.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        p2.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        System.out.println("[DapHold]  Left click swing - body rotation synced!");
        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawP2 = yawP1 + 180;
        p1.setYRot(yawP1);
        p1.setYBodyRot(yawP1);
        p1.setYHeadRot(yawP1);
        p1.teleportTo(p1.level(), p1Pos.x, p1Pos.y, p1Pos.z, java.util.Set.of(), yawP1, 0.0f, false);
        p2.setYRot(yawP2);
        p2.setYBodyRot(yawP2);
        p2.setYHeadRot(yawP2);
        p2.teleportTo(p2.level(), p2Pos.x, p2Pos.y, p2Pos.z, java.util.Set.of(), yawP2, 0.0f, false);
    }
    private static boolean arePlayersFacingEachOther(ServerPlayer p1, ServerPlayer p2) {
        net.minecraft.world.phys.Vec3 p1Pos = p1.position();
        net.minecraft.world.phys.Vec3 p2Pos = p2.position();
        net.minecraft.world.phys.Vec3 directionTo = p2Pos.subtract(p1Pos).normalize();
        net.minecraft.world.phys.Vec3 p1Looking = p1.getLookAngle();
        double dot1 = p1Looking.dot(directionTo);
        if (dot1 < 0.85) return false;
        net.minecraft.world.phys.Vec3 directionBack = p1Pos.subtract(p2Pos).normalize();
        net.minecraft.world.phys.Vec3 p2Looking = p2.getLookAngle();
        double dot2 = p2Looking.dot(directionBack);
        return dot2 >= 0.85;
    }
    public static void startDapHold(ServerPlayer hfPlayer, ServerPlayer dapPlayer) {
        UUID hfId = hfPlayer.getUUID();
        UUID dapId = dapPlayer.getUUID();
        if (isInDapHold(hfId) || isInDapHold(dapId)) return;
        if (!arePlayersFacingEachOther(hfPlayer, dapPlayer)) {
            hfPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNot facing each other!"), true);
            dapPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNot facing each other!"), true);
            System.out.println("[DapHold]  FAILED - Players not facing each other!");
            return;
        }
        System.out.println("[DapHold]  Facing check passed! START! HF=" + hfPlayer.getName().getString() + " DAP=" + dapPlayer.getName().getString());
        HighFiveHandler.handRaisedTime.remove(hfId);
        HighFiveHandler.startAnimTime.remove(hfId);
        HighFiveHandler.syncHandRaised(hfPlayer, false);
        System.out.println("[DapHold] Removed HF player from HighFiveHandler control");
        com.cooptest.DapSession session = com.cooptest.DapSessionManager.createSession(
                hfId, dapId,
                1.5,
                com.cooptest.DapSession.DapType.PERFECT_DAP
        );
        activePairs.put(hfId, dapId);
        pairStartTime.put(hfId, System.currentTimeMillis());
        sendFreeze(hfPlayer.level().getServer(), hfId,  true);
        sendFreeze(hfPlayer.level().getServer(), dapId, true);
        System.out.println("[DapHold] Sent freeze to both players");
        spawnHandStand(hfPlayer, dapPlayer);
        System.out.println("[DapHold] Sending DapHoldStartPayload:");
        System.out.println("  - HF player (" + hfPlayer.getName().getString() + "): role=0 (highfive_dap)");
        System.out.println("  - DAP player (" + dapPlayer.getName().getString() + "): role=1 (dap_high)");
        sendToAll(hfPlayer.level().getServer(), new DapHoldStartPayload(hfId,  dapId, 0));
        sendToAll(hfPlayer.level().getServer(), new DapHoldStartPayload(dapId, hfId,  1));
        PoseNetworking.broadcastAnimState(hfPlayer, 38);
        PoseNetworking.broadcastAnimState(dapPlayer, 39);
    }
    private static void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Set<UUID> toCleanup = new HashSet<>();
        for (Map.Entry<UUID, UUID> entry : activePairs.entrySet()) {
            UUID hfId  = entry.getKey();
            UUID dapId = entry.getValue();
            ServerPlayer hfPlayer  = server.getPlayerList().getPlayer(hfId);
            ServerPlayer dapPlayer = server.getPlayerList().getPlayer(dapId);
            if (hfPlayer == null || dapPlayer == null) { toCleanup.add(hfId); continue; }
            Long startMs = pairStartTime.get(hfId);
            if (startMs == null) { toCleanup.add(hfId); continue; }
            long elapsed = now - startMs;
            if (!tpComplete.contains(hfId)) {
                tpComplete.add(hfId);
            }
            updateHandStand(hfPlayer, dapPlayer, hfId);
            if (elapsed % 500 < 50) {
                hfPlayer.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                dapPlayer.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            if (!windowOpen.contains(hfId) && elapsed >= J_WINDOW_START_MS) {
                windowOpen.add(hfId);
                sendToAll(server, new DapHoldWindowPayload(true));
                hfPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§e⚡ HOLD J "), true);
                dapPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§e⚡ HOLD J "), true);
            }
            if (!impactFired.contains(hfId) && elapsed >= IMPACT_MS) {
                impactFired.add(hfId);
                spawnImpactParticles(hfPlayer, dapPlayer, hfId);
            }
            if (windowOpen.contains(hfId) && !looping.contains(hfId)
                    && !endingAnimation.contains(hfId) && elapsed >= J_WINDOW_END_MS) {
                if (isHoldingJ(hfId, now) && isHoldingJ(dapId, now)) {
                    looping.add(hfId);
                    loopStartTime.put(hfId, now);
                    com.cooptest.DapSessionManager.removeSession(hfId);
                    sendToAll(server, new DapHoldLoopPayload(true));
                    if (hfPlayer != null && dapPlayer != null) {
                        PoseNetworking.broadcastAnimState(hfPlayer, 40);
                        PoseNetworking.broadcastAnimState(dapPlayer, 40);
                    }
                    System.out.println("[DapHold] BOTH HELD J → DAPPING LOOP!");
                } else {
                    endingAnimation.add(hfId);
                    sendToAll(server, new DapHoldWindowPayload(false));
                    doUnfreeze(server, hfId, dapId);
                }
            }
            if (endingAnimation.contains(hfId) && elapsed >= ANIM_LENGTH_MS) {
                sendToAll(server, new DapHoldEndPayload(false));
                toCleanup.add(hfId);
            }
        }
        Set<UUID> groupResultNeeded = new HashSet<>();
        for (UUID hfId : looping) {
            ServerPlayer hfPlayer = server.getPlayerList().getPlayer(hfId);
            UUID dapId = activePairs.get(hfId);
            ServerPlayer dapPlayer = server.getPlayerList().getPlayer(dapId);
            if (hfPlayer == null || dapPlayer == null) continue;
            ServerLevel world = hfPlayer.level();
            ArmorStand stand = handStands.get(hfId);
            if (stand != null && !stand.isRemoved()) {
                Vec3 impactPos = stand.position();
                world.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        impactPos.x, impactPos.y, impactPos.z, 2, 0.1, 0.1, 0.1, 0.02);
            }
            Set<UUID> joiners = groupJoiners.get(hfId);
            if (joiners != null && !joiners.isEmpty()) {
                Long first = releaseFirst.get(hfId);
                if (first != null && now - first > RELEASE_WINDOW_MS) {
                    groupResultNeeded.add(hfId);
                    continue;
                }
                Set<UUID> toEvict = new HashSet<>();
                for (UUID jId : joiners) {
                    Long lastJ = joinerJLast.get(jId);
                    if (lastJ == null || now - lastJ > 300) toEvict.add(jId);
                }
                for (UUID jId : toEvict) {
                    joiners.remove(jId);
                    joinerGroup.remove(jId);
                    joinerJLast.remove(jId);
                    sendFreeze(server, jId, false);
                    ServerPlayer jp = server.getPlayerList().getPlayer(jId);
                    if (jp != null) {
                        PoseNetworking.broadcastAnimState(jp, 41);
                        jp.displayClientMessage(net.minecraft.network.chat.Component.literal("§7Left the group"), true);
                    }
                }
                if (server.getTickCount() % 4 == 0) {
                    faceGroupCenter(hfId, server);
                    ServerPlayer hfP2 = server.getPlayerList().getPlayer(hfId);
                    UUID dapId2 = activePairs.get(hfId);
                    ServerPlayer dapP2 = server.getPlayerList().getPlayer(dapId2);
                    if (hfP2 != null)  hfP2.setYHeadRot(hfP2.getVisualRotationYInDegrees());
                    if (dapP2 != null) dapP2.setYHeadRot(dapP2.getVisualRotationYInDegrees());
                    for (UUID jId : joiners) {
                        ServerPlayer jp = server.getPlayerList().getPlayer(jId);
                        if (jp != null) jp.setYHeadRot(jp.getVisualRotationYInDegrees());
                    }
                }
                Vec3 mid = getGroupMidpoint(hfId, server);
                int chargeParticles = joiners.size() + 1;
                world.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        mid.x, mid.y + 1.2, mid.z, chargeParticles, 0.3, 0.2, 0.3, 0.05);
            }
        }
        for (UUID hfId : groupResultNeeded) {
            if (looping.contains(hfId)) doGroupResult(hfId, server, false);
        }
        toCleanup.forEach(hfId -> cleanupPair(hfId, server));
    }
    private static void onJHold(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        UUID hfId = getPairHfId(id);
        if (hfId != null && windowOpen.contains(hfId)) {
            jHoldLastTick.put(id, now);
            return;
        }
        if (joinerGroup.containsKey(id)) {
            joinerJLast.put(id, now);
            return;
        }
        if (hfId == null) tryJoinGroup(player, now);
    }
    private static void onJRelease(ServerPlayer player) {
        UUID id = player.getUUID();
        jHoldLastTick.remove(id);
        joinerJLast.remove(id);
        UUID joinerHfId = joinerGroup.get(id);
        if (joinerHfId != null) {
            logGroupRelease(id, joinerHfId, player.level().getServer());
            return;
        }
        UUID hfId = getPairHfId(id);
        if (hfId == null || !looping.contains(hfId)) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        if (groupJoiners.containsKey(hfId) && !groupJoiners.get(hfId).isEmpty()) {
            logGroupRelease(id, hfId, server);
            return;
        }
        UUID dapId = activePairs.get(hfId);
        looping.remove(hfId);
        loopStartTime.remove(hfId);
        doUnfreeze(server, hfId, dapId);
        sendToAll(server, new DapHoldEndPayload(true));
        ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
        ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
        if (hfP  != null) PoseNetworking.broadcastAnimState(hfP,  41);
        if (dapP != null) PoseNetworking.broadcastAnimState(dapP, 41);
        pairStartTime.put(hfId, System.currentTimeMillis() + 100 - 1042L);
    }
    private static void tryJoinGroup(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        for (UUID hfId : looping) {
            UUID dapId = activePairs.get(hfId);
            if (hfId.equals(id) || (dapId != null && dapId.equals(id))) continue;
            Vec3 mid = getGroupMidpoint(hfId, player.level().getServer());
            if (player.position().distanceTo(mid) > GROUP_JOIN_RADIUS) continue;
            addGroupJoiner(player, hfId);
            return;
        }
    }
    private static void addGroupJoiner(ServerPlayer joiner, UUID hfId) {
        UUID id = joiner.getUUID();
        MinecraftServer server = joiner.level().getServer();
        groupJoiners.computeIfAbsent(hfId, k -> new HashSet<>()).add(id);
        joinerGroup.put(id, hfId);
        joinerJLast.put(id, System.currentTimeMillis());
        sendFreeze(server, id, true);
        PoseNetworking.broadcastAnimState(joiner, 38);
        int total = 2 + groupJoiners.get(hfId).size();
        GroupJoinedPayload pkt = new GroupJoinedPayload(id, hfId, total);
        sendToAll(server, pkt);
        faceGroupCenter(hfId, server);
        joiner.displayClientMessage(net.minecraft.network.chat.Component.literal("§a§l⚡ JOINED GROUP DAP! (" + total + " players)"), true);
        ServerPlayer hfP = server.getPlayerList().getPlayer(hfId);
        if (hfP != null) hfP.displayClientMessage(net.minecraft.network.chat.Component.literal("§e§l+" + joiner.getName().getString() + " joined! (" + total + " total)"), true);
    }
    private static void logGroupRelease(UUID id, UUID hfId, MinecraftServer server) {
        if (server == null) return;
        releasedSet.computeIfAbsent(hfId, k -> new HashSet<>()).add(id);
        if (!releaseFirst.containsKey(hfId)) releaseFirst.put(hfId, System.currentTimeMillis());
        checkGroupRelease(hfId, server);
    }
    private static void checkGroupRelease(UUID hfId, MinecraftServer server) {
        Set<UUID> joiners = groupJoiners.getOrDefault(hfId, emptySet());
        int total = 2 + joiners.size();
        int released = releasedSet.getOrDefault(hfId, emptySet()).size();
        long elapsed = System.currentTimeMillis() - releaseFirst.getOrDefault(hfId, Long.MAX_VALUE);
        if (released >= total) {
            doGroupResult(hfId, server, elapsed <= RELEASE_WINDOW_MS);
        }
    }
    private static void doGroupResult(UUID hfId, MinecraftServer server, boolean perfect) {
        UUID dapId = activePairs.get(hfId);
        Set<UUID> joiners = new HashSet<>(groupJoiners.getOrDefault(hfId, emptySet()));
        int memberCount = 2 + joiners.size();
        java.util.List<ServerPlayer> all = new java.util.ArrayList<>();
        ServerPlayer hfP  = server.getPlayerList().getPlayer(hfId);
        ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
        if (hfP  != null) all.add(hfP);
        if (dapP != null) all.add(dapP);
        for (UUID jId : joiners) {
            ServerPlayer jp = server.getPlayerList().getPlayer(jId);
            if (jp != null) all.add(jp);
        }
        Vec3 center = all.stream().map(ServerPlayer::position)
                .reduce(Vec3.ZERO, Vec3::add)
                .scale(1.0 / Math.max(1, all.size()));
        ServerLevel world = hfP != null ? hfP.level() : server.overworld();
        if (perfect) {
            for (ServerPlayer p : all) {
                PoseNetworking.broadcastAnimState(p, 68);
            }
            for (ServerPlayer p : all) sendFreeze(server, p.getUUID(), false);
            final java.util.List<ServerPlayer> allFinal = all;
            final Vec3 centerFinal = center;
            final ServerLevel worldFinal = world;
            final int mc = memberCount;
            new Thread(() -> {
                try { Thread.sleep(1670); } catch (InterruptedException ignored) {}
                server.execute(() -> {
                    for (ServerPlayer p : allFinal) {
                        if (!p.isAlive()) continue;
                        p.push(0, 0.4 + mc * 0.1, 0);
                        p.hurtMarked = true;
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.SPEED, 120, Math.min(2, mc - 1)));
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.JUMP_BOOST, 120, 0));
                        p.displayClientMessage(net.minecraft.network.chat.Component.literal("§6§l✨ PERFECT GROUP DAP! §e" + mc + " players!"), true);
                    }
                    for (int i = 0; i < mc * 3; i++) {
                        double ox = (worldFinal.random.nextDouble() - 0.5) * 3;
                        double oz = (worldFinal.random.nextDouble() - 0.5) * 3;
                        worldFinal.sendParticles(ParticleTypes.FIREWORK,
                                centerFinal.x + ox, centerFinal.y + 2 + i * 0.5, centerFinal.z + oz,
                                6, 0.3, 0.1, 0.3, 0.12);
                    }
                    worldFinal.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                            centerFinal.x, centerFinal.y + 1.5, centerFinal.z, mc * 5, 0.6, 0.6, 0.6, 0.3);
                    worldFinal.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                            centerFinal.x, centerFinal.y + 1, centerFinal.z, mc, 0.4, 0.3, 0.4, 0);
                    worldFinal.playSound(null, centerFinal.x, centerFinal.y, centerFinal.z,
                            ModSounds.EPIC_DAP, SoundSource.PLAYERS, 1.5f, 0.9f + mc * 0.05f);
                    worldFinal.playSound(null, centerFinal.x, centerFinal.y, centerFinal.z,
                            net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                            SoundSource.PLAYERS, 1.2f, 0.8f);
                });
            }).start();
        } else {
            for (ServerPlayer p : all) {
                Vec3 dir = p.position().subtract(center).normalize();
                if (dir.lengthSqr() < 0.01) dir = new Vec3(1, 0, 0);
                p.push(dir.x * 0.9, 0.3, dir.z * 0.9);
                p.hurtMarked = true;
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("§c❌ Release not synced!"), true);
            }
            world.sendParticles(ParticleTypes.POOF,
                    center.x, center.y + 1, center.z, 12, 0.4, 0.3, 0.4, 0.05);
            world.playSound(null, center.x, center.y, center.z,
                    net.minecraft.sounds.SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
        sendToAll(server, new GroupResultPayload(perfect, memberCount));
        if (!perfect) {
            sendToAll(server, new DapHoldEndPayload(false));
            if (hfP  != null) PoseNetworking.broadcastAnimState(hfP,  41);
            if (dapP != null) PoseNetworking.broadcastAnimState(dapP, 41);
            for (UUID jId : joiners) {
                ServerPlayer jp = server.getPlayerList().getPlayer(jId);
                if (jp != null) PoseNetworking.broadcastAnimState(jp, 41);
            }
        }
        for (UUID jId : joiners) {
            sendFreeze(server, jId, false);
            joinerGroup.remove(jId);
            joinerJLast.remove(jId);
        }
        groupJoiners.remove(hfId);
        releaseFirst.remove(hfId);
        releasedSet.remove(hfId);
        looping.remove(hfId);
        loopStartTime.remove(hfId);
        if (dapId != null) doUnfreeze(server, hfId, dapId);
        pairStartTime.put(hfId, System.currentTimeMillis() + 100 - 1042L);
    }
    private static Vec3 getGroupMidpoint(UUID hfId, MinecraftServer server) {
        java.util.List<Vec3> positions = new java.util.ArrayList<>();
        ServerPlayer hfP  = server.getPlayerList().getPlayer(hfId);
        UUID dapId = activePairs.get(hfId);
        ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
        if (hfP  != null) positions.add(hfP.position());
        if (dapP != null) positions.add(dapP.position());
        for (UUID jId : groupJoiners.getOrDefault(hfId, emptySet())) {
            ServerPlayer jp = server.getPlayerList().getPlayer(jId);
            if (jp != null) positions.add(jp.position());
        }
        if (positions.isEmpty()) return Vec3.ZERO;
        return positions.stream().reduce(Vec3.ZERO, Vec3::add)
                .scale(1.0 / positions.size());
    }
    private static void faceGroupCenter(UUID hfId, MinecraftServer server) {
        java.util.List<ServerPlayer> members = new java.util.ArrayList<>();
        ServerPlayer hfP  = server.getPlayerList().getPlayer(hfId);
        UUID dapId = activePairs.get(hfId);
        ServerPlayer dapP = server.getPlayerList().getPlayer(dapId);
        if (hfP  != null) members.add(hfP);
        if (dapP != null) members.add(dapP);
        for (UUID jId : groupJoiners.getOrDefault(hfId, emptySet())) {
            ServerPlayer jp = server.getPlayerList().getPlayer(jId);
            if (jp != null) members.add(jp);
        }
        if (members.size() < 2) return;
        Vec3 center = members.stream().map(ServerPlayer::position)
                .reduce(Vec3.ZERO, Vec3::add).scale(1.0 / members.size());
        for (ServerPlayer p : members) {
            Vec3 diff = center.subtract(p.position());
            if (diff.horizontalDistanceSqr() < 0.001) continue;
            float yaw = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x))) - 90f;
            p.setYRot(yaw); p.setYBodyRot(yaw); p.setYHeadRot(yaw);
        }
    }
    public static void forceUnfreeze(MinecraftServer server, UUID id) {
        sendFreeze(server, id, false);
    }
    public static UUID getPairHfId(UUID id) {
        if (activePairs.containsKey(id)) return id;
        for (Map.Entry<UUID, UUID> e : activePairs.entrySet())
            if (e.getValue().equals(id)) return e.getKey();
        return null;
    }
    private static UUID getPairHfIdPrivate(UUID id) { return getPairHfId(id); }
    private static boolean isHoldingJ(UUID id, long now) {
        Long last = jHoldLastTick.get(id);
        return last != null && (now - last) < 200;
    }
    private static void smoothTP(ServerPlayer hf, ServerPlayer dap, UUID hfId) {
        double dist = hf.position().distanceTo(dap.position());
        if (dist <= STOP_DISTANCE) {
            tpComplete.add(hfId);
            faceEachOther(hf, dap);
            return;
        }
        double move = Math.min(TP_SPEED, (dist - STOP_DISTANCE) / 2.0);
        Vec3 dir   = dap.position().subtract(hf.position()).normalize();
        Vec3 newHf  = hf.position().add(dir.scale(move));
        Vec3 newDap = dap.position().add(dir.reverse().scale(move));
        hf.teleportTo(hf.level(),   newHf.x,  newHf.y,  newHf.z, java.util.Set.of(), hf.getYRot(),  hf.getXRot(), false);
        dap.teleportTo(dap.level(), newDap.x, newDap.y, newDap.z, java.util.Set.of(), dap.getYRot(), dap.getXRot(), false);
    }
    private static void faceEachOther(ServerPlayer a, ServerPlayer b) {
        Vec3 diff = b.position().subtract(a.position());
        float yawA = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x))) - 90f;
        a.teleportTo(a.level(), a.getX(), a.getY(), a.getZ(), java.util.Set.of(),yawA, a.getXRot(), false);
        b.teleportTo(b.level(), b.getX(), b.getY(), b.getZ(), java.util.Set.of(), yawA + 180f, b.getXRot(), false);
    }
    private static void spawnHandStand(ServerPlayer hf, ServerPlayer dap) {
        ServerLevel world = hf.level();
        Vec3 mid = hf.position().add(0, 1.4, 0).add(dap.position().add(0, 1.4, 0)).scale(0.5);
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, world);
        stand.setPos(mid.x, mid.y, mid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        world.addFreshEntity(stand);
        handStands.put(hf.getUUID(), stand);
    }
    private static void updateHandStand(ServerPlayer hf, ServerPlayer dap, UUID hfId) {
        ArmorStand stand = handStands.get(hfId);
        if (stand == null || stand.isRemoved()) return;
        Vec3 mid = hf.position().add(0, 1.4, 0).add(dap.position().add(0, 1.4, 0)).scale(0.5);
        stand.setPos(mid.x, mid.y, mid.z);
    }
    private static void spawnImpactParticles(ServerPlayer hf, ServerPlayer dap, UUID hfId) {
        ServerLevel world = hf.level();
        ArmorStand stand = handStands.get(hfId);
        double x, y, z;
        if (stand != null && !stand.isRemoved()) {
            x = stand.getX(); y = stand.getY(); z = stand.getZ();
        } else {
            Vec3 mid = hf.position().add(dap.position()).scale(0.5).add(0, 1.4, 0);
            x = mid.x; y = mid.y; z = mid.z;
        }
        world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f),     x, y, z, 3,  0,   0,   0,   0);
        world.sendParticles(ParticleTypes.END_ROD,   x, y, z, 40, 0.4, 0.4, 0.4, 0.15);
        world.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 80, 0.6, 0.6, 0.6, 0.08);
        world.sendParticles(ParticleTypes.CLOUD,     x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
        world.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 5,  0.3, 0.3, 0.3, 0);
        double groundY = hf.getY() + 0.1;
        for (double angle = 0; angle < 360; angle += 8) {
            double rad = Math.toRadians(angle);
            for (double r = 0.5; r <= 3.0; r += 0.5) {
                world.sendParticles(ParticleTypes.END_ROD,
                        x + Math.cos(rad) * r, groundY, z + Math.sin(rad) * r,
                        2, 0.05, 0.05, 0.05, 0.02);
            }
        }
        world.playSound(null, x, y, z, ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }
    private static void sendFreeze(MinecraftServer server, UUID targetId, boolean freeze) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ServerPlayNetworking.send(p, new DapHoldFreezePayload(targetId, freeze));
    }
    private static void doUnfreeze(MinecraftServer server, UUID hfId, UUID dapId) {
        sendFreeze(server, hfId, false);
        sendFreeze(server, dapId, false);
        com.cooptest.DapSessionManager.removeSession(hfId);
        ArmorStand stand = handStands.remove(hfId);
        if (stand != null && !stand.isRemoved()) stand.discard();
    }
    private static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            ServerPlayNetworking.send(p, payload);
    }
    private static void cleanupPair(UUID hfId, MinecraftServer server) {
        UUID dapId = activePairs.remove(hfId);
        pairStartTime.remove(hfId); windowOpen.remove(hfId); impactFired.remove(hfId);
        looping.remove(hfId); endingAnimation.remove(hfId); tpComplete.remove(hfId);
        loopStartTime.remove(hfId);
        jHoldLastTick.remove(hfId);
        if (dapId != null) jHoldLastTick.remove(dapId);
        ArmorStand stand = handStands.remove(hfId);
        if (stand != null && !stand.isRemoved()) stand.discard();
        Set<UUID> joiners = groupJoiners.remove(hfId);
        if (joiners != null) {
            for (UUID jId : joiners) {
                joinerGroup.remove(jId);
                joinerJLast.remove(jId);
                sendFreeze(server, jId, false);
                ServerPlayer jp = server.getPlayerList().getPlayer(jId);
                if (jp != null) PoseNetworking.broadcastAnimState(jp, 41);
            }
        }
        releaseFirst.remove(hfId);
        releasedSet.remove(hfId);
        com.cooptest.DapSessionManager.removeSession(hfId);
        sendFreeze(server, hfId, false);
        if (dapId != null) sendFreeze(server, dapId, false);
        long now = System.currentTimeMillis();
        ChargedDapHandler.cooldowns.put(hfId, now + 1000);
        if (dapId != null) ChargedDapHandler.cooldowns.put(dapId, now + 1000);
        HighFiveHandler.highFiveCooldown.put(hfId, now);
        if (dapId != null) HighFiveHandler.highFiveCooldown.put(dapId, now);
        System.out.println("[DapHold] Cleaned up: " + hfId + " (1s cooldown applied)");
    }
    public static boolean tryDetect(ServerPlayer player, ServerPlayer partner) {
        boolean playerHF  = HighFiveHandler.hasHandRaised(player.getUUID());
        boolean partnerHF = HighFiveHandler.hasHandRaised(partner.getUUID());
        if (playerHF && !partnerHF)  { startDapHold(player,  partner); return true; }
        if (partnerHF && !playerHF)  { startDapHold(partner, player);  return true; }
        return false;
    }
    public static boolean isInDapHold(UUID playerId) {
        return activePairs.containsKey(playerId)
                || activePairs.containsValue(playerId)
                || joinerGroup.containsKey(playerId);
    }
}