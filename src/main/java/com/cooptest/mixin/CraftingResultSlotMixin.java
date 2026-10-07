package com.cooptest.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ResultSlot.class)
public class CraftingResultSlotMixin {

    @Shadow @Final private CraftingContainer craftSlots;

    @Unique
    private boolean isMahitoPotion(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents != null && contents.potion().isPresent()) {
            String potionId = contents.potion().get().getRegisteredName();
            return potionId.contains("mahito");
        }
        return false;
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeMahitoPotion(Player player, ItemStack stack, CallbackInfo ci) {
        if (isMahitoPotion(stack)) {
            // Clear ALL slots in the crafting grid to prevent duplication
            for (int i = 0; i < craftSlots.getContainerSize(); i++) {
                craftSlots.setItem(i, ItemStack.EMPTY);
            }
            craftSlots.setChanged();
        }
    }
}