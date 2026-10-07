package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class QTEManager {
   private static final int STAGE_1_DELAY_TICKS = 8;
   private static final int STAGE_1_WINDOW_TICKS = 12;
   private static final int STAGE_2_DELAY_TICKS = 6;
   private static final int STAGE_2_WINDOW_TICKS = 9;
   private static final int STAGE_3_DELAY_TICKS = 4;
   private static final int STAGE_3_WINDOW_TICKS = 6;
   private static final int TIMEOUT_GRACE_TICKS = 4;
   private static final String[] BUTTONS = new String[]{"G", "H"};
   private static final Random RANDOM = new Random();
   private static final Map<UUID, QTEManager.QTESession> activeSessions = new HashMap<>();

   public static QTEManager.QTESession triggerQTE(
      ServerPlayer p1, ServerPlayer p2, int maxStages, QTEManager.QTECallback onSuccess, QTEManager.QTECallback onFail, QTEManager.StageCallback onStage
   ) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      if (!activeSessions.containsKey(id1) && !activeSessions.containsKey(id2)) {
         QTEManager.QTESession session = new QTEManager.QTESession(id1, id2, Math.min(maxStages, 3), false);
         session.player1Ref = p1;
         session.player2Ref = p2;
         session.onAllStagesComplete = onSuccess;
         session.onFail = onFail;
         session.onStageComplete = onStage;
         activeSessions.put(id1, session);
         activeSessions.put(id2, session);
         sendQTEWindowToClients(session);
         return session;
      } else {
         return null;
      }
   }

   public static QTEManager.QTESession triggerQTESolo(
      ServerPlayer player, int maxStages, QTEManager.QTECallback onSuccess, QTEManager.QTECallback onFail, QTEManager.StageCallback onStage
   ) {
      UUID id = player.getUUID();
      UUID fakeId = UUID.randomUUID();
      if (activeSessions.containsKey(id)) {
         return null;
      }

      QTEManager.QTESession session = new QTEManager.QTESession(id, fakeId, Math.min(maxStages, 3), true);
      session.player1Ref = player;
      session.player2Ref = player;
      session.onAllStagesComplete = onSuccess;
      session.onFail = onFail;
      session.onStageComplete = onStage;
      activeSessions.put(id, session);
      sendQTEWindowToClient(player, session);
      return session;
   }

   public static void onButtonPress(ServerPlayer player, String button) {
      if (player != null && button != null) {
         UUID playerId = player.getUUID();
         QTEManager.QTESession session = activeSessions.get(playerId);
         if (session != null) {
            if (!button.equals(session.expectedButton)) {
               player.displayClientMessage(Component.literal("§c§lWRONG BUTTON!"), true);
            } else if (session.phase != QTEManager.QTESession.QTEPhase.ACTIVE) {
               if (session.phase == QTEManager.QTESession.QTEPhase.WAIT) {
                  player.displayClientMessage(Component.literal("§c§lTOO EARLY!"), true);
               } else {
                  player.displayClientMessage(Component.literal("§c§lTOO LATE!"), true);
               }
            } else {
               if (playerId.equals(session.player1Id)) {
                  session.player1Pressed = true;
               } else if (playerId.equals(session.player2Id)) {
                  session.player2Pressed = true;
               }

               if (!session.isSolo) {
                  checkBothPressed(session);
               }
            }
         }
      }
   }

   public static boolean isInQTE(UUID playerId) {
      return activeSessions.containsKey(playerId);
   }

   public static QTEManager.QTESession getSession(UUID playerId) {
      return activeSessions.get(playerId);
   }

   public static void cancelQTE(UUID playerId) {
      QTEManager.QTESession session = activeSessions.get(playerId);
      if (session != null) {
         cleanupSession(session);
      }
   }

   public static void tick(MinecraftServer server) {
      for (QTEManager.QTESession session : new ArrayList<>(activeSessions.values().stream().distinct().toList())) {
         tickSession(session, server);
      }
   }

   private static void tickSession(QTEManager.QTESession session, MinecraftServer server) {
      session.ticksInStage++;
      switch (session.phase) {
         case WAIT:
            if (session.ticksInStage >= session.delayTicks) {
               session.phase = QTEManager.QTESession.QTEPhase.ACTIVE;
               session.ticksInStage = 0;
               sendPrompt(session);
            }
            break;
         case ACTIVE:
            if (session.isSolo && session.player1Pressed && !session.player2Pressed && session.ticksInStage >= 4) {
               session.player2Pressed = true;
               checkBothPressed(session);
            }

            if (session.ticksInStage >= session.windowTicks) {
               session.phase = QTEManager.QTESession.QTEPhase.GRACE;
               session.ticksInStage = 0;
            }
            break;
         case GRACE:
            if (session.ticksInStage >= 4) {
               if (session.player1Ref != null) {
                  session.player1Ref.displayClientMessage(Component.literal("§c§l✖ MISSED!"), true);
                  ServerPlayNetworking.send(session.player1Ref, new QTEClearPayload(session.player1Id));
               }

               if (session.player2Ref != null && !session.isSolo) {
                  session.player2Ref.displayClientMessage(Component.literal("§c§l✖ MISSED!"), true);
                  ServerPlayNetworking.send(session.player2Ref, new QTEClearPayload(session.player2Id));
               }

               if (session.onFail != null) {
                  session.onFail.execute(session.player1Ref, session.player2Ref);
               }

               cleanupSession(session);
            }
            break;
         case STAGE_TRANSITION:
            if (session.ticksInStage >= 10) {
               session.currentStage++;
               session.player1Pressed = false;
               session.player2Pressed = false;
               session.ticksInStage = 0;
               session.phase = QTEManager.QTESession.QTEPhase.WAIT;
               session.applyStageTimings(session.currentStage);
               session.expectedButton = BUTTONS[RANDOM.nextInt(BUTTONS.length)];
               sendQTEWindowToClients(session);
            }
            break;
         case COMPLETE:
            cleanupSession(session);
      }
   }

   private static void checkBothPressed(QTEManager.QTESession session) {
      if (session.player1Pressed && session.player2Pressed) {
         if (session.onStageComplete != null) {
            session.onStageComplete.execute(session.player1Ref, session.player2Ref, session.currentStage);
         }

         if (session.currentStage < session.maxStages) {
            session.phase = QTEManager.QTESession.QTEPhase.STAGE_TRANSITION;
            session.ticksInStage = 0;
            if (session.player1Ref != null) {
               ServerPlayNetworking.send(session.player1Ref, new QTEClearPayload(session.player1Id));
               session.player1Ref.displayClientMessage(Component.literal("§a§l✓ STAGE " + session.currentStage + " CLEAR!"), true);
            }

            if (session.player2Ref != null && !session.isSolo) {
               ServerPlayNetworking.send(session.player2Ref, new QTEClearPayload(session.player2Id));
               session.player2Ref.displayClientMessage(Component.literal("§a§l✓ STAGE " + session.currentStage + " CLEAR!"), true);
            }
         } else {
            session.phase = QTEManager.QTESession.QTEPhase.COMPLETE;
            if (session.player1Ref != null) {
               ServerPlayNetworking.send(session.player1Ref, new QTEClearPayload(session.player1Id));
            }

            if (session.player2Ref != null && !session.isSolo) {
               ServerPlayNetworking.send(session.player2Ref, new QTEClearPayload(session.player2Id));
            }

            if (session.onAllStagesComplete != null) {
               session.onAllStagesComplete.execute(session.player1Ref, session.player2Ref);
            }

            cleanupSession(session);
         }
      }
   }

   private static void sendQTEWindowToClients(QTEManager.QTESession session) {
      long windowStartOffset = session.delayTicks * 50L;
      long windowDuration = session.windowTicks * 50L;
      if (session.player1Ref != null) {
         ServerPlayNetworking.send(
            session.player1Ref,
            new QTEWindowPayload(session.player1Id, session.expectedButton, session.currentStage, windowStartOffset, windowStartOffset + windowDuration)
         );
      }

      if (session.player2Ref != null && !session.isSolo) {
         ServerPlayNetworking.send(
            session.player2Ref,
            new QTEWindowPayload(session.player2Id, session.expectedButton, session.currentStage, windowStartOffset, windowStartOffset + windowDuration)
         );
      }
   }

   private static void sendQTEWindowToClient(ServerPlayer player, QTEManager.QTESession session) {
      long windowStartOffset = session.delayTicks * 50L;
      long windowDuration = session.windowTicks * 50L;
      ServerPlayNetworking.send(
         player, new QTEWindowPayload(player.getUUID(), session.expectedButton, session.currentStage, windowStartOffset, windowStartOffset + windowDuration)
      );
   }

   private static void sendPrompt(QTEManager.QTESession session) {
      String stageText = session.maxStages > 1 ? " §7(Stage " + session.currentStage + "/" + session.maxStages + ")" : "";
      if (session.player1Ref != null) {
         session.player1Ref.displayClientMessage(Component.literal("§e§lPRESS [" + session.expectedButton + "]!" + stageText), true);
      }

      if (session.player2Ref != null && !session.isSolo) {
         session.player2Ref.displayClientMessage(Component.literal("§e§lPRESS [" + session.expectedButton + "]!" + stageText), true);
      }
   }

   private static void cleanupSession(QTEManager.QTESession session) {
      session.phase = QTEManager.QTESession.QTEPhase.COMPLETE;
      activeSessions.remove(session.player1Id);
      activeSessions.remove(session.player2Id);
   }

   @FunctionalInterface
   public interface QTECallback {
      void execute(ServerPlayer var1, ServerPlayer var2);
   }

   public static class QTESession {
      public final UUID player1Id;
      public final UUID player2Id;
      public final boolean isSolo;
      public int currentStage;
      public final int maxStages;
      public int ticksInStage;
      public int delayTicks;
      public int windowTicks;
      public QTEManager.QTESession.QTEPhase phase;
      public String expectedButton;
      public boolean player1Pressed;
      public boolean player2Pressed;
      public QTEManager.QTECallback onAllStagesComplete;
      public QTEManager.QTECallback onFail;
      public QTEManager.StageCallback onStageComplete;
      public ServerPlayer player1Ref;
      public ServerPlayer player2Ref;

      QTESession(UUID p1, UUID p2, int maxStages, boolean solo) {
         this.player1Id = p1;
         this.player2Id = p2;
         this.maxStages = maxStages;
         this.isSolo = solo;
         this.currentStage = 1;
         this.ticksInStage = 0;
         this.phase = QTEManager.QTESession.QTEPhase.WAIT;
         this.player1Pressed = false;
         this.player2Pressed = false;
         this.applyStageTimings(1);
         this.expectedButton = QTEManager.BUTTONS[QTEManager.RANDOM.nextInt(QTEManager.BUTTONS.length)];
      }

      private void applyStageTimings(int stage) {
         switch (stage) {
            case 1:
               this.delayTicks = 8;
               this.windowTicks = 12;
               break;
            case 2:
               this.delayTicks = 6;
               this.windowTicks = 9;
               break;
            case 3:
               this.delayTicks = 4;
               this.windowTicks = 6;
               break;
            default:
               this.delayTicks = 4;
               this.windowTicks = 6;
         }
      }

      public float getPhaseProgress() {
         return switch (this.phase) {
            case WAIT -> (float)this.ticksInStage / this.delayTicks;
            case ACTIVE -> (float)this.ticksInStage / this.windowTicks;
            case GRACE -> this.ticksInStage / 4.0F;
            default -> 1.0F;
         };
      }

      public enum QTEPhase {
         WAIT,
         ACTIVE,
         GRACE,
         STAGE_TRANSITION,
         COMPLETE;
      }
   }

   @FunctionalInterface
   public interface StageCallback {
      void execute(ServerPlayer var1, ServerPlayer var2, int var3);
   }
}
