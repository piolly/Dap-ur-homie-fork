package com.cooptest.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface InputAccessor {
   @Accessor("keyPresses")
   void coop$setPlayerInput(Input var1);

   @Accessor("keyPresses")
   Input coop$getPlayerInput();

   @Accessor("moveVector")
   void coop$setMovementVector(Vec2 var1);
}
