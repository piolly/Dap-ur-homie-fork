package com.cooptest;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class MsgUtil {
    private MsgUtil() {}

    public static void show(Player player, Component text, boolean overlay) {
        if (overlay) {
            player.sendOverlayMessage(text);
        } else {
            player.sendSystemMessage(text);
        }
    }
}
