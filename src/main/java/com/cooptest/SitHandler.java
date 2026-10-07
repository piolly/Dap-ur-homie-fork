package com.cooptest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import com.mojang.brigadier.context.CommandContext;
import java.util.*;
public class SitHandler {
    public record SitFHoldPayload(boolean holding) implements CustomPacketPayload {
        public static final Type<SitFHoldPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("cooptest", "sit_f_hold"));
        public static final StreamCodec<FriendlyByteBuf, SitFHoldPayload> CODEC =
                StreamCodec.ofMember((v, buf) -> buf.writeBoolean(v.holding()),
                        buf -> new SitFHoldPayload(buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(SitFHoldPayload.ID, SitFHoldPayload.CODEC);
    }
    private static final Map<UUID, Double> sittingPlayers = new HashMap<>();
    private static final Map<UUID, UUID>   reachingSitter  = new HashMap<>();
    private static final Set<String>       activePickup    = new HashSet<>();
    public static boolean isSitting(UUID id) { return sittingPlayers.containsKey(id); }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SitFHoldPayload.ID, (payload, context) ->
                context.server().execute(() -> onFHold(context.player(), payload.holding())));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Map.Entry<UUID, Double> e : new HashMap<>(sittingPlayers).entrySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null) continue;
                double sitY = e.getValue() - 0.5;
                p.setDeltaMovement(0, 0, 0);
                p.syncVelocity = true;
                if (Math.abs(p.getY() - sitY) > 0.05) {
                    p.teleportTo(p.level(), p.getX(), sitY, p.getZ(),
                            java.util.Set.of(), p.getYRot(), p.getXRot(), false);
                }
            }
        });
    }
    public static int executeSit(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        UUID id = player.getUUID();
        if (isSitting(id)) {
            if (!isInPickup(id)) standup(player, null);
        } else {
            sit(player);
        }
        return 1;
    }
    private static void sit(ServerPlayer player) {
        UUID id = player.getUUID();
        double originalY = player.getY();
        double sitY = originalY - 0.5;
        sittingPlayers.put(id, originalY);
        player.teleportTo(player.level(),
                player.getX(), sitY, player.getZ(),
                java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(true));
        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.SITTING.ordinal());
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§7[Sitting — a friend can hold F to help you up]"));
    }
    private static void onFHold(ServerPlayer helper, boolean holding) {
        UUID hid = helper.getUUID();
        if (isSitting(hid)) return;
        if (!holding) {
            UUID sitterId = reachingSitter.remove(hid);
            if (sitterId == null) return;
            ServerPlayer sitter = helper.level().getServer().getPlayerList().getPlayer(sitterId);
            if (sitter == null || !isSitting(sitterId)) return;
            if (helper.distanceTo(sitter) > 1.5f) {
                PoseNetworking.broadcastAnimState(helper,
                        com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
                return;
            }
            startPickup(helper, sitter);
        } else {
            ServerPlayer nearest = null;
            double closest = 3.0;
            for (UUID sid : sittingPlayers.keySet()) {
                ServerPlayer s = helper.level().getServer().getPlayerList().getPlayer(sid);
                if (s != null && helper.distanceTo(s) < closest) { closest = helper.distanceTo(s); nearest = s; }
            }
            if (nearest == null) return;
            if (isInPickup(nearest.getUUID())) return;
            reachingSitter.put(hid, nearest.getUUID());
            PoseNetworking.broadcastAnimState(helper,
                    com.cooptest.client.CoopAnimationHandler.AnimState.REACH_DOWN.ordinal());
        }
    }
    private static void startPickup(ServerPlayer helper, ServerPlayer sitter) {
        UUID hid = helper.getUUID(), sid = sitter.getUUID();
        String k = hid + ":" + sid;
        final Double originalY = sittingPlayers.get(sid);
        final double sitY      = originalY != null ? originalY - 0.5 : sitter.getY();
        activePickup.add(k);
        Vec3 diff = sitter.position().subtract(helper.position());
        float helperYaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float sitterYaw  = helperYaw + 180f;
        helper.setYRot(helperYaw); helper.setYBodyRot(helperYaw); helper.setYHeadRot(helperYaw);
        sitter.setYRot(sitterYaw); sitter.setYBodyRot(sitterYaw); sitter.setYHeadRot(sitterYaw);
        helper.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        ServerPlayNetworking.send(helper, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(sitter, new ChargedDapHandler.PerfectDapFreezePayload(true));
        PoseNetworking.broadcastAnimState(helper,
                com.cooptest.client.CoopAnimationHandler.AnimState.REACH_PICKUP.ordinal());
        PoseNetworking.broadcastAnimState(sitter,
                com.cooptest.client.CoopAnimationHandler.AnimState.STAND_UP.ordinal());
        var server = helper.level().getServer();
        schedule(server, 2880L, () -> {
            ServerPlayer h = server.getPlayerList().getPlayer(hid);
            ServerPlayer s = server.getPlayerList().getPlayer(sid);
            if (h == null || s == null) return;
            Vec3 dir = s.position().subtract(h.position()).normalize();
            Vec3 mid = h.position().add(0, 1.2, 0).add(dir.scale(0.5));
            h.level().playSound(null, mid.x, mid.y, mid.z,
                    ModSounds.DAP_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.8f);
            h.level().playSound(null, mid.x, mid.y, mid.z,
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
            h.level().sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    mid.x, mid.y, mid.z, 8, 0.15, 0.15, 0.15, 0.06);
            h.level().sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                    mid.x, mid.y, mid.z, 4, 0.1, 0.1, 0.1, 0.04);
        });
        final long   LIFT_START_MS = 3880L;
        final long   LIFT_END_MS   = 5170L;
        final int    LIFT_STEPS    = 10;
        final long   stepInterval  = (LIFT_END_MS - LIFT_START_MS) / LIFT_STEPS;
        for (int i = 0; i <= LIFT_STEPS; i++) {
            final int step = i;
            long delay = LIFT_START_MS + step * stepInterval;
            schedule(server, delay, () -> {
                ServerPlayer s = server.getPlayerList().getPlayer(sid);
                if (s == null) return;
                if (step == 0) {
                    sittingPlayers.remove(sid);
                }
                if (originalY == null) return;
                double t   = (double) step / LIFT_STEPS;
                double liftY = sitY + (originalY - sitY) * t;
                s.teleportTo(s.level(), s.getX(), liftY, s.getZ(),
                        java.util.Set.of(), s.getYRot(), s.getXRot(), false);
            });
        }
        final double SITTER_PUSH_FORWARD  = 0.2;
        final double HELPER_PUSH_BACKWARD = 0.2;
        schedule(server, 4290L, () -> {
            ServerPlayer h = server.getPlayerList().getPlayer(hid);
            ServerPlayer s = server.getPlayerList().getPlayer(sid);
            if (h == null || s == null) return;
            Vec3 dir2 = s.position().subtract(h.position()).normalize();
            Vec3 newHelperPos = h.position().add(dir2.scale(-HELPER_PUSH_BACKWARD));
            h.teleportTo(h.level(), newHelperPos.x, h.getY(), newHelperPos.z,
                    java.util.Set.of(), h.getYRot(), h.getXRot(), false);
            Vec3 newSitterPos = s.position().add(dir2.scale(-SITTER_PUSH_FORWARD));
            s.teleportTo(s.level(), newSitterPos.x, s.getY(), newSitterPos.z,
                    java.util.Set.of(), s.getYRot(), s.getXRot(), false);
        });
        schedule(server, 5200L, () -> {
            ServerPlayer h = server.getPlayerList().getPlayer(hid);
            ServerPlayer s = server.getPlayerList().getPlayer(sid);
            if (h != null) ServerPlayNetworking.send(h, new ChargedDapHandler.PerfectDapFreezePayload(false));
            if (s != null) ServerPlayNetworking.send(s, new ChargedDapHandler.PerfectDapFreezePayload(false));
        });
        schedule(server, 6100L, () -> {
            activePickup.remove(k);
            ServerPlayer h = server.getPlayerList().getPlayer(hid);
            ServerPlayer s = server.getPlayerList().getPlayer(sid);
            if (h != null) PoseNetworking.broadcastAnimState(h,
                    com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
            if (s != null) PoseNetworking.broadcastAnimState(s,
                    com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
        });
    }
    private static void standup(ServerPlayer player, Double originalY) {
        UUID id = player.getUUID();
        Double oy = sittingPlayers.remove(id);
        if (oy != null) {
            player.teleportTo(player.level(),
                    player.getX(), oy, player.getZ(),
                    java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        }
        ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(false));
        PoseNetworking.broadcastAnimState(player,
                com.cooptest.client.CoopAnimationHandler.AnimState.NONE.ordinal());
    }
    private static boolean isInPickup(UUID id) {
        return activePickup.stream().anyMatch(k -> k.contains(id.toString()));
    }
    private static void schedule(net.minecraft.server.MinecraftServer server, long ms, Runnable r) {
        new java.util.Timer(true).schedule(new java.util.TimerTask() {
            @Override public void run() { server.execute(r); }
        }, ms);
    }
    public static void cleanup(UUID id) {
        sittingPlayers.remove(id);
        reachingSitter.remove(id);
        activePickup.removeIf(k -> k.contains(id.toString()));
    }
}