package com.cooptest.client;

import com.cooptest.FallDapHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class FallDapClientHandler {

    private static final Map<UUID, Integer> fallDapStates = new HashMap<>();

    public static final int STATE_NONE = 0;
    public static final int STATE_CHARGING = 1;
    public static final int STATE_FALLING = 2;
    public static final int STATE_HIT = 3;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FallDapHandler.FallDapAnimPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();
                        int state = payload.state();

                        fallDapStates.put(playerId, state);

                        Minecraft client = context.client();
                        if (client.level != null) {
                            for (Player player : client.level.players()) {
                                if (player.getUUID().equals(playerId)) {
                                    triggerFallDapAnimation(player, state);
                                    break;
                                }
                            }
                        }
                    });
                }
        );


        ClientPlayNetworking.registerGlobalReceiver(FallDapHandler.SquashAnimPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        UUID playerId = payload.playerId();


                        Minecraft client = context.client();
                        if (client.level != null) {
                            for (Player player : client.level.players()) {
                                if (player.getUUID().equals(playerId)) {
                                    CoopAnimationHandler.playSquashed(player);
                                    break;
                                }
                            }
                        }
                    });
                }
        );
    }

    private static void triggerFallDapAnimation(Player player, int state) {
        UUID playerId = player.getUUID();

        switch (state) {
            case STATE_CHARGING -> CoopAnimationHandler.playFallDapChargeStart(player);
            case STATE_FALLING -> CoopAnimationHandler.playFallDapFalling(player);
            case STATE_HIT -> CoopAnimationHandler.playFallDapHit(player);
            case STATE_NONE -> {

                fallDapStates.remove(playerId);

            }
        }
    }

    public static int getFallDapState(UUID playerId) {
        return fallDapStates.getOrDefault(playerId, STATE_NONE);
    }

    public static boolean isInFallDap(UUID playerId) {
        int state = fallDapStates.getOrDefault(playerId, STATE_NONE);
        return state == STATE_CHARGING || state == STATE_FALLING;
    }

    public static void cleanup(UUID playerId) {
        fallDapStates.remove(playerId);
    }
}