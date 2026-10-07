package com.cooptest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;

public class CuffHandler {
   private static final int CUFF_ANIM_ORDINAL = 98;
   private static final Set<UUID> cuffedPlayers = new HashSet<>();
   private static final Map<UUID, UUID> cuffCarriers = new HashMap<>();
   private static final Map<UUID, UUID> cuffCarrying = new HashMap<>();

   public static void register() {
      registerCommand();
      registerInteraction();
      registerBlockClickDrop();
      registerBlockBreakBlock();
      registerBlockPlaceBlock();
      registerAttackBlock();
   }

   private static void registerCommand() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("cuff")
                           .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                        .then(Commands.literal("all").executes(ctx -> {
                           List<ServerPlayer> players = ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers();
                           ServerPlayer sender = null;

                           try {
                              sender = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                           } catch (Exception var7) {
                           }

                           ServerPlayer finalSender = sender;
                           int count = 0;

                           for (ServerPlayer p : players) {
                              if ((finalSender == null || !p.getUUID().equals(finalSender.getUUID())) && !isCuffed(p.getUUID())) {
                                 cuff(p);
                                 count++;
                              }
                           }

                           int finalCount = count;
                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("§c[Cuff] Cuffed " + finalCount + " player(s)."), false);
                           return finalCount;
                        })))
                     .then(Commands.literal("uncuffall").executes(ctx -> {
                        List<ServerPlayer> players = ((CommandSourceStack)ctx.getSource()).getServer().getPlayerList().getPlayers();
                        int count = 0;

                        for (ServerPlayer p : new ArrayList<>(players)) {
                           if (isCuffed(p.getUUID())) {
                              uncuff(p);
                              count++;
                           }
                        }

                        int finalCount = count;
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("§a[Cuff] Freed " + finalCount + " player(s)."), false);
                        return finalCount;
                     })))
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

                              if (isCuffed(target.getUUID())) {
                                 uncuff(target);
                                 ((CommandSourceStack)ctx.getSource())
                                    .sendSuccess(() -> Component.literal("§a[Cuff] " + target.getName().getString() + " has been freed."), false);
                              } else {
                                 cuff(target);
                                 ((CommandSourceStack)ctx.getSource())
                                    .sendSuccess(() -> Component.literal("§c[Cuff] " + target.getName().getString() + " has been cuffed."), false);
                              }

                              return 1;
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
         }

         if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer clicker) {
            if (!(entity instanceof ServerPlayer target)) {
               return InteractionResult.PASS;
            } else {
               UUID clickerId = clicker.getUUID();
               UUID targetId = target.getUUID();
               if (cuffedPlayers.contains(targetId)) {
                  ItemStack held = clicker.getItemInHand(hand);
                  FoodProperties food = held.isEmpty() ? null : (FoodProperties)held.get(DataComponents.FOOD);
                  if (food != null) {
                     target.getFoodData().eat(food);
                     if (!clicker.isCreative()) {
                        held.shrink(1);
                     }

                     clicker.sendOverlayMessage(Component.literal("§a[Cuff] Fed " + target.getName().getString() + "."));
                     target.sendOverlayMessage(Component.literal("§aYou were fed."));
                     return InteractionResult.SUCCESS;
                  }

                  if (!held.isEmpty() && held.getItem() instanceof PotionItem) {
                     PotionContents contents = (PotionContents)held.get(DataComponents.POTION_CONTENTS);
                     if (contents != null) {
                        for (MobEffectInstance effect : contents.getAllEffects()) {
                           target.addEffect(new MobEffectInstance(effect));
                        }

                        if (!clicker.isCreative()) {
                           held.shrink(1);
                           ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                           if (!clicker.getInventory().add(bottle)) {
                              clicker.spawnAtLocation(clicker.level(), bottle);
                           }
                        }

                        clicker.sendOverlayMessage(Component.literal("§a[Cuff] Applied potion to " + target.getName().getString() + "."));
                        target.sendOverlayMessage(Component.literal("§aA potion was applied to you."));
                        return InteractionResult.SUCCESS;
                     }
                  }
               }

               if (!clicker.isShiftKeyDown()) {
                  return InteractionResult.PASS;
               }

               if (cuffCarrying.containsKey(clickerId)) {
                  dropCuffed(clicker);
                  return InteractionResult.SUCCESS;
               }

               if (cuffedPlayers.contains(targetId) && !cuffCarriers.containsKey(targetId)) {
                  ItemStack held = clicker.getItemInHand(hand);
                  if (!held.isEmpty()) {
                     clicker.sendOverlayMessage(Component.literal("§cEmpty your hand to carry them."));
                     return InteractionResult.PASS;
                  } else {
                     pickupCuffed(clicker, target);
                     return InteractionResult.SUCCESS;
                  }
               } else {
                  return InteractionResult.PASS;
               }
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void registerBlockClickDrop() {
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer carrier) {
            if (!carrier.isShiftKeyDown()) {
               return InteractionResult.PASS;
            }

            if (!cuffCarrying.containsKey(carrier.getUUID())) {
               return InteractionResult.PASS;
            }

            dropCuffed(carrier);
            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void registerBlockBreakBlock() {
      PlayerBlockBreakEvents.BEFORE.register((Before)(world, player, pos, state, blockEntity) -> {
         if (world.isClientSide()) {
            return true;
         }

         if (player instanceof ServerPlayer sp) {
            if (!cuffedPlayers.contains(sp.getUUID())) {
               return true;
            }

            sp.sendOverlayMessage(Component.literal("§c⛓ You can't break blocks while cuffed."));
            return false;
         } else {
            return true;
         }
      });
   }

   private static void registerBlockPlaceBlock() {
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer sp) {
            if (!cuffedPlayers.contains(sp.getUUID())) {
               return InteractionResult.PASS;
            } else {
               ItemStack held = player.getItemInHand(hand);
               if (!held.isEmpty() && held.getItem() instanceof BlockItem) {
                  sp.sendOverlayMessage(Component.literal("§c⛓ You can't place blocks while cuffed."));
                  return InteractionResult.FAIL;
               } else {
                  return InteractionResult.PASS;
               }
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void registerAttackBlock() {
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer sp) {
            if (!cuffedPlayers.contains(sp.getUUID())) {
               return InteractionResult.PASS;
            }

            sp.sendOverlayMessage(Component.literal("§c⛓ You can't attack while cuffed."));
            return InteractionResult.FAIL;
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void cuff(ServerPlayer target) {
      cuffedPlayers.add(target.getUUID());
      PoseNetworking.broadcastAnimState(target, 98);
      target.sendSystemMessage(Component.literal("§c⛓ You have been cuffed. You cannot move."));
   }

   private static void uncuff(ServerPlayer target) {
      UUID targetId = target.getUUID();
      if (cuffCarriers.containsKey(targetId)) {
         UUID carrierId = cuffCarriers.remove(targetId);
         cuffCarrying.remove(carrierId);
         target.stopRiding();
         if (target.level().getServer() != null) {
            ServerPlayer carrier = target.level().getServer().getPlayerList().getPlayer(carrierId);
            if (carrier != null) {
               broadcastPassengers(carrier);
            }
         }
      }

      cuffedPlayers.remove(targetId);
      PoseNetworking.broadcastAnimState(target, 0);
      target.sendSystemMessage(Component.literal("§a⛓ You have been uncuffed. You are free!"));
   }

   private static void pickupCuffed(ServerPlayer carrier, ServerPlayer cuffed) {
      if (cuffCarrying.containsKey(carrier.getUUID())) {
         carrier.sendOverlayMessage(Component.literal("§cYou're already carrying someone."));
      } else {
         boolean success = cuffed.startRiding(carrier, true, true);
         if (!success) {
            carrier.sendOverlayMessage(Component.literal("§cCouldn't pick them up — try standing closer."));
         } else {
            cuffCarriers.put(cuffed.getUUID(), carrier.getUUID());
            cuffCarrying.put(carrier.getUUID(), cuffed.getUUID());
            broadcastPassengers(carrier);
            carrier.sendOverlayMessage(Component.literal("§c[Cuff] Carrying. Shift+right-click to drop."));
            cuffed.sendOverlayMessage(Component.literal("§cYou are being carried!"));
         }
      }
   }

   private static void dropCuffed(ServerPlayer carrier) {
      UUID cuffedId = cuffCarrying.remove(carrier.getUUID());
      if (cuffedId != null) {
         cuffCarriers.remove(cuffedId);
         if (carrier.level().getServer() != null) {
            ServerPlayer cuffed = carrier.level().getServer().getPlayerList().getPlayer(cuffedId);
            if (cuffed != null) {
               cuffed.stopRiding();
               broadcastPassengers(carrier);
               cuffed.sendOverlayMessage(Component.literal("§cYou were dropped."));
            }
         }

         carrier.sendOverlayMessage(Component.literal("§a[Cuff] Dropped."));
      }
   }

   private static void broadcastPassengers(ServerPlayer vehicle) {
      if (vehicle.level().getServer() != null) {
         ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(vehicle);

         for (ServerPlayer p : vehicle.level().getServer().getPlayerList().getPlayers()) {
            p.connection.send(packet);
         }
      }
   }

   public static void onPlayerLeave(UUID playerId) {
      if (cuffedPlayers.remove(playerId)) {
         UUID carrierId = cuffCarriers.remove(playerId);
         if (carrierId != null) {
            cuffCarrying.remove(carrierId);
         }
      }

      UUID cuffedId = cuffCarrying.remove(playerId);
      if (cuffedId != null) {
         cuffCarriers.remove(cuffedId);
      }
   }

   public static boolean isCuffed(UUID playerId) {
      return cuffedPlayers.contains(playerId);
   }

   public static boolean isBeingCarried(UUID cuffedPlayerId) {
      return cuffCarriers.containsKey(cuffedPlayerId);
   }

   public static boolean isCarryingCuffed(UUID carrierId) {
      return cuffCarrying.containsKey(carrierId);
   }
}
