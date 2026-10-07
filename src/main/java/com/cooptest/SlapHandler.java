package com.cooptest;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
public class SlapHandler {
    private static final double SLAP_RANGE      = 1.2;
    private static final double SNAP_DISTANCE   = 0.9;
    private static final double BACK_THRESHOLD  = 0.70;
    private static final double FRONT_THRESHOLD = -0.50;
    private static final double AIM_THRESHOLD   = 0.80;
    private static final long   IMPACT_DELAY_MS = 130L;
    private static final long   FRONT_IMPACT_MS = 250L;
    private static final int    ANIM_SLAP       = 67;
    private static final int    ANIM_SLAP_FRONT = 82;
    public record CameraFlickPayload(UUID playerId, float pitchDelta) implements CustomPacketPayload {
        public static final Type<CameraFlickPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "camera_flick"));
        public static final StreamCodec<FriendlyByteBuf, CameraFlickPayload> CODEC =
                StreamCodec.ofMember(
                        (val, buf) -> { buf.writeUUID(val.playerId()); buf.writeFloat(val.pitchDelta()); },
                        buf -> new CameraFlickPayload(buf.readUUID(), buf.readFloat())
                );
        @Override public Type<CameraFlickPayload> type() { return ID; }
    }
    public record CameraYawFlickPayload(UUID playerId, float yawDelta) implements CustomPacketPayload {
        public static final Type<CameraYawFlickPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "camera_yaw_flick"));
        public static final StreamCodec<FriendlyByteBuf, CameraYawFlickPayload> CODEC =
                StreamCodec.ofMember(
                        (val, buf) -> { buf.writeUUID(val.playerId()); buf.writeFloat(val.yawDelta()); },
                        buf -> new CameraYawFlickPayload(buf.readUUID(), buf.readFloat())
                );
        @Override public Type<CameraYawFlickPayload> type() { return ID; }
    }
    public record ScreenClosePayload(UUID playerId) implements CustomPacketPayload {
        public static final Type<ScreenClosePayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("testcoop", "screen_close"));
        public static final StreamCodec<FriendlyByteBuf, ScreenClosePayload> CODEC =
                StreamCodec.ofMember(
                        (val, buf) -> buf.writeUUID(val.playerId()),
                        buf -> new ScreenClosePayload(buf.readUUID())
                );
        @Override public Type<ScreenClosePayload> type() { return ID; }
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(CameraFlickPayload.ID,    CameraFlickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraYawFlickPayload.ID, CameraYawFlickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScreenClosePayload.ID,    ScreenClosePayload.CODEC);
    }
    public static boolean checkSlapOnRelease(ServerPlayer attacker) {
        Vec3 aEye  = attacker.position().add(0, attacker.getEyeHeight(attacker.asLivingEntity().asLivingEntity().getPose()), 0);
        Vec3 aLook = attacker.getViewVector(1.0f);
        ServerPlayer victim = null;
        double closest = SLAP_RANGE + 0.001;
        for (ServerPlayer candidate : attacker.level().players()) {
            if (candidate == attacker) continue;
            double dist = attacker.position().distanceTo(candidate.position());
            if (dist >= closest) continue;
            Vec3 victimLook = candidate.getViewVector(1.0f);
            double lookDot = aLook.dot(victimLook);
            boolean isBack  = lookDot >= BACK_THRESHOLD;
            boolean isFront = lookDot <= FRONT_THRESHOLD;
            if (!isBack && !isFront) continue;
            Vec3 victimHead = candidate.position().add(0, 1.6, 0);
            Vec3 toHead     = victimHead.subtract(aEye);
            double distToHead = toHead.length();
            if (distToHead < 0.01) continue;
            double aimDot = toHead.normalize().dot(aLook);
            if (aimDot < AIM_THRESHOLD) continue;
            victim = candidate;
            closest = dist;
        }
        if (victim == null) return false;
        Vec3 victimLookFinal = victim.getViewVector(1.0f);
        if (aLook.dot(victimLookFinal) <= FRONT_THRESHOLD) {
            executeFrontSlap(attacker, victim);
        } else {
            executeSlap(attacker, victim);
        }
        return true;
    }
    private static void executeSlap(ServerPlayer attacker, ServerPlayer victim) {
        ServerLevel world = attacker.level();
        Vec3 victimPos  = victim.position();
        Vec3 victimFwd  = victim.getViewVector(1.0f);
        Vec3 victimFwdH = new Vec3(victimFwd.x, 0, victimFwd.z).normalize();
        if (victimFwdH.lengthSqr() < 0.001) victimFwdH = new Vec3(1, 0, 0);
        Vec3 snapPos = victimPos.subtract(victimFwdH.scale(SNAP_DISTANCE));
        double safeY = snapPos.y;
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos check = BlockPos.containing(snapPos.x, snapPos.y - dy, snapPos.z);
            if (!world.getBlockState(check).isAir()) {
                safeY = check.getY() + 1.0;
                break;
            }
        }
        snapPos = new Vec3(snapPos.x, safeY, snapPos.z);
        attacker.teleportTo(snapPos.x, snapPos.y, snapPos.z);
        Vec3 diff = victimPos.subtract(snapPos);
        float yaw = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x))) - 90f;
        attacker.setYRot(yaw);
        attacker.setYBodyRot(yaw);
        attacker.setYHeadRot(yaw);
        attacker.yBodyRotO = yaw;
        attacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        attacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        PoseNetworking.broadcastAnimState(attacker, ANIM_SLAP);
        final UUID victimId = victim.getUUID();
        new Thread(() -> {
            try { Thread.sleep(IMPACT_DELAY_MS); } catch (InterruptedException ignored) {}
            attacker.level().getServer().execute(() -> {
                ServerPlayer v = attacker.level().getServer().getPlayerList().getPlayer(victimId);
                if (v == null) return;
                Vec3 hitPos = v.position().add(0, 1.7, 0);
                v.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 6, false, true));
                float reactYaw = v.getYHeadRot() + 90f;
                v.setYHeadRot(reactYaw);
                CameraFlickPayload flick = new CameraFlickPayload(victimId, 70f);
                for (var p : attacker.level().getServer().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, flick);
                }
                ServerPlayNetworking.send(v, new ScreenClosePayload(victimId));
                v.displayClientMessage(net.minecraft.network.chat.Component.literal("§c§l I like ya cut G"), true);
                attacker.displayClientMessage(net.minecraft.network.chat.Component.literal("§6§l SLAP!"), true);
                world.sendParticles(ParticleTypes.CRIT,
                        hitPos.x, hitPos.y, hitPos.z, 10, 0.15, 0.1, 0.15, 0.15);
                world.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        hitPos.x, hitPos.y, hitPos.z, 4, 0.1, 0.05, 0.1, 0.05);
                world.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        hitPos.x, hitPos.y, hitPos.z, 6, 0.1, 0.1, 0.1, 0.08);
                world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                        ModSounds.SLAP, SoundSource.PLAYERS, 1.4f, 1.0f);
                world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 2.0f);
            });
        }).start();
    }
    private static void executeFrontSlap(ServerPlayer attacker, ServerPlayer victim) {
        ServerLevel world = attacker.level();
        Vec3 diff = victim.position().subtract(attacker.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        attacker.setYRot(yaw); attacker.setYBodyRot(yaw); attacker.setYHeadRot(yaw);
        attacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        attacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        PoseNetworking.broadcastAnimState(attacker, ANIM_SLAP_FRONT);
        final UUID victimId = victim.getUUID();
        new Thread(() -> {
            try { Thread.sleep(FRONT_IMPACT_MS); } catch (InterruptedException ignored) {}
            attacker.level().getServer().execute(() -> {
                ServerPlayer v = attacker.level().getServer().getPlayerList().getPlayer(victimId);
                if (v == null) return;
                Vec3 hitPos = v.position().add(0, 1.7, 0);
                v.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 5, false, true));
                CameraYawFlickPayload yawFlick = new CameraYawFlickPayload(victimId, +45f);
                for (var p : attacker.level().getServer().getPlayerList().getPlayers())
                    ServerPlayNetworking.send(p, yawFlick);
                ServerPlayNetworking.send(v, new ScreenClosePayload(victimId));
                world.sendParticles(ParticleTypes.CRIT,         hitPos.x, hitPos.y, hitPos.z, 10, 0.15, 0.1, 0.15, 0.15);
                world.sendParticles(ParticleTypes.SWEEP_ATTACK,  hitPos.x, hitPos.y, hitPos.z,  4, 0.1,  0.05, 0.1, 0.05);
                world.playSound(null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP,  SoundSource.PLAYERS, 1.4f, 0.9f);
                world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.8f);
            });
        }).start();
    }
}