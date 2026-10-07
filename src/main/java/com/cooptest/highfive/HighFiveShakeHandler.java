package com.cooptest.highfive;

import com.cooptest.HighFiveHandler;
import com.cooptest.PoseNetworking;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class HighFiveShakeHandler {
   public static final long ARM_TIMEOUT_MS = 7000L;
   public static final long SHAKE_WINDOW_MS = 600L;
   public static final long WINDOW_DEAD_ZONE_MS = 70L;
   public static final long WINDOW_REST_MS = 120L;
   public static final long WINDOW_SHRINK_PER_STREAK_MS = 6L;
   public static final long WINDOW_MIN_MS = 250L;
   public static final long MIN_PRESS_GAP_MS = 200L;
   public static final long PERFECT_WINDOW_MS = 300L;
   public static final long INACTIVITY_TIMEOUT_MS = 2000L;
   public static final int MAX_CONSECUTIVE_CLASHES = 2;
   public static final long MAX_STALL_MS = 8000L;
   public static final double HANDSHAKE_FACE_DISTANCE = 1.3;
   public static final long HANDSHAKE_POSITION_MS = 250L;
   public static final double HANDSHAKE_MAX_REPOSITION = 2.0;
   public static final double FISTBUMP_PUSHBACK = 0.8;
   public static final double FISTBUMP_PUSHFORWARD = 0.5;
   public static final long FISTBUMP_BACK_MS = 250L;
   public static final long FISTBUMP_FWD_MS = 500L;
   public static final long FISTBUMP_RETURN_MS = 630L;
   public static final double ARMDAP_PUSH_CLOSER = 0.4;
   public static final long ARMDAP_CLOSER_MS = 530L;
   public static final long ARMDAP_RETURN_MS = 830L;
   public static final double PUSH_APART_STRENGTH = 0.35;
   public static final float CLASH_SHAKE_AMOUNT = 0.5F;
   public static final long CLASH_SHAKE_MS = 150L;
   public static final float AMBIENT_SHAKE_AMOUNT = 0.08F;
   public static final long AMBIENT_SHAKE_REFRESH_MS = 100L;
   public static final float IMPACT_SHAKE_AMOUNT = 0.35F;
   public static final long IMPACT_SHAKE_MS = 120L;
   private static final Map<UUID, HighFiveShakeHandler.Dir> heldDirection = new HashMap<>();
   private static final Map<UUID, HighFiveShakeHandler.Dir> prevRawDirection = new HashMap<>();
   private static final Map<UUID, HighFiveShakeHandler.Dir> lockedDirection = new HashMap<>();
   private static final Map<UUID, HighFiveShakeHandler.Dir> bankedPress = new HashMap<>();
   private static final Map<UUID, Long> lastAcceptedPressTime = new HashMap<>();
   private static final Map<UUID, Deque<HighFiveShakeHandler.Dir>> recentInputs = new HashMap<>();
   private static final Map<UUID, Boolean> handshakeArmed = new HashMap<>();
   private static final Map<UUID, Long> armedAt = new HashMap<>();
   private static final Map<UUID, UUID> activeSession = new HashMap<>();
   private static final Map<UUID, HighFiveShakeHandler.Phase> sessionPhase = new HashMap<>();
   private static final Map<UUID, Long> windowOpenedAt = new HashMap<>();
   private static final Map<UUID, Long> animStartedAt = new HashMap<>();
   private static final Map<UUID, Long> animDurationMs = new HashMap<>();
   private static final Map<UUID, Integer> streakCount = new HashMap<>();
   private static final Map<UUID, Integer> consecutiveClashes = new HashMap<>();
   private static final Map<UUID, Long> lastRealInputTime = new HashMap<>();
   private static final List<HighFiveShakeHandler.PendingRepos> pendingRepos = new ArrayList<>();
   private static final List<HighFiveShakeHandler.PendingRepos> pendingReturn = new ArrayList<>();
   public static final Identifier SHAKE_DIR_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_dir");
   public static final Identifier SHAKE_END_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_end_key");
   public static final Identifier SHAKE_ARM_TOGGLE_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_arm_toggle");
   public static final Identifier SHAKE_ARMED_STATE_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_armed_state");
   public static final Identifier SHAKE_SESSION_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_session");
   public static final Identifier SHAKE_RESULT_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_result");
   public static final Identifier SHAKE_KEYPRESS_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_keypress");
   public static final Identifier SHAKE_ANIM_START_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_anim_start");
   public static final Identifier SHAKE_IMPACT_ID = Identifier.fromNamespaceAndPath("cooptest", "shake_impact");

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(HighFiveShakeHandler.ShakeDirPayload.ID, HighFiveShakeHandler.ShakeDirPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HighFiveShakeHandler.ShakeEndKeyPayload.ID, HighFiveShakeHandler.ShakeEndKeyPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HighFiveShakeHandler.ShakeArmTogglePayload.ID, HighFiveShakeHandler.ShakeArmTogglePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveShakeHandler.ShakeArmedStatePayload.ID, HighFiveShakeHandler.ShakeArmedStatePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveShakeHandler.ShakeSessionPayload.ID, HighFiveShakeHandler.ShakeSessionPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveShakeHandler.ShakeResultPayload.ID, HighFiveShakeHandler.ShakeResultPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveShakeHandler.ShakeKeyPressPayload.ID, HighFiveShakeHandler.ShakeKeyPressPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveShakeHandler.ShakeAnimStartPayload.ID, HighFiveShakeHandler.ShakeAnimStartPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HighFiveShakeHandler.ShakeImpactPayload.ID, HighFiveShakeHandler.ShakeImpactPayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(HighFiveShakeHandler.ShakeDirPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         UUID id = player.getUUID();
         HighFiveShakeHandler.Dir dir = HighFiveShakeHandler.Dir.values()[payload.dirOrdinal()];
         context.server().execute(() -> onDirReceived(player, id, dir));
      });
      ServerPlayNetworking.registerGlobalReceiver(
         HighFiveShakeHandler.ShakeEndKeyPayload.ID, (payload, context) -> context.server().execute(() -> endHandshake(context.player(), "F pressed", false))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         HighFiveShakeHandler.ShakeArmTogglePayload.ID, (payload, context) -> context.server().execute(() -> toggleArmed(context.player()))
      );
      ServerTickEvents.END_SERVER_TICK.register(HighFiveShakeHandler::tick);
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> cleanup(server, handler.getPlayer().getUUID()));
   }

   private static void onDirReceived(ServerPlayer player, UUID id, HighFiveShakeHandler.Dir dir) {
      heldDirection.put(id, dir);
      if (dir != HighFiveShakeHandler.Dir.NONE) {
         lastRealInputTime.put(id, System.currentTimeMillis());
      }

      HighFiveShakeHandler.Dir prev = prevRawDirection.getOrDefault(id, HighFiveShakeHandler.Dir.NONE);
      boolean risingEdge = dir != HighFiveShakeHandler.Dir.NONE && prev == HighFiveShakeHandler.Dir.NONE;
      prevRawDirection.put(id, dir);
      if (risingEdge) {
         if (activeSession.containsKey(id)) {
            long now = System.currentTimeMillis();
            long lastAccepted = lastAcceptedPressTime.getOrDefault(id, 0L);
            if (now - lastAccepted >= 200L) {
               Deque<HighFiveShakeHandler.Dir> recent = recentInputs.computeIfAbsent(id, k -> new ArrayDeque<>());
               if (recent.size() >= 2) {
                  HighFiveShakeHandler.Dir[] last = recent.toArray(new HighFiveShakeHandler.Dir[0]);
                  if (last[0] == dir && last[1] == dir) {
                     player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.2F);
                     System.out.println("[Handshake] " + player.getName().getString() + " rejected 3x same button: " + dir);
                     return;
                  }
               }

               lastAcceptedPressTime.put(id, now);
               HighFiveShakeHandler.Phase phase = sessionPhase.getOrDefault(id, HighFiveShakeHandler.Phase.IDLE);
               if (phase == HighFiveShakeHandler.Phase.IDLE) {
                  Long openedAt = getWindowOpenedAt(id);
                  if (openedAt != null && now - openedAt >= 70L) {
                     boolean wasEmpty = !lockedDirection.containsKey(id);
                     lockedDirection.putIfAbsent(id, dir);
                     if (wasEmpty) {
                        broadcastKeyPress(player, dir);
                     }
                  }
               } else if (phase == HighFiveShakeHandler.Phase.ANIMATING && !bankedPress.containsKey(id)) {
                  bankedPress.put(id, dir);
                  broadcastKeyPress(player, dir);
                  UUID partnerId = activeSession.get(id);
                  if (partnerId != null && !bankedPress.containsKey(partnerId)) {
                     player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 1.4F);
                  }
               }
            }
         }
      }
   }

   private static void toggleArmed(ServerPlayer player) {
      UUID id = player.getUUID();
      System.out
         .println(
            "[Handshake-ARM] toggle from "
               + player.getName().getString()
               + " | handRaised="
               + HighFiveHandler.handRaisedTime.containsKey(id)
               + " | inSession="
               + activeSession.containsKey(id)
               + " | currently armed="
               + handshakeArmed.getOrDefault(id, false)
         );
      if (HighFiveHandler.handRaisedTime.containsKey(id) && !activeSession.containsKey(id)) {
         boolean newState = !handshakeArmed.getOrDefault(id, false);
         handshakeArmed.put(id, newState);
         if (newState) {
            armedAt.put(id, System.currentTimeMillis());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.6F);
         } else {
            armedAt.remove(id);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 0.9F);
         }

         System.out.println("[Handshake-ARM] now " + newState);
         ServerPlayNetworking.send(player, new HighFiveShakeHandler.ShakeArmedStatePayload(newState));
      } else {
         System.out.println("[Handshake-ARM] BLOCKED");
      }
   }

   private static void disarm(UUID id) {
      handshakeArmed.remove(id);
      armedAt.remove(id);
   }

   public static void onHandLowered(UUID id) {
      disarm(id);
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      drainRepos(pendingRepos);
      drainRepos(pendingReturn);
      new HashMap<>(armedAt).forEach((id, t) -> {
         if (activeSession.containsKey(id)) {
            armedAt.remove(id);
         } else {
            if (now - t >= 7000L) {
               disarm(id);
               ServerPlayer p = server.getPlayerList().getPlayer(id);
               if (p != null) {
                  ServerPlayNetworking.send(p, new HighFiveShakeHandler.ShakeArmedStatePayload(false));
                  p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 0.7F);
               }
            }
         }
      });

      for (ServerPlayer p1 : server.getPlayerList().getPlayers()) {
         UUID id1 = p1.getUUID();
         if (!activeSession.containsKey(id1) && handshakeArmed.getOrDefault(id1, false)) {
            for (ServerPlayer p2 : server.getPlayerList().getPlayers()) {
               if (p2 != p1) {
                  UUID id2 = p2.getUUID();
                  if (!activeSession.containsKey(id2) && handshakeArmed.getOrDefault(id2, false) && HighFiveHandler.reachPointsOverlap(p1, p2)) {
                     startHandshake(p1, p2, now);
                  }
               }
            }
         }
      }

      for (Entry<UUID, UUID> entry : new HashMap<>(activeSession).entrySet()) {
         UUID sk = entry.getKey();
         UUID pk = entry.getValue();
         if (activeSession.get(pk) != null && activeSession.get(pk).equals(sk) && sk.compareTo(pk) < 0) {
            ServerPlayer p1 = server.getPlayerList().getPlayer(sk);
            ServerPlayer p2 = server.getPlayerList().getPlayer(pk);
            if (p1 != null && p2 != null) {
               long li1 = lastRealInputTime.getOrDefault(sk, now);
               long li2 = lastRealInputTime.getOrDefault(pk, now);
               if (now - Math.max(li1, li2) > 8000L) {
                  endHandshakeByKey(server, sk, "watchdog", false);
               } else {
                  HighFiveShakeHandler.Phase phase = sessionPhase.getOrDefault(sk, HighFiveShakeHandler.Phase.IDLE);
                  switch (phase) {
                     case IDLE:
                        tickIdle(server, p1, p2, sk, pk, now);
                        break;
                     case ANIMATING:
                        tickAnimating(server, p1, p2, sk, pk, now);
                  }
               }
            } else {
               endHandshakeByKey(server, sk, "player left", false);
            }
         }
      }
   }

   private static void tickIdle(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, UUID sk, UUID pk, long now) {
      Long openedAt = windowOpenedAt.get(sk);
      if (openedAt != null) {
         long li1 = lastRealInputTime.getOrDefault(sk, now);
         long li2 = lastRealInputTime.getOrDefault(pk, now);
         if (now - Math.max(li1, li2) > 2000L) {
            endHandshakeByKey(server, sk, "inactivity", true);
         } else {
            HighFiveShakeHandler.Dir d1 = lockedDirection.getOrDefault(sk, HighFiveShakeHandler.Dir.NONE);
            HighFiveShakeHandler.Dir d2 = lockedDirection.getOrDefault(pk, HighFiveShakeHandler.Dir.NONE);
            boolean bothChose = d1 != HighFiveShakeHandler.Dir.NONE && d2 != HighFiveShakeHandler.Dir.NONE;
            int streak = streakCount.getOrDefault(sk, 0);
            long effectiveWindow = Math.max(250L, 600L - streak * 6L);
            boolean timedOut = now - openedAt >= effectiveWindow;
            if (bothChose || timedOut) {
               resolveMove(server, p1, p2, sk, pk, d1, d2, false, now);
            }
         }
      }
   }

   private static void tickAnimating(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, UUID sk, UUID pk, long now) {
      Long startMs = animStartedAt.get(sk);
      Long durMs = animDurationMs.get(sk);
      if (startMs != null && durMs != null) {
         if (now - startMs >= durMs) {
            HighFiveShakeHandler.Dir b1 = bankedPress.getOrDefault(sk, HighFiveShakeHandler.Dir.NONE);
            HighFiveShakeHandler.Dir b2 = bankedPress.getOrDefault(pk, HighFiveShakeHandler.Dir.NONE);
            boolean bothBanked = b1 != HighFiveShakeHandler.Dir.NONE && b2 != HighFiveShakeHandler.Dir.NONE;
            animStartedAt.remove(sk);
            animDurationMs.remove(sk);
            bankedPress.remove(sk);
            bankedPress.remove(pk);
            if (bothBanked) {
               resolveMove(server, p1, p2, sk, pk, b1, b2, true, now);
            } else {
               enterIdle(p1, p2, sk, pk, now);
               if (b1 != HighFiveShakeHandler.Dir.NONE) {
                  lockedDirection.put(sk, b1);
                  broadcastKeyPress(p1, b1);
               }

               if (b2 != HighFiveShakeHandler.Dir.NONE) {
                  lockedDirection.put(pk, b2);
                  broadcastKeyPress(p2, b2);
               }
            }
         }
      }
   }

   private static void resolveMove(
      MinecraftServer server,
      ServerPlayer p1,
      ServerPlayer p2,
      UUID sk,
      UUID pk,
      HighFiveShakeHandler.Dir d1,
      HighFiveShakeHandler.Dir d2,
      boolean perfect,
      long now
   ) {
      int[] combo = lookupCombo(d1, d2);
      int streak = streakCount.getOrDefault(sk, 0);
      boolean success = combo != null;
      lockedDirection.remove(sk);
      lockedDirection.remove(pk);
      if (success) {
         streakCount.put(sk, ++streak);
         consecutiveClashes.put(sk, 0);
         trackInput(sk, d1);
         trackInput(pk, d2);
         System.out
            .println(
               "[Handshake] MOVE #"
                  + streak
                  + ": "
                  + p1.getName().getString()
                  + "="
                  + d1
                  + " + "
                  + p2.getName().getString()
                  + "="
                  + d2
                  + (perfect ? " PERFECT" : "")
                  + " (p1="
                  + combo[1]
                  + " p2="
                  + combo[2]
                  + ")"
            );
         long dur = combo[3];
         sessionPhase.put(sk, HighFiveShakeHandler.Phase.ANIMATING);
         sessionPhase.put(pk, HighFiveShakeHandler.Phase.ANIMATING);
         animStartedAt.put(sk, now);
         animDurationMs.put(sk, dur);
         PoseNetworking.broadcastAnimState(p1, combo[1]);
         PoseNetworking.broadcastAnimState(p2, combo[2]);
         HighFiveShakeHandler.ShakeAnimStartPayload asp = new HighFiveShakeHandler.ShakeAnimStartPayload(dur, now);
         ServerPlayNetworking.send(p1, asp);
         ServerPlayNetworking.send(p2, asp);
         scheduleImpacts(p1, p2, combo[1], dur, now);
         HighFiveShakeHandler.ShakeResultPayload result = new HighFiveShakeHandler.ShakeResultPayload(true, perfect, streak);
         ServerPlayNetworking.send(p1, result);
         ServerPlayNetworking.send(p2, result);
      } else {
         int clashes = consecutiveClashes.getOrDefault(sk, 0) + 1;
         consecutiveClashes.put(sk, clashes);
         System.out.println("[Handshake] CLASH (" + clashes + " in a row, d1=" + d1 + " d2=" + d2 + ")");
         HighFiveShakeHandler.ShakeResultPayload result = new HighFiveShakeHandler.ShakeResultPayload(false, false, streak);
         ServerPlayNetworking.send(p1, result);
         ServerPlayNetworking.send(p2, result);
         if (clashes >= 2) {
            endHandshakeByKey(server, sk, "2 clashes", true);
            return;
         }

         enterIdle(p1, p2, sk, pk, now);
      }
   }

   private static void enterIdle(ServerPlayer p1, ServerPlayer p2, UUID sk, UUID pk, long now) {
      sessionPhase.put(sk, HighFiveShakeHandler.Phase.IDLE);
      sessionPhase.put(pk, HighFiveShakeHandler.Phase.IDLE);
      PoseNetworking.broadcastAnimState(p1, 114);
      PoseNetworking.broadcastAnimState(p2, 114);
      windowOpenedAt.put(sk, now + 120L);
   }

   private static void scheduleImpacts(ServerPlayer p1, ServerPlayer p2, int p1Ordinal, long animDurMs, long now) {
      Vec3 toP2 = p2.position().subtract(p1.position());
      double dist = Math.hypot(toP2.x, toP2.z);
      if (!(dist < 0.01)) {
         Vec3 dir = new Vec3(toP2.x / dist, 0.0, toP2.z / dist);
         switch (p1Ordinal) {
            case 116:
            case 117:
               scheduleRepoPair(p1, p2, dir.scale(0.2), dir.reverse().scale(0.2), now, 4);
               scheduleImpactEffects(p1, p2, now + 540L);
               scheduleRepoPair(p1, p2, dir.reverse().scale(0.2), dir.scale(0.2), now + 710L, 3);
               scheduleRepoPair(p1, p2, dir.scale(0.2), dir.reverse().scale(0.2), now + 1000L, 3);
               scheduleImpactEffects(p1, p2, now + 1250L);
               scheduleRepoPair(p1, p2, dir.reverse().scale(0.2), dir.scale(0.2), now + 1420L, 4);
               break;
            case 118:
               scheduleRepoPair(p1, p2, dir.reverse().scale(0.3), dir.scale(0.3), now + 460L, 4);
               scheduleRepoPair(p1, p2, dir.scale(0.2), dir.reverse().scale(0.2), now + 500L, 7);
               scheduleImpactEffects(p1, p2, now + 630L);
               scheduleRepoPair(p1, p2, dir.reverse().scale(0.2), dir.scale(0.2), now + 790L, 8);
               break;
            case 119:
               scheduleRepoPair(p1, p2, dir.scale(0.2), dir.reverse().scale(0.2), now + 880L, 4);
               scheduleImpactEffects(p1, p2, now + 1000L);
               scheduleRepoPair(p1, p2, dir.reverse().scale(0.2), dir.scale(0.2), now + 1170L, 5);
         }
      }
   }

   private static void scheduleRepoPair(ServerPlayer p1, ServerPlayer p2, Vec3 delta1, Vec3 delta2, long atMs, int ticks) {
      long delay = Math.max(0L, atMs - System.currentTimeMillis());
      new Thread(() -> {
         try {
            Thread.sleep(delay);
         } catch (InterruptedException var8) {
         }

         p1.level().getServer().execute(() -> {
            pendingRepos.add(new HighFiveShakeHandler.PendingRepos(p1, delta1, ticks));
            pendingRepos.add(new HighFiveShakeHandler.PendingRepos(p2, delta2, ticks));
         });
      }).start();
   }

   private static void scheduleImpactEffects(ServerPlayer p1, ServerPlayer p2, long atMs) {
      long delay = Math.max(0L, atMs - System.currentTimeMillis());
      new Thread(() -> {
         try {
            Thread.sleep(delay);
         } catch (InterruptedException var5) {
         }

         p1.level().getServer().execute(() -> {
            if (activeSession.containsKey(p1.getUUID())) {
               double mx = (p1.getX() + p2.getX()) / 2.0;
               double my = (p1.getY() + p2.getY()) / 2.0 + 1.2;
               double mz = (p1.getZ() + p2.getZ()) / 2.0;
               p1.level().playSound(null, mx, my, mz, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
               HighFiveShakeHandler.ShakeImpactPayload shakePayload = new HighFiveShakeHandler.ShakeImpactPayload(0.35F, 120L);
               ServerPlayNetworking.send(p1, shakePayload);
               ServerPlayNetworking.send(p2, shakePayload);
            }
         });
      }).start();
   }

   private static void startHandshake(ServerPlayer p1, ServerPlayer p2, long now) {
      if (p1.getUUID().compareTo(p2.getUUID()) > 0) {
         ServerPlayer tmp = p1;
         p1 = p2;
         p2 = tmp;
      }

      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      activeSession.put(id1, id2);
      activeSession.put(id2, id1);
      streakCount.put(id1, 0);
      consecutiveClashes.put(id1, 0);
      sessionPhase.put(id1, HighFiveShakeHandler.Phase.STARTING);
      sessionPhase.put(id2, HighFiveShakeHandler.Phase.STARTING);
      disarm(id1);
      disarm(id2);
      lastRealInputTime.put(id1, now);
      lastRealInputTime.put(id2, now);
      System.out.println("[Handshake] STARTED " + p1.getName().getString() + " + " + p2.getName().getString());
      ServerPlayNetworking.send(p1, new HighFiveShakeHandler.ShakeSessionPayload(true));
      ServerPlayNetworking.send(p2, new HighFiveShakeHandler.ShakeSessionPayload(true));
      positionFacingEachOther(p1, p2);
      PoseNetworking.broadcastAnimState(p1, 113);
      PoseNetworking.broadcastAnimState(p2, 113);

      for (UUID id : new UUID[]{id1, id2}) {
         HighFiveHandler.handRaisedTime.remove(id);
         HighFiveHandler.startAnimTime.remove(id);
         HighFiveHandler.lastHoldRefresh.remove(id);
         HighFiveHandler.handRaiseSprinting.remove(id);
      }

      HighFiveHandler.syncHandRaised(p1, false);
      HighFiveHandler.syncHandRaised(p2, false);
      ServerPlayer fp1 = p1;
      ServerPlayer fp2 = p2;
      UUID fid1 = id1;
      new Thread(() -> {
         try {
            Thread.sleep(350L);
         } catch (InterruptedException var4x) {
         }

         fp1.level().getServer().execute(() -> {
            if (activeSession.containsKey(fid1)) {
               PoseNetworking.broadcastAnimState(fp1, 114);
               PoseNetworking.broadcastAnimState(fp2, 114);
               sessionPhase.put(fid1, HighFiveShakeHandler.Phase.IDLE);
               sessionPhase.put(activeSession.get(fid1), HighFiveShakeHandler.Phase.IDLE);
               windowOpenedAt.put(fid1, System.currentTimeMillis() + 120L);
            }
         });
      }).start();
      windowOpenedAt.put(id1, now);
   }

   private static void positionFacingEachOther(ServerPlayer p1, ServerPlayer p2) {
      Vec3 pos1 = p1.position();
      Vec3 pos2 = p2.position();
      Vec3 toP2 = pos2.subtract(pos1);
      double dist = Math.hypot(toP2.x, toP2.z);
      if (dist < 0.01) {
         toP2 = HighFiveHandler.horizontalForward(p1);
         dist = 1.0;
      }

      Vec3 dir = new Vec3(toP2.x / dist, 0.0, toP2.z / dist);
      double mid_x = (pos1.x + pos2.x) / 2.0;
      double mid_z = (pos1.z + pos2.z) / 2.0;
      double half = 0.65;
      Vec3 t1 = new Vec3(mid_x - dir.x * half, pos1.y, mid_z - dir.z * half);
      Vec3 t2 = new Vec3(mid_x + dir.x * half, pos2.y, mid_z + dir.z * half);
      int ticks = (int)Math.max(1L, 5L);
      if (t1.distanceTo(pos1) <= 2.0) {
         pendingRepos.add(new HighFiveShakeHandler.PendingRepos(p1, t1.subtract(pos1), ticks));
      }

      if (t2.distanceTo(pos2) <= 2.0) {
         pendingRepos.add(new HighFiveShakeHandler.PendingRepos(p2, t2.subtract(pos2), ticks));
      }

      scheduleYawLerp(p1, p2, ticks);
      scheduleYawLerp(p2, p1, ticks);
   }

   private static void scheduleYawLerp(ServerPlayer player, ServerPlayer target, int ticks) {
      Vec3 to = target.position().subtract(player.position());
      float targetYaw = (float)Math.toDegrees(Math.atan2(-to.x, to.z));
      float startYaw = player.getYRot();
      new Thread(() -> {
         for (int i = 1; i <= ticks; i++) {
            float t = (float)i / ticks;
            float y = startYaw + Mth.wrapDegrees(targetYaw - startYaw) * t;

            try {
               Thread.sleep(50L);
            } catch (InterruptedException var8) {
            }

            MinecraftServer server = player.level().getServer();
            if (server == null) {
               return;
            }

            server.execute(() -> {
               player.setYRot(y);
               player.setYHeadRot(y);
               player.setYBodyRot(y);
            });
         }
      }).start();
   }

   private static void drainRepos(List<HighFiveShakeHandler.PendingRepos> list) {
      Iterator<HighFiveShakeHandler.PendingRepos> it = list.iterator();

      while (it.hasNext()) {
         HighFiveShakeHandler.PendingRepos r = it.next();
         if (r.remainingTicks <= 0) {
            it.remove();
         } else {
            Vec3 step = r.remainingDelta.scale(1.0 / r.remainingTicks);
            Vec3 pos = r.player.position();
            r.player.teleportTo(pos.x + step.x, pos.y, pos.z + step.z);
            r.remainingDelta = r.remainingDelta.subtract(step);
            if (--r.remainingTicks <= 0) {
               it.remove();
            }
         }
      }
   }

   private static int[] lookupCombo(HighFiveShakeHandler.Dir d1, HighFiveShakeHandler.Dir d2) {
      if (d1 == HighFiveShakeHandler.Dir.W && d2 == HighFiveShakeHandler.Dir.S) {
         return new int[]{0, 116, 117, 2000};
      } else if (d1 == HighFiveShakeHandler.Dir.S && d2 == HighFiveShakeHandler.Dir.W) {
         return new int[]{0, 117, 116, 2000};
      } else if (d1 == HighFiveShakeHandler.Dir.A && d2 == HighFiveShakeHandler.Dir.A) {
         return new int[]{0, 118, 118, 1167};
      } else {
         return d1 == HighFiveShakeHandler.Dir.D && d2 == HighFiveShakeHandler.Dir.D ? new int[]{0, 119, 119, 1500} : null;
      }
   }

   private static void broadcastKeyPress(ServerPlayer player, HighFiveShakeHandler.Dir dir) {
      UUID partnerId = activeSession.get(player.getUUID());
      MinecraftServer server = player.level().getServer();
      if (server != null) {
         HighFiveShakeHandler.ShakeKeyPressPayload p = new HighFiveShakeHandler.ShakeKeyPressPayload(
            player.getUUID(), player.getName().getString(), dir.ordinal()
         );
         ServerPlayNetworking.send(player, p);
         if (partnerId != null) {
            ServerPlayer partner = server.getPlayerList().getPlayer(partnerId);
            if (partner != null) {
               ServerPlayNetworking.send(partner, p);
            }
         }
      }
   }

   private static void trackInput(UUID id, HighFiveShakeHandler.Dir dir) {
      Deque<HighFiveShakeHandler.Dir> q = recentInputs.computeIfAbsent(id, k -> new ArrayDeque<>());
      q.addLast(dir);

      while (q.size() > 2) {
         q.removeFirst();
      }
   }

   private static Long getWindowOpenedAt(UUID id) {
      Long d = windowOpenedAt.get(id);
      if (d != null) {
         return d;
      }

      UUID p = activeSession.get(id);
      return p != null ? windowOpenedAt.get(p) : null;
   }

   private static void endHandshake(ServerPlayer player, String reason, boolean push) {
      endHandshakeByKey(player.level().getServer(), player.getUUID(), reason, push);
   }

   private static void endHandshakeByKey(MinecraftServer server, UUID id, String reason, boolean push) {
      UUID partnerId = activeSession.remove(id);
      if (partnerId != null) {
         activeSession.remove(partnerId);
         int finalStreak = streakCount.getOrDefault(id, 0);

         for (UUID uid : new UUID[]{id, partnerId}) {
            sessionPhase.remove(uid);
            windowOpenedAt.remove(uid);
            animStartedAt.remove(uid);
            animDurationMs.remove(uid);
            streakCount.remove(uid);
            consecutiveClashes.remove(uid);
            lastRealInputTime.remove(uid);
            heldDirection.remove(uid);
            prevRawDirection.remove(uid);
            lockedDirection.remove(uid);
            bankedPress.remove(uid);
            lastAcceptedPressTime.remove(uid);
            recentInputs.remove(uid);
            disarm(uid);
            HighFiveHandler.handRaisedTime.remove(uid);
            HighFiveHandler.startAnimTime.remove(uid);
            HighFiveHandler.lastHoldRefresh.remove(uid);
            HighFiveHandler.handRaiseSprinting.remove(uid);
            HighFiveHandler.highFiveCooldown.put(uid, System.currentTimeMillis());
         }

         System.out.println("[Handshake] ENDED (" + reason + ") streak=" + finalStreak);
         if (server != null) {
            ServerPlayer p1 = server.getPlayerList().getPlayer(id);
            ServerPlayer p2 = server.getPlayerList().getPlayer(partnerId);
            if (p1 != null) {
               PoseNetworking.broadcastAnimState(p1, 115);
               HighFiveHandler.syncHandRaised(p1, false);
               ServerPlayNetworking.send(p1, new HighFiveShakeHandler.ShakeSessionPayload(false));
            }

            if (p2 != null) {
               PoseNetworking.broadcastAnimState(p2, 115);
               HighFiveHandler.syncHandRaised(p2, false);
               ServerPlayNetworking.send(p2, new HighFiveShakeHandler.ShakeSessionPayload(false));
            }

            if (push && p1 != null && p2 != null) {
               applyPushApart(p1, p2);
            }
         }
      }
   }

   private static void applyPushApart(ServerPlayer p1, ServerPlayer p2) {
      Vec3 to = p2.position().subtract(p1.position());
      double d = Math.hypot(to.x, to.z);
      if (!(d < 0.01)) {
         Vec3 dir = new Vec3(to.x / d, 0.0, to.z / d);
         Vec3 v1 = p1.getDeltaMovement();
         p1.setDeltaMovement(v1.x - dir.x * 0.35, v1.y, v1.z - dir.z * 0.35);
         p1.hurtMarked = true;
         Vec3 v2 = p2.getDeltaMovement();
         p2.setDeltaMovement(v2.x + dir.x * 0.35, v2.y, v2.z + dir.z * 0.35);
         p2.hurtMarked = true;
      }
   }

   public static boolean isHandshakeClaimed(UUID id) {
      return activeSession.containsKey(id) || handshakeArmed.getOrDefault(id, false);
   }

   public static boolean isArmed(UUID id) {
      return handshakeArmed.getOrDefault(id, false);
   }

   public static boolean isInHandshake(UUID id) {
      return activeSession.containsKey(id);
   }

   public static void cleanup(MinecraftServer server, UUID id) {
      disarm(id);
      heldDirection.remove(id);
      prevRawDirection.remove(id);
      lockedDirection.remove(id);
      bankedPress.remove(id);
      lastAcceptedPressTime.remove(id);
      lastRealInputTime.remove(id);
      recentInputs.remove(id);
      if (activeSession.containsKey(id)) {
         endHandshakeByKey(server, id, "cleanup", false);
      }
   }

   public enum Dir {
      W,
      A,
      S,
      D,
      NONE;
   }

   private static final class PendingRepos {
      final ServerPlayer player;
      Vec3 remainingDelta;
      int remainingTicks;

      PendingRepos(ServerPlayer p, Vec3 d, int t) {
         this.player = p;
         this.remainingDelta = d;
         this.remainingTicks = t;
      }
   }

   private enum Phase {
      STARTING,
      IDLE,
      ANIMATING;
   }

   public record ShakeAnimStartPayload(long durationMs, long serverStartMs) implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeAnimStartPayload> ID = new Type(HighFiveShakeHandler.SHAKE_ANIM_START_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeAnimStartPayload> CODEC = StreamCodec.ofMember((p, b) -> {
         b.writeLong(p.durationMs);
         b.writeLong(p.serverStartMs);
      }, b -> new HighFiveShakeHandler.ShakeAnimStartPayload(b.readLong(), b.readLong()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShakeArmTogglePayload() implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeArmTogglePayload> ID = new Type(HighFiveShakeHandler.SHAKE_ARM_TOGGLE_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeArmTogglePayload> CODEC = StreamCodec.unit(
         new HighFiveShakeHandler.ShakeArmTogglePayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShakeArmedStatePayload(boolean armed) implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeArmedStatePayload> ID = new Type(HighFiveShakeHandler.SHAKE_ARMED_STATE_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeArmedStatePayload> CODEC = StreamCodec.ofMember(
         (p, b) -> b.writeBoolean(p.armed), b -> new HighFiveShakeHandler.ShakeArmedStatePayload(b.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShakeDirPayload(int dirOrdinal) implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeDirPayload> ID = new Type(HighFiveShakeHandler.SHAKE_DIR_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeDirPayload> CODEC = StreamCodec.ofMember(
         (p, b) -> b.writeInt(p.dirOrdinal), b -> new HighFiveShakeHandler.ShakeDirPayload(b.readInt())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShakeEndKeyPayload() implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeEndKeyPayload> ID = new Type(HighFiveShakeHandler.SHAKE_END_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeEndKeyPayload> CODEC = StreamCodec.unit(
         new HighFiveShakeHandler.ShakeEndKeyPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShakeImpactPayload(float amount, long durationMs) implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeImpactPayload> ID = new Type(HighFiveShakeHandler.SHAKE_IMPACT_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeImpactPayload> CODEC = StreamCodec.ofMember((p, b) -> {
         b.writeFloat(p.amount);
         b.writeLong(p.durationMs);
      }, b -> new HighFiveShakeHandler.ShakeImpactPayload(b.readFloat(), b.readLong()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShakeKeyPressPayload(UUID playerId, String playerName, int dirOrdinal) implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeKeyPressPayload> ID = new Type(HighFiveShakeHandler.SHAKE_KEYPRESS_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeKeyPressPayload> CODEC = StreamCodec.ofMember((p, b) -> {
         b.writeUUID(p.playerId);
         b.writeUtf(p.playerName);
         b.writeInt(p.dirOrdinal);
      }, b -> new HighFiveShakeHandler.ShakeKeyPressPayload(b.readUUID(), b.readUtf(), b.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShakeResultPayload(boolean success, boolean perfect, int streak) implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeResultPayload> ID = new Type(HighFiveShakeHandler.SHAKE_RESULT_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeResultPayload> CODEC = StreamCodec.ofMember((p, b) -> {
         b.writeBoolean(p.success);
         b.writeBoolean(p.perfect);
         b.writeInt(p.streak);
      }, b -> new HighFiveShakeHandler.ShakeResultPayload(b.readBoolean(), b.readBoolean(), b.readInt()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record ShakeSessionPayload(boolean active) implements CustomPacketPayload {
      public static final Type<HighFiveShakeHandler.ShakeSessionPayload> ID = new Type(HighFiveShakeHandler.SHAKE_SESSION_ID);
      public static final StreamCodec<FriendlyByteBuf, HighFiveShakeHandler.ShakeSessionPayload> CODEC = StreamCodec.ofMember(
         (p, b) -> b.writeBoolean(p.active), b -> new HighFiveShakeHandler.ShakeSessionPayload(b.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }
}
