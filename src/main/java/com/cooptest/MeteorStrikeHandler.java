package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

// IGNORE THIS IS TRASH WASTE 4 hr ON THIS
public class MeteorStrikeHandler {
    public static final long ABILITY_DURATION_MS = 60_000;
    public static final long COUNTDOWN_MS = 3_000;
    public static final int CRATER_RADIUS = 10;
    public static final int DAMAGE_RADIUS = 20;
    private static final Map<UUID, Long> abilityExpiry = new HashMap<>();
    private static final Map<UUID, PendingMeteor> pendingMeteors = new HashMap<>();
    private static class PendingMeteor {
        final UUID playerId;
        final BlockPos target;
        final long impactTime;
        final ServerLevel world;
        boolean invulnGranted = false;
        PendingMeteor(UUID playerId, BlockPos target, ServerLevel world) {
            this.playerId  = playerId;
            this.target    = target;
            this.world     = world;
            this.impactTime = System.currentTimeMillis() + COUNTDOWN_MS;
        }
    }
    public record MeteorFirePayload() implements CustomPacketPayload {
        public static final Type<MeteorFirePayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("cooptest", "meteor_fire"));
        public static final StreamCodec<FriendlyByteBuf, MeteorFirePayload> CODEC =
                StreamCodec.ofMember((p, buf) -> {}, buf -> new MeteorFirePayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record MeteorGrantPayload(long expiryMs) implements CustomPacketPayload {
        public static final Type<MeteorGrantPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("cooptest", "meteor_grant"));
        public static final StreamCodec<FriendlyByteBuf, MeteorGrantPayload> CODEC =
                StreamCodec.ofMember((p, buf) -> buf.writeLong(p.expiryMs),
                        buf -> new MeteorGrantPayload(buf.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record MeteorStatusPayload(long remainingAbilityMs, long countdownMs) implements CustomPacketPayload {
        public static final Type<MeteorStatusPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("cooptest", "meteor_status"));
        public static final StreamCodec<FriendlyByteBuf, MeteorStatusPayload> CODEC =
                StreamCodec.ofMember((p, buf) -> { buf.writeLong(p.remainingAbilityMs); buf.writeLong(p.countdownMs); },
                        buf -> new MeteorStatusPayload(buf.readLong(), buf.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record MeteorExpiredPayload() implements CustomPacketPayload {
        public static final Type<MeteorExpiredPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("cooptest", "meteor_expired"));
        public static final StreamCodec<FriendlyByteBuf, MeteorExpiredPayload> CODEC =
                StreamCodec.ofMember((p, buf) -> {}, buf -> new MeteorExpiredPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(MeteorFirePayload.ID,    MeteorFirePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorGrantPayload.ID,   MeteorGrantPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorStatusPayload.ID,  MeteorStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorExpiredPayload.ID, MeteorExpiredPayload.CODEC);
    }
    public static void registerClientPayloads() {
        try { PayloadTypeRegistry.playC2S().register(MeteorFirePayload.ID, MeteorFirePayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(MeteorGrantPayload.ID, MeteorGrantPayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(MeteorStatusPayload.ID, MeteorStatusPayload.CODEC); } catch (Exception ignored) {}
        try { PayloadTypeRegistry.playS2C().register(MeteorExpiredPayload.ID, MeteorExpiredPayload.CODEC); } catch (Exception ignored) {}
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MeteorFirePayload.ID,
                (payload, ctx) -> ctx.server().execute(() -> onFire(ctx.player())));
        ServerTickEvents.END_SERVER_TICK.register(MeteorStrikeHandler::tick);
    }
    public static void grantAbility(ServerPlayer p1, ServerPlayer p2) {
        long expiry = System.currentTimeMillis() + ABILITY_DURATION_MS;
        abilityExpiry.put(p1.getUUID(), expiry);
        abilityExpiry.put(p2.getUUID(), expiry);
        try { ServerPlayNetworking.send(p1, new MeteorGrantPayload(expiry)); } catch (Exception ignored) {}
        try { ServerPlayNetworking.send(p2, new MeteorGrantPayload(expiry)); } catch (Exception ignored) {}
        for (ServerPlayer p : p1.level().getServer().getPlayerList().getPlayers()) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§c☄ " + p1.getName().getString() + " §7and §c" +
                            p2.getName().getString() + " §7have unlocked §c§lMETEOR STRIKE§7! Press §lG§7 to fire!"), false);
        }
    }
    public static boolean hasAbility(UUID id) { return abilityExpiry.containsKey(id); }
    public static void cleanup(UUID id) {
        abilityExpiry.remove(id);
        pendingMeteors.remove(id);
    }
    private static void onFire(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!abilityExpiry.containsKey(id)) return;
        if (pendingMeteors.containsKey(id)) return;
        Vec3 eye  = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        BlockPos target = null;
        for (double d = 1.0; d <= 80.0; d += 0.5) {
            Vec3 point = eye.add(look.scale(d));
            BlockPos bp = BlockPos.containing(point);
            if (!player.level().getBlockState(bp).isAir()) {
                target = bp;
                break;
            }
        }
        if (target == null) {
            Vec3 endpoint = eye.add(look.scale(80.0));
            target = BlockPos.containing(endpoint);
        }
        pendingMeteors.put(id, new PendingMeteor(id, target, player.level()));
        final BlockPos finalTarget = target;
        Vec3 targetCenter = Vec3.atCenterOf(finalTarget);
        for (double d = 0; d < eye.distanceTo(targetCenter); d += 1.0) {
            Vec3 point = eye.add(look.scale(d));
            player.level().sendParticles(ParticleTypes.CRIT,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 2.0f, 0.5f);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§c☄ METEOR INCOMING §7— impact in 3 seconds!"), true);
    }
    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        abilityExpiry.entrySet().removeIf(e -> {
            if (now >= e.getValue()) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    try { ServerPlayNetworking.send(p, new MeteorExpiredPayload()); } catch (Exception ignored) {}
                }
                pendingMeteors.remove(e.getKey());
                return true;
            }
            return false;
        });
        for (Iterator<PendingMeteor> it = new ArrayList<>(pendingMeteors.values()).iterator(); it.hasNext();) {
            PendingMeteor m = it.next();
            long msLeft = m.impactTime - now;
            if (msLeft <= 500 && !m.invulnGranted) {
                m.invulnGranted = true;
                ServerPlayer p = server.getPlayerList().getPlayer(m.playerId);
                if (p != null) p.setInvulnerable(true);
            }
            if (server.getTickCount() % 2 == 0) {
                spawnCountdownPillar(m.world, m.target, (float) msLeft / COUNTDOWN_MS);
            }
            if (now >= m.impactTime) {
                pendingMeteors.remove(m.playerId);
                abilityExpiry.remove(m.playerId);
                ServerPlayer p = server.getPlayerList().getPlayer(m.playerId);
                impact(m.world, m.target, p);
                if (p != null) {
                    try { ServerPlayNetworking.send(p, new MeteorExpiredPayload()); } catch (Exception ignored) {}
                    final ServerPlayer fp = p;
                    server.execute(() -> {
                        fp.setInvulnerable(false);
                    });
                }
            } else {
                ServerPlayer p = server.getPlayerList().getPlayer(m.playerId);
                if (p != null) {
                    long abilityLeft = Math.max(0, abilityExpiry.getOrDefault(m.playerId, 0L) - now);
                    try { ServerPlayNetworking.send(p, new MeteorStatusPayload(abilityLeft, msLeft)); } catch (Exception ignored) {}
                }
            }
        }
        for (Map.Entry<UUID, Long> e : abilityExpiry.entrySet()) {
            if (!pendingMeteors.containsKey(e.getKey())) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null && server.getTickCount() % 5 == 0) {
                    long abilityLeft = Math.max(0, e.getValue() - now);
                    try { ServerPlayNetworking.send(p, new MeteorStatusPayload(abilityLeft, -1)); } catch (Exception ignored) {}
                }
            }
        }
    }
    private static void spawnCountdownPillar(ServerLevel world, BlockPos target, float progress) {
        double x = target.getX() + 0.5, z = target.getZ() + 0.5;
        int height = (int)(50 * progress) + 5;
        for (int y = 0; y < height; y += 3) {
            world.sendParticles(ParticleTypes.FLAME,
                    x, target.getY() + y, z, 1, 0.3, 0, 0.3, 0.05);
        }
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30.0 + (System.currentTimeMillis() / 100.0 % 360));
            double radius = 3.0 * progress + 0.5;
            world.sendParticles(ParticleTypes.CRIT,
                    x + Math.cos(angle) * radius, target.getY() + 0.5, z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
        if (progress < 0.5f) {
            world.playSound(null, x, target.getY(), z,
                    SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 1.5f, 0.5f + (1.0f - progress));
        }
    }
    private static void impact(ServerLevel world, BlockPos target, ServerPlayer shooter) {
        Vec3 center = Vec3.atCenterOf(target);
        int r = CRATER_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x*x + y*y + z*z > r*r) continue;
                    BlockPos bp = target.offset(x, y, z);
                    var state = world.getBlockState(bp);
                    if (state.isAir()) continue;
                    if (state.getBlock() == Blocks.BEDROCK) continue;
                    world.destroyBlock(bp, false);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45.0);
            double ex = center.x + Math.cos(angle) * 12;
            double ez = center.z + Math.sin(angle) * 12;
            world.explode(shooter, ex, center.y, ez, 12.0f, true,
                    Level.ExplosionInteraction.TNT);
        }
        world.explode(shooter, center.x, center.y, center.z, 20.0f, true,
                Level.ExplosionInteraction.TNT);
        AABB hitBox = new AABB(center, center).inflate(DAMAGE_RADIUS);
        for (var e : world.getEntities(shooter, hitBox)) {
            if (!(e instanceof net.minecraft.world.entity.LivingEntity living)) continue;
            double dist = e.position().distanceTo(center);
            if (dist > DAMAGE_RADIUS) continue;
            float dmg = (float)(25.0 * (1.0 - dist / DAMAGE_RADIUS));
            living.hurtClient(world.damageSources().explosion(null, shooter));
            Vec3 dir = e.position().subtract(center).normalize();
            if (dir.lengthSqr() < 0.001) dir = new Vec3(0, 1, 0);
            living.push(dir.x * 3.0, 2.0, dir.z * 3.0);
            living.hurtMarked = true;
        }
        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 20, 5, 5, 5, 0);
        world.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 200, 8, 4, 8, 0.5);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 5.0f, 0.3f);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0f, 0.5f);
    }
}