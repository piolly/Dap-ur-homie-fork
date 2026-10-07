package com.cooptest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StrongSlapCommand {
   private static final Set<UUID> armed = new HashSet<>();

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("slap").executes(ctx -> {
         CommandSourceStack source = (CommandSourceStack)ctx.getSource();

         ServerPlayer player;
         try {
            player = source.getPlayerOrException();
         } catch (Exception e) {
            source.sendFailure(Component.literal("Must be run by a player."));
            return 0;
         }

         UUID id = player.getUUID();
         if (armed.contains(id)) {
            armed.remove(id);
            player.displayClientMessage(Component.literal("§7[Strong Slap §cDISARMED§7] — G behaves normally."), false);
         } else {
            armed.add(id);
            player.displayClientMessage(Component.literal("§6[Strong Slap §aARMED§6] — Hold G on a player's back then release to start the QTE."), false);
         }

         return 1;
      }));
   }

   public static boolean isArmed(UUID playerId) {
      return armed.contains(playerId);
   }

   public static void disarm(UUID playerId) {
      armed.remove(playerId);
   }

   public static void clearAll() {
      armed.clear();
   }
}
