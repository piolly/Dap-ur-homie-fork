package com.cooptest.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public class CraftingResultSlotMixin {
   @Unique
   private boolean isMahitoPotion(ItemStack stack) {
      if (!stack.is(Items.POTION)) {
         return false;
      } else {
         PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
         if (contents != null && contents.potion().isPresent()) {
            String potionId = ((Holder)contents.potion().get()).getRegisteredName();
            return potionId.contains("mahito");
         } else {
            return false;
         }
      }
   }

   @Inject(method = "onTake", at = @At("HEAD"))
   private void onTakeMahitoPotion(Player player, ItemStack stack, CallbackInfo ci) {
      if (this.isMahitoPotion(stack)) {
         CraftingContainer input = ((CraftingResultSlotInputAccessor)(Object)this).coop$getInput();

         for (int i = 0; i < input.getContainerSize(); i++) {
            input.setItem(i, ItemStack.EMPTY);
         }

         input.setChanged();
      }
   }
}
