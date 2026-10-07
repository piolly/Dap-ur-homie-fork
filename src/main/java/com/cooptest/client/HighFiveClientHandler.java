package com.cooptest.client;

import com.cooptest.GrabInputHandler;
import com.cooptest.HighFiveHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.highfive.HighFiveShakeHandler;
import com.cooptest.highfive.client.HighFiveShakeClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public class HighFiveClientHandler {
   private static KeyMapping highFiveKey;
   private static boolean wasKeyPressed = false;
   private static boolean wasArmTogglePressed = false;
   private static long flashStartTime = 0L;
   private static int currentTier = 0;
   private static final Map<UUID, Boolean> raisedHands = new HashMap<>();
   private static final Map<UUID, Long> highFiveAnimStart = new HashMap<>();
   public static final long HIGH_FIVE_ANIM_DURATION = 1458L;
   private static long comboWindowStart = 0L;
   private static boolean inComboWindow = false;
   private static final long COMBO_WINDOW_MS = 750L;
   private static float lockedHugPitch = 0.0F;
   private static float lockedHugYaw = 0.0F;
   private static boolean hugCameraLocked = false;
   private static final Map<UUID, Boolean> frozenPlayers = new HashMap<>();

   public static void register() {
      highFiveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coopmoves.highfive", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 72, CoopKeyCategories.COOPMOVES));
      ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.HandRaisedSyncPayload.ID, (payload, context) -> context.client().execute(() -> {
         raisedHands.put(payload.playerId(), payload.raised());
         Minecraft client = context.client();
         if (client.player != null && client.player.getUUID().equals(payload.playerId())) {
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.HighFiveAnimPayload.ID, (payload, context) -> context.client().execute(() -> {
         UUID playerId = payload.playerId();
         int animState = payload.animState();
         Minecraft client = context.client();
         if (client.level != null) {
            boolean found = false;

            label24:
            for (Player player : client.level.players()) {
               if (player.getUUID().equals(playerId)) {
                  found = true;
                  switch (animState) {
                     case 1:
                        CoopAnimationHandler.playHighFiveStart(player);
                        break label24;
                     case 2:
                        CoopAnimationHandler.playHighFiveEnd(player);
                        break label24;
                     case 3:
                        CoopAnimationHandler.playHighFiveHit(player);
                        break label24;
                     case 4:
                        CoopAnimationHandler.playHighFiveSike(player);
                     default:
                        break label24;
                  }
               }
            }

            if (!found) {
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         HighFiveHandler.HighFiveSuccessPayload.ID,
         (payload, context) -> context.client()
            .execute(() -> onHighFiveSuccess(payload.x(), payload.y(), payload.z(), payload.player1(), payload.player2(), payload.tier()))
      );
      ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.ComboWindowPayload.ID, (payload, context) -> context.client().execute(() -> {
         comboWindowStart = System.currentTimeMillis();
         inComboWindow = true;
      }));
      ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.ComboWindowClosePayload.ID, (payload, context) -> context.client().execute(() -> {
         inComboWindow = false;
         Minecraft client = context.client();
         if (client.player != null) {
            UUID myId = client.player.getUUID();
            CoopAnimationHandler.syncAnimState(myId, CoopAnimationHandler.AnimState.NONE);
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         HighFiveHandler.FreezeStatePayload.ID, (payload, context) -> context.client().execute(() -> frozenPlayers.put(payload.playerId(), payload.frozen()))
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            boolean inActiveHandshake = HighFiveShakeClientHandler.isLocalPlayerInHandshake();
            boolean isArmedForHandshake = HighFiveShakeClientHandler.isLocalPlayerArmed();
            if (inActiveHandshake) {
               long winHandle = Minecraft.getInstance().getWindow().handle();
               boolean w = GLFW.glfwGetKey(winHandle, 87) == 1;
               boolean a = GLFW.glfwGetKey(winHandle, 65) == 1;
               boolean s = GLFW.glfwGetKey(winHandle, 83) == 1;
               boolean d = GLFW.glfwGetKey(winHandle, 68) == 1;
               int dirOrdinal;
               if (w) {
                  dirOrdinal = HighFiveShakeHandler.Dir.W.ordinal();
               } else if (a) {
                  dirOrdinal = HighFiveShakeHandler.Dir.A.ordinal();
               } else if (s) {
                  dirOrdinal = HighFiveShakeHandler.Dir.S.ordinal();
               } else if (d) {
                  dirOrdinal = HighFiveShakeHandler.Dir.D.ordinal();
               } else {
                  dirOrdinal = HighFiveShakeHandler.Dir.NONE.ordinal();
               }

               ClientPlayNetworking.send(new HighFiveShakeHandler.ShakeDirPayload(dirOrdinal));
               boolean fPressed = GLFW.glfwGetKey(winHandle, 70) == 1;
               if (fPressed) {
                  ClientPlayNetworking.send(new HighFiveShakeHandler.ShakeEndKeyPayload());
               }
            }

            if (inActiveHandshake || isArmedForHandshake) {
               wasKeyPressed = highFiveKey.isDown();
            } else if (BrosClientHandler.blocksHighFive()) {
               wasKeyPressed = highFiveKey.isDown();
            } else if (CoopAnimationHandler.isInHuddleAnim(client.player.getUUID())) {
               wasKeyPressed = highFiveKey.isDown();
            } else if (GrabInputHandler.isLocalPlayerInGrab()) {
               wasKeyPressed = highFiveKey.isDown();
            } else if (!HandSpinClientHandler.isLocalPlayerSpinning() && !HandSpinClientHandler.isLocalPlayerMonkeFlying()) {
               UUID hugCheckId = client.player.getUUID();
               boolean inHug = CoopAnimationHandler.isInHugAnim(hugCheckId);
               boolean inHuddle = CoopAnimationHandler.isInHuddleAnim(hugCheckId);
               if (!inHug && !inHuddle) {
                  hugCameraLocked = false;
               } else {
                  if (!hugCameraLocked) {
                     lockedHugPitch = client.player.getXRot();
                     lockedHugYaw = client.player.getYRot();
                     hugCameraLocked = true;
                  }

                  client.player.setXRot(lockedHugPitch);
                  client.player.setYRot(lockedHugYaw);
                  if (client.player.xRotO != lockedHugPitch) {
                     client.player.xRotO = lockedHugPitch;
                  }

                  if (client.player.yRotO != lockedHugYaw) {
                     client.player.yRotO = lockedHugYaw;
                  }
               }

               UUID myId = client.player.getUUID();
               Long animStart = highFiveAnimStart.get(myId);
               if (animStart != null) {
                  long elapsed = System.currentTimeMillis() - animStart;
                  if (elapsed > 1458L) {
                     CoopAnimationHandler.AnimState currentState = CoopAnimationHandler.getAnimState(myId);
                     if (currentState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT) {
                        highFiveAnimStart.remove(myId);
                        raisedHands.put(myId, false);
                        CoopAnimationHandler.syncAnimState(myId, CoopAnimationHandler.AnimState.NONE);
                     } else if (currentState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_COMBO) {
                        highFiveAnimStart.remove(myId);
                     }
                  }
               }

               boolean isKeyPressed = highFiveKey.isDown();
               if (isKeyPressed && !wasKeyPressed) {
                  if (CoopAnimationHandler.isLocalPlayerCuffed()) {
                     wasKeyPressed = true;
                     return;
                  }

                  if (ChargedDapClientHandler.isPlayerFrozen() && !QTEClientHandler.isActive()) {
                     wasKeyPressed = true;
                     return;
                  }

                  if (ChargedDapClientHandler.isDapBadBlocking()) {
                     wasKeyPressed = true;
                     return;
                  }
               }

               if (isKeyPressed && !wasKeyPressed) {
                  if (FusionClientHandler.isQTEOpen()) {
                     FusionClientHandler.handleQTEHPress();
                     wasKeyPressed = true;
                     return;
                  }

                  if (QTEClientHandler.isActive()) {
                     if (!inComboWindow) {
                        QTEClientHandler.handleKeyPress("H");
                        ChargedDapClientHandler.postQTEBlockEndMs = System.currentTimeMillis() + 1000L;
                        wasKeyPressed = true;
                        return;
                     }

                     if ("H".equals(QTEClientHandler.getExpectedButton())) {
                        QTEClientHandler.handleKeyPress("H");
                        ChargedDapClientHandler.postQTEBlockEndMs = System.currentTimeMillis() + 1000L;
                        wasKeyPressed = true;
                        return;
                     }
                  }
               }

               if (inComboWindow && isKeyPressed && !wasKeyPressed) {
                  ClientPlayNetworking.send(new HighFiveHandler.ComboRequestPayload());
                  inComboWindow = false;
               }

               if (inComboWindow && System.currentTimeMillis() - comboWindowStart > 750L) {
                  inComboWindow = false;
               }

               if (CoopAnimationHandler.isInDapAnim(client.player.getUUID())) {
                  wasKeyPressed = isKeyPressed;
               } else {
                  if (isKeyPressed) {
                     ClientPlayNetworking.send(new HighFiveHandler.HighFiveHoldPayload());
                  }

                  if (!inActiveHandshake && raisedHands.getOrDefault(client.player.getUUID(), false)) {
                     boolean rightClickNow = client.options.keyUse.isDown();
                     if (rightClickNow && !wasArmTogglePressed) {
                        ClientPlayNetworking.send(new HighFiveShakeHandler.ShakeArmTogglePayload());
                     }

                     wasArmTogglePressed = rightClickNow;
                  } else {
                     wasArmTogglePressed = false;
                  }

                  if (isKeyPressed && !wasKeyPressed) {
                  }

                  if (!inComboWindow && isKeyPressed && !wasKeyPressed) {
                     if (ChargedDapClientHandler.isLocalPlayerCharging()) {
                        client.player.displayClientMessage(Component.literal("§cCan't high five while charging dap!"), true);
                     } else if (!client.player.getMainHandItem().isEmpty()) {
                        client.player.displayClientMessage(Component.literal("§cHands must be empty for high five!"), true);
                     } else {
                        raisedHands.put(client.player.getUUID(), true);
                        boolean rightClickHeld = client.options.keyUse.isDown();
                        boolean inGrabReady = PoseNetworking.poseStates.getOrDefault(client.player.getUUID(), PoseState.NONE) == PoseState.GRAB_READY;
                        if (rightClickHeld && !inGrabReady) {
                           ClientPlayNetworking.send(new HighFiveHandler.SikeRequestPayload());
                        } else {
                           ClientPlayNetworking.send(new HighFiveHandler.HighFiveRequestPayload());
                        }
                     }
                  }

                  wasKeyPressed = isKeyPressed;
               }
            } else {
               wasKeyPressed = highFiveKey.isDown();
            }
         }
      });
      HudRenderCallback.EVENT.register(HighFiveClientHandler::renderHUD);
   }

   private static void onHighFiveSuccess(double x, double y, double z, UUID player1, UUID player2, int tier) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         UUID myId = client.player.getUUID();
         if (myId.equals(player1) || myId.equals(player2)) {
            boolean now = raisedHands.getOrDefault(myId, false);
         }

         raisedHands.put(player1, false);
         raisedHands.put(player2, false);
         if (myId.equals(player1) || myId.equals(player2)) {
            boolean var14 = raisedHands.getOrDefault(myId, false);
         }

         long now = System.currentTimeMillis();
         highFiveAnimStart.put(player1, now);
         highFiveAnimStart.put(player2, now);
         if (myId.equals(player1) || myId.equals(player2)) {
            flashStartTime = now;
            currentTier = tier;
            client.player.swing(InteractionHand.MAIN_HAND);

            String message = switch (tier) {
               case 0 -> "§6 High Five!";
               case 1 -> "§e Nice High Five! ";
               case 2 -> "§a§l BIG HIGH FIVE! ";
               case 3 -> "§c§l⚡ EXPLOSIVE HIGH FIVE! ⚡";
               default -> "§6 High Five!";
            };
            client.player.displayClientMessage(Component.literal(message), true);
         }
      }
   }

   private static void renderHUD(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         int screenWidth = client.getWindow().getGuiScaledWidth();
         int screenHeight = client.getWindow().getGuiScaledHeight();

         long flashDuration = switch (currentTier) {
            case 0 -> 200L;
            case 1 -> 250L;
            case 2 -> 350L;
            case 3 -> 500L;
            default -> 200L;
         };
         long timeSinceFlash = System.currentTimeMillis() - flashStartTime;
         if (timeSinceFlash < flashDuration) {
            float progress = (float)timeSinceFlash / (float)flashDuration;

            int baseAlpha = switch (currentTier) {
               case 0 -> 120;
               case 1 -> 150;
               case 2 -> 200;
               case 3 -> 255;
               default -> 120;
            };
            int alpha = (int)((1.0F - progress) * baseAlpha);

            int flashColor = switch (currentTier) {
               case 0 -> alpha << 24 | 16777113;
               case 1 -> alpha << 24 | 16763904;
               case 2 -> alpha << 24 | 65416;
               case 3 -> alpha << 24 | 16777215;
               default -> alpha << 24 | 16777113;
            };
            context.fill(0, 0, screenWidth, screenHeight, flashColor);
         }

         UUID myId = client.player.getUUID();
         boolean handRaised = raisedHands.getOrDefault(myId, false);
         if (handRaised) {
            if (System.currentTimeMillis() % 2000L < 50L) {
            }

            String text = " Ready for High Five!";
            int textWidth = client.font.width(text);
            int textX = (screenWidth - textWidth) / 2;
            int textY = screenHeight / 2 - 40;
            float pulse = (float)(Math.sin(System.currentTimeMillis() / 150.0) * 0.3 + 0.7);
            int alpha = (int)(pulse * 255.0F);
            int color = alpha << 24 | 16776960;
            context.drawString(client.font, text, textX, textY, color, true);
         }

         if (inComboWindow && !FusionClientHandler.isQTEOpen() && !FusionClientHandler.isGWindowOpen()) {
            long elapsed = System.currentTimeMillis() - comboWindowStart;
            long remaining = 750L - elapsed;
            if (remaining > 0L) {
               String gKey = "G";
               String hKey = "H";

               try {
                  KeyMapping ck = ChargedDapClientHandler.getChargeKey();
                  if (ck != null) {
                     gKey = ck.getTranslatedKeyMessage().getString().toUpperCase();
                  }
               } catch (Exception var23) {
               }

               try {
                  if (highFiveKey != null) {
                     hKey = highFiveKey.getTranslatedKeyMessage().getString().toUpperCase();
                  }
               } catch (Exception var22) {
               }

               float pulse = (float)(Math.sin(System.currentTimeMillis() / 80.0) * 0.4 + 0.6);
               int alpha = (int)(pulse * 255.0F);
               int color = (float)elapsed / 750.0F < 0.5F ? alpha << 24 | 16776960 : alpha << 24 | 16729088;
               String text = "[" + hKey + "] Combo";
               int tw = client.font.width(text);
               context.drawString(client.font, text, (screenWidth - tw) / 2, screenHeight / 2 + 10, color, true);
            }
         }
      }
   }

   public static boolean isInComboWindow() {
      return inComboWindow;
   }

   public static void clearComboWindow() {
      inComboWindow = false;
   }

   public static boolean isInHugOpportunityWindow() {
      return inComboWindow;
   }

   public static boolean hasHandRaised(UUID playerId) {
      return raisedHands.getOrDefault(playerId, false);
   }

   public static KeyMapping getHighFiveKey() {
      return highFiveKey;
   }

   public static String getHighFiveBlockReason() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         return "unknown";
      }

      UUID myId = client.player.getUUID();
      if (highFiveKey != null && highFiveKey.isDown()) {
         return "H key pressed";
      }

      if (raisedHands.getOrDefault(myId, false)) {
         return "raisedHands map = true";
      }

      Long animStart = highFiveAnimStart.get(myId);
      if (animStart != null) {
         long elapsed = System.currentTimeMillis() - animStart;
         if (elapsed <= 1458L) {
            return "Animation playing (" + elapsed + "ms / 1458ms)";
         }
      }

      CoopAnimationHandler.AnimState animState = CoopAnimationHandler.getAnimState(myId);
      return animState != CoopAnimationHandler.AnimState.HIGHFIVE_START && animState != CoopAnimationHandler.AnimState.HIGHFIVE_HIT
         ? "unknown"
         : "Animation state = " + animState;
   }

   public static boolean isLocalPlayerInHighFive() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         return false;
      }

      UUID myId = client.player.getUUID();
      if (highFiveKey != null && highFiveKey.isDown()) {
         return true;
      }

      if (raisedHands.getOrDefault(myId, false)) {
         return true;
      }

      Long animStart = highFiveAnimStart.get(myId);
      if (animStart != null) {
         long elapsed = System.currentTimeMillis() - animStart;
         if (elapsed <= 1458L) {
            return true;
         }

         highFiveAnimStart.remove(myId);
         raisedHands.put(myId, false);
      }

      CoopAnimationHandler.AnimState animState = CoopAnimationHandler.getAnimState(myId);
      boolean inAnim = animState == CoopAnimationHandler.AnimState.HIGHFIVE_START || animState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT;
      if (!inAnim && animState == CoopAnimationHandler.AnimState.HIGHFIVE_END) {
         raisedHands.put(myId, false);
         highFiveAnimStart.remove(myId);
      }

      return inAnim;
   }

   public static float getHighFiveAnimProgress(UUID playerId) {
      Long startTime = highFiveAnimStart.get(playerId);
      if (startTime == null) {
         return -1.0F;
      } else {
         long elapsed = System.currentTimeMillis() - startTime;
         if (elapsed > 1458L) {
            highFiveAnimStart.remove(playerId);
            return -1.0F;
         } else {
            return (float)elapsed / 1458.0F;
         }
      }
   }

   public static void cleanup(UUID playerId) {
      raisedHands.remove(playerId);
      highFiveAnimStart.remove(playerId);
      frozenPlayers.remove(playerId);
   }

   public static boolean isLocalPlayerFrozen() {
      Minecraft client = Minecraft.getInstance();
      return client.player == null ? false : frozenPlayers.getOrDefault(client.player.getUUID(), false);
   }

   public static boolean isPlayerFrozen(UUID playerId) {
      return frozenPlayers.getOrDefault(playerId, false);
   }
}
