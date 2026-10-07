package com.cooptest;

import com.cooptest.client.CoopAnimationHandler;
import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

public class SitHandler {
   private static final Map<UUID, Double> sittingPlayers = new HashMap<>();
   private static final Map<UUID, UUID> reachingSitter = new HashMap<>();
   private static final Set<String> activePickup = new HashSet<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(SitHandler.SitFHoldPayload.ID, SitHandler.SitFHoldPayload.CODEC);
   }

   public static boolean isSitting(UUID id) {
      return sittingPlayers.containsKey(id);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(
         SitHandler.SitFHoldPayload.ID, (payload, context) -> context.server().execute(() -> onFHold(context.player(), payload.holding()))
      );
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         for (Entry<UUID, Double> e : new HashMap<>(sittingPlayers).entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
               double sitY = e.getValue() - 0.5;
               p.setDeltaMovement(0.0, 0.0, 0.0);
               p.syncVelocity = true;
               if (Math.abs(p.getY() - sitY) > 0.05) {
                  p.teleportTo(p.level(), p.getX(), sitY, p.getZ(), Set.of(), p.getYRot(), p.getXRot(), false);
               }
            }
         }
      });
   }

   public static int executeSit(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayer();
      if (player == null) {
         return 0;
      }

      UUID id = player.getUUID();
      if (isSitting(id)) {
         if (!isInPickup(id)) {
            standup(player, null);
         }
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
      player.teleportTo(player.level(), player.getX(), sitY, player.getZ(), Set.of(), player.getYRot(), player.getXRot(), false);
      ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(true));
      PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.SITTING.ordinal());
      player.sendOverlayMessage(Component.literal("§7[Sitting — a friend can hold F to help you up]"));
   }

   private static void onFHold(ServerPlayer helper, boolean holding) {
      UUID hid = helper.getUUID();
      if (!isSitting(hid)) {
         if (!holding) {
            UUID sitterId = reachingSitter.remove(hid);
            if (sitterId == null) {
               return;
            }

            ServerPlayer sitter = helper.level().getServer().getPlayerList().getPlayer(sitterId);
            if (sitter == null || !isSitting(sitterId)) {
               return;
            }

            if (helper.distanceTo(sitter) > 1.5F) {
               PoseNetworking.broadcastAnimState(helper, CoopAnimationHandler.AnimState.NONE.ordinal());
               return;
            }

            startPickup(helper, sitter);
         } else {
            ServerPlayer nearest = null;
            double closest = 3.0;

            for (UUID sid : sittingPlayers.keySet()) {
               ServerPlayer s = helper.level().getServer().getPlayerList().getPlayer(sid);
               if (s != null && helper.distanceTo(s) < closest) {
                  closest = helper.distanceTo(s);
                  nearest = s;
               }
            }

            if (nearest == null) {
               return;
            }

            if (isInPickup(nearest.getUUID())) {
               return;
            }

            reachingSitter.put(hid, nearest.getUUID());
            PoseNetworking.broadcastAnimState(helper, CoopAnimationHandler.AnimState.REACH_DOWN.ordinal());
         }
      }
   }

   private static void startPickup(ServerPlayer helper, ServerPlayer sitter) {
      UUID hid = helper.getUUID();
      UUID sid = sitter.getUUID();
      String k = hid + ":" + sid;
      Double originalY = sittingPlayers.get(sid);
      double sitY = originalY != null ? originalY - 0.5 : sitter.getY();
      activePickup.add(k);
      Vec3 diff = sitter.position().subtract(helper.position());
      float helperYaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
      float sitterYaw = helperYaw + 180.0F;
      helper.setYRot(helperYaw);
      helper.setYBodyRot(helperYaw);
      helper.setYHeadRot(helperYaw);
      sitter.setYRot(sitterYaw);
      sitter.setYBodyRot(sitterYaw);
      sitter.setYHeadRot(sitterYaw);
      helper.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
      ServerPlayNetworking.send(helper, new ChargedDapHandler.PerfectDapFreezePayload(true));
      ServerPlayNetworking.send(sitter, new ChargedDapHandler.PerfectDapFreezePayload(true));
      PoseNetworking.broadcastAnimState(helper, CoopAnimationHandler.AnimState.REACH_PICKUP.ordinal());
      PoseNetworking.broadcastAnimState(sitter, CoopAnimationHandler.AnimState.STAND_UP.ordinal());
      MinecraftServer server = helper.level().getServer();
      schedule(server, 2880L, () -> {
         ServerPlayer h = server.getPlayerList().getPlayer(hid);
         ServerPlayer s = server.getPlayerList().getPlayer(sid);
         if (h != null && s != null) {
            Vec3 dir = s.position().subtract(h.position()).normalize();
            Vec3 mid = h.position().add(0.0, 1.2, 0.0).add(dir.scale(0.5));
            h.level().playSound(null, mid.x, mid.y, mid.z, ModSounds.DAP_HIT, SoundSource.PLAYERS, 1.2F, 0.8F);
            h.level().playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
            h.level().sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 8, 0.15, 0.15, 0.15, 0.06);
            h.level().sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 4, 0.1, 0.1, 0.1, 0.04);
         }
      });
      long LIFT_START_MS = 3880L;
      long LIFT_END_MS = 5170L;
      int LIFT_STEPS = 10;
      long stepInterval = 129L;

      for (int i = 0; i <= 10; i++) {
         int step = i;
         long delay = 3880L + step * 129L;
         schedule(server, delay, () -> {
            ServerPlayer s = server.getPlayerList().getPlayer(sid);
            if (s != null) {
               if (step == 0) {
                  sittingPlayers.remove(sid);
               }

               if (originalY != null) {
                  double t = step / 10.0;
                  double liftY = sitY + (originalY - sitY) * t;
                  s.teleportTo(s.level(), s.getX(), liftY, s.getZ(), Set.of(), s.getYRot(), s.getXRot(), false);
               }
            }
         });
      }

      double SITTER_PUSH_FORWARD = 0.2;
      double HELPER_PUSH_BACKWARD = 0.2;
      schedule(server, 4290L, () -> {
         ServerPlayer h = server.getPlayerList().getPlayer(hid);
         ServerPlayer s = server.getPlayerList().getPlayer(sid);
         if (h != null && s != null) {
            Vec3 dir2 = s.position().subtract(h.position()).normalize();
            Vec3 newHelperPos = h.position().add(dir2.scale(-0.2));
            h.teleportTo(h.level(), newHelperPos.x, h.getY(), newHelperPos.z, Set.of(), h.getYRot(), h.getXRot(), false);
            Vec3 newSitterPos = s.position().add(dir2.scale(-0.2));
            s.teleportTo(s.level(), newSitterPos.x, s.getY(), newSitterPos.z, Set.of(), s.getYRot(), s.getXRot(), false);
         }
      });
      schedule(server, 5200L, () -> {
         ServerPlayer h = server.getPlayerList().getPlayer(hid);
         ServerPlayer s = server.getPlayerList().getPlayer(sid);
         if (h != null) {
            ServerPlayNetworking.send(h, new ChargedDapHandler.PerfectDapFreezePayload(false));
         }

         if (s != null) {
            ServerPlayNetworking.send(s, new ChargedDapHandler.PerfectDapFreezePayload(false));
         }
      });
      schedule(server, 6100L, () -> {
         activePickup.remove(k);
         ServerPlayer h = server.getPlayerList().getPlayer(hid);
         ServerPlayer s = server.getPlayerList().getPlayer(sid);
         if (h != null) {
            PoseNetworking.broadcastAnimState(h, CoopAnimationHandler.AnimState.NONE.ordinal());
         }

         if (s != null) {
            PoseNetworking.broadcastAnimState(s, CoopAnimationHandler.AnimState.NONE.ordinal());
         }
      });
   }

   private static void standup(ServerPlayer player, Double originalY) {
      UUID id = player.getUUID();
      Double oy = sittingPlayers.remove(id);
      if (oy != null) {
         player.teleportTo(player.level(), player.getX(), oy, player.getZ(), Set.of(), player.getYRot(), player.getXRot(), false);
      }

      ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(false));
      PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.NONE.ordinal());
   }

   private static boolean isInPickup(UUID id) {
      return activePickup.stream().anyMatch(k -> k.contains(id.toString()));
   }

   private static void schedule(MinecraftServer server, long ms, Runnable r) {
      new Timer(true).schedule(new TimerTask() {
         @Override
         public void run() {
            server.execute(r);
         }
      }, ms);
   }

   public static void cleanup(UUID id) {
      sittingPlayers.remove(id);
      reachingSitter.remove(id);
      activePickup.removeIf(k -> k.contains(id.toString()));
   }

   public record SitFHoldPayload(boolean holding) implements CustomPacketPayload {
      public static final Type<SitHandler.SitFHoldPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "sit_f_hold"));
      public static final StreamCodec<FriendlyByteBuf, SitHandler.SitFHoldPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeBoolean(v.holding()), buf -> new SitHandler.SitFHoldPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
