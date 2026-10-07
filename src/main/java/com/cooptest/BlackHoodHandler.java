package com.cooptest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class BlackHoodHandler {
   private static final Map<UUID, UUID> hoodedPlayers = new HashMap<>();

   public static void register() {
      registerInteraction();
      registerTickEnforcement();
      registerJoinSync();
      registerDeathListener();
      registerCommand();
   }

   private static void registerDeathListener() {
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
         if (entity instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            if (hoodedPlayers.containsKey(playerId)) {
               hoodedPlayers.remove(playerId);
               if (player.level().getServer() != null) {
                  BlackHoodNetworking.broadcastHoodState(player.level().getServer(), playerId, false);
               }
            }
         }
      });
   }

   private static void registerCommand() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("unhood").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                  .then(
                     Commands.argument("player", EntityArgument.player())
                        .executes(
                           ctx -> {
                              ServerPlayer target;
                              try {
                                 target = EntityArgument.getPlayer(ctx, "player");
                              } catch (Exception e) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Player not found."));
                                 return 0;
                              }

                              if (!hoodedPlayers.containsKey(target.getUUID())) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal(target.getName().getString() + " is not hooded."));
                                 return 0;
                              } else {
                                 removeHoodByCommand(((CommandSourceStack)ctx.getSource()).getServer(), target);
                                 ((CommandSourceStack)ctx.getSource())
                                    .sendSuccess(() -> Component.literal("§a[Hood] Removed from " + target.getName().getString() + "."), false);
                                 return 1;
                              }
                           }
                        )
                  )
            )
         );
   }

   private static void registerInteraction() {
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         } else if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
         } else if (!(player instanceof ServerPlayer clicker)) {
            return InteractionResult.PASS;
         } else if (entity instanceof ServerPlayer target) {
            if (clicker.getUUID().equals(target.getUUID())) {
               return InteractionResult.PASS;
            }

            UUID targetId = target.getUUID();
            UUID clickerId = clicker.getUUID();
            if (clicker.isShiftKeyDown() && hoodedPlayers.containsKey(targetId)) {
               boolean isOriginalHooder = clickerId.equals(hoodedPlayers.get(targetId));
               boolean isOp = Commands.LEVEL_GAMEMASTERS.check(clicker.permissions());
               if (isOriginalHooder || isOp) {
                  removeHood(clicker.level().getServer(), target, clicker);
                  return InteractionResult.SUCCESS;
               }
            }

            if (!clicker.isShiftKeyDown()) {
               ItemStack heldItem = clicker.getItemInHand(hand);
               if (!heldItem.isEmpty() && heldItem.is(MahitoItems.BLACK_HOOD) && !hoodedPlayers.containsKey(targetId)) {
                  equipHood(clicker, target, hand);
                  return InteractionResult.SUCCESS;
               }
            }

            return InteractionResult.PASS;
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void equipHood(ServerPlayer equiper, ServerPlayer target, InteractionHand hand) {
      ItemStack existing = target.getItemBySlot(EquipmentSlot.HEAD);
      if (!existing.isEmpty()) {
         target.spawnAtLocation(target.level(), existing.copy());
         target.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      }

      target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MahitoItems.BLACK_HOOD));
      target.getInventory().setChanged();
      target.level()
         .players()
         .forEach(
            p -> p.connection
               .send(new ClientboundSetEquipmentPacket(target.getId(), List.of(new Pair(EquipmentSlot.HEAD, new ItemStack(MahitoItems.BLACK_HOOD)))))
         );
      if (!equiper.isCreative()) {
         equiper.getItemInHand(hand).shrink(1);
      }

      hoodedPlayers.put(target.getUUID(), equiper.getUUID());
      MinecraftServer server = equiper.level().getServer();
      if (server != null) {
         BlackHoodNetworking.broadcastHoodState(server, target.getUUID(), true);
      }

      equiper.sendOverlayMessage(Component.literal("§c[Hood] ").append(target.getName()).append(Component.literal(" can't see a thing.")));
      target.sendSystemMessage(Component.literal("§8[Hood] §cSomething was just put on your head..."));
   }

   private static void removeHoodByCommand(MinecraftServer server, ServerPlayer target) {
      UUID targetId = target.getUUID();
      hoodedPlayers.remove(targetId);
      ItemStack headSlot = target.getItemBySlot(EquipmentSlot.HEAD);
      if (!headSlot.isEmpty() && headSlot.is(MahitoItems.BLACK_HOOD)) {
         target.spawnAtLocation(target.level(), headSlot.copy());
         target.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
         target.getInventory().setChanged();
      }

      target.level()
         .players()
         .forEach(p -> p.connection.send(new ClientboundSetEquipmentPacket(target.getId(), List.of(new Pair(EquipmentSlot.HEAD, ItemStack.EMPTY)))));
      if (server != null) {
         BlackHoodNetworking.broadcastHoodState(server, targetId, false);
      }

      target.sendSystemMessage(Component.literal("§a[Hood] §fYour hood was removed by an admin."));
   }

   private static void removeHood(MinecraftServer server, ServerPlayer target, ServerPlayer remover) {
      UUID targetId = target.getUUID();
      hoodedPlayers.remove(targetId);
      ItemStack headSlot = target.getItemBySlot(EquipmentSlot.HEAD);
      if (!headSlot.isEmpty() && headSlot.is(MahitoItems.BLACK_HOOD)) {
         target.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
         target.getInventory().setChanged();
         ItemStack returnStack = new ItemStack(MahitoItems.BLACK_HOOD);
         if (!remover.getInventory().add(returnStack)) {
            remover.spawnAtLocation(remover.level(), returnStack);
         }
      }

      target.level()
         .players()
         .forEach(p -> p.connection.send(new ClientboundSetEquipmentPacket(target.getId(), List.of(new Pair(EquipmentSlot.HEAD, ItemStack.EMPTY)))));
      if (server != null) {
         BlackHoodNetworking.broadcastHoodState(server, targetId, false);
      }

      remover.sendOverlayMessage(Component.literal("§a[Hood] Removed from ").append(target.getName()).append(Component.literal(".")));
      target.sendSystemMessage(Component.literal("§a[Hood] §fThe hood has been removed. You can see again!"));
   }

   private static void registerTickEnforcement() {
      ServerTickEvents.END_SERVER_TICK
         .register(
            (EndTick)server -> {
               if (!hoodedPlayers.isEmpty()) {
                  for (UUID targetId : hoodedPlayers.keySet()) {
                     ServerPlayer player = server.getPlayerList().getPlayer(targetId);
                     if (player != null) {
                        ItemStack headSlot = player.getItemBySlot(EquipmentSlot.HEAD);
                        boolean hoodPresent = !headSlot.isEmpty() && headSlot.is(MahitoItems.BLACK_HOOD);
                        if (!hoodPresent) {
                           if (!headSlot.isEmpty()) {
                              player.spawnAtLocation(player.level(), headSlot.copy());
                           }

                           player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MahitoItems.BLACK_HOOD));
                           player.getInventory().setChanged();
                           player.level()
                              .players()
                              .forEach(
                                 p -> p.connection
                                    .send(
                                       new ClientboundSetEquipmentPacket(
                                          player.getId(), List.of(new Pair(EquipmentSlot.HEAD, new ItemStack(MahitoItems.BLACK_HOOD)))
                                       )
                                    )
                              );
                        }

                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                           ItemStack slot = player.getInventory().getItem(i);
                           if (!slot.isEmpty() && slot.is(MahitoItems.BLACK_HOOD)) {
                              player.getInventory().removeItemNoUpdate(i);
                           }
                        }
                     }
                  }
               }
            }
         );
   }

   private static void registerJoinSync() {
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         ServerPlayer newPlayer = handler.getPlayer();

         for (UUID hoodedId : hoodedPlayers.keySet()) {
            ServerPlayNetworking.send(newPlayer, new BlackHoodNetworking.HoodStatePayload(hoodedId, true));
         }
      });
   }

   public static void onPlayerLeave(UUID playerId) {
   }

   public static boolean isHooded(UUID playerId) {
      return hoodedPlayers.containsKey(playerId);
   }
}
