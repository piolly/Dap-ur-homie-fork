package com.cooptest.client;

import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

public final class CoopRenderStateData {
   public static final RenderStateDataKey<CoopRenderStateData.PlayerSnapshot> PLAYER_SNAPSHOT = RenderStateDataKey.create(() -> "coop_player_snapshot");
   public static final RenderStateDataKey<Float> GRAB_FACING_YAW = RenderStateDataKey.create(() -> "coop_grab_facing_yaw");

   private CoopRenderStateData() {
   }

   public record PlayerSnapshot(
      UUID uuid, boolean handSwinging, boolean usingItem, boolean onGround, boolean hasVehicle, boolean vehicleIsPlayer, float vehicleYaw, float ownYaw
   ) {
   }
}
