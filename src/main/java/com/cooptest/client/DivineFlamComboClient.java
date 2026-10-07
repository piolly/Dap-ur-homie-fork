package com.cooptest.client;

import com.cooptest.DivineFlamCombo;
import com.cooptest.ModKeyCategories;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;


public class DivineFlamComboClient {

    private static KeyMapping divineFlameKey;
    private static long divineFlameEndTime = 0;

    public static void register() {
        divineFlameKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.cooptest.divine_flame",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                ModKeyCategories.COOPMOVES
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (divineFlameKey.consumeClick()) {
                ClientPlayNetworking.send(new DivineFlamCombo.DivineJPressPayload());
                divineFlameEndTime = System.currentTimeMillis() + 3000;
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(
                DivineFlamCombo.DivineStartPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        divineFlameEndTime = System.currentTimeMillis() + 1460;
                    });
                }
        );

    }


    public static boolean isLocalPlayerInCombo() {
        return System.currentTimeMillis() < divineFlameEndTime;
    }

    public static boolean onHighFivePress() {
        return false;
    }
}