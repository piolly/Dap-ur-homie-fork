package com.cooptest.spin;

import com.cooptest.GrabMechanic;
import com.cooptest.HighFiveHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class HandSpinHandler {
   public static final long HOLD_REQUIRED_MS = 1000L;
   public static final double SPIN_RADIUS = 0.75;
   public static final double SPIN_MAX_ANGULAR_VEL = 0.2;
   public static final double SPIN_ACCEL = 0.0037;
   public static final double SPIN_DECEL_FAST = 0.006;
   public static final double SPIN_DECEL_SLOW = 0.0015;
   public static final double SPIN_RANGE_TO_START = 2.0;
   public static final long SIMULTANEOUS_RELEASE_WINDOW_MS = 350L;
   public static final double STRENGTH_MAX_ANGULAR_VEL = 0.26;
   public static final double STRENGTH_ACCEL = 0.005;
   public static final double STRENGTH_LIFT_BLOCKS = 0.5;
   public static final int STRENGTH_LIFT_TICKS = 18;
   public static final long STRENGTH_AUTO_YEET_MS = 10000L;
   public static final long MONKE_ANIM_MS = 625L;
   public static final double STRENGTH_LAUNCH_STRENGTH = 2.6;
   public static final double STRENGTH_LAUNCH_UP = 0.7;
   public static final int STRENGTH_LAND_GRACE_TICKS = 4;
   public static final int STRENGTH_MAX_FLIGHT_TICKS = 200;
   public static final long MAX_SPIN_MS = 300000L;
   public static final int TERMINAL_STAGE_WATCHDOG_TICKS = 200;
   public static final int SYNC_INTERVAL_TICKS = 3;
   public static final double PUSH_APART_STRENGTH = 0.35;
   public static final double PUSH_APART_SLIDE_BLOCKS = 0.6;
   public static final long PUSH_APART_SLIDE_TICKS = 6L;
   public static final double LAUNCH_STRENGTH_MAX = 1.15;
   public static final double LAUNCH_STRENGTH_MIN = 0.45;
   public static final double LAUNCH_UP = 0.32;
   public static final double LAUNCH_SLIDE_BLOCKS = 1.0;
   public static final long LAUNCH_SLIDE_TICKS = 5L;
   public static final double MAX_REPOSITION_SNAP = 3.0;
   public static final double FACING_DOT_THRESHOLD = 0.0;
   public static final int HAND_SWING_INTERVAL_TICKS = 4;
   public static final float DAMAGE_CANCEL_THRESHOLD = 4.0F;
   public static final double DAMAGE_KNOCKBACK_STRENGTH = 0.75;
   public static final float SPIN_SHAKE_AMOUNT = 0.12F;
   public static final long SPIN_SHAKE_REFRESH_MS = 150L;
   public static final long SPIN_SHAKE_PULSE_MS = 200L;
   public static final int ANIM_NONE = 0;
   public static final int ANIM_HAND_SPIN_START = 120;
   public static final int ANIM_HAND_SPIN_IDLE = 121;
   public static final int ANIM_HAND_SPIN_END = 122;
   public static final int ANIM_MONKE = 123;
   public static final int ANIM_MONKE_IDLE = 124;
   private static final long HAND_SPIN_START_MS = 334L;
   private static final long HAND_SPIN_END_MS = 584L;
   private static final Map<UUID, HandSpinHandler.Session> sessionsByKey = new HashMap<>();
   private static final Map<UUID, UUID> playerToKey = new HashMap<>();
   private static final Map<UUID, Long> readyFHoldStart = new HashMap<>();
   private static final Set<UUID> fHeld = new HashSet<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(HandSpinHandler.HandSpinStartPayload.ID, HandSpinHandler.HandSpinStartPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HandSpinHandler.HandSpinStopPayload.ID, HandSpinHandler.HandSpinStopPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HandSpinHandler.HandSpinShakePulsePayload.ID, HandSpinHandler.HandSpinShakePulsePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HandSpinHandler.HandSpinStrengthPayload.ID, HandSpinHandler.HandSpinStrengthPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HandSpinHandler.HandSpinSyncPayload.ID, HandSpinHandler.HandSpinSyncPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HandSpinHandler.HandSpinObserverPayload.ID, HandSpinHandler.HandSpinObserverPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HandSpinHandler.HandSpinMonkeFlyPayload.ID, HandSpinHandler.HandSpinMonkeFlyPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HandSpinHandler.HandSpinFHoldPayload.ID, HandSpinHandler.HandSpinFHoldPayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinFHoldPayload.ID, (payload, ctx) -> {
         ServerPlayer player = ctx.player();
         boolean holding = payload.holding();
         ctx.server().execute(() -> onFHold(player, holding));
      });
      ServerTickEvents.END_SERVER_TICK.register(HandSpinHandler::tick);
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
         if (entity instanceof ServerPlayer victim) {
            if (amount < 4.0F) {
               return true;
            } else {
               UUID id = victim.getUUID();
               UUID key = playerToKey.get(id);
               if (key == null) {
                  return true;
               } else {
                  HandSpinHandler.Session s = sessionsByKey.get(key);
                  if (s != null && !s.branchLocked) {
                     lockBranchDamage(s);
                     return true;
                  } else {
                     return true;
                  }
               }
            }
         } else {
            return true;
         }
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         ServerPlayer player = handler.getPlayer();
         if (player != null) {
            cleanup(player.getUUID(), server);
         }
      });
      ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL
         .register((player, origin, destination) -> cleanup(player.getUUID(), player.level().getServer()));
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> clearAll());
   }

   public static void clearAll() {
      sessionsByKey.clear();
      playerToKey.clear();
      readyFHoldStart.clear();
      fHeld.clear();
   }

   private static void onFHold(ServerPlayer player, boolean holding) {
      UUID id = player.getUUID();
      if (holding) {
         fHeld.add(id);
      } else {
         fHeld.remove(id);
      }

      if (!playerToKey.containsKey(id)) {
         PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
         if (pose != PoseState.GRAB_READY) {
            readyFHoldStart.remove(id);
            fHeld.remove(id);
         } else {
            if (holding) {
               readyFHoldStart.putIfAbsent(id, System.currentTimeMillis());
            } else {
               readyFHoldStart.remove(id);
            }
         }
      }
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      detectNewSpins(server, now);

      for (HandSpinHandler.Session s : new ArrayList<>(sessionsByKey.values())) {
         tickSession(s, server, now);
      }
   }

   private static void detectNewSpins(MinecraftServer server, long now) {
      if (!readyFHoldStart.isEmpty()) {
         List<UUID> candidates = new ArrayList<>();

         for (UUID id : readyFHoldStart.keySet()) {
            if (!playerToKey.containsKey(id)) {
               Long start = readyFHoldStart.get(id);
               if (start != null && now - start >= 1000L && fHeld.contains(id)) {
                  PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                  if (pose == PoseState.GRAB_READY) {
                     candidates.add(id);
                  }
               }
            }
         }

         if (candidates.size() >= 2) {
            for (int i = 0; i < candidates.size(); i++) {
               UUID a = candidates.get(i);
               if (!playerToKey.containsKey(a)) {
                  ServerPlayer pa = server.getPlayerList().getPlayer(a);
                  if (pa != null) {
                     for (int j = i + 1; j < candidates.size(); j++) {
                        UUID b = candidates.get(j);
                        if (!playerToKey.containsKey(b)) {
                           ServerPlayer pb = server.getPlayerList().getPlayer(b);
                           if (pb != null
                              && !(pa.distanceTo(pb) > 2.0)
                              && facingEachOther(pa, pb)
                              && !GrabMechanic.isHolding(pa)
                              && !GrabMechanic.isBeingHeld(pa)
                              && !GrabMechanic.isHolding(pb)
                              && !GrabMechanic.isBeingHeld(pb)) {
                              startSpin(server, pa, pb);
                              break;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean facingEachOther(ServerPlayer a, ServerPlayer b) {
      Vec3 fwdA = HighFiveHandler.horizontalForward(a);
      Vec3 fwdB = HighFiveHandler.horizontalForward(b);
      Vec3 aToB = b.position().subtract(a.position());
      double lenAB = aToB.horizontalDistance();
      if (lenAB < 1.0E-4) {
         return true;
      }

      Vec3 dirAB = new Vec3(aToB.x / lenAB, 0.0, aToB.z / lenAB);
      Vec3 dirBA = dirAB.scale(-1.0);
      double dotA = fwdA.x * dirAB.x + fwdA.z * dirAB.z;
      double dotB = fwdB.x * dirBA.x + fwdB.z * dirBA.z;
      return dotA >= 0.0 && dotB >= 0.0;
   }

   private static void startSpin(MinecraftServer server, ServerPlayer a, ServerPlayer b) {
      ServerPlayer p1 = a;
      ServerPlayer p2 = b;
      if (p1.getUUID().compareTo(p2.getUUID()) > 0) {
         ServerPlayer tmp = p1;
         p1 = p2;
         p2 = tmp;
      }

      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      Vec3 pos1 = p1.position();
      Vec3 pos2 = p2.position();
      Vec3 center = pos1.add(pos2).scale(0.5);
      double startAngle = Math.atan2(pos1.z - center.z, pos1.x - center.x);
      HandSpinHandler.Session s = new HandSpinHandler.Session(id1, id2, center, startAngle);
      s.continuousYaw1 = p1.getYRot();
      s.continuousYaw2 = p2.getYRot();
      sessionsByKey.put(id1, s);
      playerToKey.put(id1, id1);
      playerToKey.put(id2, id1);
      readyFHoldStart.remove(id1);
      readyFHoldStart.remove(id2);
      PoseNetworking.broadcastAnimState(p1, 120);
      PoseNetworking.broadcastAnimState(p2, 120);
      HandSpinHandler.HandSpinStartPayload startPayload = new HandSpinHandler.HandSpinStartPayload(id1, id2, center.x, center.y, center.z);
      ServerPlayNetworking.send(p1, startPayload);
      ServerPlayNetworking.send(p2, startPayload);
      ServerLevel world = p1.level();
      world.playSound(null, center.x, center.y + 1.0, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.3F);
      world.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 1.0, center.z, 14, 0.5, 0.4, 0.5, 0.03);
      world.sendParticles(ParticleTypes.CRIT, center.x, center.y + 1.0, center.z, 10, 0.4, 0.3, 0.4, 0.05);
   }

   private static void sendSyncIfDue(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2) {
      if (++s.syncCounter >= 3) {
         s.syncCounter = 0;
         double lift = s.liftTicks <= 0 ? 0.0 : 0.5 * Math.min(1.0, s.liftTicks / 18.0);
         HandSpinHandler.HandSpinObserverPayload op = new HandSpinHandler.HandSpinObserverPayload(
            s.p1, s.p2, s.center.x, s.center.y, s.center.z, s.angle, s.angularVel, s.p1.equals(s.weakId) ? lift : 0.0, s.p2.equals(s.weakId) ? lift : 0.0
         );
         Set<ServerPlayer> targets = new HashSet<>(PlayerLookup.tracking(p1));
         targets.addAll(PlayerLookup.tracking(p2));
         targets.add(p1);
         targets.add(p2);

         for (ServerPlayer t : targets) {
            ServerPlayNetworking.send(t, op);
         }
      }
   }

   private static void playLoopEffects(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, long tick) {
      ServerLevel world = p1.level();
      double t = Math.min(1.0, s.angularVel / 0.2);
      world.sendParticles(ParticleTypes.CRIT, s.center.x, s.center.y + 1.0, s.center.z, 2, 0.5, 0.4, 0.5, 0.02);
      long whooshInterval = 14L - Math.round(t * 8.0);
      if (tick % whooshInterval == 0L) {
         world.playSound(null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.45F, 0.9F + (float)t * 0.8F);
      }
   }

   private static void tickSession(HandSpinHandler.Session s, MinecraftServer server, long now) {
      if (s.stage == HandSpinHandler.Stage.MONKE_FLY) {
         tickMonkeFly(s, server);
      } else {
         ServerPlayer p1 = server.getPlayerList().getPlayer(s.p1);
         ServerPlayer p2 = server.getPlayerList().getPlayer(s.p2);
         if (p1 != null && p2 != null) {
            if (p1.level() != p2.level()) {
               hardCancel(s, p1, p2, server);
            } else if (!p1.isPassenger() && !p2.isPassenger() && !p1.isSpectator() && !p2.isSpectator()) {
               if (now - s.startedAtMs > 300000L) {
                  hardCancel(s, p1, p2, server);
               } else {
                  if (s.lastSeenStage != s.stage) {
                     s.lastSeenStage = s.stage;
                     s.stageEnteredTick = server.getTickCount();
                  }

                  boolean terminalStage = s.stage == HandSpinHandler.Stage.ENDING_FAST
                     || s.stage == HandSpinHandler.Stage.ENDING_SLOW
                     || s.stage == HandSpinHandler.Stage.PUSH_APART
                     || s.stage == HandSpinHandler.Stage.DAMAGE_CUT
                     || s.stage == HandSpinHandler.Stage.DONE
                     || s.stage == HandSpinHandler.Stage.STRENGTH_YEET;
                  if (terminalStage && s.stageEnteredTick != -1L && server.getTickCount() - s.stageEnteredTick > 200L) {
                     hardCancel(s, p1, p2, server);
                  } else if (p1.isAlive() && p2.isAlive()) {
                     switch (s.stage) {
                        case RAMPING_UP:
                        case SPINNING:
                           if (s.stage == HandSpinHandler.Stage.RAMPING_UP && s.endAnimStartTick == -1L) {
                              s.endAnimStartTick = server.getTickCount();
                           }

                           boolean startAnimDone = s.endAnimStartTick != -1L && (server.getTickCount() - s.endAnimStartTick) * 50L >= 334L;
                           if (s.stage == HandSpinHandler.Stage.RAMPING_UP && startAnimDone) {
                              PoseNetworking.broadcastAnimState(p1, 121);
                              PoseNetworking.broadcastAnimState(p2, 121);
                              s.stage = HandSpinHandler.Stage.SPINNING;
                           }

                           if (checkInterrupts(s, p1, p2)) {
                              return;
                           }

                           if (!s.strengthMode && !s.branchLocked && s.angularVel >= 0.199999999) {
                              boolean s1 = hasStrengthTwo(p1);
                              boolean s2 = hasStrengthTwo(p2);
                              if (s1 ^ s2) {
                                 s.strengthMode = true;
                                 s.strongId = s1 ? s.p1 : s.p2;
                                 s.weakId = s1 ? s.p2 : s.p1;
                                 s.strengthStartMs = now;
                                 ServerPlayer weak = s1 ? p2 : p1;
                                 PoseNetworking.broadcastAnimState(weak, 123);
                                 HandSpinHandler.HandSpinStrengthPayload sp = new HandSpinHandler.HandSpinStrengthPayload(s.weakId);
                                 ServerPlayNetworking.send(p1, sp);
                                 ServerPlayNetworking.send(p2, sp);
                              }
                           }

                           double cap = s.strengthMode ? 0.26 : 0.2;
                           double accel = s.strengthMode ? 0.005 : 0.0037;
                           if (s.strengthMode) {
                              if (s.liftTicks < 18) {
                                 s.liftTicks++;
                              }

                              if (!s.monkeIdleSent && now - s.strengthStartMs >= 625L) {
                                 ServerPlayer weak = server.getPlayerList().getPlayer(s.weakId);
                                 if (weak != null) {
                                    PoseNetworking.broadcastAnimState(weak, 124);
                                 }

                                 s.monkeIdleSent = true;
                              }
                           }

                           s.angularVel = Math.min(cap, s.angularVel + accel);
                           sendSyncIfDue(s, p1, p2);
                           s.angle = s.angle + s.angularVel;
                           applyOrbitPositions(s, p1, p2);
                           swingHandsIfDue(s, p1, p2, server);
                           playLoopEffects(s, p1, p2, server.getTickCount());
                           refreshSpinShake(s, p1, p2);
                           evaluateReleases(s, p1, p2, now);
                           break;
                        case ENDING_FAST:
                           beginTangentLaunch(s, p1, p2, server);
                           break;
                        case ENDING_SLOW:
                           sendSyncIfDue(s, p1, p2);
                           s.angularVel = Math.max(0.0, s.angularVel - 0.0015);
                           s.angle = s.angle + s.angularVel;
                           applyOrbitPositions(s, p1, p2);
                           swingHandsIfDue(s, p1, p2, server);
                           if (s.angularVel <= 1.0E-4) {
                              finishToEndAnim(s, p1, p2, server, false);
                           }
                           break;
                        case PUSH_APART:
                           if (s.pushTicksRemaining > 0) {
                              Vec3 step1 = s.p1RemainingDelta.scale(1.0 / s.pushTicksRemaining);
                              Vec3 step2 = s.p2RemainingDelta.scale(1.0 / s.pushTicksRemaining);
                              Vec3 np1 = p1.position().add(step1);
                              Vec3 np2 = p2.position().add(step2);
                              p1.teleportTo(np1.x, np1.y, np1.z);
                              p2.teleportTo(np2.x, np2.y, np2.z);
                              s.p1RemainingDelta = s.p1RemainingDelta.subtract(step1);
                              s.p2RemainingDelta = s.p2RemainingDelta.subtract(step2);
                              s.pushTicksRemaining--;
                           } else if (s.tangentLaunch) {
                              s.stage = HandSpinHandler.Stage.DONE;
                           } else {
                              finishToEndAnim(s, p1, p2, server, true);
                           }
                           break;
                        case DAMAGE_CUT:
                           if (s.endAnimStartTick == -1L) {
                              beginDamageCutKnockback(s, p1, p2, server);
                           } else if (server.getTickCount() - s.endAnimStartTick >= 2L) {
                              releaseSession(s, p1, p2);
                           }
                           break;
                        case DONE:
                           if (s.endAnimStartTick != -1L && (server.getTickCount() - s.endAnimStartTick) * 50L >= 584L) {
                              releaseSession(s, p1, p2);
                           }
                           break;
                        case STRENGTH_YEET:
                           beginStrengthYeet(s, p1, p2, server);
                     }
                  } else {
                     hardCancel(s, p1, p2, server);
                  }
               }
            } else {
               hardCancel(s, p1, p2, server);
            }
         } else {
            hardCancel(s, p1, p2, server);
         }
      }
   }

   private static boolean checkInterrupts(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2) {
      PoseState pose1 = PoseNetworking.poseStates.getOrDefault(s.p1, PoseState.NONE);
      PoseState pose2 = PoseNetworking.poseStates.getOrDefault(s.p2, PoseState.NONE);
      if (pose1 != PoseState.GRAB_READY) {
         PoseNetworking.poseStates.put(s.p1, PoseState.GRAB_READY);
      }

      if (pose2 != PoseState.GRAB_READY) {
         PoseNetworking.poseStates.put(s.p2, PoseState.GRAB_READY);
      }

      if (p1.distanceTo(p2) > 4.5) {
         if (!s.branchLocked) {
            lockBranchSlow(s);
         }

         return true;
      } else {
         return false;
      }
   }

   private static void evaluateReleases(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, long now) {
      if (!s.branchLocked) {
         if (!s.strengthMode) {
            boolean p1Holding = fHeld.contains(s.p1);
            boolean p2Holding = fHeld.contains(s.p2);
            if (!p1Holding && s.p1ReleasedAt == null) {
               s.p1ReleasedAt = now;
            }

            if (!p2Holding && s.p2ReleasedAt == null) {
               s.p2ReleasedAt = now;
            }

            if (s.p1ReleasedAt != null || s.p2ReleasedAt != null) {
               if (s.p1ReleasedAt != null && s.p2ReleasedAt != null) {
                  long diff = Math.abs(s.p1ReleasedAt - s.p2ReleasedAt);
                  if (diff <= 350L) {
                     lockBranchFast(s);
                  } else {
                     lockBranchSlow(s);
                  }
               } else {
                  long firstReleaseAt = s.p1ReleasedAt != null ? s.p1ReleasedAt : s.p2ReleasedAt;
                  if (now - firstReleaseAt > 350L) {
                     lockBranchSlow(s);
                  }
               }
            }
         } else {
            boolean strongHolding = fHeld.contains(s.strongId);
            if (!strongHolding || now - s.strengthStartMs >= 10000L) {
               s.branchLocked = true;
               s.stage = HandSpinHandler.Stage.STRENGTH_YEET;
            }
         }
      }
   }

   private static boolean hasStrengthTwo(ServerPlayer p) {
      MobEffectInstance effect = p.getEffect(MobEffects.STRENGTH);
      return effect != null && effect.getAmplifier() >= 1;
   }

   private static void lockBranchFast(HandSpinHandler.Session s) {
      s.branchLocked = true;
      s.stage = HandSpinHandler.Stage.ENDING_FAST;
   }

   private static void lockBranchSlow(HandSpinHandler.Session s) {
      s.branchLocked = true;
      s.stage = HandSpinHandler.Stage.ENDING_SLOW;
   }

   private static void lockBranchDamage(HandSpinHandler.Session s) {
      s.branchLocked = true;
      s.stage = HandSpinHandler.Stage.DAMAGE_CUT;
   }

   private static void swingHandsIfDue(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, MinecraftServer server) {
      long tick = server.getTickCount();
      if (s.lastHandSwingTick == -1L || tick - s.lastHandSwingTick >= 4L) {
         s.lastHandSwingTick = tick;
         p1.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
         p2.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
      }
   }

   private static void refreshSpinShake(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2) {
      long now = System.currentTimeMillis();
      if (now - s.lastShakePulseMs >= 150L) {
         s.lastShakePulseMs = now;
         HandSpinHandler.HandSpinShakePulsePayload pulse = new HandSpinHandler.HandSpinShakePulsePayload(0.12F, 200L);
         ServerPlayNetworking.send(p1, pulse);
         ServerPlayNetworking.send(p2, pulse);
      }
   }

   private static void beginDamageCutKnockback(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, MinecraftServer server) {
      Vec3 pos1 = p1.position();
      Vec3 pos2 = p2.position();
      Vec3 outward1 = pos1.subtract(s.center);
      Vec3 outward2 = pos2.subtract(s.center);
      double len1 = Math.max(0.001, outward1.horizontalDistance());
      double len2 = Math.max(0.001, outward2.horizontalDistance());
      Vec3 dir1 = new Vec3(outward1.x / len1, 0.0, outward1.z / len1);
      Vec3 dir2 = new Vec3(outward2.x / len2, 0.0, outward2.z / len2);
      p1.setDeltaMovement(p1.getDeltaMovement().add(dir1.scale(0.75)).add(0.0, 0.15, 0.0));
      p2.setDeltaMovement(p2.getDeltaMovement().add(dir2.scale(0.75)).add(0.0, 0.15, 0.0));
      p1.syncVelocity = true;
      p2.syncVelocity = true;
      PoseNetworking.broadcastAnimState(p1, 0);
      PoseNetworking.broadcastAnimState(p2, 0);
      ServerLevel world = p1.level();
      world.playSound(null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.7F);
      s.endAnimStartTick = server.getTickCount();
   }

   private static void beginTangentLaunch(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, MinecraftServer server) {
      double sin = Math.sin(s.angle);
      double cos = Math.cos(s.angle);
      Vec3 tan1 = new Vec3(-sin, 0.0, cos);
      Vec3 tan2 = tan1.scale(-1.0);
      double t = Math.min(1.0, s.angularVel / 0.2);
      double strength = 0.45 + 0.7 * t;
      p1.setDeltaMovement(tan1.scale(strength).add(0.0, 0.32, 0.0));
      p2.setDeltaMovement(tan2.scale(strength).add(0.0, 0.32, 0.0));
      p1.syncVelocity = true;
      p2.syncVelocity = true;
      s.p1RemainingDelta = tan1.scale(1.0 * t);
      s.p2RemainingDelta = tan2.scale(1.0 * t);
      s.pushTicksRemaining = 5;
      s.tangentLaunch = true;
      ServerPlayNetworking.send(p1, new HandSpinHandler.HandSpinStopPayload(s.p1));
      ServerPlayNetworking.send(p2, new HandSpinHandler.HandSpinStopPayload(s.p2));
      PoseNetworking.broadcastAnimState(p1, 122);
      PoseNetworking.broadcastAnimState(p2, 122);
      s.endAnimStartTick = server.getTickCount();
      ServerLevel world = p1.level();
      world.sendParticles(ParticleTypes.CLOUD, s.center.x, s.center.y + 1.0, s.center.z, 8, 0.25, 0.15, 0.25, 0.1);
      world.playSound(null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.75F + (float)t * 0.4F);
      s.stage = HandSpinHandler.Stage.PUSH_APART;
   }

   private static void beginStrengthYeet(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, MinecraftServer server) {
      ServerPlayer strong = s.p1.equals(s.strongId) ? p1 : p2;
      ServerPlayer weak = s.p1.equals(s.weakId) ? p1 : p2;
      boolean weakIsP1 = s.p1.equals(s.weakId);
      double sin = Math.sin(s.angle);
      double cos = Math.cos(s.angle);
      Vec3 tanWeak = weakIsP1 ? new Vec3(-sin, 0.0, cos) : new Vec3(sin, 0.0, -cos);
      weak.setDeltaMovement(tanWeak.scale(2.6).add(0.0, 0.7, 0.0));
      weak.syncVelocity = true;
      ServerPlayNetworking.send(weak, new HandSpinHandler.HandSpinStopPayload(weak.getUUID()));
      ServerPlayNetworking.send(weak, new HandSpinHandler.HandSpinMonkeFlyPayload(weak.getUUID(), true));
      PoseNetworking.poseStates.put(strong.getUUID(), PoseState.NONE);
      PoseNetworking.broadcastPoseChange(server, strong.getUUID(), PoseState.NONE);
      PoseNetworking.broadcastAnimState(strong, 122);
      ServerPlayNetworking.send(strong, new HandSpinHandler.HandSpinStopPayload(strong.getUUID()));
      fHeld.remove(strong.getUUID());
      ServerLevel world = weak.level();
      world.sendParticles(ParticleTypes.CLOUD, s.center.x, s.center.y + 1.0, s.center.z, 8, 0.25, 0.15, 0.25, 0.1);
      world.playSound(null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
      s.flyTicks = 0;
      s.stage = HandSpinHandler.Stage.MONKE_FLY;
   }

   private static void tickMonkeFly(HandSpinHandler.Session s, MinecraftServer server) {
      ServerPlayer weak = server.getPlayerList().getPlayer(s.weakId);
      if (weak != null && weak.isAlive()) {
         s.flyTicks++;
         boolean landed = s.flyTicks > 4 && (weak.onGround() || weak.isInWater());
         if (landed || s.flyTicks > 200) {
            finishMonkeFly(s, server, weak);
         }
      } else {
         finishMonkeFly(s, server, weak);
      }
   }

   private static void finishMonkeFly(HandSpinHandler.Session s, MinecraftServer server, ServerPlayer weak) {
      PoseNetworking.poseStates.put(s.weakId, PoseState.NONE);
      PoseNetworking.broadcastPoseChange(server, s.weakId, PoseState.NONE);
      if (weak != null) {
         PoseNetworking.broadcastAnimState(weak, 0);
         ServerPlayNetworking.send(weak, new HandSpinHandler.HandSpinMonkeFlyPayload(s.weakId, false));
         ServerPlayNetworking.send(weak, new HandSpinHandler.HandSpinStopPayload(s.weakId));
      }

      endInternal(s);
   }

   private static void hardCancel(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, MinecraftServer server) {
      for (UUID id : new UUID[]{s.p1, s.p2}) {
         PoseNetworking.poseStates.put(id, PoseState.NONE);
         if (server != null) {
            PoseNetworking.broadcastPoseChange(server, id, PoseState.NONE);
         }
      }

      for (ServerPlayer p : new ServerPlayer[]{p1, p2}) {
         if (p != null) {
            UUID id = p.getUUID();
            PoseNetworking.broadcastAnimState(p, 0);
            ServerPlayNetworking.send(p, new HandSpinHandler.HandSpinStopPayload(id));
            ServerPlayNetworking.send(p, new HandSpinHandler.HandSpinMonkeFlyPayload(id, false));
         }
      }

      endInternal(s);
   }

   private static void beginPushApart(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, MinecraftServer server) {
      Vec3 pos1 = p1.position();
      Vec3 pos2 = p2.position();
      Vec3 outward1 = pos1.subtract(s.center);
      Vec3 outward2 = pos2.subtract(s.center);
      double len1 = Math.max(0.001, outward1.horizontalDistance());
      double len2 = Math.max(0.001, outward2.horizontalDistance());
      Vec3 dir1 = new Vec3(outward1.x / len1, 0.0, outward1.z / len1);
      Vec3 dir2 = new Vec3(outward2.x / len2, 0.0, outward2.z / len2);
      p1.setDeltaMovement(p1.getDeltaMovement().add(dir1.scale(0.35)));
      p2.setDeltaMovement(p2.getDeltaMovement().add(dir2.scale(0.35)));
      p1.syncVelocity = true;
      p2.syncVelocity = true;
      s.p1RemainingDelta = dir1.scale(0.6);
      s.p2RemainingDelta = dir2.scale(0.6);
      s.pushTicksRemaining = 6;
      s.stage = HandSpinHandler.Stage.PUSH_APART;
   }

   private static void finishToEndAnim(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2, MinecraftServer server, boolean fromPush) {
      PoseNetworking.broadcastAnimState(p1, 122);
      PoseNetworking.broadcastAnimState(p2, 122);
      s.endAnimStartTick = server.getTickCount();
      s.stage = HandSpinHandler.Stage.DONE;
   }

   private static void releaseSession(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2) {
      if (p1 != null) {
         PoseNetworking.poseStates.put(s.p1, PoseState.NONE);
         PoseNetworking.broadcastPoseChange(p1.level().getServer(), s.p1, PoseState.NONE);
         PoseNetworking.broadcastAnimState(p1, 0);
         ServerPlayNetworking.send(p1, new HandSpinHandler.HandSpinStopPayload(s.p1));
      }

      if (p2 != null) {
         PoseNetworking.poseStates.put(s.p2, PoseState.NONE);
         PoseNetworking.broadcastPoseChange(p2.level().getServer(), s.p2, PoseState.NONE);
         PoseNetworking.broadcastAnimState(p2, 0);
         ServerPlayNetworking.send(p2, new HandSpinHandler.HandSpinStopPayload(s.p2));
      }

      endInternal(s);
   }

   private static void endInternal(HandSpinHandler.Session s) {
      sessionsByKey.remove(s.p1);
      playerToKey.remove(s.p1);
      playerToKey.remove(s.p2);
      fHeld.remove(s.p1);
      fHeld.remove(s.p2);
      readyFHoldStart.remove(s.p1);
      readyFHoldStart.remove(s.p2);
   }

   private static void applyOrbitPositions(HandSpinHandler.Session s, ServerPlayer p1, ServerPlayer p2) {
      double x1 = s.center.x + Math.cos(s.angle) * 0.75;
      double z1 = s.center.z + Math.sin(s.angle) * 0.75;
      double x2 = s.center.x + Math.cos(s.angle + Math.PI) * 0.75;
      double z2 = s.center.z + Math.sin(s.angle + Math.PI) * 0.75;
      double y = s.center.y;
      double lift = s.liftTicks <= 0 ? 0.0 : 0.5 * Math.min(1.0, s.liftTicks / 18.0);
      double y1 = y + (s.p1.equals(s.weakId) ? lift : 0.0);
      double y2 = y + (s.p2.equals(s.weakId) ? lift : 0.0);
      float yaw1 = (float)Math.toDegrees(Math.atan2(x1 - s.center.x, -(z1 - s.center.z)));
      float yaw2 = (float)Math.toDegrees(Math.atan2(x2 - s.center.x, -(z2 - s.center.z)));
      p1.setYRot(yaw1);
      p1.setYHeadRot(yaw1);
      p1.setYBodyRot(yaw1);
      p2.setYRot(yaw2);
      p2.setYHeadRot(yaw2);
      p2.setYBodyRot(yaw2);
      p1.teleportTo(x1, y1, z1);
      p2.teleportTo(x2, y2, z2);
   }

   private static float unwrapYaw(Float previousContinuous, float rawTargetYaw) {
      if (previousContinuous == null) {
         return rawTargetYaw;
      }

      float delta = Mth.wrapDegrees(rawTargetYaw - previousContinuous);
      return previousContinuous + delta;
   }

   public static boolean isInSession(UUID playerId) {
      return playerToKey.containsKey(playerId);
   }

   public static void cleanup(UUID playerId) {
      readyFHoldStart.remove(playerId);
      fHeld.remove(playerId);
      UUID key = playerToKey.get(playerId);
      if (key != null) {
         HandSpinHandler.Session s = sessionsByKey.get(key);
         if (s == null) {
            playerToKey.remove(playerId);
         } else {
            endInternal(s);
         }
      }
   }

   public static void cleanup(UUID playerId, MinecraftServer server) {
      UUID key = playerToKey.get(playerId);
      readyFHoldStart.remove(playerId);
      fHeld.remove(playerId);
      if (key != null) {
         HandSpinHandler.Session s = sessionsByKey.get(key);
         if (s == null) {
            playerToKey.remove(playerId);
         } else {
            UUID otherId = s.p1.equals(playerId) ? s.p2 : s.p1;
            if (server != null) {
               ServerPlayer self = server.getPlayerList().getPlayer(playerId);
               if (self != null) {
                  PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                  PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                  PoseNetworking.broadcastAnimState(self, 0);
                  ServerPlayNetworking.send(self, new HandSpinHandler.HandSpinStopPayload(playerId));
                  ServerPlayNetworking.send(self, new HandSpinHandler.HandSpinMonkeFlyPayload(playerId, false));
               }

               ServerPlayer other = server.getPlayerList().getPlayer(otherId);
               if (other != null) {
                  PoseNetworking.poseStates.put(otherId, PoseState.NONE);
                  PoseNetworking.broadcastPoseChange(server, otherId, PoseState.NONE);
                  PoseNetworking.broadcastAnimState(other, 0);
                  ServerPlayNetworking.send(other, new HandSpinHandler.HandSpinStopPayload(otherId));
                  ServerPlayNetworking.send(other, new HandSpinHandler.HandSpinMonkeFlyPayload(otherId, false));
               }
            }

            endInternal(s);
         }
      }
   }

   public record HandSpinFHoldPayload(boolean holding) implements CustomPacketPayload {
      public static final Type<HandSpinHandler.HandSpinFHoldPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "handspin_fhold"));
      public static final StreamCodec<FriendlyByteBuf, HandSpinHandler.HandSpinFHoldPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> buf.writeBoolean(payload.holding), buf -> new HandSpinHandler.HandSpinFHoldPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HandSpinMonkeFlyPayload(UUID playerId, boolean flying) implements CustomPacketPayload {
      public static final Type<HandSpinHandler.HandSpinMonkeFlyPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "handspin_monke_fly"));
      public static final StreamCodec<FriendlyByteBuf, HandSpinHandler.HandSpinMonkeFlyPayload> CODEC = StreamCodec.ofMember((value, buf) -> {
         buf.writeUUID(value.playerId());
         buf.writeBoolean(value.flying());
      }, buf -> new HandSpinHandler.HandSpinMonkeFlyPayload(buf.readUUID(), buf.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HandSpinObserverPayload(
      UUID p1, UUID p2, double centerX, double centerY, double centerZ, double angle, double angularVel, double p1Lift, double p2Lift
   ) implements CustomPacketPayload {
      public static final Type<HandSpinHandler.HandSpinObserverPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "handspin_observer"));
      public static final StreamCodec<FriendlyByteBuf, HandSpinHandler.HandSpinObserverPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> {
            buf.writeUUID(v.p1());
            buf.writeUUID(v.p2());
            buf.writeDouble(v.centerX());
            buf.writeDouble(v.centerY());
            buf.writeDouble(v.centerZ());
            buf.writeDouble(v.angle());
            buf.writeDouble(v.angularVel());
            buf.writeDouble(v.p1Lift());
            buf.writeDouble(v.p2Lift());
         },
         buf -> new HandSpinHandler.HandSpinObserverPayload(
            buf.readUUID(),
            buf.readUUID(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
         )
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HandSpinShakePulsePayload(float amount, long durationMs) implements CustomPacketPayload {
      public static final Type<HandSpinHandler.HandSpinShakePulsePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "handspin_shake_pulse"));
      public static final StreamCodec<FriendlyByteBuf, HandSpinHandler.HandSpinShakePulsePayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeFloat(payload.amount);
         buf.writeVarLong(payload.durationMs);
      }, buf -> new HandSpinHandler.HandSpinShakePulsePayload(buf.readFloat(), buf.readVarLong()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HandSpinStartPayload(UUID p1, UUID p2, double centerX, double centerY, double centerZ) implements CustomPacketPayload {
      public static final Type<HandSpinHandler.HandSpinStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "handspin_start"));
      public static final StreamCodec<FriendlyByteBuf, HandSpinHandler.HandSpinStartPayload> CODEC = StreamCodec.ofMember((payload, buf) -> {
         buf.writeUUID(payload.p1);
         buf.writeUUID(payload.p2);
         buf.writeDouble(payload.centerX);
         buf.writeDouble(payload.centerY);
         buf.writeDouble(payload.centerZ);
      }, buf -> new HandSpinHandler.HandSpinStartPayload(buf.readUUID(), buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HandSpinStopPayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<HandSpinHandler.HandSpinStopPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "handspin_stop"));
      public static final StreamCodec<FriendlyByteBuf, HandSpinHandler.HandSpinStopPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> buf.writeUUID(payload.playerId), buf -> new HandSpinHandler.HandSpinStopPayload(buf.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HandSpinStrengthPayload(UUID weakId) implements CustomPacketPayload {
      public static final Type<HandSpinHandler.HandSpinStrengthPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "handspin_strength"));
      public static final StreamCodec<FriendlyByteBuf, HandSpinHandler.HandSpinStrengthPayload> CODEC = StreamCodec.ofMember(
         (value, buf) -> buf.writeUUID(value.weakId()), buf -> new HandSpinHandler.HandSpinStrengthPayload(buf.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record HandSpinSyncPayload(double angle, double angularVel) implements CustomPacketPayload {
      public static final Type<HandSpinHandler.HandSpinSyncPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "handspin_sync"));
      public static final StreamCodec<FriendlyByteBuf, HandSpinHandler.HandSpinSyncPayload> CODEC = StreamCodec.ofMember((value, buf) -> {
         buf.writeDouble(value.angle());
         buf.writeDouble(value.angularVel());
      }, buf -> new HandSpinHandler.HandSpinSyncPayload(buf.readDouble(), buf.readDouble()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static class Session {
      final UUID p1;
      final UUID p2;
      HandSpinHandler.Stage stage = HandSpinHandler.Stage.RAMPING_UP;
      Vec3 center;
      double angle;
      double angularVel = 0.0;
      Long p1ReleasedAt = null;
      Long p2ReleasedAt = null;
      boolean branchLocked = false;
      Vec3 p1RemainingDelta;
      Vec3 p2RemainingDelta;
      int pushTicksRemaining = 0;
      boolean tangentLaunch = false;
      boolean strengthMode = false;
      UUID strongId = null;
      UUID weakId = null;
      long strengthStartMs = 0L;
      boolean monkeIdleSent = false;
      int liftTicks = 0;
      int flyTicks = 0;
      int syncCounter = 0;
      long endAnimStartTick = -1L;
      long lastHandSwingTick = -1L;
      long lastShakePulseMs = 0L;
      final long startedAtMs = System.currentTimeMillis();
      HandSpinHandler.Stage lastSeenStage = null;
      long stageEnteredTick = -1L;
      Float continuousYaw1 = null;
      Float continuousYaw2 = null;

      Session(UUID p1, UUID p2, Vec3 center, double startAngle) {
         this.p1 = p1;
         this.p2 = p2;
         this.center = center;
         this.angle = startAngle;
      }
   }

   private enum Stage {
      RAMPING_UP,
      SPINNING,
      ENDING_FAST,
      ENDING_SLOW,
      PUSH_APART,
      DAMAGE_CUT,
      DONE,
      STRENGTH_YEET,
      MONKE_FLY;
   }
}
