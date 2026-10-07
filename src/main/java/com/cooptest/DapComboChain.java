package com.cooptest;

import com.cooptest.client.CoopAnimationHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class DapComboChain {
   private static final int STAGE0_EVALUATE_TICK = 18;
   private static final int STAGE0_ANIM_END = 20;
   private static final int STAGE1_SOUND_1 = 8;
   private static final int STAGE1_SOUND_2 = 15;
   private static final int STAGE1_SOUND_3 = 24;
   private static final int STAGE1_EVALUATE_TICK = 24;
   private static final int STAGE1_ANIM_END = 57;
   private static final int STAGE2_SOUND_1 = 6;
   private static final int STAGE2_SOUND_2 = 23;
   private static final int STAGE2_SOUND_3 = 28;
   private static final int STAGE2_SOUND_4 = 39;
   private static final int STAGE2_EVALUATE_TICK = 42;
   private static final int STAGE2_ANIM_END = 70;
   private static final int STAGE3_MYBOY_SOUND_1 = 4;
   private static final int STAGE3_MYBOY_SOUND_2 = 8;
   private static final int STAGE3_ANIM_END = 51;
   private static final String[] BUTTONS = new String[]{"G", "H"};
   private static final Random RANDOM = new Random();
   private static final Map<UUID, DapComboChain.ComboSession> activeCombos = new HashMap<>();

   public static void startCombo(ServerPlayer p1, ServerPlayer p2, Vec3 impactPos) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      if (!activeCombos.containsKey(id1) && !activeCombos.containsKey(id2)) {
         DapComboChain.ComboSession session = new DapComboChain.ComboSession(p1, p2, impactPos);
         activeCombos.put(id1, session);
         activeCombos.put(id2, session);
         ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
         ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
      }
   }

   public static void startFromExtend1(ServerPlayer p1, ServerPlayer p2, Vec3 impactPos) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      if (!activeCombos.containsKey(id1) && !activeCombos.containsKey(id2)) {
         DapComboChain.ComboSession session = new DapComboChain.ComboSession(p1, p2, impactPos);
         session.stage = 1;
         session.ticksInStage = 0;
         session.resetQTE();
         activeCombos.put(id1, session);
         activeCombos.put(id2, session);
         ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
         ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
         PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P1.ordinal());
         PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P2.ordinal());
         openQTEWindow(session);
      }
   }

   public static boolean onButtonPress(ServerPlayer player, String button) {
      UUID id = player.getUUID();
      DapComboChain.ComboSession session = activeCombos.get(id);
      if (session == null) {
         return false;
      }

      if (!session.qteWindowOpen) {
         return true;
      }

      if (!button.equals(session.expectedButton)) {
         String who = player.getName().getString();
         Component msg = Component.literal("§c" + who + " pressed the wrong button!");
         session.p1Ref.sendOverlayMessage(msg);
         session.p2Ref.sendOverlayMessage(msg);
         closeQTEWindow(session);
         session.evaluated = true;
         session.stage = -(session.stage + 1);
         return true;
      }

      if (session.stage == 2) {
         int pressCount;
         if (id.equals(session.p1Id)) {
            session.p1RapidCount++;
            pressCount = session.p1RapidCount;
            if (session.p1RapidCount >= 3) {
               session.p1Pressed = true;
            }
         } else {
            if (!id.equals(session.p2Id)) {
               return true;
            }

            session.p2RapidCount++;
            pressCount = session.p2RapidCount;
            if (session.p2RapidCount >= 3) {
               session.p2Pressed = true;
            }
         }

         Vec3 armPos = getRightArmTip(player);
         session.world.playSound(null, armPos.x, armPos.y, armPos.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.6F, 1.2F);
         int remaining = 3 - pressCount;
         long windowRemaining = Math.max(100L, (42 - session.ticksInStage) * 50L);
         if (remaining > 0) {
            ServerPlayNetworking.send(player, new QTEWindowPayload(player.getUUID(), session.expectedButton, -remaining, 0L, windowRemaining));
         }
      } else if (id.equals(session.p1Id)) {
         session.p1Pressed = true;
      } else if (id.equals(session.p2Id)) {
         session.p2Pressed = true;
      }

      return true;
   }

   public static boolean isInCombo(UUID playerId) {
      return activeCombos.containsKey(playerId);
   }

   public static void cancelCombo(UUID playerId) {
      DapComboChain.ComboSession session = activeCombos.get(playerId);
      if (session != null) {
         cleanup(session, false);
      }
   }

   public static void tick(MinecraftServer server) {
      for (DapComboChain.ComboSession session : activeCombos.values().stream().distinct().toList()) {
         ServerPlayer p1 = server.getPlayerList().getPlayer(session.p1Id);
         ServerPlayer p2 = server.getPlayerList().getPlayer(session.p2Id);
         if (p1 != null && p2 != null && !p1.isDeadOrDying() && !p2.isDeadOrDying()) {
            session.p1Ref = p1;
            session.p2Ref = p2;
            tickSession(session);
         } else {
            cleanup(session, false);
         }
      }
   }

   private static void tickSession(DapComboChain.ComboSession s) {
      s.ticksInStage++;
      switch (s.stage) {
         case -3:
            if (s.ticksInStage >= 70) {
               cleanup(s, false);
            }
            break;
         case -2:
            if (s.ticksInStage >= 57) {
               cleanup(s, false);
            }
            break;
         case -1:
            if (s.ticksInStage >= 20) {
               cleanup(s, false);
            }
            break;
         case 0:
            tickStage0(s);
            break;
         case 1:
            tickStage1(s);
            break;
         case 2:
            tickStage2(s);
            break;
         case 3:
            tickStage3(s);
            break;
         default:
            cleanup(s, false);
      }
   }

   private static void tickStage0(DapComboChain.ComboSession s) {
      if (s.ticksInStage == 8 && !s.qteSent) {
         s.world.playSound(null, s.impactPos.x, s.impactPos.y, s.impactPos.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.8F);
         openQTEWindow(s);
      }

      if (s.ticksInStage >= 18 && !s.evaluated) {
         s.evaluated = true;
         closeQTEWindow(s);
         if (s.p1Pressed && s.p2Pressed) {
            startExtend1(s);
         } else {
            sendFailMessage(s);
            s.stage = -1;
         }
      }
   }

   private static void startExtend1(DapComboChain.ComboSession s) {
      s.advanceStage();
      PoseNetworking.broadcastAnimState(s.p1Ref, CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P1.ordinal());
      PoseNetworking.broadcastAnimState(s.p2Ref, CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P2.ordinal());
      ServerPlayNetworking.send(s.p1Ref, new ChargedDapHandler.PerfectDapFreezePayload(true));
      ServerPlayNetworking.send(s.p2Ref, new ChargedDapHandler.PerfectDapFreezePayload(true));
      openQTEWindow(s);
   }

   private static void tickStage1(DapComboChain.ComboSession s) {
      if (s.ticksInStage == 8 || s.ticksInStage == 15 || s.ticksInStage == 24) {
         playCritEffects(s);
      }

      if (s.ticksInStage >= 24 && !s.evaluated) {
         s.evaluated = true;
         closeQTEWindow(s);
         if (s.p1Pressed && s.p2Pressed) {
            startExtend2(s);
         } else {
            sendFailMessage(s);
            s.stage = -2;
         }
      }
   }

   private static void startExtend2(DapComboChain.ComboSession s) {
      s.advanceStage();
      PoseNetworking.broadcastAnimState(s.p1Ref, CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND_BOTH.ordinal());
      PoseNetworking.broadcastAnimState(s.p2Ref, CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND_BOTH.ordinal());
      openQTEWindow(s);
   }

   private static void tickStage2(DapComboChain.ComboSession s) {
      if (s.ticksInStage == 6 || s.ticksInStage == 23 || s.ticksInStage == 28) {
         playCritEffects(s);
      }

      if (s.ticksInStage == 39) {
         playCritEffects(s);
         s.world.playSound(null, s.impactPos.x, s.impactPos.y, s.impactPos.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 1.5F, 1.0F);
      }

      if (s.ticksInStage >= 42 && !s.evaluated) {
         s.evaluated = true;
         closeQTEWindow(s);
         if (s.p1Pressed && s.p2Pressed) {
            startFinal(s);
         } else {
            sendFailMessage(s);
            s.stage = -3;
         }
      }
   }

   private static void startFinal(DapComboChain.ComboSession s) {
      s.advanceStage();
      DapSessionManager.removeSessionForPlayer(s.p1Id);
      PoseNetworking.broadcastAnimState(s.p1Ref, CoopAnimationHandler.AnimState.PERFECT_DAP_MYBOY_P1.ordinal());
      PoseNetworking.broadcastAnimState(s.p2Ref, CoopAnimationHandler.AnimState.PERFECT_DAP_MYBOY_P1.ordinal());
      ServerPlayNetworking.send(s.p1Ref, new ChargedDapHandler.PerfectDapFreezePayload(false));
      ServerPlayNetworking.send(s.p2Ref, new ChargedDapHandler.PerfectDapFreezePayload(false));
      s.p1Ref.sendOverlayMessage(Component.literal("§d§l★ MY BOY! ★"));
      s.p2Ref.sendOverlayMessage(Component.literal("§d§l★ MY BOY! ★"));
      spawnFinishEffect(s);
   }

   private static void tickStage3(DapComboChain.ComboSession s) {
      if (s.ticksInStage == 4 || s.ticksInStage == 8) {
         playSmallArmEffect(s);
      }

      if (s.ticksInStage >= 51) {
         cleanup(s, true);
      }
   }

   private static void openQTEWindow(DapComboChain.ComboSession s) {
      if (!s.qteSent) {
         s.qteSent = true;
         s.qteWindowOpen = true;

         float pitch = switch (s.stage) {
            case 0 -> 1.8F;
            case 1 -> 1.5F;
            case 2 -> 1.3F;
            default -> 1.5F;
         };
         s.world
            .playSound(null, s.impactPos.x, s.impactPos.y, s.impactPos.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, pitch);

         int evaluateTick = switch (s.stage) {
            case 0 -> 18;
            case 1 -> 24;
            case 2 -> 42;
            default -> 20;
         };
         long windowDurationMs = evaluateTick * 50L;
         int displayStage = s.stage == 2 ? -3 : s.stage + 1;
         ServerPlayNetworking.send(s.p1Ref, new QTEWindowPayload(s.p1Id, s.expectedButton, displayStage, 0L, windowDurationMs));
         ServerPlayNetworking.send(s.p2Ref, new QTEWindowPayload(s.p2Id, s.expectedButton, displayStage, 0L, windowDurationMs));
      }
   }

   private static void closeQTEWindow(DapComboChain.ComboSession s) {
      s.qteWindowOpen = false;
      ServerPlayNetworking.send(s.p1Ref, new QTEClearPayload(s.p1Id));
      ServerPlayNetworking.send(s.p2Ref, new QTEClearPayload(s.p2Id));
   }

   private static void playCritEffects(DapComboChain.ComboSession s) {
      for (ServerPlayer player : List.of(s.p1Ref, s.p2Ref)) {
         Vec3 armPos = getRightArmTip(player);
         s.world.playSound(null, armPos.x, armPos.y, armPos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 1.0F);
         s.world.sendParticles(ParticleTypes.CRIT, armPos.x, armPos.y, armPos.z, 8, 0.12, 0.12, 0.12, 0.1);
         s.world.sendParticles(ParticleTypes.ENCHANTED_HIT, armPos.x, armPos.y, armPos.z, 5, 0.08, 0.08, 0.08, 0.05);
      }
   }

   private static void playSmallArmEffect(DapComboChain.ComboSession s) {
      for (ServerPlayer player : List.of(s.p1Ref, s.p2Ref)) {
         Vec3 armPos = getRightArmTip(player);
         s.world.playSound(null, armPos.x, armPos.y, armPos.z, ModSounds.DAP_WEAK, SoundSource.PLAYERS, 0.3F, 0.6F);
         s.world.sendParticles(ParticleTypes.CRIT, armPos.x, armPos.y, armPos.z, 4, 0.08, 0.08, 0.08, 0.05);
      }
   }

   private static void spawnFinishEffect(DapComboChain.ComboSession s) {
      Vec3 mid = s.p1Ref.position().add(s.p2Ref.position()).scale(0.5).add(0.0, 1.0, 0.0);
      s.world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, 40, 0.5, 0.5, 0.5, 0.2);
      s.world.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 20, 0.3, 0.8, 0.3, 0.1);
      s.world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), mid.x, mid.y, mid.z, 2, 0.0, 0.0, 0.0, 0.0);
      s.world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0F, 0.8F);

      for (ServerPlayer player : List.of(s.p1Ref, s.p2Ref)) {
         Vec3 armPos = getRightArmTip(player);
         s.world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, armPos.x, armPos.y, armPos.z, 15, 0.15, 0.15, 0.15, 0.15);
      }
   }

   private static Vec3 getRightArmTip(ServerPlayer player) {
      double yawRad = Math.toRadians(player.yBodyRot);
      double rightX = -Math.cos(yawRad);
      double rightZ = Math.sin(yawRad);
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      return new Vec3(player.getX() + rightX * 0.3 + forwardX * 0.4, player.getY() + 1.0, player.getZ() + rightZ * 0.3 + forwardZ * 0.4);
   }

   private static void sendFailMessage(DapComboChain.ComboSession s) {
      if (!s.p1Pressed && !s.p2Pressed) {
         Component msg = Component.literal("§cBoth players missed!");
         s.p1Ref.sendOverlayMessage(msg);
         s.p2Ref.sendOverlayMessage(msg);
      } else {
         String who = !s.p1Pressed ? s.p1Ref.getName().getString() : s.p2Ref.getName().getString();
         Component msg = Component.literal("§c" + who + " missed the extend!");
         s.p1Ref.sendOverlayMessage(msg);
         s.p2Ref.sendOverlayMessage(msg);
      }
   }

   private static void cleanup(DapComboChain.ComboSession s, boolean success) {
      if (s.qteWindowOpen) {
         closeQTEWindow(s);
      }

      if (s.p1Ref != null) {
         ServerPlayNetworking.send(s.p1Ref, new ChargedDapHandler.PerfectDapFreezePayload(false));
      }

      if (s.p2Ref != null) {
         ServerPlayNetworking.send(s.p2Ref, new ChargedDapHandler.PerfectDapFreezePayload(false));
      }

      if (s.p1Ref != null) {
         PoseNetworking.broadcastAnimState(s.p1Ref, CoopAnimationHandler.AnimState.NONE.ordinal());
      }

      if (s.p2Ref != null) {
         PoseNetworking.broadcastAnimState(s.p2Ref, CoopAnimationHandler.AnimState.NONE.ordinal());
      }

      if (success && s.p1Ref != null && s.p2Ref != null) {
         DuoPoseHandler.onComboEnded(s.p1Ref, s.p2Ref);
      }

      DapSessionManager.removeSessionForPlayer(s.p1Id);
      HighFiveHandler.cleanup(s.p1Id);
      HighFiveHandler.cleanup(s.p2Id);
      activeCombos.remove(s.p1Id);
      activeCombos.remove(s.p2Id);
   }

   public static class ComboSession {
      public final UUID p1Id;
      public final UUID p2Id;
      public ServerPlayer p1Ref;
      public ServerPlayer p2Ref;
      public ServerLevel world;
      public Vec3 impactPos;
      public int stage;
      public int ticksInStage;
      public boolean evaluated;
      public String expectedButton;
      public boolean p1Pressed;
      public boolean p2Pressed;
      public boolean qteWindowOpen;
      public boolean qteSent;
      public int p1RapidCount;
      public int p2RapidCount;
      public static final int RAPID_REQUIRED = 3;

      ComboSession(ServerPlayer p1, ServerPlayer p2, Vec3 pos) {
         this.p1Id = p1.getUUID();
         this.p2Id = p2.getUUID();
         this.p1Ref = p1;
         this.p2Ref = p2;
         this.world = p1.level();
         this.impactPos = pos;
         this.stage = 0;
         this.ticksInStage = 0;
         this.evaluated = false;
         this.expectedButton = DapComboChain.BUTTONS[DapComboChain.RANDOM.nextInt(DapComboChain.BUTTONS.length)];
         this.p1Pressed = false;
         this.p2Pressed = false;
         this.qteWindowOpen = false;
         this.qteSent = false;
      }

      void resetQTE() {
         this.p1Pressed = false;
         this.p2Pressed = false;
         this.qteWindowOpen = false;
         this.qteSent = false;
         this.evaluated = false;
         this.p1RapidCount = 0;
         this.p2RapidCount = 0;
         this.expectedButton = DapComboChain.BUTTONS[DapComboChain.RANDOM.nextInt(DapComboChain.BUTTONS.length)];
      }

      void advanceStage() {
         this.stage++;
         this.ticksInStage = 0;
         this.resetQTE();
      }
   }
}
