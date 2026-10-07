package com.cooptest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class HuddleHandler {
   private static final double HUDDLE_RANGE = 2.0;
   private static final double HUDDLE_RADIUS = 1.0;
   private static final double HUDDLE_MIN_SPACING = 0.95;
   private static final double HUDDLE_MAX_RADIUS = 3.2;
   private static final long HOLD_REQUIRED_MS = 1000L;
   private static final long F_RELEASE_GRACE_MS = 1200L;
   private static final long QTE_WINDOW_MS = 1400L;
   private static final long QTE_ANIM_PRE_MS = 400L;
   private static final long HUDDLE_MAX_MS = 20000L;
   private static final long COOLDOWN_MS = 8000L;
   private static final int ANIM_HUDDLE_START = 70;
   private static final int ANIM_HUDDLE_IDLE = 71;
   private static final int ANIM_HUDDLE_QTE1 = 72;
   private static final int ANIM_HUDDLE_END = 73;
   private static final int ANIM_HUDDLE_QTE2 = 77;
   private static final int ANIM_HUDDLE_QTE3 = 78;
   private static final int ANIM_NONE = 0;
   public static final double ARM_CHARGE_MIN = 0.95;
   public static final long ARM_SHIFT_HOLD_MS = 1000L;
   public static final double TAP_FILL = 0.055;
   public static final double BAR_DRAIN_PER_TICK = 0.016;
   public static final long BAR_DRAIN_GRACE_MS = 220L;
   public static final double TAP_SCALE_PER_PLAYER = 0.55;
   public static final double STAGE2_AT = 0.34;
   public static final double STAGE3_AT = 0.68;
   public static final float SHAKE_MIN = 0.1F;
   public static final float SHAKE_MAX = 0.85F;
   public static final long SHAKE_REFRESH_MS = 160L;
   public static final int MAX_SCALE_PLAYERS = 8;
   public static final long CANCEL_GRACE_MS = 700L;
   public static final long QTE_AUTO_START_MS = 900L;
   public static final long ACTIVE_WINDOW_MS = 700L;
   public static final long END_MOVE_RELEASE_MS = 960L;
   public static final double SYNC_BONUS_FILL = 0.05;
   public static final long SYNC_BONUS_COOLDOWN_MS = 400L;
   private static final long HUDDLE_START_MS = 542L;
   private static final Random RANDOM = new Random();
   private static final Map<String, HuddleHandler.HuddleSession> sessions = new HashMap<>();
   private static final Map<UUID, String> playerSession = new HashMap<>();
   private static final Map<UUID, Long> fHoldStart = new HashMap<>();
   private static final Map<String, Long> cooldowns = new HashMap<>();
   private static final Map<UUID, Long> joinerEnterMs = new HashMap<>();
   public static final boolean HUDDLE_DEBUG = true;
   public static final long CHARGE_GRACE_MS = 1200L;
   private static final Map<UUID, Long> lastFullCharge = new HashMap<>();
   private static final int GLIDE_TICKS = 6;
   private static final List<HuddleHandler.Glide> pendingGlide = new ArrayList<>();
   private static MinecraftServer serverRef;

   private static double radiusFor(int n) {
      if (n <= 2) {
         return 1.0;
      }

      double needed = n * 0.95 / (Math.PI * 2);
      return Math.min(3.2, Math.max(1.0, needed));
   }

   public static boolean sharingHuddle(UUID a, UUID b) {
      String sk = playerSession.get(a);
      return sk != null && sk.equals(playerSession.get(b));
   }

   private static String key(UUID a, UUID b) {
      return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
   }

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(HuddleHandler.HuddleFHoldPayload.ID, HuddleHandler.HuddleFHoldPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HuddleHandler.HuddleShiftPayload.ID, HuddleHandler.HuddleShiftPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HuddleHandler.HuddleTapPayload.ID, HuddleHandler.HuddleTapPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HuddleHandler.HuddleCancelPayload.ID, HuddleHandler.HuddleCancelPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(HuddleHandler.HuddleHoldOpenPayload.ID, HuddleHandler.HuddleHoldOpenPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HuddleHandler.HuddleHoldInfoPayload.ID, HuddleHandler.HuddleHoldInfoPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HuddleHandler.HuddleEndPayload.ID, HuddleHandler.HuddleEndPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HuddleHandler.HuddleBarPayload.ID, HuddleHandler.HuddleBarPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HuddleHandler.HuddleShakePayload.ID, HuddleHandler.HuddleShakePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(HuddleHandler.HuddleYawPayload.ID, HuddleHandler.HuddleYawPayload.CODEC);
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(
         HuddleHandler.HuddleFHoldPayload.ID, (payload, ctx) -> ctx.server().execute(() -> DuoPoseHandler.onFHold(ctx.player(), payload.holding()))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         HuddleHandler.HuddleShiftPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onShiftHold(ctx.player(), payload.holding()))
      );
      ServerPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleTapPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onTap(ctx.player())));
      ServerPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleHoldOpenPayload.ID, (payload, ctx) -> {
         ServerPlayer pl = ctx.player();
         String sk = playerSession.get(pl.getUUID());
         if (sk != null) {
            HuddleHandler.HuddleSession hs = sessions.get(sk);
            if (hs != null) {
               if (payload.holding()) {
                  hs.holdsOpen.add(pl.getUUID());
               } else {
                  hs.holdsOpen.remove(pl.getUUID());
               }
            }
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleCancelPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onCancel(ctx.player())));
      registerDebugCommand();
      ServerTickEvents.END_SERVER_TICK.register(HuddleHandler::tick);
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
         if (entity instanceof ServerPlayer victim) {
            if (source.getEntity() instanceof ServerPlayer attacker) {
               return attacker.getUUID().equals(victim.getUUID()) ? true : !sharingHuddle(victim.getUUID(), attacker.getUUID());
            } else {
               return true;
            }
         } else {
            return true;
         }
      });
   }

   private static void onShiftHold(ServerPlayer player, boolean holding) {
      UUID id = player.getUUID();
      String sk = playerSession.get(id);
      if (sk != null) {
         HuddleHandler.HuddleSession s = sessions.get(sk);
         if (s != null) {
            if (holding && System.currentTimeMillis() - s.huddleStart > 700L) {
               onCancel(player);
            }

            return;
         }
      }

      if (!holding || !HighFiveHandler.isInBlockingState(id)) {
         if (!holding || sk != null || !DuoPoseHandler.isBlockingHuddle(id)) {
            if (sk != null) {
               HuddleHandler.HuddleSession s = sessions.get(sk);
               if (s != null) {
                  if (holding) {
                     s.holdsF.add(id);
                  } else {
                     s.holdsF.remove(id);
                  }

                  if (s.stage == HuddleHandler.HuddleStage.IDLE && !holding && s.firstRelease == null) {
                     s.firstRelease = System.currentTimeMillis();
                  }

                  return;
               }
            }

            PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            if (pose != PoseState.GRAB_READY
               && pose != PoseState.GRAB_HOLDING
               && pose != PoseState.GRABBED
               && pose != PoseState.PUSH_IDLE
               && pose != PoseState.PUSH_ACTION) {
               if (holding) {
                  fHoldStart.putIfAbsent(id, System.currentTimeMillis());
               } else {
                  fHoldStart.remove(id);
               }

               System.out.println("[HUDDLE] shift=" + holding + " recorded for " + id + " | holders now=" + fHoldStart.size());
            }
         }
      }
   }

   private static void noteCharges() {
      long now = System.currentTimeMillis();
      long need = 237L;

      for (Entry<UUID, Long> e : ChargedDapHandler.chargeStartTime.entrySet()) {
         if (now - e.getValue() >= need) {
            lastFullCharge.put(e.getKey(), now);
         }
      }
   }

   private static boolean atFullCharge(UUID id) {
      Long when = lastFullCharge.get(id);
      boolean ok = when != null && System.currentTimeMillis() - when <= 1200L;
      if (!ok) {
         System.out.println("[HUDDLE] " + id + " no recent full charge");
      }

      return ok;
   }

   private static void consumeCharges(Collection<UUID> ids) {
      for (UUID id : ids) {
         ChargedDapHandler.chargeStartTime.remove(id);
         lastFullCharge.remove(id);
      }
   }

   private static void onTap(ServerPlayer player) {
      UUID id = player.getUUID();
      String sk = playerSession.get(id);
      if (sk != null) {
         HuddleHandler.HuddleSession s = sessions.get(sk);
         if (s != null && s.stage == HuddleHandler.HuddleStage.QTE) {
            long now = System.currentTimeMillis();
            s.lastTapByPlayer.put(id, now);
            int active = 0;

            for (UUID u : s.players) {
               Long t = s.lastTapByPlayer.get(u);
               if (t != null && now - t <= 700L) {
                  active++;
               }
            }

            int total = Math.max(1, s.players.size());
            double participation = (double)active / total;
            double divisor = 1.0 + 0.55 * Math.max(0, total - 2);
            double gain = 0.055 / divisor * participation;
            if (active == total && total >= 2 && now - s.lastSyncBonus >= 400L) {
               s.lastSyncBonus = now;
               gain += 0.05;
               ServerLevel w = player.level();
               Vec3 c = s.center.add(0.0, 1.2, 0.0);
               w.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 14, 0.4, 0.4, 0.4, 0.25);
               w.playSound(null, c.x, c.y, c.z, (SoundEvent)SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.9F, 1.8F);
               MinecraftServer srv = w.getServer();
               if (srv != null) {
                  for (UUID u : s.players) {
                     ServerPlayer lp = srv.getPlayerList().getPlayer(u);
                     if (lp != null) {
                        ServerPlayNetworking.send(lp, new HuddleHandler.HuddleShakePayload(0.5F, 140));
                        lp.sendOverlayMessage(Component.literal("§6§lIN SYNC"));
                     }
                  }
               }
            }

            s.bar = Math.min(1.0, s.bar + gain);
            s.lastTapMs = now;
         }
      }
   }

   private static void onCancel(ServerPlayer player) {
      String sk = playerSession.get(player.getUUID());
      if (sk != null) {
         HuddleHandler.HuddleSession s = sessions.get(sk);
         if (s != null) {
            MinecraftServer server = player.level().getServer();
            if (server != null) {
               UUID id = player.getUUID();
               player.sendOverlayMessage(Component.literal("§7left the huddle"));
               s.players.remove(id);
               s.holdsF.remove(id);
               s.cancelArmed.remove(id);
               s.holdsOpen.remove(id);
               playerSession.remove(id);
               PoseNetworking.broadcastAnimState(player, 0);
               ServerPlayNetworking.send(player, new HuddleHandler.HuddleBarPayload(0.0F, 0, false));
               if (s.players.size() < 2) {
                  failHuddle(s, server);
               } else {
                  smoothRepositionAll(s, server);
               }
            }
         }
      }
   }

   public static boolean onButtonPress(ServerPlayer player, String button) {
      UUID id = player.getUUID();
      String sk = playerSession.get(id);
      if (sk == null) {
         return false;
      }

      HuddleHandler.HuddleSession s = sessions.get(sk);
      if (s == null || s.stage != HuddleHandler.HuddleStage.QTE || !s.qteOpen) {
         System.out
            .println(
               "[HUDDLE] onButtonPress IGNORED btn="
                  + button
                  + " player="
                  + player.getName().getString()
                  + " inSession="
                  + (sk != null)
                  + " stage="
                  + (s != null ? s.stage : "null")
                  + " qteOpen="
                  + (s != null ? s.qteOpen : "null")
            );
         return false;
      }

      if ("FAIL".equals(button)) {
         failHuddle(s, player.level().getServer());
         return true;
      }

      if (!"G".equals(button) && !"H".equals(button)) {
         return false;
      }

      String expected = s.expectedButton();
      if (!button.equals(expected)) {
         failHuddle(s, player.level().getServer());
         return true;
      }

      if (id.equals(s.p1)) {
         s.qteHits.add(s.p1);
      } else if (s.players.contains(id)) {
         s.qteHits.add(id);
      }

      return true;
   }

   private static void tickGlides() {
      Iterator<HuddleHandler.Glide> it = pendingGlide.iterator();

      while (it.hasNext()) {
         HuddleHandler.Glide g = it.next();
         if (g.player != null && g.player.isAlive() && !g.player.isRemoved()) {
            g.ticksLeft--;
            double t = 1.0 - (double)g.ticksLeft / g.total;
            double k = t * t * (3.0 - 2.0 * t);
            double x = g.startX + (g.targetX - g.startX) * k;
            double z = g.startZ + (g.targetZ - g.startZ) * k;
            if (g.ticksLeft <= 0) {
               x = g.targetX;
               z = g.targetZ;
            }

            g.player.teleportTo(g.player.level(), x, g.player.getY(), z, Set.of(), g.yaw, g.player.getXRot(), false);
            if (g.ticksLeft <= 0) {
               g.player.setYRot(g.yaw);
               g.player.setYBodyRot(g.yaw);
               ServerPlayNetworking.send(g.player, new HuddleHandler.HuddleYawPayload(g.yaw));
               it.remove();
            }
         } else {
            it.remove();
         }
      }
   }

   private static void tick(MinecraftServer server) {
      serverRef = server;
      long now = System.currentTimeMillis();
      tickGlides();
      if (server.getTickCount() % 4 == 0) {
         detectNewHuddles(server, now);
      }

      joinerEnterMs.entrySet().removeIf(entry -> {
         if (now - entry.getValue() >= 542L) {
            ServerPlayer jp = server.getPlayerList().getPlayer(entry.getKey());
            if (jp != null) {
               PoseNetworking.broadcastAnimState(jp, 71);
            }

            return true;
         } else {
            return false;
         }
      });
      Set<HuddleHandler.HuddleSession> seen = Collections.newSetFromMap(new IdentityHashMap<>());

      for (HuddleHandler.HuddleSession s : new ArrayList<>(sessions.values())) {
         if (seen.add(s)) {
            tickSession(s, server, now);
         }
      }

      cooldowns.entrySet().removeIf(e -> now - e.getValue() > 8000L);
   }

   private static void detectNewHuddles(MinecraftServer server, long now) {
      noteCharges();
      List<UUID> holders = new ArrayList<>(fHoldStart.keySet());

      for (UUID joiner : holders) {
         if (!playerSession.containsKey(joiner)) {
            Long startJ = fHoldStart.get(joiner);
            if (startJ != null && now - startJ >= 1000L) {
               ServerPlayer pj = server.getPlayerList().getPlayer(joiner);
               if (pj != null) {
                  for (HuddleHandler.HuddleSession s : new ArrayList<>(sessions.values())) {
                     if (s.stage == HuddleHandler.HuddleStage.IDLE && !s.players.contains(joiner) && s.center != null && !(pj.distanceToSqr(s.center) > 9.0)) {
                        s.players.add(joiner);
                        consumeCharges(List.of(joiner));
                        s.holdsF.add(joiner);
                        playerSession.put(joiner, key(s.p1, s.p2));
                        fHoldStart.remove(joiner);
                        smoothRepositionAll(s, server);
                        PoseNetworking.broadcastAnimState(pj, 70);
                        joinerEnterMs.put(joiner, now);

                        for (UUID uid : s.players) {
                           ServerPlayer pp = server.getPlayerList().getPlayer(uid);
                           if (pp != null) {
                              pp.swing(InteractionHand.MAIN_HAND, true);
                           }
                        }

                        final List<UUID> allNow = new ArrayList<>(s.players);
                        new Timer().schedule(new TimerTask() {
                           @Override
                           public void run() {
                              server.execute(() -> {
                                 for (UUID uid : allNow) {
                                    ServerPlayer pp = server.getPlayerList().getPlayer(uid);
                                    if (pp != null) {
                                       pp.swing(InteractionHand.MAIN_HAND, true);
                                    }
                                 }
                              });
                           }
                        }, 120L);
                        new Timer().schedule(new TimerTask() {
                           @Override
                           public void run() {
                              server.execute(() -> {
                                 for (UUID uid : allNow) {
                                    ServerPlayer pp = server.getPlayerList().getPlayer(uid);
                                    if (pp != null) {
                                       pp.swing(InteractionHand.MAIN_HAND, true);
                                    }
                                 }
                              });
                           }
                        }, 260L);
                        final ServerPlayer fpj = pj;
                        new Timer()
                           .schedule(
                              new TimerTask() {
                                 @Override
                                 public void run() {
                                    server.execute(
                                       () -> {
                                          if (fpj.isAlive()) {
                                             Vec3 arm = fpj.position().add(0.0, 1.4, 0.0).add(fpj.getViewVector(1.0F).scale(0.4));
                                             fpj.level().sendParticles(ParticleTypes.CRIT, arm.x, arm.y, arm.z, 6, 0.08, 0.08, 0.08, 0.06);
                                             fpj.level().sendParticles(ParticleTypes.ENCHANTED_HIT, arm.x, arm.y, arm.z, 4, 0.06, 0.06, 0.06, 0.04);
                                             fpj.level()
                                                .playSound(
                                                   null, arm.x, arm.y, arm.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 2.0F
                                                );
                                          }
                                       }
                                    );
                                 }
                              },
                              420L
                           );
                        pj.level()
                           .playSound(
                              null, s.center.x, s.center.y, s.center.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.7F
                           );
                        pj.sendOverlayMessage(Component.literal("§aYou joined the huddle!"));

                        for (UUID pid : s.players) {
                           if (!pid.equals(joiner)) {
                              ServerPlayer pp = server.getPlayerList().getPlayer(pid);
                              if (pp != null) {
                                 pp.sendOverlayMessage(Component.literal("§a" + pj.getName().getString() + " joined the huddle!"));
                              }
                           }
                        }
                        break;
                     }
                  }
               }
            }
         }
      }

      for (int i = 0; i < holders.size(); i++) {
         UUID a = holders.get(i);
         if (!playerSession.containsKey(a)) {
            Long startA = fHoldStart.get(a);
            if (startA != null && now - startA >= 1000L && atFullCharge(a)) {
               ServerPlayer pa = server.getPlayerList().getPlayer(a);
               if (pa != null) {
                  for (int j = i + 1; j < holders.size(); j++) {
                     UUID b = holders.get(j);
                     if (!playerSession.containsKey(b)) {
                        Long startB = fHoldStart.get(b);
                        if (startB != null && now - startB >= 1000L && atFullCharge(b)) {
                           ServerPlayer pb = server.getPlayerList().getPlayer(b);
                           if (pb != null && !(pa.distanceTo(pb) > 2.0)) {
                              String k = key(a, b);
                              if (!cooldowns.containsKey(k)) {
                                 List<UUID> initPlayers = new ArrayList<>(List.of(a, b));
                                 HuddleHandler.HuddleSession s = new HuddleHandler.HuddleSession(initPlayers);
                                 sessions.put(k, s);
                                 playerSession.put(a, k);
                                 playerSession.put(b, k);
                                 fHoldStart.remove(a);
                                 fHoldStart.remove(b);
                                 startHuddle(s, pa, pb, server);
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
   }

   private static void startHuddle(HuddleHandler.HuddleSession s, ServerPlayer pa, ServerPlayer pb, MinecraftServer server) {
      consumeCharges(s.players);
      s.center = pa.position().add(pb.position()).scale(0.5);
      Vec3 toPa = pa.position().subtract(s.center);
      s.baseAngle = toPa.horizontalDistanceSqr() > 0.001 ? Math.atan2(toPa.z, toPa.x) : 0.0;
      ServerLevel world = pa.level();
      smoothRepositionAll(s, server);

      for (UUID uid : s.players) {
         ServerPlayer p = server.getPlayerList().getPlayer(uid);
         if (p != null) {
            PoseNetworking.broadcastAnimState(p, 70);
            p.swing(InteractionHand.MAIN_HAND, true);
         }
      }

      final List<UUID> foundersSnap = new ArrayList<>(s.players);
      new Timer().schedule(new TimerTask() {
         @Override
         public void run() {
            pa.level().getServer().execute(() -> {
               for (UUID uid : foundersSnap) {
                  ServerPlayer p = pa.level().getServer().getPlayerList().getPlayer(uid);
                  if (p != null) {
                     p.swing(InteractionHand.MAIN_HAND, true);
                  }
               }
            });
         }
      }, 120L);
      new Timer().schedule(new TimerTask() {
         @Override
         public void run() {
            pa.level().getServer().execute(() -> {
               for (UUID uid : foundersSnap) {
                  ServerPlayer p = pa.level().getServer().getPlayerList().getPlayer(uid);
                  if (p != null) {
                     p.swing(InteractionHand.MAIN_HAND, true);
                  }
               }
            });
         }
      }, 260L);
      world.playSound(null, s.center.x, s.center.y, s.center.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
      world.sendParticles(ParticleTypes.ENCHANTED_HIT, s.center.x, s.center.y + 1.0, s.center.z, 12, 0.4, 0.4, 0.4, 0.06);
   }

   private static void smoothRepositionAll(HuddleHandler.HuddleSession s, MinecraftServer server) {
      if (s.center != null) {
         int n = s.players.size();

         for (int ci = 0; ci < n; ci++) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.players.get(ci));
            if (p != null) {
               double r = radiusFor(n);
               double angle = s.baseAngle + (Math.PI * 2) * ci / n;
               double targetX = s.center.x + r * Math.cos(angle);
               double targetZ = s.center.z + r * Math.sin(angle);
               float targetYaw = (float)(-Math.toDegrees(Math.atan2(s.center.x - targetX, s.center.z - targetZ)));
               pendingGlide.removeIf(g -> g.player == p);
               pendingGlide.add(new HuddleHandler.Glide(p, targetX, targetZ, targetYaw, 6));
            }
         }
      }
   }

   private static void repositionCircle(HuddleHandler.HuddleSession s, MinecraftServer server) {
      if (s.center != null) {
         int n = s.players.size();
         double[] slotAngle = new double[n];

         for (int i = 0; i < n; i++) {
            slotAngle[i] = s.baseAngle + (Math.PI * 2) * i / n;
         }

         int[] assigned = new int[n];
         boolean[] taken = new boolean[n];
         Arrays.fill(assigned, -1);

         for (int pi = 0; pi < n; pi++) {
            ServerPlayer pp = server.getPlayerList().getPlayer(s.players.get(pi));
            if (pp != null) {
               Vec3 off = pp.position().subtract(s.center);
               double cur = Math.atan2(off.z, off.x);
               int best = -1;
               double bestDelta = Double.MAX_VALUE;

               for (int si = 0; si < n; si++) {
                  if (!taken[si]) {
                     double d = Math.abs(Mth.wrapDegrees(Math.toDegrees(slotAngle[si] - cur)));
                     if (d < bestDelta) {
                        bestDelta = d;
                        best = si;
                     }
                  }
               }

               if (best >= 0) {
                  assigned[pi] = best;
                  taken[best] = true;
               }
            }
         }

         for (int pi = 0; pi < n; pi++) {
            if (assigned[pi] == -1) {
               for (int si = 0; si < n; si++) {
                  if (!taken[si]) {
                     assigned[pi] = si;
                     taken[si] = true;
                     break;
                  }
               }
            }
         }

         for (int ci = 0; ci < n; ci++) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.players.get(ci));
            if (p != null) {
               double angle = slotAngle[assigned[ci] >= 0 ? assigned[ci] : ci];
               double r = radiusFor(n);
               double px = s.center.x + r * Math.cos(angle);
               double pz = s.center.z + r * Math.sin(angle);
               float yaw = (float)(-Math.toDegrees(Math.atan2(s.center.x - px, s.center.z - pz)));
               p.teleportTo(p.level(), px, p.getY(), pz, Set.of(), yaw, p.getXRot(), false);
               p.setYRot(yaw);
               p.setYBodyRot(yaw);
               ServerPlayNetworking.send(p, new HuddleHandler.HuddleYawPayload(yaw));
            }
         }
      }
   }

   private static void tickSession(HuddleHandler.HuddleSession s, MinecraftServer server, long now) {
      List<ServerPlayer> live = new ArrayList<>();

      for (UUID uid : new ArrayList<>(s.players)) {
         ServerPlayer lp = server.getPlayerList().getPlayer(uid);
         if (lp != null && lp.isAlive()) {
            live.add(lp);
         } else {
            s.players.remove(uid);
            s.holdsF.remove(uid);
            s.cancelArmed.remove(uid);
            s.holdsOpen.remove(uid);
            playerSession.remove(uid);
         }
      }

      if (live.size() < 2) {
         failHuddle(s, server);
      } else {
         ServerPlayer p1 = live.get(0);
         ServerPlayer p2 = live.get(1);
         boolean holdingOpen = !s.holdsOpen.isEmpty();
         if (holdingOpen) {
            s.huddleStart = now - 1L;
         }

         if (now - s.huddleStart > 20000L) {
            failHuddle(s, server);
         } else {
            switch (s.stage) {
               case ENTERING:
                  if (s.elapsed() >= 542L) {
                     s.stage = HuddleHandler.HuddleStage.IDLE;
                     s.stageStart = now;
                     PoseNetworking.broadcastAnimState(p1, 71);
                     PoseNetworking.broadcastAnimState(p2, 71);
                  }
                  break;
               case IDLE:
                  if (now - s.lastAuraTick >= 80L) {
                     s.lastAuraTick = now;
                     s.auraAngle += 0.35;
                     Vec3 center = s.center != null ? s.center : p1.position().add(p2.position()).scale(0.5);
                     double r = 1.4;

                     for (int i = 0; i < 4; i++) {
                        double a = s.auraAngle + (Math.PI / 2) * i;
                        double px = center.x + r * Math.cos(a);
                        double pz = center.z + r * Math.sin(a);
                        p1.level().sendParticles(ParticleTypes.END_ROD, px, center.y + 0.05, pz, 1, 0.0, 0.0, 0.0, 0.01);
                     }

                     double r2 = 0.7;

                     for (int i = 0; i < 3; i++) {
                        double a = -s.auraAngle * 1.5 + (Math.PI * 2.0 / 3.0) * i;
                        double px = center.x + r2 * Math.cos(a);
                        double pz = center.z + r2 * Math.sin(a);
                        p1.level().sendParticles(ParticleTypes.ENCHANTED_HIT, px, center.y + 0.3, pz, 1, 0.0, 0.0, 0.0, 0.005);
                     }
                  }

                  if (!s.holdsOpen.isEmpty()) {
                     if (server.getTickCount() % 5 == 0) {
                        HuddleHandler.HuddleHoldInfoPayload info = new HuddleHandler.HuddleHoldInfoPayload(s.holdsOpen.size(), s.players.size());

                        for (UUID uid : s.players) {
                           ServerPlayer lp = server.getPlayerList().getPlayer(uid);
                           if (lp != null) {
                              ServerPlayNetworking.send(lp, info);
                           }
                        }
                     }

                     s.stageStart = now;
                     return;
                  }

                  if (s.elapsed() >= 900L) {
                     for (UUID uid : s.players) {
                        ServerPlayer lp = server.getPlayerList().getPlayer(uid);
                        if (lp != null) {
                           ServerPlayNetworking.send(lp, new HuddleHandler.HuddleHoldInfoPayload(0, s.players.size()));
                        }
                     }

                     Vec3 mid = s.center != null ? s.center.add(0.0, 1.0, 0.0) : p1.position().add(p2.position()).scale(0.5).add(0.0, 1.0, 0.0);
                     ServerLevel w0 = p1.level();
                     w0.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.3F);
                     w0.playSound(null, mid.x, mid.y, mid.z, ModSounds.DAP_HIT, SoundSource.PLAYERS, 0.9F, 1.4F);
                     w0.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 12, 0.25, 0.25, 0.25, 0.1);
                     w0.sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 6, 0.2, 0.2, 0.2, 0.06);

                     for (UUID uid : s.players) {
                        ServerPlayer lp = server.getPlayerList().getPlayer(uid);
                        if (lp != null) {
                           ServerPlayNetworking.send(lp, new HuddleHandler.HuddleShakePayload(0.9F, 220));
                        }
                     }

                     startQTEStep(s, server, p1, p2, now);
                  }
               case RELEASING:
               default:
                  break;
               case QTE:
                  if (!s.qteAnimStarted) {
                     return;
                  }

                  if (!holdingOpen && now - s.lastTapMs > 220L) {
                     s.bar = Math.max(0.0, s.bar - 0.016);
                  }

                  int wantStage = s.bar >= 0.68 ? 3 : (s.bar >= 0.34 ? 2 : 1);
                  if (wantStage != s.barAnimStage) {
                     s.barAnimStage = wantStage;

                     int anim = switch (wantStage) {
                        case 2 -> 77;
                        case 3 -> 78;
                        default -> 72;
                     };

                     for (ServerPlayer lp : live) {
                        PoseNetworking.broadcastAnimState(lp, anim);
                     }

                     Vec3 sc = s.center.add(0.0, 1.1, 0.0);
                     ServerLevel sw = p1.level();
                     switch (wantStage) {
                        case 2:
                           sw.sendParticles(ParticleTypes.ENCHANTED_HIT, sc.x, sc.y, sc.z, 26, 0.5, 0.4, 0.5, 0.22);
                           sw.sendParticles(ParticleTypes.WAX_OFF, sc.x, sc.y, sc.z, 18, 0.4, 0.4, 0.4, 0.16);
                           break;
                        case 3:
                           sw.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sc.x, sc.y, sc.z, 30, 0.5, 0.5, 0.5, 0.2);
                           sw.sendParticles(ParticleTypes.GLOW, sc.x, sc.y, sc.z, 26, 0.45, 0.45, 0.45, 0.18);
                           sw.sendParticles(ParticleTypes.END_ROD, sc.x, sc.y, sc.z, 20, 0.3, 0.6, 0.3, 0.26);
                           break;
                        default:
                           sw.sendParticles(ParticleTypes.CRIT, sc.x, sc.y, sc.z, 20, 0.4, 0.35, 0.4, 0.18);
                           sw.sendParticles(ParticleTypes.ELECTRIC_SPARK, sc.x, sc.y, sc.z, 16, 0.35, 0.35, 0.35, 0.15);
                     }

                     for (ServerPlayer lp : live) {
                        ServerPlayNetworking.send(lp, new HuddleHandler.HuddleShakePayload(0.55F + wantStage * 0.3F, 200));
                     }

                     Vec3 c = s.center;
                     p1.level()
                        .playSound(null, c.x, c.y, c.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 0.9F + wantStage * 0.25F);
                  }

                  float shake = (float)(0.1F + 0.75 * s.bar);
                  boolean pulse = now - s.lastShakeMs >= 160L;
                  if (pulse) {
                     s.lastShakeMs = now;
                  }

                  for (ServerPlayer lp : live) {
                     ServerPlayNetworking.send(lp, new HuddleHandler.HuddleBarPayload((float)s.bar, s.players.size(), true));
                     if (pulse && s.bar > 0.05) {
                        ServerPlayNetworking.send(lp, new HuddleHandler.HuddleShakePayload(shake, 220));
                     }
                  }

                  Vec3 mid = s.center.add(0.0, 1.0, 0.0);
                  ServerLevel w = p1.level();
                  int swirl = (int)Math.round(2.0 + s.bar * 10.0);
                  w.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, swirl, 0.35, 0.35, 0.35, 0.1 + s.bar * 0.25);
                  if (s.bar > 0.5) {
                     w.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, (int)(s.bar * 8.0), 0.25, 0.45, 0.25, 0.18);
                  }

                  if (s.bar >= 1.0) {
                     s.resetQTE();

                     for (ServerPlayer lp : live) {
                        PoseNetworking.broadcastAnimState(lp, 73);
                        ServerPlayNetworking.send(lp, new HuddleHandler.HuddleBarPayload(1.0F, s.players.size(), false));
                     }

                     finale(w, s.center, live.size());
                     System.out.println("[HUDDLE] bar COMPLETE - lighting rally for " + s.players.size() + " player(s)");
                     RallyBeaconHandler.light(w, s.center, new HashSet<>(s.players));
                     successHuddle(s, server, live, now);
                     return;
                  }

                  if (s.bar <= 0.0 && s.elapsed() > 1500L) {
                     failHuddle(s, server);
                  }
                  break;
               case ENDING:
                  if (!s.endMoveReleased && s.elapsed() >= 960L) {
                     s.endMoveReleased = true;

                     for (ServerPlayer lp : live) {
                        ServerPlayNetworking.send(lp, new ChargedDapHandler.PerfectDapFreezePayload(false));
                        ServerPlayNetworking.send(lp, new HuddleHandler.HuddleBarPayload(0.0F, 0, false));
                     }
                  }

                  if (s.elapsed() >= 1375L) {
                     for (ServerPlayer lp : live) {
                        ServerPlayNetworking.send(lp, new ChargedDapHandler.PerfectDapFreezePayload(false));
                     }

                     cleanupSession(s, server);
                  }
                  break;
               case DONE:
                  cleanupSession(s, server);
            }
         }
      }
   }

   private static void registerDebugCommand() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                 "testhuddle"
                              )
                              .executes(ctx -> spawnTestRing(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), 4)))
                           .then(Commands.literal("2").executes(c -> spawnTestRing(((CommandSourceStack)c.getSource()).getPlayerOrException(), 2))))
                        .then(Commands.literal("4").executes(c -> spawnTestRing(((CommandSourceStack)c.getSource()).getPlayerOrException(), 4))))
                     .then(Commands.literal("6").executes(c -> spawnTestRing(((CommandSourceStack)c.getSource()).getPlayerOrException(), 6))))
                  .then(Commands.literal("8").executes(c -> spawnTestRing(((CommandSourceStack)c.getSource()).getPlayerOrException(), 8)))
            )
         );
   }

   private static int spawnTestRing(ServerPlayer player, int count) {
      count = Math.max(2, Math.min(8, count));
      ServerLevel world = player.level();
      double yawRad = Math.toRadians(player.getYRot());
      Vec3 fwd = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
      Vec3 centre = player.position().add(fwd.scale(2.0));
      world.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y + 1.2, centre.z, 30, 0.05, 0.35, 0.05, 0.0);
      List<ArmorStand> stands = new ArrayList<>();

      for (int i = 0; i < count; i++) {
         double angle = (Math.PI * 2) * i / count;
         double rr = radiusFor(count);
         double px = centre.x + rr * Math.cos(angle);
         double pz = centre.z + rr * Math.sin(angle);
         float yaw = (float)(-Math.toDegrees(Math.atan2(centre.x - px, centre.z - pz)));
         ArmorStand stand = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, world);
         stand.setPosRaw(px, centre.y, pz);
         stand.setYRot(yaw);
         stand.setYHeadRot(yaw);
         stand.setYBodyRot(yaw);
         stand.setPermanentlyInvulnerable(true);
         stand.setCustomNameVisible(true);
         stand.setCustomName(Component.literal("§e#" + i + " §7yaw §f" + String.format("%.1f", yaw)));
         world.addFreshEntity(stand);
         stands.add(stand);

         for (double t = 0.15; t < 1.0; t += 0.15) {
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, px + (centre.x - px) * t, centre.y + 1.3, pz + (centre.z - pz) * t, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }

      player.sendSystemMessage(Component.literal("§6/testhuddle §7ring of §f" + count + " §7at radius §f1.0§7 — stands despawn in 30s"));
      MinecraftServer srv = world.getServer();
      if (srv != null) {
         new Thread(() -> {
            try {
               Thread.sleep(30000L);
            } catch (InterruptedException ignored) {
               return;
            }

            srv.execute(() -> stands.forEach(Entity::discard));
         }).start();
      }

      return 1;
   }

   private static void finale(ServerLevel w, Vec3 center, int playerCount) {
      int tier = Math.min(8, Math.max(2, playerCount));
      double m = tier / 2.0;
      Vec3 hands = center.add(0.0, 1.1, 0.0);
      w.sendParticles(ParticleTypes.FIREWORK, hands.x, hands.y, hands.z, (int)(30.0 * m), 0.5, 0.5, 0.5, 0.3 * m);
      w.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, hands.x, hands.y, hands.z, (int)(24.0 * m), 0.5, 0.5, 0.5, 0.28 * m);
      w.sendParticles(ParticleTypes.END_ROD, hands.x, hands.y, hands.z, (int)(20.0 * m), 0.4, 0.4, 0.4, 0.35 * m);
      w.sendParticles(ParticleTypes.EXPLOSION, hands.x, hands.y, hands.z, (int)(2.0 * m), 0.5, 0.5, 0.5, 0.0);
      w.playSound(null, center.x, center.y, center.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.4F, 1.3F - tier * 0.04F);
      w.playSound(null, center.x, center.y, center.z, ModSounds.TRUE_FRIENDSHIP, SoundSource.PLAYERS, 1.0F, 1.0F);
      MinecraftServer srv = w.getServer();
      if (srv != null) {
         int height = (int)(6.0 * m);
         new Thread(() -> {
            for (int step = 0; step <= height; step++) {
               double y = hands.y + step * 0.55;
               boolean top = step == height;
               srv.execute(() -> {
                  w.sendParticles(ParticleTypes.END_ROD, hands.x, y, hands.z, (int)(6.0 * m), 0.14, 0.06, 0.14, 0.05);
                  w.sendParticles(ParticleTypes.FIREWORK, hands.x, y, hands.z, (int)(4.0 * m), 0.1, 0.05, 0.1, 0.04);
                  if (top) {
                     w.sendParticles(ParticleTypes.EXPLOSION_EMITTER, hands.x, y, hands.z, (int)Math.max(1.0, m), 0.6, 0.3, 0.6, 0.0);
                     w.sendParticles(ParticleTypes.FIREWORK, hands.x, y, hands.z, (int)(50.0 * m), 1.4, 1.0, 1.4, 0.55);
                     w.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, hands.x, y, hands.z, (int)(36.0 * m), 1.2, 0.9, 1.2, 0.45);
                     w.playSound(null, hands.x, y, hands.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.6F, 1.0F);
                     w.playSound(null, hands.x, y, hands.z, ModSounds.EXPLOSION_IMPACT, SoundSource.PLAYERS, 1.3F, 1.1F);
                  }
               });

               try {
                  Thread.sleep(35L);
               } catch (InterruptedException ignored) {
                  return;
               }
            }
         }).start();
      }
   }

   private static void startQTEStep(HuddleHandler.HuddleSession s, MinecraftServer server, ServerPlayer p1, ServerPlayer p2, long now) {
      s.qteStep = 1;
      s.stage = HuddleHandler.HuddleStage.QTE;
      s.stageStart = now;
      s.resetQTE();
      s.qteAnimStarted = true;
      s.qteAnimStart = now;
      s.bar = 0.0;
      s.barAnimStage = 1;
      s.lastTapMs = now;

      for (UUID uid : s.players) {
         ServerPlayer lp = server.getPlayerList().getPlayer(uid);
         if (lp != null) {
            PoseNetworking.broadcastAnimState(lp, 72);
         }
      }
   }

   private static String roman(int amplifier) {
      return switch (amplifier) {
         case 0 -> "I";
         case 1 -> "II";
         case 2 -> "III";
         default -> "IV";
      };
   }

   private static void successHuddle(HuddleHandler.HuddleSession s, MinecraftServer server, List<ServerPlayer> live, long now) {
      s.stage = HuddleHandler.HuddleStage.ENDING;
      s.stageStart = now;
      cooldowns.put(key(s.p1, s.p2), now);

      for (ServerPlayer lp : live) {
         ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(lp.getUUID(), "G", 0, 0L, 0L, false, 0));
      }

      int tier = Math.min(8, Math.max(2, live.size()));
      int regenAmp = tier >= 6 ? 2 : 1;
      int speedAmp = tier >= 6 ? 2 : (tier >= 4 ? 1 : 0);
      int strAmp = tier >= 5 ? 1 : 0;
      int resAmp = tier >= 6 ? 1 : 0;

      for (ServerPlayer lp : live) {
         lp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, regenAmp, false, true));
         lp.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, speedAmp, false, true));
         lp.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 900, strAmp, false, true));
         lp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, resAmp, false, true));
         lp.experienceLevel += 2;
         lp.displayClientMessage(
            Component.literal(
               "§d§l✦ HUDDLE! ✦ §7Regen "
                  + roman(regenAmp)
                  + ", Speed "
                  + roman(speedAmp)
                  + ", Strength "
                  + roman(strAmp)
                  + ", Resistance "
                  + roman(resAmp)
                  + " §8(60s)"
            ),
            false
         );
      }

      ServerPlayer p1 = live.get(0);
      ServerPlayer p2 = live.get(1);
      Vec3 center = s.center != null ? s.center : p1.position().add(p2.position()).scale(0.5);
      ServerLevel flashWorld = p1.level();
      flashWorld.playSound(null, center.x, center.y, center.z, SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 2.0F, 0.6F);
      flashWorld.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 2.0F, 0.8F);
      flashWorld.playSound(null, center.x, center.y, center.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5F, 1.0F);
      final List<ServerPlayer> liveFinal = new ArrayList<>(live);
      new Timer()
         .schedule(
            new TimerTask() {
               @Override
               public void run() {
                  server.execute(
                     () -> {
                        if (!liveFinal.stream().noneMatch(LivingEntity::isAlive)) {
                           Vec3 sum = Vec3.ZERO;

                           for (ServerPlayer lp : liveFinal) {
                              sum = sum.add(lp.position());
                           }

                           Vec3 mid = sum.scale(1.0 / liveFinal.size()).add(0.0, 1.0, 0.0);
                           ServerLevel world = liveFinal.get(0).level();
                           world.sendParticles(ParticleTypes.HAPPY_VILLAGER, mid.x, mid.y, mid.z, 80, 0.8, 0.8, 0.8, 0.5);
                           world.sendParticles(ParticleTypes.COMPOSTER, mid.x, mid.y, mid.z, 60, 0.6, 0.6, 0.6, 0.4);
                           world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, 40, 0.6, 0.6, 0.6, 0.3);
                           world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), mid.x, mid.y, mid.z, 3, 0.1, 0.1, 0.1, 0.0);
                           world.sendParticles(ParticleTypes.FIREWORK, mid.x, mid.y, mid.z, 40, 0.5, 0.6, 0.5, 0.25);
                           world.sendParticles(ParticleTypes.CRIT, mid.x, mid.y - 0.5, mid.z, 20, 1.0, 0.0, 1.0, 0.05);
                           world.sendParticles(ParticleTypes.HEART, mid.x, mid.y, mid.z, 15, 0.6, 0.4, 0.6, 0.1);

                           for (double a = 0.0; a < Math.PI * 2; a += 0.4) {
                              world.sendParticles(
                                 ParticleTypes.HAPPY_VILLAGER, mid.x + Math.cos(a) * 1.5, mid.y - 0.9, mid.z + Math.sin(a) * 1.5, 2, 0.0, 0.1, 0.0, 0.05
                              );
                           }

                           world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 2.0F, 1.2F);
                           world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5F, 1.2F);
                           world.playSound(null, mid.x, mid.y, mid.z, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.5F, 2.0F);
                           ItemStack fw = new ItemStack(Items.FIREWORK_ROCKET);

                           for (int ri = 0; ri < 5; ri++) {
                              double ox = (HuddleHandler.RANDOM.nextDouble() - 0.5) * 1.2;
                              double oz = (HuddleHandler.RANDOM.nextDouble() - 0.5) * 1.2;
                              world.addFreshEntity(new FireworkRocketEntity(world, mid.x + ox, mid.y + 0.5, mid.z + oz, fw));
                           }
                        }
                     }
                  );
               }
            },
            500L
         );
      new Timer().schedule(new TimerTask() {
         @Override
         public void run() {
            server.execute(() -> {
               if (!liveFinal.isEmpty() && liveFinal.get(0).isAlive()) {
                  Vec3 sum2 = Vec3.ZERO;

                  for (ServerPlayer lp : liveFinal) {
                     sum2 = sum2.add(lp.position());
                  }

                  final Vec3 mid = sum2.scale(1.0 / liveFinal.size()).add(0.0, 1.0, 0.0);
                  final ServerLevel world = liveFinal.get(0).level();

                  for (int pulse = 0; pulse < 4; pulse++) {
                     final int pp = pulse;
                     new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                           server.execute(() -> {
                              float fade = 1.0F - pp * 0.25F;
                              int cnt = (int)(20.0F * fade);
                              world.sendParticles(ParticleTypes.HAPPY_VILLAGER, mid.x, mid.y, mid.z, cnt, 0.9 * fade, 0.7 * fade, 0.9 * fade, 0.15);
                              world.sendParticles(ParticleTypes.COMPOSTER, mid.x, mid.y, mid.z, cnt / 2, 0.7 * fade, 0.6 * fade, 0.7 * fade, 0.1);
                           });
                        }
                     }, pp * 250);
                  }
               }
            });
         }
      }, 1875L);

      for (ServerPlayer lp : live) {
         ServerPlayNetworking.send(lp, new HuddleHandler.HuddleEndPayload(s.p1, s.p2, true));
      }
   }

   private static void failHuddle(HuddleHandler.HuddleSession s, MinecraftServer server) {
      cooldowns.put(key(s.p1, s.p2), System.currentTimeMillis());
      if (server == null) {
         cleanupSession(s, server);
      } else {
         List<ServerPlayer> live = new ArrayList<>();

         for (UUID uid : s.players) {
            ServerPlayer lp = server.getPlayerList().getPlayer(uid);
            if (lp != null) {
               live.add(lp);
            }
         }

         for (ServerPlayer lp : live) {
            ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(lp.getUUID(), "G", 0, 0L, 0L, false, 0));
            PoseNetworking.broadcastAnimState(lp, 0);
            lp.removeEffect(MobEffects.SPEED);
            lp.removeEffect(MobEffects.STRENGTH);
            lp.sendOverlayMessage(Component.literal("§c✗ Huddle failed!"));
            ServerPlayNetworking.send(lp, new HuddleHandler.HuddleEndPayload(s.p1, s.p2, false));
         }

         if (live.size() >= 2) {
            Vec3 center = s.center != null ? s.center : live.get(0).position().add(live.get(1).position()).scale(0.5);

            for (ServerPlayer lp : live) {
               Vec3 dir = lp.position().subtract(center).normalize();
               lp.push(dir.x * 0.6, 0.4, dir.z * 0.6);
               lp.hurtMarked = true;
            }

            live.get(0).level().sendParticles(ParticleTypes.ANGRY_VILLAGER, center.x, center.y + 1.0, center.z, 8, 0.3, 0.3, 0.3, 0.05);
            live.get(0).level().playSound(null, center.x, center.y, center.z, SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 0.7F, 1.5F);
         }

         cleanupSession(s, server);
      }
   }

   private static void cleanupSession(HuddleHandler.HuddleSession s, MinecraftServer server) {
      String k = key(s.p1, s.p2);
      sessions.remove(k);

      for (UUID uid : s.players) {
         playerSession.remove(uid);
         joinerEnterMs.remove(uid);
         if (server != null) {
            ServerPlayer lp = server.getPlayerList().getPlayer(uid);
            if (lp != null) {
               ServerPlayNetworking.send(lp, new ChargedDapHandler.PerfectDapFreezePayload(false));
            }
         }
      }
   }

   public static void cleanup(UUID id) {
      fHoldStart.remove(id);
      joinerEnterMs.remove(id);
      String k = playerSession.remove(id);
      if (k != null) {
         HuddleHandler.HuddleSession s = sessions.get(k);
         if (s != null) {
            s.players.remove(id);
            s.holdsF.remove(id);
            s.cancelArmed.remove(id);
            s.holdsOpen.remove(id);
            s.qteHits.remove(id);
            pendingGlide.removeIf(g -> g.player != null && g.player.getUUID().equals(id));
            MinecraftServer server = serverRef;
            if (s.players.size() >= 2) {
               if (server != null) {
                  smoothRepositionAll(s, server);
               }
            } else {
               if (server != null) {
                  failHuddle(s, server);
               } else {
                  sessions.remove(k);

                  for (UUID uid : new ArrayList<>(s.players)) {
                     playerSession.remove(uid);
                  }
               }
            }
         }
      }
   }

   public static boolean isInHuddle(UUID id) {
      String k = playerSession.get(id);
      return k != null && sessions.containsKey(k);
   }

   private static final class Glide {
      final ServerPlayer player;
      final double startX;
      final double startZ;
      final double targetX;
      final double targetZ;
      final float yaw;
      final int total;
      int ticksLeft;

      Glide(ServerPlayer p, double x, double z, float yaw, int ticks) {
         this.player = p;
         this.startX = p.position().x;
         this.startZ = p.position().z;
         this.targetX = x;
         this.targetZ = z;
         this.yaw = yaw;
         this.total = ticks;
         this.ticksLeft = ticks;
      }
   }

   public record HuddleBarPayload(float fill, int players, boolean active) implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleBarPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_bar"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleBarPayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeFloat(v.fill());
         buf.writeInt(v.players());
         buf.writeBoolean(v.active());
      }, buf -> new HuddleHandler.HuddleBarPayload(buf.readFloat(), buf.readInt(), buf.readBoolean()));

      public Type<HuddleHandler.HuddleBarPayload> type() {
         return ID;
      }
   }

   public record HuddleCancelPayload() implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleCancelPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_cancel"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleCancelPayload> CODEC = StreamCodec.unit(new HuddleHandler.HuddleCancelPayload());

      public Type<HuddleHandler.HuddleCancelPayload> type() {
         return ID;
      }
   }

   public record HuddleEndPayload(UUID p1, UUID p2, boolean success) implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleEndPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_end_result"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleEndPayload> CODEC = StreamCodec.ofMember((val, buf) -> {
         buf.writeUUID(val.p1());
         buf.writeUUID(val.p2());
         buf.writeBoolean(val.success());
      }, buf -> new HuddleHandler.HuddleEndPayload(buf.readUUID(), buf.readUUID(), buf.readBoolean()));

      public Type<HuddleHandler.HuddleEndPayload> type() {
         return ID;
      }
   }

   public record HuddleFHoldPayload(boolean holding) implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleFHoldPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_f_hold"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleFHoldPayload> CODEC = StreamCodec.ofMember(
         (val, buf) -> buf.writeBoolean(val.holding()), buf -> new HuddleHandler.HuddleFHoldPayload(buf.readBoolean())
      );

      public Type<HuddleHandler.HuddleFHoldPayload> type() {
         return ID;
      }
   }

   public record HuddleHoldInfoPayload(int holding, int players) implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleHoldInfoPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_hold_info"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleHoldInfoPayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeVarInt(v.holding());
         buf.writeVarInt(v.players());
      }, buf -> new HuddleHandler.HuddleHoldInfoPayload(buf.readVarInt(), buf.readVarInt()));

      public Type<HuddleHandler.HuddleHoldInfoPayload> type() {
         return ID;
      }
   }

   public record HuddleHoldOpenPayload(boolean holding) implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleHoldOpenPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_hold_open"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleHoldOpenPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeBoolean(v.holding()), buf -> new HuddleHandler.HuddleHoldOpenPayload(buf.readBoolean())
      );

      public Type<HuddleHandler.HuddleHoldOpenPayload> type() {
         return ID;
      }
   }

   private static class HuddleSession {
      final List<UUID> players;
      final UUID p1;
      final UUID p2;
      HuddleHandler.HuddleStage stage = HuddleHandler.HuddleStage.ENTERING;
      long stageStart = System.currentTimeMillis();
      long huddleStart = System.currentTimeMillis();
      Vec3 center;
      double baseAngle = 0.0;
      Set<UUID> holdsF;
      Long firstRelease = null;
      int qteStep = 0;
      String qteBtn1 = "G";
      String qteBtn2 = "H";
      String qteBtn3 = "G";
      Set<UUID> qteHits = new HashSet<>();
      boolean qteOpen = false;
      boolean qteAnimStarted = false;
      long qteAnimStart = 0L;
      double bar = 0.0;
      long lastTapMs = 0L;
      int barAnimStage = 1;
      long lastShakeMs = 0L;
      long lastSyncBonus = 0L;
      boolean endMoveReleased = false;
      Map<UUID, Long> lastTapByPlayer = new HashMap<>();
      Set<UUID> cancelArmed = new HashSet<>();
      Set<UUID> holdsOpen = new HashSet<>();
      long lastAuraTick = 0L;
      double auraAngle = 0.0;
      int stepsHit = 0;

      HuddleSession(List<UUID> playerList) {
         this.players = new ArrayList<>(playerList);
         this.p1 = playerList.get(0);
         this.p2 = playerList.get(1);
         this.holdsF = new HashSet<>(playerList);
         this.qteBtn1 = HuddleHandler.RANDOM.nextBoolean() ? "G" : "H";
         this.qteBtn2 = this.qteBtn1.equals("G") ? "H" : "G";
         this.qteBtn3 = HuddleHandler.RANDOM.nextBoolean() ? "G" : "H";
      }

      void resetQTE() {
         this.qteHits.clear();
         this.qteOpen = false;
         this.qteAnimStarted = false;
      }

      boolean allHit() {
         return this.qteHits.containsAll(this.players);
      }

      boolean allHoldF() {
         return this.holdsF.containsAll(this.players);
      }

      boolean anyReleasedF() {
         return !this.holdsF.containsAll(this.players);
      }

      long elapsed() {
         return System.currentTimeMillis() - this.stageStart;
      }

      String expectedButton() {
         return switch (this.qteStep) {
            case 1 -> this.qteBtn1;
            case 2 -> this.qteBtn2;
            case 3 -> this.qteBtn3;
            default -> "G";
         };
      }
   }

   public record HuddleShakePayload(float amount, int durationMs) implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleShakePayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_shake"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleShakePayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeFloat(v.amount());
         buf.writeInt(v.durationMs());
      }, buf -> new HuddleHandler.HuddleShakePayload(buf.readFloat(), buf.readInt()));

      public Type<HuddleHandler.HuddleShakePayload> type() {
         return ID;
      }
   }

   public record HuddleShiftPayload(boolean holding) implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleShiftPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_shift"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleShiftPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeBoolean(v.holding()), buf -> new HuddleHandler.HuddleShiftPayload(buf.readBoolean())
      );

      public Type<HuddleHandler.HuddleShiftPayload> type() {
         return ID;
      }
   }

   private enum HuddleStage {
      ENTERING,
      IDLE,
      RELEASING,
      QTE,
      ENDING,
      DONE;
   }

   public record HuddleTapPayload() implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleTapPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_tap"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleTapPayload> CODEC = StreamCodec.unit(new HuddleHandler.HuddleTapPayload());

      public Type<HuddleHandler.HuddleTapPayload> type() {
         return ID;
      }
   }

   public record HuddleYawPayload(float yaw) implements CustomPacketPayload {
      public static final Type<HuddleHandler.HuddleYawPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "huddle_yaw"));
      public static final StreamCodec<FriendlyByteBuf, HuddleHandler.HuddleYawPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeFloat(v.yaw()), buf -> new HuddleHandler.HuddleYawPayload(buf.readFloat())
      );

      public Type<HuddleHandler.HuddleYawPayload> type() {
         return ID;
      }
   }
}
