package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class StrongSlapHandler {
   private static final double RANGE = 2.5;
   private static final double AIM_DOT = 0.78;
   private static final double BACK_DOT = 0.65;
   private static final double SNAP_DIST = 0.8;
   private static final int TP_TICKS = 5;
   private static final long START_ANIM_MS = 958L;
   private static final long CHARGE_LOOP_MS = 5708L;
   private static final long STAGE_GAP_MS = 250L;
   private static final long TOTAL_STAGES = 6L;
   private static final long CHARGE_SWING_1_MS = 830L;
   private static final long CHARGE_SWING_2_MS = 3790L;
   private static final long FIRE_BEGIN_MS = 2667L;
   private static final long EXPLOSION_MS = 4083L;
   private static final long IMPACT_MS = 4200L;
   private static final long GREEN_START = 600L;
   private static final long GREEN_END = 1050L;
   private static final long NECK_BROKEN_MS = 180000L;
   public static final int ANIM_STRONG_START = 91;
   public static final int ANIM_STRONG_CHARGE = 92;
   public static final int ANIM_STRONG_HIT = 93;
   public static final int ANIM_NONE = 0;
   private static final Map<UUID, StrongSlapHandler.SlapSession> sessions = new HashMap<>();
   private static final Map<UUID, UUID> victimToAtk = new HashMap<>();
   private static final Map<UUID, Long> neckBroken = new HashMap<>();
   private static final Map<UUID, Long> lastTortureMs = new HashMap<>();
   private static final Map<UUID, Long> lastResistDamageMs = new HashMap<>();
   private static final Random TORTURE_RNG = new Random();
   private static final long TORTURE_INTERVAL_MS = 2000L;
   private static final long RESIST_DAMAGE_COOLDOWN_MS = 600L;

   public static void register() {
      PayloadTypeRegistry.clientboundPlay().register(StrongSlapHandler.NeckBrokenPayload.ID, StrongSlapHandler.NeckBrokenPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(StrongSlapHandler.MoveFreezePayload.ID, StrongSlapHandler.MoveFreezePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(StrongSlapHandler.TortureTickPayload.ID, StrongSlapHandler.TortureTickPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(StrongSlapHandler.LookLockPayload.ID, StrongSlapHandler.LookLockPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(StrongSlapHandler.NeckResistPayload.ID, StrongSlapHandler.NeckResistPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(StrongSlapHandler.NeckResistPayload.ID, (payload, ctx) -> {
         ServerPlayer victim = ctx.player();
         UUID id = victim.getUUID();
         if (neckBroken.containsKey(id)) {
            long now = System.currentTimeMillis();
            if (now - lastResistDamageMs.getOrDefault(id, 0L) >= 600L) {
               lastResistDamageMs.put(id, now);
               victim.hurtServer(victim.level(), victim.level().damageSources().generic(), 1.0F);
            }
         }
      });
      ServerTickEvents.END_SERVER_TICK.register(StrongSlapHandler::tick);
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         UUID id = handler.getPlayer().getUUID();
         Long expiry = neckBroken.get(id);
         if (expiry != null) {
            long remaining = expiry - System.currentTimeMillis();
            if (remaining > 0L) {
               ServerPlayNetworking.send(handler.getPlayer(), new StrongSlapHandler.NeckBrokenPayload(id, remaining));
            } else {
               neckBroken.remove(id);
            }
         }
      });
      UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         ItemStack stack = player.getItemInHand(hand);
         boolean isGoldenApple = stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
         if (isGoldenApple && neckBroken.containsKey(player.getUUID())) {
            cureNeckBroken((ServerPlayer)player);
         }

         return InteractionResult.PASS;
      });
   }

   public static boolean tryStrongSlap(ServerPlayer attacker, long chargeDurationMs) {
      if (chargeDurationMs < 300L) {
         return false;
      }

      if (sessions.containsKey(attacker.getUUID())) {
         return false;
      }

      ServerPlayer victim = findBackVictim(attacker);
      if (victim == null) {
         return false;
      }

      if (victimToAtk.containsKey(victim.getUUID())) {
         return false;
      }

      Vec3 victimFwd = victim.getViewVector(1.0F);
      Vec3 victimFwdH = new Vec3(victimFwd.x, 0.0, victimFwd.z);
      if (victimFwdH.lengthSqr() > 0.001) {
         victimFwdH = victimFwdH.normalize();
      } else {
         victimFwdH = new Vec3(1.0, 0.0, 0.0);
      }

      Vec3 tpTarget = victim.position().subtract(victimFwdH.scale(0.8));
      StrongSlapHandler.SlapSession session = new StrongSlapHandler.SlapSession(attacker.getUUID(), victim.getUUID(), attacker.position(), tpTarget);
      sessions.put(attacker.getUUID(), session);
      victimToAtk.put(victim.getUUID(), attacker.getUUID());
      attacker.swing(InteractionHand.MAIN_HAND, true);
      sendFreeze(attacker, true);
      if (!CoopMovesConfig.get().enableStrongSlapSmoothTp) {
         session.tpTick = 5;
         Vec3 diff = victim.position().subtract(attacker.position());
         float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
         attacker.setYRot(yaw);
         attacker.setYBodyRot(yaw);
         attacker.setYHeadRot(yaw);
         sendLookLock(attacker, true);
         sendLookLock(victim, true);
         PoseNetworking.broadcastAnimState(attacker, 91);
         StrongSlapHandler.SlapSession fs = session;
         new Thread(() -> {
            try {
               Thread.sleep(958L);
            } catch (InterruptedException var3x) {
            }

            attacker.level().getServer().execute(() -> {
               if (!fs.cancelled) {
                  ServerPlayer a2 = attacker.level().getServer().getPlayerList().getPlayer(fs.attackerId);
                  if (a2 != null) {
                     fs.chargeAnimStartMs = System.currentTimeMillis();
                     PoseNetworking.broadcastAnimState(a2, 92);
                     openQTEStage(a2, fs);
                     scheduleChargeSounds(attacker.level().getServer(), fs);
                  }
               }
            });
         }).start();
      }

      return true;
   }

   public static boolean onButtonPress(ServerPlayer presser, String button) {
      StrongSlapHandler.SlapSession session = sessions.get(presser.getUUID());
      if (session != null && session.qteOpen) {
         session.qteOpen = false;
         if (!button.equals("G")) {
            cancelSession(presser.level().getServer(), session, "§c✗ Wrong button — slap cancelled!");
            return true;
         }

         session.currentStage++;
         if (session.currentStage >= 6L) {
            launchHitAnim(presser, session);
         } else {
            StrongSlapHandler.SlapSession s = session;
            new Thread(() -> {
               try {
                  Thread.sleep(250L);
               } catch (InterruptedException var3x) {
               }

               presser.level().getServer().execute(() -> {
                  if (!s.cancelled) {
                     ServerPlayer atk = presser.level().getServer().getPlayerList().getPlayer(s.attackerId);
                     if (atk != null) {
                        openQTEStage(atk, s);
                     } else {
                        cancelSession(presser.level().getServer(), s, null);
                     }
                  }
               });
            }).start();
         }

         return true;
      } else {
         return false;
      }
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      List<UUID> toRemove = new ArrayList<>();

      for (Entry<UUID, StrongSlapHandler.SlapSession> entry : sessions.entrySet()) {
         StrongSlapHandler.SlapSession s = entry.getValue();
         if (s.cancelled) {
            toRemove.add(entry.getKey());
         } else {
            ServerPlayer atk = server.getPlayerList().getPlayer(s.attackerId);
            ServerPlayer vic = server.getPlayerList().getPlayer(s.victimId);
            if (atk != null && vic != null) {
               atk.setDeltaMovement(Vec3.ZERO);
               atk.syncVelocity = true;
               if (s.tpTick < 5) {
                  s.tpTick++;
                  float frac = s.tpTick / 5.0F;
                  Vec3 cur = s.tpStart;
                  Vec3 tgt = s.tpTarget;
                  double nx = cur.x + (tgt.x - cur.x) * frac;
                  double ny = cur.y + (tgt.y - cur.y) * frac;
                  double nz = cur.z + (tgt.z - cur.z) * frac;
                  atk.teleportTo(nx, ny, nz);
                  if (s.tpTick == 5) {
                     Vec3 diff = vic.position().subtract(atk.position());
                     float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
                     atk.setYRot(yaw);
                     atk.setYBodyRot(yaw);
                     atk.setYHeadRot(yaw);
                     sendLookLock(atk, true);
                     sendLookLock(vic, true);
                     PoseNetworking.broadcastAnimState(atk, 91);
                     StrongSlapHandler.SlapSession fs = s;
                     new Thread(() -> {
                        try {
                           Thread.sleep(958L);
                        } catch (InterruptedException var3x) {
                        }

                        server.execute(() -> {
                           if (!fs.cancelled) {
                              ServerPlayer a2 = server.getPlayerList().getPlayer(fs.attackerId);
                              if (a2 != null) {
                                 fs.chargeAnimStartMs = System.currentTimeMillis();
                                 PoseNetworking.broadcastAnimState(a2, 92);
                                 openQTEStage(a2, fs);
                                 scheduleChargeSounds(server, fs);
                              }
                           }
                        });
                     }).start();
                  }
               }

               if (s.qteOpen && now - s.stageStart > 2600L) {
                  cancelSession(server, s, "§c✗ QTE timed out!");
                  toRemove.add(entry.getKey());
               }
            } else {
               if (s.victimId != null) {
                  victimToAtk.remove(s.victimId);
               }

               toRemove.add(entry.getKey());
            }
         }
      }

      toRemove.forEach(idx -> {
         StrongSlapHandler.SlapSession s = sessions.remove(idx);
         if (s != null) {
            victimToAtk.remove(s.victimId);
         }
      });
      List<UUID> cured = new ArrayList<>();

      for (Entry<UUID, Long> entry : neckBroken.entrySet()) {
         UUID id = entry.getKey();
         if (now >= entry.getValue()) {
            cured.add(id);
         } else {
            Long last = lastTortureMs.getOrDefault(id, 0L);
            if (now - last >= 2000L) {
               lastTortureMs.put(id, now);
               ServerPlayer victim = server.getPlayerList().getPlayer(id);
               if (victim != null) {
                  victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 1, false, false));
                  victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3, false, false));
                  victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                  int r = 100 + TORTURE_RNG.nextInt(155);
                  int g = TORTURE_RNG.nextInt(200);
                  int b = 100 + TORTURE_RNG.nextInt(155);
                  int color = r << 16 | g << 8 | b;
                  ServerPlayNetworking.send(victim, new StrongSlapHandler.TortureTickPayload(id, color));
               }
            }
         }
      }

      cured.forEach(idx -> {
         neckBroken.remove(idx);
         lastTortureMs.remove(idx);
         lastResistDamageMs.remove(idx);
         ServerPlayer p = server.getPlayerList().getPlayer(idx);
         if (p != null) {
            sendLookLock(p, false);
            ServerPlayNetworking.send(p, new StrongSlapHandler.NeckBrokenPayload(idx, 0L));
         }
      });
   }

   private static void openQTEStage(ServerPlayer attacker, StrongSlapHandler.SlapSession session) {
      session.qteOpen = true;
      session.stageStart = System.currentTimeMillis();
      DapFusionHandler.FusionQTEPayload qte = new DapFusionHandler.FusionQTEPayload(attacker.getUUID(), "G", session.currentStage, 600L, 1050L, true, 1);
      ServerPlayNetworking.send(attacker, qte);
   }

   private static void launchHitAnim(ServerPlayer attacker, StrongSlapHandler.SlapSession session) {
      session.hitAnimStarted = true;
      long elapsed = System.currentTimeMillis() - session.chargeAnimStartMs;
      long posInCycle = elapsed % 5708L;
      long waitMs = 5708L - posInCycle;
      StrongSlapHandler.SlapSession finalSession = session;
      UUID atkId = attacker.getUUID();
      UUID vicId = session.victimId;
      new Thread(() -> {
         try {
            Thread.sleep(waitMs);
         } catch (InterruptedException var7) {
         }

         attacker.level().getServer().execute(() -> {
            if (!finalSession.cancelled) {
               ServerPlayer atk = attacker.level().getServer().getPlayerList().getPlayer(atkId);
               if (atk != null) {
                  PoseNetworking.broadcastAnimState(atk, 93);
                  new Thread(() -> {
                     try {
                        Thread.sleep(2667L);
                     } catch (InterruptedException var4x) {
                     }

                     if (!finalSession.cancelled) {
                        atk.level().getServer().execute(() -> spawnFireBuildup(atk, vicId));
                     }
                  }).start();
                  new Thread(() -> {
                     try {
                        Thread.sleep(4083L);
                     } catch (InterruptedException var4x) {
                     }

                     if (!finalSession.cancelled) {
                        atk.level().getServer().execute(() -> {
                           spawnBigExplosion(atk, vicId);
                           ServerPlayer victim = atk.level().getServer().getPlayerList().getPlayer(vicId);
                           Vec3 pos = victim != null ? victim.position().add(0.0, 1.5, 0.0) : atk.position();
                           atk.level().playSound(null, pos.x, pos.y, pos.z, ModSounds.FAHH, SoundSource.PLAYERS, 4.0F, 1.0F);
                        });
                     }
                  }).start();
                  new Thread(() -> {
                     try {
                        Thread.sleep(4200L);
                     } catch (InterruptedException var5) {
                     }

                     if (!finalSession.cancelled) {
                        atk.level().getServer().execute(() -> applyVictimEffects(atk.level().getServer(), atkId, vicId));
                     }
                  }).start();
               }
            }
         });
      }).start();
   }

   private static void scheduleChargeSounds(MinecraftServer server, StrongSlapHandler.SlapSession session) {
      new Thread(() -> {
         while (!session.hitAnimStarted && !session.cancelled) {
            try {
               Thread.sleep(830L);
            } catch (InterruptedException ignored) {
               return;
            }

            if (!session.hitAnimStarted && !session.cancelled) {
               server.execute(() -> playSwingSound(server, session));

               try {
                  Thread.sleep(2960L);
               } catch (InterruptedException ignored) {
                  return;
               }

               if (!session.hitAnimStarted && !session.cancelled) {
                  server.execute(() -> playSwingSound(server, session));

                  try {
                     Thread.sleep(1918L);
                     continue;
                  } catch (InterruptedException ignored) {
                     return;
                  }
               }

               return;
            }

            return;
         }
      }).start();
   }

   private static void playSwingSound(MinecraftServer server, StrongSlapHandler.SlapSession session) {
      if (!session.cancelled) {
         ServerPlayer atk = server.getPlayerList().getPlayer(session.attackerId);
         if (atk != null) {
            Vec3 pos = atk.position();
            atk.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
         }
      }
   }

   private static void spawnFireBuildup(ServerPlayer attacker, UUID vicId) {
      ServerLevel world = attacker.level();
      Vec3 look = attacker.getViewVector(1.0F);
      Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();

      for (int i = 0; i < 5; i++) {
         int burst = i;
         new Thread(() -> {
            try {
               Thread.sleep(burst * 280L);
            } catch (InterruptedException var4x) {
            }

            attacker.level().getServer().execute(() -> {
               Vec3 l = attacker.getViewVector(1.0F);
               Vec3 r = new Vec3(-l.z, 0.0, l.x).normalize();
               Vec3 tip = attacker.getEyePosition().add(l.scale(0.7)).add(r.scale(0.45));
               world.sendParticles(ParticleTypes.FLAME, tip.x, tip.y, tip.z, 6, 0.1, 0.1, 0.1, 0.04);
               world.sendParticles(ParticleTypes.LAVA, tip.x, tip.y, tip.z, 2, 0.05, 0.05, 0.05, 0.0);
               world.playSound(null, tip.x, tip.y, tip.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, 1.4F + burst * 0.1F);
            });
         }).start();
      }
   }

   private static void spawnBigExplosion(ServerPlayer attacker, UUID vicId) {
      ServerLevel world = attacker.level();
      ServerPlayer victim = attacker.level().getServer().getPlayerList().getPlayer(vicId);
      if (victim != null) {
         Vec3 hitPos = victim.position().add(0.0, 1.6, 0.0);
         world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, hitPos.x, hitPos.y, hitPos.z, 3, 0.2, 0.2, 0.2, 0.0);
         world.sendParticles(ParticleTypes.FLAME, hitPos.x, hitPos.y, hitPos.z, 40, 0.3, 0.3, 0.3, 0.12);
         world.sendParticles(ParticleTypes.LAVA, hitPos.x, hitPos.y, hitPos.z, 20, 0.2, 0.2, 0.2, 0.0);
         world.sendParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 25, 0.3, 0.3, 0.3, 0.2);
         world.sendParticles(ParticleTypes.ENCHANTED_HIT, hitPos.x, hitPos.y, hitPos.z, 20, 0.2, 0.2, 0.2, 0.1);
         world.sendParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 8, 0.15, 0.1, 0.15, 0.05);
         world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hitPos.x, hitPos.y, hitPos.z, 15, 0.25, 0.25, 0.25, 0.08);
         world.playSound(null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP, SoundSource.PLAYERS, 2.0F, 0.5F);
         world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.8F);
         world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.4F);
      }
   }

   private static void applyVictimEffects(MinecraftServer server, UUID atkId, UUID vicId) {
      ServerPlayer victim = server.getPlayerList().getPlayer(vicId);
      ServerPlayer attacker = server.getPlayerList().getPlayer(atkId);
      if (victim != null) {
         victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 7, false, false));
         victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
         victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 3600, 1, false, false));
         victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
         ServerPlayNetworking.send(victim, new SlapHandler.ScreenClosePayload(vicId));
         long expiry = System.currentTimeMillis() + 180000L;
         neckBroken.put(vicId, expiry);
         ServerPlayNetworking.send(victim, new StrongSlapHandler.NeckBrokenPayload(vicId, 180000L));
         UUID atkIdFinal = atkId;
         new Thread(() -> {
            try {
               Thread.sleep(800L);
            } catch (InterruptedException var3x) {
            }

            server.execute(() -> {
               ServerPlayer a = server.getPlayerList().getPlayer(atkIdFinal);
               if (a != null) {
                  sendFreeze(a, false);
                  sendLookLock(a, false);
                  PoseNetworking.broadcastAnimState(a, 0);
                  StrongSlapCommand.disarm(atkIdFinal);
               }
            });
         }).start();
         sessions.remove(atkId);
         victimToAtk.remove(vicId);
      }
   }

   private static void cancelSession(MinecraftServer server, StrongSlapHandler.SlapSession session, String reason) {
      if (!session.cancelled) {
         session.cancelled = true;
         ServerPlayer atk = server.getPlayerList().getPlayer(session.attackerId);
         ServerPlayer vic = server.getPlayerList().getPlayer(session.victimId);
         if (atk != null) {
            PoseNetworking.broadcastAnimState(atk, 0);
            sendFreeze(atk, false);
            sendLookLock(atk, false);
            if (reason != null) {
               atk.sendOverlayMessage(Component.literal(reason));
            }
         }

         if (vic != null) {
            sendFreeze(vic, false);
            sendLookLock(vic, false);
         }

         sessions.remove(session.attackerId);
         victimToAtk.remove(session.victimId);
      }
   }

   private static void cureNeckBroken(ServerPlayer player) {
      UUID id = player.getUUID();
      neckBroken.remove(id);
      lastTortureMs.remove(id);
      lastResistDamageMs.remove(id);
      sendFreeze(player, false);
      sendLookLock(player, false);
      ServerPlayNetworking.send(player, new StrongSlapHandler.NeckBrokenPayload(id, 0L));
   }

   public static void onPlayerLeave(UUID id) {
      StrongSlapHandler.SlapSession s = sessions.remove(id);
      if (s != null) {
         victimToAtk.remove(s.victimId);
         s.cancelled = true;
      }

      UUID atkId = victimToAtk.remove(id);
      if (atkId != null) {
         StrongSlapHandler.SlapSession as = sessions.remove(atkId);
         if (as != null) {
            as.cancelled = true;
         }
      }

      lastResistDamageMs.remove(id);
   }

   private static void sendFreeze(ServerPlayer player, boolean frozen) {
      ServerPlayNetworking.send(player, new StrongSlapHandler.MoveFreezePayload(player.getUUID(), frozen));
   }

   private static void sendLookLock(ServerPlayer player, boolean locked) {
      float yaw = locked ? player.getYRot() : 0.0F;
      ServerPlayNetworking.send(player, new StrongSlapHandler.LookLockPayload(player.getUUID(), locked, yaw));
   }

   public static void sendLookLockPublic(ServerPlayer player, boolean locked) {
      sendLookLock(player, locked);
   }

   public static void sendLookLockAtYaw(ServerPlayer player, float targetYaw) {
      ServerPlayNetworking.send(player, new StrongSlapHandler.LookLockPayload(player.getUUID(), true, targetYaw));
   }

   private static ServerPlayer findBackVictim(ServerPlayer attacker) {
      Vec3 eye = attacker.getEyePosition();
      Vec3 look = attacker.getViewVector(1.0F);
      ServerPlayer best = null;
      double closest = 2.501;

      for (ServerPlayer p : attacker.level().players()) {
         if (!p.equals(attacker)) {
            double dist = attacker.position().distanceTo(p.position());
            if (!(dist >= closest)) {
               double lookDot = look.dot(p.getViewVector(1.0F));
               if (!(lookDot < 0.65)) {
                  Vec3 toHead = p.position().add(0.0, 1.6, 0.0).subtract(eye);
                  if (!(toHead.length() < 0.01) && !(toHead.normalize().dot(look) < 0.78)) {
                     best = p;
                     closest = dist;
                  }
               }
            }
         }
      }

      return best;
   }

   public static boolean isAttackerInSession(UUID id) {
      return sessions.containsKey(id);
   }

   public static boolean isVictimInSession(UUID id) {
      return victimToAtk.containsKey(id);
   }

   public record LookLockPayload(UUID playerId, boolean locked, float targetYaw) implements CustomPacketPayload {
      public static final Type<StrongSlapHandler.LookLockPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "look_lock"));
      public static final StreamCodec<FriendlyByteBuf, StrongSlapHandler.LookLockPayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeUUID(v.playerId());
         buf.writeBoolean(v.locked());
         buf.writeFloat(v.targetYaw());
      }, buf -> new StrongSlapHandler.LookLockPayload(buf.readUUID(), buf.readBoolean(), buf.readFloat()));

      public Type<StrongSlapHandler.LookLockPayload> type() {
         return ID;
      }
   }

   public record MoveFreezePayload(UUID playerId, boolean frozen) implements CustomPacketPayload {
      public static final Type<StrongSlapHandler.MoveFreezePayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "strong_slap_freeze"));
      public static final StreamCodec<FriendlyByteBuf, StrongSlapHandler.MoveFreezePayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeUUID(v.playerId());
         buf.writeBoolean(v.frozen());
      }, buf -> new StrongSlapHandler.MoveFreezePayload(buf.readUUID(), buf.readBoolean()));

      public Type<StrongSlapHandler.MoveFreezePayload> type() {
         return ID;
      }
   }

   public record NeckBrokenPayload(UUID playerId, long durationMs) implements CustomPacketPayload {
      public static final Type<StrongSlapHandler.NeckBrokenPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "neck_broken"));
      public static final StreamCodec<FriendlyByteBuf, StrongSlapHandler.NeckBrokenPayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeUUID(v.playerId());
         buf.writeLong(v.durationMs());
      }, buf -> new StrongSlapHandler.NeckBrokenPayload(buf.readUUID(), buf.readLong()));

      public Type<StrongSlapHandler.NeckBrokenPayload> type() {
         return ID;
      }
   }

   public record NeckResistPayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<StrongSlapHandler.NeckResistPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "neck_resist"));
      public static final StreamCodec<FriendlyByteBuf, StrongSlapHandler.NeckResistPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeUUID(v.playerId()), buf -> new StrongSlapHandler.NeckResistPayload(buf.readUUID())
      );

      public Type<StrongSlapHandler.NeckResistPayload> type() {
         return ID;
      }
   }

   private static class SlapSession {
      final UUID attackerId;
      final UUID victimId;
      Vec3 tpStart;
      Vec3 tpTarget;
      int tpTick = 0;
      int currentStage = 0;
      boolean qteOpen = false;
      long stageStart = 0L;
      long chargeAnimStartMs = 0L;
      boolean hitAnimStarted = false;
      boolean cancelled = false;

      SlapSession(UUID atk, UUID vic, Vec3 tpStart, Vec3 tpTarget) {
         this.attackerId = atk;
         this.victimId = vic;
         this.tpStart = tpStart;
         this.tpTarget = tpTarget;
      }
   }

   public record TortureTickPayload(UUID playerId, int randomColor) implements CustomPacketPayload {
      public static final Type<StrongSlapHandler.TortureTickPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "torture_tick"));
      public static final StreamCodec<FriendlyByteBuf, StrongSlapHandler.TortureTickPayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeUUID(v.playerId());
         buf.writeInt(v.randomColor());
      }, buf -> new StrongSlapHandler.TortureTickPayload(buf.readUUID(), buf.readInt()));

      public Type<StrongSlapHandler.TortureTickPayload> type() {
         return ID;
      }
   }
}
