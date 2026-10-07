package com.cooptest;

import com.cooptest.bros.BrosHandler;
import com.cooptest.bully.BullyDapHandler;
import com.cooptest.bully.BullyFailHandler;
import com.cooptest.highfive.HighFiveNormalHandler;
import com.cooptest.highfive.HighFiveShakeHandler;
import com.cooptest.highfive.ReadyFiveHandler;
import com.cooptest.highfive.ReadyHugHandler;
import com.cooptest.highfive.ReadyPushHandler;
import com.cooptest.meme.SpinYeetConfig;
import com.cooptest.meme.SpinYeetHandler;
import com.cooptest.spin.HandSpinHandler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public class TestCoop implements ModInitializer {
   public void onInitialize() {
      try {
         CoopMovesConfig.load();
         CoopMovesConfig cfg = CoopMovesConfig.get();
         ModSounds.register();
         ModEffects.register();
         MahitoItems.register();
         PoseNetworking.registerPayloads();
         NormalFacingDapHandler.registerPayloads();
         SitHandler.registerPayloads();
         DuoPoseHandler.registerPayloads();
         GrabNetworking.registerPayloads();
         HandSpinHandler.registerPayloads();
         HighFiveHandler.registerPayloads();
         HighFivePassHandler.registerPayloads();
         HighFiveShakeHandler.registerPayloads();
         ReadyFiveHandler.registerPayloads();
         ReadyHugHandler.registerPayloads();
         ReadyPushHandler.registerPayloads();
         HuddleHandler.registerPayloads();
         ChargedDapHandler.registerPayloads();
         DapFusionHandler.registerPayloads();
         DapPositioning.registerPayloads();
         DapFlair.registerPayloads();
         DapRunHandler.registerPayloads();
         MeteorStrikeHandler.registerPayloads();
         BullyDapHandler.registerPayloads();
         PushInteractionHandler.registerPayloads();
         FallCatchHandler.registerPayloads();
         MarioJumpHandler.registerPayloads();
         HeavenDapPayloads.registerPayloads();
         ClapHandler.registerPayloads();
         BrosHandler.registerPayloads();
         SpearStrikeHandler.registerPayloads();
         PoseNetworking.registerServerReceiver();
         if (cfg.enableGrab) {
            GrabNetworking.registerServerReceivers();
            GrabMechanic.ShieldModePayload.register();
            GrabInteractionHandler.register();
            GrabMechanic.registerShieldDamageEvent();
            GrabActionLock.register();
            if (cfg.enableSpin) {
               SpinHandler.register();
            }

            if (cfg.enableGroundPound) {
               GroundPoundHandler.register();
            }

            HandSpinHandler.register();
         }

         if (cfg.enableHighFive) {
            HighFiveHandler.register();
            HighfiveDapHandler.register();
            HighFivePassHandler.register();
            HighFiveNormalHandler.register();
            HighFiveShakeHandler.register();
            ReadyFiveHandler.register();
         }

         if (cfg.enableHighFiveHug) {
            ReadyHugHandler.register();
         }

         if (cfg.enableHighFive) {
            HuddleHandler.register();
         }

         if (cfg.enableDap) {
            ChargedDapHandler.register();
            DapSessionManager.register();
            DapFusionHandler.register();
            DapPositioning.register();
            DapFlair.register();
            DapRunHandler.register();
            MeteorStrikeHandler.register();
            PerfectDapComboHandler.register();
            FacingDapHandler.register();
            NormalFacingDapHandler.register();
            SitHandler.register();
            DuoPoseHandler.register();
            BullyDapHandler.register();
            BullyFailHandler.register();
         }

         if (cfg.enableDapHold) {
            DapHoldHandler.register();
         }

         if (cfg.enablePush) {
            ReadyPushHandler.register();
         }

         if (cfg.enableCatch) {
            FallCatchHandler.register();
         }

         if (cfg.enableMarioJump) {
            MarioJumpHandler.register();
         }

         if (cfg.enableHeavenDap) {
         }

         if (cfg.enableFallDap) {
            FallDapHandler.register();
         }

         if (cfg.enableClap) {
            ClapHandler.register();
         }

         MahitoTrollHandler.register();
         AnimationTickHandler.register();
         LaunchedPlayerTracker.register();
         CarryingSlowdown.register();
         SpinYeetConfig.load();
         SpinYeetHandler.registerPayloads();
         SpinYeetHandler.register();
         PactHandler.register();
         HighFiveStreakHandler.register();
         CoopSlowCleanup.register();
         SpearStrikeHandler.register();
         BrosHandler.register();
         RallyBeaconHandler.register();
         FlairBarrierHandler.register();
         PlayerCleanupHandler.register();
         BlackHoodNetworking.registerPayloads();
         BlackHoodHandler.register();
         CuffHandler.register();
         if (cfg.enableKick) {
            KickHandler.register();
         }

         if (cfg.enableSlap) {
            SlapHandler.register();
            StrongSlapHandler.register();
            SikeFollowUpHandler.register();
         }

         CommandRegistrationCallback.EVENT
            .register(
               (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
                  dispatcher.register((LiteralArgumentBuilder)Commands.literal("sit").executes(SitHandler::executeSit));
                  StrongSlapCommand.register(dispatcher);
                  dispatcher.register(
                     (LiteralArgumentBuilder)Commands.literal("testpass")
                        .then(
                           Commands.argument("target", EntityArgument.player())
                              .executes(
                                 ctx -> {
                                    ServerPlayer p1 = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                    ServerPlayer p2 = EntityArgument.getPlayer(ctx, "target");
                                    HighFivePassHandler.executePassHighFive(
                                       p1,
                                       p2,
                                       HighFiveHandler.slotPoint(p2)
                                          .subtract(HighFiveHandler.horizontalForward(p1).scale(0.1))
                                          .add(HighFiveHandler.horizontalLeft(p1).scale(0.2)),
                                       HighFiveHandler.slotPoint(p1)
                                          .subtract(HighFiveHandler.horizontalForward(p2).scale(0.1))
                                          .add(HighFiveHandler.horizontalLeft(p2).scale(0.2))
                                    );
                                    return 1;
                                 }
                              )
                        )
                  );
                  dispatcher.register(
                     (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("testfive")
                              .executes(ctx -> triggerTestFive(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), 0)))
                           .then(Commands.literal("fast").executes(ctx -> triggerTestFive(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), 1))))
                        .then(Commands.literal("mixed").executes(ctx -> triggerTestFive(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), 2)))
                  );
               }
            );
         ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            if (cfg.enableGrab) {
               if (cfg.enableGroundPound) {
                  GroundPoundHandler.tick(server);
               }

               GrabMechanic.tick(server);
               if (cfg.enableSpin) {
                  SpinHandler.tick(server);
               }
            }

            if (cfg.enableDap) {
               ChargedDapHandler.checkTickSpeedRestore(server);
               QTEManager.tick(server);
               if (cfg.enableDapCombo) {
                  DapComboChain.tick(server);
               }
            }

            if (cfg.enablePush && server.getTickCount() % 20 == 0) {
               PushInteractionHandler.cleanupExpiredImmunity();
            }
         });
      } catch (Exception e) {
         System.err.println("[TestCoop] CRASH during initialization!");
         e.printStackTrace();
         throw new RuntimeException("TestCoop initialization failed", e);
      }
   }

   private static int triggerTestFive(ServerPlayer player, int mode) {
      Vec3 fwd = HighFiveHandler.horizontalForward(player);
      double spawnDist = 1.5;
      double sx = player.getX() + fwd.x * spawnDist;
      double sy = player.getY();
      double sz = player.getZ() + fwd.z * spawnDist;
      ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, player.level());
      stand.setPosRaw(sx, sy, sz);
      float yaw = (float)Math.toDegrees(Math.atan2(-(sx - player.getX()), sz - player.getZ()));
      stand.setYRot(yaw);
      stand.setYHeadRot(yaw);
      stand.setYBodyRot(yaw);
      stand.setInvulnerable(true);
      player.level().addFreshEntity(stand);
      long now = System.currentTimeMillis();
      switch (mode) {
         case 1:
            PoseNetworking.broadcastAnimState(player, 112);
            HighFiveNormalHandler.executeFastSolo(player, now);
            break;
         case 2:
            PoseNetworking.broadcastAnimState(player, 112);
            HighFiveNormalHandler.executeMixedSoloFast(player, now);
            break;
         default:
            PoseNetworking.broadcastAnimState(player, 20);
            HighFiveNormalHandler.executeNormalSolo(player, now);
      }

      new Thread(() -> {
         try {
            Thread.sleep(2200L);
         } catch (InterruptedException var3x) {
         }

         player.level().getServer().execute(stand::discard);
      }).start();
      return 1;
   }
}
