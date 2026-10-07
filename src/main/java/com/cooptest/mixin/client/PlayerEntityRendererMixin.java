package com.cooptest.mixin.client;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {
    @Unique
    private static final HashMap<UUID, Boolean> matrixPushed = new HashMap<>();
    @Unique
    private static final HashMap<UUID, Float> lockedYaw = new HashMap<>();
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("RETURN"))
    private void rotateGrabbedPlayer(@Coerce Object stateObj, PoseStack matrices, float bodyYaw, float animationProgress, CallbackInfo ci) {
        AvatarRenderState state = (AvatarRenderState) stateObj;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(state.id);
        if (!(entity instanceof Player player)) return;
        UUID uuid = player.getUUID();
        PoseState pose = PoseNetworking.poseStates.getOrDefault(uuid, PoseState.NONE);
        if (pose == PoseState.GRABBED) {
            float facingYaw;
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof Player holder) {
                facingYaw = holder.getYRot();
                lockedYaw.put(player.getUUID(), facingYaw);
            } else {
                if (lockedYaw.containsKey(player.getUUID())) {
                    facingYaw = lockedYaw.get(player.getUUID());
                } else {
                    facingYaw = player.getYRot();
                    lockedYaw.put(player.getUUID(), facingYaw);
                }
            }
            float counterRotation = -bodyYaw + facingYaw;
            matrices.rotate(Axis.YP.rotationDegrees(counterRotation));
            matrices.translate(0, 0.9, 0);
            matrices.rotate(Axis.XP.rotationDegrees(90));
            matrices.translate(0, -0.9, 0);
            matrixPushed.put(player.getUUID(), true);
        } else {
            lockedYaw.remove(player.getUUID());
            matrixPushed.put(player.getUUID(), false);
        }
    }
}