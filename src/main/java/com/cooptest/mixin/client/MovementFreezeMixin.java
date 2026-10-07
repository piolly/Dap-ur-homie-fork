package com.cooptest.mixin.client;

import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.DapHoldClientHandler;
import com.cooptest.client.DivineFlamComboClient;
import com.cooptest.client.DuoPoseClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.HuddleClientHandler;
import com.cooptest.client.StrongSlapClientHandler;
import com.cooptest.highfive.client.HighFiveShakeClientHandler;
import com.cooptest.highfive.client.ReadyHugClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MovementFreezeMixin {
   @Inject(method = "tick", at = @At("TAIL"))
   private void onTick(CallbackInfo ci) {
      boolean shouldFreeze = HighFiveClientHandler.isLocalPlayerFrozen()
         || DivineFlamComboClient.isLocalPlayerInCombo()
         || ChargedDapClientHandler.isLocalPlayerFireDapFrozen()
         || ChargedDapClientHandler.isLocalPlayerPerfectDapFrozen()
         || DapHoldClientHandler.isLocalPlayerFrozen()
         || ReadyHugClientHandler.isLocalPlayerInHug()
         || ChargedDapClientHandler.isDapBadBlocking()
         || StrongSlapClientHandler.isLocalPlayerFrozen()
         || DuoPoseClientHandler.isLocalPlayerFrozen()
         || isInHuddle()
         || isLocalPlayerCuffed()
         || isInHandshake()
         || isInHandSpin()
         || BrosClientHandler.isEngaged();
      if (shouldFreeze) {
         InputAccessor input = (InputAccessor)this;
         input.coop$setPlayerInput(Input.EMPTY);
         input.coop$setMovementVector(Vec2.ZERO);
      } else {
         if (HuddleClientHandler.isSuppressingSneak()) {
            InputAccessor input = (InputAccessor)this;
            Input cur = input.coop$getPlayerInput();
            if (cur != null && cur.shift()) {
               input.coop$setPlayerInput(new Input(cur.forward(), cur.backward(), cur.left(), cur.right(), cur.jump(), false, cur.sprint()));
            }
         }
      }
   }

   private static boolean isInHandshake() {
      Minecraft client = Minecraft.getInstance();
      return client.player == null ? false : HighFiveShakeClientHandler.isLocalPlayerInHandshake();
   }

   private static boolean isInHandSpin() {
      return HandSpinClientHandler.isLocalPlayerSpinning() || HandSpinClientHandler.isLocalPlayerMonkeFlying();
   }

   private static boolean isInHuddle() {
      Minecraft client = Minecraft.getInstance();
      return client.player == null ? false : CoopAnimationHandler.isInHuddleAnim(client.player.getUUID());
   }

   private static boolean isLocalPlayerCuffed() {
      return CoopAnimationHandler.isLocalPlayerCuffed();
   }
}
