package com.cooptest.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.highfive.client.HighFiveShakeClientHandler;
import com.cooptest.highfive.client.ReadyHugClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public class CoopAnimationHandler {
   private static final String MOD_ID = "testcoop";
   public static final Identifier ANIMATION_LAYER_ID = Identifier.fromNamespaceAndPath("testcoop", "coop_animations");
   public static final Identifier GRAB_HOLDING_ANIM = Identifier.fromNamespaceAndPath("testcoop", "grab_holding");
   public static final Identifier GRAB_HOLDING_CHARGE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "grab_holding_charge");
   public static final Identifier GRAB_HOLDING_CHARGE_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "grab_holding_charge_idle");
   public static final Identifier GRAB_THROW_ANIM = Identifier.fromNamespaceAndPath("testcoop", "grab_throw");
   public static final Identifier GRAB_READY_ANIM = Identifier.fromNamespaceAndPath("testcoop", "grab_ready");
   public static final Identifier GRAB_READY_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "grab_ready_idle");
   public static final Identifier DAP_CHARGE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_charge");
   public static final Identifier DAP_CHARGE_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_charge_idle");
   public static final Identifier DAP_HIT_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_hit");
   public static final Identifier FIRE_DAP_CHARGE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fire_dap_charge");
   public static final Identifier FIRE_DAP_CHARGE_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fire_dap_charge_idle");
   public static final Identifier FIRE_DAP_HIT_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fire_dap_hit");
   public static final Identifier PUSH_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "push_start");
   public static final Identifier PUSH_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "push_idle");
   public static final Identifier PUSH_ANIM = Identifier.fromNamespaceAndPath("testcoop", "push");
   public static final Identifier CATCH_ANIM = Identifier.fromNamespaceAndPath("testcoop", "catching");
   public static final Identifier MAHITO_ANIM = Identifier.fromNamespaceAndPath("testcoop", "mahito");
   public static final Identifier HIGHFIVE_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_start");
   public static final Identifier HIGHFIVE_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_end");
   public static final Identifier HIGHFIVE_HIT_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_hit");
   public static final Identifier HIGHFIVE_HIT_COMBO_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_hitcombo");
   public static final Identifier HIGHFIVE_HIT_PASS_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_hit_pass");
   public static final Identifier HIGHFIVE_HIT_FAST_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_hit_fast");
   public static final Identifier SHAKE_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "shake_start");
   public static final Identifier SHAKE_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "shake_idle");
   public static final Identifier SHAKE_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "shake_end");
   public static final Identifier SHAKE_UP_DOWN_ANIM = Identifier.fromNamespaceAndPath("testcoop", "shake_up_down");
   public static final Identifier SHAKE_DOWN_UP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "shake_down_up");
   public static final Identifier SHAKE_FISTBUMP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "shake_fistbump");
   public static final Identifier SHAKE_ARMDAP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "shake_armdap");
   public static final Identifier HAND_SPIN_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "handspin_start");
   public static final Identifier HAND_SPIN_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "handspin_idle");
   public static final Identifier HAND_SPIN_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "handspin_end");
   public static final Identifier MONKE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "monke");
   public static final Identifier MONKE_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "monke_idle");
   public static final Identifier DAP_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_end");
   public static final Identifier DAP_RUN_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_run");
   public static final Identifier JUMP_HIGH_ANIM = Identifier.fromNamespaceAndPath("testcoop", "jumphigh");
   public static final Identifier BROS_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bros_start");
   public static final Identifier BROS_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bros_idle");
   public static final Identifier BROS_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bros_end");
   public static final Identifier BROS_START_MIRROR_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bros_start_mirror");
   public static final Identifier BROS_IDLE_MIRROR_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bros_idle_mirror");
   public static final Identifier BROS_END_MIRROR_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bros_end_mirror");
   public static final Identifier DAP_CHARGE_FALL_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_charge_fall_start");
   public static final Identifier DAP_CHARGE_FALLING_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_charge_falling");
   public static final Identifier DAP_CHARGE_FALL_HIT_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_charge_fall_hit");
   public static final Identifier SQUASHED_ANIM = Identifier.fromNamespaceAndPath("testcoop", "squashed");
   public static final Identifier PERFECT_DAP_HIT_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_hit");
   public static final Identifier DAP_DOWN_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_down");
   public static final Identifier DAP_HIT_WEAK_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_hit_weak");
   public static final Identifier PERFECT_DAP_EXTEND1_P1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_extandp1");
   public static final Identifier PERFECT_DAP_EXTEND1_P2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_extandp2");
   public static final Identifier PERFECT_DAP_MYBOY_P1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_extande_myboyp1");
   public static final Identifier PERFECT_DAP_MYBOY_P2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_extande_myboyp2");
   public static final Identifier PERFECT_DAP_EXTEND_BOTH_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_extand_both");
   public static final Identifier HEAVE_DAP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "heave_dap");
   public static final Identifier HOLD_SHIELD_ANIM = Identifier.fromNamespaceAndPath("testcoop", "hold_shield");
   public static final Identifier SHIELD_ANIM = Identifier.fromNamespaceAndPath("testcoop", "shield");
   public static final Identifier MARIO_JUMP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "jumpmario");
   public static final Identifier POP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "pop");
   public static final Identifier HUG_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "hug_start");
   public static final Identifier HUGGING_ANIM = Identifier.fromNamespaceAndPath("testcoop", "hugging");
   public static final Identifier HUGGING2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "hugging2");
   public static final Identifier HUG_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "hugend");
   public static final Identifier FIRE_DAP_HIT_PERFECT_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fire_dap_hit_perfect");
   public static final Identifier FIRE_DAP_COMBO_P1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fire_dap_hitp1");
   public static final Identifier FIRE_DAP_COMBO_P2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fire_dap_hitp2");
   public static final Identifier DAPHOLD_HIGHFIVE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_dap");
   public static final Identifier DAPHOLD_DAP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_high");
   public static final Identifier DAPHOLD_DAPPING_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dapping");
   public static final Identifier DAPHOLD_DAPPING_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dapping_end");
   public static final Identifier CLAP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "clap");
   public static final Identifier CLAP_SPAM_ANIM = Identifier.fromNamespaceAndPath("testcoop", "clapspam");
   public static final Identifier CLAP_STRONG_ANIM = Identifier.fromNamespaceAndPath("testcoop", "clap_strong");
   public static final Identifier FUSION_START_P1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fusion_startp1");
   public static final Identifier FUSION_START_P2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fusion_startp2");
   public static final Identifier FUSION_HIT_P1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fusion_hitp1");
   public static final Identifier FUSION_HIT_P2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fusion_hitp2");
   public static final Identifier FUSION_IDLE_P1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fusion_idlep1");
   public static final Identifier FUSION_IDLE_P2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "fusion_idlep2");
   public static final Identifier AURA_WALK_ANIM = Identifier.fromNamespaceAndPath("testcoop", "walk_aura");
   public static final Identifier BULLY_DAP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bully_dap");
   public static final Identifier BULLY_DAP_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bully_idle");
   public static final Identifier BULLY_DAP_HIT_P1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bully_hitp1");
   public static final Identifier BULLY_DAP_HIT_P2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bully_hitp2");
   public static final Identifier BULLY_FAIL_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bully_fail");
   public static final Identifier HIGHFIVE_HUG_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_hug");
   public static final Identifier HIGHFIVE_HUG2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_hug2");
   public static final Identifier KICK_ANIM = Identifier.fromNamespaceAndPath("testcoop", "kick");
   public static final Identifier DROP_KICK_ANIM = Identifier.fromNamespaceAndPath("testcoop", "drop_kick");
   public static final Identifier HIGHFIVE_SIKE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_sike");
   public static final Identifier SPIN_ANIM = Identifier.fromNamespaceAndPath("testcoop", "spin");
   public static final Identifier GROUND_POUND_DIVE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "ground_pound_dive");
   public static final Identifier GROUND_POUND_LAND_ANIM = Identifier.fromNamespaceAndPath("testcoop", "ground_pound_land");
   public static final Identifier SLAP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "slap");
   public static final Identifier END_GROUP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "end_group");
   public static final Identifier PERFECT_DAP_HIT_COMBO_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_hitcombo");
   public static final Identifier PERFECT_DAP_HIT_COMBO_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_hitcombo_end");
   public static final Identifier FACING_DAP_P1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_hitp1");
   public static final Identifier FACING_DAP_P2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "perfect_dap_hitp2");
   public static final Identifier HUDDLE_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "huddle_start");
   public static final Identifier HUDDLE_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "huddle_idle");
   public static final Identifier HUDDLE_QTE1_ANIM = Identifier.fromNamespaceAndPath("testcoop", "huddle_qte1");
   public static final Identifier HUDDLE_QTE2_ANIM = Identifier.fromNamespaceAndPath("testcoop", "huddle_qte2");
   public static final Identifier HUDDLE_QTE3_ANIM = Identifier.fromNamespaceAndPath("testcoop", "huddle_qte3");
   public static final Identifier LAY_DOWN_ANIM = Identifier.fromNamespaceAndPath("testcoop", "lay_down");
   public static final Identifier BONK_ANIM = Identifier.fromNamespaceAndPath("testcoop", "bonk");
   public static final Identifier DAP_HIT_FACE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_hit_face");
   public static final Identifier SLAP_FRONT_ANIM = Identifier.fromNamespaceAndPath("testcoop", "slap_front");
   public static final Identifier DAP_HIT_BAD_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_hit_bad");
   public static final Identifier DAP_LOOP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_loop");
   public static final Identifier DAP_LOOP_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_loop_end");
   public static final Identifier HEAVEN_DAP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "heaven_dap");
   public static final Identifier SITTING_ANIM = Identifier.fromNamespaceAndPath("testcoop", "sitting");
   public static final Identifier REACH_DOWN_ANIM = Identifier.fromNamespaceAndPath("testcoop", "reach_down");
   public static final Identifier REACH_PICKUP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "reach_pickup");
   public static final Identifier STAND_UP_ANIM = Identifier.fromNamespaceAndPath("testcoop", "stand_up");
   public static final Identifier HUDDLE_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "huddle_end");
   public static final Identifier STRONG_SLAP_START_ANIM = Identifier.fromNamespaceAndPath("testcoop", "slap_start");
   public static final Identifier STRONG_SLAP_CHARGE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "slap_charge");
   public static final Identifier STRONG_SLAP_HIT_ANIM = Identifier.fromNamespaceAndPath("testcoop", "slap_hit");
   public static final Identifier NOYA_ANIM = Identifier.fromNamespaceAndPath("testcoop", "noya");
   public static final Identifier DUO_POSE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "duo_pose");
   public static final Identifier DUO_POSE_IDLE_ANIM = Identifier.fromNamespaceAndPath("testcoop", "duo_pose_idle");
   public static final Identifier DUO_POSE_END_ANIM = Identifier.fromNamespaceAndPath("testcoop", "duo_pose_end");
   public static final Identifier CUFF_ANIM = Identifier.fromNamespaceAndPath("testcoop", "cuff");
   public static final Identifier SPIN_YEET_GRABBER_ANIM = Identifier.fromNamespaceAndPath("testcoop", "grabber");
   public static final Identifier SPIN_YEET_GRABBED_ANIM = Identifier.fromNamespaceAndPath("testcoop", "help");
   public static final Identifier HIGHFIVE_HOLD_ANIM = Identifier.fromNamespaceAndPath("testcoop", "highfive_hold");
   public static final Identifier DAP_HOLD_NEW_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_hold");
   public static final Identifier DAP_HOLD_IDLE_NEW_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_hold_idle");
   public static final Identifier DAP_HOLD_END_NEW_ANIM = Identifier.fromNamespaceAndPath("testcoop", "dap_hold_end");
   private static final Map<UUID, PoseState> currentPoses = new HashMap<>();
   private static final Map<UUID, CoopAnimationHandler.AnimState> animStates = new HashMap<>();
   private static final Map<UUID, Long> chargeStartTime = new HashMap<>();
   private static final int DAP_CHARGE_DURATION_TICKS = 5;
   private static final int GRAB_CHARGE_DURATION_TICKS = 32;
   private static final int GRAB_READY_DURATION_TICKS = 5;
   private static final int PUSH_START_DURATION_TICKS = 9;
   private static final int THROW_ANIM_DURATION_TICKS = 6;
   private static final int HIGHFIVE_START_DURATION_TICKS = 7;
   private static final int HIGHFIVE_HIT_DURATION_TICKS = 29;
   private static final int HIGHFIVE_END_DURATION_TICKS = 30;
   private static final int DAP_HIT_EFFECT_DELAY_TICKS = 5;
   private static final int FALL_CHARGE_DURATION_TICKS = 15;
   private static final int PERFECT_DAP_HIT_DURATION_TICKS = 33;
   private static final int DAP_DOWN_DURATION_TICKS = 7;
   private static final int FIRE_DAP_HIT_DURATION_TICKS = 46;
   private static final int DAP_HIT_DURATION_TICKS = 34;
   private static final int MARIO_JUMP_DURATION_TICKS = 10;
   private static final int CLAP_DURATION_TICKS = 8;
   private static final int CLAP_SPAM_DURATION_TICKS = 5;
   private static final int CLAP_STRONG_DURATION_TICKS = 3;
   private static final int FUSION_HIT_DURATION_TICKS = 12;
   private static final int HIGHFIVE_HUG_DURATION_TICKS = 88;
   private static final int HIGHFIVE_HUG2_DURATION_TICKS = 51;
   private static final int HIGHFIVE_HIT_PASS_DURATION_TICKS = 39;
   private static final int HIGHFIVE_HIT_FAST_DURATION_TICKS = 25;
   private static final int SHAKE_START_DURATION_TICKS = 7;
   private static final int DAP_END_DURATION_TICKS = 7;
   private static final int DAP_RUN_DURATION_TICKS = 17;
   private static final int JUMP_HIGH_DURATION_TICKS = 14;
   private static final int BROS_END_DURATION_TICKS = 10;
   private static final int SHAKE_END_DURATION_TICKS = 8;
   private static final int POP_DURATION_TICKS = 8;
   private static final int KICK_DURATION_TICKS = 20;
   private static final int DROP_KICK_DURATION_TICKS = 35;
   private static final int HIGHFIVE_SIKE_DURATION_TICKS = 29;
   private static final int GROUND_POUND_LAND_DURATION_TICKS = 10;
   private static final int SLAP_DURATION_TICKS = 19;
   private static final int END_GROUP_DURATION_TICKS = 78;
   private static final int PERFECT_DAP_HIT_COMBO_TICKS = 23;
   private static final int PERFECT_DAP_HIT_COMBO_END_TICKS = 10;
   private static final int FACING_DAP_P1_TICKS = 80;
   private static final int FACING_DAP_P2_TICKS = 82;
   private static final int HUDDLE_START_DURATION_TICKS = 11;
   private static final int HUDDLE_QTE1_DURATION_TICKS = 20;
   private static final int HUDDLE_QTE2_DURATION_TICKS = 20;
   private static final int HUDDLE_QTE3_DURATION_TICKS = 20;
   private static final int HUDDLE_END_DURATION_TICKS = 28;
   private static final int STRONG_SLAP_HIT_DURATION_TICKS = 99;
   private static final int DAP_HOLD_END_NEW_TICKS = 17;
   private static boolean initialized = false;
   private static final int WATCHDOG_TICKS = 100;
   private static final Map<UUID, long[]> watchdog = new HashMap<>();
   private static final long PROTECTED_CLIP_MS = 1500L;
   private static final Map<UUID, Long> protectedUntil = new HashMap<>();
   private static final long DAP_RUN_NONE_GUARD_MS = 650L;
   private static final Map<UUID, Long> dapRunStartedAt = new HashMap<>();
   public static boolean DEBUG_ANIM = false;

   public static void syncAnimState(UUID playerId, CoopAnimationHandler.AnimState state) {
      animStates.put(playerId, state);
      PoseNetworking.sendAnimState(playerId, state.ordinal());
   }

   public static void register() {
      if (isPALAvailable()) {
         try {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY
               .registerFactory(
                  ANIMATION_LAYER_ID,
                  1500,
                  player -> new PlayerAnimationController(
                     player, (controller, state, animSetter) -> controller.getFirstPersonMode() != FirstPersonMode.NONE ? PlayState.CONTINUE : PlayState.STOP
                  )
               );
            initialized = true;
            FirstPersonAnimationTest.init();
            ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> tick());
         } catch (Exception var1) {
         }
      }
   }

   private static boolean isPALAvailable() {
      try {
         Class.forName("com.zigythebird.playeranimcore.animation.layered.IAnimation");
         return true;
      } catch (ClassNotFoundException e) {
         return false;
      }
   }

   private static PlayerAnimationController getController(AbstractClientPlayer player) {
      return (PlayerAnimationController)PlayerAnimationAccess.getPlayerAnimationLayer(player, ANIMATION_LAYER_ID);
   }

   public static void updatePlayerAnimation(Player player, PoseState newPose) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();
            PoseState currentPose = currentPoses.get(playerId);
            if (currentPose != newPose) {
               CoopAnimationHandler.AnimState currentAnimState = animStates.get(playerId);
               currentPoses.put(playerId, newPose);
               Minecraft client = Minecraft.getInstance();
               boolean isLocalPlayer = client.player != null && client.player.getUUID().equals(playerId);

               try {
                  PlayerAnimationController controller = getController(clientPlayer);
                  if (controller == null) {
                     return;
                  }

                  switch (newPose) {
                     case GRABBED:
                        animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                        break;
                     case GRAB_READY:
                        controller.triggerAnimation(GRAB_READY_ANIM);
                        if (isLocalPlayer) {
                           FirstPersonAnimationTest.showBothHands();
                           syncAnimState(playerId, CoopAnimationHandler.AnimState.GRAB_READY);
                        } else {
                           animStates.put(playerId, CoopAnimationHandler.AnimState.GRAB_READY);
                        }

                        chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                        break;
                     case GRAB_HOLDING:
                        controller.triggerAnimation(GRAB_HOLDING_ANIM);
                        if (isLocalPlayer) {
                           FirstPersonAnimationTest.showBothHands();
                           syncAnimState(playerId, CoopAnimationHandler.AnimState.GRAB_HOLDING);
                        } else {
                           animStates.put(playerId, CoopAnimationHandler.AnimState.GRAB_HOLDING);
                        }
                        break;
                     case PUSH_IDLE:
                        controller.triggerAnimation(PUSH_START_ANIM);
                        if (isLocalPlayer) {
                           FirstPersonAnimationTest.showBothHands();
                           syncAnimState(playerId, CoopAnimationHandler.AnimState.PUSH_START);
                        } else {
                           animStates.put(playerId, CoopAnimationHandler.AnimState.PUSH_START);
                        }

                        chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                        break;
                     case NONE:
                        controller.stop();
                        if (isLocalPlayer) {
                           syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                           FirstPersonAnimationTest.stop();
                        } else {
                           animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                        }

                        chargeStartTime.remove(playerId);
                  }
               } catch (Exception var9) {
               }
            }
         }
      }
   }

   public static void startGrabCharge(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();
            CoopAnimationHandler.AnimState currentState = animStates.get(playerId);
            if (currentState == CoopAnimationHandler.AnimState.GRAB_HOLDING || currentState == CoopAnimationHandler.AnimState.GRAB_CHARGE_IDLE) {
               try {
                  PlayerAnimationController controller = getController(clientPlayer);
                  if (controller != null) {
                     controller.triggerAnimation(GRAB_HOLDING_CHARGE_ANIM);
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.GRAB_CHARGING);
                     chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                     Minecraft client = Minecraft.getInstance();
                     if (client.player != null && client.player.getUUID().equals(playerId)) {
                        FirstPersonAnimationTest.showBothHands();
                     }
                  }
               } catch (Exception var6) {
               }
            }
         }
      }
   }

   public static void playThrowAnimation(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(GRAB_THROW_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.GRAB_THROWING);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.playThrow();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void startDapCharge(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DAP_CHARGE_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_CHARGING);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.playDapCharge();
                  }

                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void stopDapCharge(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();
            CoopAnimationHandler.AnimState state = animStates.get(playerId);
            if (state == CoopAnimationHandler.AnimState.DAP_CHARGING
               || state == CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE
               || state == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGING
               || state == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGE_IDLE) {
               try {
                  PlayerAnimationController controller = getController(clientPlayer);
                  if (controller != null) {
                     controller.stop();
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                     chargeStartTime.remove(playerId);
                     Minecraft client = Minecraft.getInstance();
                     if (client.player != null && client.player.getUUID().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                     }
                  }
               } catch (Exception var6) {
               }
            }
         }
      }
   }

   public static void stopDapChargeLocalOnly(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();
            CoopAnimationHandler.AnimState state = animStates.get(playerId);
            if (state == CoopAnimationHandler.AnimState.DAP_CHARGING
               || state == CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE
               || state == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGING
               || state == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGE_IDLE) {
               try {
                  PlayerAnimationController controller = getController(clientPlayer);
                  if (controller != null) {
                     controller.stop();
                     animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                     chargeStartTime.remove(playerId);
                     Minecraft client = Minecraft.getInstance();
                     if (client.player != null && client.player.getUUID().equals(playerId)) {
                        FirstPersonAnimationTest.stop();
                     }
                  }
               } catch (Exception var6) {
               }
            }
         }
      }
   }

   public static void cancelDapCharge(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();
            CoopAnimationHandler.AnimState state = animStates.get(playerId);

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  if (state == CoopAnimationHandler.AnimState.DAP_CHARGING
                     || state == CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE
                     || state == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGING
                     || state == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGE_IDLE
                     || state == CoopAnimationHandler.AnimState.DAP_HIT
                     || state == CoopAnimationHandler.AnimState.FIRE_DAP_HIT
                     || state == null) {
                     controller.triggerAnimation(DAP_DOWN_ANIM);
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_DOWN);
                  }

                  chargeStartTime.remove(playerId);
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   private static boolean isOneShotClip(CoopAnimationHandler.AnimState s) {
      return s == CoopAnimationHandler.AnimState.DAP_HIT
         || s == CoopAnimationHandler.AnimState.DAP_HIT_WEAK
         || s == CoopAnimationHandler.AnimState.PERFECT_DAP_HIT
         || s == CoopAnimationHandler.AnimState.MID_DAP_HIT
         || s == CoopAnimationHandler.AnimState.DAP_HIT_FACE
         || s == CoopAnimationHandler.AnimState.DAP_END
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_FAST
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_PASS
         || s == CoopAnimationHandler.AnimState.JUMP_HIGH
         || s == CoopAnimationHandler.AnimState.BROS_START
         || s == CoopAnimationHandler.AnimState.BROS_END
         || s == CoopAnimationHandler.AnimState.BROS_START_MIRROR
         || s == CoopAnimationHandler.AnimState.BROS_END_MIRROR;
   }

   private static boolean isProtectedClip(CoopAnimationHandler.AnimState s) {
      return s == CoopAnimationHandler.AnimState.DAP_RUN
         || s == CoopAnimationHandler.AnimState.DAP_HIT
         || s == CoopAnimationHandler.AnimState.DAP_HIT_WEAK
         || s == CoopAnimationHandler.AnimState.PERFECT_DAP_HIT
         || s == CoopAnimationHandler.AnimState.MID_DAP_HIT
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_FAST
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_PASS;
   }

   private static boolean isChargeState(CoopAnimationHandler.AnimState s) {
      return s == CoopAnimationHandler.AnimState.DAP_CHARGING
         || s == CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE
         || s == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGING
         || s == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGE_IDLE;
   }

   private static boolean watchdogCheck(
      UUID playerId, CoopAnimationHandler.AnimState state, long now, boolean isLocalPlayer, PlayerAnimationController controller
   ) {
      if (!isOneShotClip(state)) {
         watchdog.remove(playerId);
         return false;
      }

      long[] w = watchdog.get(playerId);
      if (w == null || w[0] != state.ordinal()) {
         watchdog.put(playerId, new long[]{state.ordinal(), now});
         return false;
      }

      if (now - w[1] < 100L) {
         return false;
      }

      watchdog.remove(playerId);
      controller.stop();
      if (isLocalPlayer) {
         syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
      } else {
         animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
      }

      chargeStartTime.remove(playerId);
      return true;
   }

   public static void tick() {
      if (initialized) {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null) {
            long currentTime = client.level.getGameTime();
            UUID localPlayerId = client.player != null ? client.player.getUUID() : null;

            for (Player player : client.level.players()) {
               if (player instanceof AbstractClientPlayer clientPlayer) {
                  UUID playerId = player.getUUID();
                  CoopAnimationHandler.AnimState state = animStates.get(playerId);
                  if (state != null) {
                     boolean isLocalPlayer = playerId.equals(localPlayerId);

                     try {
                        PlayerAnimationController controller = getController(clientPlayer);
                        if (controller != null && !watchdogCheck(playerId, state, currentTime, isLocalPlayer, controller)) {
                           Long startTime = chargeStartTime.get(playerId);
                           switch (state) {
                              case GRAB_READY:
                                 if (startTime != null && currentTime - startTime >= 5L) {
                                    controller.triggerAnimation(GRAB_READY_IDLE_ANIM);
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.showBothHands();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.GRAB_READY_IDLE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.GRAB_READY_IDLE);
                                    }
                                 }
                              case GRAB_READY_IDLE:
                              case GRAB_HOLDING:
                              case GRAB_CHARGE_IDLE:
                              case DAP_CHARGE_IDLE:
                              case FIRE_DAP_CHARGING:
                              case FIRE_DAP_CHARGE_IDLE:
                              case PUSH_IDLE:
                              case PUSHING:
                              case CATCHING:
                              case MAHITO:
                              case HIGHFIVE_HIT_COMBO:
                              case DAP_CHARGE_FALL_START:
                              case DAP_CHARGE_FALLING:
                              case DAP_CHARGE_FALL_HIT:
                              case SQUASHED:
                              case HOLD_SHIELD:
                              case SHIELD:
                              case HUG_START:
                              case HUGGING:
                              case HUGGING2:
                              case HUG_END:
                              case FIRE_DAP_COMBO_P1:
                              case FIRE_DAP_COMBO_P2:
                              case DAPHOLD_HIGHFIVE:
                              case DAPHOLD_DAP:
                              case DAPHOLD_DAPPING:
                              case DAPHOLD_DAPPING_END:
                              case DAP_HIT_WEAK:
                              case PERFECT_DAP_EXTEND1_P1:
                              case PERFECT_DAP_EXTEND1_P2:
                              case PERFECT_DAP_MYBOY_P1:
                              case PERFECT_DAP_MYBOY_P2:
                              case PERFECT_DAP_EXTEND_BOTH:
                              case HEAVE_DAP:
                              case FUSION_START_P1:
                              case FUSION_START_P2:
                              case FUSION_IDLE_P1:
                              case FUSION_IDLE_P2:
                              case AURA_WALK:
                              case SPIN:
                              case GROUND_POUND_DIVE:
                              case PERFECT_DAP_HIT_COMBO:
                              case HUDDLE_IDLE:
                              case HUDDLE_QTE1:
                              case HUDDLE_QTE2:
                              case HUDDLE_QTE3:
                              case LAY_DOWN:
                              case BONK:
                              case DAP_HIT_FACE:
                              case SLAP_FRONT:
                              case DAP_HIT_BAD:
                              case DAP_LOOP:
                              case DAP_LOOP_END:
                              case REACH_DOWN:
                              case REACH_PICKUP:
                              case STAND_UP:
                              case HEAVEN_DAP:
                              case STRONG_SLAP_START:
                              case STRONG_SLAP_CHARGE:
                              case DUO_POSE:
                              case DUO_POSE_IDLE:
                              case CUFF:
                              case BULLY_DAP_P1:
                              case BULLY_DAP_P2:
                              case BULLY_DAP_IDLE:
                              case BULLY_DAP_HIT_P1:
                              case BULLY_DAP_HIT_P2:
                              case BULLY_FAIL:
                              case SPIN_YEET_GRABBER:
                              case SPIN_YEET_GRABBED:
                              case HIGHFIVE_HOLD:
                              case DAP_HOLD_NEW:
                              case DAP_HOLD_IDLE_NEW:
                              case SHAKE_IDLE:
                              case SHAKE_UP_DOWN:
                              case SHAKE_DOWN_UP:
                              case SHAKE_FISTBUMP:
                              case SHAKE_ARMDAP:
                              case HAND_SPIN_START:
                              case HAND_SPIN_IDLE:
                              case HAND_SPIN_END:
                              case MONKE:
                              case MONKE_IDLE:
                              case BROS_START:
                              case BROS_IDLE:
                              case BROS_START_MIRROR:
                              case BROS_IDLE_MIRROR:
                              default:
                                 break;
                              case GRAB_CHARGING:
                                 if (startTime != null && currentTime - startTime >= 32L) {
                                    controller.triggerAnimation(GRAB_HOLDING_CHARGE_IDLE_ANIM);
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.GRAB_CHARGE_IDLE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.GRAB_CHARGE_IDLE);
                                    }
                                 }
                                 break;
                              case GRAB_THROWING:
                                 if (startTime != null && currentTime - startTime >= 6L) {
                                    PoseState pose = currentPoses.get(playerId);
                                    if (pose == PoseState.GRAB_HOLDING) {
                                       controller.triggerAnimation(GRAB_HOLDING_ANIM);
                                       if (isLocalPlayer) {
                                          syncAnimState(playerId, CoopAnimationHandler.AnimState.GRAB_HOLDING);
                                       } else {
                                          animStates.put(playerId, CoopAnimationHandler.AnimState.GRAB_HOLDING);
                                       }
                                    } else {
                                       controller.stop();
                                       if (isLocalPlayer) {
                                          syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                       } else {
                                          animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                       }
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case DAP_CHARGING:
                                 if (startTime != null && currentTime - startTime >= 5L) {
                                    controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE);
                                    }
                                 }
                                 break;
                              case DAP_HIT:
                                 if (startTime != null && currentTime - startTime >= 34L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case FIRE_DAP_HIT:
                                 if (startTime != null && currentTime - startTime >= 46L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case PUSH_START:
                                 if (startTime != null && currentTime - startTime >= 9L) {
                                    controller.triggerAnimation(PUSH_IDLE_ANIM);
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.showBothHands();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.PUSH_IDLE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.PUSH_IDLE);
                                    }
                                 }
                                 break;
                              case HIGHFIVE_START:
                                 if (startTime != null && currentTime - startTime >= 7L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HIGHFIVE_END:
                                 if (startTime != null && currentTime - startTime >= 30L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HIGHFIVE_HIT:
                                 if (startTime != null && currentTime - startTime >= 29L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case PERFECT_DAP_HIT:
                                 if (startTime != null && currentTime - startTime >= 33L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case DAP_DOWN:
                                 if (startTime != null && currentTime - startTime >= 7L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case MARIO_JUMP:
                                 if (startTime != null && currentTime - startTime >= 10L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case POP:
                                 if (startTime != null && currentTime - startTime >= 8L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case CLAP:
                                 if (startTime != null && currentTime - startTime >= 8L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case CLAP_SPAM:
                                 if (startTime != null && currentTime - startTime >= 5L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case CLAP_STRONG:
                                 if (startTime != null && currentTime - startTime >= 3L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case FUSION_HIT_P1:
                              case FUSION_HIT_P2:
                                 if (startTime != null && currentTime - startTime >= 12L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HIGHFIVE_HUG:
                                 if (startTime != null && currentTime - startTime >= 88L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HIGHFIVE_HUG2:
                                 if (startTime != null && currentTime - startTime >= 51L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case KICK:
                                 if (startTime != null && currentTime - startTime >= 20L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case DROP_KICK:
                                 if (startTime != null && currentTime - startTime >= 35L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HIGHFIVE_SIKE:
                                 if (startTime != null && currentTime - startTime >= 29L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case GROUND_POUND_LAND:
                                 if (startTime != null && currentTime - startTime >= 10L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case SLAP:
                                 if (startTime != null && currentTime - startTime >= 19L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case END_GROUP:
                                 if (startTime != null && currentTime - startTime >= 78L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HUDDLE_START:
                                 if (startTime != null && currentTime - startTime >= 11L) {
                                    controller.stop();
                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HUDDLE_END:
                                 if (startTime != null && currentTime - startTime >= 28L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case PERFECT_DAP_HIT_COMBO_END:
                                 if (startTime != null && currentTime - startTime >= 10L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case FACING_DAP_P1:
                                 if (startTime != null && currentTime - startTime >= 80L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case FACING_DAP_P2:
                                 if (startTime != null && currentTime - startTime >= 82L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case SITTING:
                                 if (client.player != null && client.player.getUUID().equals(playerId)) {
                                    FirstPersonAnimationTest.showBothHands();
                                 }
                                 break;
                              case STRONG_SLAP_HIT:
                                 if (startTime != null && currentTime - startTime >= 99L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case NOYA:
                                 if (startTime != null && currentTime - startTime >= 89L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case DUO_POSE_END:
                                 if (startTime != null && currentTime - startTime >= 33L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HIGHFIVE_HIT_PASS:
                                 if (startTime != null && currentTime - startTime >= 39L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                    HighFivePassClientHandler.onAnimationEnd(playerId);
                                 }
                                 break;
                              case DAP_HOLD_END_NEW:
                                 if (startTime != null && currentTime - startTime >= 17L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.stop();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case HIGHFIVE_HIT_FAST:
                                 if (startTime != null && currentTime - startTime >= 25L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       FirstPersonAnimationTest.showBothHands();
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case SHAKE_START:
                                 if (startTime != null && currentTime - startTime >= 7L) {
                                    controller.stop();
                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case SHAKE_END:
                                 if (startTime != null && currentTime - startTime >= 8L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case DAP_END:
                                 if (startTime != null && currentTime - startTime >= 7L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case DAP_RUN:
                                 if (startTime != null && currentTime - startTime >= 17L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case JUMP_HIGH:
                                 if (startTime != null && currentTime - startTime >= 14L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case MID_DAP_HIT:
                                 if (startTime != null && currentTime - startTime >= 33L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                                 break;
                              case BROS_END:
                              case BROS_END_MIRROR:
                                 if (startTime != null && currentTime - startTime >= 10L) {
                                    controller.stop();
                                    if (isLocalPlayer) {
                                       syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
                                    } else {
                                       animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                                    }

                                    chargeStartTime.remove(playerId);
                                 }
                           }
                        }
                     } catch (Exception var13) {
                     }
                  }
               }
            }
         }
      }
   }

   public static void stopAnimation(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.stop();
               }
            } catch (Exception var3) {
            }

            UUID playerId = player.getUUID();
            currentPoses.remove(playerId);
            animStates.remove(playerId);
            chargeStartTime.remove(playerId);
         }
      }
   }

   public static void cleanup(UUID playerId) {
      protectedUntil.remove(playerId);
      dapRunStartedAt.remove(playerId);
      currentPoses.remove(playerId);
      animStates.remove(playerId);
      chargeStartTime.remove(playerId);
   }

   public static boolean isInChargeIdle(UUID playerId) {
      CoopAnimationHandler.AnimState state = animStates.get(playerId);
      return state == CoopAnimationHandler.AnimState.GRAB_CHARGE_IDLE
         || state == CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE
         || state == CoopAnimationHandler.AnimState.FIRE_DAP_CHARGE_IDLE;
   }

   public static void playDapHit(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();
            if (!DapHoldClientHandler.isAnimationLocked(playerId)) {
               try {
                  PlayerAnimationController controller = getController(clientPlayer);
                  if (controller != null) {
                     controller.triggerAnimation(DAP_HIT_ANIM);
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_HIT);
                     chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                     Minecraft client = Minecraft.getInstance();
                     if (client.player != null && client.player.getUUID().equals(playerId)) {
                        FirstPersonAnimationTest.playDapHit();
                     }
                  }
               } catch (Exception var5) {
               }
            }
         }
      }
   }

   public static void playFireDapHit(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(FIRE_DAP_HIT_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.FIRE_DAP_HIT);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void startFireDapCharge(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(FIRE_DAP_CHARGE_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.FIRE_DAP_CHARGING);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playFireDapChargeIdle(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(FIRE_DAP_CHARGE_IDLE_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.FIRE_DAP_CHARGE_IDLE);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playDapChargeIdle(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playPushAnimation(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(PUSH_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.PUSHING);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.playPush();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playCatchAnimation(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(CATCH_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.CATCHING);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playMahitoAnimation(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(MAHITO_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.MAHITO);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playHighFiveStart(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(HIGHFIVE_START_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.HIGHFIVE_START);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.playHighFiveStart();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playHighFiveEnd(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(HIGHFIVE_END_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.HIGHFIVE_END);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playHighFiveHit(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();
            if (!DapHoldClientHandler.isAnimationLocked(playerId)) {
               try {
                  PlayerAnimationController controller = getController(clientPlayer);
                  if (controller != null) {
                     controller.triggerAnimation(HIGHFIVE_HIT_ANIM);
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.HIGHFIVE_HIT);
                     chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                     Minecraft client = Minecraft.getInstance();
                     if (client.player != null && client.player.getUUID().equals(playerId)) {
                        FirstPersonAnimationTest.playHighFiveHit();
                     }
                  }
               } catch (Exception var5) {
               }
            }
         }
      }
   }

   public static void playFallDapChargeStart(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DAP_CHARGE_FALL_START_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_CHARGE_FALL_START);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playFallDapFalling(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DAP_CHARGE_FALLING_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_CHARGE_FALLING);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playFallDapHit(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DAP_CHARGE_FALL_HIT_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_CHARGE_FALL_HIT);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playSquashed(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(SQUASHED_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.SQUASHED);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playPerfectDapHit(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(PERFECT_DAP_HIT_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.PERFECT_DAP_HIT);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.playPerfectDap();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playDapDown(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DAP_DOWN_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DAP_DOWN);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static void playHoldShield(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(HOLD_SHIELD_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.HOLD_SHIELD);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playShield(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(SHIELD_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.SHIELD);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void stopShieldAnimation(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.stop();
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.NONE);
               }
            } catch (Exception var4) {
            }
         }
      }
   }

   public static boolean isInBlockingState(UUID playerId) {
      CoopAnimationHandler.AnimState state = animStates.get(playerId);
      return state == CoopAnimationHandler.AnimState.HIGHFIVE_END
         || state == CoopAnimationHandler.AnimState.SQUASHED
         || state == CoopAnimationHandler.AnimState.PERFECT_DAP_HIT
         || state == CoopAnimationHandler.AnimState.DAP_DOWN;
   }

   public static boolean isInHuddleAnim(UUID playerId) {
      CoopAnimationHandler.AnimState s = getAnimState(playerId);
      return s == CoopAnimationHandler.AnimState.HUDDLE_START
         || s == CoopAnimationHandler.AnimState.HUDDLE_IDLE
         || s == CoopAnimationHandler.AnimState.HUDDLE_QTE1
         || s == CoopAnimationHandler.AnimState.HUDDLE_QTE2
         || s == CoopAnimationHandler.AnimState.HUDDLE_QTE3
         || s == CoopAnimationHandler.AnimState.HUDDLE_END;
   }

   public static boolean isInDapOrHighFiveAnim(UUID playerId) {
      CoopAnimationHandler.AnimState s = getAnimState(playerId);
      return s == CoopAnimationHandler.AnimState.DAP_HIT
         || s == CoopAnimationHandler.AnimState.DAP_HIT_WEAK
         || s == CoopAnimationHandler.AnimState.PERFECT_DAP_HIT
         || s == CoopAnimationHandler.AnimState.MID_DAP_HIT
         || s == CoopAnimationHandler.AnimState.DAP_HIT_FACE
         || s == CoopAnimationHandler.AnimState.DAP_LOOP
         || s == CoopAnimationHandler.AnimState.DAP_HOLD_END_NEW
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_FAST
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_PASS;
   }

   public static boolean isInDapAnim(UUID playerId) {
      CoopAnimationHandler.AnimState s = getAnimState(playerId);
      return s == CoopAnimationHandler.AnimState.DAP_HIT
         || s == CoopAnimationHandler.AnimState.DAP_HIT_WEAK
         || s == CoopAnimationHandler.AnimState.PERFECT_DAP_HIT
         || s == CoopAnimationHandler.AnimState.MID_DAP_HIT
         || s == CoopAnimationHandler.AnimState.DAP_HIT_FACE
         || s == CoopAnimationHandler.AnimState.DAP_LOOP
         || s == CoopAnimationHandler.AnimState.DAP_HOLD_END_NEW;
   }

   public static boolean isLocalPlayerBusy() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         return false;
      }

      UUID id = client.player.getUUID();
      if (!isInDapOrHighFiveAnim(id) && !isInHuddleAnim(id) && !isLocalPlayerCuffed()) {
         CoopAnimationHandler.AnimState st = getAnimState(id);
         if (st == CoopAnimationHandler.AnimState.SPIN_YEET_GRABBER
            || st == CoopAnimationHandler.AnimState.SPIN_YEET_GRABBED
            || st == CoopAnimationHandler.AnimState.HAND_SPIN_START
            || st == CoopAnimationHandler.AnimState.HAND_SPIN_IDLE
            || st == CoopAnimationHandler.AnimState.HAND_SPIN_END
            || st == CoopAnimationHandler.AnimState.MONKE
            || st == CoopAnimationHandler.AnimState.MONKE_IDLE
            || st == CoopAnimationHandler.AnimState.BROS_START
            || st == CoopAnimationHandler.AnimState.BROS_IDLE
            || st == CoopAnimationHandler.AnimState.BROS_END
            || st == CoopAnimationHandler.AnimState.BROS_START_MIRROR
            || st == CoopAnimationHandler.AnimState.BROS_IDLE_MIRROR
            || st == CoopAnimationHandler.AnimState.BROS_END_MIRROR) {
            return true;
         } else if (ChargedDapClientHandler.isLocalPlayerCharging()
            || ChargedDapClientHandler.isInFaceDapSession()
            || ChargedDapClientHandler.isDapBadBlocking()) {
            return true;
         } else if (HighFiveClientHandler.isLocalPlayerInHighFive() || HighFiveClientHandler.isLocalPlayerFrozen()) {
            return true;
         } else if (HighFiveShakeClientHandler.isLocalPlayerInHandshake()) {
            return true;
         } else if (ReadyHugClientHandler.isLocalPlayerInHug()) {
            return true;
         } else if (HandSpinClientHandler.isLocalPlayerSpinning() || HandSpinClientHandler.isLocalPlayerMonkeFlying()) {
            return true;
         } else if (DapHoldClientHandler.isLocalPlayerFrozen()
            || StrongSlapClientHandler.isLocalPlayerFrozen()
            || DuoPoseClientHandler.isLocalPlayerFrozen()
            || DivineFlamComboClient.isLocalPlayerInCombo()) {
            return true;
         } else {
            return BrosClientHandler.blocksGrab() ? true : SpearStrikeClientHandler.isSteering();
         }
      } else {
         return true;
      }
   }

   public static boolean isInHighFiveStartAnim(UUID playerId) {
      CoopAnimationHandler.AnimState s = getAnimState(playerId);
      return s == CoopAnimationHandler.AnimState.HIGHFIVE_START
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HOLD
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_END;
   }

   public static int getAnimStateOrdinal(UUID playerId) {
      CoopAnimationHandler.AnimState s = animStates.get(playerId);
      return s == null ? 0 : s.ordinal();
   }

   public static boolean isInHugAnim(UUID playerId) {
      CoopAnimationHandler.AnimState s = getAnimState(playerId);
      return s == CoopAnimationHandler.AnimState.HUG_START
         || s == CoopAnimationHandler.AnimState.HUGGING
         || s == CoopAnimationHandler.AnimState.HUGGING2
         || s == CoopAnimationHandler.AnimState.HUG_END
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HUG
         || s == CoopAnimationHandler.AnimState.HIGHFIVE_HUG2;
   }

   public static CoopAnimationHandler.AnimState getAnimState(UUID playerId) {
      return animStates.getOrDefault(playerId, CoopAnimationHandler.AnimState.NONE);
   }

   public static boolean isAnimating(UUID playerId) {
      return getAnimState(playerId) != CoopAnimationHandler.AnimState.NONE;
   }

   public static boolean isLocalPlayerCuffed() {
      Minecraft client = Minecraft.getInstance();
      return client.player == null ? false : getAnimState(client.player.getUUID()) == CoopAnimationHandler.AnimState.CUFF;
   }

   public static void setAnimStateFromNetwork(Player player, int stateOrdinal) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            if (DEBUG_ANIM) {
               Minecraft mc0 = Minecraft.getInstance();
               boolean self = mc0.player != null && mc0.player.getUUID().equals(player.getUUID());
               boolean hasCtl = getController(clientPlayer) != null;
               String name = player.getName().getString();
               if (mc0.player != null) {
                  mc0.player
                     .displayClientMessage(
                        Component.literal("§8[anim] §7" + name + (self ? " §8(you)" : "") + " §f" + stateOrdinal + " §7ctl=" + (hasCtl ? "§aok" : "§cNULL")),
                        false
                     );
               }
            }

            Minecraft client = Minecraft.getInstance();
            if (stateOrdinal == 0) {
               UUID playerId = player.getUUID();
               CoopAnimationHandler.AnimState prevState = animStates.get(playerId);
               if (prevState == CoopAnimationHandler.AnimState.DAP_RUN) {
                  Long startedAt = dapRunStartedAt.get(playerId);
                  if (startedAt != null && System.currentTimeMillis() - startedAt < 650L) {
                     return;
                  }
               }

               dapRunStartedAt.remove(playerId);

               try {
                  PlayerAnimationController controller = getController(clientPlayer);
                  if (controller != null) {
                     controller.stop();
                  }
               } catch (Exception var10) {
               }

               animStates.remove(playerId);
               currentPoses.remove(playerId);
            } else {
               CoopAnimationHandler.AnimState state = CoopAnimationHandler.AnimState.values()[stateOrdinal];
               UUID playerId = player.getUUID();
               CoopAnimationHandler.AnimState current = animStates.get(playerId);
               if (isChargeState(state)) {
                  Long until = protectedUntil.get(playerId);
                  if (until != null && System.currentTimeMillis() < until) {
                     return;
                  }
               }

               if (isProtectedClip(state)) {
                  protectedUntil.put(playerId, System.currentTimeMillis() + 1500L);
               }

               if (state == CoopAnimationHandler.AnimState.DAP_RUN) {
                  dapRunStartedAt.put(playerId, System.currentTimeMillis());
               } else {
                  dapRunStartedAt.remove(playerId);
               }

               if (stateOrdinal >= 108 && stateOrdinal <= 111 || stateOrdinal == 126 || !HighfiveDapClientHandler.isAnimationLocked(playerId)) {
                  try {
                     PlayerAnimationController controller = getController(clientPlayer);
                     if (controller == null) {
                        return;
                     }

                     switch (state) {
                        case NONE:
                           controller.stop();
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.stop();
                           }
                           break;
                        case GRAB_READY:
                           controller.triggerAnimation(GRAB_READY_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case GRAB_READY_IDLE:
                           controller.triggerAnimation(GRAB_READY_IDLE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case GRAB_HOLDING:
                           controller.triggerAnimation(GRAB_HOLDING_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case GRAB_CHARGING:
                           controller.triggerAnimation(GRAB_HOLDING_CHARGE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case GRAB_CHARGE_IDLE:
                           controller.triggerAnimation(GRAB_HOLDING_CHARGE_IDLE_ANIM);
                           break;
                        case GRAB_THROWING:
                           controller.triggerAnimation(GRAB_THROW_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playThrow();
                           }
                           break;
                        case DAP_CHARGING:
                           controller.triggerAnimation(DAP_CHARGE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playDapCharge();
                           }
                           break;
                        case DAP_CHARGE_IDLE:
                           controller.triggerAnimation(DAP_CHARGE_IDLE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playDapCharge();
                           }
                           break;
                        case DAP_HIT:
                           controller.triggerAnimation(DAP_HIT_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playDapHit();
                           }
                           break;
                        case FIRE_DAP_CHARGING:
                           controller.triggerAnimation(FIRE_DAP_CHARGE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case FIRE_DAP_CHARGE_IDLE:
                           controller.triggerAnimation(FIRE_DAP_CHARGE_IDLE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case FIRE_DAP_HIT:
                           controller.triggerAnimation(FIRE_DAP_HIT_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PUSH_START:
                           controller.triggerAnimation(PUSH_START_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PUSH_IDLE:
                           controller.triggerAnimation(PUSH_IDLE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PUSHING:
                           controller.triggerAnimation(PUSH_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playPush();
                           }
                           break;
                        case CATCHING:
                           controller.triggerAnimation(CATCH_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case MAHITO:
                           controller.triggerAnimation(MAHITO_ANIM);
                           break;
                        case HIGHFIVE_START:
                           controller.triggerAnimation(HIGHFIVE_START_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playHighFiveStart();
                           }
                           break;
                        case HIGHFIVE_END:
                           controller.triggerAnimation(HIGHFIVE_END_ANIM);
                           break;
                        case HIGHFIVE_HIT:
                           controller.triggerAnimation(HIGHFIVE_HIT_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playHighFiveHit();
                           }
                           break;
                        case HIGHFIVE_HIT_COMBO:
                           controller.triggerAnimation(HIGHFIVE_HIT_COMBO_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playHighFiveCombo();
                           }
                           break;
                        case DAP_CHARGE_FALL_START:
                           controller.triggerAnimation(DAP_CHARGE_FALL_START_ANIM);
                           break;
                        case DAP_CHARGE_FALLING:
                           controller.triggerAnimation(DAP_CHARGE_FALLING_ANIM);
                           break;
                        case DAP_CHARGE_FALL_HIT:
                           controller.triggerAnimation(DAP_CHARGE_FALL_HIT_ANIM);
                           break;
                        case SQUASHED:
                           controller.triggerAnimation(SQUASHED_ANIM);
                           break;
                        case PERFECT_DAP_HIT:
                           controller.triggerAnimation(PERFECT_DAP_HIT_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playPerfectDap();
                           }
                           break;
                        case DAP_DOWN:
                           controller.triggerAnimation(DAP_DOWN_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.stop();
                           }
                           break;
                        case HOLD_SHIELD:
                           controller.triggerAnimation(HOLD_SHIELD_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SHIELD:
                           controller.triggerAnimation(SHIELD_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case MARIO_JUMP:
                           controller.triggerAnimation(MARIO_JUMP_ANIM);
                           break;
                        case POP:
                           controller.triggerAnimation(POP_ANIM);
                           break;
                        case HUG_START:
                           controller.triggerAnimation(HUG_START_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playHug();
                           }
                           break;
                        case HUGGING:
                           controller.triggerAnimation(HUGGING_ANIM);
                           break;
                        case HUGGING2:
                           controller.triggerAnimation(HUGGING2_ANIM);
                           break;
                        case HUG_END:
                           controller.triggerAnimation(HUG_END_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.stop();
                           }
                           break;
                        case FIRE_DAP_COMBO_P1:
                           controller.triggerAnimation(FIRE_DAP_COMBO_P1_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case FIRE_DAP_COMBO_P2:
                           controller.triggerAnimation(FIRE_DAP_COMBO_P2_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAPHOLD_HIGHFIVE:
                           controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playHighFiveStart();
                           }
                           break;
                        case DAPHOLD_DAP:
                           controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playDapHit();
                           }
                           break;
                        case DAPHOLD_DAPPING:
                           controller.triggerAnimation(DAPHOLD_DAPPING_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAPHOLD_DAPPING_END:
                           controller.triggerAnimation(DAPHOLD_DAPPING_END_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.stop();
                           }
                        case DAP_HIT_WEAK:
                        default:
                           break;
                        case PERFECT_DAP_EXTEND1_P1:
                           controller.triggerAnimation(PERFECT_DAP_EXTEND1_P1_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PERFECT_DAP_EXTEND1_P2:
                           controller.triggerAnimation(PERFECT_DAP_EXTEND1_P2_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PERFECT_DAP_MYBOY_P1:
                           controller.triggerAnimation(PERFECT_DAP_MYBOY_P1_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PERFECT_DAP_MYBOY_P2:
                           controller.triggerAnimation(PERFECT_DAP_MYBOY_P2_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PERFECT_DAP_EXTEND_BOTH:
                           controller.triggerAnimation(PERFECT_DAP_EXTEND_BOTH_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HEAVE_DAP:
                           controller.triggerAnimation(HEAVE_DAP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case CLAP:
                           controller.triggerAnimation(CLAP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case CLAP_SPAM:
                           controller.triggerAnimation(CLAP_SPAM_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case CLAP_STRONG:
                           controller.triggerAnimation(CLAP_STRONG_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case FUSION_START_P1:
                           controller.triggerAnimation(FUSION_START_P1_ANIM);
                           break;
                        case FUSION_START_P2:
                           controller.triggerAnimation(FUSION_START_P2_ANIM);
                           break;
                        case FUSION_HIT_P1:
                           controller.triggerAnimation(FUSION_HIT_P1_ANIM);
                           break;
                        case FUSION_HIT_P2:
                           controller.triggerAnimation(FUSION_HIT_P2_ANIM);
                           break;
                        case FUSION_IDLE_P1:
                           controller.triggerAnimation(FUSION_IDLE_P1_ANIM);
                           break;
                        case FUSION_IDLE_P2:
                           controller.triggerAnimation(FUSION_IDLE_P2_ANIM);
                           break;
                        case AURA_WALK:
                           controller.triggerAnimation(AURA_WALK_ANIM);
                           break;
                        case HIGHFIVE_HUG:
                           controller.triggerAnimation(HIGHFIVE_HUG_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playHug();
                           }
                           break;
                        case HIGHFIVE_HUG2:
                           controller.triggerAnimation(HIGHFIVE_HUG2_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playHug();
                           }
                           break;
                        case KICK:
                           controller.triggerAnimation(KICK_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playKick();
                           }
                           break;
                        case DROP_KICK:
                           controller.triggerAnimation(DROP_KICK_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playDropKick();
                           }
                           break;
                        case HIGHFIVE_SIKE:
                           controller.triggerAnimation(HIGHFIVE_SIKE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SPIN:
                           controller.triggerAnimation(SPIN_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case GROUND_POUND_DIVE:
                           controller.triggerAnimation(GROUND_POUND_DIVE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.stop();
                           }
                           break;
                        case GROUND_POUND_LAND:
                           controller.triggerAnimation(GROUND_POUND_LAND_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.stop();
                           }
                           break;
                        case SLAP:
                           controller.triggerAnimation(SLAP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playSlap();
                           }
                           break;
                        case END_GROUP:
                           controller.triggerAnimation(END_GROUP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PERFECT_DAP_HIT_COMBO:
                           controller.triggerAnimation(PERFECT_DAP_HIT_COMBO_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HUDDLE_START:
                           controller.triggerAnimation(HUDDLE_START_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HUDDLE_IDLE:
                           controller.triggerAnimation(HUDDLE_IDLE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HUDDLE_QTE1:
                           controller.triggerAnimation(HUDDLE_QTE1_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HUDDLE_END:
                           controller.triggerAnimation(HUDDLE_END_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case PERFECT_DAP_HIT_COMBO_END:
                           controller.triggerAnimation(PERFECT_DAP_HIT_COMBO_END_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case FACING_DAP_P1:
                           controller.triggerAnimation(FACING_DAP_P1_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case FACING_DAP_P2:
                           controller.triggerAnimation(FACING_DAP_P2_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HUDDLE_QTE2:
                           controller.triggerAnimation(HUDDLE_QTE2_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HUDDLE_QTE3:
                           controller.triggerAnimation(HUDDLE_QTE3_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case LAY_DOWN:
                           controller.triggerAnimation(LAY_DOWN_ANIM);
                           break;
                        case BONK:
                           controller.triggerAnimation(BONK_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAP_HIT_FACE:
                           controller.triggerAnimation(DAP_HIT_FACE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SLAP_FRONT:
                           controller.triggerAnimation(SLAP_FRONT_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAP_HIT_BAD:
                           controller.triggerAnimation(DAP_HIT_BAD_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              ChargedDapClientHandler.triggerDapBadBlock();
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAP_LOOP:
                           controller.triggerAnimation(DAP_LOOP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAP_LOOP_END:
                           controller.triggerAnimation(DAP_LOOP_END_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SITTING:
                           controller.triggerAnimation(SITTING_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case REACH_DOWN:
                           controller.triggerAnimation(REACH_DOWN_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case REACH_PICKUP:
                           controller.triggerAnimation(REACH_PICKUP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case STAND_UP:
                           controller.triggerAnimation(STAND_UP_ANIM);
                           break;
                        case HEAVEN_DAP:
                           controller.triggerAnimation(HEAVEN_DAP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case STRONG_SLAP_START:
                           controller.triggerAnimation(STRONG_SLAP_START_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case STRONG_SLAP_CHARGE:
                           controller.triggerAnimation(STRONG_SLAP_CHARGE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case STRONG_SLAP_HIT:
                           controller.triggerAnimation(STRONG_SLAP_HIT_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case NOYA:
                           controller.triggerAnimation(NOYA_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DUO_POSE:
                           controller.triggerAnimation(DUO_POSE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DUO_POSE_IDLE:
                           controller.triggerAnimation(DUO_POSE_IDLE_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DUO_POSE_END:
                           controller.triggerAnimation(DUO_POSE_END_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case CUFF:
                           controller.triggerAnimation(CUFF_ANIM);
                           break;
                        case BULLY_DAP_P1:
                           controller.triggerAnimation(BULLY_DAP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case BULLY_DAP_P2:
                           controller.triggerAnimation(BULLY_DAP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case BULLY_DAP_IDLE:
                           controller.triggerAnimation(BULLY_DAP_IDLE_ANIM);
                           break;
                        case BULLY_DAP_HIT_P1:
                           controller.triggerAnimation(BULLY_DAP_HIT_P1_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case BULLY_DAP_HIT_P2:
                           controller.triggerAnimation(BULLY_DAP_HIT_P2_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case BULLY_FAIL:
                           controller.triggerAnimation(BULLY_FAIL_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HIGHFIVE_HIT_PASS:
                           controller.triggerAnimation(HIGHFIVE_HIT_PASS_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SPIN_YEET_GRABBER:
                           controller.triggerAnimation(SPIN_YEET_GRABBER_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SPIN_YEET_GRABBED:
                           controller.triggerAnimation(SPIN_YEET_GRABBED_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HIGHFIVE_HOLD:
                           controller.triggerAnimation(HIGHFIVE_HOLD_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAP_HOLD_NEW:
                           controller.triggerAnimation(DAP_HOLD_NEW_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAP_HOLD_IDLE_NEW:
                           controller.triggerAnimation(DAP_HOLD_IDLE_NEW_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAP_HOLD_END_NEW:
                           controller.triggerAnimation(DAP_HOLD_END_NEW_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HIGHFIVE_HIT_FAST:
                           controller.triggerAnimation(HIGHFIVE_HIT_FAST_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SHAKE_START:
                           controller.triggerAnimation(SHAKE_START_ANIM);
                           break;
                        case SHAKE_IDLE:
                           controller.triggerAnimation(SHAKE_IDLE_ANIM);
                           break;
                        case SHAKE_END:
                           controller.triggerAnimation(SHAKE_END_ANIM);
                           break;
                        case SHAKE_UP_DOWN:
                           controller.triggerAnimation(SHAKE_UP_DOWN_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SHAKE_DOWN_UP:
                           controller.triggerAnimation(SHAKE_DOWN_UP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SHAKE_FISTBUMP:
                           controller.triggerAnimation(SHAKE_FISTBUMP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case SHAKE_ARMDAP:
                           controller.triggerAnimation(SHAKE_ARMDAP_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HAND_SPIN_START:
                           controller.triggerAnimation(HAND_SPIN_START_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case HAND_SPIN_IDLE:
                           controller.triggerAnimation(HAND_SPIN_IDLE_ANIM);
                           break;
                        case HAND_SPIN_END:
                           controller.triggerAnimation(HAND_SPIN_END_ANIM);
                           break;
                        case MONKE:
                           controller.triggerAnimation(MONKE_ANIM);
                           break;
                        case MONKE_IDLE:
                           controller.triggerAnimation(MONKE_IDLE_ANIM);
                           break;
                        case DAP_END:
                           controller.triggerAnimation(DAP_END_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case DAP_RUN:
                           controller.triggerAnimation(DAP_RUN_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case JUMP_HIGH:
                           controller.triggerAnimation(JUMP_HIGH_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case MID_DAP_HIT:
                           controller.triggerAnimation(PERFECT_DAP_HIT_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.playPerfectDap();
                           }
                           break;
                        case BROS_START:
                           controller.triggerAnimation(BROS_START_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case BROS_IDLE:
                           controller.triggerAnimation(BROS_IDLE_ANIM);
                           break;
                        case BROS_END:
                           controller.triggerAnimation(BROS_END_ANIM);
                           break;
                        case BROS_START_MIRROR:
                           controller.triggerAnimation(BROS_START_MIRROR_ANIM);
                           if (client.player != null && client.player.getUUID().equals(playerId)) {
                              FirstPersonAnimationTest.showBothHands();
                           }
                           break;
                        case BROS_IDLE_MIRROR:
                           controller.triggerAnimation(BROS_IDLE_MIRROR_ANIM);
                           break;
                        case BROS_END_MIRROR:
                           controller.triggerAnimation(BROS_END_MIRROR_ANIM);
                     }

                     if (client.level != null) {
                        long worldTime = client.level.getGameTime();
                        switch (state) {
                           case DAP_HIT:
                           case FIRE_DAP_HIT:
                           case HIGHFIVE_END:
                           case HIGHFIVE_HIT:
                           case PERFECT_DAP_HIT:
                           case DAP_DOWN:
                           case MARIO_JUMP:
                           case POP:
                           case CLAP:
                           case CLAP_SPAM:
                           case CLAP_STRONG:
                           case FUSION_HIT_P1:
                           case FUSION_HIT_P2:
                           case HIGHFIVE_HUG:
                           case HIGHFIVE_HUG2:
                           case KICK:
                           case DROP_KICK:
                           case HIGHFIVE_SIKE:
                           case GROUND_POUND_LAND:
                           case SLAP:
                           case END_GROUP:
                           case PERFECT_DAP_HIT_COMBO:
                           case HUDDLE_START:
                           case HUDDLE_END:
                           case PERFECT_DAP_HIT_COMBO_END:
                           case FACING_DAP_P1:
                           case FACING_DAP_P2:
                           case HUDDLE_QTE2:
                           case HUDDLE_QTE3:
                           case STRONG_SLAP_HIT:
                           case NOYA:
                           case DUO_POSE_END:
                           case HIGHFIVE_HIT_PASS:
                           case DAP_HOLD_END_NEW:
                           case HIGHFIVE_HIT_FAST:
                           case SHAKE_START:
                           case SHAKE_END:
                           case SHAKE_UP_DOWN:
                           case SHAKE_DOWN_UP:
                           case SHAKE_FISTBUMP:
                           case SHAKE_ARMDAP:
                           case HAND_SPIN_START:
                           case HAND_SPIN_END:
                           case MONKE:
                           case DAP_END:
                           case DAP_RUN:
                           case JUMP_HIGH:
                           case MID_DAP_HIT:
                           case BROS_END:
                           case BROS_END_MIRROR:
                              chargeStartTime.put(playerId, worldTime);
                           case FIRE_DAP_CHARGING:
                           case FIRE_DAP_CHARGE_IDLE:
                           case PUSH_START:
                           case PUSH_IDLE:
                           case PUSHING:
                           case CATCHING:
                           case MAHITO:
                           case HIGHFIVE_START:
                           case HIGHFIVE_HIT_COMBO:
                           case DAP_CHARGE_FALL_START:
                           case DAP_CHARGE_FALLING:
                           case DAP_CHARGE_FALL_HIT:
                           case SQUASHED:
                           case HOLD_SHIELD:
                           case SHIELD:
                           case HUG_START:
                           case HUGGING:
                           case HUGGING2:
                           case HUG_END:
                           case FIRE_DAP_COMBO_P1:
                           case FIRE_DAP_COMBO_P2:
                           case DAPHOLD_HIGHFIVE:
                           case DAPHOLD_DAP:
                           case DAPHOLD_DAPPING:
                           case DAPHOLD_DAPPING_END:
                           case DAP_HIT_WEAK:
                           case PERFECT_DAP_EXTEND1_P1:
                           case PERFECT_DAP_EXTEND1_P2:
                           case PERFECT_DAP_MYBOY_P1:
                           case PERFECT_DAP_MYBOY_P2:
                           case PERFECT_DAP_EXTEND_BOTH:
                           case HEAVE_DAP:
                           case FUSION_START_P1:
                           case FUSION_START_P2:
                           case FUSION_IDLE_P1:
                           case FUSION_IDLE_P2:
                           case AURA_WALK:
                           case SPIN:
                           case GROUND_POUND_DIVE:
                           case HUDDLE_IDLE:
                           case HUDDLE_QTE1:
                           case LAY_DOWN:
                           case BONK:
                           case DAP_HIT_FACE:
                           case SLAP_FRONT:
                           case DAP_HIT_BAD:
                           case DAP_LOOP:
                           case DAP_LOOP_END:
                           case SITTING:
                           case REACH_DOWN:
                           case REACH_PICKUP:
                           case STAND_UP:
                           case HEAVEN_DAP:
                           case STRONG_SLAP_START:
                           case STRONG_SLAP_CHARGE:
                           case DUO_POSE:
                           case DUO_POSE_IDLE:
                           case CUFF:
                           case BULLY_DAP_P1:
                           case BULLY_DAP_P2:
                           case BULLY_DAP_IDLE:
                           case BULLY_DAP_HIT_P1:
                           case BULLY_DAP_HIT_P2:
                           case BULLY_FAIL:
                           case SPIN_YEET_GRABBER:
                           case SPIN_YEET_GRABBED:
                           case HIGHFIVE_HOLD:
                           case DAP_HOLD_NEW:
                           case DAP_HOLD_IDLE_NEW:
                           case SHAKE_IDLE:
                           case HAND_SPIN_IDLE:
                           case MONKE_IDLE:
                           case BROS_START:
                           case BROS_IDLE:
                           case BROS_START_MIRROR:
                           case BROS_IDLE_MIRROR:
                        }
                     }

                     animStates.put(playerId, state);
                  } catch (Exception var11) {
                  }
               }
            }
         }
      }
   }

   public static void playFireDapHitPerfect(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(FIRE_DAP_HIT_PERFECT_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.FIRE_DAP_HIT);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playFireDapComboP1(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(FIRE_DAP_COMBO_P1_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.FIRE_DAP_HIT);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playFireDapComboP2(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(FIRE_DAP_COMBO_P2_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.FIRE_DAP_HIT);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playDapHoldStart(Player player, int role) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  animStates.put(playerId, CoopAnimationHandler.AnimState.NONE);
                  Minecraft client = Minecraft.getInstance();
                  boolean isLocalPlayer = client.player != null && client.player.getUUID().equals(playerId);
                  if (role == 0) {
                     controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.DAPHOLD_HIGHFIVE);
                     if (isLocalPlayer) {
                        FirstPersonAnimationTest.playHighFiveStart();
                     }
                  } else {
                     controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.DAPHOLD_DAP);
                     if (isLocalPlayer) {
                        FirstPersonAnimationTest.playDapHit();
                     }
                  }
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }

   public static void playDapHoldDapping(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DAPHOLD_DAPPING_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DAPHOLD_DAPPING);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playDapHoldResume(Player player, int role) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  if (role == 0) {
                     controller.triggerAnimation(DAPHOLD_HIGHFIVE_ANIM);
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.DAPHOLD_HIGHFIVE);
                  } else {
                     controller.triggerAnimation(DAPHOLD_DAP_ANIM);
                     syncAnimState(playerId, CoopAnimationHandler.AnimState.DAPHOLD_DAP);
                  }

                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.stop();
                  }
               }
            } catch (Exception var6) {
            }
         }
      }
   }

   public static void playDapHoldEnd(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DAPHOLD_DAPPING_END_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DAPHOLD_DAPPING_END);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playKick(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(KICK_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.KICK);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.playKick();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playDropKick(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(DROP_KICK_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.DROP_KICK);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.playDropKick();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playHighFiveSike(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(HIGHFIVE_SIKE_ANIM);
                  syncAnimState(playerId, CoopAnimationHandler.AnimState.HIGHFIVE_SIKE);
                  chargeStartTime.put(playerId, Minecraft.getInstance().level.getGameTime());
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playSlap(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(SLAP_ANIM);
                  animStates.put(playerId, CoopAnimationHandler.AnimState.SLAP);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.playSlap();
                  }

                  chargeStartTime.put(playerId, client.level != null ? client.level.getGameTime() : 0L);
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playEndGroup(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(END_GROUP_ANIM);
                  animStates.put(playerId, CoopAnimationHandler.AnimState.END_GROUP);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }

                  chargeStartTime.put(playerId, client.level != null ? client.level.getGameTime() : 0L);
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public static void playSpinAnimation(Player player) {
      if (initialized) {
         if (player instanceof AbstractClientPlayer clientPlayer) {
            UUID playerId = player.getUUID();

            try {
               PlayerAnimationController controller = getController(clientPlayer);
               if (controller != null) {
                  controller.triggerAnimation(SPIN_ANIM);
                  animStates.put(playerId, CoopAnimationHandler.AnimState.SPIN);
                  Minecraft client = Minecraft.getInstance();
                  if (client.player != null && client.player.getUUID().equals(playerId)) {
                     FirstPersonAnimationTest.showBothHands();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   public enum AnimState {
      NONE,
      GRAB_READY,
      GRAB_READY_IDLE,
      GRAB_HOLDING,
      GRAB_CHARGING,
      GRAB_CHARGE_IDLE,
      GRAB_THROWING,
      DAP_CHARGING,
      DAP_CHARGE_IDLE,
      DAP_HIT,
      FIRE_DAP_CHARGING,
      FIRE_DAP_CHARGE_IDLE,
      FIRE_DAP_HIT,
      PUSH_START,
      PUSH_IDLE,
      PUSHING,
      CATCHING,
      MAHITO,
      HIGHFIVE_START,
      HIGHFIVE_END,
      HIGHFIVE_HIT,
      HIGHFIVE_HIT_COMBO,
      DAP_CHARGE_FALL_START,
      DAP_CHARGE_FALLING,
      DAP_CHARGE_FALL_HIT,
      SQUASHED,
      PERFECT_DAP_HIT,
      DAP_DOWN,
      HOLD_SHIELD,
      SHIELD,
      MARIO_JUMP,
      POP,
      HUG_START,
      HUGGING,
      HUGGING2,
      HUG_END,
      FIRE_DAP_COMBO_P1,
      FIRE_DAP_COMBO_P2,
      DAPHOLD_HIGHFIVE,
      DAPHOLD_DAP,
      DAPHOLD_DAPPING,
      DAPHOLD_DAPPING_END,
      DAP_HIT_WEAK,
      PERFECT_DAP_EXTEND1_P1,
      PERFECT_DAP_EXTEND1_P2,
      PERFECT_DAP_MYBOY_P1,
      PERFECT_DAP_MYBOY_P2,
      PERFECT_DAP_EXTEND_BOTH,
      HEAVE_DAP,
      CLAP,
      CLAP_SPAM,
      CLAP_STRONG,
      FUSION_START_P1,
      FUSION_START_P2,
      FUSION_HIT_P1,
      FUSION_HIT_P2,
      FUSION_IDLE_P1,
      FUSION_IDLE_P2,
      AURA_WALK,
      HIGHFIVE_HUG,
      HIGHFIVE_HUG2,
      KICK,
      DROP_KICK,
      HIGHFIVE_SIKE,
      SPIN,
      GROUND_POUND_DIVE,
      GROUND_POUND_LAND,
      SLAP,
      END_GROUP,
      PERFECT_DAP_HIT_COMBO,
      HUDDLE_START,
      HUDDLE_IDLE,
      HUDDLE_QTE1,
      HUDDLE_END,
      PERFECT_DAP_HIT_COMBO_END,
      FACING_DAP_P1,
      FACING_DAP_P2,
      HUDDLE_QTE2,
      HUDDLE_QTE3,
      LAY_DOWN,
      BONK,
      DAP_HIT_FACE,
      SLAP_FRONT,
      DAP_HIT_BAD,
      DAP_LOOP,
      DAP_LOOP_END,
      SITTING,
      REACH_DOWN,
      REACH_PICKUP,
      STAND_UP,
      HEAVEN_DAP,
      STRONG_SLAP_START,
      STRONG_SLAP_CHARGE,
      STRONG_SLAP_HIT,
      NOYA,
      DUO_POSE,
      DUO_POSE_IDLE,
      DUO_POSE_END,
      CUFF,
      BULLY_DAP_P1,
      BULLY_DAP_P2,
      BULLY_DAP_IDLE,
      BULLY_DAP_HIT_P1,
      BULLY_DAP_HIT_P2,
      BULLY_FAIL,
      HIGHFIVE_HIT_PASS,
      SPIN_YEET_GRABBER,
      SPIN_YEET_GRABBED,
      HIGHFIVE_HOLD,
      DAP_HOLD_NEW,
      DAP_HOLD_IDLE_NEW,
      DAP_HOLD_END_NEW,
      HIGHFIVE_HIT_FAST,
      SHAKE_START,
      SHAKE_IDLE,
      SHAKE_END,
      SHAKE_UP_DOWN,
      SHAKE_DOWN_UP,
      SHAKE_FISTBUMP,
      SHAKE_ARMDAP,
      HAND_SPIN_START,
      HAND_SPIN_IDLE,
      HAND_SPIN_END,
      MONKE,
      MONKE_IDLE,
      DAP_END,
      DAP_RUN,
      JUMP_HIGH,
      MID_DAP_HIT,
      BROS_START,
      BROS_IDLE,
      BROS_END,
      BROS_START_MIRROR,
      BROS_IDLE_MIRROR,
      BROS_END_MIRROR;
   }
}
