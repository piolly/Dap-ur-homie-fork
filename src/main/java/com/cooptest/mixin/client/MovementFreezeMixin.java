package com.cooptest.mixin.client;

import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.DapHoldClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(KeyboardInput.class)
public class MovementFreezeMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        boolean shouldFreeze =
                HighFiveClientHandler.isLocalPlayerFrozen()
                        || com.cooptest.client.DivineFlamComboClient.isLocalPlayerInCombo()
                        || ChargedDapClientHandler.isLocalPlayerFireDapFrozen()
                        || ChargedDapClientHandler.isLocalPlayerPerfectDapFrozen()
                        || DapHoldClientHandler.isLocalPlayerFrozen()
                        || com.cooptest.client.HugClientHandler.isLocalPlayerInHug()
                        || ChargedDapClientHandler.isDapBadBlocking()
                        || isInHuddle();

        if (shouldFreeze) {
            ClientInput input = (ClientInput) (Object) this;
            ((InputAccessor) input).setMovementVector(Vec2.ZERO);
        }
    }

    private static boolean isInHuddle() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        return com.cooptest.client.CoopAnimationHandler.isInHuddleAnim(client.player.getUUID());
    }
}