package com.cooptest.client;
import com.cooptest.PushInteractionHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class PushClientHandler {
    private static final Map<UUID, Long> pushAnimStart = new HashMap<>();
    private static final long PUSH_ANIM_DURATION = 400;
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PushInteractionHandler.PushAnimPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    pushAnimStart.put(payload.playerId(), System.currentTimeMillis());
                    Minecraft client = context.client();
                    if (client.level != null) {
                        for (var player : client.level.players()) {
                            if (player.getUUID().equals(payload.playerId())) {
                                CoopAnimationHandler.playPushAnimation(player);
                                break;
                            }
                        }
                    }
                    if (client.player != null && client.player.getUUID().equals(payload.playerId())) {
                        client.player.swing(InteractionHand.MAIN_HAND);
                    }
                }));
    }
    public static float getPushAnimProgress(UUID playerId) {
        Long start = pushAnimStart.get(playerId);
        if (start == null) return -1f;
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > PUSH_ANIM_DURATION) { pushAnimStart.remove(playerId); return -1f; }
        return (float) elapsed / PUSH_ANIM_DURATION;
    }
    public static void cleanup(UUID playerId) { pushAnimStart.remove(playerId); }
}