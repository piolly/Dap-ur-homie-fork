package com.cooptest;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public class MahitoCraftingHandler {
   public static void register() {
   }

   public static boolean isValidMahitoRecipe(CraftingContainer inventory) {
      int ghastTearCount = 0;
      int rottenFleshCount = 0;
      int waterBottleCount = 0;
      int otherItems = 0;

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty()) {
            if (stack.is(Items.GHAST_TEAR)) {
               ghastTearCount += stack.getCount();
            } else if (stack.is(Items.ROTTEN_FLESH)) {
               rottenFleshCount += stack.getCount();
            } else if (stack.is(Items.POTION)) {
               PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
               if (contents != null && contents.potion().isPresent()) {
                  String potionId = ((Holder)contents.potion().get()).getRegisteredName();
                  if (potionId.contains("water")) {
                     waterBottleCount += stack.getCount();
                  } else {
                     otherItems++;
                  }
               } else {
                  waterBottleCount += stack.getCount();
               }
            } else {
               otherItems++;
            }
         }
      }

      return ghastTearCount >= 1 && rottenFleshCount >= 64 && waterBottleCount >= 1 && otherItems == 0;
   }

   public static ItemStack createResult() {
      return MahitoItems.createMahitoPotion();
   }

   public static void consumeIngredients(CraftingContainer inventory) {
      int fleshToConsume = 64;
      boolean ghastTearConsumed = false;
      boolean waterBottleConsumed = false;

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty()) {
            if (stack.is(Items.GHAST_TEAR) && !ghastTearConsumed) {
               stack.shrink(1);
               ghastTearConsumed = true;
            } else if (stack.is(Items.ROTTEN_FLESH) && fleshToConsume > 0) {
               int toRemove = Math.min(stack.getCount(), fleshToConsume);
               stack.shrink(toRemove);
               fleshToConsume -= toRemove;
            } else if (stack.is(Items.POTION) && !waterBottleConsumed) {
               stack.shrink(1);
               waterBottleConsumed = true;
            }
         }
      }
   }
}
