package com.cooptest.client.compat;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;

public class CoopBoneCache {
   public static final ThreadLocal<UUID> renderingPlayer = new ThreadLocal<>();
   private static final Map<UUID, IdentityHashMap<ModelPart, float[]>> cache = new HashMap<>();

   public static void save(UUID id, PlayerModel pm) {
      IdentityHashMap<ModelPart, float[]> map = new IdentityHashMap<>();
      savePart(map, pm.head);
      savePart(map, pm.hat);
      savePart(map, pm.body);
      savePart(map, pm.rightArm);
      savePart(map, pm.leftArm);
      savePart(map, pm.rightLeg);
      savePart(map, pm.leftLeg);
      savePart(map, pm.rightSleeve);
      savePart(map, pm.leftSleeve);
      savePart(map, pm.rightPants);
      savePart(map, pm.leftPants);
      savePart(map, pm.jacket);
      cache.put(id, map);
   }

   private static void savePart(IdentityHashMap<ModelPart, float[]> map, ModelPart part) {
      map.put(part, new float[]{part.xRot, part.yRot, part.zRot});
   }

   public static float[] get(ModelPart part) {
      UUID id = renderingPlayer.get();
      if (id == null) {
         return null;
      }

      IdentityHashMap<ModelPart, float[]> map = cache.get(id);
      return map == null ? null : map.get(part);
   }

   public static void clear(UUID id) {
      cache.remove(id);
      renderingPlayer.remove();
   }
}
