package com.cooptest.spin;

import com.cooptest.HighFiveHandler;
import com.cooptest.PoseNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public class HandSpinArmorStandTest {
   private static final Map<UUID, HandSpinArmorStandTest.TestSession> sessions = new HashMap<>();
   private static boolean tickRegistered = false;

   public static void ensureTickRegistered() {
      if (!tickRegistered) {
         tickRegistered = true;
         ServerTickEvents.END_SERVER_TICK.register(HandSpinArmorStandTest::tick);
      }
   }

   public static int start(ServerPlayer player) {
      ensureTickRegistered();
      UUID id = player.getUUID();
      if (sessions.containsKey(id)) {
         player.sendSystemMessage(Component.literal("§eAlready running — /testspin stop first"));
         return 0;
      } else {
         Vec3 fwd = HighFiveHandler.horizontalForward(player);
         double spawnDist = 1.5;
         Vec3 playerPos = player.position();
         Vec3 standPos = new Vec3(playerPos.x + fwd.x * spawnDist, playerPos.y, playerPos.z + fwd.z * spawnDist);
         ArmorStand stand = new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, player.level());
         stand.setPosRaw(standPos.x, standPos.y, standPos.z);
         stand.setPermanentlyInvulnerable(true);
         stand.setCustomName(Component.literal("HandSpin Test Partner"));
         stand.setCustomNameVisible(true);
         player.level().addFreshEntity(stand);
         Vec3 center = playerPos.add(standPos).scale(0.5);
         double startAngle = Math.atan2(playerPos.z - center.z, playerPos.x - center.x);
         HandSpinArmorStandTest.TestSession s = new HandSpinArmorStandTest.TestSession(id, center, startAngle, player.level().getServer().getTickCount());
         s.stand = stand;
         s.continuousYaw1 = player.getYRot();
         s.continuousYaw2 = stand.getYRot();
         sessions.put(id, s);
         ServerLevel world = player.level();
         world.playSound(null, center.x, center.y + 1.0, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.3F);
         world.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 1.0, center.z, 14, 0.5, 0.4, 0.5, 0.03);
         world.sendParticles(ParticleTypes.CRIT, center.x, center.y + 1.0, center.z, 10, 0.4, 0.3, 0.4, 0.05);
         player.sendSystemMessage(Component.literal("§aHand Spin test started. §7/testspin cancel | cancel slow | damage | stop"));
         return 1;
      }
   }

   public static int cancel(ServerPlayer player, boolean slow) {
      HandSpinArmorStandTest.TestSession s = sessions.get(player.getUUID());
      if (s == null) {
         player.sendSystemMessage(Component.literal("§cNo test spin running — /testspin first"));
         return 0;
      } else if (s.stage != HandSpinArmorStandTest.Stage.RAMPING_UP && s.stage != HandSpinArmorStandTest.Stage.SPINNING) {
         player.sendSystemMessage(Component.literal("§cAlready ending"));
         return 0;
      } else {
         s.stage = slow ? HandSpinArmorStandTest.Stage.ENDING_SLOW : HandSpinArmorStandTest.Stage.ENDING_FAST;
         player.sendSystemMessage(
            Component.literal(slow ? "§7Branch B: slow decel to a full stop, then separate." : "§7Branch A: fast decel then push-apart."));
         return 1;
      }
   }

   public static int damage(ServerPlayer player) {
      HandSpinArmorStandTest.TestSession s = sessions.get(player.getUUID());
      if (s == null) {
         player.sendSystemMessage(Component.literal("§cNo test spin running — /testspin first"));
         return 0;
      } else if (s.stage != HandSpinArmorStandTest.Stage.RAMPING_UP && s.stage != HandSpinArmorStandTest.Stage.SPINNING) {
         player.sendSystemMessage(Component.literal("§cAlready ending"));
         return 0;
      } else {
         s.stage = HandSpinArmorStandTest.Stage.DAMAGE_CUT;
         s.endAnimStartTick = -1L;
         player.sendSystemMessage(Component.literal("§cBranch C: damage interrupt — instant cut, big knockback, no end anim."));
         return 1;
      }
   }

   public static int stop(ServerPlayer player) {
      HandSpinArmorStandTest.TestSession s = sessions.remove(player.getUUID());
      if (s == null) {
         return 0;
      }

      if (s.stand != null) {
         s.stand.discard();
      }

      PoseNetworking.broadcastAnimState(player, 0);
      player.sendSystemMessage(Component.literal("§7Test spin force-stopped."));
      return 1;
   }

   private static void tick(MinecraftServer server) {
      if (!sessions.isEmpty()) {
         for (HandSpinArmorStandTest.TestSession s : new ArrayList<>(sessions.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.playerId);
            if (player != null && s.stand != null && s.stand.isAlive()) {
               switch (s.stage) {
                  case RAMPING_UP:
                  case SPINNING:
                     long elapsedMs = (server.getTickCount() - s.startTick) * 50L;
                     if (s.stage == HandSpinArmorStandTest.Stage.RAMPING_UP) {
                        if (elapsedMs == 0L) {
                           PoseNetworking.broadcastAnimState(player, 120);
                        }

                        if (elapsedMs >= 334L) {
                           PoseNetworking.broadcastAnimState(player, 121);
                           s.stage = HandSpinArmorStandTest.Stage.SPINNING;
                        }
                     }

                     s.angularVel = Math.min(0.2, s.angularVel + 0.0037);
                     s.angle = s.angle + s.angularVel;
                     applyOrbit(s, player);
                     swingHandIfDue(s, player, server);
                     playLoopEffects(s, player, server);
                     refreshShakePulse(s, player);
                     break;
                  case ENDING_FAST:
                     s.angularVel = Math.max(0.0, s.angularVel - 0.006);
                     s.angle = s.angle + s.angularVel;
                     applyOrbit(s, player);
                     swingHandIfDue(s, player, server);
                     if (s.angularVel <= 1.0E-4) {
                        beginPushApart(s, player);
                     }
                     break;
                  case ENDING_SLOW:
                     s.angularVel = Math.max(0.0, s.angularVel - 0.0015);
                     s.angle = s.angle + s.angularVel;
                     applyOrbit(s, player);
                     swingHandIfDue(s, player, server);
                     if (s.angularVel <= 1.0E-4) {
                        finishToEnd(s, player, server);
                     }
                     break;
                  case DAMAGE_CUT:
                     if (s.endAnimStartTick == -1L) {
                        beginDamageCutKnockback(s, player, server);
                     } else if (server.getTickCount() - s.endAnimStartTick >= 2L) {
                        PoseNetworking.broadcastAnimState(player, 0);
                        s.stand.discard();
                        sessions.remove(s.playerId);
                        player.sendSystemMessage(Component.literal("§cTest complete — damage cut."));
                     }
                     break;
                  case PUSH_APART:
                     if (s.pushTicksRemaining > 0) {
                        Vec3 step = s.playerRemainingDelta.scale(1.0 / s.pushTicksRemaining);
                        Vec3 np = player.position().add(step);
                        player.teleportTo(np.x, np.y, np.z);
                        s.playerRemainingDelta = s.playerRemainingDelta.subtract(step);
                        Vec3 standStep = s.standRemainingDelta.scale(1.0 / s.pushTicksRemaining);
                        Vec3 nsp = s.stand.position().add(standStep);
                        s.stand.teleportTo(nsp.x, nsp.y, nsp.z);
                        s.standRemainingDelta = s.standRemainingDelta.subtract(standStep);
                        s.pushTicksRemaining--;
                     } else {
                        finishToEnd(s, player, server);
                     }
                     break;
                  case DONE:
                     if (s.endAnimStartTick != -1L && (server.getTickCount() - s.endAnimStartTick) * 50L >= 584L) {
                        PoseNetworking.broadcastAnimState(player, 0);
                        s.stand.discard();
                        sessions.remove(s.playerId);
                        player.sendSystemMessage(Component.literal("§aTest complete."));
                     }
               }
            } else {
               if (s.stand != null) {
                  s.stand.discard();
               }

               sessions.remove(s.playerId);
            }
         }
      }
   }

   private static void swingHandIfDue(HandSpinArmorStandTest.TestSession s, ServerPlayer player, MinecraftServer server) {
      long tick = server.getTickCount();
      if (s.lastHandSwingTick == -1L || tick - s.lastHandSwingTick >= 4L) {
         s.lastHandSwingTick = tick;
         player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
      }
   }

   private static void playLoopEffects(HandSpinArmorStandTest.TestSession s, ServerPlayer player, MinecraftServer server) {
      ServerLevel world = player.level();
      world.sendParticles(ParticleTypes.CRIT, s.center.x, s.center.y + 1.0, s.center.z, 2, 0.5, 0.4, 0.5, 0.02);
      if (server.getTickCount() % 10 == 0) {
         world.playSound(null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.6F);
      }
   }

   private static void refreshShakePulse(HandSpinArmorStandTest.TestSession s, ServerPlayer player) {
      long now = System.currentTimeMillis();
      if (now - s.lastShakePulseMs >= 150L) {
         s.lastShakePulseMs = now;
         ServerPlayNetworking.send(player, new HandSpinHandler.HandSpinShakePulsePayload(0.12F, 200L));
      }
   }

   private static void beginDamageCutKnockback(HandSpinArmorStandTest.TestSession s, ServerPlayer player, MinecraftServer server) {
      Vec3 pos1 = player.position();
      Vec3 pos2 = s.stand.position();
      Vec3 out1 = pos1.subtract(s.center);
      Vec3 out2 = pos2.subtract(s.center);
      double len1 = Math.max(0.001, out1.horizontalDistance());
      double len2 = Math.max(0.001, out2.horizontalDistance());
      Vec3 dir1 = new Vec3(out1.x / len1, 0.0, out1.z / len1);
      Vec3 dir2 = new Vec3(out2.x / len2, 0.0, out2.z / len2);
      player.setDeltaMovement(player.getDeltaMovement().add(dir1.scale(0.75)).add(0.0, 0.15, 0.0));
      player.syncVelocity = true;
      Vec3 standTarget = pos2.add(dir2.scale(1.5));
      s.stand.teleportTo(standTarget.x, standTarget.y, standTarget.z);
      PoseNetworking.broadcastAnimState(player, 0);
      ServerLevel world = player.level();
      world.playSound(null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.7F);
      s.endAnimStartTick = server.getTickCount();
   }

   private static void applyOrbit(HandSpinArmorStandTest.TestSession s, ServerPlayer player) {
      double x1 = s.center.x + Math.cos(s.angle) * 0.75;
      double z1 = s.center.z + Math.sin(s.angle) * 0.75;
      double x2 = s.center.x + Math.cos(s.angle + Math.PI) * 0.75;
      double z2 = s.center.z + Math.sin(s.angle + Math.PI) * 0.75;
      double y = s.center.y;
      player.teleportTo(x1, y, z1);
      s.stand.teleportTo(x2, y, z2);
      float yaw1 = (float)Math.toDegrees(Math.atan2(x1 - s.center.x, z1 - s.center.z));
      float yaw2 = (float)Math.toDegrees(Math.atan2(x2 - s.center.x, z2 - s.center.z));
      player.setYRot(yaw1);
      player.setYHeadRot(yaw1);
      player.setYBodyRot(yaw1);
      s.stand.setYRot(yaw2);
      s.stand.setYHeadRot(yaw2);
      s.stand.setYBodyRot(yaw2);
   }

   private static float unwrapYaw(Float previousContinuous, float rawTargetYaw) {
      if (previousContinuous == null) {
         return rawTargetYaw;
      }

      float delta = Mth.wrapDegrees(rawTargetYaw - previousContinuous);
      return previousContinuous + delta;
   }

   private static void beginPushApart(HandSpinArmorStandTest.TestSession s, ServerPlayer player) {
      Vec3 pos1 = player.position();
      Vec3 pos2 = s.stand.position();
      Vec3 out1 = pos1.subtract(s.center);
      Vec3 out2 = pos2.subtract(s.center);
      double len1 = Math.max(0.001, out1.horizontalDistance());
      double len2 = Math.max(0.001, out2.horizontalDistance());
      Vec3 dir1 = new Vec3(out1.x / len1, 0.0, out1.z / len1);
      Vec3 dir2 = new Vec3(out2.x / len2, 0.0, out2.z / len2);
      player.setDeltaMovement(player.getDeltaMovement().add(dir1.scale(0.35)));
      player.syncVelocity = true;
      s.playerRemainingDelta = dir1.scale(0.6);
      s.standRemainingDelta = dir2.scale(0.6);
      s.pushTicksRemaining = 6;
      s.stage = HandSpinArmorStandTest.Stage.PUSH_APART;
   }

   private static void finishToEnd(HandSpinArmorStandTest.TestSession s, ServerPlayer player, MinecraftServer server) {
      PoseNetworking.broadcastAnimState(player, 122);
      s.endAnimStartTick = server.getTickCount();
      s.stage = HandSpinArmorStandTest.Stage.DONE;
   }

   private enum Stage {
      RAMPING_UP,
      SPINNING,
      ENDING_FAST,
      ENDING_SLOW,
      DAMAGE_CUT,
      PUSH_APART,
      DONE;
   }

   private static class TestSession {
      final UUID playerId;
      ArmorStand stand;
      Vec3 center;
      double angle;
      double angularVel = 0.0;
      HandSpinArmorStandTest.Stage stage = HandSpinArmorStandTest.Stage.RAMPING_UP;
      long startTick;
      long endAnimStartTick = -1L;
      Vec3 playerRemainingDelta;
      Vec3 standRemainingDelta;
      int pushTicksRemaining = 0;
      long lastHandSwingTick = -1L;
      long lastShakePulseMs = 0L;
      Float continuousYaw1 = null;
      Float continuousYaw2 = null;

      TestSession(UUID playerId, Vec3 center, double startAngle, long startTick) {
         this.playerId = playerId;
         this.center = center;
         this.angle = startAngle;
         this.startTick = startTick;
      }
   }
}
