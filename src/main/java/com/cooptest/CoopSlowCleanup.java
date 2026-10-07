package com.cooptest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class CoopSlowCleanup {
   private static final Identifier[] SLOW_IDS = new Identifier[]{
      Identifier.fromNamespaceAndPath("testcoop", "sike_slow"),
      Identifier.fromNamespaceAndPath("testcoop", "highfive_fast_slow"),
      Identifier.fromNamespaceAndPath("testcoop", "highfive_pass_slow"),
      Identifier.fromNamespaceAndPath("testcoop", "kick_slow")
   };

   private CoopSlowCleanup() {
   }

   public static void register() {
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> clear(handler.getPlayer()));
   }

   public static void clear(ServerPlayer player) {
      if (player != null) {
         AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
         if (attr != null) {
            for (Identifier id : SLOW_IDS) {
               try {
                  attr.removeModifier(id);
               } catch (Throwable var7) {
               }
            }
         }
      }
   }
}
