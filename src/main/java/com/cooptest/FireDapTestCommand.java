package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class FireDapTestCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("testfiredap").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
            .executes(FireDapTestCommand::execute)
      );
   }

   private static int execute(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();

      try {
         ServerPlayer player = source.getPlayerOrException();
         Vec3 pos = player.position();
         Vec3 fakePartnerPos = pos.add(1.0, 0.0, 0.0);
         player.sendSystemMessage(Component.literal("§a[TEST] Starting Fire Dap test..."));
         player.sendSystemMessage(Component.literal("§a[TEST] You are P1, fake partner is P2"));
         player.sendSystemMessage(Component.literal("§a[TEST] Press J when window opens!"));
         ChargedDapHandler.startFireDap(player, player, pos);
         player.sendSystemMessage(Component.literal("§a[TEST] Fire dap started! Check console logs!"));
         return 1;
      } catch (Exception e) {
         source.sendFailure(Component.literal("§cError: " + e.getMessage()));
         e.printStackTrace();
         return 0;
      }
   }
}
