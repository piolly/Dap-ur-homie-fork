package com.cooptest.client;
import com.cooptest.SitHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.lwjgl.glfw.GLFW;
public class SitClientHandler {
    private static boolean wasFHeld = false;
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.screen != null) return;
            boolean fHeld = InputConstants.isKeyDown(
                    net.minecraft.client.Minecraft.getInstance().getWindow(),
                    GLFW.GLFW_KEY_F);
            if (fHeld != wasFHeld) {
                wasFHeld = fHeld;
                ClientPlayNetworking.send(new SitHandler.SitFHoldPayload(fHeld));
            }
        });
    }
}