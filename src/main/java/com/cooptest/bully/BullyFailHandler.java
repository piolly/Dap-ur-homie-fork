package com.cooptest.bully;

import com.cooptest.ChargedDapHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.client.CoopAnimationHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public class BullyFailHandler {
   private static final double STAND_Y_OFFSET = -0.5;
   private static final Map<UUID, ArmorStand> failStands = new HashMap<>();
   private static final Set<UUID> inFail = new HashSet<>();

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         for (UUID id : new ArrayList<>(inFail)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
               forceCleanup(id);
            } else if (player.isShiftKeyDown() || player.getVehicle() == null) {
               exitFail(player);
            }
         }
      });
   }

   public static void enterFail(ServerPlayer player, double peakHeight) {
      UUID id = player.getUUID();
      if (!inFail.contains(id)) {
         if (!BullyDapHandler.isInBullyDap(id)) {
            ServerLevel world = player.level();
            Vec3 pos = player.position();
            ChargedDapHandler.chargeStartTime.remove(id);
            ChargedDapHandler.fireLevel.remove(id);
            ChargedDapHandler.ChargeSyncPayload cancel = new ChargedDapHandler.ChargeSyncPayload(id, 0.0F, 0.0F, false);

            for (ServerPlayer pl : PlayerLookup.all(player.level().getServer())) {
               ServerPlayNetworking.send(pl, cancel);
            }

            float extra = Math.max(0.0F, (float)peakHeight - 3.0F);
            if (extra > 0.0F) {
               player.hurtServer(world, world.damageSources().fall(), extra);
            }

            ArmorStand stand = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, world);
            stand.setPos(pos.x, pos.y + -0.5, pos.z);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setPermanentlyInvulnerable(true);
            stand.setSilent(true);
            stand.setYRot(player.getYRot());
            stand.setYHeadRot(player.getYRot());
            world.addFreshEntity(stand);
            player.startRiding(stand, true, true);
            PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.BULLY_FAIL.ordinal());
            inFail.add(id);
            failStands.put(id, stand);
            player.sendOverlayMessage(Component.literal("§c§l⚡ Miss!  §rPress §nSHIFT§r to get up"));
         }
      }
   }

   private static void exitFail(ServerPlayer player) {
      UUID id = player.getUUID();
      inFail.remove(id);
      player.stopRiding();
      ArmorStand stand = failStands.remove(id);
      if (stand != null && !stand.isRemoved()) {
         stand.discard();
      }

      PoseNetworking.broadcastAnimState(player, 0);
   }

   private static void forceCleanup(UUID id) {
      inFail.remove(id);
      ArmorStand stand = failStands.remove(id);
      if (stand != null && !stand.isRemoved()) {
         stand.discard();
      }
   }

   public static void cleanup(UUID id) {
      forceCleanup(id);
   }

   public static boolean isInFail(UUID id) {
      return inFail.contains(id);
   }
}
