package com.cooptest;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;


public class MahitoItems {

    public static Holder<Potion> MAHITO_POTION;

    public static void register() {
        Potion mahitoPotion = new Potion(
                "mahito_stuff", new MobEffectInstance(ModEffects.MAHITO, 1200, 0) // 60 seconds
        );

        MAHITO_POTION = Registry.<Potion, Potion>registerForHolder(
                BuiltInRegistries.POTION,
                Identifier.fromNamespaceAndPath("testcoop", "mahito_stuff"),
                mahitoPotion
        );

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(content -> {
            ItemStack potionStack = new ItemStack(Items.POTION);
            potionStack.set(DataComponents.POTION_CONTENTS,
                    new PotionContents(MAHITO_POTION));
            content.accept(potionStack);
        });
    }


    public static ItemStack createMahitoPotion() {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS,
                new PotionContents(MAHITO_POTION));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.testcoop.mahito_potion"));
        return stack;
    }
}