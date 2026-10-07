package com.cooptest;

import com.cooptest.HeavenDapPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class ChargedDapHandler {


    public static final float DAP_RANGE = 1.6f;

    public static final long CHARGE_TIME_MS     = 250;
    public static final long RELEASE_WINDOW_MS  = 500;
    public static final long PERFECT_WINDOW_MS  = 85;
    public static final long GOOD_SYNC_MS       = 300;
    public static final long COOLDOWN_MS        = 1500;
    public static final long WHIFF_COOLDOWN_MS  = 800;
    public static final long FIRE_DELAY_MS      = 2000;
    public static final long FIRE_BUILD_TIME_MS = 2000;


    public static long chargeTimeMs()    { return CoopMovesConfig.get().dapChargeWindowMs; }
    public static long releaseWindowMs() { return CoopMovesConfig.get().dapReleaseWindowMs; }
    public static long perfectWindowMs() { return CoopMovesConfig.get().dapPerfectWindowMs; }
    public static long cooldownMs()      { return CoopMovesConfig.get().dapCooldownMs; }
    public static long whiffCooldownMs() { return CoopMovesConfig.get().dapWhiffCooldownMs; }
    public static long fireDelayMs()     { return CoopMovesConfig.get().dapFireDelayMs; }
    public static long fireBuildTimeMs() { return CoopMovesConfig.get().dapFireBuildTimeMs; }
    public static final double MIN_MOVEMENT_SPEED = 1.5;
    public static final long FIRE_GRACE_PERIOD_MS = 500;


    public static final double SPEED_BONUS_THRESHOLD = 8.0;
    public static final double SPEED_TIER_4_THRESHOLD = 25.0;
    public static final double PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED = 10.0;

    public static final int SPEED_HISTORY_TICKS = 40;



    public static final Map<UUID, Long> chargeStartTime = new HashMap<>();
    public static final Map<UUID, Long> releaseTime = new HashMap<>();
    public static final Map<UUID, UUID> waitingForPartner = new HashMap<>();
    public static final Map<UUID, Long> cooldowns = new HashMap<>();


    public static final Map<UUID, Long> fireStartTime = new HashMap<>();
    public static final Map<UUID, Long> fireGraceTime = new HashMap<>();
    public static final Map<UUID, Float> fireLevel = new HashMap<>();



    public static final Map<UUID, Long> fireMaxedStartTime = new HashMap<>();
    public static final Set<UUID> heavenReady = new HashSet<>();
    public static final long HEAVEN_READY_TIME_MS = 5000;


    private static class HeavenParticleSpawner {
        final ServerLevel world;
        final Vec3 pos;
        final long startTime;
        final long endTime;

        HeavenParticleSpawner(ServerLevel world, Vec3 pos, long durationMs) {
            this.world = world;
            this.pos = pos;
            this.startTime = System.currentTimeMillis();
            this.endTime = this.startTime + durationMs;
        }
    }
    private static final List<HeavenParticleSpawner> activeHeavenParticles = new ArrayList<>();


    public static final Map<UUID, LinkedList<Double>> speedHistory = new HashMap<>();


    public static final Map<UUID, Integer> impactFreezeTicks = new HashMap<>();


    private static final Map<UUID, Long> perfectDapStartTime = new HashMap<>();
    private static final Map<UUID, UUID> perfectDapPartner = new HashMap<>();
    private static final Map<UUID, Long> perfectDapFreezeEnd = new HashMap<>();
    private static final Map<UUID, Boolean> perfectDapImpactSent = new HashMap<>();

    private static final Map<UUID, Boolean> perfectDapExtendHit = new HashMap<>();
    private static final Map<UUID, Long> comboCooldown = new HashMap<>();





    private static final Map<UUID, Boolean> fireComboActive = new HashMap<>();


    private static final Map<UUID, Long> underwaterRemovalStart = new HashMap<>();
    private static final Map<UUID, Vec3> underwaterRemovalPos = new HashMap<>();
    private static final Map<UUID, ServerLevel> underwaterRemovalWorld = new HashMap<>();



    private static final long FIRE_DAP_HIT_LENGTH = 2292;
    private static final long FIRE_IMPACT_TIME = 210;
    private static final long FIRE_J_WINDOW_START = 830;
    private static final long FIRE_J_WINDOW_END = 2200;
    private static final long FIRE_WINDOW_START = 710;
    private static final long FIRE_WINDOW_END = 1420;

    private static final long FUSION_G_WINDOW_START_MS = DapFusionHandler.FUSION_G_WINDOW_START;


    private static final long FIRE_COMBO_FREEZE_MS = 4000;
    private static final long FIRE_COMBO_ARM_IMPACT = 1330;
    private static final long FIRE_COMBO_TORNADO = 1460;


    private static final Map<UUID, Long> fireDapStartTime = new HashMap<>();
    private static final Map<UUID, UUID> fireDapPartner = new HashMap<>();
    private static final Map<UUID, Boolean> inFireDapHit = new HashMap<>();
    private static final Map<UUID, Boolean> fireCircleSpawned = new HashMap<>();


    private static final Map<UUID, Long> fireDapComboRequestTime = new HashMap<>();
    private static final Map<UUID, Long> fireDapComboFreezeEnd = new HashMap<>();


    private static final Map<UUID, HeavenDapData> heavenPlayers = new HashMap<>();

    static class HeavenDapData {
        Vec3 originalMidpoint;
        ServerLevel world;
        long startTime;
        UUID partnerId;

        HeavenDapData(Vec3 originalMidpoint, ServerLevel world, long startTime, UUID partnerId) {
            this.originalMidpoint = originalMidpoint;
            this.world = world;
            this.startTime = startTime;
            this.partnerId = partnerId;
        }
    }


    private static class TornadoSwirlEntity {
        final Vec3 startCenter;
        final ServerLevel world;
        double angle;
        double height;
        double radius;
        int age;

        TornadoSwirlEntity(ServerLevel world, Vec3 center, double startAngle, double startRadius) {
            this.world = world;
            this.startCenter = center;
            this.angle = startAngle;
            this.height = 0;
            this.radius = startRadius;
            this.age = 0;
        }

        boolean tick() {

            height += 0.2;


            angle += 18;


            if (height < 30) {
                radius += 0.08;
            } else {
                radius -= 0.05;
            }


            double x = startCenter.x + Math.cos(Math.toRadians(angle)) * radius;
            double z = startCenter.z + Math.sin(Math.toRadians(angle)) * radius;
            double y = startCenter.y + height;


            world.sendParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.08);
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 4, 0.2, 0.2, 0.2, 0.05);


            if (height % 5 < 0.5) {
                world.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.4, 0.4, 0.4, 0.06);
                world.sendParticles(ParticleTypes.LAVA, x, y, z, 3, 0.2, 0.2, 0.2, 0.02);
            }

            age++;


            return height < 60 && age < 60;
        }
    }


    private static final List<TornadoSwirlEntity> activeTornadoSwirls = new ArrayList<>();
    private static long tornadoStartTime = 0;
    private static boolean tornadoActive = false;
    private static Vec3 tornadoCenter = null;
    private static ServerLevel tornadoWorld = null;


    private static long auraBeamStartTime = 0;
    private static boolean auraBeamsActive = false;
    private static UUID auraBeamPlayer1 = null;
    private static UUID auraBeamPlayer2 = null;


    private static final Map<UUID, Vec3> smoothTPTarget = new HashMap<>();
    private static final Map<UUID, Integer> smoothTPProgress = new HashMap<>();

    private static final Map<UUID, net.minecraft.world.entity.decoration.ArmorStand> fireDapArmorStands = new HashMap<>();

    private static final Map<UUID, net.minecraft.world.entity.decoration.ArmorStand> perfectDapArmorStands = new HashMap<>();


    private static class FireDapScheduledEvent {
        final ServerPlayer p1;
        final ServerPlayer p2;
        final long executeTime;
        FireDapScheduledEvent(ServerPlayer p1, ServerPlayer p2, long executeTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.executeTime = executeTime;
        }
    }
    private static final Map<UUID, FireDapScheduledEvent> pendingFireArmImpacts = new HashMap<>();
    private static final Map<UUID, FireDapScheduledEvent> pendingFireTornadoSpawns = new HashMap<>();


    private static class SaturnRing {
        final Vec3 center;
        final long startTime;
        final long endTime;
        SaturnRing(Vec3 center, long startTime) {
            this.center = center;
            this.startTime = startTime;
            this.endTime = startTime + 20000;
        }
    }
    private static final List<SaturnRing> activeSaturnRings = new ArrayList<>();






    private static final Map<UUID, Long> impactFreezeEnd = new HashMap<>();
    private static final long IMPACT_FREEZE_MS = 90;

    public static void freezeOnImpact(UUID playerId) {
        long now = System.currentTimeMillis();
        impactFreezeEnd.put(playerId, now + IMPACT_FREEZE_MS);
    }

    public static boolean isInImpactFreeze(UUID playerId) {
        Long freezeEnd = impactFreezeEnd.get(playerId);
        if (freezeEnd == null) return false;

        if (System.currentTimeMillis() >= freezeEnd) {
            impactFreezeEnd.remove(playerId);
            return false;
        }
        return true;
    }


    public static final Map<UUID, Long> blockingAnimEndTime = new HashMap<>();


    private static final List<ScheduledParticles> scheduledParticles = new ArrayList<>();


    private static final Set<UUID> highFivePartners = new HashSet<>();



    private static final Map<UUID, Long> perfectFriendshipLevitation = new HashMap<>();
    private static final Map<UUID, UUID> perfectFriendshipPartner = new HashMap<>();

    private record ScheduledParticles(ServerLevel world, double x, double y, double z, long spawnTime) {}

    private record ScheduledPerfectDapEffect(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2, long effectTime) {}

    private static final List<ScheduledPerfectDapEffect> scheduledPerfectDapEffects = new ArrayList<>();



    public static final Identifier CHARGE_START_ID = Identifier.fromNamespaceAndPath("cooptest", "charged_dap_start");
    public static final Identifier CHARGE_RELEASE_ID = Identifier.fromNamespaceAndPath("cooptest", "charged_dap_release");
    public static final Identifier CHARGE_SYNC_ID = Identifier.fromNamespaceAndPath("cooptest", "charged_dap_sync");
    public static final Identifier DAP_RESULT_ID = Identifier.fromNamespaceAndPath("cooptest", "charged_dap_result");
    public static final Identifier WHIFF_COOLDOWN_ID = Identifier.fromNamespaceAndPath("cooptest", "whiff_cooldown");
    public static final Identifier IMPACT_FRAME_ID = Identifier.fromNamespaceAndPath("cooptest", "impact_frame");

    public record ChargeStartPayload() implements CustomPacketPayload {
        public static final Type<ChargeStartPayload> ID = new Type<>(CHARGE_START_ID);
        public static final StreamCodec<FriendlyByteBuf, ChargeStartPayload> CODEC =
                StreamCodec.unit(new ChargeStartPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ChargeReleasePayload() implements CustomPacketPayload {
        public static final Type<ChargeReleasePayload> ID = new Type<>(CHARGE_RELEASE_ID);
        public static final StreamCodec<FriendlyByteBuf, ChargeReleasePayload> CODEC =
                StreamCodec.unit(new ChargeReleasePayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public record WhiffCooldownPayload(long cooldownDurationMs) implements CustomPacketPayload {
        public static final Type<WhiffCooldownPayload> ID = new Type<>(WHIFF_COOLDOWN_ID);
        public static final StreamCodec<FriendlyByteBuf, WhiffCooldownPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> buf.writeLong(payload.cooldownDurationMs),
                        buf -> new WhiffCooldownPayload(buf.readLong())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public record ImpactFramePayload(int durationMs, boolean grayscale) implements CustomPacketPayload {
        public static final Type<ImpactFramePayload> ID = new Type<>(IMPACT_FRAME_ID);
        public static final StreamCodec<FriendlyByteBuf, ImpactFramePayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeInt(payload.durationMs);
                            buf.writeBoolean(payload.grayscale);
                        },
                        buf -> new ImpactFramePayload(buf.readInt(), buf.readBoolean())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public static final Identifier PERFECT_DAP_FREEZE_ID = Identifier.fromNamespaceAndPath("cooptest", "perfect_dap_freeze");

    public record PerfectDapFreezePayload(boolean frozen) implements CustomPacketPayload {
        public static final Type<PerfectDapFreezePayload> ID = new Type<>(PERFECT_DAP_FREEZE_ID);
        public static final StreamCodec<FriendlyByteBuf, PerfectDapFreezePayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> buf.writeBoolean(payload.frozen),
                        buf -> new PerfectDapFreezePayload(buf.readBoolean())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public static final Identifier PERFECT_DAP_IMPACT_FRAME_ID = Identifier.fromNamespaceAndPath("cooptest", "perfect_dap_impact_frame");
    public static final Identifier FACING_DAP_IMPACT_ID = Identifier.fromNamespaceAndPath("cooptest", "facing_dap_impact");

    public record FacingDapImpactPayload() implements CustomPacketPayload {
        public static final Type<FacingDapImpactPayload> ID = new Type<>(FACING_DAP_IMPACT_ID);
        public static final StreamCodec<FriendlyByteBuf, FacingDapImpactPayload> CODEC =
                StreamCodec.unit(new FacingDapImpactPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record PerfectDapImpactFramePayload(int frameIndex) implements CustomPacketPayload {
        public static final Type<PerfectDapImpactFramePayload> ID = new Type<>(PERFECT_DAP_IMPACT_FRAME_ID);
        public static final StreamCodec<FriendlyByteBuf, PerfectDapImpactFramePayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> buf.writeInt(payload.frameIndex),
                        buf -> new PerfectDapImpactFramePayload(buf.readInt())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public static final Identifier FIRE_DAP_J_PRESS_ID = Identifier.fromNamespaceAndPath("cooptest", "fire_dap_j_press");
    public static final Identifier FIRE_DAP_WINDOW_ID = Identifier.fromNamespaceAndPath("cooptest", "fire_dap_window");
    public static final Identifier FIRE_DAP_FREEZE_ID = Identifier.fromNamespaceAndPath("cooptest", "fire_dap_freeze");
    public static final Identifier FIRE_DAP_FP_ID = Identifier.fromNamespaceAndPath("cooptest", "fire_dap_fp");

    public record FireDapJPressPayload() implements CustomPacketPayload {
        public static final Type<FireDapJPressPayload> ID = new Type<>(FIRE_DAP_J_PRESS_ID);
        public static final StreamCodec<FriendlyByteBuf, FireDapJPressPayload> CODEC =
                StreamCodec.unit(new FireDapJPressPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record FireDapWindowPayload() implements CustomPacketPayload {
        public static final Type<FireDapWindowPayload> ID = new Type<>(FIRE_DAP_WINDOW_ID);
        public static final StreamCodec<FriendlyByteBuf, FireDapWindowPayload> CODEC =
                StreamCodec.unit(new FireDapWindowPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record FireDapFreezePayload(UUID playerId, boolean frozen) implements CustomPacketPayload {
        public static final Type<FireDapFreezePayload> ID = new Type<>(FIRE_DAP_FREEZE_ID);
        public static final StreamCodec<FriendlyByteBuf, FireDapFreezePayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.playerId);
                    buf.writeBoolean(payload.frozen);
                },
                buf -> new FireDapFreezePayload(buf.readUUID(), buf.readBoolean())
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public record FireDapFirstPersonPayload(UUID playerId, boolean showBothHands) implements CustomPacketPayload {
        public static final Type<FireDapFirstPersonPayload> ID = new Type<>(FIRE_DAP_FP_ID);
        public static final StreamCodec<FriendlyByteBuf, FireDapFirstPersonPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.playerId);
                    buf.writeBoolean(payload.showBothHands);
                },
                buf -> new FireDapFirstPersonPayload(buf.readUUID(), buf.readBoolean())
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }





    public record HeavenReadyPayload(UUID playerId, boolean ready) implements CustomPacketPayload {
        public static final Type<HeavenReadyPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "heaven_ready"));
        public static final StreamCodec<FriendlyByteBuf, HeavenReadyPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeUUID(payload.playerId);
                    buf.writeBoolean(payload.ready);
                },
                buf -> new HeavenReadyPayload(buf.readUUID(), buf.readBoolean())
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }








    public record ChargeSyncPayload(UUID playerId, float chargePercent, float firePercent, boolean isCharging) implements CustomPacketPayload {
        public static final Type<ChargeSyncPayload> ID = new Type<>(CHARGE_SYNC_ID);
        public static final StreamCodec<FriendlyByteBuf, ChargeSyncPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUUID(payload.playerId);
                            buf.writeFloat(payload.chargePercent);
                            buf.writeFloat(payload.firePercent);
                            buf.writeBoolean(payload.isCharging);
                        },
                        buf -> new ChargeSyncPayload(buf.readUUID(), buf.readFloat(), buf.readFloat(), buf.readBoolean())
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    public record DapResultPayload(double x, double y, double z, UUID player1, UUID player2,
                                   int tier, boolean perfectHit) implements CustomPacketPayload {
        public static final Type<DapResultPayload> ID = new Type<>(DAP_RESULT_ID);
        public static final StreamCodec<FriendlyByteBuf, DapResultPayload> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeDouble(payload.x);
                            buf.writeDouble(payload.y);
                            buf.writeDouble(payload.z);
                            buf.writeUUID(payload.player1);
                            buf.writeUUID(payload.player2);
                            buf.writeInt(payload.tier);
                            buf.writeBoolean(payload.perfectHit);
                        },
                        buf -> new DapResultPayload(
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readUUID(), buf.readUUID(), buf.readInt(), buf.readBoolean()
                        )
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    private static void broadcastHeavenReadyStatus(MinecraftServer server, UUID playerId, boolean ready) {
        HeavenReadyPayload payload = new HeavenReadyPayload(playerId, ready);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(ChargeStartPayload.ID, ChargeStartPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChargeReleasePayload.ID, ChargeReleasePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HeavenReadyPayload.ID, HeavenReadyPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChargeSyncPayload.ID, ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DapResultPayload.ID, DapResultPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WhiffCooldownPayload.ID, WhiffCooldownPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ImpactFramePayload.ID, ImpactFramePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PerfectDapFreezePayload.ID, PerfectDapFreezePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PerfectDapImpactFramePayload.ID, PerfectDapImpactFramePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FacingDapImpactPayload.ID, FacingDapImpactPayload.CODEC);


        PayloadTypeRegistry.serverboundPlay().register(FireDapJPressPayload.ID, FireDapJPressPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FireDapWindowPayload.ID, FireDapWindowPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FireDapFreezePayload.ID, FireDapFreezePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FireDapFirstPersonPayload.ID, FireDapFirstPersonPayload.CODEC);


        PayloadTypeRegistry.serverboundPlay().register(QTEButtonPressPayload.ID, QTEButtonPressPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QTEWindowPayload.ID, QTEWindowPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QTEClearPayload.ID, QTEClearPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ChargeStartPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {

                if (!CoopMovesConfig.get().enableDap) {
                    return;
                }
                onChargeStart(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ChargeReleasePayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {

                if (!CoopMovesConfig.get().enableDap) {
                    return;
                }
                onChargeRelease(player);
            });
        });


        ServerPlayNetworking.registerGlobalReceiver(FireDapJPressPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> onFireDapJPress(player));
        });


        ServerPlayNetworking.registerGlobalReceiver(QTEButtonPressPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            String button = payload.button();
            context.server().execute(() -> {

                if (PerfectDapComboHandler.onButtonPress(player, button)) return;


                if (DapFusionHandler.onQTEButtonPress(player, button)) {
                    return;
                }

                if (HighFiveQTEHugHandler.onButtonPress(player, button)) {
                    return;
                }



                if (HuddleHandler.onButtonPress(player, button)) {
                    return;
                }

                if (DapComboChain.onButtonPress(player, button)) {
                    return;
                }
                QTEManager.onButtonPress(player, button);
            });
        });


        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {
                    if (world.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
                    if (!(player instanceof ServerPlayer sp)) return net.minecraft.world.InteractionResult.PASS;
                    if (!(entity instanceof ServerPlayer target)) return net.minecraft.world.InteractionResult.PASS;
                    if (sp.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
                    if (getChargePercent(sp.getUUID()) < 0.95f) return net.minecraft.world.InteractionResult.PASS;

                    if (getChargePercent(target.getUUID()) < 0.95f) {

                        NormalFacingDapHandler.recordRightClick(sp, target);
                        return net.minecraft.world.InteractionResult.PASS;
                    }

                    if (NormalFacingDapHandler.isConfirmed(sp.getUUID(), target.getUUID())
                            || NormalFacingDapHandler.isConfirmedOneSide(target.getUUID(), sp.getUUID())) {

                        NormalFacingDapHandler.clearConfirm(sp.getUUID(), target.getUUID());
                        chargeStartTime.remove(sp.getUUID());
                        chargeStartTime.remove(target.getUUID());
                        broadcastChargeCancel(sp);
                        broadcastChargeCancel(target);
                        NormalFacingDapHandler.start(sp, target);
                    } else {
                        NormalFacingDapHandler.recordRightClick(sp, target);
                    }
                    return net.minecraft.world.InteractionResult.PASS;
                });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();


            Iterator<ScheduledParticles> particleIt = scheduledParticles.iterator();
            while (particleIt.hasNext()) {
                ScheduledParticles sp = particleIt.next();
                if (now >= sp.spawnTime()) {
                    sp.world().sendParticles(ParticleTypes.CRIT, sp.x(), sp.y(), sp.z(), 15, 0.3, 0.3, 0.3, 0.15);
                    sp.world().sendParticles(ParticleTypes.ENCHANT, sp.x(), sp.y(), sp.z(), 10, 0.2, 0.2, 0.2, 0.1);
                    particleIt.remove();
                }
            }


            Iterator<ScheduledPerfectDapEffect> effectIt = scheduledPerfectDapEffects.iterator();
            while (effectIt.hasNext()) {
                ScheduledPerfectDapEffect effect = effectIt.next();
                if (now >= effect.effectTime()) {

                    net.minecraft.world.entity.decoration.ArmorStand stand = perfectDapArmorStands.get(effect.p1().getUUID());
                    Vec3 pos;
                    if (stand != null && !stand.isRemoved()) {
                        pos = stand.position();
                    } else {
                        pos = effect.pos();
                    }
                    ServerLevel world = effect.world();


                    ServerPlayNetworking.send(effect.p1(), new PerfectDapImpactFramePayload(1));
                    ServerPlayNetworking.send(effect.p2(), new PerfectDapImpactFramePayload(1));




                    world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0);


                    world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 40, 0.4, 0.4, 0.4, 0.12);


                    world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 50, 0.5, 0.5, 0.5, 0.15);


                    world.playSound(null, pos.x, pos.y, pos.z,
                            ModSounds.DAP_HIT, SoundSource.PLAYERS, 1.5f, 1.0f);
                    world.playSound(null, pos.x, pos.y, pos.z,
                            ModSounds.IMPACT, SoundSource.PLAYERS, 1.0f, 1.0f);
                    world.playSound(null, pos.x, pos.y, pos.z,
                            SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 1.0f);


                    if (!CoopMovesConfig.get().noGriefMode) {
                        createExplosion(world, pos, effect.p1(), effect.p2(), 3.5, 6.0f);
                        createShockwave(world, pos, effect.p1(), effect.p2(), 10.0, 2.0);
                    }




                    handleUnderwaterPerfectDap(world, pos, effect.p1(), effect.p2());

                    effectIt.remove();
                }
            }


            Iterator<Map.Entry<UUID, Long>> underwaterIt = underwaterRemovalStart.entrySet().iterator();
            while (underwaterIt.hasNext()) {
                Map.Entry<UUID, Long> entry = underwaterIt.next();
                UUID trackId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                if (elapsed >= 5000) {
                    underwaterIt.remove();
                    underwaterRemovalPos.remove(trackId);
                    underwaterRemovalWorld.remove(trackId);
                    continue;
                }


                Vec3 pos = underwaterRemovalPos.get(trackId);
                ServerLevel world = underwaterRemovalWorld.get(trackId);
                if (pos == null || world == null) continue;

                BlockPos centerPos = BlockPos.containing(pos);
                double radius = 3.0;
                int radiusInt = (int) Math.ceil(radius);


                for (int x = -radiusInt; x <= radiusInt; x++) {
                    for (int y = -radiusInt; y <= radiusInt; y++) {
                        for (int z = -radiusInt; z <= radiusInt; z++) {
                            double distance = Math.sqrt(x*x + y*y + z*z);
                            if (distance <= radius) {
                                BlockPos blockPos = centerPos.offset(x, y, z);
                                if (world.getBlockState(blockPos).is(net.minecraft.world.level.block.Blocks.WATER)) {
                                    world.setBlockAndUpdate(blockPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                                }
                            }
                        }
                    }
                }
            }


            Iterator<HeavenParticleSpawner> heavenParticleIt = activeHeavenParticles.iterator();
            while (heavenParticleIt.hasNext()) {
                HeavenParticleSpawner spawner = heavenParticleIt.next();

                if (now >= spawner.endTime) {

                    heavenParticleIt.remove();
                    continue;
                }


                ServerLevel world = spawner.world;
                Vec3 pos = spawner.pos;


                world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 3, 3, 3, 0);
                world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 10, 5, 5, 5, 0.3);
                world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 4, 4, 4, 0.2);
                world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f), pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }


            Iterator<Map.Entry<UUID, Long>> perfectDapIt = perfectDapStartTime.entrySet().iterator();
            while (perfectDapIt.hasNext()) {
                Map.Entry<UUID, Long> entry = perfectDapIt.next();
                UUID playerId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {

                    perfectDapIt.remove();
                    perfectDapPartner.remove(playerId);
                    perfectDapFreezeEnd.remove(playerId);
                    perfectDapImpactSent.remove(playerId);


                    net.minecraft.world.entity.decoration.ArmorStand stand = perfectDapArmorStands.remove(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        stand.discard();
                    }
                    continue;
                }

                UUID partnerId = perfectDapPartner.get(playerId);
                ServerPlayer partner = partnerId != null ? server.getPlayerList().getPlayer(partnerId) : null;


                if (partner != null) {
                    net.minecraft.world.entity.decoration.ArmorStand stand = perfectDapArmorStands.get(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        Vec3 p1Hand = player.position().add(0, 1.4, 0);
                        Vec3 p2Hand = partner.position().add(0, 1.4, 0);
                        Vec3 handMid = p1Hand.add(p2Hand).scale(0.5);
                        stand.setPos(handMid.x, handMid.y, handMid.z);
                        stand.setRemainingFireTicks(0);
                    }



                    smoothDapDescent(player, stand);
                }


                if (elapsed >= 150 && elapsed <= 1330 && partner != null) {
                    ServerLevel world = player.level();


                    Vec3 particlePos;
                    net.minecraft.world.entity.decoration.ArmorStand stand = perfectDapArmorStands.get(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        particlePos = stand.position();
                    } else {

                        particlePos = player.position().add(partner.position()).scale(0.5).add(0, 1.4, 0);
                    }



                }


                if (elapsed >= 290 && elapsed < 310 && partner != null) {
                    if (!perfectDapImpactSent.getOrDefault(playerId, false)) {
                        ServerPlayNetworking.send(player, new PerfectDapImpactFramePayload(1));
                        perfectDapImpactSent.put(playerId, true);
                    }
                }



                if (elapsed >= 812 && perfectDapFreezeEnd.containsKey(playerId)) {
                    perfectDapFreezeEnd.remove(playerId);
                    ServerPlayNetworking.send(player, new PerfectDapFreezePayload(false));













                    if (partnerId != null) {
                        ServerPlayer partner2 = server.getPlayerList().getPlayer(partnerId);
                        if (partner2 != null) {
                            perfectDapFreezeEnd.remove(partnerId);
                            ServerPlayNetworking.send(partner2, new PerfectDapFreezePayload(false));
                        }
                    }
                }


                if (elapsed >= 1625) {
                    perfectDapIt.remove();
                    perfectDapPartner.remove(playerId);
                    perfectDapFreezeEnd.remove(playerId);
                    perfectDapImpactSent.remove(playerId);
                    perfectDapExtendHit.remove(playerId);


                    net.minecraft.world.entity.decoration.ArmorStand stand = perfectDapArmorStands.remove(playerId);
                    if (stand != null && !stand.isRemoved()) {
                        stand.discard();
                    }
                }
            }


            Iterator<Map.Entry<UUID, HeavenDapData>> heavenIt = heavenPlayers.entrySet().iterator();
            while (heavenIt.hasNext()) {
                Map.Entry<UUID, HeavenDapData> entry = heavenIt.next();
                UUID playerId = entry.getKey();
                HeavenDapData data = entry.getValue();
                long elapsed = now - data.startTime;

                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    heavenIt.remove();
                    continue;
                }




                if (elapsed >= 3000 && elapsed <= 9000) {
                    Vec3 pos = player.position();


                    data.world.sendParticles(ParticleTypes.WHITE_ASH,
                            pos.x, pos.y + 1, pos.z,
                            5, 1.0, 1.0, 1.0, 0.02);


                    data.world.sendParticles(ParticleTypes.CLOUD,
                            pos.x, pos.y, pos.z,
                            3, 0.5, 0.5, 0.5, 0.01);
                }


                if (elapsed >= 9500 && elapsed < 9600) {
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 40, 0, false, false));
                }



                if (elapsed >= 11500) {
                    Vec3 returnPos = data.originalMidpoint;
                    UUID partnerId = data.partnerId;
                    ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);

                    if (partner != null) {

                        double dx = partner.getX() - player.getX();
                        double dz = partner.getZ() - player.getZ();
                        float yawTowardsPartner = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                        float yawAwayFromPartner = yawTowardsPartner + 180;


                        player.teleportTo(data.world, returnPos.x, returnPos.y, returnPos.z, java.util.Set.of(), yawAwayFromPartner, 0.0f, false);


                        player.stopFallFlying();
                        player.setDeltaMovement(Vec3.ZERO);
                        player.syncVelocity = true;


                        ServerPlayNetworking.send(player, new PerfectDapFreezePayload(false));
                        PoseNetworking.broadcastAnimState(player, 0);


                        player.removeEffect(MobEffects.NAUSEA);



                        if (partner != null && heavenPlayers.containsKey(partnerId)) {

                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.RESISTANCE, 80, 255, false, false));
                            partner.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.RESISTANCE, 80, 255, false, false));
                            final ServerLevel _rw = data.world;
                            final Vec3 _rp = returnPos;
                            for (int i = 0; i < 8; i++) {
                                double a = Math.toRadians(i * 45.0);
                                for (double dy : new double[]{0, 20, -20}) {
                                    _rw.explode(null,
                                            _rp.x + Math.cos(a) * 5, _rp.y + dy,
                                            _rp.z + Math.sin(a) * 5, 8f,
                                            false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                                }
                            }
                            new Thread(() -> {
                                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                                _rw.getServer().execute(() -> {
                                    for (int i = 0; i < 8; i++) {
                                        double a = Math.toRadians(i * 45.0 + 22.5);
                                        for (double dy : new double[]{0, 20, -20}) {
                                            _rw.explode(null,
                                                    _rp.x + Math.cos(a) * 25, _rp.y + dy,
                                                    _rp.z + Math.sin(a) * 25, 20f,
                                                    true, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                                        }
                                    }
                                });
                            }).start();
                            new Thread(() -> {
                                try { Thread.sleep(350); } catch (InterruptedException ignored) {}
                                _rw.getServer().execute(() -> {
                                    for (int i = 0; i < 8; i++) {
                                        double a = Math.toRadians(i * 45.0);
                                        for (double dy : new double[]{0, 20, -20}) {
                                            _rw.explode(null,
                                                    _rp.x + Math.cos(a) * 60, _rp.y + dy,
                                                    _rp.z + Math.sin(a) * 60, 15f,
                                                    true, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                                        }
                                    }
                                });
                            }).start();
                            new Thread(() -> {
                                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                                _rw.getServer().execute(() -> {
                                    for (int i = 0; i < 8; i++) {
                                        double a = Math.toRadians(i * 45.0 + 22.5);
                                        _rw.explode(null,
                                                _rp.x + Math.cos(a) * 100, _rp.y,
                                                _rp.z + Math.sin(a) * 100, 12f,
                                                true, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                                    }
                                });
                            }).start();
                        }


                        data.world.sendParticles((ParticleOptions)ParticleTypes.FLASH, returnPos.x, returnPos.y, returnPos.z, 1, 0.0, 0.0, 0.0, 0.0);
                        data.world.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                                returnPos.x, returnPos.y, returnPos.z,
                                10, 3, 3, 3, 0.5);


                        final ServerPlayer finalPlayer = player;
                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                                finalPlayer.level().getServer().execute(() -> {

                                    ServerPlayNetworking.send(finalPlayer, new HeavenDapPayloads.RestoreVolumePayload());
                                });
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }).start();




                        if (partner != null && heavenPlayers.containsKey(partnerId)) {

                            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                        "§d§l✨ " + player.getName().getString() + " §7and §d§l" +
                                                partner.getName().getString() + " §7have achieved §d§lPERFECT FRIENDSHIP! ✨"
                                ), false);
                            }


                            server.overworld().playSound(null, returnPos.x, returnPos.y, returnPos.z,
                                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 3.0f, 1.0f);
                            server.overworld().playSound(null, returnPos.x, returnPos.y, returnPos.z,
                                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 2.0f, 1.5f);
                        }
                    }




                    heavenIt.remove();
                }
            }



            Iterator<Map.Entry<UUID, Long>> fireDapIt = fireDapStartTime.entrySet().iterator();
            while (fireDapIt.hasNext()) {
                Map.Entry<UUID, Long> entry = fireDapIt.next();
                UUID playerId = entry.getKey();
                long startTime = entry.getValue();
                long elapsed = now - startTime;

                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    fireDapIt.remove();
                    inFireDapHit.remove(playerId);
                    fireDapPartner.remove(playerId);
                    continue;
                }


                if (elapsed >= FIRE_IMPACT_TIME && !fireCircleSpawned.getOrDefault(playerId, true) && inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null && fireDapStartTime.containsKey(partnerId)) {
                        ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
                        if (partner != null && !fireCircleSpawned.getOrDefault(partnerId, true)) {
                            spawnFireCircle(player, partner);


                            long freezeEnd = now + (FIRE_DAP_HIT_LENGTH - elapsed);
                            fireDapComboFreezeEnd.put(playerId, freezeEnd);
                            fireDapComboFreezeEnd.put(partnerId, freezeEnd);


                            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                ServerPlayNetworking.send(p, new FireDapFreezePayload(playerId, true));
                                ServerPlayNetworking.send(p, new FireDapFreezePayload(partnerId, true));
                            }



                            fireCircleSpawned.put(playerId, true);
                            fireCircleSpawned.put(partnerId, true);
                        }
                    }
                }


                if (elapsed >= FIRE_IMPACT_TIME && inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
                        if (partner != null) {

                            net.minecraft.world.entity.decoration.ArmorStand stand = fireDapArmorStands.get(playerId);
                            if (stand != null && !stand.isRemoved()) {
                                Vec3 p1Hand = player.position().add(0, 1.4, 0);
                                Vec3 p2Hand = partner.position().add(0, 1.4, 0);
                                Vec3 handMid = p1Hand.add(p2Hand).scale(0.5);
                                stand.setPos(handMid.x, handMid.y, handMid.z);
                                stand.setRemainingFireTicks(0);


                                smoothDapDescent(player, stand);
                                smoothDapDescent(partner, stand);
                            }
                        }
                    }
                }


                if (elapsed >= FIRE_DAP_HIT_LENGTH && inFireDapHit.getOrDefault(playerId, false)) {

                    Long jpressTime = fireDapComboRequestTime.get(playerId);
                    if (jpressTime != null && (now - jpressTime) < 2000) {


                        if (now - jpressTime >= 1000) {

                            UUID partnerId = fireDapPartner.get(playerId);
                            if (partnerId != null) {
                                ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
                                if (partner != null) {

                                    partner.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c✗ You missed the combo! " + player.getName().getString() + " pressed J!"));

                                    player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c✗ " + partner.getName().getString() + " missed the combo!"));
                                }
                            }

                        } else {
                            continue;
                        }
                    }

                    fireDapIt.remove();
                    inFireDapHit.remove(playerId);
                    fireDapComboRequestTime.remove(playerId);


                    DapSessionManager.removeSessionForPlayer(playerId);



                    if (fireDapComboFreezeEnd.containsKey(playerId)) {
                        fireDapComboFreezeEnd.remove(playerId);
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                            ServerPlayNetworking.send(p, new FireDapFreezePayload(playerId, false));
                        }
                        PoseNetworking.broadcastAnimState(player, 0);
                    }
                }
            }


            for (Map.Entry<UUID, Long> entry : new HashMap<>(fireDapComboFreezeEnd).entrySet()) {
                UUID playerId = entry.getKey();
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && !inFireDapHit.getOrDefault(playerId, false)) {
                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
                        if (partner != null) {
                            teleportFireDapFacingEachOther(player, partner, 1.2);
                        }
                    }
                }
            }


            Iterator<Map.Entry<UUID, FireDapScheduledEvent>> armIt = pendingFireArmImpacts.entrySet().iterator();
            while (armIt.hasNext()) {
                Map.Entry<UUID, FireDapScheduledEvent> entry = armIt.next();
                FireDapScheduledEvent event = entry.getValue();
                if (now >= event.executeTime) {
                    executeFireArmImpact(event.p1, event.p2);
                    armIt.remove();
                }
            }


            Iterator<Map.Entry<UUID, FireDapScheduledEvent>> tornadoIt = pendingFireTornadoSpawns.entrySet().iterator();
            while (tornadoIt.hasNext()) {
                Map.Entry<UUID, FireDapScheduledEvent> entry = tornadoIt.next();
                FireDapScheduledEvent event = entry.getValue();
                if (now >= event.executeTime) {
                    spawnFireTornado(event.p1, event.p2);
                    tornadoIt.remove();
                }
            }


            if (auraBeamsActive && server != null) {
                long elapsed = now - auraBeamStartTime;


                if (elapsed < 4000) {
                    ServerPlayer p1 = server.getPlayerList().getPlayer(auraBeamPlayer1);
                    ServerPlayer p2 = server.getPlayerList().getPlayer(auraBeamPlayer2);

                    if (p1 != null && p2 != null) {
                        spawnAnimatedAuraBeam(p1, elapsed);
                        spawnAnimatedAuraBeam(p2, elapsed);
                    }
                } else {
                    auraBeamsActive = false;
                }
            }


            Iterator<Map.Entry<UUID, Vec3>> tpIt = smoothTPTarget.entrySet().iterator();
            while (tpIt.hasNext()) {
                Map.Entry<UUID, Vec3> entry = tpIt.next();
                UUID playerId = entry.getKey();
                Vec3 target = entry.getValue();

                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    tpIt.remove();
                    smoothTPProgress.remove(playerId);
                    continue;
                }

                int progress = smoothTPProgress.getOrDefault(playerId, 0);


                if (progress < 10) {
                    Vec3 current = player.position();
                    double t = (progress + 1) / 10.0;

                    Vec3 newPos = new Vec3(
                            current.x + (target.x - current.x) * t,
                            current.y + (target.y - current.y) * t,
                            current.z + (target.z - current.z) * t
                    );


                    UUID partnerId = fireDapPartner.get(playerId);
                    if (partnerId != null) {
                        ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
                        if (partner != null) {
                            double dx = partner.position().x - newPos.x;
                            double dz = partner.position().z - newPos.z;
                            float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;

                            player.teleportTo(player.level(), newPos.x, newPos.y, newPos.z, java.util.Set.of(), yaw, player.getXRot(), false);
                        }
                    }

                    smoothTPProgress.put(playerId, progress + 1);
                } else {
                    tpIt.remove();
                    smoothTPProgress.remove(playerId);
                }
            }


            if (tornadoActive && tornadoCenter != null && tornadoWorld != null) {
                long elapsed = now - tornadoStartTime;


                if (elapsed < 5000) {

                    if (elapsed % 40 == 0) {

                        for (int i = 0; i < 12; i++) {
                            double startAngle = i * 30;
                            double startRadius = 3.0 + (Math.random() * 2);

                            TornadoSwirlEntity swirl = new TornadoSwirlEntity(tornadoWorld, tornadoCenter, startAngle, startRadius);
                            activeTornadoSwirls.add(swirl);
                        }
                    }
                } else {

                    tornadoActive = false;
                    activeTornadoSwirls.clear();
                }


                Iterator<TornadoSwirlEntity> swirlIt = activeTornadoSwirls.iterator();
                while (swirlIt.hasNext()) {
                    TornadoSwirlEntity swirl = swirlIt.next();
                    boolean stillAlive = swirl.tick();
                    if (!stillAlive) {
                        swirlIt.remove();
                    }
                }


                if (tornadoCenter != null && tornadoWorld != null) {
                    double tornadoRadius = 30;


                    UUID shieldPlayer1 = auraBeamPlayer1;
                    UUID shieldPlayer2 = auraBeamPlayer2;


                    AABB searchBox = new AABB(
                            tornadoCenter.x - tornadoRadius, tornadoCenter.y, tornadoCenter.z - tornadoRadius,
                            tornadoCenter.x + tornadoRadius, tornadoCenter.y + 70, tornadoCenter.z + tornadoRadius
                    );

                    for (Entity entity : tornadoWorld.getEntities(null, searchBox)) {

                        if (entity.getUUID().equals(shieldPlayer1) || entity.getUUID().equals(shieldPlayer2)) {
                            continue;
                        }

                        Vec3 entityPos = entity.position();
                        double dx = entityPos.x - tornadoCenter.x;
                        double dz = entityPos.z - tornadoCenter.z;
                        double distanceToCenter = Math.sqrt(dx * dx + dz * dz);


                        if (distanceToCenter >= tornadoRadius - 2 && distanceToCenter <= tornadoRadius + 2) {

                            Vec3 direction = new Vec3(dx, 0, dz).normalize();

                            entity.setDeltaMovement(
                                    direction.x * 2.0,
                                    0.5,
                                    direction.z * 2.0
                            );
                            entity.syncVelocity = true;


                            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                                living.hurtClient(living.damageSources().genericKill());
                            }
                        }


                        if (entity instanceof net.minecraft.world.entity.projectile.Projectile) {

                            if (distanceToCenter >= tornadoRadius - 3) {
                                entity.discard();
                            }
                        }
                    }
                }
            }


            Iterator<Map.Entry<UUID, Long>> freezeIt = fireDapComboFreezeEnd.entrySet().iterator();
            while (freezeIt.hasNext()) {
                Map.Entry<UUID, Long> entry = freezeIt.next();
                if (now >= entry.getValue()) {
                    UUID playerId = entry.getKey();


                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerPlayNetworking.send(player, new FireDapFreezePayload(playerId, false));
                    }

                    freezeIt.remove();
                    fireDapPartner.remove(playerId);


                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        PoseNetworking.broadcastAnimState(player, 0);
                    }

                }
            }


            Iterator<SaturnRing> ringIt = activeSaturnRings.iterator();
            while (ringIt.hasNext()) {
                SaturnRing ring = ringIt.next();
                if (now >= ring.endTime) {
                    ringIt.remove();
                    continue;
                }


                long elapsed = now - ring.startTime;
                double rotationAngle = (elapsed / 100.0) * 360.0;
                double radius = 50.0;


                ServerLevel world = server.overworld();


                for (double angle = 0; angle < 360; angle += 5) {
                    double rad = Math.toRadians(angle + rotationAngle);
                    double x = ring.center.x + Math.cos(rad) * radius;
                    double z = ring.center.z + Math.sin(rad) * radius;
                    double y = ring.center.y;


                    world.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
                    world.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, 1, 0.05, 0.05, 0.05, 0);
                }
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID id = player.getUUID();


                if (impactFreezeTicks.containsKey(id)) {
                    int remaining = impactFreezeTicks.get(id);
                    if (remaining > 0) {

                        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
                        player.syncVelocity = true;
                        impactFreezeTicks.put(id, remaining - 1);
                    } else {
                        impactFreezeTicks.remove(id);
                    }
                }

                Vec3 velocity = getEffectiveVelocity(player);
                double speed = velocity.length() * 20.0;


                LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());
                history.addLast(speed);
                while (history.size() > SPEED_HISTORY_TICKS) {
                    history.removeFirst();
                }


                if (chargeStartTime.containsKey(id)) {
                    float charge = getChargePercent(id);


                    double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;

                    if (charge >= 0.99f) {

                        boolean isMoving = horizontalSpeed >= MIN_MOVEMENT_SPEED;

                        if (isMoving) {

                            fireGraceTime.remove(id);


                            if (!CoopMovesConfig.get().enableFireDap) {
                                fireStartTime.remove(id);
                                fireLevel.put(id, 0f);
                            } else {
                                if (!fireStartTime.containsKey(id)) {
                                    fireStartTime.put(id, now);
                                }

                                long timeAtFullCharge = now - fireStartTime.get(id);

                                if (timeAtFullCharge >= fireDelayMs()) {

                                    long fireBuildTime = timeAtFullCharge - fireDelayMs();
                                    float fire = Math.min(1.0f, (float) fireBuildTime / fireBuildTimeMs());
                                    fireLevel.put(id, fire);


                                    spawnFireHandParticles(player, fire);


                                    if (fire >= 0.99f) {

                                        if (!fireMaxedStartTime.containsKey(id)) {
                                            fireMaxedStartTime.put(id, now);
                                        }

                                        long timeAtMaxFire = now - fireMaxedStartTime.get(id);
                                        if (timeAtMaxFire >= HEAVEN_READY_TIME_MS && !heavenReady.contains(id)) {

                                            heavenReady.add(id);


                                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                                    net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS,
                                                    1.0f, 0.8f);

                                            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§d§l✨ HEAVEN READY! ✨ §7(Fire UI broken!)"));


                                            HeavenReadyPayload payload = new HeavenReadyPayload(id, true);
                                            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                                ServerPlayNetworking.send(p, payload);
                                            }
                                        }
                                    } else {

                                        fireMaxedStartTime.remove(id);
                                    }
                                } else {

                                    fireLevel.put(id, 0f);
                                }
                            }
                        } else {

                            if (!fireGraceTime.containsKey(id)) {
                                fireGraceTime.put(id, now);
                            }

                            long graceDuration = now - fireGraceTime.get(id);
                            if (graceDuration > FIRE_GRACE_PERIOD_MS) {

                                fireStartTime.remove(id);
                                fireLevel.put(id, 0f);
                                fireMaxedStartTime.remove(id);


                                if (heavenReady.remove(id)) {
                                    broadcastHeavenReadyStatus(server, id, false);
                                }
                            }

                        }
                    } else {

                        fireStartTime.remove(id);
                        fireGraceTime.remove(id);
                        fireLevel.put(id, 0f);
                        fireMaxedStartTime.remove(id);


                        if (heavenReady.remove(id)) {
                            broadcastHeavenReadyStatus(server, id, false);
                        }
                    }
                } else {
                    fireStartTime.remove(id);
                    fireGraceTime.remove(id);
                    fireLevel.remove(id);
                    fireMaxedStartTime.remove(id);


                    if (heavenReady.remove(id)) {
                        broadcastHeavenReadyStatus(server, id, false);
                    }
                }
            }


            Iterator<Map.Entry<UUID, UUID>> it = waitingForPartner.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, UUID> entry = it.next();
                UUID waiterId = entry.getKey();
                Long releaseT = releaseTime.get(waiterId);

                if (releaseT != null && now - releaseT > releaseWindowMs()) {
                    ServerPlayer waiter = server.getPlayerList().getPlayer(waiterId);
                    ServerPlayer partner = server.getPlayerList().getPlayer(entry.getValue());

                    if (waiter != null && partner != null) {
                        executeFizzle(waiter, partner);
                    }

                    it.remove();
                    releaseTime.remove(waiterId);
                    chargeStartTime.remove(waiterId);
                    chargeStartTime.remove(entry.getValue());
                    broadcastChargeCancel(waiter);
                    if (partner != null) broadcastChargeCancel(partner);
                }
            }


            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!chargeStartTime.containsKey(player.getUUID())) continue;

                float charge = getChargePercent(player.getUUID());
                float fire = fireLevel.getOrDefault(player.getUUID(), 0f);
                ChargeSyncPayload syncPayload = new ChargeSyncPayload(player.getUUID(), charge, fire, true);

                for (ServerPlayer other : PlayerLookup.tracking(player)) {
                    ServerPlayNetworking.send(other, syncPayload);
                }
                ServerPlayNetworking.send(player, syncPayload);
            }
        });
    }

    private static void spawnFireHandParticles(ServerPlayer player, float fireLevel) {

        if (Math.random() > 0.33) return;

        ServerLevel world = player.level();
        Vec3 pos = player.position();


        float yaw = player.getYRot();
        double yawRad = Math.toRadians(yaw);


        double rightX = -Math.cos(yawRad) * 0.4;
        double rightZ = -Math.sin(yawRad) * 0.4;


        double handX = pos.x + rightX;
        double handY = pos.y + 1.3;
        double handZ = pos.z + rightZ;


        world.sendParticles(ParticleTypes.FLAME, handX, handY, handZ, 1, 0.06, 0.06, 0.06, 0.005);


        if (fireLevel > 0.6f) {
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, handX, handY, handZ, 1, 0.05, 0.05, 0.05, 0.003);
        }
    }

    private static void onChargeStart(ServerPlayer player) {
        UUID uuid = player.getUUID();


        if (HighFiveHandler.hasHandRaised(uuid)) {

            broadcastChargeCancel(player);
            return;
        }
        if (HighFiveHandler.isInBlockingAnimation(uuid)) {

            broadcastChargeCancel(player);
            return;
        }


        if (isInComboCooldown(uuid)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§cWait 1 second after combo!"));
            broadcastChargeCancel(player);
            return;
        }


        if (FallCatchHandler.isInCatchReadyMode(uuid)) return;

        if (isOnCooldown(uuid)) return;


        if (!player.getMainHandItem().isEmpty()) return;



        chargeStartTime.put(uuid, System.currentTimeMillis());
        fireLevel.put(uuid, 0f);


        ChargeSyncPayload payload = new ChargeSyncPayload(uuid, 0f, 0f, true);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer other : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(other, payload);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 0.8f);
    }

    private static void onChargeRelease(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (!chargeStartTime.containsKey(uuid)) return;

        long now = System.currentTimeMillis();
        float myCharge = getChargePercent(uuid);
        float myFire = fireLevel.getOrDefault(uuid, 0f);



        if (myCharge >= 0.95f) {
            boolean partnerAlsoCharging = chargeStartTime.keySet().stream()
                    .filter(uid -> !uid.equals(uuid))
                    .anyMatch(uid -> {
                        ServerPlayer nearby = player.level().players().stream()
                                .filter(p -> p.getUUID().equals(uid)
                                        && player.distanceTo(p) < 2.5f).findFirst().orElse(null);
                        return nearby != null;
                    });
            if (!partnerAlsoCharging && SlapHandler.checkSlapOnRelease(player)) {
                chargeStartTime.remove(uuid);
                fireLevel.remove(uuid);
                fireStartTime.remove(uuid);
                if (heavenReady.remove(uuid)) broadcastHeavenReadyStatus(player.level().getServer(), uuid, false);
                broadcastChargeCancel(player);
                return;
            }
        }


        java.util.List<ServerPlayer> allPartners = findAllDapPartners(player);
        if (allPartners.size() >= 2) {
            ServerPlayer tp2 = allPartners.get(0);
            ServerPlayer tp3 = allPartners.get(1);

            Long t2 = chargeStartTime.get(tp2.getUUID());
            Long t3 = chargeStartTime.get(tp3.getUUID());
            if (t2 != null && t3 != null) {

                long maxDiff = Math.max(Math.abs(now - t2), Math.max(Math.abs(now - t3), Math.abs(t2 - t3)));
                if (maxDiff <= releaseWindowMs() * 2) {
                    executeTripleDap(player, tp2, tp3);
                    for (ServerPlayer tp : new ServerPlayer[]{player, tp2, tp3}) {
                        UUID tid = tp.getUUID();
                        chargeStartTime.remove(tid); fireLevel.remove(tid); fireStartTime.remove(tid);
                        if (heavenReady.remove(tid)) broadcastHeavenReadyStatus(tp.level().getServer(), tid, false);
                        broadcastChargeCancel(tp);
                    }
                    return;
                }
            }
        }

        ServerPlayer partner = findAnyDapPartner(player);

        if (partner == null) {

            executeWhiff(player);
            chargeStartTime.remove(uuid);
            fireLevel.remove(uuid);
            fireStartTime.remove(uuid);


            if (heavenReady.remove(uuid)) {
                broadcastHeavenReadyStatus(player.level().getServer(), uuid, false);
            }


            broadcastChargeCancel(player);


            long cooldownEnd = now + whiffCooldownMs();
            cooldowns.put(uuid, cooldownEnd);

            broadcastWhiffCooldown(player, cooldownEnd);

            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c✗ Whiff! 0.8s cooldown"));
            return;
        }

        UUID partnerId = partner.getUUID();


        if (HighFiveHandler.hasHandRaised(partnerId) && !chargeStartTime.containsKey(partnerId)) {

            if (DapHoldHandler.tryDetect(player, partner)) {

                chargeStartTime.remove(uuid);
                fireLevel.remove(uuid);
                fireStartTime.remove(uuid);


                if (heavenReady.remove(uuid)) {
                    broadcastHeavenReadyStatus(player.level().getServer(), uuid, false);
                }

                broadcastChargeCancel(player);
                return;
            }



            highFivePartners.add(partnerId);


            executeDap(player, partner, myCharge, myCharge, myFire, myFire, 1.0f, now, now);
            highFivePartners.remove(partnerId);

            HighFiveHandler.handRaisedTime.remove(partnerId);
            HighFiveHandler.startAnimTime.remove(partnerId);
            HighFiveHandler.highFiveCooldown.put(partnerId, now);
            HighFiveHandler.syncHandRaised(partner, false);

            chargeStartTime.remove(uuid);
            fireLevel.remove(uuid);
            fireStartTime.remove(uuid);


            if (heavenReady.remove(uuid)) {
                broadcastHeavenReadyStatus(player.level().getServer(), uuid, false);
            }

            broadcastChargeCancel(player);
            return;
        }


        if (waitingForPartner.containsKey(partnerId) && waitingForPartner.get(partnerId).equals(uuid)) {
            long partnerReleaseTime = releaseTime.get(partnerId);
            float partnerCharge = getChargePercent(partnerId);
            float partnerFire = fireLevel.getOrDefault(partnerId, 0f);

            executeDap(player, partner, myCharge, partnerCharge, myFire, partnerFire, 1.0f, partnerReleaseTime, now);

            waitingForPartner.remove(partnerId);
            releaseTime.remove(partnerId);
            chargeStartTime.remove(uuid);
            chargeStartTime.remove(partnerId);
            fireLevel.remove(uuid);
            fireLevel.remove(partnerId);
            fireStartTime.remove(uuid);
            fireStartTime.remove(partnerId);


            if (heavenReady.remove(uuid)) {
                broadcastHeavenReadyStatus(player.level().getServer(), uuid, false);
            }
            if (heavenReady.remove(partnerId)) {
                broadcastHeavenReadyStatus(partner.level().getServer(), partnerId, false);
            }

            broadcastChargeCancel(player);
            broadcastChargeCancel(partner);

        } else {
            releaseTime.put(uuid, now);
            waitingForPartner.put(uuid, partnerId);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7f, 1.2f);
        }
    }

    private static ServerPlayer findAnyDapPartner(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(DAP_RANGE);
        Vec3 look = player.getViewVector(1.0f);

        for (ServerPlayer other : player.level().players()) {
            if (other == player) continue;
            if (isOnCooldown(other.getUUID())) continue;

            boolean isReady = chargeStartTime.containsKey(other.getUUID()) ||
                    HighFiveHandler.hasHandRaised(other.getUUID());

            if (!isReady) continue;
            if (!searchBox.intersects(other.getBoundingBox())) continue;



            Vec3 toOther = other.position().subtract(player.position()).normalize();
            if (look.dot(toOther) <= 0.0) continue;

            return other;
        }
        return null;
    }

    private static java.util.List<ServerPlayer> findAllDapPartners(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(DAP_RANGE);
        java.util.List<ServerPlayer> result = new java.util.ArrayList<>();
        for (ServerPlayer other : player.level().players()) {
            if (other == player) continue;
            if (isOnCooldown(other.getUUID())) continue;
            if (!chargeStartTime.containsKey(other.getUUID())) continue;
            if (searchBox.intersects(other.getBoundingBox())) result.add(other);
            if (result.size() >= 2) break;
        }
        return result;
    }

    private static void executeTripleDap(ServerPlayer p1, ServerPlayer p2, ServerPlayer p3) {
        ServerLevel world = p1.level();
        long now = System.currentTimeMillis();


        Vec3 center = p1.position().add(p2.position()).add(p3.position()).scale(1.0 / 3.0);
        net.minecraft.world.entity.decoration.ArmorStand stand =
                new net.minecraft.world.entity.decoration.ArmorStand(world, center.x, center.y, center.z);
        stand.setInvisible(true); stand.setNoGravity(true);
        stand.setPermanentlyInvulnerable(true); stand.setSilent(true);
        world.addFreshEntity(stand);

        double radius = 0.7;
        ServerPlayer[] trio = {p1, p2, p3};
        for (int i = 0; i < 3; i++) {

            double angle = Math.PI * 2 * i / 3;
            double px = center.x + radius * Math.cos(angle);
            double pz = center.z + radius * Math.sin(angle);

            float yaw = (float)(-Math.toDegrees(Math.atan2(center.x - px, center.z - pz)));
            trio[i].teleportTo(world, px, p1.getY(), pz, java.util.Set.of(), yaw, 0,false);
            trio[i].setYRot(yaw); trio[i].setYBodyRot(yaw); trio[i].setYHeadRot(yaw);
            trio[i].yBodyRotO = yaw;
            trio[i].swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        }


        stand.discard();


        for (ServerPlayer p : trio) cooldowns.put(p.getUUID(), now + cooldownMs());


        for (ServerPlayer p : trio) {
            PoseNetworking.broadcastAnimState(p,
                    com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
        }


        Vec3 cTop = center.add(0, 1.4, 0);
        world.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                cTop.x, cTop.y, cTop.z, 40, 0.4, 0.4, 0.4, 0.15);
        world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f),
                cTop.x, cTop.y, cTop.z, 3, 0.1, 0.1, 0.1, 0);
        world.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                cTop.x, cTop.y, cTop.z, 15, 0.5, 0.5, 0.5, 0.2);

        world.playSound(null, cTop.x, cTop.y, cTop.z,
                ModSounds.EPIC_DAP, net.minecraft.sounds.SoundSource.PLAYERS, 1.3f, 1.1f);

        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("§6§l⚡ TRIPLE DAP!");
        for (ServerPlayer p : trio) p.sendOverlayMessage(msg);
    }

    private static void executeDap(ServerPlayer p1, ServerPlayer p2,
                                   float charge1, float charge2, float fire1, float fire2,
                                   float syncQuality, long releaseTime1, long releaseTime2) {

        if (DapHoldHandler.isInDapHold(p1.getUUID()) || DapHoldHandler.isInDapHold(p2.getUUID())) {
            return;
        }

        long now = System.currentTimeMillis();
        cooldowns.put(p1.getUUID(), now + cooldownMs());
        cooldowns.put(p2.getUUID(), now + cooldownMs());



        float avgCharge = (charge1 + charge2) / 2.0f;
        float avgFire   = (fire1 + fire2) / 2.0f;

        double speed1 = getMaxRecentSpeed(p1.getUUID());
        double speed2 = getMaxRecentSpeed(p2.getUUID());
        double combinedSpeed = speed1 + speed2;


        long timeDiff = Math.abs(releaseTime1 - releaseTime2);
        boolean perfectHit = timeDiff <= perfectWindowMs();
        boolean bothCharging = chargeStartTime.containsKey(p1.getUUID()) && chargeStartTime.containsKey(p2.getUUID());



        int tier = calculateTier(avgCharge, combinedSpeed, avgFire, fire1, fire2);


        if (tier >= 3 && bothCharging && !perfectHit) {

            if (timeDiff > releaseWindowMs()) {

                return;
            }
        }






        boolean isPerfectDap = (tier >= 3 && bothCharging && perfectHit);
        boolean isHighTierDap = (tier >= 4);
        if (!isPerfectDap && !isHighTierDap) {
            if (!arePlayersFacingEachOther(p1, p2)) {
                p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§lKeep eye contact!"));
                p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§lKeep eye contact!"));
                cooldowns.put(p1.getUUID(), now + 300);
                cooldowns.put(p2.getUUID(), now + 300);

                broadcastChargeCancel(p1);
                broadcastChargeCancel(p2);
                PoseNetworking.broadcastAnimState(p1, 0);
                PoseNetworking.broadcastAnimState(p2, 0);
                return;
            }
        }

        Vec3 pos1 = p1.position();
        Vec3 pos2 = p2.position();
        Vec3 dapPos = pos1.add(pos2).scale(0.5).add(0, 0.5, 0);

        ServerLevel world = p1.level();



        if (NormalFacingDapHandler.isConfirmed(p1.getUUID(), p2.getUUID())) {
            NormalFacingDapHandler.clearConfirm(p1.getUUID(), p2.getUUID());
            NormalFacingDapHandler.start(p1, p2);
            return;
        }


        switch (tier) {
            case 0 -> executeTier0(world, dapPos, p1, p2);
            case 1 -> executeTier1(world, dapPos, p1, p2);
            case 2 -> executeTier2(world, dapPos, p1, p2);
            case 3 -> executeTier3Great(world, dapPos, p1, p2, perfectHit, bothCharging);
            case 4 -> executeTier4Legendary(world, dapPos, p1, p2, perfectHit, bothCharging, speed1, speed2);
            case 5 -> executeTier5FireDap(world, dapPos, p1, p2, perfectHit);
        }


        p1.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        p2.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);


        MahitoTrollHandler.checkForMahitoTroll(p1, p2);




        speedHistory.remove(p1.getUUID());
        speedHistory.remove(p2.getUUID());
        chargeStartTime.remove(p1.getUUID());
        chargeStartTime.remove(p2.getUUID());

        broadcastChargeCancel(p1);
        broadcastChargeCancel(p2);



        boolean facingActive = FacingDapHandler.isActive(p1.getUUID()) || FacingDapHandler.isActive(p2.getUUID());
        if (!facingActive) {
            if (highFivePartners.contains(p1.getUUID())) {
                PoseNetworking.broadcastAnimState(p1,
                        com.cooptest.client.CoopAnimationHandler.AnimState.HIGHFIVE_HIT.ordinal());
                PoseNetworking.broadcastAnimState(p2,
                        com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
                comboCooldown.put(p1.getUUID(), now + 1000);
                comboCooldown.put(p2.getUUID(), now + 1000);
            } else if (highFivePartners.contains(p2.getUUID())) {
                PoseNetworking.broadcastAnimState(p2,
                        com.cooptest.client.CoopAnimationHandler.AnimState.HIGHFIVE_HIT.ordinal());
                PoseNetworking.broadcastAnimState(p1,
                        com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
                comboCooldown.put(p1.getUUID(), now + 1000);
                comboCooldown.put(p2.getUUID(), now + 1000);
            } else {

                int animOrd = tier <= 2
                        ? com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT_BAD.ordinal()
                        : com.cooptest.client.CoopAnimationHandler.AnimState.DAP_HIT.ordinal();
                PoseNetworking.broadcastAnimState(p1, animOrd);
                PoseNetworking.broadcastAnimState(p2, animOrd);
            }
        }


        long particleSpawnTime = System.currentTimeMillis() + 800;
        scheduledParticles.add(new ScheduledParticles(world, dapPos.x, dapPos.y, dapPos.z, particleSpawnTime));


        DapResultPayload result = new DapResultPayload(
                dapPos.x, dapPos.y, dapPos.z,
                p1.getUUID(), p2.getUUID(), tier, perfectHit
        );
        for (ServerPlayer other : PlayerLookup.all(p1.level().getServer())) {
            ServerPlayNetworking.send(other, result);
        }
    }

    private static int calculateTier(float avgCharge, double combinedSpeed, float avgFire, float fire1, float fire2) {


        if (fire1 >= 0.90f && fire2 >= 0.90f) {
            return 5;
        }


        if (avgCharge >= 0.8f && combinedSpeed >= SPEED_TIER_4_THRESHOLD) {
            return 4;
        }


        float chargeScore = avgCharge * 100;
        float speedBonus = 0;
        if (combinedSpeed >= SPEED_BONUS_THRESHOLD) {
            speedBonus = (float)((combinedSpeed - SPEED_BONUS_THRESHOLD) / (SPEED_TIER_4_THRESHOLD - SPEED_BONUS_THRESHOLD) * 30);
        }
        float finalScore = chargeScore + speedBonus;

        if (finalScore >= 100) return 3;
        if (finalScore >= 70) return 2;
        if (finalScore >= 40) return 1;
        return 0;
    }

    private static void executeFizzle(ServerPlayer p1, ServerPlayer p2) {
        ServerLevel world = p1.level();
        Vec3 pos = p1.position().add(p2.position()).scale(0.5).add(0, 1.4, 0);


        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8f, 0.5f);


        world.sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 12, 0.4, 0.3, 0.4, 0.03);
        world.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.02);

        p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§7*missed!* timing off..."));
        p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§7*missed!* timing off..."));

        chargeStartTime.remove(p1.getUUID());
        chargeStartTime.remove(p2.getUUID());
        fireLevel.remove(p1.getUUID());
        fireLevel.remove(p2.getUUID());


        if (heavenReady.remove(p1.getUUID())) {
            broadcastHeavenReadyStatus(p1.level().getServer(), p1.getUUID(), false);
        }
        if (heavenReady.remove(p2.getUUID())) {
            broadcastHeavenReadyStatus(p2.level().getServer(), p2.getUUID(), false);
        }

        broadcastChargeCancel(p1);
        broadcastChargeCancel(p2);


        PoseNetworking.broadcastAnimState(p1, 0);
        PoseNetworking.broadcastAnimState(p2, 0);


        UUID p1Id = p1.getUUID();
        UUID p2Id = p2.getUUID();
        for (ServerPlayer p : p1.level().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(p1Id, false));
            ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(p2Id, false));
        }


        long now = System.currentTimeMillis();
        cooldowns.put(p1.getUUID(), now + 500);
        cooldowns.put(p2.getUUID(), now + 500);
    }

    public static void executeWhiff(ServerPlayer player) {
        ServerLevel world = player.level();


        Vec3 pos = player.position().add(0, 1.4, 0);
        Vec3 look = player.getLookAngle();
        pos = pos.add(look.scale(0.5));


        if (Math.random() < 0.1) {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_MISS, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else {
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.5f, 1.5f);
        }


        world.sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.02);
        world.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 5, 0.15, 0.15, 0.15, 0.01);


        setBlockingAnimation(player.getUUID(), 330);


        PoseNetworking.broadcastAnimState(player, 0);


        UUID playerId = player.getUUID();
        for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new FireDapFirstPersonPayload(playerId, false));
        }


        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§7*whoosh*"));
    }


    private static void executeTier0(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();


        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,
                DapSession.DapType.NORMAL_DAP
        );

        if (session == null) {

            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 0);
            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§7Weak dap..."));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§7Weak dap..."));
            return;
        }


        session.onComplete(() -> {

            new Thread(() -> {
                try { Thread.sleep(710); } catch (InterruptedException ignored) {}
                world.getServer().execute(() -> {
                    Vec3 midpoint = p1.position().add(p2.position()).scale(0.5).add(0, 1.3, 0);
                    world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                            SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.9f, 1.1f);
                    spawnPrecisionDapParticles(world, midpoint, 0);
                });
            }).start();

            new Thread(() -> {
                try { Thread.sleep(1667); } catch (InterruptedException ignored) {}
                world.getServer().execute(() -> {
                    PoseNetworking.broadcastAnimState(p1, 0);
                    PoseNetworking.broadcastAnimState(p2, 0);
                });
            }).start();
            applyKnockback(p1, p2, pos, 0.1);
        });
    }


    private static void executeTier1(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();


        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,
                DapSession.DapType.NORMAL_DAP
        );

        if (session == null) {

            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 1);
            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§e✋ Decent Dap!"));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§e✋ Decent Dap!"));
            return;
        }

        session.onComplete(() -> {

            new Thread(() -> {
                try { Thread.sleep(710); } catch (InterruptedException ignored) {}
                world.getServer().execute(() -> {
                    Vec3 midpoint = p1.position().add(p2.position()).scale(0.5).add(0, 1.3, 0);
                    world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                            SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.9f, 1.1f);
                    spawnPrecisionDapParticles(world, midpoint, 1);
                });
            }).start();

            new Thread(() -> {
                try { Thread.sleep(1667); } catch (InterruptedException ignored) {}
                world.getServer().execute(() -> {
                    PoseNetworking.broadcastAnimState(p1, 0);
                    PoseNetworking.broadcastAnimState(p2, 0);
                });
            }).start();
            applyKnockback(p1, p2, pos, 0.3);
        });
    }


    private static void executeTier2(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();


        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,
                DapSession.DapType.NORMAL_DAP
        );

        if (session == null) {

            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 2);
            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§a✋ Good Dap! ✋"));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§a✋ Good Dap! ✋"));
            return;
        }

        session.onComplete(() -> {

            new Thread(() -> {
                try { Thread.sleep(710); } catch (InterruptedException ignored) {}
                world.getServer().execute(() -> {
                    Vec3 midpoint = p1.position().add(p2.position()).scale(0.5).add(0, 1.3, 0);
                    world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                            SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.9f, 1.1f);
                    spawnPrecisionDapParticles(world, midpoint, 2);
                });
            }).start();

            new Thread(() -> {
                try { Thread.sleep(1667); } catch (InterruptedException ignored) {}
                world.getServer().execute(() -> {
                    PoseNetworking.broadcastAnimState(p1, 0);
                    PoseNetworking.broadcastAnimState(p2, 0);
                });
            }).start();
            applyKnockback(p1, p2, pos, 0.6);
        });
    }


    private static void executeTier3Great(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2,
                                          boolean perfectHit, boolean bothCharging) {
        if (bothCharging && perfectHit) {

            rotateBothPlayersToFaceEachOther(p1, p2);



            if (FacingDapHandler.areFacingEachOther(p1, p2) && !FacingDapHandler.isActive(p1.getUUID())) {
                FacingDapHandler.start(p1, p2);
                return;
            }


            long now = System.currentTimeMillis();
            UUID id1 = p1.getUUID();
            UUID id2 = p2.getUUID();



            DapSession session = DapSessionManager.createSession(
                    id1, id2,
                    1.5,
                    DapSession.DapType.PERFECT_DAP
            );

            if (session == null) {
                executeTier3Normal(world, pos, p1, p2);
                return;
            }


            perfectDapStartTime.put(id1, now);
            perfectDapStartTime.put(id2, now);
            perfectDapPartner.put(id1, id2);
            perfectDapPartner.put(id2, id1);


            session.onComplete(() -> {
                startPerfectDapTier3Animation(world, pos, p1, p2);
            });


        } else {
            executeTier3Normal(world, pos, p1, p2);
        }
    }

    private static void startPerfectDapTier3Animation(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        long now = System.currentTimeMillis();
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();



        Vec3 diff12 = p2.position().subtract(p1.position());
        float yaw1 = (float)(Math.toDegrees(Math.atan2(diff12.z, diff12.x))) - 90f;
        float yaw2 = yaw1 + 180f;
        p1.setYRot(yaw1); p1.setYBodyRot(yaw1); p1.setYHeadRot(yaw1);
        p2.setYRot(yaw2); p2.setYBodyRot(yaw2); p2.setYHeadRot(yaw2);
        p1.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        p2.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);


        Vec3 p1Hand = p1.position().add(0, 1.4, 0);
        Vec3 p2Hand = p2.position().add(0, 1.4, 0);
        Vec3 handMid = p1Hand.add(p2Hand).scale(0.5);






        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_HIT.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_HIT.ordinal());


        perfectDapFreezeEnd.put(id1, now + 1290);
        perfectDapFreezeEnd.put(id2, now + 1290);


        ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(true));


        setBlockingAnimation(id1, 1625);
        setBlockingAnimation(id2, 1625);



        long effectTime = now + 150;
        scheduledPerfectDapEffects.add(new ScheduledPerfectDapEffect(
                world, pos, p1, p2, effectTime
        ));

        p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l✋ PERFECT GREAT DAP! ✋"));
        p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l✋ PERFECT GREAT DAP! ✋"));


        DapSessionManager.removeSession(id1);
    }

    private static void executeTier3Normal(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();


        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.4,
                DapSession.DapType.NORMAL_DAP
        );

        if (session == null) {

            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            spawnPrecisionDapParticles(world, pos, 3);
            world.sendParticles((ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)), pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            createExplosion(world, pos, p1, p2, 3.5, 6.0f);
            applyKnockback(p1, p2, pos, 1.0);
            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l✋ GREAT DAP! ✋"));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l✋ GREAT DAP! ✋"));
            return;
        }


        session.onComplete(() -> {
            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0f, 1.0f);

            spawnPrecisionDapParticles(world, pos, 3);
            world.sendParticles((ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)), pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            createExplosion(world, pos, p1, p2, 3.5, 6.0f);
            applyKnockback(p1, p2, pos, 1.0);
            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l✋ GREAT DAP! ✋"));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6§l✋ GREAT DAP! ✋"));


            if (CoopMovesConfig.get().enableDapCombo) DapComboChain.startCombo(p1, p2, pos);
        });
    }

    private static void startStage2Extender(ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        ServerLevel world = p1.level();
        long now = System.currentTimeMillis();




        ServerPlayNetworking.send(p1, new QTEClearPayload(id1));
        ServerPlayNetworking.send(p2, new QTEClearPayload(id2));


        ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(true));


        perfectDapFreezeEnd.put(id1, now + 4500);
        perfectDapFreezeEnd.put(id2, now + 4500);


        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P1.ordinal());


        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P2.ordinal());

        p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§d§l★ EXTENDER DAP! ★"));
        p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§d§l★ EXTENDER DAP! ★"));




        new Thread(() -> {
            try {
                Thread.sleep(4500);
                world.getServer().execute(() -> {


                    ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(false));
                    ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(false));


                    perfectDapFreezeEnd.remove(id1);
                    perfectDapFreezeEnd.remove(id2);


                    PoseNetworking.broadcastAnimState(p1,
                            com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
                    PoseNetworking.broadcastAnimState(p2,
                            com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());


                    HighFiveHandler.cleanup(id1);
                    HighFiveHandler.cleanup(id2);


                    long cooldownTime = System.currentTimeMillis() + 1000;
                    comboCooldown.put(id1, cooldownTime);
                    comboCooldown.put(id2, cooldownTime);

                });
            } catch (InterruptedException e) {}
        }).start();
    }

    public static boolean isInQTE(UUID playerId) {
        return QTEManager.isInQTE(playerId) || DapComboChain.isInCombo(playerId);
    }


    private static long tickSpeedRestoreTime = 0;


    private static void executeTier4Legendary(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2,
                                              boolean perfectHit, boolean bothCharging, double speed1, double speed2) {
        var server = world.getServer();






        UUID p1Id = p1.getUUID();
        UUID p2Id = p2.getUUID();
        boolean bothHeavenReady = heavenReady.contains(p1Id) && heavenReady.contains(p2Id);

        if (bothHeavenReady && perfectHit) {



            startHeavenDap(p1, p2, pos, world);


            new Thread(() -> {
                try {
                    Thread.sleep(500);

                    world.getServer().execute(() -> {


                        p1.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.RESISTANCE, 80, 255, false, false));
                        p2.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.RESISTANCE, 80, 255, false, false));
                        boolean _d = !CoopMovesConfig.get().noGriefMode;
                        for (int i = 0; i < 8; i++) {
                            double a = Math.toRadians(i * 45.0);
                            for (double dy : new double[]{0, 20, -20}) {
                                world.explode(null,
                                        pos.x + Math.cos(a) * 5, pos.y + dy,
                                        pos.z + Math.sin(a) * 5, 8f,
                                        false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                            }
                        }
                        new Thread(() -> {
                            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                            world.getServer().execute(() -> {
                                for (int i = 0; i < 8; i++) {
                                    double a = Math.toRadians(i * 45.0 + 22.5);
                                    for (double dy : new double[]{0, 20, -20}) {
                                        world.explode(null,
                                                pos.x + Math.cos(a) * 25, pos.y + dy,
                                                pos.z + Math.sin(a) * 25, 20f,
                                                _d, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                                    }
                                }
                            });
                        }).start();
                        new Thread(() -> {
                            try { Thread.sleep(350); } catch (InterruptedException ignored) {}
                            world.getServer().execute(() -> {
                                for (int i = 0; i < 8; i++) {
                                    double a = Math.toRadians(i * 45.0);
                                    for (double dy : new double[]{0, 20, -20}) {
                                        world.explode(null,
                                                pos.x + Math.cos(a) * 60, pos.y + dy,
                                                pos.z + Math.sin(a) * 60, 15f,
                                                _d, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                                    }
                                }
                            });
                        }).start();
                        new Thread(() -> {
                            try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                            world.getServer().execute(() -> {
                                for (int i = 0; i < 8; i++) {
                                    double a = Math.toRadians(i * 45.0 + 22.5);
                                    world.explode(null,
                                            pos.x + Math.cos(a) * 100, pos.y,
                                            pos.z + Math.sin(a) * 100, 12f,
                                            _d, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                                }
                            });
                        }).start();



                        spawnExpandingLegendarySonicBoom(world, pos);


                        activeHeavenParticles.add(new HeavenParticleSpawner(world, pos, 30000));


                        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 20, 5, 5, 5, 0);
                        world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 300, 10, 10, 10, 0.5);
                        world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 200, 8, 8, 8, 0.4);
                        world.sendParticles((ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)), pos.x, pos.y, pos.z, 10, 0, 0, 0, 0);


                        spawnStarBurst(world, pos, 100, 5.0);


                        createMassiveShockwave(world, pos, p1, p2, 50.0, 10.0);


                        activeSaturnRings.add(new SaturnRing(pos, System.currentTimeMillis()));

                    });

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

        } else if (bothCharging) {




            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.EPIC_DAP, SoundSource.PLAYERS, 2.0f, 0.5f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 0.7f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 2.0f, 0.8f);


            spawnStarBurst(world, pos, 40, 1.5);
            world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 3, 1, 1, 1, 0);
            world.sendParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 50, 0.5, 0.5, 0.5, 0.2);
            world.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 100, 1.0, 1.0, 1.0, 0.1);


            world.explode(null, pos.x, pos.y, pos.z, 6.0f,
                    !CoopMovesConfig.get().noGriefMode,
                    net.minecraft.world.level.Level.ExplosionInteraction.MOB);


            removeTotem(p1);
            removeTotem(p2);


            p1.setHealth(0);
            p2.setHealth(0);
            p1.die(world.damageSources().magic());
            p2.die(world.damageSources().magic());

            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§4§l☠ THE POWER WAS TOO GREAT! ☠"));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§4§l☠ THE POWER WAS TOO GREAT! ☠"));


            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§4§l☠ " + p1.getName().getString() + " §7and §4" + p2.getName().getString() +
                                " §7failed to achieve Perfect Friendship... §c§lTHEY PERISHED!"
                ), false);
            }
        } else {

            world.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.EPIC_DAP, SoundSource.PLAYERS, 2.0f, 0.9f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 1.0f);
            world.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.0f, 0.9f);

            spawnStarBurst(world, pos, 30, 1.0);
            world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.5, 0.5, 0.5, 0.25);


            world.explode(null, pos.x, pos.y, pos.z, 5.0f,
                    !CoopMovesConfig.get().noGriefMode,
                    net.minecraft.world.level.Level.ExplosionInteraction.MOB);

            applyKnockback(p1, p2, pos, 2.0);

            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§d§l⚡ LEGENDARY DAP! ⚡"));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§d§l⚡ LEGENDARY DAP! ⚡"));
        }
    }

    private static void removeTotem(ServerPlayer player) {

        if (player.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
            player.getMainHandItem().setCount(0);
        }

        if (player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
            player.getOffhandItem().setCount(0);
        }
    }

    public static void checkTickSpeedRestore(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();


        if (tickSpeedRestoreTime > 0 && now >= tickSpeedRestoreTime) {

            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "tick rate 20"
            );
            tickSpeedRestoreTime = 0;


            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Time returns to normal..."));
            }
        }


        Iterator<Map.Entry<UUID, Long>> it = perfectFriendshipLevitation.entrySet().iterator();
        Set<UUID> processed = new HashSet<>();

        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID playerId = entry.getKey();
            long endTime = entry.getValue();

            if (now >= endTime && !processed.contains(playerId)) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                UUID partnerId = perfectFriendshipPartner.get(playerId);
                ServerPlayer partner = partnerId != null ? server.getPlayerList().getPlayer(partnerId) : null;

                if (player != null) {

                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, true));
                    processed.add(playerId);
                }


                if (player != null && partner != null && !processed.contains(partnerId)) {

                    partner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, true));
                    processed.add(partnerId);


                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "§d§l✨ " + player.getName().getString() + " §7and §d§l" + partner.getName().getString() +
                                        " §7have achieved §b§lPERFECT FRIENDSHIP§7! §d§l✨"
                        ), false);
                    }
                }

                it.remove();
            }
        }


        for (UUID id : processed) {
            perfectFriendshipPartner.remove(id);
            perfectFriendshipLevitation.remove(id);
        }
    }

    public static void startHeavenDap(ServerPlayer p1, ServerPlayer p2, Vec3 midpoint, ServerLevel world) {
        long now = System.currentTimeMillis();
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();





        ServerPlayNetworking.send(p1, new HeavenDapPayloads.HeavenImpactPayload());
        ServerPlayNetworking.send(p2, new HeavenDapPayloads.HeavenImpactPayload());


        ServerPlayNetworking.send(p1, new PerfectDapImpactFramePayload(1));
        ServerPlayNetworking.send(p2, new PerfectDapImpactFramePayload(1));


        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.EPIC_DAP, SoundSource.PLAYERS, 3.0f, 1.2f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 2.0f, 1.0f);















        final double RING_1_RADIUS   = 100;
        final double RING_2_RADIUS   = 25;
        final double RING_3_RADIUS   = 60;
        final double RING_4_RADIUS   = 100;
        final float  RING_1_POWER    = 8f;
        final float  RING_2_POWER    = 20f;
        final float  RING_3_POWER    = 15f;
        final float  RING_4_POWER    = 12f;
        final double VERTICAL_SPREAD = 200;


        p1.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.RESISTANCE, 80, 255, false, false));
        p2.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.RESISTANCE, 80, 255, false, false));


        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0);
            for (double dy : new double[]{0, VERTICAL_SPREAD, -VERTICAL_SPREAD}) {
                world.explode(null,
                        midpoint.x + Math.cos(a) * RING_1_RADIUS, midpoint.y + dy,
                        midpoint.z + Math.sin(a) * RING_1_RADIUS, RING_1_POWER,
                        false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }
        }

        new Thread(() -> {
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            world.getServer().execute(() -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0 + 22.5);
                    for (double dy : new double[]{0, VERTICAL_SPREAD, -VERTICAL_SPREAD}) {
                        world.explode(null,
                                midpoint.x + Math.cos(a) * RING_2_RADIUS, midpoint.y + dy,
                                midpoint.z + Math.sin(a) * RING_2_RADIUS, RING_2_POWER,
                                true, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                    }
                }
            });
        }).start();

        new Thread(() -> {
            try { Thread.sleep(350); } catch (InterruptedException ignored) {}
            world.getServer().execute(() -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0);
                    for (double dy : new double[]{0, VERTICAL_SPREAD, -VERTICAL_SPREAD}) {
                        world.explode(null,
                                midpoint.x + Math.cos(a) * RING_3_RADIUS, midpoint.y + dy,
                                midpoint.z + Math.sin(a) * RING_3_RADIUS, RING_3_POWER,
                                true, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                    }
                }
            });
        }).start();

        new Thread(() -> {
            try { Thread.sleep(600); } catch (InterruptedException ignored) {}
            world.getServer().execute(() -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0 + 22.5);
                    world.explode(null,
                            midpoint.x + Math.cos(a) * RING_4_RADIUS, midpoint.y,
                            midpoint.z + Math.sin(a) * RING_4_RADIUS, RING_4_POWER,
                            true, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                }
            });
        }).start();


        spawnSonicBoomCircles(world, midpoint);


        world.sendParticles((ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)), midpoint.x, midpoint.y, midpoint.z, 5, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, midpoint.x, midpoint.y, midpoint.z, 3, 0.5, 0.5, 0.5, 0);
        world.sendParticles(ParticleTypes.END_ROD, midpoint.x, midpoint.y, midpoint.z, 50, 1.0, 1.0, 1.0, 0.3);
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, midpoint.x, midpoint.y, midpoint.z, 40, 0.8, 0.8, 0.8, 0.2);




        heavenPlayers.put(id1, new HeavenDapData(midpoint, world, now, id2));
        heavenPlayers.put(id2, new HeavenDapData(midpoint, world, now, id1));


        new Thread(() -> {
            try {
                Thread.sleep(700);

                p1.level().getServer().execute(() -> {
                    if (p1.isRemoved() || p2.isRemoved()) return;



                    double heavenY = 500.0;
                    Vec3 heavenMid = new Vec3(midpoint.x, heavenY, midpoint.z);


                    Vec3 dir = p2.position().subtract(p1.position()).normalize();


                    Vec3 pos1 = heavenMid.add(dir.scale(-2.5));
                    Vec3 pos2 = heavenMid.add(dir.scale(2.5));


                    double dx = pos2.x - pos1.x;
                    double dz = pos2.z - pos1.z;
                    float yaw1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                    float yaw2 = yaw1 + 180;


                    p1.teleportTo(world, pos1.x, pos1.y, pos1.z, java.util.Set.of(), yaw1, 0.0f, false);
                    p2.teleportTo(world, pos2.x, pos2.y, pos2.z, java.util.Set.of(), yaw2, 0.0f, false);

                    p1.stopFallFlying();
                    p2.stopFallFlying();
                    p1.setDeltaMovement(Vec3.ZERO);
                    p2.setDeltaMovement(Vec3.ZERO);
                    p1.syncVelocity = true;
                    p2.syncVelocity = true;


                    ServerPlayNetworking.send(p1, new PerfectDapFreezePayload(true));
                    ServerPlayNetworking.send(p2, new PerfectDapFreezePayload(true));


                    PoseNetworking.broadcastAnimState(p1,
                            com.cooptest.client.CoopAnimationHandler.AnimState.HEAVEN_DAP.ordinal());
                    PoseNetworking.broadcastAnimState(p2,
                            com.cooptest.client.CoopAnimationHandler.AnimState.HEAVEN_DAP.ordinal());


                    p1.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, false));
                    p2.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, false));


                    p1.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 40, 0, false, false));
                    p2.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 40, 0, false, false));


                    ServerPlayNetworking.send(p1, new HeavenDapPayloads.HeavenDapStartPayload());
                    ServerPlayNetworking.send(p2, new HeavenDapPayloads.HeavenDapStartPayload());

                });

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void spawnSonicBoomCircles(ServerLevel world, Vec3 pos) {

        new Thread(() -> {
            try {
                for (int circle = 0; circle < 3; circle++) {
                    final int circleNum = circle;
                    final double baseRadius = 2.0 + (circle * 2.0);

                    world.getServer().execute(() -> {

                        for (int i = 0; i < 60; i++) {
                            double angle = Math.toRadians(i * 6);
                            double x = pos.x + Math.cos(angle) * baseRadius;
                            double z = pos.z + Math.sin(angle) * baseRadius;


                            world.sendParticles(ParticleTypes.WHITE_ASH,
                                    x, pos.y + 0.5, z,
                                    3, 0.1, 0.3, 0.1, 0.05);


                            world.sendParticles(ParticleTypes.CLOUD,
                                    x, pos.y + 0.5, z,
                                    2, 0.05, 0.2, 0.05, 0.02);
                        }
                    });

                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void spawnPerfectDapSonicBoom(ServerLevel world, Vec3 center) {

        spawnExpandingRing(world, center, 0.5, 3.0, 200);
    }

    private static void spawnFireDapSonicBoom(ServerLevel world, Vec3 pos) {
        new Thread(() -> {
            try {

                spawnExpandingFireRing(world, pos, 2.0, 10.0, 250);
                Thread.sleep(100);


                spawnExpandingFireRing(world, pos, 5.0, 15.0, 350);


            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingFireRing(ServerLevel world, Vec3 center, double startRadius, double endRadius, int durationMs) {
        int steps = 15;
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;

        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    double radius = startRadius + (radiusStep * step);
                    final double currentRadius = radius;

                    world.getServer().execute(() -> {
                        int particleCount = (int) (currentRadius * 15);
                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians((360.0 / particleCount) * i);

                            double x = center.x + Math.cos(angle) * currentRadius;
                            double z = center.z + Math.sin(angle) * currentRadius;
                            double y = center.y + 0.5;


                            double vx = Math.cos(angle) * 0.35;
                            double vz = Math.sin(angle) * 0.35;


                            world.sendParticles(ParticleTypes.FLAME,
                                    x, y, z, 0, vx, 0.0, vz, 0.5);
                            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                    x, y, z, 0, vx * 0.8, 0.0, vz * 0.8, 0.4);


                            if (i % 3 == 0) {
                                world.sendParticles(ParticleTypes.LAVA,
                                        x, y, z, 1, 0, 0, 0, 0);
                            }
                        }
                    });

                    Thread.sleep(stepDuration);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingLegendarySonicBoom(ServerLevel world, Vec3 center) {
        new Thread(() -> {
            try {

                spawnExpandingRing(world, center, 1.0, 6.0, 300);
                Thread.sleep(200);


                spawnExpandingRing(world, center, 6.0, 15.0, 400);
                Thread.sleep(200);


                spawnExpandingRing(world, center, 15.0, 30.0, 500);


            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingRing(ServerLevel world, Vec3 center, double startRadius, double endRadius, int durationMs) {
        int steps = 20;
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;

        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    double radius = startRadius + (radiusStep * step);
                    final double currentRadius = radius;

                    world.getServer().execute(() -> {

                        int particleCount = (int) (currentRadius * 20);
                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians((360.0 / particleCount) * i);


                            double x = center.x + Math.cos(angle) * currentRadius;
                            double z = center.z + Math.sin(angle) * currentRadius;
                            double y = center.y + 0.5;


                            double vx = Math.cos(angle) * 0.4;
                            double vz = Math.sin(angle) * 0.4;


                            world.sendParticles(ParticleTypes.CLOUD,
                                    x, y, z, 0, vx, 0.0, vz, 0.6);
                            world.sendParticles(ParticleTypes.WHITE_ASH,
                                    x, y, z, 0, vx, 0.0, vz, 0.5);


                            if (i % (particleCount / 24) == 0) {
                                world.sendParticles(ParticleTypes.END_ROD,
                                        x, y, z, 1, 0, 0, 0, 0);
                            }
                        }
                    });

                    Thread.sleep(stepDuration);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void createMassiveShockwave(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2,
                                               double radius, double strength) {
        AABB shockwaveBox = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );


        for (Entity entity : world.getEntities(null, shockwaveBox)) {
            if (entity == p1 || entity == p2) continue;

            double dist = entity.position().distanceTo(pos);
            if (dist > radius || dist < 0.5) continue;


            double knockbackStrength = (1.0 - dist / radius) * strength;
            Vec3 knockDir = entity.position().subtract(pos).normalize();

            entity.push(
                    knockDir.x * knockbackStrength * 2.0,
                    knockbackStrength * 1.5,
                    knockDir.z * knockbackStrength * 2.0
            );
            entity.syncVelocity = true;
        }


        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0f, 0.5f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 3.0f, 0.6f);
    }


    private static void executeTier5FireDap(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2,
                                            boolean perfectHit) {


        UUID p1Id = p1.getUUID(), p2Id = p2.getUUID();
        boolean bothHeavenReady  = heavenReady.contains(p1Id) && heavenReady.contains(p2Id);
        double  speed1           = getMaxRecentSpeed(p1Id);
        double  speed2           = getMaxRecentSpeed(p2Id);
        boolean bothMovingFast   = speed1 >= PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED
                && speed2 >= PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED;

        if (bothHeavenReady && bothMovingFast) {
            startHeavenDap(p1, p2, pos, world);
            return;
        }



        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EPIC_DAP, SoundSource.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.FIRE_IMPACT, SoundSource.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 1.3f);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.8f);


        spawnFireDapSonicBoom(world, pos);



        Random rand = new Random();
        int sphereParticles = 24;
        for (int i = 0; i < sphereParticles; i++) {

            double theta = rand.nextDouble() * 2 * Math.PI;
            double phi = Math.acos(2 * rand.nextDouble() - 1);
            double radius = 0.5 + rand.nextDouble() * 0.5;


            double dx = Math.sin(phi) * Math.cos(theta) * radius;
            double dy = Math.cos(phi) * radius;
            double dz = Math.sin(phi) * Math.sin(theta) * radius;


            double speed = 0.15 + rand.nextDouble() * 0.15;
            world.sendParticles(ParticleTypes.FLAME,
                    pos.x + dx, pos.y + dy + 1.0, pos.z + dz,
                    1, dx * speed, dy * speed, dz * speed, 0.1);
        }


        world.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.0, pos.z, 30, 0.3, 0.3, 0.3, 0.2);
        world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z, 15, 0.2, 0.2, 0.2, 0.15);
        world.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 1.0, pos.z, 8, 0.3, 0.3, 0.3, 0);
        world.sendParticles((ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)), pos.x, pos.y + 1.0, pos.z, 3, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 20, 0.5, 0.5, 0.5, 0.15);
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 25, 0.5, 0.5, 0.5, 0.3);


        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5);
            for (double r = 1; r <= 2.5; r += 0.5) {
                double ringX = Math.cos(angle) * r;
                double ringZ = Math.sin(angle) * r;
                world.sendParticles(ParticleTypes.FLAME, pos.x + ringX, pos.y, pos.z + ringZ, 1, 0.05, 0.1, 0.05, 0.02);
            }
        }


        createFireShockwave(world, pos, p1, p2);

        if (perfectHit) {



            world.sendParticles((ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)), pos.x, pos.y, pos.z, 40, 0.8, 0.8, 0.8, 0.15);
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 50, 0.6, 0.6, 0.6, 0.3);

            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🔥 PERFECT FIRE DAP! 🔥"));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🔥 PERFECT FIRE DAP! 🔥"));
        } else {

            p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🔥 FIRE DAP! 🔥"));
            p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🔥 FIRE DAP! 🔥"));
        }


        startFireDap(p1, p2, pos);


        DapFusionHandler.openFusionWindow(p1, p2);

        new Thread(() -> {
            try { Thread.sleep(FUSION_G_WINDOW_START_MS); } catch (InterruptedException ignored) {}
            p1.level().getServer().execute(() -> {
                if (inFireDapHit.getOrDefault(p1.getUUID(), false)) {
                    p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6Press §lG §r§6to FUSE  §7|  §cPress §lJ §r§cfor Fire Combo"));
                    p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§6Press §lG §r§6to FUSE  §7|  §cPress §lJ §r§cfor Fire Combo"));
                }
            });
        }).start();


        for (ServerPlayer nearby : PlayerLookup.around(world, pos, 50)) {
            if (nearby != p1 && nearby != p2) {
                String prefix = perfectHit ? "§c§lPERFECT " : "§c§l";
                nearby.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        prefix + "🔥 " + p1.getName().getString() + " §7and §c" + p2.getName().getString() +
                                " §7unleashed a §c§lFIRE DAP§7!"
                ), false);
            }
        }
    }




    private static void spawnPrecisionDapParticles(ServerLevel world, Vec3 pos, int tier) {

        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, world);
        stand.snapTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setPermanentlyInvulnerable(true);
        stand.setCustomNameVisible(false);


        world.addFreshEntity(stand);


        Vec3 exactPos = stand.position().add(0, 1.0, 0);


        int particleCount = 15 + (tier * 5);


        world.sendParticles(
                ParticleTypes.CRIT,
                exactPos.x, exactPos.y, exactPos.z,
                particleCount,
                0.3, 0.3, 0.3,
                0.15
        );


        if (tier >= 3) {
            world.sendParticles(
                    ParticleTypes.ENCHANTED_HIT,
                    exactPos.x, exactPos.y, exactPos.z,
                    particleCount / 2,
                    0.4, 0.4, 0.4,
                    0.2
            );
        }


        new Thread(() -> {
            try {
                Thread.sleep(50);
                world.getServer().execute(() -> stand.discard());
            } catch (InterruptedException e) {
                world.getServer().execute(() -> stand.discard());
            }
        }).start();
    }


    private static float calculateYawToFace(ServerPlayer from, ServerPlayer to) {
        Vec3 fromPos = from.position();
        Vec3 toPos = to.position();
        double dx = toPos.x - fromPos.x;
        double dz = toPos.z - fromPos.z;
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return (float) angle;
    }

    private static void smoothRotateToFacePartner(ServerPlayer player, ServerPlayer partner) {
        float targetYaw = calculateYawToFace(player, partner);
        float currentYaw = player.getYRot();


        targetYaw = ((targetYaw % 360) + 540) % 360 - 180;
        currentYaw = ((currentYaw % 360) + 540) % 360 - 180;


        float diff = targetYaw - currentYaw;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;


        final float finalCurrentYaw = currentYaw;
        final float finalDiff = diff;


        final int steps = 10;
        final long delayPerStep = 50;

        new Thread(() -> {
            for (int i = 1; i <= steps; i++) {
                final int step = i;
                try {
                    Thread.sleep(delayPerStep);
                    player.level().getServer().execute(() -> {
                        float progress = (float) step / steps;
                        float newYaw = finalCurrentYaw + (finalDiff * progress);
                        player.setYRot(newYaw);

                        player.connection.send(new ClientboundTeleportEntityPacket(player.getId(), net.minecraft.world.entity.PositionMoveRotation.of(player), java.util.Set.of(), player.onGround()));
                    });
                } catch (InterruptedException e) { break; }
            }
        }).start();
    }

    private static void rotateBothPlayersToFaceEachOther(ServerPlayer p1, ServerPlayer p2) {
        smoothRotateToFacePartner(p1, p2);
        smoothRotateToFacePartner(p2, p1);
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

        p1.setDeltaMovement(0, 0, 0);
        p2.setDeltaMovement(0, 0, 0);

        p1.syncVelocity = true;
        p2.syncVelocity = true;
    }

    public static void applyImpactFreeze(ServerPlayer p1, ServerPlayer p2, int ticks) {
        if (ticks <= 0) return;


        p1.setDeltaMovement(0, 0, 0);
        p2.setDeltaMovement(0, 0, 0);
        p1.syncVelocity = true;
        p2.syncVelocity = true;


        impactFreezeTicks.put(p1.getUUID(), ticks);
        impactFreezeTicks.put(p2.getUUID(), ticks);
    }

    private static void createExplosion(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2,
                                        double radius, float maxDamage) {
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
            entity.syncVelocity = true;

            if (entity instanceof ServerPlayer target) {
                float damage = (float)((1.0 - dist / radius) * maxDamage);
                target.hurtClient(world.damageSources().explosion(null));
            }
        }
    }

    private static void createShockwave(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2,
                                        double radius, double strength) {
        AABB shockwaveBox = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );


        for (int i = 0; i < 36; i++) {
            double angle = (i / 36.0) * Math.PI * 2;
            for (double r = 1; r <= radius; r += 2) {
                double px = pos.x + Math.cos(angle) * r;
                double pz = pos.z + Math.sin(angle) * r;
                world.sendParticles(ParticleTypes.CLOUD, px, pos.y, pz, 1, 0, 0.1, 0, 0.02);
                world.sendParticles(ParticleTypes.SWEEP_ATTACK, px, pos.y + 0.5, pz, 1, 0, 0, 0, 0);
            }
        }


        for (Entity entity : world.getEntities(null, shockwaveBox)) {
            if (entity == p1 || entity == p2) continue;

            double dist = entity.position().distanceTo(pos);
            if (dist > radius || dist < 0.5) continue;


            double knockbackStrength = (1.0 - dist / radius) * strength;
            Vec3 knockDir = entity.position().subtract(pos).normalize();


            entity.push(
                    knockDir.x * knockbackStrength * 1.5,
                    knockbackStrength * 0.6,
                    knockDir.z * knockbackStrength * 1.5
            );
            entity.syncVelocity = true;


            if (entity instanceof ServerPlayer target) {
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.8f);
            }
        }


        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    private static void handleUnderwaterPerfectDap(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {

        if (!p1.isUnderWater() && !p2.isUnderWater()) {
            return;
        }



        p1.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WATER_BREATHING, 120, 0, false, false));
        p2.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.WATER_BREATHING, 120, 0, false, false));


        world.sendParticles(ParticleTypes.SPLASH,
                pos.x, pos.y, pos.z,
                80, 2.0, 2.0, 2.0, 0.4);
        world.sendParticles(ParticleTypes.BUBBLE_POP,
                pos.x, pos.y, pos.z,
                40, 1.5, 1.5, 1.5, 0.3);


        UUID trackId = UUID.randomUUID();
        underwaterRemovalStart.put(trackId, System.currentTimeMillis());
        underwaterRemovalPos.put(trackId, pos);
        underwaterRemovalWorld.put(trackId, world);

    }

    private static void createLegendaryExplosion(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        double radius = 6.0;
        AABB damageBox = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        for (Entity entity : world.getEntities(null, damageBox)) {
            if (entity == p1 || entity == p2) continue;

            double dist = entity.position().distanceTo(pos);
            if (dist > radius) continue;

            double knockbackStrength = (1.0 - dist / radius) * 3.0;
            Vec3 knockDir = entity.position().subtract(pos).normalize();
            entity.push(knockDir.x * knockbackStrength, knockbackStrength * 0.7, knockDir.z * knockbackStrength);
            entity.syncVelocity = true;

            float damage;
            if (entity instanceof ServerPlayer) {
                damage = (float)((1.0 - dist / radius) * 8.0);
            } else {
                damage = (float)((1.0 - dist / radius) * 20.0);
            }
            entity.hurtClient(world.damageSources().explosion(null));
        }
    }

    private static void createFireShockwave(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
        double radius = 50.0;
        AABB damageBox = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );


        p1.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 1, false, false, true));
        p2.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 1, false, false, true));

        for (Entity entity : world.getEntities(null, damageBox)) {
            if (entity == p1 || entity == p2) continue;

            double dist = entity.position().distanceTo(pos);
            if (dist > radius) continue;


            double knockbackStrength = (1.0 - dist / radius) * 15.0;
            Vec3 knockDir = entity.position().subtract(pos).normalize();


            entity.push(
                    knockDir.x * knockbackStrength,
                    knockbackStrength * 1.5,
                    knockDir.z * knockbackStrength
            );
            entity.syncVelocity = true;


            entity.igniteForSeconds(5);
        }



        for (double ringRadius = 3.0; ringRadius <= 15.0; ringRadius += 1.5) {
            int points = (int)(ringRadius * 8);
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double fireX = pos.x + Math.cos(angle) * ringRadius;
                double fireZ = pos.z + Math.sin(angle) * ringRadius;


                BlockPos groundPos = world.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(fireX, pos.y, fireZ)
                );


                BlockPos firePos = groundPos.above();
                if (world.getBlockState(firePos).isAir()) {
                    world.setBlockAndUpdate(firePos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());


                    world.scheduleTick(firePos, net.minecraft.world.level.block.Blocks.FIRE, 60);
                }
            }
        }


        Random rand = new Random();
        for (int i = 0; i < 200; i++) {
            double particleRadius = rand.nextDouble() * 20.0;
            double particleAngle = rand.nextDouble() * 2 * Math.PI;
            double px = pos.x + Math.cos(particleAngle) * particleRadius;
            double pz = pos.z + Math.sin(particleAngle) * particleRadius;
            double py = pos.y + rand.nextDouble() * 3.0;

            world.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, 0, 0.5, 0, 0.05);
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0.3, 0, 0.03);
        }
    }

    private static void broadcastChargeCancel(ServerPlayer player) {
        NormalFacingDapHandler.clearConfirm(player.getUUID(), null);
        if (player == null) return;
        ChargeSyncPayload payload = new ChargeSyncPayload(player.getUUID(), 0f, 0f, false);
        for (ServerPlayer other : PlayerLookup.all(player.level().getServer())) {
            ServerPlayNetworking.send(other, payload);
        }


    }

    public static void broadcastWhiffCooldown(ServerPlayer player, long cooldownEnd) {
        if (player == null) return;
        WhiffCooldownPayload payload = new WhiffCooldownPayload(whiffCooldownMs());
        ServerPlayNetworking.send(player, payload);


        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
    }

    public static void broadcastImpactFrame(ServerPlayer p1, ServerPlayer p2, int durationMs, boolean grayscale) {
        ImpactFramePayload payload = new ImpactFramePayload(durationMs, grayscale);
        if (p1 != null) ServerPlayNetworking.send(p1, payload);
        if (p2 != null) ServerPlayNetworking.send(p2, payload);


        if (p1 != null) {
            for (ServerPlayer nearby : PlayerLookup.around(p1.level(), p1.position(), 20)) {
                if (nearby != p1 && nearby != p2) {
                    ServerPlayNetworking.send(nearby, payload);
                }
            }
        }
    }




    private static boolean arePlayersFacingEachOther(ServerPlayer p1, ServerPlayer p2) {
        Vec3 p1Pos = p1.position();
        Vec3 p2Pos = p2.position();
        Vec3 p1ToP2 = new Vec3(p2Pos.x - p1Pos.x, 0, p2Pos.z - p1Pos.z).normalize();
        Vec3 p2ToP1 = p1ToP2.reverse();

        double yaw1Rad = Math.toRadians(p1.getYRot());
        Vec3 look1 = new Vec3(-Math.sin(yaw1Rad), 0, Math.cos(yaw1Rad));
        double yaw2Rad = Math.toRadians(p2.getYRot());
        Vec3 look2 = new Vec3(-Math.sin(yaw2Rad), 0, Math.cos(yaw2Rad));

        double dot1 = look1.dot(p1ToP2);
        double dot2 = look2.dot(p2ToP1);


        return dot1 > -0.3 && dot2 > -0.3;
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

    private static Vec3 getEffectiveVelocity(ServerPlayer player) {

        if (player.isPassenger()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {

                return vehicle.getDeltaMovement();
            }
        }


        if (player.isFallFlying()) {

            return player.getDeltaMovement();
        }


        return player.getDeltaMovement();
    }

    public static float getChargePercent(UUID playerId) {
        Long startTime = chargeStartTime.get(playerId);
        if (startTime == null) return 0f;

        long elapsed = System.currentTimeMillis() - startTime;
        return Math.min(1.0f, (float) elapsed / chargeTimeMs());
    }

    public static float getFireLevel(UUID playerId) {
        return fireLevel.getOrDefault(playerId, 0f);
    }

    public static boolean isCharging(UUID playerId) {
        return chargeStartTime.containsKey(playerId);
    }

    public static boolean isFullyCharged(UUID playerId) {
        Long startTime = chargeStartTime.get(playerId);
        if (startTime == null) return false;
        long chargeTime = System.currentTimeMillis() - startTime;
        float chargePercent = Math.min(chargeTime / (float)chargeTimeMs(), 1.0f);
        return chargePercent >= 0.8f;
    }

    public static Long getChargeStartTime(UUID playerId) {
        return chargeStartTime.get(playerId);
    }

    public static void resetChargeStartTime(UUID playerId) {
        chargeStartTime.put(playerId, System.currentTimeMillis());
    }

    private static boolean isOnCooldown(UUID uuid) {
        Long cooldownEnd = cooldowns.get(uuid);
        if (cooldownEnd == null) return false;

        long now = System.currentTimeMillis();


        if (cooldownEnd - now > 10000) {
            cooldowns.remove(uuid);
            return false;
        }

        return now < cooldownEnd;
    }

    public static void cleanup(UUID uuid) {
        chargeStartTime.remove(uuid);
        releaseTime.remove(uuid);
        waitingForPartner.remove(uuid);
        cooldowns.remove(uuid);
        speedHistory.remove(uuid);
        fireStartTime.remove(uuid);
        fireGraceTime.remove(uuid);
        fireLevel.remove(uuid);
        impactFreezeTicks.remove(uuid);
        blockingAnimEndTime.remove(uuid);


        perfectDapStartTime.remove(uuid);
        perfectDapPartner.remove(uuid);
        perfectDapFreezeEnd.remove(uuid);
        perfectDapImpactSent.remove(uuid);
        comboCooldown.remove(uuid);
        net.minecraft.world.entity.decoration.ArmorStand pStand = perfectDapArmorStands.remove(uuid);
        if (pStand != null && !pStand.isRemoved()) pStand.discard();


        fireDapStartTime.remove(uuid);
        fireDapPartner.remove(uuid);
        inFireDapHit.remove(uuid);
        fireCircleSpawned.remove(uuid);
        fireDapComboRequestTime.remove(uuid);
        fireDapComboFreezeEnd.remove(uuid);
        pendingFireArmImpacts.remove(uuid);
        pendingFireTornadoSpawns.remove(uuid);
        fireComboActive.remove(uuid);
        net.minecraft.world.entity.decoration.ArmorStand fStand = fireDapArmorStands.remove(uuid);
        if (fStand != null && !fStand.isRemoved()) fStand.discard();


        heavenPlayers.remove(uuid);
        heavenReady.remove(uuid);
        fireMaxedStartTime.remove(uuid);


        DapSessionManager.removeSessionForPlayer(uuid);


        DapComboChain.cancelCombo(uuid);

        PerfectDapComboHandler.cancelCombo(uuid);
    }

    public static boolean isInBlockingAnimation(UUID uuid) {
        Long endTime = blockingAnimEndTime.get(uuid);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            blockingAnimEndTime.remove(uuid);
            return false;
        }
        return true;
    }

    public static void setBlockingAnimation(UUID uuid, long durationMs) {
        blockingAnimEndTime.put(uuid, System.currentTimeMillis() + durationMs);
    }

    public static boolean isPerfectDapFrozen(UUID playerId) {
        return perfectDapFreezeEnd.containsKey(playerId);
    }



    public static boolean isFireDapFrozen(UUID playerId) {
        return fireDapComboFreezeEnd.containsKey(playerId);
    }

    public static boolean isInFireDapBlockingState(UUID playerId) {
        return inFireDapHit.getOrDefault(playerId, false) || fireDapComboFreezeEnd.containsKey(playerId);
    }

    public static boolean isInComboCooldown(UUID playerId) {
        Long cooldownEnd = comboCooldown.get(playerId);
        if (cooldownEnd == null) return false;

        long now = System.currentTimeMillis();
        if (now < cooldownEnd) {
            return true;
        } else {
            comboCooldown.remove(playerId);
            return false;
        }
    }

    public static void startFireDap(ServerPlayer p1, ServerPlayer p2, Vec3 midpoint) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();



        fireComboActive.put(id1, true);
        fireComboActive.put(id2, true);


        fireDapStartTime.put(id1, now);
        fireDapStartTime.put(id2, now);
        fireCircleSpawned.put(id1, false);
        fireCircleSpawned.put(id2, false);
        fireDapPartner.put(id1, id2);
        fireDapPartner.put(id2, id1);
        inFireDapHit.put(id1, true);
        inFireDapHit.put(id2, true);


        DapSession session = DapSessionManager.createSession(
                id1, id2,
                1.2,
                DapSession.DapType.FIRE_DAP
        );

        if (session == null) {

            fireComboActive.remove(id1);
            fireComboActive.remove(id2);
            fireDapStartTime.remove(id1);
            fireDapStartTime.remove(id2);
            return;
        }


        session.onComplete(() -> {
            startFireDapAnimation(p1, p2, midpoint);
        });

    }

    private static void startFireDapAnimation(ServerPlayer p1, ServerPlayer p2, Vec3 midpoint) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        ServerLevel world = p1.level();


        long now = System.currentTimeMillis();
        fireDapStartTime.put(id1, now);
        fireDapStartTime.put(id2, now);


        p1.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        p2.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);


        Vec3 p1Hand = p1.position().add(0, 1.4, 0);
        Vec3 p2Hand = p2.position().add(0, 1.4, 0);
        Vec3 handMid = p1Hand.add(p2Hand).scale(0.5);

        net.minecraft.world.entity.decoration.ArmorStand stand =
                new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, world);
        stand.setPos(handMid.x, handMid.y, handMid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setPermanentlyInvulnerable(true);
        stand.setSilent(true);
        stand.setRemainingFireTicks(0);
        world.addFreshEntity(stand);
        fireDapArmorStands.put(id1, stand);


        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_HIT.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_HIT.ordinal());



        for (ServerPlayer player : p1.level().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id1, true));
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id2, true));
        }



        p1.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 255, false, false));
        p1.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 255, false, false));
        p2.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 255, false, false));
        p2.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 255, false, false));


        ServerPlayNetworking.send(p1, new FireDapWindowPayload());
        ServerPlayNetworking.send(p2, new FireDapWindowPayload());


    }


    private static void onFireDapJPress(ServerPlayer player) {
        UUID playerId = player.getUUID();


        if (!inFireDapHit.getOrDefault(playerId, false)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long startTime = fireDapStartTime.get(playerId);
        if (startTime == null) {
            return;
        }

        long elapsed = now - startTime;


        if (elapsed < FIRE_J_WINDOW_START || elapsed > FIRE_J_WINDOW_END) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§cToo early/late for combo!"));
            return;
        }


        DapFusionHandler.cancelForJCombo(playerId);



        UUID partnerId = fireDapPartner.get(playerId);
        if (partnerId == null) {
            return;
        }


        if (partnerId.equals(playerId)) {
            fireDapComboRequestTime.put(playerId, now);
            ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
            if (partner != null) {
                executeFireDapCombo(player, partner);
            }
            return;
        }

        ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
        if (partner == null) {
            return;
        }


        fireDapComboRequestTime.put(playerId, now);



        Long partnerRequestTime = fireDapComboRequestTime.get(partnerId);
        if (partnerRequestTime != null) {
            long diff = Math.abs(now - partnerRequestTime);
            if (diff < 1000) {

                executeFireDapCombo(player, partner);
            }
        }


    }

    private static void executeFireDapCombo(ServerPlayer p1, ServerPlayer p2) {
        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();
        long now = System.currentTimeMillis();



        inFireDapHit.remove(id1);
        inFireDapHit.remove(id2);
        fireDapComboRequestTime.remove(id1);
        fireDapComboRequestTime.remove(id2);


        DapSessionManager.removeSessionForPlayer(id1);


        p1.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        p2.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);





        fireDapComboFreezeEnd.put(id1, now + FIRE_COMBO_FREEZE_MS);
        fireDapComboFreezeEnd.put(id2, now + FIRE_COMBO_FREEZE_MS);


        for (ServerPlayer player : p1.level().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new FireDapFreezePayload(id1, true));
            ServerPlayNetworking.send(player, new FireDapFreezePayload(id2, true));
        }


        PoseNetworking.broadcastAnimState(p1,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_COMBO_P1.ordinal());
        PoseNetworking.broadcastAnimState(p2,
                com.cooptest.client.CoopAnimationHandler.AnimState.FIRE_DAP_COMBO_P2.ordinal());


        Vec3 midpoint = p1.position().add(p2.position()).scale(0.5);
        p1.level().playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0f, 0.8f);


        for (ServerPlayer player : p1.level().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id1, true));
            ServerPlayNetworking.send(player, new FireDapFirstPersonPayload(id2, true));
        }


        p1.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🔥 DIVINE FLAME COMBO! 🔥"));
        p2.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§c§l🔥 DIVINE FLAME COMBO! 🔥"));


        p1.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 255, false, false));
        p1.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 255, false, false));
        p2.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 255, false, false));
        p2.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 255, false, false));


        spawnVerticalFireWalls(p1, p2);


        auraBeamsActive = true;
        auraBeamStartTime = now;
        auraBeamPlayer1 = id1;
        auraBeamPlayer2 = id2;


        pendingFireArmImpacts.put(id1, new FireDapScheduledEvent(p1, p2, now + FIRE_COMBO_ARM_IMPACT));


        pendingFireTornadoSpawns.put(id1, new FireDapScheduledEvent(p1, p2, now + FIRE_COMBO_TORNADO));

    }

    private static void spawnAnimatedAuraBeam(ServerPlayer player, long elapsed) {
        ServerLevel world = player.level();
        Vec3 playerPos = player.position();


        double phase = (elapsed % 1000) / 1000.0;


        for (int y = 0; y < 70; y++) {
            double currentY = playerPos.y + y;


            double coreAngle = (y * 20 + elapsed * 0.5) % 360;
            double coreRad = Math.toRadians(coreAngle);
            double coreRadius = 0.3;

            double coreX = playerPos.x + Math.cos(coreRad) * coreRadius;
            double coreZ = playerPos.z + Math.sin(coreRad) * coreRadius;

            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, coreX, currentY, coreZ, 1, 0.05, 0.05, 0.05, 0.01);
            if (y % 3 == 0) {
                world.sendParticles(ParticleTypes.ENCHANT, coreX, currentY, coreZ, 2, 0.1, 0.1, 0.1, 0.5);
            }


            double midAngle = (y * 15 - elapsed * 0.3) % 360;
            double midRad = Math.toRadians(midAngle);
            double midRadius = 0.6;

            double midX = playerPos.x + Math.cos(midRad) * midRadius;
            double midZ = playerPos.z + Math.sin(midRad) * midRadius;

            world.sendParticles(ParticleTypes.FLAME, midX, currentY, midZ, 2, 0.1, 0.1, 0.1, 0.02);


            if (y % 2 == 0) {
                double outerAngle = (y * 10 + elapsed * 0.2) % 360;
                for (int i = 0; i < 4; i++) {
                    double angle = outerAngle + (i * 90);
                    double rad = Math.toRadians(angle);
                    double outerRadius = 0.9 + Math.sin(phase * Math.PI * 2) * 0.2;

                    double x = playerPos.x + Math.cos(rad) * outerRadius;
                    double z = playerPos.z + Math.sin(rad) * outerRadius;

                    world.sendParticles(ParticleTypes.LARGE_SMOKE, x, currentY, z, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }


            if (y % 5 == 0) {
                double waveOffset = Math.sin((y / 70.0 + phase) * Math.PI * 2) * 0.5;
                world.sendParticles(ParticleTypes.END_ROD,
                        playerPos.x + waveOffset, currentY, playerPos.z,
                        1, 0.1, 0.1, 0.1, 0.02);
            }
        }


        for (double angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle + elapsed * 0.5);
            double radius = 3.0;

            double x = playerPos.x + Math.cos(rad) * radius;
            double z = playerPos.z + Math.sin(rad) * radius;

            world.sendParticles(ParticleTypes.FLAME, x, playerPos.y + 0.1, z, 3, 0.1, 0.1, 0.1, 0.05);
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, playerPos.y + 0.1, z, 2, 0.1, 0.1, 0.1, 0.03);


            if (angle % 30 == 0) {
                world.sendParticles(ParticleTypes.END_ROD, x, playerPos.y + 0.1, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void spawnVerticalFireWalls(ServerPlayer p1, ServerPlayer p2) {
        ServerLevel world = p1.level();
        Vec3 midpoint = p1.position().add(p2.position()).scale(0.5);





        double wallDistance = 5.0;
        int wallHeight = 50;
        int wallLength = 10;


        for (int x = -wallLength/2; x <= wallLength/2; x++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + x;
                double wy = midpoint.y + y;
                double wz = midpoint.z + wallDistance;


                if (Math.abs(x) <= 1.5) continue;

                world.sendParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.sendParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }


        for (int x = -wallLength/2; x <= wallLength/2; x++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + x;
                double wy = midpoint.y + y;
                double wz = midpoint.z - wallDistance;

                if (Math.abs(x) <= 1.5) continue;

                world.sendParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.sendParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }


        for (int z = -wallLength/2; z <= wallLength/2; z++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + wallDistance;
                double wy = midpoint.y + y;
                double wz = midpoint.z + z;

                if (Math.abs(z) <= 1.5) continue;

                world.sendParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.sendParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }


        for (int z = -wallLength/2; z <= wallLength/2; z++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x - wallDistance;
                double wy = midpoint.y + y;
                double wz = midpoint.z + z;

                if (Math.abs(z) <= 1.5) continue;

                world.sendParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                if (y % 3 == 0) {
                    world.sendParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                }
            }
        }

    }

    private static void spawnFireCircle(ServerPlayer p1, ServerPlayer p2) {
        ServerLevel world = p1.level();
        Vec3 midpoint = p1.position().add(p2.position()).scale(0.5);


        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, midpoint.x, midpoint.y + 1, midpoint.z, 3, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.EXPLOSION, midpoint.x, midpoint.y + 1, midpoint.z, 20, 2.0, 2.0, 2.0, 0);


        int radius = 20;
        for (double angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            double x = midpoint.x + radius * Math.cos(rad);
            double z = midpoint.z + radius * Math.sin(rad);


            world.sendParticles(ParticleTypes.FLAME, x, midpoint.y + 0.1, z, 20, 0.4, 1.2, 0.4, 0.05);
            world.sendParticles(ParticleTypes.LAVA, x, midpoint.y + 0.1, z, 10, 0.3, 0.6, 0.3, 0.02);
            world.sendParticles(ParticleTypes.LARGE_SMOKE, x, midpoint.y + 0.1, z, 15, 0.5, 1.8, 0.5, 0.1);


            if (angle % 20 == 0) {
                for (int h = 0; h < 10; h++) {
                    world.sendParticles(ParticleTypes.FLAME, x, midpoint.y + h, z, 25, 0.6, 0.6, 0.6, 0.12);
                    world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y + h, z, 12, 0.4, 0.4, 0.4, 0.06);
                }
            }
        }


        for (int height = 0; height < 15; height++) {
            for (double angle = 0; angle < 360; angle += 10) {
                double rad = Math.toRadians(angle + height * 25);
                double distance = 3 + height * 0.4;
                double x = midpoint.x + distance * Math.cos(rad);
                double z = midpoint.z + distance * Math.sin(rad);

                world.sendParticles(ParticleTypes.FLAME, x, midpoint.y + height, z, 5, 0.3, 0.3, 0.3, 0.04);
                world.sendParticles((ParticleOptions)ParticleTypes.DRAGON_BREATH, x, midpoint.y + height, z, 3, 0.2, 0.2, 0.2, 0.02);
                world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y + height, z, 2, 0.15, 0.15, 0.15, 0.01);
            }
        }


        for (int h = 0; h < 20; h++) {
            world.sendParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y + h, midpoint.z, 40, 2.0, 0.5, 2.0, 0.15);
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y + h, midpoint.z, 20, 1.5, 0.3, 1.5, 0.1);
            world.sendParticles(ParticleTypes.LAVA, midpoint.x, midpoint.y + h, midpoint.z, 15, 1.0, 0.2, 1.0, 0.05);
        }


        Random random = new Random();
        int fireCount = 0;
        for (int i = 0; i < 300; i++) {

            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 4 + random.nextDouble() * 16;

            double x = midpoint.x + Math.cos(angle) * distance;
            double z = midpoint.z + Math.sin(angle) * distance;

            BlockPos pos = new BlockPos((int)x, (int)midpoint.y, (int)z);
            BlockPos above = pos.above();


            if (world.getBlockState(pos).isRedstoneConductor(world, pos) &&
                    world.getBlockState(above).isAir()) {
                world.setBlockAndUpdate(above, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
                fireCount++;


                world.sendParticles(ParticleTypes.FLAME, x, midpoint.y + 0.5, z, 20, 0.5, 1.0, 0.5, 0.08);
                world.sendParticles(ParticleTypes.LARGE_SMOKE, x, midpoint.y + 0.5, z, 10, 0.4, 0.8, 0.4, 0.06);
                world.sendParticles(ParticleTypes.LAVA, x, midpoint.y + 0.1, z, 5, 0.3, 0.2, 0.3, 0.02);
            }
        }


        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.5f, 0.5f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.0f, 0.7f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.EPIC_DAP, SoundSource.PLAYERS, 2.0f, 1.2f);

    }

    private static void executeFireArmImpact(ServerPlayer p1, ServerPlayer p2) {
        ServerLevel world = p1.level();


        Vec3 midpoint;
        net.minecraft.world.entity.decoration.ArmorStand stand = fireDapArmorStands.get(p1.getUUID());
        if (stand != null && !stand.isRemoved()) {
            midpoint = stand.position();
        } else {

            midpoint = p1.position().add(p2.position()).scale(0.5).add(0, 1.2, 0);
        }




        world.sendParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y, midpoint.z,
                3, 0.3, 0.3, 0.3, 0.1);
        world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y, midpoint.z,
                2, 0.2, 0.2, 0.2, 0.08);


        Vec3 underArms = new Vec3(midpoint.x, midpoint.y - 0.5, midpoint.z);
        world.sendParticles(ParticleTypes.FLAME, underArms.x, underArms.y, underArms.z,
                8, 0.5, 0.2, 0.5, 0.12);
        world.sendParticles(ParticleTypes.LAVA, underArms.x, underArms.y, underArms.z,
                4, 0.4, 0.15, 0.4, 0.05);


        for (int ring = 1; ring <= 15; ring++) {
            double radius = ring * 1.5;
            for (double angle = 0; angle < 360; angle += 8) {
                double rad = Math.toRadians(angle);
                double x = midpoint.x + radius * Math.cos(rad);
                double z = midpoint.z + radius * Math.sin(rad);

                world.sendParticles(ParticleTypes.FLAME, x, midpoint.y, z, 3, 0.2, 0.5, 0.2, 0.05);
                world.sendParticles((ParticleOptions)ParticleTypes.DRAGON_BREATH, x, midpoint.y, z, 2, 0.15, 0.3, 0.15, 0.03);
            }
        }


        double pillarStartY = midpoint.y + 2.0;
        for (int h = 0; h < 30; h++) {

            world.sendParticles(ParticleTypes.FLAME, midpoint.x, pillarStartY + h, midpoint.z,
                    25, 1.5, 0.5, 1.5, 0.15);
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, pillarStartY + h, midpoint.z,
                    15, 1.2, 0.4, 1.2, 0.1);
            world.sendParticles(ParticleTypes.LAVA, midpoint.x, pillarStartY + h, midpoint.z,
                    10, 1.0, 0.3, 1.0, 0.08);


            if (h < 5) {
                world.sendParticles(ParticleTypes.LARGE_SMOKE, midpoint.x, pillarStartY + h, midpoint.z,
                        20, 2.0, 0.5, 2.0, 0.12);
            }
        }


        for (double angle = 0; angle < 360; angle += 3) {
            double rad = Math.toRadians(angle);
            double radius = 3.0;

            double x = midpoint.x + Math.cos(rad) * radius;
            double z = midpoint.z + Math.sin(rad) * radius;


            world.sendParticles(ParticleTypes.FLAME, x, midpoint.y, z, 5, 0.1, 0.1, 0.1, 0.08);
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y, z, 3, 0.1, 0.1, 0.1, 0.05);
            world.sendParticles(ParticleTypes.END_ROD, x, midpoint.y, z, 1, 0, 0, 0, 0);


            if (angle % 15 == 0) {
                for (int h = 0; h < 10; h++) {
                    world.sendParticles(ParticleTypes.FLAME, x, midpoint.y + h, z, 2, 0.05, 0.05, 0.05, 0.02);
                }
            }
        }


        UUID id1 = p1.getUUID();
        UUID id2 = p2.getUUID();

        AABB searchBox = new AABB(
                midpoint.x - 30, midpoint.y - 30, midpoint.z - 30,
                midpoint.x + 30, midpoint.y + 30, midpoint.z + 30
        );

        for (Entity entity : world.getEntities(null, searchBox)) {

            if (entity.getUUID().equals(id1) || entity.getUUID().equals(id2)) {
                continue;
            }

            Vec3 entityPos = entity.position();
            double distance = entityPos.distanceTo(midpoint);

            if (distance < 30 && distance > 0.1) {

                Vec3 direction = entityPos.subtract(midpoint).normalize();


                double strength = (30 - distance) / 30.0 * 3.0;

                entity.setDeltaMovement(
                        direction.x * strength,
                        0.8 + (strength * 0.5),
                        direction.z * strength
                );
                entity.syncVelocity = true;


                if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                    float damage = (float)((30 - distance) / 30.0 * 20.0);
                    living.hurtClient(living.damageSources().explosion(null));
                }
            }
        }


        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                ModSounds.GALACTIC_DAP, SoundSource.PLAYERS, 3.0f, 1.0f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5f, 0.8f);

    }

    private static void spawnFireTornado(ServerPlayer p1, ServerPlayer p2) {
        ServerLevel world = p1.level();
        Vec3 midpoint = p1.position().add(p2.position()).scale(0.5);



        tornadoStartTime = System.currentTimeMillis();
        tornadoActive = true;
        tornadoCenter = midpoint;
        tornadoWorld = world;
        activeTornadoSwirls.clear();


        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 4.0f, 0.4f);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z,
                SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3.0f, 0.6f);
    }

    private static void teleportFireDapFacingEachOther(ServerPlayer p1, ServerPlayer p2, double targetDistance) {
        Vec3 p1Pos = p1.position();
        Vec3 p2Pos = p2.position();
        double distance = p1Pos.distanceTo(p2Pos);


        Vec3 direction = p2Pos.subtract(p1Pos).normalize();
        Vec3 midpoint = p1Pos.add(p2Pos).scale(0.5);


        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawP2 = yawP1 + 180;


        if (Math.abs(distance - targetDistance) > 0.05) {
            Vec3 offset = direction.scale(targetDistance / 2.0);
            Vec3 targetP1 = midpoint.subtract(offset);
            Vec3 targetP2 = midpoint.add(offset);

            p1.teleportTo(p1.level(), targetP1.x, targetP1.y, targetP1.z, java.util.Set.of(), yawP1, 0.0f, false);
            p2.teleportTo(p2.level(), targetP2.x, targetP2.y, targetP2.z, java.util.Set.of(), yawP2, 0.0f, false);


            p1.setYRot(yawP1);
            p1.setYBodyRot(yawP1);
            p1.setYHeadRot(yawP1);
            p2.setYRot(yawP2);
            p2.setYBodyRot(yawP2);
            p2.setYHeadRot(yawP2);
        } else {

            p1.teleportTo(p1.level(), p1Pos.x, p1Pos.y, p1Pos.z, java.util.Set.of(), yawP1, 0.0f, false);
            p2.teleportTo(p2.level(), p2Pos.x, p2Pos.y, p2Pos.z, java.util.Set.of(), yawP2, 0.0f, false);


            p1.setYRot(yawP1);
            p1.setYBodyRot(yawP1);
            p1.setYHeadRot(yawP1);
            p2.setYRot(yawP2);
            p2.setYBodyRot(yawP2);
            p2.setYHeadRot(yawP2);
        }
    }

    private static void teleportPerfectDapFacingEachOther(ServerPlayer p1, ServerPlayer p2, double targetDistance) {
        Vec3 p1Pos = p1.position();
        Vec3 p2Pos = p2.position();
        double distance = p1Pos.distanceTo(p2Pos);


        Vec3 direction = p2Pos.subtract(p1Pos).normalize();
        Vec3 midpoint = p1Pos.add(p2Pos).scale(0.5);


        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawP2 = yawP1 + 180;


        if (Math.abs(distance - targetDistance) > 0.05) {
            Vec3 offset = direction.scale(targetDistance / 2.0);
            Vec3 targetP1 = midpoint.subtract(offset);
            Vec3 targetP2 = midpoint.add(offset);

            p1.teleportTo(p1.level(), targetP1.x, targetP1.y, targetP1.z, java.util.Set.of(), yawP1, 0.0f, false);
            p2.teleportTo(p2.level(), targetP2.x, targetP2.y, targetP2.z, java.util.Set.of(), yawP2, 0.0f, false);


            p1.setYRot(yawP1);
            p1.setYBodyRot(yawP1);
            p1.setYHeadRot(yawP1);
            p2.setYRot(yawP2);
            p2.setYBodyRot(yawP2);
            p2.setYHeadRot(yawP2);
        } else {

            p1.teleportTo(p1.level(), p1Pos.x, p1Pos.y, p1Pos.z, java.util.Set.of(), yawP1, 0.0f, false);
            p2.teleportTo(p2.level(), p2Pos.x, p2Pos.y, p2Pos.z, java.util.Set.of(), yawP2, 0.0f, false);


            p1.setYRot(yawP1);
            p1.setYBodyRot(yawP1);
            p1.setYHeadRot(yawP1);
            p2.setYRot(yawP2);
            p2.setYBodyRot(yawP2);
            p2.setYHeadRot(yawP2);
        }
    }

    private static void smoothDapDescent(ServerPlayer player, net.minecraft.world.entity.decoration.ArmorStand stand) {
        if (stand == null || stand.isRemoved()) return;


        if (fireComboActive.getOrDefault(player.getUUID(), false)) {
            return;
        }

        ServerLevel world = player.level();
        Vec3 playerPos = player.position();
        Vec3 standPos = stand.position();


        double distanceToPlayer = standPos.distanceTo(playerPos);
        if (distanceToPlayer < 0.8) {

            Vec3 direction = standPos.subtract(playerPos).normalize();


            Vec3 newStandPos = playerPos.add(direction.scale(1.2));
            stand.setPos(newStandPos.x, newStandPos.y, newStandPos.z);

            return;
        }



        BlockPos feetPos = BlockPos.containing(playerPos.x, playerPos.y - 0.1, playerPos.z);
        boolean solidBlockBelow = !world.getBlockState(feetPos).isAir() &&
                world.getBlockState(feetPos).isRedstoneConductor(world, feetPos);

        if (solidBlockBelow) {

            return;
        }


        BlockPos checkPos = BlockPos.containing(playerPos.x, playerPos.y - 1.0, playerPos.z);
        int blocksChecked = 0;


        while (blocksChecked < 10) {
            if (!world.getBlockState(checkPos).isAir() &&
                    world.getBlockState(checkPos).isRedstoneConductor(world, checkPos)) {
                break;
            }
            checkPos = checkPos.below();
            blocksChecked++;
        }


        double groundY = checkPos.getY() + 1.0;


        if (playerPos.y <= groundY + 0.1) {
            return;
        }



        double newY = playerPos.y - 0.12;


        newY = Math.max(newY, groundY);


        if (newY < playerPos.y && newY >= groundY) {
            player.setPos(playerPos.x, newY, playerPos.z);


            stand.setPos(standPos.x, newY + 1.4, standPos.z);
        }
    }
}