package com.cooptest.client;
import com.cooptest.SlapHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
public class SlapClientHandler {
    private static long slapCooldownEnd = 0;
    public static boolean isOnSlapCooldown() { return System.currentTimeMillis() < slapCooldownEnd; }
    public static void triggerSlapCooldown() { slapCooldownEnd = System.currentTimeMillis() + 500L; }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.CameraFlickPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Minecraft client = context.client();
                    if (client.player == null) return;
                    if (!client.player.getUUID().equals(payload.playerId())) return;
                    float newPitch = Math.min(90f, client.player.getXRot() + payload.pitchDelta());
                    client.player.setXRot(newPitch);
                    triggerSlapCooldown();
                }));
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.CameraYawFlickPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    var client = context.client();
                    if (client.player == null) return;
                    if (!client.player.getUUID().equals(payload.playerId())) return;
                    client.player.setYRot(client.player.getYRot() + payload.yawDelta());
                }));
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.ScreenClosePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    Minecraft client = context.client();
                    if (client.player == null) return;
                    if (!client.player.getUUID().equals(payload.playerId())) return;
                    if (client.screen != null) {
                        client.setScreen(null);
                    }
                }));
    }
}