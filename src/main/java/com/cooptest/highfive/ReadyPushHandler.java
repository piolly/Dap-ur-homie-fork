package com.cooptest.highfive;

import com.cooptest.CoopMovesConfig;
import com.cooptest.GrabMechanic;
import com.cooptest.LaunchedPlayerTracker;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.PushInteractionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ReadyPushHandler {
   public static final long ARM_REQUIRED_MS = 1500L;
   public static final float PUSH_RANGE = 2.5F;
   public static final long INPUT_TIMEOUT_MS = 400L;
   public static final long START_ANIM_MS = 400L;
   public static final long PUSH_ANIM_MS = 400L;
   public static final long JUMP_WINDOW_MS = 800L;
   public static final double VEL_LOW = 0.5;
   public static final double VEL_MEDIUM = 1.8;
   public static final double VEL_HIGH = 3.5;
   private static final int ANIM_NONE = 0;
   private static final int ANIM_PUSH_START = 13;
   private static final int ANIM_PUSH_IDLE = 14;
   private static final int ANIM_PUSHING = 15;
   private static final Map<UUID, Long> armingSince = new HashMap<>();
   private static final Map<UUID, Long> inputLastSeen = new HashMap<>();
   private static final Map<UUID, Long> armed = new HashMap<>();
   private static final Map<UUID, Long> pushingUntil = new HashMap<>();
   private static final Map<UUID, Long> lastJumpTime = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(ReadyPushHandler.PushInputPayload.ID, ReadyPushHandler.PushInputPayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(ReadyPushHandler.PushInputPayload.ID, (payload, ctx) -> {
         ServerPlayer player = ctx.player();
         ctx.server().execute(() -> onInput(player, payload.shift(), payload.rightClick()));
      });
      ServerTickEvents.END_SERVER_TICK.register(ReadyPushHandler::tick);
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
         }

         if (!CoopMovesConfig.get().enablePush) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer launchee) {
            if (entity instanceof ServerPlayer pusher) {
               if (launchee == pusher) {
                  return InteractionResult.PASS;
               }

               if (!armed.containsKey(pusher.getUUID())) {
                  return InteractionResult.PASS;
               }

               if (isMidPush(pusher.getUUID())) {
                  return InteractionResult.PASS;
               }

               if (pusher.distanceTo(launchee) > 2.5F) {
                  return InteractionResult.PASS;
               }

               if (PushInteractionHandler.hasPushImmunity(launchee.getUUID())) {
                  return InteractionResult.PASS;
               }

               launch(pusher, launchee);
               return InteractionResult.SUCCESS;
            } else {
               return InteractionResult.PASS;
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void onInput(ServerPlayer player, boolean shift, boolean rightClick) {
      UUID id = player.getUUID();
      long now = System.currentTimeMillis();
      inputLastSeen.put(id, now);
      if (armed.containsKey(id)) {
         if (!shift) {
            disarm(player);
         }
      } else if (PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE) != PoseState.GRAB_READY) {
         armingSince.remove(id);
      } else {
         if (shift && rightClick) {
            armingSince.putIfAbsent(id, now);
         } else {
            armingSince.remove(id);
         }
      }
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();

      for (UUID id : new ArrayList<>(inputLastSeen.keySet())) {
         if (now - inputLastSeen.get(id) > 400L) {
            inputLastSeen.remove(id);
            armingSince.remove(id);
            if (armed.containsKey(id)) {
               ServerPlayer p = server.getPlayerList().getPlayer(id);
               if (p != null) {
                  disarm(p);
               } else {
                  cleanup(id);
               }
            }
         }
      }

      for (UUID id : new ArrayList<>(armingSince.keySet())) {
         if (now - armingSince.get(id) >= 1500L) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            armingSince.remove(id);
            if (p != null && PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE) == PoseState.GRAB_READY) {
               arm(p);
            }
         }
      }

      for (UUID id : new ArrayList<>(armed.keySet())) {
         ServerPlayer p = server.getPlayerList().getPlayer(id);
         if (p != null && p.isAlive()) {
            Long until = pushingUntil.get(id);
            if (until != null && now >= until) {
               pushingUntil.remove(id);
               PoseNetworking.broadcastAnimState(p, 14);
            }

            Long began = armed.get(id);
            if (began != null && began > 0L && now - began >= 400L) {
               armed.put(id, 0L);
               PoseNetworking.broadcastAnimState(p, 14);
            }
         } else {
            cleanup(id);
         }
      }

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (!p.onGround() && p.getDeltaMovement().y > 0.08) {
            lastJumpTime.put(p.getUUID(), now);
         }
      }

      lastJumpTime.entrySet().removeIf(e -> now - e.getValue() > 3200L);
   }

   private static void arm(ServerPlayer p) {
      MinecraftServer server = p.level().getServer();
      if (server != null) {
         UUID id = p.getUUID();
         armed.put(id, System.currentTimeMillis());
         PoseNetworking.poseStates.put(id, PoseState.PUSH_IDLE);
         PoseNetworking.broadcastPoseChange(server, id, PoseState.PUSH_IDLE);
         PoseNetworking.broadcastAnimState(p, 13);
         Vec3 pos = p.position();
         p.level().playSound(null, pos.x, pos.y, pos.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.8F);
         p.sendOverlayMessage(Component.literal("§eReady to boost — homie right-clicks you!"));
      }
   }

   private static void disarm(ServerPlayer p) {
      MinecraftServer server = p.level().getServer();
      UUID id = p.getUUID();
      armed.remove(id);
      pushingUntil.remove(id);
      armingSince.remove(id);
      if (server != null) {
         PoseNetworking.broadcastAnimState(p, 0);
         PoseNetworking.poseStates.put(id, PoseState.GRAB_READY);
         PoseNetworking.broadcastPoseChange(server, id, PoseState.GRAB_READY);
      }
   }

   private static void launch(ServerPlayer pusher, ServerPlayer launchee) {
      long now = System.currentTimeMillis();
      Long jt = lastJumpTime.get(launchee.getUUID());
      boolean recentJump = jt != null && now - jt < 800L;
      boolean airborne = !launchee.onGround();
      double vel;
      String tier;
      if (airborne || recentJump) {
         vel = capToCeiling(launchee, 3.5);
         tier = "§bHIGH";
      } else if (launchee.isShiftKeyDown()) {
         vel = capToCeiling(launchee, 0.5);
         tier = "§7LOW";
      } else {
         vel = capToCeiling(launchee, 1.8);
         tier = "§eMEDIUM";
      }

      launchee.sendOverlayMessage(Component.literal(tier + " boost!"));
      pushingUntil.put(pusher.getUUID(), now + 400L);
      PoseNetworking.broadcastAnimState(pusher, 15);
      PushInteractionHandler.PushAnimPayload pkt = new PushInteractionHandler.PushAnimPayload(pusher.getUUID());

      for (ServerPlayer p : PlayerLookup.tracking(pusher)) {
         ServerPlayNetworking.send(p, pkt);
      }

      ServerPlayNetworking.send(pusher, pkt);
      launchee.setDeltaMovement(launchee.getDeltaMovement().x, 0.0, launchee.getDeltaMovement().z);
      launchee.push(0.0, vel, 0.0);
      launchee.hurtMarked = true;
      PushInteractionHandler.pushImmunity.put(launchee.getUUID(), now);
      LaunchedPlayerTracker.markPlayerAsLaunched(launchee.getUUID());
      UUID carried = GrabMechanic.holding.get(launchee.getUUID());
      if (carried != null) {
         ServerPlayer c = launchee.level().getServer().getPlayerList().getPlayer(carried);
         if (c != null) {
            c.push(0.0, vel * 0.85, 0.0);
            c.hurtMarked = true;
            LaunchedPlayerTracker.markPlayerAsLaunched(c.getUUID());
            PushInteractionHandler.pushImmunity.put(c.getUUID(), now);
         }
      }
   }

   private static double capToCeiling(ServerPlayer t, double base) {
      BlockPos pos = t.blockPosition();

      for (int y = 1; y <= 15; y++) {
         BlockPos check = pos.above(y);
         BlockState state = t.level().getBlockState(check);
         if (!state.isAir() && state.isRedstoneConductor(t.level(), check)) {
            return Math.min(base, Math.sqrt(3.2 * Math.max(2, y - 1)));
         }
      }

      return base;
   }

   public static boolean isArmed(UUID id) {
      return armed.containsKey(id);
   }

   private static boolean isMidPush(UUID id) {
      Long until = pushingUntil.get(id);
      return until != null && System.currentTimeMillis() < until;
   }

   public static void cleanup(UUID id) {
      armed.remove(id);
      armingSince.remove(id);
      inputLastSeen.remove(id);
      pushingUntil.remove(id);
      lastJumpTime.remove(id);
   }

   public record PushInputPayload(boolean shift, boolean rightClick) implements CustomPacketPayload {
      public static final Type<ReadyPushHandler.PushInputPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "readypush_input"));
      public static final StreamCodec<FriendlyByteBuf, ReadyPushHandler.PushInputPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeBoolean(p.shift);
         buf.writeBoolean(p.rightClick);
      }, buf -> new ReadyPushHandler.PushInputPayload(buf.readBoolean(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
