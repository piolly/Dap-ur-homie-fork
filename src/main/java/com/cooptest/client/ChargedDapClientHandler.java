package com.cooptest.client;

import com.cooptest.ChargedDapHandler;
import com.cooptest.CoopMovesConfig;
import com.cooptest.GrabInputHandler;
import com.cooptest.NormalFacingDapHandler;
import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.highfive.client.HighFiveShakeClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ChargedDapClientHandler {
   private static KeyMapping chargedDapKey;
   private static boolean wasKeyPressed = false;
   private static boolean isCharging = false;
   private static boolean wasFireCharging = false;
   private static boolean fireChargeComplete = false;
   private static float lastFireLevel = 0.0F;
   private static long chargeStartTime = 0L;
   private static long whiffCooldownEnd = 0L;
   public static long postQTEBlockEndMs = 0L;
   private static final Map<UUID, Float> otherPlayerCharges = new HashMap<>();
   private static final Map<UUID, Float> otherPlayerFire = new HashMap<>();
   private static final Map<UUID, Boolean> otherPlayerCharging = new HashMap<>();
   private static float localFireLevel = 0.0F;
   private static boolean isHeavenReady = false;
   private static long heavenReadyStartTime = 0L;
   private static long flashStartTime = 0L;
   private static int resultTier = 0;
   private static boolean resultPerfect = false;
   private static long perfectImpactStartTime = 0L;
   private static boolean isPerfectDapFrozen = false;
   private static boolean perfectImpactActive = false;
   private static int perfectDapImpactFrame = 0;
   private static long perfectDapImpactFrameStartTime = 0L;
   private static boolean facingDapImpactActive = false;
   private static long facingDapImpactStartMs = 0L;
   private static Identifier IMPAC7_TEXTURE;
   private static Identifier IMPAC8_TEXTURE;
   private static Identifier IMPAC9_TEXTURE;
   public static boolean dropKickImpactActive = false;
   private static long dropKickImpactStartMs = 0L;
   private static final long DK_FADE_IN = 50L;
   private static final long DK_FRAME1 = 150L;
   private static final long DK_FRAME2 = 230L;
   private static final long DK_FRAME3 = 310L;
   private static final long DK_FADE_OUT = 430L;
   private static long dapBadBlockEnd = 0L;
   private static boolean inFaceDapSession = false;
   private static float faceDapLockedYaw = Float.NaN;
   private static final long WHITE_FADE_DURATION = 30L;
   private static final long IMPACT1_END = 80L;
   private static final long IMPACT2_END = 130L;
   private static final long IMPACT3_END = 180L;
   private static Identifier IMPACT1_TEXTURE;
   private static Identifier IMPACT2_TEXTURE;
   private static Identifier IMPACT3_TEXTURE;
   private static Identifier PERFECT_FRAME0_TEXTURE;
   private static Identifier PERFECT_FRAME1_TEXTURE;
   private static Identifier PERFECT_FRAME2_TEXTURE;
   private static Identifier PERFECT_FRAME3_TEXTURE;
   private static KeyMapping fireDapComboKey;
   private static boolean fireDapWasKeyPressed = false;
   private static long fireDapComboWindowStart = 0L;
   private static boolean inFireDapComboWindow = false;
   private static final long FIRE_DAP_COMBO_WINDOW_MS = 2200L;
   private static final Map<UUID, Boolean> fireDapFrozenPlayers = new HashMap<>();
   private static final Set<UUID> fireDapFirstPersonPlayers = new HashSet<>();
   private static final long CHARGE_TIME_MS = 250L;
   private static final long FLASH_DURATION = 500L;

   public static boolean isDapBadBlocking() {
      return System.currentTimeMillis() < dapBadBlockEnd;
   }

   public static void triggerDapBadBlock() {
      dapBadBlockEnd = System.currentTimeMillis() + 1667L;
   }

   public static void setInFaceDapSession(boolean v) {
      inFaceDapSession = v;
      if (v) {
         faceDapLockedYaw = Float.NaN;
      }
   }

   public static boolean isFaceDapYawLocked() {
      return inFaceDapSession;
   }

   public static boolean isInFaceDapSession() {
      return inFaceDapSession;
   }

   public static void tickFaceDapYawLock() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         faceDapLockedYaw = Float.NaN;
      } else if (!inFaceDapSession) {
         faceDapLockedYaw = Float.NaN;
      } else {
         if (Float.isNaN(faceDapLockedYaw)) {
            faceDapLockedYaw = mc.player.getYRot();
         }

         mc.player.setYRot(faceDapLockedYaw);
         mc.player.setYHeadRot(faceDapLockedYaw);
         mc.player.setYBodyRot(faceDapLockedYaw);
      }
   }

   public static void triggerDropKickImpact() {
      dropKickImpactActive = true;
      dropKickImpactStartMs = System.currentTimeMillis();
   }

   public static boolean isFireDapJKeyHeld() {
      return fireDapComboKey != null && fireDapComboKey.isDown();
   }

   public static void forceStopCharging() {
      isCharging = false;
      localFireLevel = 0.0F;
      wasFireCharging = false;
      fireChargeComplete = false;
      lastFireLevel = 0.0F;
   }

   public static KeyMapping getChargedDapKey() {
      return chargedDapKey;
   }

   public static void cancelChargeForBros() {
      Minecraft client = Minecraft.getInstance();
      boolean wasCharging = isCharging;
      forceStopCharging();
      if (wasCharging && client.player != null) {
         CoopAnimationHandler.stopDapCharge(client.player);
      }
   }

   public static boolean isLocalPlayerCharging() {
      return isCharging;
   }

   public static float getLocalChargePercent() {
      if (isCharging && chargeStartTime != 0L) {
         long elapsed = System.currentTimeMillis() - chargeStartTime;
         return Math.min(1.0F, (float)elapsed / 250.0F);
      } else {
         return 0.0F;
      }
   }

   public static void register() {
      chargedDapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coopmoves.dap", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 71, CoopKeyCategories.COOPMOVES));
      ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ChargeSyncPayload.ID, (payload, context) -> context.client().execute(() -> {
         UUID playerId = payload.playerId();
         Minecraft client = Minecraft.getInstance();
         if (payload.isCharging()) {
            otherPlayerCharges.put(playerId, payload.chargePercent());
            otherPlayerFire.put(playerId, payload.firePercent());
            otherPlayerCharging.put(playerId, true);
            if (client.player != null && client.player.getUUID().equals(playerId)) {
               float newFireLevel = CoopMovesConfig.get().enableFireDap ? payload.firePercent() : 0.0F;
               if (newFireLevel < localFireLevel - 0.1F && wasFireCharging) {
                  wasFireCharging = false;
                  fireChargeComplete = false;
                  CoopAnimationHandler.playDapChargeIdle(client.player);
               }

               localFireLevel = newFireLevel;
            }
         } else {
            otherPlayerCharges.remove(playerId);
            otherPlayerFire.remove(playerId);
            otherPlayerCharging.remove(playerId);
            if (client.player != null && client.player.getUUID().equals(playerId)) {
               localFireLevel = 0.0F;
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.WhiffCooldownPayload.ID, (payload, context) -> context.client().execute(() -> {
         long duration = payload.cooldownDurationMs();
         whiffCooldownEnd = System.currentTimeMillis() + duration;
         isCharging = false;
         localFireLevel = 0.0F;
         wasFireCharging = false;
         fireChargeComplete = false;
         lastFireLevel = 0.0F;
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            CoopAnimationHandler.stopDapCharge(client.player);
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ImpactFramePayload.ID, (payload, context) -> context.client().execute(() -> {
         if (payload.grayscale()) {
            perfectImpactStartTime = System.currentTimeMillis();
            perfectImpactActive = true;
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         ChargedDapHandler.DapResultPayload.ID,
         (payload, context) -> context.client()
            .execute(() -> onDapResult(payload.x(), payload.y(), payload.z(), payload.player1(), payload.player2(), payload.tier(), payload.perfectHit()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ChargedDapHandler.PerfectDapFreezePayload.ID, (payload, context) -> context.client().execute(() -> isPerfectDapFrozen = payload.frozen())
      );
      ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.PerfectDapImpactFramePayload.ID, (payload, context) -> context.client().execute(() -> {
         if (isPerfectDapFrozen) {
            long shakeDuration = CoopImpactHandler.PERFECT_DAP_SEQUENCE.length * 33L;
            CoopImpactHandler.start(CoopImpactHandler.PERFECT_DAP_SEQUENCE, 33L, true);
            CoopCameraShakeHandler.shake(1.2F, shakeDuration);
            CoopChromaHandler.start();
            CoopRadialBlurHandler.start();
            CoopSpeedLinesRenderer.start();
            CoopScreenSquishHandler.trigger();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FacingDapImpactPayload.ID, (payload, context) -> context.client().execute(() -> {
         if (IMPAC7_TEXTURE == null) {
            IMPAC7_TEXTURE = Identifier.fromNamespaceAndPath("testcoop", "textures/gui/impact/impac7.png");
            IMPAC8_TEXTURE = Identifier.fromNamespaceAndPath("testcoop", "textures/gui/impact/impac8.png");
            IMPAC9_TEXTURE = Identifier.fromNamespaceAndPath("testcoop", "textures/gui/impact/impac9.png");
         }

         facingDapImpactActive = true;
         facingDapImpactStartMs = System.currentTimeMillis();
      }));
      ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.HeavenReadyPayload.ID, (payload, context) -> context.client().execute(() -> {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.playerId())) {
               if (payload.ready()) {
                  isHeavenReady = true;
                  heavenReadyStartTime = System.currentTimeMillis();
               } else {
                  isHeavenReady = false;
               }
            }
         }
      }));
      fireDapComboKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coopmoves.fire_dap_combo", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, 74, CoopKeyCategories.COOPMOVES));
      ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapWindowPayload.ID, (payload, context) -> context.client().execute(() -> {
         fireDapComboWindowStart = System.currentTimeMillis();
         inFireDapComboWindow = true;
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         ChargedDapHandler.FireDapFreezePayload.ID,
         (payload, context) -> context.client().execute(() -> fireDapFrozenPlayers.put(payload.playerId(), payload.frozen()))
      );
      ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapFirstPersonPayload.ID, (payload, context) -> context.client().execute(() -> {
         UUID playerId = payload.playerId();
         boolean show = payload.showBothHands();
         if (show) {
            fireDapFirstPersonPlayers.add(playerId);
            Minecraft client = context.client();
            if (client.player != null && client.player.getUUID().equals(playerId)) {
            }
         } else {
            fireDapFirstPersonPlayers.remove(playerId);
            Minecraft client = context.client();
            if (client.player != null && client.player.getUUID().equals(playerId)) {
            }
         }
      }));
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               tickFaceDapYawLock();
               if (client.player != null && CoopAnimationHandler.isInHuddleAnim(client.player.getUUID())) {
                  isCharging = false;
                  wasKeyPressed = chargedDapKey.isDown();
               } else if (client.player != null) {
                  if (BrosClientHandler.blocksDap()) {
                     if (isCharging) {
                        cancelChargeForBros();
                     }

                     wasKeyPressed = chargedDapKey.isDown();
                  } else if (HighfiveDapClientHandler.isActive()) {
                     wasKeyPressed = chargedDapKey.isDown();
                  } else if (DapFlairClientHandler.isArmed()) {
                     wasKeyPressed = chargedDapKey.isDown();
                  } else if (SikeFollowUpClientHandler.isArmed()) {
                     wasKeyPressed = chargedDapKey.isDown();
                  } else if (HighFiveShakeClientHandler.isLocalPlayerInHandshake() || HighFiveShakeClientHandler.isLocalPlayerArmed()) {
                     wasKeyPressed = chargedDapKey.isDown();
                  } else if (GrabInputHandler.isLocalPlayerInGrabOrReady()) {
                     wasKeyPressed = chargedDapKey.isDown();
                  } else if (HandSpinClientHandler.isInputBlockedBySpin(client.player.getUUID())) {
                     wasKeyPressed = chargedDapKey.isDown();
                  } else {
                     boolean isKeyPressed = chargedDapKey.isDown();
                     boolean onCooldown = System.currentTimeMillis() < whiffCooldownEnd;
                     boolean inHighFive = HighFiveClientHandler.isLocalPlayerInHighFive();
                     boolean inBlocking = CoopAnimationHandler.isInBlockingState(client.player.getUUID());
                     if (isKeyPressed && !wasKeyPressed) {
                        if (CoopAnimationHandler.isLocalPlayerCuffed()) {
                           wasKeyPressed = true;
                           return;
                        }

                        if (isPerfectDapFrozen && !QTEClientHandler.isActive() && !FusionClientHandler.isQTEOpen() && !FusionClientHandler.isGWindowOpen()) {
                           wasKeyPressed = true;
                           return;
                        }
                     }

                     if (isKeyPressed && !wasKeyPressed) {
                        if (FusionClientHandler.isQTEOpen()) {
                           FusionClientHandler.handleQTEGPress();
                           wasKeyPressed = true;
                           return;
                        }

                        if (FusionClientHandler.isGWindowOpen()) {
                           FusionClientHandler.handleGKeyPress();
                           wasKeyPressed = true;
                           return;
                        }

                        if (QTEClientHandler.isActive()) {
                           QTEClientHandler.handleKeyPress("G");
                           wasKeyPressed = true;
                           return;
                        }
                     }

                     if (isKeyPressed && !wasKeyPressed && !inBlocking) {
                        if (inHighFive || HighFiveClientHandler.isInComboWindow()) {
                           wasKeyPressed = true;
                           return;
                        }

                        if (inFaceDapSession) {
                           wasKeyPressed = true;
                           return;
                        }

                        if (isDapBadBlocking()) {
                           wasKeyPressed = true;
                           return;
                        }

                        if (CoopAnimationHandler.isInDapOrHighFiveAnim(client.player.getUUID())
                           || CoopAnimationHandler.isInHighFiveStartAnim(client.player.getUUID())) {
                           wasKeyPressed = true;
                           return;
                        }

                        if (onCooldown) {
                           long remaining = (whiffCooldownEnd - System.currentTimeMillis()) / 100L;
                           client.player.sendOverlayMessage(Component.literal("§cDap on cooldown! " + remaining / 10.0 + "s"));
                        } else if (!client.player.getMainHandItem().isEmpty()) {
                           client.player.sendOverlayMessage(Component.literal("§cMain hand must be empty for charged dap!"));
                        } else if (!isCharging) {
                           isCharging = true;
                           chargeStartTime = System.currentTimeMillis();
                           localFireLevel = 0.0F;
                           wasFireCharging = false;
                           fireChargeComplete = false;
                           lastFireLevel = 0.0F;
                           ClientPlayNetworking.send(new ChargedDapHandler.ChargeStartPayload());
                           CoopAnimationHandler.startDapCharge(client.player);
                        }
                     }

                     if (isCharging && inBlocking) {
                        isCharging = false;
                        localFireLevel = 0.0F;
                        wasFireCharging = false;
                        fireChargeComplete = false;
                        lastFireLevel = 0.0F;
                        CoopAnimationHandler.stopDapCharge(client.player);
                     }

                     if (isCharging && !onCooldown && !inBlocking) {
                        if (localFireLevel > 0.05F && !wasFireCharging) {
                           wasFireCharging = true;
                           fireChargeComplete = false;
                           CoopAnimationHandler.startFireDapCharge(client.player);
                        }

                        if (wasFireCharging && localFireLevel >= 0.99F && !fireChargeComplete) {
                           fireChargeComplete = true;
                           CoopAnimationHandler.playFireDapChargeIdle(client.player);
                        }

                        if (wasFireCharging && localFireLevel < 0.05F) {
                           wasFireCharging = false;
                           fireChargeComplete = false;
                           CoopAnimationHandler.playDapChargeIdle(client.player);
                        }

                        lastFireLevel = localFireLevel;
                     }

                     if (isCharging && onCooldown) {
                        isCharging = false;
                        localFireLevel = 0.0F;
                        wasFireCharging = false;
                        fireChargeComplete = false;
                        lastFireLevel = 0.0F;
                        CoopAnimationHandler.stopDapCharge(client.player);
                     }

                     if (!isKeyPressed && wasKeyPressed && isCharging) {
                        isCharging = false;
                        localFireLevel = 0.0F;
                        wasFireCharging = false;
                        fireChargeComplete = false;
                        lastFireLevel = 0.0F;
                        ClientPlayNetworking.send(new ChargedDapHandler.ChargeReleasePayload());
                     }

                     boolean fireDapJKeyPressed = fireDapComboKey.isDown();
                     if (inFireDapComboWindow && fireDapJKeyPressed && !fireDapWasKeyPressed) {
                        ClientPlayNetworking.send(new ChargedDapHandler.FireDapJPressPayload());
                        inFireDapComboWindow = false;
                     }

                     if (inFireDapComboWindow && System.currentTimeMillis() - fireDapComboWindowStart > 2200L) {
                        inFireDapComboWindow = false;
                     }

                     if (isKeyPressed && inFaceDapSession) {
                        ClientPlayNetworking.send(new NormalFacingDapHandler.DapLoopHoldPayload());
                     }

                     fireDapWasKeyPressed = fireDapJKeyPressed;
                     wasKeyPressed = isKeyPressed;
                  }
               }
            }
         );
      net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("cooptest", "chargeddapclienthandler_hud"), ChargedDapClientHandler::renderHUD);
   }

   private static void onDapResult(double x, double y, double z, UUID player1, UUID player2, int tier, boolean perfectHit) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         UUID myId = client.player.getUUID();
         boolean iAmInDap = myId.equals(player1) || myId.equals(player2);
         isCharging = false;
         localFireLevel = 0.0F;
         wasFireCharging = false;
         fireChargeComplete = false;
         lastFireLevel = 0.0F;
         otherPlayerCharges.clear();
         otherPlayerFire.clear();
         otherPlayerCharging.clear();
         if (iAmInDap) {
            CoopAnimationHandler.stopDapChargeLocalOnly(client.player);
            flashStartTime = System.currentTimeMillis();
            resultTier = tier;
            resultPerfect = perfectHit;
         }

         spawnTierParticles(client, x, y, z, tier, perfectHit);
      }
   }

   private static void spawnTierParticles(Minecraft client, double x, double y, double z, int tier, boolean perfect) {
      if (client.level != null) {
         ParticleOptions particle;
         int particleCount;
         switch (tier) {
            case 0:
               particle = ParticleTypes.SMOKE;
               particleCount = 5;
               break;
            case 1:
               particle = ParticleTypes.CRIT;
               particleCount = 10;
               break;
            case 2:
               particle = ParticleTypes.HAPPY_VILLAGER;
               particleCount = 15;
               break;
            case 3:
               particle = ParticleTypes.ENCHANT;
               particleCount = 20;
               break;
            case 4:
               particle = ParticleTypes.TOTEM_OF_UNDYING;
               particleCount = 25;
               break;
            case 5:
               particle = ParticleTypes.FLAME;
               particleCount = 30;
               break;
            default:
               particle = ParticleTypes.CRIT;
               particleCount = 5;
         }

         for (int i = 0; i < particleCount; i++) {
            double offsetX = (Math.random() - 0.5) * 0.5;
            double offsetY = (Math.random() - 0.5) * 0.5;
            double offsetZ = (Math.random() - 0.5) * 0.5;
            double velX = (Math.random() - 0.5) * 0.3;
            double velY = Math.random() * 0.2;
            double velZ = (Math.random() - 0.5) * 0.3;
            client.level.addParticle(particle, false, false, x + offsetX, y + offsetY, z + offsetZ, velX, velY, velZ);
         }

         if (perfect) {
            for (int i = 0; i < 8; i++) {
               double angle = i / 8.0 * Math.PI * 2.0;
               double offsetX = Math.cos(angle) * 0.3;
               double offsetZ = Math.sin(angle) * 0.3;
               client.level.addParticle(ParticleTypes.ENCHANT, false, false, x + offsetX, y + 0.5, z + offsetZ, 0.0, 0.1, 0.0);
            }
         }
      }
   }

   private static void renderHUD(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         if (!inFaceDapSession && !CoopAnimationHandler.isInHuddleAnim(client.player.getUUID())) {
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            long timeSinceFlash = System.currentTimeMillis() - flashStartTime;
            if (timeSinceFlash < 500L) {
               float progress = (float)timeSinceFlash / 500.0F;

               int baseAlpha = switch (resultTier) {
                  case 0 -> 30;
                  case 1 -> 40;
                  case 2 -> 50;
                  case 3 -> 60;
                  case 4 -> 80;
                  case 5 -> 100;
                  default -> 30;
               };
               if (resultPerfect) {
                  baseAlpha = Math.min(baseAlpha + 40, 140);
               }

               int alpha = (int)((1.0F - progress) * baseAlpha);

               int flashColor = switch (resultTier) {
                  case 0 -> alpha << 24 | 8947848;
                  case 1 -> alpha << 24 | 16776960;
                  case 2 -> alpha << 24 | 0xFF00;
                  case 3 -> alpha << 24 | 16755200;
                  case 4 -> alpha << 24 | 16711935;
                  case 5 -> alpha << 24 | 16729088;
                  default -> alpha << 24 | 16777215;
               };
               context.fill(0, 0, screenWidth, screenHeight, flashColor);
            }

            if (perfectImpactActive) {
               long elapsed = System.currentTimeMillis() - perfectImpactStartTime;
               if (elapsed < 30L) {
                  float fadeProgress = (float)elapsed / 30.0F;
                  int alpha = (int)(255.0F * fadeProgress);
                  context.fill(0, 0, screenWidth, screenHeight, alpha << 24 | 16777215);
               } else if (elapsed < 80L) {
                  context.blit(RenderPipelines.GUI_TEXTURED, IMPACT1_TEXTURE, 0, 0, 0.0F, 0.0F, screenWidth, screenHeight, 1920, 1080, 1920, 1080, -1);
               } else if (elapsed < 130L) {
                  context.blit(RenderPipelines.GUI_TEXTURED, IMPACT2_TEXTURE, 0, 0, 0.0F, 0.0F, screenWidth, screenHeight, 1920, 1080, 1920, 1080, -1);
               } else if (elapsed < 180L) {
                  context.blit(RenderPipelines.GUI_TEXTURED, IMPACT3_TEXTURE, 0, 0, 0.0F, 0.0F, screenWidth, screenHeight, 1920, 1080, 1920, 1080, -1);
               } else {
                  perfectImpactActive = false;
               }
            }

            if (facingDapImpactActive) {
               long elapsed = System.currentTimeMillis() - facingDapImpactStartMs;
               Identifier facingFrame;
               if (elapsed < 50L) {
                  facingFrame = IMPAC7_TEXTURE;
               } else if (elapsed < 100L) {
                  facingFrame = IMPAC8_TEXTURE;
               } else if (elapsed < 150L) {
                  facingFrame = IMPAC9_TEXTURE;
               } else if (elapsed < 200L) {
                  facingFrame = PERFECT_FRAME0_TEXTURE;
               } else {
                  facingDapImpactActive = false;
                  facingFrame = null;
               }

               if (facingFrame != null) {
                  context.blit(RenderPipelines.GUI_TEXTURED, facingFrame, 0, 0, 0.0F, 0.0F, screenWidth, screenHeight, 1920, 1080, 1920, 1080, -1);
               }
            }

            if (dropKickImpactActive) {
               long dke = System.currentTimeMillis() - dropKickImpactStartMs;
               if (dke >= 430L) {
                  dropKickImpactActive = false;
               } else {
                  Identifier dkTex;
                  float dkAlpha;
                  if (dke < 50L) {
                     dkTex = IMPACT1_TEXTURE;
                     dkAlpha = (float)dke / 50.0F;
                  } else if (dke < 150L) {
                     dkTex = IMPACT1_TEXTURE;
                     dkAlpha = 1.0F;
                  } else if (dke < 230L) {
                     dkTex = IMPACT2_TEXTURE;
                     dkAlpha = 1.0F;
                  } else if (dke < 310L) {
                     dkTex = IMPACT3_TEXTURE;
                     dkAlpha = 1.0F;
                  } else {
                     dkTex = IMPACT3_TEXTURE;
                     dkAlpha = 1.0F - (float)(dke - 310L) / 120.0F;
                  }

                  int dkTint = (int)(Math.max(0.0F, Math.min(1.0F, dkAlpha)) * 255.0F) << 24 | 16777215;
                  context.blit(RenderPipelines.GUI_TEXTURED, dkTex, 0, 0, 0.0F, 0.0F, screenWidth, screenHeight, 1920, 1080, 1920, 1080, dkTint);
               }
            }

            boolean onCooldown = System.currentTimeMillis() < whiffCooldownEnd;
            if (onCooldown && !isCharging) {
               long remaining = whiffCooldownEnd - System.currentTimeMillis();
               float cooldownProgress = (float)remaining / 800.0F;
               int barWidth = 40;
               int barHeight = 3;
               int barX = (screenWidth - barWidth) / 2;
               int barY = screenHeight / 2 + 20;
               context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 1140850688);
               int fillWidth = (int)(barWidth * cooldownProgress);
               context.fill(barX, barY, barX + fillWidth, barY + barHeight, -1140916224);
            }

            if (inFireDapComboWindow) {
               long elapsed = System.currentTimeMillis() - fireDapComboWindowStart;
               long remaining = 2200L - elapsed;
               if (remaining > 0L) {
                  String text = "§c§l\ud83d\udd25 PRESS J! \ud83d\udd25";
                  int textWidth = client.font.width(text);
                  int textX = (screenWidth - textWidth) / 2;
                  int textY = screenHeight / 2 + 10;
                  float pulse = (float)(Math.sin(System.currentTimeMillis() / 80.0) * 0.4 + 0.6);
                  int alpha = (int)(pulse * 255.0F);
                  float timeProgress = (float)elapsed / 2200.0F;
                  int color = timeProgress < 0.5F ? alpha << 24 | 16746496 : alpha << 24 | 0xFF0000;
                  context.text(client.font, text, textX, textY, color, true);
                  int barWidth = 100;
                  int barHeight = 3;
                  int barX = (screenWidth - barWidth) / 2;
                  int barY = textY + 12;
                  context.fill(barX, barY, barX + barWidth, barY + barHeight, Integer.MIN_VALUE);
                  int fillWidth = (int)(barWidth * (1.0F - timeProgress));
                  int barColor = timeProgress < 0.5F ? -30720 : -65536;
                  context.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor);
               }
            }

            if (isCharging && !onCooldown) {
               long elapsed = System.currentTimeMillis() - chargeStartTime;
               float chargePercent = Math.min(1.0F, (float)elapsed / 250.0F);
               float myFire = CoopMovesConfig.get().enableFireDap ? localFireLevel : 0.0F;
               int barWidth = 40;
               int barHeight = 3;
               int barX = (screenWidth - barWidth) / 2;
               int barY = screenHeight / 2 + 20;
               context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 1140850688);
               if (!(myFire > 0.05F)) {
                  int fillWidth = (int)(barWidth * chargePercent);
                  int fillColor;
                  if (chargePercent >= 0.99F) {
                     fillColor = -1157562624;
                  } else {
                     fillColor = -1140872704;
                  }

                  context.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
               } else {
                  int greenWidth = (int)(barWidth * chargePercent);
                  context.fill(barX, barY, barX + greenWidth, barY + barHeight, -1157562624);
                  int redWidth = (int)(barWidth * myFire);
                  int shakeX = 0;
                  int shakeY = 0;
                  if (!isHeavenReady) {
                     if (myFire >= 0.99F) {
                        shakeX = (int)((Math.random() - 0.5) * 4.0);
                        shakeY = (int)((Math.random() - 0.5) * 2.0);
                        context.fill(barX + shakeX, barY + shakeY, barX + redWidth + shakeX, barY + barHeight + shakeY, -570482176);
                     } else {
                        context.fill(barX, barY, barX + redWidth, barY + barHeight, -570482176);
                     }
                  } else {
                     long timeSinceReady = System.currentTimeMillis() - heavenReadyStartTime;
                     shakeX = (int)((Math.random() - 0.5) * 8.0);
                     shakeY = (int)((Math.random() - 0.5) * 6.0);
                     if (timeSinceReady % 500L < 250L) {
                        context.fill(barX + shakeX, barY + shakeY, barX + redWidth + shakeX, barY + barHeight + shakeY, -65281);
                     } else {
                        context.fill(barX + shakeX, barY + shakeY, barX + redWidth + shakeX, barY + barHeight + shakeY, -570482176);
                     }

                     context.fill(barX - 5 + shakeX, barY + 1 + shakeY, barX + barWidth + 5 + shakeX, barY + 2 + shakeY, -1);

                     for (int i = 0; i < barWidth; i++) {
                        int crackY = barY + i % 2 + shakeY;
                        context.fill(barX + i + shakeX, crackY, barX + i + 1 + shakeX, crackY + 1, -1996488705);
                     }

                     if (timeSinceReady % 100L < 50L) {
                        context.fill(barX - 2 + shakeX, barY - 3 + shakeY, barX - 1 + shakeX, barY - 2 + shakeY, -8960);
                        context.fill(barX + barWidth + 1 + shakeX, barY - 3 + shakeY, barX + barWidth + 2 + shakeX, barY - 2 + shakeY, -8960);
                     }
                  }
               }

               int partnerY = barY + 8;

               for (Entry<UUID, Float> entry : otherPlayerCharges.entrySet()) {
                  if (otherPlayerCharging.getOrDefault(entry.getKey(), false) && !entry.getKey().equals(client.player.getUUID())) {
                     boolean inRange = false;
                     if (client.level != null) {
                        for (AbstractClientPlayer player : client.level.players()) {
                           if (player.getUUID().equals(entry.getKey())) {
                              if (client.player.distanceTo(player) <= 20.0) {
                                 inRange = true;
                              }
                              break;
                           }
                        }
                     }

                     if (inRange) {
                        float partnerCharge = entry.getValue();
                        float partnerFire = otherPlayerFire.getOrDefault(entry.getKey(), 0.0F);
                        int pBarWidth = 30;
                        int pBarHeight = 2;
                        int pBarX = (screenWidth - pBarWidth) / 2;
                        context.fill(pBarX - 1, partnerY - 1, pBarX + pBarWidth + 1, partnerY + pBarHeight + 1, 855638016);
                        int pFillWidth = (int)(pBarWidth * partnerCharge);
                        int pFillColor = partnerFire > 0.1F ? -1426111488 : (partnerCharge >= 0.99F ? -1442775296 : -1426085376);
                        context.fill(pBarX, partnerY, pBarX + pFillWidth, partnerY + pBarHeight, pFillColor);
                        partnerY += 6;
                     }
                  }
               }
            }

            QTEClientHandler.renderHUD(context, screenWidth, screenHeight);
         } else {
            isCharging = false;
            localFireLevel = 0.0F;
         }
      }
   }

   public static boolean isCurrentlyCharging() {
      return isCharging;
   }

   public static KeyMapping getChargeKey() {
      return chargedDapKey;
   }

   public static KeyMapping getComboKey() {
      return fireDapComboKey;
   }

   public static float getChargePercent() {
      if (!isCharging) {
         return 0.0F;
      }

      long elapsed = System.currentTimeMillis() - chargeStartTime;
      return Math.min(1.0F, (float)elapsed / 250.0F);
   }

   public static float getFireLevel() {
      return localFireLevel;
   }

   public static boolean isPlayerCharging(UUID playerId) {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && client.player.getUUID().equals(playerId) ? isCharging : otherPlayerCharging.getOrDefault(playerId, false);
   }

   public static float getPlayerChargePercent(UUID playerId) {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && client.player.getUUID().equals(playerId) ? getChargePercent() : otherPlayerCharges.getOrDefault(playerId, 0.0F);
   }

   public static float getPlayerFireLevel(UUID playerId) {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && client.player.getUUID().equals(playerId) ? localFireLevel : otherPlayerFire.getOrDefault(playerId, 0.0F);
   }

   public static boolean isOnWhiffCooldown() {
      return System.currentTimeMillis() < whiffCooldownEnd;
   }

   public static boolean isImpactFrameActive() {
      return perfectImpactActive;
   }

   public static boolean isLocalPlayerPerfectDapFrozen() {
      return isPerfectDapFrozen;
   }

   public static boolean isLocalPlayerFireDapFrozen() {
      Minecraft client = Minecraft.getInstance();
      return client.player == null ? false : fireDapFrozenPlayers.getOrDefault(client.player.getUUID(), false);
   }

   public static boolean shouldShowFireDapFirstPerson() {
      Minecraft client = Minecraft.getInstance();
      return client.player == null ? false : fireDapFirstPersonPlayers.contains(client.player.getUUID());
   }

   public static boolean isInQTEWindow() {
      return QTEClientHandler.isActive();
   }

   public static boolean isPlayerFrozen() {
      return isPerfectDapFrozen;
   }

   public static String getQTEExpectedButton() {
      return QTEClientHandler.getExpectedButton();
   }

   public static long getQTEWindowStart() {
      return QTEClientHandler.getWindowStart();
   }

   public static long getQTEWindowEnd() {
      return QTEClientHandler.getWindowEnd();
   }

   public static int getQTEStage() {
      return QTEClientHandler.getStage();
   }

   public static void cleanup(UUID playerId) {
      otherPlayerCharges.remove(playerId);
      otherPlayerFire.remove(playerId);
      otherPlayerCharging.remove(playerId);
      fireDapFrozenPlayers.remove(playerId);
      fireDapFirstPersonPlayers.remove(playerId);
   }
}
