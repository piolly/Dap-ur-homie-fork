package com.cooptest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class BonkConfig {
   private static final Path CONFIG_PATH = Paths.get("config", "cooptest.properties");

   public static void load() {
      Properties props = new Properties();
      if (Files.exists(CONFIG_PATH)) {
         try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
         } catch (IOException e) {
            System.err.println("[cooptest] Failed to load config: " + e.getMessage());
         }
      } else {
         props.setProperty("bonk_enabled", "true");
         save(props);
      }

      BonkHandler.ENABLED = Boolean.parseBoolean(props.getProperty("bonk_enabled", "true"));
      System.out.println("[cooptest] bonk_enabled=" + BonkHandler.ENABLED);
   }

   private static void save(Properties props) {
      try {
         Files.createDirectories(CONFIG_PATH.getParent());

         try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            props.store(out, "Coop Moves Config — set bonk_enabled=false to disable the bonk mechanic");
         }
      } catch (IOException e) {
         System.err.println("[cooptest] Failed to save config: " + e.getMessage());
      }
   }
}
