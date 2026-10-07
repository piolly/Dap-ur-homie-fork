package com.cooptest.client;

import com.cooptest.ModSounds;
import com.cooptest.StrongSlapHandler;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;

public class StrongSlapClientHandler {
   private static boolean localPlayerFrozen = false;
   private static boolean neckBroken = false;
   private static long lockEndTime = 0L;
   private static final float LOCK_PITCH = 90.0F;
   private static boolean lookLocked = false;
   private static float lockedYaw = 0.0F;
   private static float lockedPitch = 0.0F;
   private static long flashEndTime = 0L;
   private static int flashColor = 16711680;
   private static final long FLASH_DURATION_MS = 600L;
   private static final Map<SoundSource, Double> savedVolumes = new EnumMap<>(SoundSource.class);
   private static boolean deafened = false;

   public static boolean isLocalPlayerFrozen() {
      return localPlayerFrozen;
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(StrongSlapHandler.MoveFreezePayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         if (ctx.client().player != null) {
            if (ctx.client().player.getUUID().equals(payload.playerId())) {
               localPlayerFrozen = payload.frozen();
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(StrongSlapHandler.NeckBrokenPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.playerId())) {
               if (payload.durationMs() <= 0L) {
                  neckBroken = false;
                  lockEndTime = 0L;
                  localPlayerFrozen = false;
                  restoreSoundVolumes(client);
               } else {
                  neckBroken = true;
                  lockEndTime = System.currentTimeMillis() + payload.durationMs();
                  client.player.setXRot(90.0F);
                  muteSoundVolumes(client);
               }
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(StrongSlapHandler.TortureTickPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.playerId())) {
               client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TORTURE, 1.0F));
               flashColor = payload.randomColor();
               flashEndTime = System.currentTimeMillis() + 600L;
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(StrongSlapHandler.LookLockPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
         Minecraft client = ctx.client();
         if (client.player != null) {
            if (client.player.getUUID().equals(payload.playerId())) {
               lookLocked = payload.locked();
               if (lookLocked) {
                  lockedYaw = payload.targetYaw();
                  lockedPitch = client.player.getXRot();
                  client.player.setYRot(lockedYaw);
               }
            }
         }
      }));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            if (lookLocked) {
               client.player.setYRot(lockedYaw);
               client.player.setXRot(lockedPitch);
            }

            if (neckBroken) {
               long now = System.currentTimeMillis();
               if (now >= lockEndTime) {
                  neckBroken = false;
                  localPlayerFrozen = false;
                  restoreSoundVolumes(client);
                  return;
               }

               float currentPitch = client.player.getXRot();
               if (currentPitch < 90.0F) {
                  client.player.setXRot(90.0F);
                  ClientPlayNetworking.send(new StrongSlapHandler.NeckResistPayload(client.player.getUUID()));
               }
            }
         }
      });
      HudRenderCallback.EVENT.register(StrongSlapClientHandler::renderHUD);
   }

   private static void muteSoundVolumes(Minecraft client) {
      if (!deafened) {
         deafened = true;

         for (SoundSource cat : SoundSource.values()) {
            if (cat != SoundSource.MASTER) {
               double current = ((Number)client.options.getSoundSourceOptionInstance(cat).get()).doubleValue();
               savedVolumes.put(cat, current);
               coop$setVolume(client.options.getSoundSourceOptionInstance(cat), 0.0);
            }
         }

         client.getSoundManager().stop();
      }
   }

   private static void restoreSoundVolumes(Minecraft client) {
      if (deafened) {
         deafened = false;

         for (SoundSource cat : SoundSource.values()) {
            if (cat != SoundSource.MASTER) {
               Double saved = savedVolumes.get(cat);
               if (saved != null) {
                  coop$setVolume(client.options.getSoundSourceOptionInstance(cat), saved);
               }
            }
         }

         savedVolumes.clear();
      }
   }

   private static void coop$setVolume(OptionInstance<?> opt, double value) {
      Object current = opt.get();
      if (current instanceof Float) {
         opt.set((float)value);
      } else {
         opt.set(value);
      }
   }

   private static void renderHUD(GuiGraphics ctx, DeltaTracker tickCounter) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         int screenW = ctx.guiWidth();
         int screenH = ctx.guiHeight();
         long now = System.currentTimeMillis();
         if (neckBroken) {
            long remaining = lockEndTime - now;
            if (remaining > 0L) {
               ctx.fill(0, 0, screenW, screenH, -872415232);
            }
         }

         if (now < flashEndTime) {
            long remaining = flashEndTime - now;
            int alpha = (int)((float)remaining / 600.0F * 255.0F);
            alpha = Math.min(255, Math.max(0, alpha));
            int color = alpha << 24 | flashColor & 16777215;
            ctx.fill(0, 0, screenW, screenH, color);
         }

         if (neckBroken) {
            long remaining = lockEndTime - now;
            if (remaining <= 0L) {
               return;
            }

            int vig = 130;
            int t = 18;
            ctx.fill(0, 0, screenW, t, vig << 24 | 12255232);
            ctx.fill(0, screenH - t, screenW, screenH, vig << 24 | 12255232);
            ctx.fill(0, 0, t, screenH, vig << 24 | 12255232);
            ctx.fill(screenW - t, 0, screenW, screenH, vig << 24 | 12255232);
         }
      }
   }

   public static boolean isNeckBroken() {
      return neckBroken;
   }
}
