package com.cooptest.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class CoopClientSettings {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("coopmoves_client.json").toFile();
   private static CoopClientSettings INSTANCE;
   public boolean cameraShakeEnabled = true;
   public boolean impactFramesEnabled = true;

   public static CoopClientSettings get() {
      if (INSTANCE == null) {
         load();
      }

      return INSTANCE;
   }

   public static void load() {
      if (FILE.exists()) {
         try (FileReader r = new FileReader(FILE)) {
            CoopClientSettings loaded = (CoopClientSettings)GSON.fromJson(r, CoopClientSettings.class);
            INSTANCE = loaded != null ? loaded : new CoopClientSettings();
         } catch (Exception e) {
            System.err.println("[CoopMoves] Client settings corrupted, resetting: " + e.getMessage());
            INSTANCE = new CoopClientSettings();
         }
      } else {
         INSTANCE = new CoopClientSettings();
      }

      save();
   }

   public static void save() {
      if (INSTANCE == null) {
         INSTANCE = new CoopClientSettings();
      }

      try {
         FILE.getParentFile().mkdirs();

         try (FileWriter w = new FileWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
         }
      } catch (Exception e) {
         System.err.println("[CoopMoves] Failed to save client settings: " + e.getMessage());
      }
   }

   public static void register() {
      load();
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, access) -> {
               dispatcher.register(
                  (LiteralArgumentBuilder)ClientCommandManager.literal("dap")
                     .then(
                        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("settings")
                                    .executes(ctx -> open((FabricClientCommandSource)ctx.getSource())))
                                 .then(
                                    ((LiteralArgumentBuilder)ClientCommandManager.literal("shake")
                                          .then(ClientCommandManager.literal("on").executes(c -> setShake((FabricClientCommandSource)c.getSource(), true))))
                                       .then(ClientCommandManager.literal("off").executes(c -> setShake((FabricClientCommandSource)c.getSource(), false)))
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)ClientCommandManager.literal("impactframes")
                                       .then(ClientCommandManager.literal("on").executes(c -> setFrames((FabricClientCommandSource)c.getSource(), true))))
                                    .then(ClientCommandManager.literal("off").executes(c -> setFrames((FabricClientCommandSource)c.getSource(), false)))
                              ))
                           .then(ClientCommandManager.literal("reset").executes(c -> {
                              get().cameraShakeEnabled = true;
                              get().impactFramesEnabled = true;
                              save();
                              msg((FabricClientCommandSource)c.getSource(), "§7Comfort settings reset.");
                              return 1;
                           }))
                     )
               );
               dispatcher.register(
                  (LiteralArgumentBuilder)ClientCommandManager.literal("coopshake")
                     .executes(c -> setShake((FabricClientCommandSource)c.getSource(), !get().cameraShakeEnabled))
               );
               dispatcher.register(
                  (LiteralArgumentBuilder)ClientCommandManager.literal("coopflash")
                     .executes(c -> setFrames((FabricClientCommandSource)c.getSource(), !get().impactFramesEnabled))
               );
            }
         );
   }

   private static int open(FabricClientCommandSource src) {
      Minecraft client = src.getClient();
      client.execute(() -> client.setScreenAndShow(new CoopClientSettings.SettingsScreen(null)));
      return 1;
   }

   private static int setShake(FabricClientCommandSource src, boolean on) {
      get().cameraShakeEnabled = on;
      save();
      msg(src, "§6Screen shake: " + state(on));
      return 1;
   }

   private static int setFrames(FabricClientCommandSource src, boolean on) {
      get().impactFramesEnabled = on;
      save();
      msg(src, "§6Impact frames: " + state(on));
      return 1;
   }

   private static String state(boolean on) {
      return on ? "§aON" : "§cOFF";
   }

   private static void msg(FabricClientCommandSource src, String text) {
      src.sendFeedback(Component.literal(text));
   }

   @Environment(EnvType.CLIENT)
   public static class SettingsScreen extends Screen {
      private final Screen parent;

      public SettingsScreen(Screen parent) {
         super(Component.literal("Coop Moves — Comfort"));
         this.parent = parent;
      }

      protected void init() {
         CoopClientSettings s = CoopClientSettings.get();
         int cx = this.width / 2;
         int y = this.height / 4 + 24;
         this.addRenderableWidget(
            CycleButton.onOffBuilder(s.cameraShakeEnabled).create(cx - 110, y, 220, 20, Component.literal("Screen shake"), (button, value) -> {
               CoopClientSettings.get().cameraShakeEnabled = value;
               CoopClientSettings.save();
            })
         );
         this.addRenderableWidget(
            CycleButton.onOffBuilder(s.impactFramesEnabled)
               .create(cx - 110, y + 28, 220, 20, Component.literal("Impact frames (flashing)"), (button, value) -> {
                  CoopClientSettings.get().impactFramesEnabled = value;
                  CoopClientSettings.save();
               })
         );
         this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            CoopClientSettings.get().cameraShakeEnabled = true;
            CoopClientSettings.get().impactFramesEnabled = true;
            CoopClientSettings.save();
            this.rebuildWidgets();
         }).bounds(cx - 110, y + 64, 108, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(cx + 2, y + 64, 108, 20).build());
      }

      public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
         super.render(ctx, mouseX, mouseY, delta);
         int cx = this.width / 2;
         ctx.centeredText(this.font, this.title, cx, this.height / 4 - 4, -1);
         ctx.centeredText(this.font, Component.literal("§7These apply to your client only"), cx, this.height / 4 + 8, -5592406);
         ctx.centeredText(this.font, Component.literal("§8config/coopmoves_client.json"), cx, this.height / 4 + 104, -7829368);
      }

      public void onClose() {
         CoopClientSettings.save();
         this.minecraft.setScreenAndShow(this.parent);
      }
   }
}
