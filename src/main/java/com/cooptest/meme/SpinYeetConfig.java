package com.cooptest.meme;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public class SpinYeetConfig {
   public static boolean ENABLED = true;
   private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("coopmoves_spinyeet.properties").toFile();

   public static void load() {
      Properties props = new Properties();
      if (CONFIG_FILE.exists()) {
         try (InputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
            ENABLED = Boolean.parseBoolean(props.getProperty("enabled", "false"));
         } catch (IOException e) {
            e.printStackTrace();
         }
      } else {
         save();
      }
   }

   public static void save() {
      Properties props = new Properties();
      props.setProperty("enabled", String.valueOf(ENABLED));

      try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
         props.store(out, "CoopMoves - Spin Yeet Config\n# Set enabled=true to allow the M-key spin yeet move");
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}
