package com.cooptest.client;
import com.cooptest.GroundPoundHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class GroundPoundClientHandler {
    private static boolean localDiving = false;
    private static long    diveStartMs = 0L;
    private static final Map<UUID, Boolean> divingPlayers = new HashMap<>();
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GroundPoundHandler.GroundPoundSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    UUID id = payload.playerId();
                    boolean diving = payload.diving();
                    divingPlayers.put(id, diving);
                    Minecraft client = context.client();
                    if (client.player == null) return;
                    boolean isLocal = client.player.getUUID().equals(id);
                    if (isLocal) {
                        localDiving = diving;
                        if (diving) {
                            diveStartMs = System.currentTimeMillis();
                            FirstPersonAnimationTest.stop();
                        } else {
                            diveStartMs = 0L;
                        }
                    }
                }));
        HudRenderCallback.EVENT.register(GroundPoundClientHandler::renderHUD);
    }
    private static void renderHUD(GuiGraphics context, DeltaTracker tc) {
        if (!localDiving) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        int sw = context.guiWidth();
        int sh = context.guiHeight();
        long elapsed = System.currentTimeMillis() - diveStartMs;
        int a = Math.min(220, (int)(elapsed / 8)) << 24;
        String label = "⬇ GROUND POUND";
        int lx = (sw - client.font.width(label)) / 2;
        context.drawString(client.font, Component.literal("§c§l" + label), lx, sh / 2 - 30, a | 0xFFFFFF, true);
    }
    public static boolean isLocalPlayerDiving()    { return localDiving; }
    public static boolean isPlayerDiving(UUID id)  { return divingPlayers.getOrDefault(id, false); }
    public static void cleanup(UUID id) {
        divingPlayers.remove(id);
        Minecraft c = Minecraft.getInstance();
        if (c.player != null && c.player.getUUID().equals(id)) {
            localDiving = false;
            diveStartMs = 0L;
        }
    }
}