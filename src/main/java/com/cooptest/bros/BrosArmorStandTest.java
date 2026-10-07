package com.cooptest.bros;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class BrosArmorStandTest {
   public static final BrosHandler.Input DUMMY_INPUT = new BrosHandler.Input();
   private static final List<ArmorStand> DUMMIES = new ArrayList<>();
   private static final Map<ArmorStand, UUID> OWNERS = new HashMap<>();

   private BrosArmorStandTest() {
   }

   public static List<ArmorStand> dummies() {
      DUMMIES.removeIf(s -> {
         boolean dead = s.isRemoved() || !s.isAlive();
         if (dead) {
            OWNERS.remove(s);
         }

         return dead;
      });
      return DUMMIES;
   }

   static void register() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                 "testbros"
                              )
                              .executes(ctx -> spawn((CommandSourceStack)ctx.getSource())))
                           .then(
                              Commands.literal("input")
                                 .then(
                                    Commands.argument("forward", FloatArgumentType.floatArg(-1.0F, 1.0F))
                                       .then(Commands.argument("right", FloatArgumentType.floatArg(-1.0F, 1.0F)).executes(ctx -> {
                                          DUMMY_INPUT.forward = FloatArgumentType.getFloat(ctx, "forward");
                                          DUMMY_INPUT.right = FloatArgumentType.getFloat(ctx, "right");
                                          return say(
                                             (CommandSourceStack)ctx.getSource(), "Bro input: fwd " + DUMMY_INPUT.forward + ", right " + DUMMY_INPUT.right
                                          );
                                       }))
                                 )
                           ))
                        .then(Commands.literal("turn").then(Commands.argument("deg", FloatArgumentType.floatArg(-20.0F, 20.0F)).executes(ctx -> {
                           DUMMY_INPUT.turn = FloatArgumentType.getFloat(ctx, "deg");
                           return say((CommandSourceStack)ctx.getSource(), "Bro turn vote: " + DUMMY_INPUT.turn + " deg/tick");
                        }))))
                     .then(Commands.literal("sprint").then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                        DUMMY_INPUT.sprint = BoolArgumentType.getBool(ctx, "on");
                        return say((CommandSourceStack)ctx.getSource(), "Bro sprint: " + DUMMY_INPUT.sprint);
                     }))))
                  .then(Commands.literal("clear").executes(ctx -> {
                     clear();
                     return say((CommandSourceStack)ctx.getSource(), "Bro removed.");
                  }))
            )
         );
   }

   private static int spawn(CommandSourceStack src) {
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         return 0;
      }

      clear();
      resetInput();
      float yaw = player.getYRot();
      double r = Math.toRadians(yaw);
      double rightX = -Math.cos(r);
      double rightZ = -Math.sin(r);
      ServerLevel world = player.level();
      ArmorStand stand = new ArmorStand(world, player.getX() + rightX * 1.5, player.getY(), player.getZ() + rightZ * 1.5);
      stand.setYRot(yaw);
      stand.setYBodyRot(yaw);
      stand.setYHeadRot(yaw);
      stand.setNoGravity(true);
      stand.setCustomName(Component.literal("Bro"));
      stand.setCustomNameVisible(true);
      world.addFreshEntity(stand);
      DUMMIES.add(stand);
      OWNERS.put(stand, player.getUUID());
      return say(src, "Bro spawned on your right (always armed). Hold G, then H.");
   }

   private static void clear() {
      for (ArmorStand s : DUMMIES) {
         try {
            if (!s.isRemoved()) {
               s.discard();
            }
         } catch (Throwable var3) {
         }
      }

      DUMMIES.clear();
      OWNERS.clear();
   }

   static void onOwnerLeft(UUID owner) {
      for (ArmorStand s : new ArrayList<>(DUMMIES)) {
         if (owner.equals(OWNERS.get(s))) {
            try {
               if (!s.isRemoved()) {
                  s.discard();
               }
            } catch (Throwable var4) {
            }

            DUMMIES.remove(s);
            OWNERS.remove(s);
         }
      }
   }

   static void clearAll() {
      clear();
      resetInput();
   }

   private static void resetInput() {
      DUMMY_INPUT.forward = 0.0F;
      DUMMY_INPUT.right = 0.0F;
      DUMMY_INPUT.turn = 0.0F;
      DUMMY_INPUT.sprint = false;
   }

   private static int say(CommandSourceStack src, String msg) {
      src.sendSuccess(() -> Component.literal(msg), false);
      return 1;
   }
}
