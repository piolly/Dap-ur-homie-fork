package com.cooptest;

import com.cooptest.bros.BrosHandler;
import com.cooptest.bully.BullyDapHandler;
import com.cooptest.bully.BullyFailHandler;
import com.cooptest.highfive.HighFiveShakeHandler;
import com.cooptest.highfive.ReadyFiveHandler;
import com.cooptest.highfive.ReadyHugHandler;
import com.cooptest.highfive.ReadyPushHandler;
import com.cooptest.meme.SpinYeetHandler;
import com.cooptest.spin.HandSpinHandler;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class PlayerCleanupHandler {
   public static void register() {
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         ServerPlayer player = handler.getPlayer();
         runFullCleanup(server, player, player.getUUID(), "DISCONNECT");
      });
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
         if (entity instanceof ServerPlayer player) {
            MinecraftServer server = player.level().getServer();
            if (server != null) {
               runFullCleanup(server, player, player.getUUID(), "DEATH");
            }
         }
      });
      ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
         MinecraftServer server = player.level().getServer();
         if (server != null) {
            runFullCleanup(server, player, player.getUUID(), "DIMENSION");
         }
      });
   }

   private static void runFullCleanup(MinecraftServer server, ServerPlayer player, UUID uuid, String reason) {
      CoopStateReset.safe("HighfiveDap", () -> HighfiveDapHandler.onPlayerLeave(uuid, server));
      CoopStateReset.safe("Grab", () -> GrabMechanic.fullCleanup(uuid));
      CoopStateReset.safe("HandSpin", () -> HandSpinHandler.cleanup(uuid, server));
      CoopStateReset.safe("Bros", () -> BrosHandler.cleanup(server, uuid, "DISCONNECT".equals(reason)));
      CoopStateReset.safe("HighFive", () -> HighFiveHandler.cleanup(uuid));
      CoopStateReset.safe("HighFiveStreak", () -> HighFiveStreakHandler.cleanup(uuid));
      CoopStateReset.safe("SpearStrike", () -> SpearStrikeHandler.cleanup(uuid));
      CoopStateReset.safe("Handshake", () -> HighFiveShakeHandler.cleanup(server, uuid));
      CoopStateReset.safe("ReadyFive", () -> ReadyFiveHandler.cleanup(uuid));
      CoopStateReset.safe("ReadyHug", () -> ReadyHugHandler.cleanup(uuid));
      CoopStateReset.safe("ReadyPush", () -> ReadyPushHandler.cleanup(uuid));
      CoopStateReset.safe("ChargedDap", () -> ChargedDapHandler.cleanup(uuid));
      CoopStateReset.safe("DapFlair", () -> DapFlair.cleanup(uuid));
      CoopStateReset.safe("DapPositioning", () -> DapPositioning.cleanup(uuid));
      CoopStateReset.safe("DapRun", () -> DapRunHandler.cleanup(uuid));
      CoopStateReset.safe("DapRunLegFx", () -> DapRunHandler.clearLegBoost(uuid));
      CoopStateReset.safe("NormalFacingDap", () -> NormalFacingDapHandler.cleanup(uuid));
      CoopStateReset.safe("PerfectDapCombo", () -> PerfectDapComboHandler.cancelCombo(uuid));
      CoopStateReset.safe("Pact", () -> PactHandler.cleanup(uuid));
      CoopStateReset.safe("QTEManager", () -> QTEManager.cancelQTE(uuid));
      CoopStateReset.safe("DapComboChain", () -> DapComboChain.cancelCombo(uuid));
      CoopStateReset.safe("MeteorStrike", () -> MeteorStrikeHandler.cleanup(uuid));
      CoopStateReset.safe("Huddle", () -> HuddleHandler.cleanup(uuid));
      if (!"DEATH".equals(reason)) {
         CoopStateReset.safe("RallyBeacon", () -> RallyBeaconHandler.cleanup(uuid));
      }

      CoopStateReset.safe("DuoPose", () -> DuoPoseHandler.onPlayerLeave(uuid));
      CoopStateReset.safe("Push immunity", () -> PushInteractionHandler.pushImmunity.remove(uuid));
      CoopStateReset.safe("MahitoTroll", () -> MahitoTrollHandler.cleanup(uuid));
      CoopStateReset.safe("FallDap", () -> FallDapHandler.cleanup(uuid));
      CoopStateReset.safe("ArmPoseTracker", () -> ArmPoseTracker.cleanup(uuid));
      CoopStateReset.safe("MarioJump", () -> MarioJumpHandler.cleanup(uuid));
      CoopStateReset.safe("DivineFlame", () -> DivineFlamCombo.cleanup(uuid));
      CoopStateReset.safe("Kick", () -> KickHandler.cleanup(uuid));
      CoopStateReset.safe("Bonk", () -> BonkHandler.cleanup(uuid));
      CoopStateReset.safe("Sit", () -> SitHandler.cleanup(uuid));
      CoopStateReset.safe("StrongSlap", () -> StrongSlapHandler.onPlayerLeave(uuid));
      CoopStateReset.safe("SikeFollowUp", () -> SikeFollowUpHandler.onPlayerLeave(uuid));
      CoopStateReset.safe("AirSpin", () -> SpinHandler.cleanup(uuid));
      CoopStateReset.safe("GroundPound", () -> GroundPoundHandler.cleanup(uuid));
      CoopStateReset.safe("BullyDap", () -> BullyDapHandler.cleanup(uuid));
      CoopStateReset.safe("BullyFail", () -> BullyFailHandler.cleanup(uuid));
      if (player != null) {
         CoopStateReset.safe("SpinYeet", () -> SpinYeetHandler.onPlayerDisconnect(player));
      }

      CoopStateReset.safe("SlowCleanup", () -> CoopSlowCleanup.clear(player));
      CoopStateReset.safe("hardResetPose", () -> CoopStateReset.hardResetPose(server, uuid));
   }
}
