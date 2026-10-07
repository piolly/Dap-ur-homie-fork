package com.cooptest;

import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.CoopKeyCategories;
import com.cooptest.client.FirstPersonAnimationTest;
import com.cooptest.client.GrabClientState;
import com.cooptest.client.GroundPoundClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.HighfiveDapClientHandler;
import com.cooptest.client.KickClientHandler;
import com.cooptest.client.SpearStrikeClientHandler;
import com.cooptest.client.SpinClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

public class GrabInputHandler {
   private static final long MAX_CHARGE_TIME_MS = 1500L;
   private static final boolean BLOCK_CHARGE_IN_SHIELD_MODE = true;
   private static KeyMapping grabKey;
   private static KeyMapping throwKey;
   private static KeyMapping shieldKey;
   private static boolean wasGrabKeyPressed = false;
   private static boolean wasThrowKeyPressed = false;
   private static boolean wasSneakPressed = false;
   private static boolean wasJumpPressed = false;
   private static boolean wasShieldKeyPressed = false;
   private static boolean wasYeetPressed = false;
   private static boolean wasShieldClickPressed = false;
   private static boolean wasOnGround = true;
   private static boolean spinWasActive = false;
   private static long spinStopTime = 0L;
   private static boolean isChargingThrow = false;
   private static boolean spearHoldSent = false;
   private static long throwChargeStartTime = 0L;
   private static float lastSentChargeProgress = -1.0F;
   public static final Map<UUID, Boolean> clientShieldMode = new HashMap<>();

   public static boolean isLocalPlayerInGrabOrReady() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         return false;
      }

      PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUUID(), PoseState.NONE);
      return pose == PoseState.GRAB_READY || pose == PoseState.GRAB_HOLDING || pose == PoseState.GRABBED;
   }

   public static boolean isLocalPlayerInGrab() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         return false;
      }

      PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUUID(), PoseState.NONE);
      return pose == PoseState.GRAB_HOLDING || pose == PoseState.GRABBED;
   }

   private static void suppressVanillaClicks(Minecraft client) {
      client.options.keyUse.setDown(false);

      while (client.options.keyUse.consumeClick()) {
      }

      client.options.keyAttack.setDown(false);

      while (client.options.keyAttack.consumeClick()) {
      }
   }

   private static boolean isRightMouseHeld(Minecraft client) {
      long window = client.getWindow().handle();
      return net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown();
   }

   private static boolean isLeftMouseHeld(Minecraft client) {
      long window = client.getWindow().handle();
      return net.minecraft.client.Minecraft.getInstance().options.keyAttack.isDown();
   }

   public static void register() {
      grabKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coopmoves.grab", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 82, CoopKeyCategories.COOPMOVES));
      throwKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coopmoves.throw", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 84, CoopKeyCategories.COOPMOVES));
      shieldKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coopmoves.shield", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 86, CoopKeyCategories.COOPMOVES));
      ClientPlayNetworking.registerGlobalReceiver(GrabMechanic.ShieldModePayload.ID, (payload, context) -> context.client().execute(() -> {
         clientShieldMode.put(payload.holderId(), payload.enabled());
         if (!payload.enabled()) {
            clientShieldMode.remove(payload.holderId());
         }
      }));
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (client.player != null) {
                  UUID playerId = client.player.getUUID();
                  PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
                  boolean isJumpPressed = client.options.keyJump.isDown();
                  if (isJumpPressed
                     && !wasJumpPressed
                     && pose == PoseState.GRABBED
                     && !client.player.isPassenger()
                     && client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                     ClientPlayNetworking.send(new GrabNetworking.ElytraBoostRequestPayload());
                  }

                  wasJumpPressed = isJumpPressed;
                  if (pose == PoseState.GRABBED && !client.player.isPassenger()) {
                     float forward = 0.0F;
                     float strafe = 0.0F;
                     if (client.options.keyUp.isDown()) {
                        forward++;
                     }

                     if (client.options.keyDown.isDown()) {
                        forward--;
                     }

                     if (client.options.keyLeft.isDown()) {
                        strafe++;
                     }

                     if (client.options.keyRight.isDown()) {
                        strafe--;
                     }

                     if (Math.abs(forward) > 0.01F || Math.abs(strafe) > 0.01F) {
                        ClientPlayNetworking.send(new GrabNetworking.AirMovementPayload(forward, strafe));
                     }
                  }

                  boolean isHolding = pose == PoseState.GRAB_HOLDING || GrabClientState.isHolding(playerId);
                  boolean isBeingHeld = pose == PoseState.GRABBED && client.player.isPassenger() || GrabClientState.isBeingHeld(playerId);
                  boolean thrownFlying = pose == PoseState.GRABBED && !client.player.isPassenger() && !client.player.onGround();
                  if (isHolding || isBeingHeld && !thrownFlying) {
                     suppressVanillaClicks(client);
                  }

                  boolean spearHold = !client.player.onGround()
                     && !client.player.isPassenger()
                     && client.gui.screen() == null
                     && SpearStrikeHandler.isSpear(client.player.getMainHandItem())
                     && net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown();
                  if (spearHold != spearHoldSent) {
                     ClientPlayNetworking.send(new SpearStrikeHandler.SpearHoldPayload(spearHold));
                     spearHoldSent = spearHold;
                  }

                  boolean isGrabKeyPressed = grabKey.isDown();
                  boolean rBlocked = CoopAnimationHandler.isInDapOrHighFiveAnim(playerId)
                     || CoopAnimationHandler.isInHuddleAnim(playerId)
                     || ChargedDapClientHandler.isInFaceDapSession()
                     || BrosClientHandler.blocksGrab()
                     || SpearStrikeClientHandler.isSteering();
                  if (isGrabKeyPressed && !wasGrabKeyPressed && HighfiveDapClientHandler.isActive()) {
                     HighfiveDapClientHandler.requestCancel();
                     wasGrabKeyPressed = isGrabKeyPressed;
                     rBlocked = true;
                  }

                  if (isGrabKeyPressed && !wasGrabKeyPressed && !rBlocked && !CoopAnimationHandler.isLocalPlayerCuffed()) {
                     if (isHolding) {
                        ClientPlayNetworking.send(new GrabNetworking.DropRequestPayload());
                     } else if (pose == PoseState.GRAB_READY) {
                        if (!HandSpinClientHandler.isLocalPlayerSpinning()) {
                           PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                           PoseNetworking.sendPoseToServer(playerId, PoseState.NONE);
                        }
                     } else if (pose == PoseState.NONE && handsEmpty(client)) {
                        PoseNetworking.poseStates.put(playerId, PoseState.GRAB_READY);
                        PoseNetworking.sendPoseToServer(playerId, PoseState.GRAB_READY);
                     }
                  }

                  wasGrabKeyPressed = isGrabKeyPressed;
                  boolean isShieldKeyPressed = shieldKey.isDown();
                  if (isShieldKeyPressed && !wasShieldKeyPressed) {
                     boolean blocked = isHolding
                        || isBeingHeld
                        || CoopAnimationHandler.isLocalPlayerBusy()
                        || pose == PoseState.GRAB_READY
                        || pose == PoseState.PUSH_IDLE
                        || ChargedDapClientHandler.isLocalPlayerCharging()
                        || HighFiveClientHandler.isLocalPlayerInHighFive()
                        || CoopAnimationHandler.isLocalPlayerCuffed();
                     if (!blocked && CoopMovesConfig.get().enableClap && client.player.getMainHandItem().isEmpty()) {
                        FirstPersonAnimationTest.showBothHands();
                        ClientPlayNetworking.send(new ClapHandler.ClapRequestPayload());
                     }
                  }

                  wasShieldKeyPressed = isShieldKeyPressed;
                  boolean isSneakPressed = client.options.keyShift.isDown();
                  boolean sneakJustPressed = isSneakPressed && !wasSneakPressed;
                  boolean sneakJustReleased = !isSneakPressed && wasSneakPressed;
                  boolean isThrownAirborne = pose == PoseState.GRABBED && !client.player.isPassenger() && !client.player.onGround();
                  boolean isHelicopterMode = isThrownAirborne && SpinClientHandler.isLocalPlayerSpinning() && client.player.getFirstPassenger() != null;
                  if (isHelicopterMode && !GroundPoundClientHandler.isLocalPlayerDiving()) {
                     if (sneakJustReleased) {
                        spinWasActive = false;
                        ClientPlayNetworking.send(new SpinHandler.SpinStopPayload());
                        SpinClientHandler.forceStopLocalSpin();
                        ClientPlayNetworking.send(new GroundPoundHandler.GroundPoundStartPayload());
                     }
                  } else if (isThrownAirborne && !GroundPoundClientHandler.isLocalPlayerDiving()) {
                     if (sneakJustReleased && SpinClientHandler.isLocalPlayerSpinning()) {
                        spinWasActive = true;
                        spinStopTime = System.currentTimeMillis();
                        ClientPlayNetworking.send(new SpinHandler.SpinStopPayload());
                        SpinClientHandler.forceStopLocalSpin();
                     }

                     if (sneakJustPressed) {
                        if (SpinClientHandler.isLocalPlayerSpinning()) {
                           spinWasActive = false;
                           ClientPlayNetworking.send(new SpinHandler.SpinStopPayload());
                           SpinClientHandler.forceStopLocalSpin();
                           ClientPlayNetworking.send(new GroundPoundHandler.GroundPoundStartPayload());
                        } else if (spinWasActive && System.currentTimeMillis() - spinStopTime < 300L) {
                           spinWasActive = false;
                           ClientPlayNetworking.send(new GroundPoundHandler.GroundPoundStartPayload());
                        } else {
                           spinWasActive = false;
                           ClientPlayNetworking.send(new SpinHandler.SpinStartPayload());
                        }
                     }
                  } else if (!isThrownAirborne || !GroundPoundClientHandler.isLocalPlayerDiving()) {
                     if (sneakJustPressed && isBeingHeld) {
                        ClientPlayNetworking.send(new GrabNetworking.EscapeRequestPayload());
                     }

                     if (!isThrownAirborne) {
                        spinWasActive = false;
                     }
                  }

                  wasSneakPressed = isSneakPressed;
                  boolean isShieldClickPressed = isHolding && isLeftMouseHeld(client);
                  if (isShieldClickPressed && !wasShieldClickPressed) {
                     ClientPlayNetworking.send(new GrabNetworking.ShieldTogglePayload());
                  }

                  wasShieldClickPressed = isShieldClickPressed;
                  boolean isYeetPressed = isHolding && isRightMouseHeld(client);
                  if (clientShieldMode.getOrDefault(playerId, false)) {
                     isYeetPressed = false;
                  }

                  if (isHolding) {
                     KickClientHandler.cancelIfCharging();
                     if (isYeetPressed && !wasYeetPressed) {
                        isChargingThrow = true;
                        throwChargeStartTime = System.currentTimeMillis();
                        lastSentChargeProgress = 0.0F;
                        CoopAnimationHandler.startGrabCharge(client.player);
                     } else if (isYeetPressed && isChargingThrow) {
                        float currentProgress = getThrowChargeProgress();
                        GrabClientState.setChargeProgress(playerId, currentProgress);
                        if (Math.abs(currentProgress - lastSentChargeProgress) >= 0.1F) {
                           PoseNetworking.sendChargeProgress(playerId, currentProgress);
                           lastSentChargeProgress = currentProgress;
                        }
                     } else if (!isYeetPressed && wasYeetPressed && isChargingThrow) {
                        long chargeTime = System.currentTimeMillis() - throwChargeStartTime;
                        float power = Math.min(1.0F, (float)chargeTime / 1500.0F);
                        ClientPlayNetworking.send(new GrabNetworking.ThrowRequestPayload(power));
                        isChargingThrow = false;
                        CoopAnimationHandler.playThrowAnimation(client.player);
                        ArmPoseTracker.throwAnimationStart.put(playerId, System.currentTimeMillis());
                        PoseNetworking.sendThrowAnimation(playerId);
                        GrabClientState.setChargeProgress(playerId, 0.0F);
                        PoseNetworking.sendChargeProgress(playerId, -1.0F);
                        lastSentChargeProgress = -1.0F;
                     }
                  } else if (isChargingThrow) {
                     isChargingThrow = false;
                     GrabClientState.setChargeProgress(playerId, 0.0F);
                     PoseNetworking.sendChargeProgress(playerId, -1.0F);
                     lastSentChargeProgress = -1.0F;
                  }

                  wasYeetPressed = isYeetPressed;
                  boolean isThrowKeyPressed = throwKey.isDown();
                  if (!isHolding) {
                     boolean canKick = !client.player.isPassenger() && pose == PoseState.NONE && !CoopAnimationHandler.isLocalPlayerBusy();
                     if (canKick) {
                        KickClientHandler.handleKickTick(client, isThrowKeyPressed, client.player.isSprinting());
                     } else {
                        KickClientHandler.cancelIfCharging();
                     }
                  }

                  wasThrowKeyPressed = isThrowKeyPressed;
                  if (pose == PoseState.GRAB_READY && !handsEmpty(client) && !HandSpinClientHandler.isLocalPlayerSpinning()) {
                     PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                     PoseNetworking.sendPoseToServer(playerId, PoseState.NONE);
                  }
               }
            }
         );
   }

   private static boolean handsEmpty(Minecraft client) {
      return client.player.getMainHandItem().isEmpty();
   }

   public static float getThrowChargeProgress() {
      if (!isChargingThrow) {
         return -1.0F;
      }

      long chargeTime = System.currentTimeMillis() - throwChargeStartTime;
      return Math.min(1.0F, (float)chargeTime / 1500.0F);
   }

   public static float getChargeProgressFor(UUID playerId) {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && client.player.getUUID().equals(playerId)
         ? getThrowChargeProgress()
         : PoseNetworking.chargeProgress.getOrDefault(playerId, -1.0F);
   }
}
