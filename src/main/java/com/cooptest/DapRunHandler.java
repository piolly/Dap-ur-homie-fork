package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.Vec3;

public class DapRunHandler {
   public static final double DETECT_RANGE = 4.0;
   public static final double SLOT_FORWARD = 0.4;
   public static final double SLOT_HEIGHT = 1.0;
   public static final double SIDE_SIGN = -1.0;
   public static final double SLOT_LEFT = 0.85;
   public static final double SLOT_RADIUS = 1.4;
   public static final long IMPACT_MS = 210L;
   public static final long ANIM_MS = 833L;
   public static final double CATCHUP_MAX = 0.3;
   public static final long CATCHUP_MS = 200L;
   public static final double CATCHUP_SIDE_MIX = 0.55;
   public static final double CATCHUP_SIDE_EXTRA = 0.55;
   public static final double SLOW_MULT_BASE = -0.22;
   public static final double SLOW_MULT_PER_TIER = -0.09;
   public static final long SLOW_DURATION_MS = 260L;
   public static final long COOLDOWN_MS = 600L;
   public static final long RELEASE_SYNC_MS = 400L;
   public static final int ANIM_DAP_RUN = 126;
   private static final Map<UUID, Long> cooldown = new HashMap<>();
   private static final Map<UUID, Long> legBoost = new HashMap<>();
   private static final Map<UUID, Long> justFired = new HashMap<>();
   private static final Map<UUID, Long> armed = new HashMap<>();
   private static final long JUST_FIRED_MS = 300L;
   private static final List<DapRunHandler.Pending> pendingImpact = new ArrayList<>();
   private static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath("testcoop", "dap_run_slow");
   private static final List<DapRunHandler.PendingSlow> pendingSlow = new ArrayList<>();

   public static boolean justFired(UUID id) {
      Long t = justFired.get(id);
      return t != null && System.currentTimeMillis() - t < 300L;
   }

   public static void registerPayloads() {
      PayloadTypeRegistry.clientboundPlay().register(DapRunHandler.DapRunStartPayload.ID, DapRunHandler.DapRunStartPayload.CODEC);
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         long now = System.currentTimeMillis();
         cooldown.entrySet().removeIf(e -> now > e.getValue());
         justFired.entrySet().removeIf(e -> now - e.getValue() > 300L);
         armed.entrySet().removeIf(e -> now - e.getValue() > 400L);
         tickLegBoost(server, now);
         Iterator<DapRunHandler.Pending> it = pendingImpact.iterator();

         while (it.hasNext()) {
            DapRunHandler.Pending p = it.next();
            if (now >= p.fireAt()) {
               doImpact(p.p1(), p.p2(), p.tier());
               it.remove();
            }
         }

         Iterator<DapRunHandler.PendingSlow> sit = pendingSlow.iterator();

         while (sit.hasNext()) {
            DapRunHandler.PendingSlow ps = sit.next();
            if (now >= ps.removeAt()) {
               removeImpactSlow(ps.player());
               sit.remove();
            }
         }
      });
   }

   public static boolean tryDapRun(ServerPlayer a, ServerPlayer b) {
      long now = System.currentTimeMillis();
      if (!isOnCooldown(a) && !isOnCooldown(b)) {
         if (a.level() != b.level()) {
            return false;
         }

         Vec3 pa = a.position();
         Vec3 pb = b.position();
         if (Math.hypot(pb.x - pa.x, pb.z - pa.z) > 4.0) {
            return false;
         }

         Vec3 sa = leftSlot(a);
         Vec3 sb = leftSlot(b);
         if (Math.hypot(sa.x - sb.x, sa.z - sb.z) > 1.4) {
            return false;
         }

         Long partnerArmed = armed.get(b.getUUID());
         if (partnerArmed != null && now - partnerArmed <= 400L) {
            armed.remove(a.getUUID());
            armed.remove(b.getUUID());
            boolean aFast = a.isSprinting();
            boolean bFast = b.isSprinting();
            int tier = aFast && bFast ? 3 : (!aFast && !bFast ? 1 : 2);
            cooldown.put(a.getUUID(), now + 600L);
            cooldown.put(b.getUUID(), now + 600L);
            if (tier >= 2) {
               DapFlair.openDapRun(a.level(), a.position().add(b.position()).scale(0.5), a, b, 210L, tier);
            }

            justFired.put(a.getUUID(), now);
            justFired.put(b.getUUID(), now);
            PoseNetworking.broadcastAnimState(a, 126);
            PoseNetworking.broadcastAnimState(b, 126);
            if (aFast && !bFast) {
               catchUp(b, a);
            } else if (bFast && !aFast) {
               catchUp(a, b);
            } else if (!aFast && !bFast) {
               catchUp(a, b);
               catchUp(b, a);
            }

            DapRunHandler.DapRunStartPayload payload = new DapRunHandler.DapRunStartPayload(tier);
            ServerPlayNetworking.send(a, payload);
            ServerPlayNetworking.send(b, payload);
            ChargedDapHandler.applyDapCooldown(a);
            ChargedDapHandler.applyDapCooldown(b);
            pendingImpact.add(new DapRunHandler.Pending(a, b, tier, now + 210L));
            return true;
         } else {
            armed.put(a.getUUID(), now);
            justFired.put(a.getUUID(), now);
            return true;
         }
      } else {
         return false;
      }
   }

   public static Vec3 slotOf(Vec3 pos, float yaw) {
      double rad = Math.toRadians(yaw);
      Vec3 fwd = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
      Vec3 left = new Vec3(Math.cos(rad), 0.0, Math.sin(rad));
      double side = -0.85;
      return new Vec3(pos.x + fwd.x * 0.4 + left.x * side, pos.y + 1.0, pos.z + fwd.z * 0.4 + left.z * side);
   }

   private static Vec3 leftSlot(ServerPlayer p) {
      return slotOf(p.position(), p.getYRot());
   }

   private static void catchUp(ServerPlayer mover, ServerPlayer target) {
      double rad = Math.toRadians(target.getYRot());
      Vec3 tLeft = new Vec3(Math.cos(rad), 0.0, Math.sin(rad));
      Vec3 to = leftSlot(target).add(tLeft.scale(-0.55));
      Vec3 from = mover.position();
      Vec3 delta = new Vec3(to.x - from.x, 0.0, to.z - from.z);
      double len = Math.hypot(delta.x, delta.z);
      if (!(len < 0.05)) {
         Vec3 toward = new Vec3(target.position().x - from.x, 0.0, target.position().z - from.z);
         double tl = Math.hypot(toward.x, toward.z);
         if (tl > 0.001) {
            toward = toward.scale(1.0 / tl);
            double along = delta.x * toward.x + delta.z * toward.z;
            Vec3 alongVec = toward.scale(along);
            Vec3 sideVec = delta.subtract(alongVec).scale(0.55);
            delta = alongVec.add(sideVec);
            len = Math.hypot(delta.x, delta.z);
            if (len < 0.05) {
               return;
            }
         }

         if (len > 0.3) {
            delta = delta.scale(0.3 / len);
         }

         DapPositioning.slideBy(mover, delta, (int)Math.max(1L, 4L));
      }
   }

   public static void onRunFlair(ServerPlayer p, boolean perfectRun) {
      if (p != null && p.isAlive()) {
         CoopMovesConfig c = CoopMovesConfig.get();
         int secs = Math.max(1, c.dapRunFlairBurstSec);
         p.addEffect(new MobEffectInstance(MobEffects.SPEED, secs * 20, Math.max(0, c.dapRunFlairBurstAmp), false, true));
         legBoost.put(p.getUUID(), System.currentTimeMillis() + secs * 1000L);
         if (p.level() instanceof ServerLevel w) {
            double var15 = p.getX();
            double y = p.getY();
            double z = p.getZ();
            Vec3 look = p.getLookAngle();
            w.sendParticles(ParticleTypes.SONIC_BOOM, var15 + look.x * 1.5, y + 1.0 + look.y * 0.5, z + look.z * 1.5, 1, 0.0, 0.0, 0.0, 0.0);
            w.playSound(null, var15, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, perfectRun ? 1.2F : 0.8F, perfectRun ? 1.4F : 1.7F);
            w.playSound(null, var15, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.5F);

            for (int i = 0; i < 16; i++) {
               double a = (Math.PI * 2) * i / 16.0;
               w.sendParticles(ParticleTypes.CLOUD, var15 + Math.cos(a) * 0.6, y + 0.05, z + Math.sin(a) * 0.6, 1, 0.0, 0.02, 0.0, 0.02);
            }
         }
      }
   }

   private static void tickLegBoost(MinecraftServer server, long now) {
      if (!legBoost.isEmpty()) {
         Iterator<Entry<UUID, Long>> lit = legBoost.entrySet().iterator();

         while (lit.hasNext()) {
            Entry<UUID, Long> e = lit.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && p.isAlive() && now <= e.getValue()) {
               if (p.level() instanceof ServerLevel w) {
                  double yaw = Math.toRadians(p.getYRot());
                  double rx = -Math.cos(yaw);
                  double rz = -Math.sin(yaw);

                  for (byte side = -1; side <= 1; side += 2) {
                     double fx = p.getX() + rx * 0.18 * side;
                     double fz = p.getZ() + rz * 0.18 * side;
                     w.sendParticles(ParticleTypes.ELECTRIC_SPARK, fx, p.getY() + 0.12, fz, 1, 0.03, 0.02, 0.03, 0.01);
                     w.sendParticles(ParticleTypes.CLOUD, fx, p.getY() + 0.05, fz, 1, 0.05, 0.0, 0.05, 0.005);
                  }
               } else {
                  lit.remove();
               }
            } else {
               lit.remove();
            }
         }
      }
   }

   public static void clearLegBoost(UUID id) {
      legBoost.remove(id);
   }

   private static void doImpact(ServerPlayer a, ServerPlayer b, int tier) {
      if (a.isAlive() && b.isAlive()) {
         ServerLevel world = a.level();
         Vec3 mid = a.position().add(b.position()).scale(0.5).add(0.0, 1.4, 0.0);
         float pitch = 1.0F + tier * 0.12F;
         world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F + tier * 0.2F, pitch);
         if (tier >= 2) {
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.8F);
         }

         applyImpactSlow(a, tier);
         applyImpactSlow(b, tier);
         CoopMovesConfig cfg = CoopMovesConfig.get();
         DapFlair.FlairShakePayload shake = new DapFlair.FlairShakePayload(cfg.dapRunShake * tier / 2.0F, cfg.dapRunShakeMs);
         ServerPlayNetworking.send(a, shake);
         ServerPlayNetworking.send(b, shake);
         if (tier >= 2) {
            DapHearts.add(a, cfg.dapRunHearts);
            DapHearts.add(b, cfg.dapRunHearts);
         }

         int count = 4 + tier * 4;
         world.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, count, 0.15, 0.15, 0.15, 0.12);
         if (tier >= 2) {
            world.sendParticles(ParticleTypes.FIREWORK, mid.x, mid.y, mid.z, tier * 3, 0.1, 0.1, 0.1, 0.06);
         }
      }
   }

   private static void applyImpactSlow(ServerPlayer p, int tier) {
      AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         double mult = -0.22 + -0.09 * (tier - 1);
         attr.removeModifier(SLOW_ID);
         attr.addPermanentModifier(new AttributeModifier(SLOW_ID, mult, Operation.ADD_MULTIPLIED_TOTAL));
         pendingSlow.add(new DapRunHandler.PendingSlow(p, System.currentTimeMillis() + 260L));
      }
   }

   private static void removeImpactSlow(ServerPlayer p) {
      AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         attr.removeModifier(SLOW_ID);
      }
   }

   private static boolean isOnCooldown(ServerPlayer p) {
      Long until = cooldown.get(p.getUUID());
      return until != null && System.currentTimeMillis() < until;
   }

   public static void cleanup(UUID playerId) {
      cooldown.remove(playerId);
      justFired.remove(playerId);
      armed.remove(playerId);
      pendingImpact.removeIf(p -> p.p1().getUUID().equals(playerId) || p.p2().getUUID().equals(playerId));
      pendingSlow.removeIf(p -> {
         if (!p.player().getUUID().equals(playerId)) {
            return false;
         }

         removeImpactSlow(p.player());
         return true;
      });
   }

   public record DapRunStartPayload(int tier) implements CustomPacketPayload {
      public static final Type<DapRunHandler.DapRunStartPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "dap_run_start"));
      public static final StreamCodec<FriendlyByteBuf, DapRunHandler.DapRunStartPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeVarInt(v.tier()), buf -> new DapRunHandler.DapRunStartPayload(buf.readVarInt())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private record Pending(ServerPlayer p1, ServerPlayer p2, int tier, long fireAt) {
   }

   private record PendingSlow(ServerPlayer player, long removeAt) {
   }
}
