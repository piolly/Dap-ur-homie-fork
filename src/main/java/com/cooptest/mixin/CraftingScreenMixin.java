package com.cooptest.mixin;

import com.cooptest.MahitoCraftingHandler;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(CraftingMenu.class)
public class CraftingScreenMixin {

    @Final private CraftingContainer input;
    @Final private ResultContainer result;

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void onCraftingChanged(net.minecraft.world.Container inventory, CallbackInfo ci) {
        if (MahitoCraftingHandler.isValidMahitoRecipe(input)) {
            result.setItem(0, MahitoCraftingHandler.createResult());
            ci.cancel();
        }
    }
}