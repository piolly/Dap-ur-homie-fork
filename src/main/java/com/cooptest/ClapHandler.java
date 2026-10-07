package com.cooptest;

import com.cooptest.client.CoopAnimationHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ClapHandler {
   private static final long TIER_SLOW_MS = 600L;
   private static final long TIER_MEDIUM_MS = 200L;
   private static final long SYNC_WINDOW_MS = 300L;
   private static final double SYNC_RANGE = 6.0;
   private static final int IMPACT_TICKS_SLOW = 5;
   private static final int IMPACT_TICKS_MEDIUM = 3;
   private static final int IMPACT_TICKS_FAST = 2;
   private static final Random RANDOM = new Random();
   private static final Map<UUID, Long> lastPressTime = new HashMap<>();
   private static final List<ClapHandler.ScheduledEffect> scheduled = new ArrayList<>();
   private static final Set<UUID> rightClickHeld = new HashSet<>();
   private static final Map<UUID, Entity> firstTarget = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(ClapHandler.ClapRequestPayload.ID, ClapHandler.ClapRequestPayload.CODEC);
      registerTodoPayload();
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(ClapHandler.ClapRequestPayload.ID, (payload, context) -> {
         ServerPlayer player = context.player();
         context.server().execute(() -> onClapRequest(player));
      });
      ServerTickEvents.END_SERVER_TICK.register(ClapHandler::tick);
      registerTodoReceiver();
   }

   private static void onClapRequest(ServerPlayer player) {
      if (!GrabMechanic.isHolding(player)) {
         if (!KickHandler.isBusy(player)) {
            UUID id = player.getUUID();
            long now = System.currentTimeMillis();
            Long last = lastPressTime.get(id);
            int tier;
            if (last == null || now - last > 600L) {
               tier = 0;
            } else if (now - last > 200L) {
               tier = 1;
            } else {
               tier = 2;
            }

            lastPressTime.put(id, now);
            if (player.hasEffect(ModEffects.TODO)) {
               performTodoSwap(player);
            }
            CoopAnimationHandler.AnimState animState = switch (tier) {
               case 1 -> CoopAnimationHandler.AnimState.CLAP_SPAM;
               case 2 -> CoopAnimationHandler.AnimState.CLAP_STRONG;
               default -> CoopAnimationHandler.AnimState.CLAP;
            };
            PoseNetworking.broadcastAnimState(player, animState.ordinal());
            if (tier == 2) {
               AABB fearBox = player.getBoundingBox().inflate(15.0);
               player.level().getEntitiesOfClass(Animal.class, fearBox, a -> !a.isRemoved()).forEach(animal -> {
                  Vec3 away = animal.position().subtract(player.position());
                  if (away.horizontalDistanceSqr() < 0.001) {
                     away = new Vec3(1.0, 0.0, 0.0);
                  }

                  away = away.normalize();
                  animal.setDeltaMovement(away.x * 0.55, 0.35, away.z * 0.55);
                  animal.hurtMarked = true;
                  if (animal instanceof Mob mob) {
                     mob.setTarget(null);
                     Vec3 fleeTarget = animal.position().add(away.scale(8.0));
                     animal.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 1.4);
                  }
               });
            }

            boolean isSync = false;

            for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
               if (other != player) {
                  Long otherLast = lastPressTime.get(other.getUUID());
                  if (otherLast != null && now - otherLast <= 300L && player.position().distanceTo(other.position()) <= 6.0) {
                     isSync = true;
                     break;
                  }
               }
            }

            double yawRad = Math.toRadians(player.yBodyRot);
            Vec3 armTip = player.position().add(-Math.sin(yawRad) * 0.5, 1.2, Math.cos(yawRad) * 0.5);

            int delay = switch (tier) {
               case 1 -> 3;
               case 2 -> 2;
               default -> 5;
            };
            scheduled.add(new ClapHandler.ScheduledEffect(player.level(), armTip, isSync, tier, delay, player));
         }
      }
   }

   private static void tick(MinecraftServer server) {
      Iterator<ClapHandler.ScheduledEffect> it = scheduled.iterator();

      while (it.hasNext()) {
         ClapHandler.ScheduledEffect fx = it.next();
         if (--fx.ticksRemaining <= 0) {
            playEffects(fx);
            it.remove();
         }
      }
   }

   private static void playEffects(ClapHandler.ScheduledEffect fx) {
      Vec3 p = fx.armTip;
      SoundEvent sound = ModSounds.CLAP_SOUNDS[RANDOM.nextInt(ModSounds.CLAP_SOUNDS.length)];
      if (fx.syncClap) {
         fx.world.playSound(null, p.x, p.y, p.z, sound, SoundSource.PLAYERS, 1.8F, 1.2F);
         fx.world.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, 1.4F);
         fx.world.sendParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 18, 0.15, 0.15, 0.15, 0.06);
         fx.world.sendParticles(ParticleTypes.WAX_ON, p.x, p.y, p.z, 10, 0.12, 0.12, 0.12, 0.03);
         fx.world.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 7, 0.12, 0.12, 0.12, 0.04);
         fx.world.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 8, 0.1, 0.1, 0.1, 0.05);
      } else {
         switch (fx.tier) {
            case 0:
               fx.world.playSound(null, p.x, p.y, p.z, sound, SoundSource.PLAYERS, 1.1F, 1.0F);
               fx.world.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 7, 0.12, 0.12, 0.12, 0.04);
               break;
            case 1:
               fx.world.playSound(null, p.x, p.y, p.z, sound, SoundSource.PLAYERS, 1.3F, 1.15F);
               fx.world.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.8F);
               fx.world.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 6, 0.12, 0.12, 0.12, 0.05);
               fx.world.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.04);
               break;
            case 2:
               fx.world.playSound(null, p.x, p.y, p.z, sound, SoundSource.PLAYERS, 1.5F, 1.35F);
               fx.world.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 1.6F);
               fx.world.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 8, 0.15, 0.15, 0.15, 0.07);
               fx.world.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 5, 0.12, 0.12, 0.12, 0.06);
               fx.world.sendParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.04);
         }
      }

      if (fx.player != null && !fx.player.isRemoved()) {
         fx.world.playSound(fx.player, p.x, p.y, p.z, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
      }
   }

   public static void registerTodoPayload() {
      PayloadTypeRegistry.serverboundPlay().register(ClapHandler.TodoRightClickPayload.ID, ClapHandler.TodoRightClickPayload.CODEC);
   }

   public static void registerTodoReceiver() {
      ServerPlayNetworking.registerGlobalReceiver(ClapHandler.TodoRightClickPayload.ID, (payload, ctx) -> ctx.server().execute(() -> {
         if (payload.held()) {
            rightClickHeld.add(ctx.player().getUUID());
         } else {
            rightClickHeld.remove(ctx.player().getUUID());
         }
      }));
   }

   private static void performTodoSwap(ServerPlayer player) {
      ServerLevel world = player.level();
      boolean targetMode = rightClickHeld.contains(player.getUUID());
      UUID pid = player.getUUID();
      if (targetMode) {
         Entity aimed = getAimedEntity(player, 50.0);
         if (aimed == null) {
            return;
         }

         Entity stored = firstTarget.get(pid);
         if (stored != null && !stored.isRemoved()) {
            spawnBlueParticles(world, stored.position().add(0.0, 1.0, 0.0));
            spawnBlueParticles(world, aimed.position().add(0.0, 1.0, 0.0));
            playSounds(world, stored.position().add(0.0, 1.0, 0.0));
            swapEntities(stored, aimed);
            firstTarget.remove(pid);
         } else {
            firstTarget.put(pid, aimed);
            world.sendParticles(ParticleTypes.END_ROD, aimed.position().x, aimed.position().y + 1.0, aimed.position().z, 5, 0.2, 0.3, 0.2, 0.03);
            playSounds(world, aimed.position().add(0.0, 1.0, 0.0));
         }
      } else {
         firstTarget.remove(pid);
         Entity target = getAimedEntity(player, 50.0);
         if (target == null) {
            return;
         }

         Vec3 fromPos = player.position();
         Vec3 toPos = target.position();
         spawnBlueParticles(world, fromPos.add(0.0, 1.0, 0.0));
         spawnBlueParticles(world, toPos.add(0.0, 1.0, 0.0));
         playSounds(world, fromPos.add(0.0, 1.0, 0.0));
         if (target instanceof ServerPlayer tp) {
            player.teleportTo(toPos.x, toPos.y, toPos.z);
            tp.teleportTo(fromPos.x, fromPos.y, fromPos.z);
         } else {
            player.teleportTo(toPos.x, toPos.y, toPos.z);
            target.setPos(fromPos.x, fromPos.y, fromPos.z);
            target.snapTo(fromPos.x, fromPos.y, fromPos.z);
         }
      }
   }

   private static void spawnBlueParticles(ServerLevel world, Vec3 pos) {
      world.sendParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 1, 0.1, 0.2, 0.1, 0.01);
   }

   private static void playSounds(ServerLevel world, Vec3 pos) {
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.CLAP_SWAP, SoundSource.PLAYERS, 1.2F, 1.0F);
      world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.8F);
   }

   private static void swapEntities(Entity a, Entity b) {
      Vec3 posA = a.position();
      Vec3 posB = b.position();
      if (a instanceof ServerPlayer pa && b instanceof ServerPlayer pb) {
         pa.teleportTo(posB.x, posB.y, posB.z);
         pb.teleportTo(posA.x, posA.y, posA.z);
      } else if (a instanceof ServerPlayer pa) {
         pa.teleportTo(posB.x, posB.y, posB.z);
         b.setPos(posA.x, posA.y, posA.z);
         b.snapTo(posA.x, posA.y, posA.z);
      } else if (b instanceof ServerPlayer pb) {
         pb.teleportTo(posA.x, posA.y, posA.z);
         a.setPos(posB.x, posB.y, posB.z);
         a.snapTo(posB.x, posB.y, posB.z);
      } else {
         a.setPos(posB.x, posB.y, posB.z);
         a.snapTo(posB.x, posB.y, posB.z);
         b.setPos(posA.x, posA.y, posA.z);
         b.snapTo(posA.x, posA.y, posA.z);
      }
   }

   private static Entity getAimedEntity(ServerPlayer player, double maxDist) {
      Vec3 start = player.getEyePosition(1.0F);
      Vec3 look = player.getViewVector(1.0F);
      Vec3 end = start.add(look.scale(maxDist));
      AABB box = player.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(1.5);
      Entity best = null;
      double closest = maxDist;

      for (Entity e : player.level().getEntities(player, box)) {
         if (!e.isRemoved()) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(start, end);
            if (hit.isPresent()) {
               double d = start.distanceTo(hit.get());
               if (d < closest) {
                  closest = d;
                  best = e;
               }
            }
         }
      }

      return best;
   }

   public static void cleanupTodo(UUID id) {
      firstTarget.remove(id);
      rightClickHeld.remove(id);
   }

   public static void cleanup(UUID id) {
      lastPressTime.remove(id);
      cleanupTodo(id);
   }

   public record ClapRequestPayload() implements CustomPacketPayload {
      public static final Identifier ID_LOC = Identifier.fromNamespaceAndPath("cooptest", "clap_request");
      public static final Type<ClapHandler.ClapRequestPayload> ID = new Type(ID_LOC);
      public static final StreamCodec<FriendlyByteBuf, ClapHandler.ClapRequestPayload> CODEC = StreamCodec.ofMember(
         (p, buf) -> {}, buf -> new ClapHandler.ClapRequestPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static class ScheduledEffect {
      final ServerLevel world;
      final Vec3 armTip;
      final boolean syncClap;
      final int tier;
      final ServerPlayer player;
      int ticksRemaining;

      ScheduledEffect(ServerLevel world, Vec3 armTip, boolean syncClap, int tier, int delay, ServerPlayer player) {
         this.world = world;
         this.armTip = armTip;
         this.syncClap = syncClap;
         this.tier = tier;
         this.ticksRemaining = delay;
         this.player = player;
      }
   }

   public record TodoRightClickPayload(boolean held) implements CustomPacketPayload {
      public static final Type<ClapHandler.TodoRightClickPayload> ID = new Type(Identifier.fromNamespaceAndPath("testcoop", "todo_rc"));
      public static final StreamCodec<FriendlyByteBuf, ClapHandler.TodoRightClickPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeBoolean(v.held()), buf -> new ClapHandler.TodoRightClickPayload(buf.readBoolean())
      );

      public Type<ClapHandler.TodoRightClickPayload> type() {
         return ID;
      }
   }
}
