package com.cooptest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class DivineFlamCombo {
   private static final Map<UUID, Long> comboWindowStart = new HashMap<>();
   private static final Map<UUID, UUID> comboPartner = new HashMap<>();
   private static final Map<UUID, Long> comboFreezeEnd = new HashMap<>();
   private static final long COMBO_WINDOW_MS = 1460L;
   private static final long COMBO_FREEZE_MS = 3800L;
   public static final Identifier DIVINE_J_PRESS_ID = Identifier.fromNamespaceAndPath("cooptest", "divine_j_press");
   public static final Identifier DIVINE_START_ID = Identifier.fromNamespaceAndPath("cooptest", "divine_start");

   public static void registerPayloads() {
      PayloadTypeRegistry.playC2S().register(DivineFlamCombo.DivineJPressPayload.ID, DivineFlamCombo.DivineJPressPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(DivineFlamCombo.DivineStartPayload.ID, DivineFlamCombo.DivineStartPayload.CODEC);
      System.out.println("[Divine Flame] Payloads registered");
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(DivineFlamCombo.DivineJPressPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> onPlayerPressJ(player));
      });
      System.out.println("[Divine Flame] Handlers registered");
   }

   public static void startDivineFlame(ServerPlayer p1, ServerPlayer p2, Vec3 midpoint) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      long now = System.currentTimeMillis();
      System.out.println("[Divine Flame] ===== STARTING DIVINE FLAME =====");
      comboWindowStart.put(id1, now);
      comboWindowStart.put(id2, now);
      comboPartner.put(id1, id2);
      comboPartner.put(id2, id1);
      System.out.println("[Divine Flame] Broadcasting ordinal 36 to both players");
      PoseNetworking.broadcastAnimState(p1, 36);
      PoseNetworking.broadcastAnimState(p2, 36);
      comboFreezeEnd.put(id1, now + 1460L);
      comboFreezeEnd.put(id2, now + 1460L);
      ServerPlayNetworking.send(p1, new DivineFlamCombo.DivineStartPayload());
      ServerPlayNetworking.send(p2, new DivineFlamCombo.DivineStartPayload());
      ServerLevel world = p1.level();
      world.playSound(null, midpoint.x, midpoint.y, midpoint.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 1.5F, 1.1F);
      System.out.println("[Divine Flame] Divine Flame window opened! Players have 1.46s to press J");
   }

   private static void onPlayerPressJ(ServerPlayer player) {
      UUID playerId = player.getUUID();
      Long windowStart = comboWindowStart.get(playerId);
      if (windowStart != null) {
         long elapsed = System.currentTimeMillis() - windowStart;
         if (elapsed > 1460L) {
            comboWindowStart.remove(playerId);
            comboPartner.remove(playerId);
         } else {
            UUID partnerId = comboPartner.get(playerId);
            if (partnerId != null) {
               ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
               if (partner != null) {
                  if (comboWindowStart.containsKey(partnerId)) {
                     executeCombo(player, partner);
                  }
               }
            }
         }
      }
   }

   private static void executeCombo(ServerPlayer p1, ServerPlayer p2) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      long now = System.currentTimeMillis();
      System.out.println("[Divine Flame] ===== EXECUTING COMBO =====");
      System.out.println("[Divine Flame] Both players pressed J!");
      comboWindowStart.remove(id1);
      comboWindowStart.remove(id2);
      comboPartner.remove(id1);
      comboPartner.remove(id2);
      comboFreezeEnd.put(id1, now + 3800L);
      comboFreezeEnd.put(id2, now + 3800L);
      System.out.println("[Divine Flame] Broadcasting ordinal 37 (P1) and 38 (P2)");
      PoseNetworking.broadcastAnimState(p1, 37);
      PoseNetworking.broadcastAnimState(p2, 38);
      ServerLevel world = p1.level();
      Vec3 midpoint = p1.position().add(p2.position()).scale(0.5);
      System.out.println("[Divine Flame] Spawning Divine Flame vortex at " + midpoint);
      world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 10.0F, 0.2F);
      world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 5.0F, 0.5F);
      System.out.println("[Divine Flame] Spawning particles...");
      world.sendParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y + 1.0, midpoint.z, 100, 1.0, 1.0, 1.0, 0.2);
      world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y + 1.0, midpoint.z, 50, 0.8, 0.8, 0.8, 0.15);
      world.sendParticles(ParticleTypes.LAVA, midpoint.x, midpoint.y + 1.0, midpoint.z, 30, 0.5, 0.5, 0.5, 0.1);
      System.out.println("[Divine Flame] DIVINE FLAME SPAWNED! Frozen for 3.8 seconds");
   }

   public static void tick(ServerLevel world) {
      long now = System.currentTimeMillis();

      for (Entry<UUID, UUID> entry : comboPartner.entrySet()) {
         UUID id1 = entry.getKey();
         UUID id2 = entry.getValue();
         ServerPlayer p1 = world.getServer().getPlayerList().getPlayer(id1);
         ServerPlayer p2 = world.getServer().getPlayerList().getPlayer(id2);
         if (p1 != null && p2 != null) {
            double distance = p1.position().distanceTo(p2.position());
            if (distance > 1.0) {
               Vec3 p1Pos = p1.position();
               Vec3 p2Pos = p2.position();
               Vec3 direction = p2Pos.subtract(p1Pos).normalize();
               double targetDistance = 0.8;
               Vec3 midpoint = p1Pos.add(p2Pos).scale(0.5);
               Vec3 offset = direction.scale(targetDistance / 2.0);
               Vec3 targetP1 = midpoint.subtract(offset);
               Vec3 targetP2 = midpoint.add(offset);
               double dx = p2Pos.x - p1Pos.x;
               double dz = p2Pos.z - p1Pos.z;
               float yawP1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
               float yawP2 = yawP1 + 180.0F;
               p1.teleportTo(p1.level(), targetP1.x, targetP1.y, targetP1.z, Set.of(), yawP1, p1.getXRot(), false);
               p2.teleportTo(p2.level(), targetP2.x, targetP2.y, targetP2.z, Set.of(), yawP2, p2.getXRot(), false);
            }
         }
      }

      comboWindowStart.entrySet().removeIf(entryx -> {
         if (now - (Long)entryx.getValue() > 1460L) {
            UUID id = (UUID)entryx.getKey();
            comboPartner.remove(id);
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
               PoseNetworking.broadcastAnimState(player, 0);
            }

            return true;
         } else {
            return false;
         }
      });
      comboFreezeEnd.entrySet().removeIf(entryx -> {
         if (now >= (Long)entryx.getValue()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer((UUID)entryx.getKey());
            if (player != null) {
               PoseNetworking.broadcastAnimState(player, 0);
            }

            return true;
         } else {
            return false;
         }
      });
   }

   public static void cleanup(UUID playerId) {
      comboWindowStart.remove(playerId);
      comboPartner.remove(playerId);
      comboFreezeEnd.remove(playerId);
   }

   public static boolean isInComboFreeze(UUID playerId) {
      return comboFreezeEnd.containsKey(playerId);
   }

   public record DivineJPressPayload() implements CustomPacketPayload {
      public static final Type<DivineFlamCombo.DivineJPressPayload> ID = new Type(DivineFlamCombo.DIVINE_J_PRESS_ID);
      public static final StreamCodec<FriendlyByteBuf, DivineFlamCombo.DivineJPressPayload> CODEC = StreamCodec.unit(new DivineFlamCombo.DivineJPressPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record DivineStartPayload() implements CustomPacketPayload {
      public static final Type<DivineFlamCombo.DivineStartPayload> ID = new Type(DivineFlamCombo.DIVINE_START_ID);
      public static final StreamCodec<FriendlyByteBuf, DivineFlamCombo.DivineStartPayload> CODEC = StreamCodec.unit(new DivineFlamCombo.DivineStartPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
