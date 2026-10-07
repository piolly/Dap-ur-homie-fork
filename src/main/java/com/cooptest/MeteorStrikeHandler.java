package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MeteorStrikeHandler {
   public static final long ABILITY_DURATION_MS = 60000L;
   public static final long COUNTDOWN_MS = 3000L;
   public static final int CRATER_RADIUS = 10;
   public static final int DAMAGE_RADIUS = 20;
   private static final Map<UUID, Long> abilityExpiry = new HashMap<>();
   private static final Map<UUID, MeteorStrikeHandler.PendingMeteor> pendingMeteors = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.playC2S().register(MeteorStrikeHandler.MeteorFirePayload.ID, MeteorStrikeHandler.MeteorFirePayload.CODEC);
      PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorGrantPayload.ID, MeteorStrikeHandler.MeteorGrantPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorStatusPayload.ID, MeteorStrikeHandler.MeteorStatusPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorExpiredPayload.ID, MeteorStrikeHandler.MeteorExpiredPayload.CODEC);
   }

   public static void registerClientPayloads() {
      try {
         PayloadTypeRegistry.playC2S().register(MeteorStrikeHandler.MeteorFirePayload.ID, MeteorStrikeHandler.MeteorFirePayload.CODEC);
      } catch (Exception var4) {
      }

      try {
         PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorGrantPayload.ID, MeteorStrikeHandler.MeteorGrantPayload.CODEC);
      } catch (Exception var3) {
      }

      try {
         PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorStatusPayload.ID, MeteorStrikeHandler.MeteorStatusPayload.CODEC);
      } catch (Exception var2) {
      }

      try {
         PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorExpiredPayload.ID, MeteorStrikeHandler.MeteorExpiredPayload.CODEC);
      } catch (Exception var1) {
      }
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(MeteorStrikeHandler.MeteorFirePayload.ID, (payload, ctx) -> ctx.server().execute(() -> onFire(ctx.player())));
      ServerTickEvents.END_SERVER_TICK.register(MeteorStrikeHandler::tick);
   }

   public static void grantAbility(ServerPlayer p1, ServerPlayer p2) {
      long expiry = System.currentTimeMillis() + 60000L;
      abilityExpiry.put(p1.getUUID(), expiry);
      abilityExpiry.put(p2.getUUID(), expiry);

      try {
         ServerPlayNetworking.send(p1, new MeteorStrikeHandler.MeteorGrantPayload(expiry));
      } catch (Exception var7) {
      }

      try {
         ServerPlayNetworking.send(p2, new MeteorStrikeHandler.MeteorGrantPayload(expiry));
      } catch (Exception var6) {
      }

      for (ServerPlayer p : p1.level().getServer().getPlayerList().getPlayers()) {
         p.displayClientMessage(
            Component.literal(
               "§c☄ " + p1.getName().getString() + " §7and §c" + p2.getName().getString() + " §7have unlocked §c§lMETEOR STRIKE§7! Press §lG§7 to fire!"
            ),
            false
         );
      }
   }

   public static boolean hasAbility(UUID id) {
      return abilityExpiry.containsKey(id);
   }

   public static void cleanup(UUID id) {
      abilityExpiry.remove(id);
      pendingMeteors.remove(id);
   }

   private static void onFire(ServerPlayer player) {
      UUID id = player.getUUID();
      if (abilityExpiry.containsKey(id)) {
         if (!pendingMeteors.containsKey(id)) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            BlockPos target = null;

            for (double d = 1.0; d <= 80.0; d += 0.5) {
               Vec3 point = eye.add(look.scale(d));
               BlockPos bp = BlockPos.containing(point);
               if (!player.level().getBlockState(bp).isAir()) {
                  target = bp;
                  break;
               }
            }

            if (target == null) {
               Vec3 endpoint = eye.add(look.scale(80.0));
               target = BlockPos.containing(endpoint);
            }

            pendingMeteors.put(id, new MeteorStrikeHandler.PendingMeteor(id, target, player.level()));
            BlockPos finalTarget = target;
            Vec3 targetCenter = Vec3.atCenterOf(finalTarget);

            for (double d = 0.0; d < eye.distanceTo(targetCenter); d++) {
               Vec3 point = eye.add(look.scale(d));
               player.level().sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 2.0F, 0.5F);
            player.displayClientMessage(Component.literal("§c☄ METEOR INCOMING §7— impact in 3 seconds!"), true);
         }
      }
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      abilityExpiry.entrySet().removeIf(ex -> {
         if (now >= (Long)ex.getValue()) {
            ServerPlayer px = server.getPlayerList().getPlayer((UUID)ex.getKey());
            if (px != null) {
               try {
                  ServerPlayNetworking.send(px, new MeteorStrikeHandler.MeteorExpiredPayload());
               } catch (Exception var6x) {
               }
            }

            pendingMeteors.remove(ex.getKey());
            return true;
         } else {
            return false;
         }
      });

      for (MeteorStrikeHandler.PendingMeteor m : new ArrayList<>(pendingMeteors.values())) {
         long msLeft = m.impactTime - now;
         if (msLeft <= 500L && !m.invulnGranted) {
            m.invulnGranted = true;
            ServerPlayer p = server.getPlayerList().getPlayer(m.playerId);
            if (p != null) {
               p.setInvulnerable(true);
            }
         }

         if (server.getTickCount() % 2 == 0) {
            spawnCountdownPillar(m.world, m.target, (float)msLeft / 3000.0F);
         }

         if (now >= m.impactTime) {
            pendingMeteors.remove(m.playerId);
            abilityExpiry.remove(m.playerId);
            ServerPlayer p = server.getPlayerList().getPlayer(m.playerId);
            impact(m.world, m.target, p);
            if (p != null) {
               try {
                  ServerPlayNetworking.send(p, new MeteorStrikeHandler.MeteorExpiredPayload());
               } catch (Exception var12) {
               }

               ServerPlayer fp = p;
               server.execute(() -> fp.setInvulnerable(false));
            }
         } else {
            ServerPlayer p = server.getPlayerList().getPlayer(m.playerId);
            if (p != null) {
               long abilityLeft = Math.max(0L, abilityExpiry.getOrDefault(m.playerId, 0L) - now);

               try {
                  ServerPlayNetworking.send(p, new MeteorStrikeHandler.MeteorStatusPayload(abilityLeft, msLeft));
               } catch (Exception var13) {
               }
            }
         }
      }

      for (Entry<UUID, Long> e : abilityExpiry.entrySet()) {
         if (!pendingMeteors.containsKey(e.getKey())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && server.getTickCount() % 5 == 0) {
               long abilityLeft = Math.max(0L, e.getValue() - now);

               try {
                  ServerPlayNetworking.send(p, new MeteorStrikeHandler.MeteorStatusPayload(abilityLeft, -1L));
               } catch (Exception var11) {
               }
            }
         }
      }
   }

   private static void spawnCountdownPillar(ServerLevel world, BlockPos target, float progress) {
      double x = target.getX() + 0.5;
      double z = target.getZ() + 0.5;
      int height = (int)(50.0F * progress) + 5;

      for (int y = 0; y < height; y += 3) {
         world.sendParticles(ParticleTypes.FLAME, x, target.getY() + y, z, 1, 0.3, 0.0, 0.3, 0.05);
      }

      for (int i = 0; i < 12; i++) {
         double angle = Math.toRadians(i * 30.0 + System.currentTimeMillis() / 100.0 % 360.0);
         double radius = 3.0 * progress + 0.5;
         world.sendParticles(ParticleTypes.CRIT, x + Math.cos(angle) * radius, target.getY() + 0.5, z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
      }

      if (progress < 0.5F) {
         world.playSound(null, x, target.getY(), z, (SoundEvent)SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.5F, 0.5F + (1.0F - progress));
      }
   }

   private static void impact(ServerLevel world, BlockPos target, ServerPlayer shooter) {
      Vec3 center = Vec3.atCenterOf(target);
      int r = 10;

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r; y++) {
            for (int z = -r; z <= r; z++) {
               if (x * x + y * y + z * z <= r * r) {
                  BlockPos bp = target.offset(x, y, z);
                  BlockState state = world.getBlockState(bp);
                  if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
                     world.destroyBlock(bp, false);
                  }
               }
            }
         }
      }

      for (int i = 0; i < 8; i++) {
         double angle = Math.toRadians(i * 45.0);
         double ex = center.x + Math.cos(angle) * 12.0;
         double ez = center.z + Math.sin(angle) * 12.0;
         world.explode(shooter, ex, center.y, ez, 12.0F, true, ExplosionInteraction.TNT);
      }

      world.explode(shooter, center.x, center.y, center.z, 20.0F, true, ExplosionInteraction.TNT);
      AABB hitBox = new AABB(center, center).inflate(20.0);

      for (Entity e : world.getEntities(shooter, hitBox)) {
         if (e instanceof LivingEntity living) {
            double dist = e.position().distanceTo(center);
            if (!(dist > 20.0)) {
               float dmg = (float)(25.0 * (1.0 - dist / 20.0));
               living.hurtServer(world, world.damageSources().explosion(null, shooter), dmg);
               Vec3 dir = e.position().subtract(center).normalize();
               if (dir.lengthSqr() < 0.001) {
                  dir = new Vec3(0.0, 1.0, 0.0);
               }

               living.push(dir.x * 3.0, 2.0, dir.z * 3.0);
               living.hurtMarked = true;
            }
         }
      }

      world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 20, 5.0, 5.0, 5.0, 0.0);
      world.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 200, 8.0, 4.0, 8.0, 0.5);
      world.playSound(null, center.x, center.y, center.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 5.0F, 0.3F);
      world.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0F, 0.5F);
   }

   public record MeteorExpiredPayload() implements CustomPacketPayload {
      public static final Type<MeteorStrikeHandler.MeteorExpiredPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "meteor_expired"));
      public static final StreamCodec<FriendlyByteBuf, MeteorStrikeHandler.MeteorExpiredPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> {}, buf -> new MeteorStrikeHandler.MeteorExpiredPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record MeteorFirePayload() implements CustomPacketPayload {
      public static final Type<MeteorStrikeHandler.MeteorFirePayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "meteor_fire"));
      public static final StreamCodec<FriendlyByteBuf, MeteorStrikeHandler.MeteorFirePayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> {}, buf -> new MeteorStrikeHandler.MeteorFirePayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record MeteorGrantPayload(long expiryMs) implements CustomPacketPayload {
      public static final Type<MeteorStrikeHandler.MeteorGrantPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "meteor_grant"));
      public static final StreamCodec<FriendlyByteBuf, MeteorStrikeHandler.MeteorGrantPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> buf.writeLong(p.expiryMs), buf -> new MeteorStrikeHandler.MeteorGrantPayload(buf.readLong())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record MeteorStatusPayload(long remainingAbilityMs, long countdownMs) implements CustomPacketPayload {
      public static final Type<MeteorStrikeHandler.MeteorStatusPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "meteor_status"));
      public static final StreamCodec<FriendlyByteBuf, MeteorStrikeHandler.MeteorStatusPayload> CODEC = StreamCodec.ofMember((p, buf) -> {
         buf.writeLong(p.remainingAbilityMs);
         buf.writeLong(p.countdownMs);
      }, buf -> new MeteorStrikeHandler.MeteorStatusPayload(buf.readLong(), buf.readLong()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static class PendingMeteor {
      final UUID playerId;
      final BlockPos target;
      final long impactTime;
      final ServerLevel world;
      boolean invulnGranted = false;

      PendingMeteor(UUID playerId, BlockPos target, ServerLevel world) {
         this.playerId = playerId;
         this.target = target;
         this.world = world;
         this.impactTime = System.currentTimeMillis() + 3000L;
      }
   }
}
