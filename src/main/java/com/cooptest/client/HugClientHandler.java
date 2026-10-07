package com.cooptest.client;
import com.cooptest.HighFiveHugHandler;
import com.cooptest.ModKeyCategories;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class HugClientHandler {
    private static KeyMapping hugKey;
    private static final Map<UUID, Boolean> inHug = new HashMap<>();
    public static void register() {
        hugKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.coopmoves.hug",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                ModKeyCategories.COOPMOVES
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (hugKey.isDown()) {
                ClientPlayNetworking.send(new HighFiveHugHandler.HugHoldPayload());
            }
        });
    }
    public static boolean isLocalPlayerInHug() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        var animState = CoopAnimationHandler.getAnimState(client.player.getUUID());
        return animState == CoopAnimationHandler.AnimState.HUG_START
                || animState == CoopAnimationHandler.AnimState.HUGGING
                || animState == CoopAnimationHandler.AnimState.HUGGING2;
    }
}