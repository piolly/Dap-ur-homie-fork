package com.cooptest.mixin;

import com.cooptest.MahitoCraftingHandler;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public class CraftingScreenMixin {
   @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
   private void onCraftingChanged(Container inventory, CallbackInfo ci) {
      CraftingInventoriesAccessor acc = (CraftingInventoriesAccessor)this;
      CraftingContainer input = acc.coop$getCraftingInventory();
      ResultContainer result = acc.coop$getCraftingResultInventory();
      if (MahitoCraftingHandler.isValidMahitoRecipe(input)) {
         result.setItem(0, MahitoCraftingHandler.createResult());
         ci.cancel();
      }
   }
}
