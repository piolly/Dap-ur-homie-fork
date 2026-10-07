package com.cooptest;

import com.cooptest.highfive.HighFiveNormalHandler;
import com.cooptest.highfive.HighFiveShakeHandler;
import com.cooptest.highfive.ReadyFiveHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HighFiveHandler {
   public static final float HIGH_FIVE_RANGE = 1.6F;
   public static final long HAND_RAISED_DURATION = 2500L;
   public static final long COOLDOWN_MS = 1000L;
   public static final long HIGH_FIVE_ANIM_DURATION = 1500L;
   public static final long START_ANIM_DELAY_MS = 0L;
   public static final long HIT_EFFECT_DELAY_MS = 100L;
   public static final long END_ANIM_DURATION_MS = 1500L;
   public static final double SPEED_TIER_1 = 5.0;
   public static final double SPEED_TIER_2 = 7.5;
   public static final double SPEED_TIER_3 = 12.0;
   public static final int SPEED_HISTORY_TICKS = 60;
   public static final double SLOT_FORWARD_OFFSET = 0.4;
   public static final double SLOT_LEFT_OFFSET = -0.8;
   public static final double SLOT_RADIUS = 1.0;
   public static final double TP_PUSHBACK_OFFSET = 0.1;
   public static final double TP_PUSHBACK_LEFT_OFFSET = 0.2;
   public static final double FRONT_EXCLUDE_DEG = 30.0;
   public static final boolean PASS_SLOT_DEBUG_PARTICLES = false;
   public static final float PASS_DETECT_RANGE = 4.0F;
   public static final double REACH_FORWARD_OFFSET = 0.7;
   public static final double REACH_RADIUS = 0.6;
   public static final double FAST_SPEED_THRESHOLD = 4.5;
   public static final double STANDUP_NORMAL_SEPARATION = 1.1;
   public static final double STANDUP_NORMAL_RIGHT = 0.2;
   public static final long STANDUP_NORMAL_IMPACT_MS = 290L;
   public static final long STANDUP_NORMAL_PULLBACK_MS = 540L;
   public static final double STANDUP_NORMAL_PULLBACK = 0.4;
   public static final double STANDUP_FAST_SEPARATION = 0.9;
   public static final double STANDUP_FAST_RIGHT = 0.2;
   public static final long STANDUP_FAST_IMPACT_MS = 210L;
   public static final double STANDUP_FAST_PUSH_THROUGH = 0.04;
   public static final double STANDUP_MIXED_STILL_NUDGE_FWD = 0.12;
   public static final double STANDUP_MIXED_STILL_NUDGE_BACK = 0.08;
   public static final int STANDUP_YAW_LERP_TICKS = 5;
   public static final boolean STANDUP_REACH_DEBUG = true;
   public static final Map<UUID, Long> handRaisedTime = new HashMap<>();
   public static final Map<UUID, Long> highFiveCooldown = new HashMap<>();
   public static final Map<UUID, Long> highFiveAnimStart = new HashMap<>();
   public static final Map<UUID, Long> startAnimTime = new HashMap<>();
   public static final Map<UUID, Long> lastHoldRefresh = new HashMap<>();
   public static final Map<UUID, Boolean> handRaiseSprinting = new HashMap<>();
   public static final Map<UUID, Long> endAnimTime = new HashMap<>();
   private static final Map<UUID, Long> comboWindowStart = new HashMap<>();
   private static final Map<UUID, UUID> comboPartner = new HashMap<>();
   private static final Map<UUID, Long> comboRequested = new HashMap<>();
   private static final Map<UUID, Long> comboFreezeEnd = new HashMap<>();
   private static final Map<UUID, HighFiveHandler.ComboImpact> pendingComboImpacts = new HashMap<>();
   private static final long COMBO_WINDOW_MS = 1000L;
   private static final long COMBO_FREEZE_MS = 2250L;
   private static final long COMBO_SECOND_HIT_MS = 1290L;
   private static final Map<UUID, Vec3> frozenPositions = new HashMap<>();
   private static final Map<BlockPos, HighFiveHandler.BeaconRemoval> pendingBeaconRemovals = new HashMap<>();
   private static final List<HighFiveHandler.ParticleBeam> activeParticleBeams = new ArrayList<>();
   private static final Map<UUID, HighFiveHandler.PendingHighFive> pendingEffects = new HashMap<>();
   public static final Map<UUID, LinkedList<Double>> speedHistory = new HashMap<>();
   public static final Identifier HIGH_FIVE_REQUEST_ID = Identifier.fromNamespaceAndPath("cooptest", "high_five_request");
   public static final Identifier HIGH_FIVE_HOLD_ID = Identifier.fromNamespaceAndPath("cooptest", "high_five_hold");
   public static final Identifier HAND_RAISED_SYNC_ID = Identifier.fromNamespaceAndPath("cooptest", "hand_raised_sync");
   public static final Identifier HIGH_FIVE_SUCCESS_ID = Identifier.fromNamespaceAndPath("cooptest", "high_five_success");
   public static final Identifier HIGH_FIVE_ANIM_ID = Identifier.fromNamespaceAndPath("cooptest", "high_five_anim");
   public static final Identifier COMBO_REQUEST_ID = Identifier.fromNamespaceAndPath("cooptest", "highfive_combo_request");
   public static final Identifier COMBO_WINDOW_ID = Identifier.fromNamespaceAndPath("cooptest", "highfive_combo_window");
   public static final Identifier COMBO_WINDOW_CLOSE_ID = Identifier.fromNamespaceAndPath("cooptest", "highfive_combo_window_close");
   public static final Identifier FREEZE_STATE_ID = Identifier.fromNamespaceAndPath("testcoop", "freeze_state");
   public static final int ANIM_START = 1;
   public static final int ANIM_END = 2;
   public static final int ANIM_HIT = 3;
   public static final int ANIM_SIKE = 4;
   private static final Set<UUID> sikeMode = new HashSet<>();
   private static final Map<UUID, Long> sikeStunEnd = new HashMap<>();
   private static final Map<UUID, Long> sikeSlowEnd = new HashMap<>();
   private static final long SIKE_ANIM_MS = 1458L;
   private static final long SIKE_SLOW_MS = 2000L;
   private static final Identifier SIKE_SLOW_ID = Identifier.fromNamespaceAndPath("testcoop", "sike_slow");
   public static final float HF_SHAKE_BASE = 0.35F;
   public static final float HF_SHAKE_PER_TIER = 0.25F;
   public static final int HF_SHAKE_MS = 200;
   public static final float HF_RARE_CHANCE = 0.12F;
   public static final int HF_FAST_BUFF_TICKS = 120;
   public static final int HF_FAST_SPEED_AMP = 2;
   public static final int HF_FAST_JUMP_AMP = 1;

   private static void spawnParticleBeam(ServerLevel world, Vec3 playerPos, boolean isYellow) {
      long now = System.currentTimeMillis();
      long endTime = now + 1750L;
      activeParticleBeams.add(new HighFiveHandler.ParticleBeam(world, playerPos.add(0.0, 0.5, 0.0), isYellow, endTime));
   }

   private static void tickParticleBeams(long now) {
      Iterator<HighFiveHandler.ParticleBeam> it = activeParticleBeams.iterator();

      while (it.hasNext()) {
         HighFiveHandler.ParticleBeam beam = it.next();
         if (now >= beam.endTime) {
            it.remove();
         } else {
            for (int i = 0; i < 30; i++) {
               double y = beam.startPos.y + i * 0.5;
               if (beam.isYellow) {
                  beam.world.sendParticles(ParticleTypes.END_ROD, beam.startPos.x, y, beam.startPos.z, 3, 0.15, 0.0, 0.15, 0.0);
                  beam.world.sendParticles(ParticleTypes.FLAME, beam.startPos.x, y, beam.startPos.z, 2, 0.1, 0.0, 0.1, 0.0);
                  double spiralAngle = i * 0.3;
                  double spiralRadius = 0.5;
                  double spiralX = beam.startPos.x + Math.cos(spiralAngle) * spiralRadius;
                  double spiralZ = beam.startPos.z + Math.sin(spiralAngle) * spiralRadius;
                  beam.world.sendParticles(ParticleTypes.END_ROD, spiralX, y, spiralZ, 1, 0.0, 0.0, 0.0, 0.0);
               } else {
                  beam.world.sendParticles(ParticleTypes.SQUID_INK, beam.startPos.x, y, beam.startPos.z, 3, 0.15, 0.0, 0.15, 0.0);
                  beam.world.sendParticles(ParticleTypes.LARGE_SMOKE, beam.startPos.x, y, beam.startPos.z, 2, 0.1, 0.0, 0.1, 0.0);
                  double spiralAngle = i * 0.3;
                  double spiralRadius = 0.5;
                  double spiralX = beam.startPos.x + Math.cos(spiralAngle) * spiralRadius;
                  double spiralZ = beam.startPos.z + Math.sin(spiralAngle) * spiralRadius;
                  beam.world.sendParticles(ParticleTypes.SQUID_INK, spiralX, y, spiralZ, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }
         }
      }
   }

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(HighFiveHandler.HighFiveRequestPayload.ID, HighFiveHandler.HighFiveRequestPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HighFiveHandler.HighFiveHoldPayload.ID, HighFiveHandler.HighFiveHoldPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveHandler.HighFiveSuccessPayload.ID, HighFiveHandler.HighFiveSuccessPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveHandler.HandRaisedSyncPayload.ID, HighFiveHandler.HandRaisedSyncPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveHandler.HighFiveAnimPayload.ID, HighFiveHandler.HighFiveAnimPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HighFiveHandler.ComboRequestPayload.ID, HighFiveHandler.ComboRequestPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveHandler.ComboWindowPayload.ID, HighFiveHandler.ComboWindowPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveHandler.ComboWindowClosePayload.ID, HighFiveHandler.ComboWindowClosePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveHandler.FreezeStatePayload.ID, HighFiveHandler.FreezeStatePayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HighFiveHandler.SikeRequestPayload.ID, HighFiveHandler.SikeRequestPayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(HighFiveHandler.HighFiveRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> {
            if (CoopMovesConfig.get().enableHighFive) {
               onHighFiveRequest(player);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HighFiveHandler.HighFiveHoldPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> {
            UUID id = player.getUUID();
            if (handRaisedTime.containsKey(id)) {
               lastHoldRefresh.put(id, System.currentTimeMillis());
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HighFiveHandler.ComboRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> {
            if (CoopMovesConfig.get().enableHighFiveCombo) {
               onComboRequest(player);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HighFiveHandler.SikeRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> {
            if (CoopMovesConfig.get().enableHighFive) {
               UUID id = player.getUUID();
               sikeMode.add(id);
               onHighFiveRequest(player);
            }
         });
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         long now = System.currentTimeMillis();
         tickParticleBeams(now);

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            Vec3 velocity = player.getDeltaMovement();
            double speed = Math.hypot(velocity.x, velocity.z) * 20.0;
            LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());
            history.addLast(speed);

            while (history.size() > 60) {
               history.removeFirst();
            }
         }

         Iterator<Entry<UUID, HighFiveHandler.PendingHighFive>> pendingIt = pendingEffects.entrySet().iterator();

         while (pendingIt.hasNext()) {
            Entry<UUID, HighFiveHandler.PendingHighFive> entry = pendingIt.next();
            HighFiveHandler.PendingHighFive pending = entry.getValue();
            if (now >= pending.effectTime) {
               executeHighFiveEffects(pending.p1, pending.p2, pending.pos, pending.tier);
               pendingIt.remove();
            }
         }

         Set<String> processedComboPairs = new HashSet<>();
         Iterator<Entry<UUID, Long>> comboWindowIt = comboWindowStart.entrySet().iterator();

         while (comboWindowIt.hasNext()) {
            Entry<UUID, Long> entry = comboWindowIt.next();
            UUID playerId = entry.getKey();
            long windowStart = entry.getValue();
            if (now - windowStart > 1000L) {
               boolean playerPressed = comboRequested.containsKey(playerId);
               UUID partnerId = comboPartner.get(playerId);
               if (playerPressed && partnerId != null) {
                  String pairKey = playerId.compareTo(partnerId) < 0 ? playerId + ":" + partnerId : partnerId + ":" + playerId;
                  if (!processedComboPairs.contains(pairKey)) {
                     processedComboPairs.add(pairKey);
                     ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                     ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
                     if (player != null && partner != null) {
                        boolean partnerPressed = comboRequested.containsKey(partnerId);
                        if (!partnerPressed) {
                           player.sendOverlayMessage(Component.literal("§c✗ " + partner.getName().getString() + " missed the combo!"));
                           partner.sendOverlayMessage(Component.literal("§c✗ You missed the combo! " + player.getName().getString() + " pressed H!"));
                        }
                     }
                  }
               }

               comboWindowIt.remove();
               comboPartner.remove(playerId);
               comboRequested.remove(playerId);
            }
         }

         Iterator<Entry<UUID, HighFiveHandler.ComboImpact>> comboImpactIt = pendingComboImpacts.entrySet().iterator();

         while (comboImpactIt.hasNext()) {
            Entry<UUID, HighFiveHandler.ComboImpact> entry = comboImpactIt.next();
            HighFiveHandler.ComboImpact impact = entry.getValue();
            if (now >= impact.impactTime) {
               executeSecondImpact(impact.p1, impact.p2);
               comboImpactIt.remove();
            }
         }

         Iterator<Entry<UUID, Long>> freezeIt = comboFreezeEnd.entrySet().iterator();

         while (freezeIt.hasNext()) {
            Entry<UUID, Long> entry = freezeIt.next();
            if (now >= entry.getValue()) {
               UUID playerId = entry.getKey();
               freezeIt.remove();
               frozenPositions.remove(playerId);
               handRaisedTime.remove(playerId);
               startAnimTime.remove(playerId);
               ServerPlayer player = server.getPlayerList().getPlayer(playerId);
               if (player != null) {
                  syncHandRaised(player, false);
                  PoseNetworking.broadcastAnimState(player, 0);

                  for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                     ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(playerId, false));
                  }

                  ServerPlayNetworking.send(player, new HighFiveHandler.ComboWindowClosePayload(playerId));
                  System.out.println("[HighFive] Combo ended - cleared hand raised and reset anim for " + player.getName().getString());
               }
            }
         }

         for (Entry<UUID, Vec3> entry : frozenPositions.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
               Vec3 frozenPos = entry.getValue();
               Vec3 currentPos = player.position();
               if (currentPos.distanceToSqr(frozenPos) > 0.01) {
                  player.teleportTo(frozenPos.x, frozenPos.y, frozenPos.z);
                  player.setDeltaMovement(Vec3.ZERO);
                  player.syncVelocity = true;
               }
            }
         }

         Iterator<Entry<UUID, Long>> animIt = highFiveAnimStart.entrySet().iterator();

         while (animIt.hasNext()) {
            Entry<UUID, Long> entry = animIt.next();
            if (now - entry.getValue() > 1500L) {
               UUID playerId = entry.getKey();
               boolean inCombo = comboFreezeEnd.containsKey(playerId);
               boolean inDapCombo = DapComboChain.isInCombo(playerId);
               if (!inCombo && !inDapCombo) {
                  ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                  if (player != null) {
                     PoseNetworking.broadcastAnimState(player, 0);
                  }
               }

               animIt.remove();
            }
         }

         Iterator<Entry<BlockPos, HighFiveHandler.BeaconRemoval>> beaconIt = pendingBeaconRemovals.entrySet().iterator();

         while (beaconIt.hasNext()) {
            Entry<BlockPos, HighFiveHandler.BeaconRemoval> entry = beaconIt.next();
            HighFiveHandler.BeaconRemoval removal = entry.getValue();
            if (now >= removal.removeTime) {
               removal.world.setBlockAndUpdate(removal.beaconPos, Blocks.AIR.defaultBlockState());
               removal.world.setBlockAndUpdate(removal.glassPos, Blocks.AIR.defaultBlockState());
               beaconIt.remove();
            }
         }

         Iterator<Entry<UUID, Long>> sikeStunIt = sikeStunEnd.entrySet().iterator();

         while (sikeStunIt.hasNext()) {
            Entry<UUID, Long> entry = sikeStunIt.next();
            if (now >= entry.getValue()) {
               UUID victimId = entry.getKey();
               sikeStunIt.remove();
               ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
               if (victim != null) {
                  for (ServerPlayer p : PlayerLookup.all(server)) {
                     ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(victimId, false));
                  }

                  frozenPositions.remove(victimId);
                  PoseNetworking.broadcastAnimState(victim, 0);
                  syncHandRaised(victim, false);
                  applySikeSlow(victim);
                  sikeSlowEnd.put(victimId, now + 2000L);
               }
            }
         }

         Iterator<Entry<UUID, Long>> sikeSlowIt = sikeSlowEnd.entrySet().iterator();

         while (sikeSlowIt.hasNext()) {
            Entry<UUID, Long> entry = sikeSlowIt.next();
            if (now >= entry.getValue()) {
               UUID victimId = entry.getKey();
               sikeSlowIt.remove();
               ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
               if (victim != null) {
                  removeSikeSlow(victim);
               }
            }
         }

         Iterator<Entry<UUID, Long>> endIt = endAnimTime.entrySet().iterator();

         while (endIt.hasNext()) {
            Entry<UUID, Long> entry = endIt.next();
            if (now >= entry.getValue()) {
               UUID playerId = entry.getKey();
               endIt.remove();
               ServerPlayer player = server.getPlayerList().getPlayer(playerId);
               if (player != null) {
                  PoseNetworking.broadcastAnimState(player, 0);
               }
            }
         }

         Iterator<Entry<UUID, Long>> it = handRaisedTime.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Long> entry = it.next();
            UUID playerId = entry.getKey();
            if (!HighFiveShakeHandler.isHandshakeClaimed(playerId)) {
               long startAnimStartTime = startAnimTime.getOrDefault(playerId, entry.getValue());
               long baseline = Math.max(startAnimStartTime, lastHoldRefresh.getOrDefault(playerId, 0L));
               if (now - baseline > 2500L) {
                  ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                  it.remove();
                  startAnimTime.remove(playerId);
                  lastHoldRefresh.remove(playerId);
                  handRaiseSprinting.remove(playerId);
                  if (player != null) {
                     executeEndAnimation(player);
                     syncHandRaised(player, false);
                  }
               }
            }
         }

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (handRaisedTime.containsKey(playerId) && !isOnCooldown(playerId) && !isInBlockingState(playerId)) {
               Long startTime = startAnimTime.get(playerId);
               if ((startTime == null || now - startTime >= 0L) && !HighFiveShakeHandler.isHandshakeClaimed(playerId)) {
                  if (server.getTickCount() % 5 == 0) {
                     Vec3 rp = reachPoint(player);
                     player.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, rp.x, rp.y, rp.z, 1, 0.0, 0.0, 0.0, 0.0);
                  }

                  ServerPlayer passPartner = findPassByPartner(player);
                  if (passPartner != null) {
                     Long passPartnerStartTime = startAnimTime.get(passPartner.getUUID());
                     if (passPartnerStartTime == null || now - passPartnerStartTime >= 0L) {
                        consumeHandRaise(player);
                        consumeHandRaise(passPartner);
                        Vec3 targetP1 = slotPoint(passPartner).subtract(horizontalForward(player).scale(0.1)).add(horizontalLeft(player).scale(0.2));
                        Vec3 targetP2 = slotPoint(player).subtract(horizontalForward(passPartner).scale(0.1)).add(horizontalLeft(passPartner).scale(0.2));
                        HighFivePassHandler.executePassHighFive(player, passPartner, targetP1, targetP2);
                     }
                  } else {
                     ServerPlayer partner = findHighFivePartner(player);
                     if (partner != null) {
                        Long partnerStartTime = startAnimTime.get(partner.getUUID());
                        if ((partnerStartTime == null || now - partnerStartTime >= 0L) && isRoughlyAhead(player, partner) && isRoughlyAhead(partner, player)) {
                           executeHighFive(player, partner);
                        }
                     }
                  }
               }
            }
         }
      });
   }

   public static boolean isInBlockingState(UUID playerId) {
      return isInBlockingAnimation(playerId) || FallDapHandler.isSquashed(playerId) || sikeStunEnd.containsKey(playerId) || sikeSlowEnd.containsKey(playerId);
   }

   public static boolean isInHighFiveMode(UUID playerId) {
      return handRaisedTime.containsKey(playerId) || startAnimTime.containsKey(playerId);
   }

   public static boolean isInAnyHighFiveState(UUID playerId) {
      return isInHighFiveMode(playerId) || isInBlockingState(playerId) || highFiveAnimStart.containsKey(playerId);
   }

   public static boolean canPerformAction(UUID playerId) {
      return !isInBlockingState(playerId);
   }

   private static void executeEndAnimation(ServerPlayer player) {
      UUID playerId = player.getUUID();
      long now = System.currentTimeMillis();
      endAnimTime.put(playerId, now + 1500L);
      broadcastHighFiveAnim(player, 2);
      ServerLevel world = player.level();
      Vec3 pos = player.position().add(0.0, 1.6, 0.0);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.5F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.4F, 1.2F);
      world.sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 6, 0.15, 0.15, 0.15, 0.01);
      player.sendOverlayMessage(Component.literal("§7*left hanging*"));
   }

   private static void broadcastHighFiveAnim(ServerPlayer player, int animState) {
      MinecraftServer server = player.level().getServer();
      if (server != null) {
         HighFiveHandler.HighFiveAnimPayload payload = new HighFiveHandler.HighFiveAnimPayload(player.getUUID(), animState);

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
         }
      }
   }

   private static double getMaxRecentSpeed(UUID playerId) {
      LinkedList<Double> history = speedHistory.get(playerId);
      if (history != null && !history.isEmpty()) {
         double maxSpeed = 0.0;

         for (Double speed : history) {
            if (speed > maxSpeed) {
               maxSpeed = speed;
            }
         }

         return maxSpeed;
      } else {
         return 0.0;
      }
   }

   private static void onHighFiveRequest(ServerPlayer player) {
      UUID uuid = player.getUUID();
      if (!ReadyFiveHandler.onHandRaise(player)) {
         if (!handRaisedTime.containsKey(uuid) && ChargedDapHandler.isCharging(uuid)) {
            System.out.println("[HighFive] Blocked H raise - G is active!");
            syncHandRaised(player, false);
         } else if (ChargedDapHandler.isInComboCooldown(uuid)) {
            System.out.println("[HighFive] Blocked H raise - combo cooldown active!");
            player.sendOverlayMessage(Component.literal("§cWait 1 second after combo!"));
            syncHandRaised(player, false);
         } else if (!isInBlockingState(uuid)) {
            if (!FallCatchHandler.isInCatchReadyMode(uuid)) {
               if (!HuddleHandler.isInHuddle(uuid)) {
                  if (!isOnCooldown(uuid)) {
                     if (player.getMainHandItem().isEmpty()) {
                        if (handRaisedTime.containsKey(uuid)) {
                           handRaisedTime.remove(uuid);
                           startAnimTime.remove(uuid);
                           lastHoldRefresh.remove(uuid);
                           handRaiseSprinting.remove(uuid);
                           syncHandRaised(player, false);
                           HighFiveShakeHandler.onHandLowered(uuid);
                           executeEndAnimation(player);
                        } else {
                           long now = System.currentTimeMillis();
                           handRaisedTime.put(uuid, now);
                           startAnimTime.put(uuid, now);
                           handRaiseSprinting.put(uuid, player.isSprinting());
                           syncHandRaised(player, true);
                           broadcastHighFiveAnim(player, 1);
                           player.level()
                              .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3F, 1.5F);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void syncHandRaised(ServerPlayer player, boolean raised) {
      if (player != null) {
         System.out.println("[HighFive Server] syncHandRaised: " + player.getName().getString() + " raised=" + raised);
         HighFiveHandler.HandRaisedSyncPayload payload = new HighFiveHandler.HandRaisedSyncPayload(player.getUUID(), raised);

         for (ServerPlayer other : PlayerLookup.all(player.level().getServer())) {
            ServerPlayNetworking.send(other, payload);
         }
      }
   }

   private static void spawnSlotDebugMarker(ServerPlayer player) {
      Vec3 slot = slotPoint(player);
      double y = player.getY() + 1.0;
      ServerLevel world = player.level();
      world.sendParticles(ParticleTypes.END_ROD, slot.x, y, slot.z, 3, 0.0, 0.05, 0.0, 0.01);
      int ringPoints = 16;

      for (int i = 0; i < ringPoints; i++) {
         double angle = (Math.PI * 2) * i / ringPoints;
         double rx = slot.x + Math.cos(angle) * 1.0;
         double rz = slot.z + Math.sin(angle) * 1.0;
         world.sendParticles(ParticleTypes.WITCH, rx, y, rz, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   public static Vec3 horizontalForward(ServerPlayer player) {
      double yawRad = Math.toRadians(player.getYRot());
      return new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
   }

   public static Vec3 horizontalLeft(ServerPlayer player) {
      double yawRad = Math.toRadians(player.getYRot());
      return new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));
   }

   public static Vec3 slotPoint(ServerPlayer player) {
      Vec3 pos = player.position();
      Vec3 forward = horizontalForward(player);
      Vec3 left = horizontalLeft(player);
      return new Vec3(pos.x + forward.x * 0.4 + left.x * -0.8, pos.y, pos.z + forward.z * 0.4 + left.z * -0.8);
   }

   private static double horizontalDistance(Vec3 a, Vec3 b) {
      return Math.hypot(a.x - b.x, a.z - b.z);
   }

   private static boolean inEachOthersSlot(ServerPlayer p1, ServerPlayer p2) {
      double d1 = horizontalDistance(slotPoint(p1), p2.position());
      double d2 = horizontalDistance(slotPoint(p2), p1.position());
      return d1 <= 1.0 || d2 <= 1.0;
   }

   private static boolean isRoughlyAhead(ServerPlayer self, ServerPlayer other) {
      Vec3 toOther = other.position().subtract(self.position());
      Vec3 dir = new Vec3(toOther.x, 0.0, toOther.z);
      if (dir.lengthSqr() < 1.0E-6) {
         return false;
      }

      dir = dir.normalize();
      double dot = Mth.clamp(horizontalForward(self).dot(dir), -1.0, 1.0);
      double angleDeg = Math.toDegrees(Math.acos(dot));
      return angleDeg <= 30.0;
   }

   private static boolean isValidPassByGeometry(ServerPlayer p1, ServerPlayer p2) {
      return !isRoughlyAhead(p1, p2) && !isRoughlyAhead(p2, p1) ? inEachOthersSlot(p1, p2) : false;
   }

   private static void consumeHandRaise(ServerPlayer player) {
      UUID id = player.getUUID();
      handRaisedTime.remove(id);
      startAnimTime.remove(id);
      lastHoldRefresh.remove(id);
      handRaiseSprinting.remove(id);
      syncHandRaised(player, false);
   }

   private static ServerPlayer findPassByPartner(ServerPlayer player) {
      if (!CoopMovesConfig.get().enableHighFivePass) {
         return null;
      }

      AABB searchBox = player.getBoundingBox().inflate(4.0);
      long now = System.currentTimeMillis();

      for (ServerPlayer other : player.level().players()) {
         if (other != player && handRaisedTime.containsKey(other.getUUID()) && !isOnCooldown(other.getUUID()) && !isInBlockingState(other.getUUID())) {
            Long otherStartTime = startAnimTime.get(other.getUUID());
            if ((otherStartTime == null || now - otherStartTime >= 0L) && searchBox.intersects(other.getBoundingBox())) {
               if (isValidPassByGeometry(player, other)) {
                  return other;
               }

               logPassByDebug(player, other);
            }
         }
      }

      return null;
   }

   private static void logPassByDebug(ServerPlayer p1, ServerPlayer p2) {
      double d1 = horizontalDistance(slotPoint(p1), p2.position());
      double d2 = horizontalDistance(slotPoint(p2), p1.position());
      float facingDiff = Mth.wrapDegrees(p1.getYRot() - p2.getYRot());
      System.out
         .println(
            new StringBuilder("[HighFivePass-DEBUG] ")
               .append(p1.getName().getString())
               .append(" <-> ")
               .append(p2.getName().getString())
               .append(" | ")
               .append(p2.getName().getString())
               .append(" in ")
               .append(p1.getName().getString())
               .append("'s slot: dist=")
               .append(String.format("%.2f", d1))
               .append(" | ")
               .append(p1.getName().getString())
               .append(" in ")
               .append(p2.getName().getString())
               .append("'s slot: dist=")
               .append(String.format("%.2f", d2))
               .append(" (need <= ")
               .append(1.0)
               .append(" on either side)")
               .append(" | facingDiff=")
               .append(facingDiff)
         );
   }

   private static void scheduleYawLerp(ServerPlayer player, ServerPlayer target) {
      Vec3 toTarget = target.position().subtract(player.position());
      float targetYaw = (float)Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
      float startYaw = player.getYRot();
      int ticks = 5;
      new Thread(() -> {
         for (int i = 1; i <= ticks; i++) {
            float t = (float)i / ticks;
            float newYaw = startYaw + Mth.wrapDegrees(targetYaw - startYaw) * t;

            try {
               Thread.sleep(50L);
            } catch (InterruptedException var8) {
            }

            player.level().getServer().execute(() -> {
               player.setYRot(newYaw);
               player.setYHeadRot(newYaw);
               player.setYBodyRot(newYaw);
            });
         }
      }).start();
   }

   public static Vec3 reachPoint(ServerPlayer player) {
      CoopMovesConfig c = CoopMovesConfig.get();
      Vec3 fwd = horizontalForward(player);
      Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
      Vec3 pos = player.position();
      return new Vec3(
         pos.x + fwd.x * c.highFiveReachForward + right.x * c.highFiveReachRightOffset,
         pos.y + 1.3,
         pos.z + fwd.z * c.highFiveReachForward + right.z * c.highFiveReachRightOffset
      );
   }

   public static boolean reachPointsOverlap(ServerPlayer p1, ServerPlayer p2) {
      CoopMovesConfig c = CoopMovesConfig.get();
      Vec3 rp1 = reachPoint(p1);
      Vec3 rp2 = reachPoint(p2);
      double dx = rp2.x - rp1.x;
      double dz = rp2.z - rp1.z;
      Vec3 fwd = horizontalForward(p1);
      double depth = dx * fwd.x + dz * fwd.z;
      double width = dx * -fwd.z + dz * fwd.x;
      double d = Math.max(0.05, c.highFiveReachDepth);
      double w = Math.max(0.05, c.highFiveReachWidth);
      return depth * depth / (d * d) + width * width / (w * w) <= 1.0;
   }

   private static ServerPlayer findHighFivePartner(ServerPlayer player) {
      AABB searchBox = player.getBoundingBox().inflate(4.0);
      long now = System.currentTimeMillis();

      for (ServerPlayer other : player.level().players()) {
         if (other != player && handRaisedTime.containsKey(other.getUUID()) && !isOnCooldown(other.getUUID()) && !isInBlockingState(other.getUUID())) {
            Long otherStartTime = startAnimTime.get(other.getUUID());
            if ((otherStartTime == null || now - otherStartTime >= 0L) && searchBox.intersects(other.getBoundingBox()) && reachPointsOverlap(player, other)) {
               return other;
            }
         }
      }

      return null;
   }

   private static void executeHighFive(ServerPlayer player1, ServerPlayer player2) {
      boolean p1Siking = sikeMode.remove(player1.getUUID());
      boolean p2Siking = sikeMode.remove(player2.getUUID());
      if (p1Siking && p2Siking) {
         executeMutualSike(player1, player2);
      } else if (p1Siking) {
         executeSike(player1, player2);
      } else if (p2Siking) {
         executeSike(player2, player1);
      } else {
         long now = System.currentTimeMillis();
         highFiveCooldown.put(player1.getUUID(), now);
         highFiveCooldown.put(player2.getUUID(), now);
         handRaisedTime.remove(player1.getUUID());
         handRaisedTime.remove(player2.getUUID());
         startAnimTime.remove(player1.getUUID());
         startAnimTime.remove(player2.getUUID());
         lastHoldRefresh.remove(player1.getUUID());
         lastHoldRefresh.remove(player2.getUUID());
         syncHandRaised(player1, false);
         syncHandRaised(player2, false);
         highFiveAnimStart.put(player1.getUUID(), now);
         highFiveAnimStart.put(player2.getUUID(), now);
         boolean p1Fast = handRaiseSprinting.getOrDefault(player1.getUUID(), false);
         boolean p2Fast = handRaiseSprinting.getOrDefault(player2.getUUID(), false);
         System.out
            .printf(
               "[HighFive-SPRINT] %s=%s %s=%s%n",
               player1.getName().getString(),
               p1Fast ? "SPRINTING" : "normal",
               player2.getName().getString(),
               p2Fast ? "SPRINTING" : "normal"
            );
         double speed1 = getMaxRecentSpeed(player1.getUUID());
         double speed2 = getMaxRecentSpeed(player2.getUUID());
         double maxSpeed = Math.max(speed1, speed2);
         int tier;
         if (maxSpeed >= 12.0) {
            tier = 3;
         } else if (p1Fast && p2Fast) {
            tier = 2;
         } else if (!p1Fast && !p2Fast) {
            tier = 0;
         } else {
            tier = 1;
         }

         speedHistory.remove(player1.getUUID());
         speedHistory.remove(player2.getUUID());
         scheduleYawLerp(player1, player2);
         scheduleYawLerp(player2, player1);
         long effectDelay;
         if (!p1Fast && !p2Fast) {
            broadcastHighFiveAnim(player1, 3);
            broadcastHighFiveAnim(player2, 3);
            PoseNetworking.broadcastAnimState(player1, 20);
            PoseNetworking.broadcastAnimState(player2, 20);
            HighFiveNormalHandler.executeNormal(player1, player2, now);
            effectDelay = 290L;
         } else if (p1Fast && p2Fast) {
            broadcastHighFiveAnim(player1, 3);
            broadcastHighFiveAnim(player2, 3);
            PoseNetworking.broadcastAnimState(player1, 112);
            PoseNetworking.broadcastAnimState(player2, 112);
            HighFiveNormalHandler.executeFast(player1, player2, now);
            effectDelay = 210L;
         } else {
            ServerPlayer fastP = p1Fast ? player1 : player2;
            ServerPlayer stillP = p1Fast ? player2 : player1;
            broadcastHighFiveAnim(fastP, 3);
            broadcastHighFiveAnim(stillP, 3);
            PoseNetworking.broadcastAnimState(fastP, 112);
            PoseNetworking.broadcastAnimState(stillP, 20);
            HighFiveNormalHandler.executeMixed(fastP, stillP, now);
            effectDelay = 210L;
         }

         Vec3 pos1 = player1.position();
         Vec3 pos2 = player2.position();
         Vec3 highFivePos = pos1.add(pos2).scale(0.5).add(0.0, 1.4, 0.0);
         pendingEffects.put(player1.getUUID(), new HighFiveHandler.PendingHighFive(player1, player2, highFivePos, tier, now + effectDelay));
         syncHandRaised(player1, false);
         syncHandRaised(player2, false);
         HighFiveHandler.HighFiveSuccessPayload successPayload = new HighFiveHandler.HighFiveSuccessPayload(
            highFivePos.x, highFivePos.y, highFivePos.z, player1.getUUID(), player2.getUUID(), tier
         );

         for (ServerPlayer other : PlayerLookup.all(player1.level().getServer())) {
            ServerPlayNetworking.send(other, successPayload);
         }

         comboWindowStart.put(player1.getUUID(), now);
         comboWindowStart.put(player2.getUUID(), now);
         comboPartner.put(player1.getUUID(), player2.getUUID());
         comboPartner.put(player2.getUUID(), player1.getUUID());
         ServerPlayer fp1 = player1;
         ServerPlayer fp2 = player2;
         new Thread(() -> {
            try {
               Thread.sleep(250L);
            } catch (InterruptedException var3x) {
            }

            fp1.level().getServer().execute(() -> {
               ServerPlayNetworking.send(fp1, new HighFiveHandler.ComboWindowPayload(fp1.getUUID()));
               ServerPlayNetworking.send(fp2, new HighFiveHandler.ComboWindowPayload(fp2.getUUID()));
            });
         }).start();
      }
   }

   private static void executeSike(ServerPlayer siker, ServerPlayer victim) {
      long now = System.currentTimeMillis();
      UUID sikerId = siker.getUUID();
      UUID victimId = victim.getUUID();
      boolean mutualSike = sikeMode.contains(victimId);
      sikeMode.remove(victimId);
      handRaisedTime.remove(sikerId);
      handRaisedTime.remove(victimId);
      startAnimTime.remove(sikerId);
      startAnimTime.remove(victimId);
      syncHandRaised(siker, false);
      syncHandRaised(victim, false);
      if (mutualSike) {
         siker.hurtServer(siker.level(), siker.level().damageSources().generic(), 6.0F);
         victim.hurtServer(victim.level(), victim.level().damageSources().generic(), 6.0F);
         Vec3 toVictim = victim.position().subtract(siker.position()).normalize();
         if (toVictim.lengthSqr() < 0.001) {
            toVictim = new Vec3(1.0, 0.0, 0.0);
         }

         siker.push(toVictim.reverse().x * 0.6, 0.5, toVictim.reverse().z * 0.6);
         siker.syncVelocity = true;
         victim.push(toVictim.x * 0.6, 0.5, toVictim.z * 0.6);
         victim.syncVelocity = true;
         broadcastHighFiveAnim(siker, 4);
         broadcastHighFiveAnim(victim, 4);
         PoseNetworking.broadcastAnimState(siker, 63);
         PoseNetworking.broadcastAnimState(victim, 63);
         sikeStunEnd.put(sikerId, now + 1458L);
         sikeStunEnd.put(victimId, now + 1458L);

         for (ServerPlayer p : PlayerLookup.all(siker.level().getServer())) {
            ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(sikerId, true));
            ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(victimId, true));
         }

         frozenPositions.put(sikerId, siker.position());
         frozenPositions.put(victimId, victim.position());
         ServerLevel world = siker.level();
         Vec3 mid = siker.position().add(victim.position()).scale(0.5).add(0.0, 1.0, 0.0);
         world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.2F, 0.7F);
         world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.DONKEY_ANGRY, SoundSource.PLAYERS, 0.9F, 0.8F);
         world.sendParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 2, 0.3, 0.3, 0.3, 0.0);
         world.sendParticles(ParticleTypes.ANGRY_VILLAGER, mid.x, mid.y + 1.0, mid.z, 8, 0.4, 0.3, 0.4, 0.05);
         siker.sendOverlayMessage(Component.literal("§4§l\ud83d\udca5 MUTUAL SIKE! You both suffer!"));
         victim.sendOverlayMessage(Component.literal("§4§l\ud83d\udca5 MUTUAL SIKE! You both suffer!"));
         highFiveCooldown.put(sikerId, now);
         highFiveCooldown.put(victimId, now);
      } else {
         PoseNetworking.broadcastAnimState(siker, 0);
         broadcastHighFiveAnim(victim, 4);
         PoseNetworking.broadcastAnimState(victim, 63);
         sikeStunEnd.put(victimId, now + 1458L);
         frozenPositions.put(victimId, victim.position());
         victim.setDeltaMovement(Vec3.ZERO);
         victim.syncVelocity = true;

         for (ServerPlayer p : PlayerLookup.all(siker.level().getServer())) {
            ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(victimId, true));
         }

         siker.level().playSound(null, siker.getX(), siker.getY(), siker.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 1.0F, 1.0F);
         ServerLevel world = victim.level();
         Vec3 vp = victim.position().add(0.0, 1.8, 0.0);
         world.playSound(null, vp.x, vp.y, vp.z, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 0.8F);
         world.playSound(null, vp.x, vp.y, vp.z, SoundEvents.UI_TOAST_OUT, SoundSource.PLAYERS, 0.7F, 0.6F);
         world.sendParticles(ParticleTypes.FALLING_WATER, vp.x - 0.15, vp.y, vp.z, 6, 0.1, 0.05, 0.1, 0.01);
         world.sendParticles(ParticleTypes.FALLING_WATER, vp.x + 0.15, vp.y, vp.z, 6, 0.1, 0.05, 0.1, 0.01);
         world.sendParticles(ParticleTypes.SPLASH, vp.x, vp.y - 0.5, vp.z, 10, 0.2, 0.05, 0.2, 0.02);
         world.sendParticles(ParticleTypes.POOF, vp.x, vp.y + 0.3, vp.z, 8, 0.2, 0.1, 0.2, 0.03);
         world.sendParticles(ParticleTypes.ANGRY_VILLAGER, vp.x, vp.y + 0.6, vp.z, 4, 0.3, 0.2, 0.3, 0.05);
         world.sendParticles(ParticleTypes.LARGE_SMOKE, vp.x, vp.y, vp.z, 5, 0.15, 0.2, 0.15, 0.01);
         siker.sendOverlayMessage(Component.literal("§6§l\ud83d\ude02 SIKE!"));
         victim.sendOverlayMessage(Component.literal("§c§lSIKE!"));
         SikeFollowUpHandler.onSikeExecuted(siker, victim);
         highFiveCooldown.put(sikerId, now);
      }
   }

   private static void executeHighFiveEffects(ServerPlayer player1, ServerPlayer player2, Vec3 highFivePos, int tier) {
      ServerLevel world = player1.level();
      switch (tier) {
         case 0:
            executeTier0(world, highFivePos, player1, player2);
            break;
         case 1:
            executeTier1(world, highFivePos, player1, player2);
            break;
         case 2:
            executeTier2(world, highFivePos, player1, player2);
            break;
         case 3:
            executeTier3(world, highFivePos, player1, player2);
      }

      float m = 1.0F + tier * 0.45F;
      world.playSound(null, highFivePos.x, highFivePos.y, highFivePos.z, ModSounds.DAP_HIT, SoundSource.PLAYERS, 0.55F + tier * 0.15F, 1.45F - tier * 0.09F);
      world.playSound(null, highFivePos.x, highFivePos.y, highFivePos.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.45F * m, 1.5F - tier * 0.08F);
      if (tier >= 1) {
         world.playSound(
            null,
            highFivePos.x,
            highFivePos.y,
            highFivePos.z,
            ModSounds.CLAP_SOUNDS[tier % ModSounds.CLAP_SOUNDS.length],
            SoundSource.PLAYERS,
            0.5F + tier * 0.12F,
            1.2F
         );
      }

      world.sendParticles(ParticleTypes.CRIT, highFivePos.x, highFivePos.y, highFivePos.z, (int)(8.0F * m), 0.18 * m, 0.18 * m, 0.18 * m, 0.12 * m);
      world.sendParticles(ParticleTypes.ENCHANTED_HIT, highFivePos.x, highFivePos.y, highFivePos.z, (int)(5.0F * m), 0.15 * m, 0.15 * m, 0.15 * m, 0.09 * m);
      float shake = 0.35F + 0.25F * Math.max(0, tier);
      DapFlair.FlairShakePayload pulse = new DapFlair.FlairShakePayload(shake, 200);
      ServerPlayNetworking.send(player1, pulse);
      ServerPlayNetworking.send(player2, pulse);
      Random rng = new Random();
      if (rng.nextFloat() < 0.12F) {
         Object[] flav = DapFlair.flavour(rng, false);
         String label = (String)flav[0];
         if ((Boolean)flav[1]) {
            DapFlair.playRareEffect(world, highFivePos, label);
            player1.sendOverlayMessage(Component.literal(label));
            player2.sendOverlayMessage(Component.literal(label));
         }
      }

      boolean fastP1 = handRaiseSprinting.getOrDefault(player1.getUUID(), false);
      boolean fastP2 = handRaiseSprinting.getOrDefault(player2.getUUID(), false);
      if (fastP1) {
         HighFiveStreakHandler.onFastFive(player1);
      }

      if (fastP2) {
         HighFiveStreakHandler.onFastFive(player2);
      }
   }

   private static void executeTier0(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 1.1F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.8F);
      spawnStarBurst(world, pos, 10, 0.3);
      world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.08);
      world.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.02);
      applyKnockback(p1, p2, pos, 0.1);
   }

   private static void executeTier1(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5F, 1.0F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 2.0F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.8F, 1.2F);
      spawnStarBurst(world, pos, 16, 0.5);
      world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 15, 0.15, 0.15, 0.15, 0.12);
      world.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 10, 0.25, 0.25, 0.25, 0.03);
      world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0.05);
      applyKnockback(p1, p2, pos, 0.4);
   }

   private static void executeTier2(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0F, 0.9F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2F, 1.0F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.2F, 2.0F);
      spawnStarBurst(world, pos, 24, 0.7);
      world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 25, 0.2, 0.2, 0.2, 0.18);
      world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.1);
      world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
      world.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.05);
      applyKnockback(p1, p2, pos, 0.8);
   }

   private static void executeTier3(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EPIC_DAP, SoundSource.PLAYERS, 2.0F, 1.0F);
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.5F, 1.0F);
      world.playSound(null, pos.x, pos.y, pos.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.3F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.5F, 1.0F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
      spawnStarBurst(world, pos, 32, 1.0);
      world.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.5, 0.5, 0.5, 0.0);
      world.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.3, 0.3, 0.3, 0.25);
      world.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 25, 0.5, 0.5, 0.5, 0.15);
      world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), pos.x, pos.y, pos.z, 2, 0.0, 0.0, 0.0, 0.0);
      world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 20, 0.4, 0.4, 0.4, 0.1);
      world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.5, 0.5, 0.5, 0.3);
      createBattleShockwave(world, pos, p1, p2, 10.0);
      ChargedDapHandler.applyImpactFreeze(p1, p2, 3);
      applyKnockback(p1, p2, pos, 0.3);
      p1.sendOverlayMessage(Component.literal("§6§l⚡ SHOCKWAVE! ⚡"));
      p2.sendOverlayMessage(Component.literal("§6§l⚡ SHOCKWAVE! ⚡"));
   }

   private static void createBattleShockwave(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2, double radius) {
      for (int ring = 1; ring <= 5; ring++) {
         double r = ring * 2.0;
         int points = (int)(r * 8.0);

         for (int i = 0; i < points; i++) {
            double angle = Math.toRadians(360.0 / points * i);
            double x = pos.x + Math.cos(angle) * r;
            double z = pos.z + Math.sin(angle) * r;
            if (ring <= 2) {
               world.sendParticles(ParticleTypes.CLOUD, x, pos.y, z, 1, 0.1, 0.2, 0.1, 0.02);
            } else if (ring <= 4) {
               world.sendParticles(ParticleTypes.SWEEP_ATTACK, x, pos.y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
            } else {
               world.sendParticles(ParticleTypes.CRIT, x, pos.y + 0.5, z, 2, 0.1, 0.1, 0.1, 0.05);
            }
         }
      }

      AABB pushBox = new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);

      for (Entity entity : world.getEntities(null, pushBox)) {
         if (entity != p1 && entity != p2) {
            double dist = entity.position().distanceTo(pos);
            if (!(dist > radius) && !(dist < 0.5)) {
               double strength = (1.0 - dist / radius) * 4.0 + 1.0;
               Vec3 dir = entity.position().subtract(pos).normalize();
               if (dir.lengthSqr() < 0.01) {
                  dir = new Vec3(Math.random() - 0.5, 0.0, Math.random() - 0.5).normalize();
               }

               entity.push(dir.x * strength, strength * 0.6, dir.z * strength);
               entity.syncVelocity = true;
               world.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1.0, entity.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
            }
         }
      }
   }

   public static void spawnStarBurst(ServerLevel world, Vec3 pos, int rays, double spread) {
      for (int i = 0; i < rays; i++) {
         double angle = (Math.PI * 2) * i / rays;
         double dx = Math.cos(angle) * spread;
         double dz = Math.sin(angle) * spread;
         world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
      }
   }

   private static void applyKnockback(ServerPlayer p1, ServerPlayer p2, Vec3 center, double strength) {
      Vec3 dir1 = p1.position().subtract(center).normalize();
      Vec3 dir2 = p2.position().subtract(center).normalize();
      if (dir1.lengthSqr() < 0.01) {
         dir1 = new Vec3(1.0, 0.0, 0.0);
      }

      if (dir2.lengthSqr() < 0.01) {
         dir2 = new Vec3(-1.0, 0.0, 0.0);
      }

      double push = 0.15 * strength;
      p1.setDeltaMovement(dir1.x * push, 0.05, dir1.z * push);
      p2.setDeltaMovement(dir2.x * push, 0.05, dir2.z * push);
      p1.syncVelocity = true;
      p2.syncVelocity = true;
   }

   private static void createHighFiveExplosion(ServerLevel world, Vec3 pos, ServerPlayer p1, ServerPlayer p2) {
      double radius = 4.0;
      AABB damageBox = new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);

      for (Entity entity : world.getEntities(null, damageBox)) {
         if (entity != p1 && entity != p2) {
            double dist = entity.position().distanceTo(pos);
            if (!(dist > radius)) {
               double knockbackStrength = (1.0 - dist / radius) * 2.0;
               Vec3 knockDir = entity.position().subtract(pos).normalize();
               entity.push(knockDir.x * knockbackStrength, knockbackStrength * 0.5, knockDir.z * knockbackStrength);
               entity.syncVelocity = true;
               if (entity instanceof ServerPlayer target) {
                  float damage = (float)((1.0 - dist / radius) * 8.0);
                  target.hurtServer(world, world.damageSources().explosion(null, null), damage);
               }
            }
         }
      }
   }

   private static boolean isOnCooldown(UUID uuid) {
      Long cooldownStart = highFiveCooldown.get(uuid);
      return cooldownStart == null ? false : System.currentTimeMillis() - cooldownStart < 1000L;
   }

   public static boolean hasHandRaised(UUID uuid) {
      return handRaisedTime.containsKey(uuid);
   }

   public static boolean isInBlockingAnimation(UUID uuid) {
      if (highFiveAnimStart.containsKey(uuid)) {
         return true;
      } else if (endAnimTime.containsKey(uuid)) {
         return true;
      } else {
         return comboFreezeEnd.containsKey(uuid) ? true : ChargedDapHandler.isInBlockingAnimation(uuid);
      }
   }

   public static float getHighFiveAnimProgress(UUID uuid) {
      Long startTime = highFiveAnimStart.get(uuid);
      if (startTime == null) {
         return -1.0F;
      } else {
         long elapsed = System.currentTimeMillis() - startTime;
         if (elapsed > 1500L) {
            highFiveAnimStart.remove(uuid);
            return -1.0F;
         } else {
            return (float)elapsed / 1500.0F;
         }
      }
   }

   private static void onComboRequest(ServerPlayer player) {
      UUID playerId = player.getUUID();
      long now = System.currentTimeMillis();
      Long windowStart = comboWindowStart.get(playerId);
      if (windowStart != null) {
         long elapsed = now - windowStart;
         if (elapsed > 1000L) {
            comboWindowStart.remove(playerId);
            comboPartner.remove(playerId);
            comboRequested.remove(playerId);
         } else {
            comboRequested.put(playerId, now);
            UUID partnerId = comboPartner.get(playerId);
            if (partnerId != null) {
               ServerPlayer partner = player.level().getServer().getPlayerList().getPlayer(partnerId);
               if (partner != null) {
                  if (comboWindowStart.containsKey(partnerId)) {
                     if (comboRequested.containsKey(partnerId)) {
                        executeCombo(player, partner);
                     }
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
      comboWindowStart.remove(id1);
      comboWindowStart.remove(id2);
      comboPartner.remove(id1);
      comboPartner.remove(id2);
      comboRequested.remove(id1);
      comboRequested.remove(id2);
      handRaisedTime.remove(id1);
      handRaisedTime.remove(id2);
      startAnimTime.remove(id1);
      startAnimTime.remove(id2);
      syncHandRaised(p1, false);
      syncHandRaised(p2, false);
      System.out.println("[HighFive] Combo started - cleared hand raised for both players");
      comboFreezeEnd.put(id1, now + 2250L);
      comboFreezeEnd.put(id2, now + 2250L);
      frozenPositions.put(id1, p1.position());
      frozenPositions.put(id2, p2.position());
      p1.setDeltaMovement(Vec3.ZERO);
      p2.setDeltaMovement(Vec3.ZERO);
      p1.syncVelocity = true;
      p2.syncVelocity = true;

      for (ServerPlayer p : PlayerLookup.all(p1.level().getServer())) {
         ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(id1, true));
         ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(id2, true));
      }

      PoseNetworking.broadcastAnimState(p1, 21);
      PoseNetworking.broadcastAnimState(p2, 21);
      pendingComboImpacts.put(id1, new HighFiveHandler.ComboImpact(p1, p2, now + 1290L));
      p1.sendOverlayMessage(Component.literal("§6§l✨ COMBO! ✨"));
      p2.sendOverlayMessage(Component.literal("§6§l✨ COMBO! ✨"));
   }

   private static void executeSecondImpact(ServerPlayer p1, ServerPlayer p2) {
      Vec3 pos = p1.position().add(p2.position()).scale(0.5).add(0.0, 0.5, 0.0);
      ServerLevel world = p1.level();
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_WEAK, SoundSource.PLAYERS, 1.0F, 1.0F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5F, 1.0F);
      world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.3, 0.3, 0.3, 0.1);
      world.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 20, 0.3, 0.3, 0.3, 0.15);
      p1.sendOverlayMessage(Component.literal("§e⚡ PERFECT! ⚡"));
      p2.sendOverlayMessage(Component.literal("§e⚡ PERFECT! ⚡"));
   }

   private static void spawnComboAura(ServerLevel world, Vec3 pos1, Vec3 pos2) {
      int particleCount = 20;
      double radius = 1.5;

      for (int i = 0; i < particleCount; i++) {
         double angle = (Math.PI * 2) * i / particleCount;
         double x1 = pos1.x + Math.cos(angle) * radius;
         double z1 = pos1.z + Math.sin(angle) * radius;
         world.sendParticles(ParticleTypes.SQUID_INK, x1, pos1.y + 1.0, z1, 1, 0.1, 0.3, 0.1, 0.02);
         double innerRadius = radius * 0.7;
         double x1Inner = pos1.x + Math.cos(angle) * innerRadius;
         double z1Inner = pos1.z + Math.sin(angle) * innerRadius;
         world.sendParticles(ParticleTypes.END_ROD, x1Inner, pos1.y + 1.0, z1Inner, 1, 0.1, 0.3, 0.1, 0.02);
         double x2 = pos2.x + Math.cos(angle) * radius;
         double z2 = pos2.z + Math.sin(angle) * radius;
         world.sendParticles(ParticleTypes.SQUID_INK, x2, pos2.y + 1.0, z2, 1, 0.1, 0.3, 0.1, 0.02);
         double x2Inner = pos2.x + Math.cos(angle) * innerRadius;
         double z2Inner = pos2.z + Math.sin(angle) * innerRadius;
         world.sendParticles(ParticleTypes.END_ROD, x2Inner, pos2.y + 1.0, z2Inner, 1, 0.1, 0.3, 0.1, 0.02);
      }
   }

   public static boolean isInComboFreeze(UUID playerId) {
      Long freezeEnd = comboFreezeEnd.get(playerId);
      return freezeEnd == null ? false : System.currentTimeMillis() < freezeEnd;
   }

   public static void cleanup(UUID playerId) {
      handRaisedTime.remove(playerId);
      highFiveCooldown.remove(playerId);
      highFiveAnimStart.remove(playerId);
      startAnimTime.remove(playerId);
      endAnimTime.remove(playerId);
      speedHistory.remove(playerId);
      pendingEffects.remove(playerId);
      comboWindowStart.remove(playerId);
      comboPartner.remove(playerId);
      comboRequested.remove(playerId);
      comboFreezeEnd.remove(playerId);
      pendingComboImpacts.remove(playerId);
      frozenPositions.remove(playerId);
      sikeMode.remove(playerId);
      sikeStunEnd.remove(playerId);
      sikeSlowEnd.remove(playerId);
   }

   private static void applySikeSlow(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(SIKE_SLOW_ID);
         attr.addTransientModifier(new AttributeModifier(SIKE_SLOW_ID, -1.0, Operation.ADD_MULTIPLIED_TOTAL));
      }
   }

   private static void removeSikeSlow(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(SIKE_SLOW_ID);
      }
   }

   private static void executeMutualSike(ServerPlayer p1, ServerPlayer p2) {
      long now = System.currentTimeMillis();

      for (ServerPlayer p : List.of(p1, p2)) {
         UUID id = p.getUUID();
         handRaisedTime.remove(id);
         startAnimTime.remove(id);
         syncHandRaised(p, false);
         PoseNetworking.broadcastAnimState(p, 0);
         highFiveCooldown.put(id, now);
      }

      Vec3 toP2 = p2.position().subtract(p1.position()).normalize();
      if (toP2.lengthSqr() < 0.01) {
         toP2 = new Vec3(1.0, 0.0, 0.0);
      }

      ServerLevel world = p1.level();
      p1.hurtServer(world, world.damageSources().magic(), 6.0F);
      p2.hurtServer(world, world.damageSources().magic(), 6.0F);
      p1.setDeltaMovement(toP2.reverse().scale(0.65).add(0.0, 0.5, 0.0));
      p1.syncVelocity = true;
      p2.setDeltaMovement(toP2.scale(0.65).add(0.0, 0.5, 0.0));
      p2.syncVelocity = true;
      Vec3 mid = p1.position().add(p2.position()).scale(0.5).add(0.0, 1.0, 0.0);
      world.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 24, 0.4, 0.4, 0.4, 0.2);
      world.sendParticles(ParticleTypes.SMOKE, mid.x, mid.y, mid.z, 12, 0.3, 0.3, 0.3, 0.02);
      world.sendParticles(ParticleTypes.FALLING_WATER, mid.x, mid.y + 0.5, mid.z, 20, 0.3, 0.2, 0.3, 0.02);
      world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.7F);
      world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 0.8F);
      world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.8F, 1.2F);
      p1.sendOverlayMessage(Component.literal("§c§l\ud83d\udca5 MUTUAL SIKE! You both played dirty!"));
      p2.sendOverlayMessage(Component.literal("§c§l\ud83d\udca5 MUTUAL SIKE! You both played dirty!"));
   }

   private static class BeaconRemoval {
      BlockPos beaconPos;
      BlockPos glassPos;
      ServerLevel world;
      long removeTime;

      BeaconRemoval(BlockPos beaconPos, BlockPos glassPos, ServerLevel world, long removeTime) {
         this.beaconPos = beaconPos;
         this.glassPos = glassPos;
         this.world = world;
         this.removeTime = removeTime;
      }
   }

   private static class ComboImpact {
      ServerPlayer p1;
      ServerPlayer p2;
      long impactTime;

      ComboImpact(ServerPlayer p1, ServerPlayer p2, long impactTime) {
         this.p1 = p1;
         this.p2 = p2;
         this.impactTime = impactTime;
      }
   }

   public record ComboRequestPayload() implements CustomPacketPayload {
      public static final Type<HighFiveHandler.ComboRequestPayload> ID = new Type(HighFiveHandler.COMBO_REQUEST_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.ComboRequestPayload> CODEC = StreamCodec.unit(new HighFiveHandler.ComboRequestPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ComboWindowClosePayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<HighFiveHandler.ComboWindowClosePayload> ID = new Type(HighFiveHandler.COMBO_WINDOW_CLOSE_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.ComboWindowClosePayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> buf.writeUUID(payload.playerId), buf -> new HighFiveHandler.ComboWindowClosePayload(buf.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ComboWindowPayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<HighFiveHandler.ComboWindowPayload> ID = new Type(HighFiveHandler.COMBO_WINDOW_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.ComboWindowPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> buf.writeUUID(payload.playerId), buf -> new HighFiveHandler.ComboWindowPayload(buf.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FreezeStatePayload(UUID playerId, boolean frozen) implements CustomPacketPayload {
      public static final Type<HighFiveHandler.FreezeStatePayload> ID = new Type(HighFiveHandler.FREEZE_STATE_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.FreezeStatePayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.playerId);
         buf.writeBoolean(payload.frozen);
      }, buf -> new HighFiveHandler.FreezeStatePayload(buf.readUUID(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HandRaisedSyncPayload(UUID playerId, boolean raised) implements CustomPacketPayload {
      public static final Type<HighFiveHandler.HandRaisedSyncPayload> ID = new Type(HighFiveHandler.HAND_RAISED_SYNC_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.HandRaisedSyncPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.playerId);
         buf.writeBoolean(payload.raised);
      }, buf -> new HighFiveHandler.HandRaisedSyncPayload(buf.readUUID(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HighFiveAnimPayload(UUID playerId, int animState) implements CustomPacketPayload {
      public static final Type<HighFiveHandler.HighFiveAnimPayload> ID = new Type(HighFiveHandler.HIGH_FIVE_ANIM_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.HighFiveAnimPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.playerId);
         buf.writeInt(payload.animState);
      }, buf -> new HighFiveHandler.HighFiveAnimPayload(buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HighFiveHoldPayload() implements CustomPacketPayload {
      public static final Type<HighFiveHandler.HighFiveHoldPayload> ID = new Type(HighFiveHandler.HIGH_FIVE_HOLD_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.HighFiveHoldPayload> CODEC = StreamCodec.unit(new HighFiveHandler.HighFiveHoldPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HighFiveRequestPayload() implements CustomPacketPayload {
      public static final Type<HighFiveHandler.HighFiveRequestPayload> ID = new Type(HighFiveHandler.HIGH_FIVE_REQUEST_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.HighFiveRequestPayload> CODEC = StreamCodec.unit(
         new HighFiveHandler.HighFiveRequestPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HighFiveSuccessPayload(double x, double y, double z, UUID player1, UUID player2, int tier) implements CustomPacketPayload {
      public static final Type<HighFiveHandler.HighFiveSuccessPayload> ID = new Type(HighFiveHandler.HIGH_FIVE_SUCCESS_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.HighFiveSuccessPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeDouble(payload.x);
         buf.writeDouble(payload.y);
         buf.writeDouble(payload.z);
         buf.writeUUID(payload.player1);
         buf.writeUUID(payload.player2);
         buf.writeInt(payload.tier);
      }, buf -> new HighFiveHandler.HighFiveSuccessPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUUID(), buf.readUUID(), buf.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static class ParticleBeam {
      ServerLevel world;
      Vec3 startPos;
      boolean isYellow;
      long endTime;

      ParticleBeam(ServerLevel world, Vec3 startPos, boolean isYellow, long endTime) {
         this.world = world;
         this.startPos = startPos;
         this.isYellow = isYellow;
         this.endTime = endTime;
      }
   }

   private static class PendingHighFive {
      ServerPlayer p1;
      ServerPlayer p2;
      Vec3 pos;
      int tier;
      long effectTime;

      PendingHighFive(ServerPlayer p1, ServerPlayer p2, Vec3 pos, int tier, long effectTime) {
         this.p1 = p1;
         this.p2 = p2;
         this.pos = pos;
         this.tier = tier;
         this.effectTime = effectTime;
      }
   }

   public record SikeRequestPayload() implements CustomPacketPayload {
      public static final Type<HighFiveHandler.SikeRequestPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "highfive_sike_request"));
      public static final StreamCodec<FriendlyByteBuf, HighFiveHandler.SikeRequestPayload> CODEC = StreamCodec.unit(new HighFiveHandler.SikeRequestPayload());

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
