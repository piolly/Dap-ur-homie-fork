package com.cooptest;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class PushInteractionHandler {
   private static final float PUSH_RANGE = 2.5F;
   private static final long HOLD_REQUIRED_MS = 1500L;
   private static final long READY_WINDOW_MS = 3000L;
   private static final long COOLDOWN_MS = 1500L;
   private static final long PUSH_IMMUNITY_MS = 500L;
   private static final long JUMP_WINDOW_MS = 800L;
   private static final double VEL_LOW = 0.5;
   private static final double VEL_MEDIUM = 1.8;
   private static final double VEL_HIGH = 3.5;
   private static final HashMap<UUID, UUID> holdTarget = new HashMap<>();
   private static final HashMap<UUID, Long> holdStart = new HashMap<>();
   private static final HashMap<UUID, UUID> readyPushers = new HashMap<>();
   private static final HashMap<UUID, Long> readyStart = new HashMap<>();
   private static final HashMap<UUID, Long> cooldowns = new HashMap<>();
   public static final HashMap<UUID, Long> pushImmunity = new HashMap<>();
   public static final HashMap<UUID, Long> lastJumpTime = new HashMap<>();
   public static final Identifier PUSH_ANIM_ID = Identifier.fromNamespaceAndPath("cooptest", "push_anim");

   public static void registerPayloads() {
      PayloadTypeRegistry.playS2C().register(PushInteractionHandler.PushAnimPayload.ID, PushInteractionHandler.PushAnimPayload.CODEC);
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(PushInteractionHandler::tick);
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer sp) {
            if (!(entity instanceof ServerPlayer target)) {
               return InteractionResult.PASS;
            } else {
               if (!CoopMovesConfig.get().enablePush) {
                  return InteractionResult.PASS;
               }

               long now = System.currentTimeMillis();
               if (sp.isShiftKeyDown()) {
                  if (HighFiveHandler.isInBlockingState(sp.getUUID())) {
                     return InteractionResult.PASS;
                  }

                  if (isOnCooldown(sp.getUUID(), now)) {
                     return InteractionResult.PASS;
                  }

                  if (readyPushers.containsKey(sp.getUUID())) {
                     return InteractionResult.PASS;
                  }

                  if (sp.distanceTo(target) > 2.5F) {
                     return InteractionResult.PASS;
                  }

                  UUID prevTarget = holdTarget.get(sp.getUUID());
                  if (!target.getUUID().equals(prevTarget)) {
                     holdTarget.put(sp.getUUID(), target.getUUID());
                     holdStart.put(sp.getUUID(), now);
                  }

                  return InteractionResult.SUCCESS;
               } else {
                  UUID intendedTarget = readyPushers.get(target.getUUID());
                  if (intendedTarget != null && intendedTarget.equals(sp.getUUID())) {
                     Long rs = readyStart.get(target.getUUID());
                     if (rs == null || now - rs > 3000L) {
                        return InteractionResult.PASS;
                     }

                     if (isOnCooldown(target.getUUID(), now)) {
                        return InteractionResult.PASS;
                     }

                     Long jt = lastJumpTime.get(sp.getUUID());
                     boolean recentJump = jt != null && now - jt < 800L;
                     double vel;
                     if (recentJump) {
                        vel = capToCeiling(sp, 3.5);
                     } else if (sp.isShiftKeyDown()) {
                        vel = capToCeiling(sp, 0.5);
                     } else {
                        vel = capToCeiling(sp, 1.8);
                     }

                     readyPushers.remove(target.getUUID());
                     readyStart.remove(target.getUUID());
                     executePush(target, sp, vel, now);
                     return InteractionResult.SUCCESS;
                  } else {
                     return InteractionResult.PASS;
                  }
               }
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   public static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();

      for (Entry<UUID, UUID> entry : new HashMap<>(holdTarget).entrySet()) {
         UUID pusherId = entry.getKey();
         UUID targetId = entry.getValue();
         Long startMs = holdStart.get(pusherId);
         if (startMs == null) {
            holdTarget.remove(pusherId);
         } else {
            ServerPlayer pusher = server.getPlayerList().getPlayer(pusherId);
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (pusher == null || !pusher.isShiftKeyDown() || target == null || pusher.distanceTo(target) > 2.5F) {
               holdTarget.remove(pusherId);
               holdStart.remove(pusherId);
            } else if (readyPushers.containsKey(pusherId)) {
               holdTarget.remove(pusherId);
               holdStart.remove(pusherId);
            } else if (now - startMs >= 1500L) {
               holdTarget.remove(pusherId);
               holdStart.remove(pusherId);
               readyPushers.put(pusherId, targetId);
               readyStart.put(pusherId, now);
               Vec3 mid = pusher.position().add(target.position()).scale(0.5);
               pusher.level().playSound(null, mid.x, mid.y, mid.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.8F);
               pusher.displayClientMessage(Component.literal("§eTell homie to right-click!"), true);
               target.displayClientMessage(Component.literal("§e[Right-click to launch!]"), true);
            }
         }
      }

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (!p.onGround() && p.getDeltaMovement().y > 0.08) {
            lastJumpTime.put(p.getUUID(), now);
         }
      }

      for (Entry<UUID, UUID> re : new HashMap<>(readyPushers).entrySet()) {
         UUID pusherId = re.getKey();
         UUID intendedTarget = re.getValue();
         Long rs = readyStart.get(pusherId);
         if (rs != null && now - rs <= 3000L) {
            ServerPlayer pusher = server.getPlayerList().getPlayer(pusherId);
            if (pusher != null) {
               for (ServerPlayer nearby : server.getPlayerList().getPlayers()) {
                  if (!nearby.getUUID().equals(pusherId) && pusher.distanceTo(nearby) <= 2.5F) {
                     UUID nearbyId = nearby.getUUID();
                     if (!nearbyId.equals(intendedTarget)) {
                        readyPushers.put(pusherId, nearbyId);
                        nearby.displayClientMessage(Component.literal("§e[Right-click to launch!]"), true);
                     }
                     break;
                  }
               }
            }
         }
      }

      readyPushers.entrySet().removeIf(e -> {
         Long t = readyStart.get(e.getKey());
         return t == null || now - t > 3000L;
      });
      readyStart.entrySet().removeIf(e -> now - e.getValue() > 3000L);
      holdStart.entrySet().removeIf(e -> now - e.getValue() > 10000L);
      lastJumpTime.entrySet().removeIf(e -> now - e.getValue() > 3200L);
      cooldowns.entrySet().removeIf(e -> now - e.getValue() > 3000L);
   }

   private static void executePush(ServerPlayer pusher, ServerPlayer target, double velocity, long now) {
      PushInteractionHandler.PushAnimPayload pkt = new PushInteractionHandler.PushAnimPayload(pusher.getUUID());

      for (ServerPlayer p : PlayerLookup.tracking(pusher)) {
         ServerPlayNetworking.send(p, pkt);
      }

      ServerPlayNetworking.send(pusher, pkt);
      target.setDeltaMovement(target.getDeltaMovement().x, 0.0, target.getDeltaMovement().z);
      target.push(0.0, velocity, 0.0);
      target.hurtMarked = true;
      pushImmunity.put(target.getUUID(), now);
      LaunchedPlayerTracker.markPlayerAsLaunched(target.getUUID());
      UUID carried = GrabMechanic.holding.get(target.getUUID());
      if (carried != null) {
         ServerPlayer c = target.level().getServer().getPlayerList().getPlayer(carried);
         if (c != null) {
            c.push(0.0, velocity * 0.85, 0.0);
            c.hurtMarked = true;
            LaunchedPlayerTracker.markPlayerAsLaunched(c.getUUID());
            pushImmunity.put(c.getUUID(), now);
         }
      }

      cooldowns.put(pusher.getUUID(), now);
      PoseNetworking.broadcastPoseChange(Objects.requireNonNull(pusher.level().getServer()), pusher.getUUID(), PoseState.PUSH_ACTION);
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

   private static boolean isOnCooldown(UUID uuid, long now) {
      Long t = cooldowns.get(uuid);
      return t != null && now - t < 1500L;
   }

   public static boolean hasPushImmunity(UUID uuid) {
      Long t = pushImmunity.get(uuid);
      if (t == null) {
         return false;
      }

      if (System.currentTimeMillis() - t < 500L) {
         return true;
      }

      pushImmunity.remove(uuid);
      return false;
   }

   public static void cleanupExpiredImmunity() {
      long now = System.currentTimeMillis();
      pushImmunity.entrySet().removeIf(e -> now - e.getValue() > 500L);
   }

   public record PushAnimPayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<PushInteractionHandler.PushAnimPayload> ID = new Type(PushInteractionHandler.PUSH_ANIM_ID);
      public static final StreamCodec<FriendlyByteBuf, PushInteractionHandler.PushAnimPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> buf.writeUUID(p.playerId), buf -> new PushInteractionHandler.PushAnimPayload(buf.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
