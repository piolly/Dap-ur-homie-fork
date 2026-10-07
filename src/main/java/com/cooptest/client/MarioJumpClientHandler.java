package com.cooptest.client;

import com.cooptest.MarioJumpHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MarioJumpClientHandler {
   private static boolean wasJumpPressed = false;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.level != null) {
            boolean isJumpPressed = client.options.keyJump.isDown();
            if (isJumpPressed && !wasJumpPressed && isOnPlayerHead(client)) {
               ClientPlayNetworking.send(new MarioJumpHandler.MarioJumpRequestPayload());
            }

            wasJumpPressed = isJumpPressed;
         }
      });
   }

   private static boolean isOnPlayerHead(Minecraft client) {
      LocalPlayer player = client.player;
      if (player == null) {
         return false;
      }

      Vec3 playerPos = player.position();
      double playerFeetY = playerPos.y;
      AABB searchBox = new AABB(playerPos.x - 0.8, playerPos.y - 2.5, playerPos.z - 0.8, playerPos.x + 0.8, playerPos.y + 0.5, playerPos.z + 0.8);

      for (Player target : client.level.getEntitiesOfClass(Player.class, searchBox, p -> p != player && p.isAlive())) {
         Vec3 targetPos = target.position();
         double targetHeadY = targetPos.y + target.getEyeHeight() + 0.15;
         double heightDiff = playerFeetY - targetHeadY;
         if (heightDiff >= -0.35 && heightDiff <= 0.5) {
            double horizDist = Math.sqrt(Math.pow(playerPos.x - targetPos.x, 2.0) + Math.pow(playerPos.z - targetPos.z, 2.0));
            if (horizDist <= 0.7) {
               return true;
            }
         }
      }

      return false;
   }
}
