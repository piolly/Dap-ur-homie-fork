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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public class HuddleHandler {
    private static final double HUDDLE_RANGE       = 2.0;
    private static final double HUDDLE_RADIUS      = 1.0;
    private static final long   HOLD_REQUIRED_MS   = 800;
    private static final long   F_RELEASE_GRACE_MS = 1200;
    private static final long   QTE_WINDOW_MS       = 1400;
    private static final long   QTE_ANIM_PRE_MS     = 400;
    private static final long   HUDDLE_MAX_MS       = 20_000;
    private static final long   COOLDOWN_MS         = 8_000;
    private static final int ANIM_HUDDLE_START = 70;
    private static final int ANIM_HUDDLE_IDLE  = 71;
    private static final int ANIM_HUDDLE_QTE1  = 72;
    private static final int ANIM_HUDDLE_END   = 73;
    private static final int ANIM_HUDDLE_QTE2  = 77;
    private static final int ANIM_HUDDLE_QTE3  = 78;
    private static final int ANIM_NONE         = 0;
    private static final long HUDDLE_START_MS   = 542;
    private static final java.util.Random RANDOM = new java.util.Random();
    private enum HuddleStage { ENTERING, IDLE, RELEASING, QTE, ENDING, DONE }
    private static class HuddleSession {
        final List<UUID> players;
        final UUID p1, p2;
        HuddleStage stage = HuddleStage.ENTERING;
        long stageStart   = System.currentTimeMillis();
        long huddleStart  = System.currentTimeMillis();
        Set<UUID> holdsF;
        Long firstRelease = null;
        int  qteStep        = 0;
        String qteBtn1      = "G";
        String qteBtn2      = "H";
        String qteBtn3      = "G";
        Set<UUID> qteHits   = new java.util.HashSet<>();
        boolean qteOpen        = false;
        boolean qteAnimStarted = false;
        long    qteAnimStart   = 0;
        HuddleSession(List<UUID> playerList) {
            this.players = new ArrayList<>(playerList);
            this.p1 = playerList.get(0);
            this.p2 = playerList.get(1);
            this.holdsF = new java.util.HashSet<>(playerList);
            qteBtn1 = RANDOM.nextBoolean() ? "G" : "H";
            qteBtn2 = qteBtn1.equals("G") ? "H" : "G";
            qteBtn3 = RANDOM.nextBoolean() ? "G" : "H";
        }
        void resetQTE() { qteHits.clear(); qteOpen = false; qteAnimStarted = false; }
        boolean allHit() { return qteHits.containsAll(players); }
        boolean allHoldF() { return holdsF.containsAll(players); }
        boolean anyReleasedF() { return !holdsF.containsAll(players); }
        long elapsed() { return System.currentTimeMillis() - stageStart; }
        long lastAuraTick = 0;
        double auraAngle  = 0.0;
        int stepsHit      = 0;
        String expectedButton() {
            return switch (qteStep) {
                case 1 -> qteBtn1;
                case 2 -> qteBtn2;
                case 3 -> qteBtn3;
                default -> "G";
            };
        }
    }
    private static final Map<String, HuddleSession> sessions      = new HashMap<>();
    private static final Map<UUID, String>          playerSession = new HashMap<>();
    private static final Map<UUID, Long>            fHoldStart    = new HashMap<>();
    private static final Map<String, Long>          cooldowns     = new HashMap<>();
    private static final Map<String, net.minecraft.world.entity.decoration.ArmorStand> centerStands = new HashMap<>();
    private static final Map<UUID, Long> joinerEnterMs = new HashMap<>();
    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
    public record HuddleFHoldPayload(boolean holding) implements CustomPacketPayload {
        public static final Type<HuddleFHoldPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "huddle_f_hold"));
        public static final StreamCodec<FriendlyByteBuf, HuddleFHoldPayload> CODEC =
                StreamCodec.ofMember((val, buf) -> buf.writeBoolean(val.holding()),
                        buf -> new HuddleFHoldPayload(buf.readBoolean()));
        @Override public Type<HuddleFHoldPayload> type() { return ID; }
    }
    public record HuddleEndPayload(UUID p1, UUID p2, boolean success) implements CustomPacketPayload {
        public static final Type<HuddleEndPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "huddle_end_result"));
        public static final StreamCodec<FriendlyByteBuf, HuddleEndPayload> CODEC =
                StreamCodec.ofMember(
                        (val, buf) -> { buf.writeUUID(val.p1()); buf.writeUUID(val.p2()); buf.writeBoolean(val.success()); },
                        buf -> new HuddleEndPayload(buf.readUUID(), buf.readUUID(), buf.readBoolean()));
        @Override public Type<HuddleEndPayload> type() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(HuddleFHoldPayload.ID, HuddleFHoldPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HuddleEndPayload.ID,   HuddleEndPayload.CODEC);
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HuddleFHoldPayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onFHold(ctx.player(), payload.holding())));
        ServerTickEvents.END_SERVER_TICK.register(HuddleHandler::tick);
    }
    private static void onFHold(ServerPlayer player, boolean holding) {
        UUID id = player.getUUID();
        String sk = playerSession.get(id);
        if (holding && sk == null && HighFiveHandler.isInBlockingState(id)) {
            return;
        }
        if (sk != null) {
            HuddleSession s = sessions.get(sk);
            if (s != null) {
                if (holding) s.holdsF.add(id);
                else s.holdsF.remove(id);
                if (s.stage == HuddleStage.IDLE && !holding && s.firstRelease == null)
                    s.firstRelease = System.currentTimeMillis();
                return;
            }
        }
        PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
        if (pose == PoseState.GRAB_READY || pose == PoseState.GRAB_HOLDING
                || pose == PoseState.GRABBED || pose == PoseState.PUSH_IDLE
                || pose == PoseState.PUSH_ACTION) return;
        if (holding) fHoldStart.putIfAbsent(id, System.currentTimeMillis());
        else          fHoldStart.remove(id);
    }
    public static boolean onButtonPress(ServerPlayer player, String button) {
        UUID id = player.getUUID();
        String sk = playerSession.get(id);
        if (sk == null) return false;
        HuddleSession s = sessions.get(sk);
        if (s == null || s.stage != HuddleStage.QTE || !s.qteOpen) {
            System.out.println("[HUDDLE] onButtonPress IGNORED btn=" + button
                    + " player=" + player.getName().getString()
                    + " inSession=" + (sk != null)
                    + " stage=" + (s != null ? s.stage : "null")
                    + " qteOpen=" + (s != null ? s.qteOpen : "null"));
            return false;
        }
        if ("FAIL".equals(button)) {
            failHuddle(s, player.level().getServer());
            return true;
        }
        if (!"G".equals(button) && !"H".equals(button)) return false;
        String expected = s.expectedButton();
        if (!button.equals(expected)) {
            failHuddle(s, player.level().getServer());
            return true;
        }
        if (id.equals(s.p1)) s.qteHits.add(s.p1);
        else if (s.players.contains(id)) s.qteHits.add(id);
        return true;
    }
    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (server.getTickCount() % 4 == 0) detectNewHuddles(server, now);
        joinerEnterMs.entrySet().removeIf(entry -> {
            if (now - entry.getValue() >= HUDDLE_START_MS) {
                ServerPlayer jp = server.getPlayerList().getPlayer(entry.getKey());
                if (jp != null) PoseNetworking.broadcastAnimState(jp, ANIM_HUDDLE_IDLE);
                return true;
            }
            return false;
        });
        Set<HuddleSession> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (HuddleSession s : new ArrayList<>(sessions.values())) {
            if (!seen.add(s)) continue;
            tickSession(s, server, now);
        }
        cooldowns.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_MS);
    }
    private static void detectNewHuddles(MinecraftServer server, long now) {
        List<UUID> holders = new ArrayList<>(fHoldStart.keySet());
        for (UUID joiner : holders) {
            if (playerSession.containsKey(joiner)) continue;
            Long startJ = fHoldStart.get(joiner);
            if (startJ == null || now - startJ < HOLD_REQUIRED_MS) continue;
            ServerPlayer pj = server.getPlayerList().getPlayer(joiner);
            if (pj == null) continue;
            for (HuddleSession s : new ArrayList<>(sessions.values())) {
                if (s.stage != HuddleStage.IDLE) continue;
                if (s.players.contains(joiner)) continue;
                net.minecraft.world.entity.decoration.ArmorStand stand =
                        centerStands.get(key(s.p1, s.p2));
                if (stand == null) continue;
                if (pj.distanceTo(stand) > HUDDLE_RANGE * 1.5) continue;
                s.players.add(joiner);
                s.holdsF.add(joiner);
                playerSession.put(joiner, key(s.p1, s.p2));
                fHoldStart.remove(joiner);
                if (stand != null) {
                    Vec3 center = stand.position();
                    int n = s.players.size();
                    ServerPlayer p1ref = server.getPlayerList().getPlayer(s.p1);
                    double baseAngle = 0;
                    if (p1ref != null) {
                        Vec3 toP1 = p1ref.position().subtract(center);
                        if (toP1.horizontalDistanceSqr() > 0.001) baseAngle = Math.atan2(toP1.z, toP1.x);
                    }
                    int ci = n - 1;
                    double angle = baseAngle + (Math.PI * 2 * ci / n);
                    double targetX = center.x + HUDDLE_RADIUS * Math.cos(angle);
                    double targetZ = center.z + HUDDLE_RADIUS * Math.sin(angle);
                    float targetYaw = (float)(-Math.toDegrees(Math.atan2(center.x - targetX, center.z - targetZ)));
                    Vec3 startPos = pj.position();
                    final float fYaw = targetYaw;
                    for (int step = 1; step <= 4; step++) {
                        final double frac = step / 4.0;
                        final double stepX = startPos.x + (targetX - startPos.x) * frac;
                        final double stepZ = startPos.z + (targetZ - startPos.z) * frac;
                        final int delay = step * 50;
                        new java.util.Timer().schedule(new java.util.TimerTask() {
                            @Override public void run() {
                                server.execute(() -> {
                                    if (!pj.isAlive()) return;
                                    pj.teleportTo(pj.level(), stepX, pj.getY(), stepZ,
                                            java.util.Set.of(), fYaw, 0, false);
                                    if (frac >= 1.0) {
                                        pj.setYRot(fYaw); pj.setYBodyRot(fYaw); pj.setYHeadRot(fYaw);
                                    }
                                });
                            }
                        }, delay);
                    }
                } else {
                    repositionCircle(s, server);
                }
                PoseNetworking.broadcastAnimState(pj, ANIM_HUDDLE_START);
                joinerEnterMs.put(joiner, now);
                for (UUID uid : s.players) {
                    ServerPlayer pp = server.getPlayerList().getPlayer(uid);
                    if (pp != null) pp.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                }
                final List<UUID> allNow = new java.util.ArrayList<>(s.players);
                final ServerPlayer fpjRef = pj;
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override public void run() {
                        server.execute(() -> {
                            for (UUID uid : allNow) {
                                ServerPlayer pp = server.getPlayerList().getPlayer(uid);
                                if (pp != null) pp.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                            }
                        });
                    }
                }, 120L);
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override public void run() {
                        server.execute(() -> {
                            for (UUID uid : allNow) {
                                ServerPlayer pp = server.getPlayerList().getPlayer(uid);
                                if (pp != null) pp.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                            }
                        });
                    }
                }, 260L);
                final ServerPlayer fpj = pj;
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override public void run() {
                        server.execute(() -> {
                            if (!fpj.isAlive()) return;
                            Vec3 arm = fpj.position().add(0, 1.4, 0)
                                    .add(fpj.getViewVector(1.0f).scale(0.4));
                            fpj.level().sendParticles(ParticleTypes.CRIT,
                                    arm.x, arm.y, arm.z, 6, 0.08, 0.08, 0.08, 0.06);
                            fpj.level().sendParticles(ParticleTypes.ENCHANTED_HIT,
                                    arm.x, arm.y, arm.z, 4, 0.06, 0.06, 0.06, 0.04);
                            fpj.level().playSound(null, arm.x, arm.y, arm.z,
                                    SoundEvents.NOTE_BLOCK_BELL.value(),
                                    SoundSource.PLAYERS, 0.8f, 2.0f);
                        });
                    }
                }, 420L);
                Vec3 sPos = stand.position();
                pj.level().playSound(null, sPos.x, sPos.y, sPos.z,
                        SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.7f);
                pj.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§aYou joined the huddle!"));
                for (UUID pid : s.players) {
                    if (pid.equals(joiner)) continue;
                    ServerPlayer pp = server.getPlayerList().getPlayer(pid);
                    if (pp != null) com.cooptest.MsgUtil.show(pp, 
                            net.minecraft.network.chat.Component.literal("§a" + pj.getName().getString() + " joined the huddle!"), true);
                }
                break;
            }
        }
        for (int i = 0; i < holders.size(); i++) {
            UUID a = holders.get(i);
            if (playerSession.containsKey(a)) continue;
            Long startA = fHoldStart.get(a);
            if (startA == null || now - startA < HOLD_REQUIRED_MS) continue;
            ServerPlayer pa = server.getPlayerList().getPlayer(a);
            if (pa == null) continue;
            for (int j = i + 1; j < holders.size(); j++) {
                UUID b = holders.get(j);
                if (playerSession.containsKey(b)) continue;
                Long startB = fHoldStart.get(b);
                if (startB == null || now - startB < HOLD_REQUIRED_MS) continue;
                ServerPlayer pb = server.getPlayerList().getPlayer(b);
                if (pb == null || pa.distanceTo(pb) > HUDDLE_RANGE) continue;
                String k = key(a, b);
                if (cooldowns.containsKey(k)) continue;
                List<UUID> initPlayers = new ArrayList<>(java.util.List.of(a, b));
                HuddleSession s = new HuddleSession(initPlayers);
                sessions.put(k, s);
                playerSession.put(a, k);
                playerSession.put(b, k);
                fHoldStart.remove(a);
                fHoldStart.remove(b);
                positionAndSpawnStand(s, pa, pb, server);
                break;
            }
        }
    }
    private static void positionAndSpawnStand(HuddleSession s,
                                              ServerPlayer pa, ServerPlayer pb,
                                              MinecraftServer server) {
        Vec3 mid = pa.position().add(pb.position()).scale(0.5);
        net.minecraft.server.level.ServerLevel world = pa.level();
        net.minecraft.world.entity.decoration.ArmorStand stand =
                new net.minecraft.world.entity.decoration.ArmorStand(world, mid.x, mid.y, mid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setPermanentlyInvulnerable(true);
        stand.setSilent(true);
        world.addFreshEntity(stand);
        centerStands.put(key(s.p1, s.p2), stand);
        repositionCircle(s, server);
        for (UUID uid : s.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(uid);
            if (p != null) {
                PoseNetworking.broadcastAnimState(p, ANIM_HUDDLE_START);
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            }
        }
        final List<UUID> foundersSnap = new java.util.ArrayList<>(s.players);
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                pa.level().getServer().execute(() -> {
                    for (UUID uid : foundersSnap) {
                        ServerPlayer p = pa.level().getServer().getPlayerList().getPlayer(uid);
                        if (p != null) p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                    }
                });
            }
        }, 120L);
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                pa.level().getServer().execute(() -> {
                    for (UUID uid : foundersSnap) {
                        ServerPlayer p = pa.level().getServer().getPlayerList().getPlayer(uid);
                        if (p != null) p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                    }
                });
            }
        }, 260L);
        world.playSound(null, mid.x, mid.y, mid.z,
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.5f);
        world.sendParticles(ParticleTypes.ENCHANTED_HIT,
                mid.x, mid.y + 1, mid.z, 12, 0.4, 0.4, 0.4, 0.06);
    }
    private static void repositionCircle(HuddleSession s, MinecraftServer server) {
        net.minecraft.world.entity.decoration.ArmorStand stand = centerStands.get(key(s.p1, s.p2));
        if (stand == null) return;
        Vec3 center = stand.position();
        int n = s.players.size();
        ServerPlayer p1p = server.getPlayerList().getPlayer(s.p1);
        double baseAngle = 0;
        if (p1p != null) {
            Vec3 toP1 = p1p.position().subtract(center);
            if (toP1.horizontalDistanceSqr() > 0.001) {
                baseAngle = Math.atan2(toP1.z, toP1.x);
            }
        }
        for (int ci = 0; ci < n; ci++) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.players.get(ci));
            if (p == null) continue;
            double angle = baseAngle + (Math.PI * 2 * ci / n);
            double px = center.x + HUDDLE_RADIUS * Math.cos(angle);
            double pz = center.z + HUDDLE_RADIUS * Math.sin(angle);
            float yaw = (float)(-Math.toDegrees(Math.atan2(center.x - px, center.z - pz)));
            p.teleportTo(p.level(), px, p.getY(), pz, java.util.Set.of(), yaw, 0, false);
            p.setYRot(yaw); p.setYBodyRot(yaw); p.setYHeadRot(yaw);
        }
    }
    private static void tickSession(HuddleSession s, MinecraftServer server, long now) {
        ServerPlayer p1 = server.getPlayerList().getPlayer(s.p1);
        ServerPlayer p2 = server.getPlayerList().getPlayer(s.p2);
        if (p1 == null || p2 == null) { failHuddle(s, server); return; }
        List<ServerPlayer> live = new ArrayList<>();
        for (UUID uid : s.players) {
            ServerPlayer lp = server.getPlayerList().getPlayer(uid);
            if (lp == null) { failHuddle(s, server); return; }
            live.add(lp);
        }
        if (now - s.huddleStart > HUDDLE_MAX_MS) { failHuddle(s, server); return; }
        switch (s.stage) {
            case ENTERING -> {
                if (s.elapsed() >= HUDDLE_START_MS) {
                    s.stage = HuddleStage.IDLE;
                    s.stageStart = now;
                    PoseNetworking.broadcastAnimState(p1, ANIM_HUDDLE_IDLE);
                    PoseNetworking.broadcastAnimState(p2, ANIM_HUDDLE_IDLE);
                }
            }
            case IDLE -> {
                if (now - s.lastAuraTick >= 80) {
                    s.lastAuraTick = now;
                    s.auraAngle += 0.35;
                    Vec3 center = p1.position().add(p2.position()).scale(0.5);
                    double r = 1.4;
                    for (int i = 0; i < 4; i++) {
                        double a = s.auraAngle + (Math.PI / 2 * i);
                        double px = center.x + r * Math.cos(a);
                        double pz = center.z + r * Math.sin(a);
                        p1.level().sendParticles(ParticleTypes.END_ROD,
                                px, center.y + 0.05, pz, 1, 0, 0, 0, 0.01);
                    }
                    double r2 = 0.7;
                    for (int i = 0; i < 3; i++) {
                        double a = -s.auraAngle * 1.5 + (Math.PI * 2 / 3 * i);
                        double px = center.x + r2 * Math.cos(a);
                        double pz = center.z + r2 * Math.sin(a);
                        p1.level().sendParticles(ParticleTypes.ENCHANTED_HIT,
                                px, center.y + 0.3, pz, 1, 0, 0, 0, 0.005);
                    }
                }
                if (s.anyReleasedF()) {
                    if (s.firstRelease == null) {
                        s.firstRelease = now;
                        Vec3 alertMid = p1.position().add(p2.position()).scale(0.5).add(0, 1, 0);
                        p1.level().playSound(null, alertMid.x, alertMid.y, alertMid.z,
                                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.2f, 1.8f);
                        p1.level().sendParticles(ParticleTypes.NOTE,
                                alertMid.x, alertMid.y + 0.5, alertMid.z, 5, 0.3, 0.2, 0.3, 0.1);
                    }
                    boolean bothReleased = s.holdsF.isEmpty();
                    boolean graceExpired = (now - s.firstRelease) > F_RELEASE_GRACE_MS;
                    if (bothReleased) {
                        Vec3 mid = p1.position().add(p2.position()).scale(0.5).add(0, 1, 0);
                        p1.level().playSound(null, mid.x, mid.y, mid.z,
                                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.3f);
                        p1.level().playSound(null, mid.x, mid.y, mid.z,
                                ModSounds.DAP_HIT, SoundSource.PLAYERS, 0.9f, 1.4f);
                        p1.level().sendParticles(ParticleTypes.CRIT,
                                mid.x, mid.y, mid.z, 12, 0.25, 0.25, 0.25, 0.1);
                        p1.level().sendParticles(ParticleTypes.ENCHANTED_HIT,
                                mid.x, mid.y, mid.z, 6, 0.2, 0.2, 0.2, 0.06);
                        startQTEStep(s, server, p1, p2, now);
                    } else if (graceExpired) {
                        failHuddle(s, server);
                    }
                } else {
                    s.firstRelease = null;
                }
            }
            case QTE -> {
                if (!s.qteAnimStarted) return;
                long animElapsed = now - s.qteAnimStart;
                if (!s.qteOpen && animElapsed >= QTE_ANIM_PRE_MS) {
                    s.qteOpen = true;
                    s.stageStart = now;
                    String btn = s.expectedButton();
                    for (ServerPlayer lp : live) {
                        ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(
                                lp.getUUID(), btn, s.qteStep, 0L, QTE_WINDOW_MS, true, 0));
                    }
                    Vec3 mid = p1.position().add(p2.position()).scale(0.5);
                    p1.level().playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS,
                            0.9f, 1.4f + s.qteStep * 0.1f);
                }
                if (!s.qteOpen) return;
                if (s.allHit()) {
                    s.stepsHit++;
                    for (ServerPlayer lp : live) {
                        lp.addEffect(new MobEffectInstance(
                                MobEffects.SPEED, 400, s.stepsHit, false, true));
                        lp.addEffect(new MobEffectInstance(
                                MobEffects.STRENGTH, 400, s.stepsHit - 1, false, true));
                    }
                    Vec3 flashMid = p1.position().add(p2.position()).scale(0.5).add(0, 1, 0);
                    int flashCount = 4 + s.stepsHit * 4;
                    p1.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                            flashMid.x, flashMid.y, flashMid.z, flashCount, 0.4, 0.4, 0.4, 0.2);
                    p1.level().playSound(null, flashMid.x, flashMid.y, flashMid.z,
                            SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS,
                            1.0f, 1.4f + s.qteStep * 0.15f);
                    if (s.qteStep == 3) {
                        s.qteStep = 0;
                        s.resetQTE();
                        for (ServerPlayer lp : live) {
                            PoseNetworking.broadcastAnimState(lp, ANIM_HUDDLE_END);
                            ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(
                                    lp.getUUID(), "G", 0, 0L, 0L, false, 0));
                        }
                        successHuddle(s, server, live, now);
                    } else {
                        s.qteStep++;
                        s.resetQTE();
                        s.stageStart = now;
                        s.qteAnimStarted = true;
                        s.qteAnimStart   = now;
                        int nextAnim = switch (s.qteStep) {
                            case 2 -> ANIM_HUDDLE_QTE2;
                            case 3 -> ANIM_HUDDLE_QTE3;
                            default -> ANIM_HUDDLE_QTE1;
                        };
                        for (ServerPlayer lp : live) PoseNetworking.broadcastAnimState(lp, nextAnim);
                    }
                    return;
                }
                if (s.elapsed() > QTE_WINDOW_MS + QTE_ANIM_PRE_MS) {
                    failHuddle(s, server);
                }
            }
            case ENDING -> {
                if (s.elapsed() >= 1375) {
                    for (ServerPlayer lp : live) {
                        ServerPlayNetworking.send(lp, new ChargedDapHandler.PerfectDapFreezePayload(false));
                    }
                    cleanupSession(s, server);
                }
            }
            case DONE -> cleanupSession(s, server);
        }
    }
    private static void startQTEStep(HuddleSession s, MinecraftServer server,
                                     ServerPlayer p1, ServerPlayer p2, long now) {
        s.qteStep = 1;
        s.stage = HuddleStage.QTE;
        s.stageStart = now;
        s.resetQTE();
        s.qteAnimStarted = true;
        s.qteAnimStart   = now;
        for (UUID uid : s.players) {
            ServerPlayer lp = server.getPlayerList().getPlayer(uid);
            if (lp == null) continue;
            PoseNetworking.broadcastAnimState(lp, ANIM_HUDDLE_QTE1);
        }
    }
    private static void successHuddle(HuddleSession s, MinecraftServer server,
                                      List<ServerPlayer> live, long now) {
        s.stage = HuddleStage.ENDING;
        s.stageStart = now;
        cooldowns.put(key(s.p1, s.p2), now);
        for (ServerPlayer lp : live) {
            ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(
                    lp.getUUID(), "G", 0, 0L, 0L, false, 0));
        }
        for (ServerPlayer lp : live) {
            lp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
            lp.addEffect(new MobEffectInstance(MobEffects.SPEED,        400, 2));
            lp.addEffect(new MobEffectInstance(MobEffects.STRENGTH,     400, 1));
            lp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,   200, 0));
            lp.experienceLevel += 2;
            com.cooptest.MsgUtil.show(lp, net.minecraft.network.chat.Component.literal(
                    "§d§l✦ HUDDLE! ✦ §7+Regen III, Speed III, Strength II, Resistance I"), true);
        }
        ServerPlayer p1 = live.get(0), p2 = live.get(1);
        Vec3 center = p1.position().add(p2.position()).scale(0.5);
        ServerLevel flashWorld = p1.level();
        flashWorld.playSound(null, center.x, center.y, center.z,
                SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 2.0f, 0.6f);
        flashWorld.playSound(null, center.x, center.y, center.z,
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 2.0f, 0.8f);
        flashWorld.playSound(null, center.x, center.y, center.z,
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);
        final List<ServerPlayer> liveFinal = new ArrayList<>(live);
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                server.execute(() -> {
                    if (liveFinal.stream().noneMatch(net.minecraft.world.entity.LivingEntity::isAlive)) return;
                    Vec3 sum = Vec3.ZERO;
                    for (ServerPlayer lp : liveFinal) sum = sum.add(lp.position());
                    Vec3 mid = sum.scale(1.0 / liveFinal.size()).add(0, 1, 0);
                    ServerLevel world = liveFinal.get(0).level();
                    world.sendParticles(ParticleTypes.HAPPY_VILLAGER,   mid.x, mid.y, mid.z, 80, 0.8, 0.8, 0.8, 0.5);
                    world.sendParticles(ParticleTypes.COMPOSTER,        mid.x, mid.y, mid.z, 60, 0.6, 0.6, 0.6, 0.4);
                    world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, 40, 0.6, 0.6, 0.6, 0.3);
                    world.sendParticles((ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)),            mid.x, mid.y, mid.z,  3, 0.1, 0.1, 0.1,   0);
                    world.sendParticles(ParticleTypes.FIREWORK,         mid.x, mid.y, mid.z, 40, 0.5, 0.6, 0.5, 0.25);
                    world.sendParticles(ParticleTypes.CRIT,             mid.x, mid.y - 0.5, mid.z, 20, 1.0, 0, 1.0, 0.05);
                    world.sendParticles(ParticleTypes.HEART,            mid.x, mid.y, mid.z, 15, 0.6, 0.4, 0.6, 0.1);
                    for (double a = 0; a < Math.PI * 2; a += 0.4) {
                        world.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                mid.x + Math.cos(a) * 1.5, mid.y - 0.9, mid.z + Math.sin(a) * 1.5,
                                2, 0, 0.1, 0, 0.05);
                    }
                    world.playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 2.0f, 1.2f);
                    world.playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.2f);
                    world.playSound(null, mid.x, mid.y, mid.z,
                            SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.5f, 2.0f);
                    net.minecraft.world.item.ItemStack fw = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
                    for (int ri = 0; ri < 5; ri++) {
                        double ox = (RANDOM.nextDouble() - 0.5) * 1.2;
                        double oz = (RANDOM.nextDouble() - 0.5) * 1.2;
                        world.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(
                                world, mid.x + ox, mid.y + 0.5, mid.z + oz, fw));
                    }
                });
            }
        }, 500L);
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                server.execute(() -> {
                    if (liveFinal.isEmpty() || !liveFinal.get(0).isAlive()) return;
                    Vec3 sum2 = Vec3.ZERO;
                    for (ServerPlayer lp : liveFinal) sum2 = sum2.add(lp.position());
                    Vec3 mid = sum2.scale(1.0 / liveFinal.size()).add(0, 1, 0);
                    ServerLevel world = liveFinal.get(0).level();
                    for (int pulse = 0; pulse < 4; pulse++) {
                        final int pp = pulse;
                        new java.util.Timer().schedule(new java.util.TimerTask() {
                            @Override public void run() {
                                server.execute(() -> {
                                    float fade = 1f - pp * 0.25f;
                                    int cnt = (int)(20 * fade);
                                    world.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                            mid.x, mid.y, mid.z, cnt, 0.9 * fade, 0.7 * fade, 0.9 * fade, 0.15);
                                    world.sendParticles(ParticleTypes.COMPOSTER,
                                            mid.x, mid.y, mid.z, cnt / 2, 0.7 * fade, 0.6 * fade, 0.7 * fade, 0.1);
                                });
                            }
                        }, (long)(pp * 250));
                    }
                });
            }
        }, 1875L);
        for (ServerPlayer lp : live) {
            ServerPlayNetworking.send(lp, new HuddleEndPayload(s.p1, s.p2, true));
        }
    }
    private static void failHuddle(HuddleSession s, MinecraftServer server) {
        cooldowns.put(key(s.p1, s.p2), System.currentTimeMillis());
        if (server == null) { cleanupSession(s, server); return; }
        List<ServerPlayer> live = new ArrayList<>();
        for (UUID uid : s.players) {
            ServerPlayer lp = server.getPlayerList().getPlayer(uid);
            if (lp != null) live.add(lp);
        }
        for (ServerPlayer lp : live) {
            ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(
                    lp.getUUID(), "G", 0, 0L, 0L, false, 0));
            PoseNetworking.broadcastAnimState(lp, ANIM_NONE);
            lp.removeEffect(MobEffects.SPEED);
            lp.removeEffect(MobEffects.STRENGTH);
            lp.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c✗ Huddle failed!"));
            ServerPlayNetworking.send(lp, new HuddleEndPayload(s.p1, s.p2, false));
        }
        if (live.size() >= 2) {
            net.minecraft.world.entity.decoration.ArmorStand stand = centerStands.get(key(s.p1, s.p2));
            Vec3 center = stand != null ? stand.position()
                    : live.get(0).position().add(live.get(1).position()).scale(0.5);
            for (ServerPlayer lp : live) {
                Vec3 dir = lp.position().subtract(center).normalize();
                lp.push(dir.x * 0.6, 0.4, dir.z * 0.6);
                lp.syncVelocity = true;
            }
            live.get(0).level().sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    center.x, center.y + 1, center.z, 8, 0.3, 0.3, 0.3, 0.05);
            live.get(0).level().playSound(null, center.x, center.y, center.z,
                    SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 0.7f, 1.5f);
        }
        cleanupSession(s, server);
    }
    private static void cleanupSession(HuddleSession s, MinecraftServer server) {
        String k = key(s.p1, s.p2);
        sessions.remove(k);
        for (UUID uid : s.players) {
            playerSession.remove(uid);
            joinerEnterMs.remove(uid);
            if (server != null) {
                ServerPlayer lp = server.getPlayerList().getPlayer(uid);
                if (lp != null) ServerPlayNetworking.send(lp, new ChargedDapHandler.PerfectDapFreezePayload(false));
            }
        }
        net.minecraft.world.entity.decoration.ArmorStand stand = centerStands.remove(k);
        if (stand != null && !stand.isRemoved()) stand.discard();
    }
    public static boolean isInHuddle(UUID id) { return playerSession.containsKey(id); }
    public static void cleanup(UUID id) {
        fHoldStart.remove(id);
        String k = playerSession.remove(id);
        if (k != null) {
            HuddleSession s = sessions.remove(k);
            if (s != null) playerSession.remove(s.p1.equals(id) ? s.p2 : s.p1);
        }
    }
}