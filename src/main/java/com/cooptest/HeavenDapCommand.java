package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class HeavenDapCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("heavendap").executes(HeavenDapCommand::execute));
   }

   private static int execute(CommandContext<CommandSourceStack> context) {
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player == null) {
         return 0;
      }

      ServerPlayer partner = null;
      double closest = 10.0;

      for (ServerPlayer other : player.level().players()) {
         if (other != player) {
            double d = player.distanceTo(other);
            if (d < closest) {
               closest = d;
               partner = other;
            }
         }
      }

      if (partner == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("§cNo nearby player found (must be within 10 blocks)."));
         return 0;
      } else {
         Vec3 mid = player.position().add(partner.position()).scale(0.5).add(0.0, 1.4, 0.0);
         ServerPlayer finalPartner = partner;
         ChargedDapHandler.startHeavenDap(player, finalPartner, mid, player.level());
         ((CommandSourceStack)context.getSource())
            .sendSuccess(() -> Component.literal("§d§l✨ HEAVEN DAP triggered with " + finalPartner.getName().getString() + "!"), false);
         return 1;
      }
   }
}
