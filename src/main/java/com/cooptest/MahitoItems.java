package com.cooptest;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyEntries;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

public class MahitoItems {
   public static Holder<Potion> MAHITO_POTION;
   public static Holder<Potion> TODO_POTION;
   private static final ResourceKey<Item> BLACK_HOOD_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("testcoop", "black_hood"));
   public static final Item BLACK_HOOD = (Item)Registry.register(
      BuiltInRegistries.ITEM, BLACK_HOOD_KEY, new Item(new Properties().stacksTo(1).setId(BLACK_HOOD_KEY))
   );

   public static void register() {
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register((ModifyEntries)entries -> entries.accept(BLACK_HOOD));
      Potion mahitoPotion = new Potion("mahito_stuff", new MobEffectInstance[]{new MobEffectInstance(ModEffects.MAHITO, 1200, 0)});
      MAHITO_POTION = Registry.registerForHolder(BuiltInRegistries.POTION, Identifier.fromNamespaceAndPath("testcoop", "mahito_stuff"), mahitoPotion);
      Potion todoPotion = new Potion("todo_potion", new MobEffectInstance[]{new MobEffectInstance(ModEffects.TODO, 2400, 0)});
      TODO_POTION = Registry.registerForHolder(BuiltInRegistries.POTION, Identifier.fromNamespaceAndPath("testcoop", "todo_potion"), todoPotion);
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register((ModifyEntries)content -> {
         ItemStack mahitoStack = new ItemStack(Items.POTION);
         mahitoStack.set(DataComponents.POTION_CONTENTS, new PotionContents(MAHITO_POTION));
         content.accept(mahitoStack);
         ItemStack todoStack = new ItemStack(Items.POTION);
         todoStack.set(DataComponents.POTION_CONTENTS, new PotionContents(TODO_POTION));
         content.accept(todoStack);
      });
   }

   public static ItemStack createMahitoPotion() {
      ItemStack stack = new ItemStack(Items.POTION);
      stack.set(DataComponents.POTION_CONTENTS, new PotionContents(MAHITO_POTION));
      return stack;
   }

   public static ItemStack createTodoPotion() {
      ItemStack stack = new ItemStack(Items.POTION);
      stack.set(DataComponents.POTION_CONTENTS, new PotionContents(TODO_POTION));
      return stack;
   }
}
