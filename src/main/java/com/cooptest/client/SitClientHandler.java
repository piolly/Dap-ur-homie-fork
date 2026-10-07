package com.cooptest.client;
import com.cooptest.SitHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
public class SitClientHandler {
    private static boolean wasFHeld = false;
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.gui.screen() != null) return;
            boolean fHeld = InputConstants.isKeyDown(InputConstants.KEY_F);
            if (fHeld != wasFHeld) {
                wasFHeld = fHeld;
                ClientPlayNetworking.send(new SitHandler.SitFHoldPayload(fHeld));
            }
        });
    }
}