package com.cooptest;

import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.CoopCameraShakeHandler;
import com.cooptest.client.CoopChromaHandler;
import com.cooptest.client.CoopImpactHandler;
import com.cooptest.client.CoopRadialBlurHandler;
import com.cooptest.client.CoopScreenSquishHandler;
import com.cooptest.client.CoopShockwaveRenderer;
import com.cooptest.client.CoopSpeedLinesRenderer;
import com.cooptest.client.FallDapClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.MahitoClientHandler;
import com.cooptest.client.PushClientHandler;
import java.util.HashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PoseNetworking {
   public static final HashMap<UUID, PoseState> poseStates = new HashMap<>();
   public static final HashMap<UUID, Float> chargeProgress = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(PoseNetworking.PoseSyncPayload.ID, PoseNetworking.PoseSyncPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(PoseNetworking.PoseSyncPayload.ID, PoseNetworking.PoseSyncPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(PoseNetworking.ChargeSyncPayload.ID, PoseNetworking.ChargeSyncPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(PoseNetworking.ChargeSyncPayload.ID, PoseNetworking.ChargeSyncPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(PoseNetworking.ThrowAnimPayload.ID, PoseNetworking.ThrowAnimPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(PoseNetworking.ThrowAnimPayload.ID, PoseNetworking.ThrowAnimPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(PoseNetworking.AnimStateSyncPayload.ID, PoseNetworking.AnimStateSyncPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(PoseNetworking.AnimStateSyncPayload.ID, PoseNetworking.AnimStateSyncPayload.CODEC);
   }

   public static void registerServerReceiver() {
      ServerPlayNetworking.registerGlobalReceiver(PoseNetworking.PoseSyncPayload.ID, (payload, context) -> {
         UUID id = payload.playerId();
         PoseState state = PoseState.values()[payload.poseOrdinal()];
         context.server().execute(() -> {
            ServerPlayer requester = context.server().getPlayerList().getPlayer(id);
            if (state == PoseState.GRAB_READY && HighFiveHandler.isInBlockingState(id)) {
               if (requester != null) {
                  ServerPlayNetworking.send(requester, new PoseNetworking.PoseSyncPayload(id, PoseState.NONE.ordinal()));
               }
            } else if (state == PoseState.PUSH_IDLE && requester != null && !requester.getMainHandItem().isEmpty()) {
               requester.sendOverlayMessage(Component.literal("§cHold nothing in your main hand to push!"));
               ServerPlayNetworking.send(requester, new PoseNetworking.PoseSyncPayload(id, PoseState.NONE.ordinal()));
            } else {
               poseStates.put(id, state);

               for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
                  ServerPlayNetworking.send(player, new PoseNetworking.PoseSyncPayload(id, state.ordinal()));
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(PoseNetworking.ChargeSyncPayload.ID, (payload, context) -> {
         UUID id = payload.playerId();
         float progress = payload.progress();
         context.server().execute(() -> {
            chargeProgress.put(id, progress);

            for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
               if (!player.getUUID().equals(id)) {
                  ServerPlayNetworking.send(player, new PoseNetworking.ChargeSyncPayload(id, progress));
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(PoseNetworking.ThrowAnimPayload.ID, (payload, context) -> {
         UUID id = payload.playerId();
         context.server().execute(() -> {
            for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
               if (!player.getUUID().equals(id)) {
                  ServerPlayNetworking.send(player, new PoseNetworking.ThrowAnimPayload(id));
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(PoseNetworking.AnimStateSyncPayload.ID, (payload, context) -> {
         UUID id = payload.playerId();
         int animState = payload.animStateOrdinal();
         context.server().execute(() -> {
            for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
               ServerPlayNetworking.send(player, new PoseNetworking.AnimStateSyncPayload(id, animState));
            }
         });
      });
   }

   public static void registerClientReceiver() {
      ClientPlayNetworking.registerGlobalReceiver(PoseNetworking.PoseSyncPayload.ID, (payload, context) -> {
         UUID id = payload.playerId();
         PoseState state = PoseState.values()[payload.poseOrdinal()];
         poseStates.put(id, state);
         context.client().execute(() -> {
            if (context.client().level != null) {
               for (Player player : context.client().level.players()) {
                  if (player.getUUID().equals(id)) {
                     CoopAnimationHandler.updatePlayerAnimation(player, state);
                     break;
                  }
               }
            }
         });
      });
      ClientPlayNetworking.registerGlobalReceiver(
         PoseNetworking.ChargeSyncPayload.ID, (payload, context) -> chargeProgress.put(payload.playerId(), payload.progress())
      );
      ClientPlayNetworking.registerGlobalReceiver(
         PoseNetworking.ThrowAnimPayload.ID, (payload, context) -> ArmPoseTracker.throwAnimationStart.put(payload.playerId(), System.currentTimeMillis())
      );
      ClientPlayNetworking.registerGlobalReceiver(PoseNetworking.AnimStateSyncPayload.ID, (payload, context) -> {
         UUID id = payload.playerId();
         int animState = payload.animStateOrdinal();
         context.client().execute(() -> {
            if (context.client().level != null) {
               LocalPlayer localPlayer = context.client().player;
               if (animState == 0) {
                  ChargedDapClientHandler.cleanup(id);
                  CoopAnimationHandler.cleanup(id);
                  HighFiveClientHandler.cleanup(id);
                  PushClientHandler.cleanup(id);
                  MahitoClientHandler.cleanup(id);
                  FallDapClientHandler.cleanup(id);
                  ArmPoseTracker.cleanup(id);
               }

               Player targetPlayer = null;

               for (Player player : context.client().level.players()) {
                  if (player.getUUID().equals(id)) {
                     targetPlayer = player;
                     break;
                  }
               }

               CoopAnimationHandler.AnimState ownState = CoopAnimationHandler.getAnimState(id);
               if (animState == 0 || localPlayer == null || !id.equals(localPlayer.getUUID()) || ownState == null || ownState.ordinal() != animState) {
                  if (targetPlayer != null) {
                     if (localPlayer != null) {
                        boolean isPerfectDap = animState == 26;
                        boolean isDapHit = animState == 10;
                        boolean isParticipant = id.equals(localPlayer.getUUID()) || localPlayer.distanceTo(targetPlayer) < 10.0;
                        if (isParticipant) {
                           if (isPerfectDap) {
                              long shakeDuration = CoopImpactHandler.PERFECT_DAP_SEQUENCE.length * 33L;
                              CoopImpactHandler.start(CoopImpactHandler.PERFECT_DAP_SEQUENCE, 33L, true);
                              CoopCameraShakeHandler.shake(1.2F, shakeDuration);
                              CoopChromaHandler.start();
                              CoopRadialBlurHandler.start();
                              CoopSpeedLinesRenderer.start();
                              CoopScreenSquishHandler.trigger();
                              if (!localPlayer.getUUID().equals(targetPlayer.getUUID())) {
                                 Vec3 mid = localPlayer.position().add(targetPlayer.position()).scale(0.5).add(0.0, 1.4, 0.0);
                                 CoopShockwaveRenderer.start(mid);
                              } else {
                                 Vec3 front = localPlayer.position().add(localPlayer.getViewVector(1.0F).scale(1.5)).add(0.0, 1.0, 0.0);
                                 CoopShockwaveRenderer.start(front);
                              }
                           } else if (isDapHit && CoopMovesConfig.get().impactFramesEveryDap) {
                              long shakeDuration = CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L;
                              CoopImpactHandler.start(CoopImpactHandler.REGULAR_DAP_SEQUENCE, 33L, true);
                              CoopCameraShakeHandler.shake(0.6F, shakeDuration);
                              CoopChromaHandler.start();
                              CoopRadialBlurHandler.start();
                              CoopSpeedLinesRenderer.start();
                              CoopScreenSquishHandler.trigger();
                              if (!localPlayer.getUUID().equals(targetPlayer.getUUID())) {
                                 Vec3 mid = localPlayer.position().add(targetPlayer.position()).scale(0.5).add(0.0, 1.4, 0.0);
                                 CoopShockwaveRenderer.start(mid);
                              } else {
                                 Vec3 front = localPlayer.position().add(localPlayer.getViewVector(1.0F).scale(1.5)).add(0.0, 1.0, 0.0);
                                 CoopShockwaveRenderer.start(front);
                              }
                           }
                        }
                     }

                     CoopAnimationHandler.setAnimStateFromNetwork(targetPlayer, animState);
                  }
               }
            }
         });
      });
   }

   public static void sendPoseToServer(UUID playerId, PoseState state) {
      ClientPlayNetworking.send(new PoseNetworking.PoseSyncPayload(playerId, state.ordinal()));
   }

   public static void sendChargeProgress(UUID playerId, float progress) {
      ClientPlayNetworking.send(new PoseNetworking.ChargeSyncPayload(playerId, progress));
   }

   public static void sendThrowAnimation(UUID playerId) {
      ClientPlayNetworking.send(new PoseNetworking.ThrowAnimPayload(playerId));
   }

   public static void sendAnimState(UUID playerId, int animStateOrdinal) {
      ClientPlayNetworking.send(new PoseNetworking.AnimStateSyncPayload(playerId, animStateOrdinal));
   }

   public static void broadcastPoseChange(MinecraftServer server, UUID playerId, PoseState state) {
      poseStates.put(playerId, state);

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         ServerPlayNetworking.send(player, new PoseNetworking.PoseSyncPayload(playerId, state.ordinal()));
      }
   }

   public static void broadcastAnimState(ServerPlayer sourcePlayer, int animStateOrdinal) {
      MinecraftServer server = sourcePlayer.level().getServer();
      if (server != null) {
         UUID playerId = sourcePlayer.getUUID();
         PoseNetworking.AnimStateSyncPayload payload = new PoseNetworking.AnimStateSyncPayload(playerId, animStateOrdinal);

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
         }
      }
   }

   public record AnimStateSyncPayload(UUID playerId, int animStateOrdinal) implements CustomPacketPayload {
      public static final Type<PoseNetworking.AnimStateSyncPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "anim_state_sync"));
      public static final StreamCodec<FriendlyByteBuf, PoseNetworking.AnimStateSyncPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.playerId);
         buf.writeInt(payload.animStateOrdinal);
      }, buf -> new PoseNetworking.AnimStateSyncPayload(buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ChargeSyncPayload(UUID playerId, float progress) implements CustomPacketPayload {
      public static final Type<PoseNetworking.ChargeSyncPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "charge_sync"));
      public static final StreamCodec<FriendlyByteBuf, PoseNetworking.ChargeSyncPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.playerId);
         buf.writeFloat(payload.progress);
      }, buf -> new PoseNetworking.ChargeSyncPayload(buf.readUUID(), buf.readFloat()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record PoseSyncPayload(UUID playerId, int poseOrdinal) implements CustomPacketPayload {
      public static final Type<PoseNetworking.PoseSyncPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "pose_sync"));
      public static final StreamCodec<FriendlyByteBuf, PoseNetworking.PoseSyncPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.playerId);
         buf.writeInt(payload.poseOrdinal);
      }, buf -> new PoseNetworking.PoseSyncPayload(buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ThrowAnimPayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<PoseNetworking.ThrowAnimPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "throw_anim"));
      public static final StreamCodec<FriendlyByteBuf, PoseNetworking.ThrowAnimPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> buf.writeUUID(payload.playerId), buf -> new PoseNetworking.ThrowAnimPayload(buf.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
