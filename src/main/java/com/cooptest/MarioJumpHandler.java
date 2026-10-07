package com.cooptest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MarioJumpHandler {
   private static final Map<UUID, Long> jumpCooldown = new HashMap<>();
   private static final long COOLDOWN_MS = 500L;
   private static final Map<UUID, Long> marioAnimEnd = new HashMap<>();
   private static final Map<UUID, Long> popAnimEnd = new HashMap<>();
   private static final long MARIO_ANIM_DURATION_MS = 500L;
   private static final long POP_ANIM_DURATION_MS = 417L;
   private static final double LAUNCH_VELOCITY = 0.68;

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(MarioJumpHandler.MarioJumpRequestPayload.ID, MarioJumpHandler.MarioJumpRequestPayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(MarioJumpHandler.MarioJumpRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> onMarioJumpRequest(player));
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         long now = System.currentTimeMillis();
         Iterator<Entry<UUID, Long>> marioIt = marioAnimEnd.entrySet().iterator();

         while (marioIt.hasNext()) {
            Entry<UUID, Long> entry = marioIt.next();
            if (now >= entry.getValue()) {
               ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
               if (player != null) {
                  PoseNetworking.broadcastAnimState(player, 0);
               }

               marioIt.remove();
            }
         }

         Iterator<Entry<UUID, Long>> popIt = popAnimEnd.entrySet().iterator();

         while (popIt.hasNext()) {
            Entry<UUID, Long> entry = popIt.next();
            if (now >= entry.getValue()) {
               ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
               if (player != null) {
                  PoseNetworking.broadcastAnimState(player, 0);
               }

               popIt.remove();
            }
         }
      });
   }

   private static void onMarioJumpRequest(ServerPlayer jumper) {
      if (jumper != null) {
         UUID jumperId = jumper.getUUID();
         long now = System.currentTimeMillis();
         if (!jumpCooldown.containsKey(jumperId) || now - jumpCooldown.get(jumperId) >= 500L) {
            if (!HighFiveHandler.isInBlockingState(jumperId)) {
               ServerPlayer target = findPlayerBelow(jumper);
               if (target != null) {
                  executeMarioJump(jumper, target);
                  jumpCooldown.put(jumperId, now);
               }
            }
         }
      }
   }

   private static ServerPlayer findPlayerBelow(ServerPlayer jumper) {
      ServerLevel world = jumper.level();
      Vec3 jumperPos = jumper.position();
      double jumperFeetY = jumperPos.y;
      AABB searchBox = new AABB(jumperPos.x - 0.8, jumperPos.y - 2.5, jumperPos.z - 0.8, jumperPos.x + 0.8, jumperPos.y + 0.5, jumperPos.z + 0.8);

      for (ServerPlayer target : world.getEntitiesOfClass(ServerPlayer.class, searchBox, p -> p != jumper && p.isAlive())) {
         Vec3 targetPos = target.position();
         double targetHeadY = targetPos.y + target.getEyeHeight() + 0.15;
         double heightDiff = jumperFeetY - targetHeadY;
         if (heightDiff >= -0.35 && heightDiff <= 0.5) {
            double horizDist = Math.sqrt(Math.pow(jumperPos.x - targetPos.x, 2.0) + Math.pow(jumperPos.z - targetPos.z, 2.0));
            if (horizDist <= 0.7) {
               return target;
            }
         }
      }

      return null;
   }

   private static void executeMarioJump(ServerPlayer jumper, ServerPlayer target) {
      ServerLevel world = jumper.level();
      Vec3 pos = jumper.position();
      long now = System.currentTimeMillis();
      System.out.println("[MARIO JUMP] Executing mario jump!");
      System.out.println("[MARIO JUMP] Jumper: " + jumper.getName().getString() + " (UUID: " + jumper.getUUID() + ")");
      System.out.println("[MARIO JUMP] Target: " + target.getName().getString() + " (UUID: " + target.getUUID() + ")");
      System.out.println("[MARIO JUMP] Broadcasting MARIO_JUMP (ordinal 30) to jumper");
      System.out.println("[MARIO JUMP] Broadcasting POP (ordinal 31) to target");
      Vec3 velocity = jumper.getDeltaMovement();
      jumper.setDeltaMovement(velocity.x, 0.68, velocity.z);
      jumper.syncVelocity = true;
      PoseNetworking.broadcastAnimState(jumper, 30);
      PoseNetworking.broadcastAnimState(target, 31);
      System.out.println("[MARIO JUMP] Animations broadcast complete IT WORKING FINALLY!");
      marioAnimEnd.put(jumper.getUUID(), now + 500L);
      popAnimEnd.put(target.getUUID(), now + 417L);
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.MARIO_JUMP, SoundSource.PLAYERS, 1.0F, 1.0F);
      jumper.sendOverlayMessage(Component.literal("§a WAHOO!"));
      target.sendOverlayMessage(Component.literal("§c BONK!"));
   }

   public static void cleanup(UUID playerId) {
      jumpCooldown.remove(playerId);
      marioAnimEnd.remove(playerId);
      popAnimEnd.remove(playerId);
   }

   public record MarioJumpRequestPayload() implements CustomPacketPayload {
      public static final Type<MarioJumpHandler.MarioJumpRequestPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "mario_jump_request"));
      public static final StreamCodec<FriendlyByteBuf, MarioJumpHandler.MarioJumpRequestPayload> CODEC = StreamCodec.unit(
         new MarioJumpHandler.MarioJumpRequestPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
