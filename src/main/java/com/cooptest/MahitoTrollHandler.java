package com.cooptest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class MahitoTrollHandler {
   private static final Map<UUID, MahitoTrollHandler.TrollData> trolledPlayers = new HashMap<>();
   private static final long TROLL_START_DELAY_MS = 2000L;
   private static final long TROLL_DEATH_DELAY_MS = 4000L;

   public static void register() {
      PayloadTypeRegistry.clientboundPlay().register(MahitoTrollHandler.MahitoAnimPayload.ID, MahitoTrollHandler.MahitoAnimPayload.CODEC);
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         long now = System.currentTimeMillis();
         Iterator<Entry<UUID, MahitoTrollHandler.TrollData>> iter = trolledPlayers.entrySet().iterator();

         while (iter.hasNext()) {
            Entry<UUID, MahitoTrollHandler.TrollData> entry = iter.next();
            UUID victimId = entry.getKey();
            MahitoTrollHandler.TrollData data = entry.getValue();
            ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
            if (victim != null && victim.isAlive()) {
               long elapsed = now - data.dapTime;
               if (!data.trollStarted && elapsed >= 2000L) {
                  data.trollStarted = true;
                  startTroll(victim, server.getPlayerList().getPlayer(data.trollerId));
               }

               if (data.trollStarted && elapsed >= 6000L) {
                  executeDeath(victim);
                  iter.remove();
               }

               if (data.trollStarted) {
                  victim.setDeltaMovement(0.0, victim.getDeltaMovement().y, 0.0);
                  victim.hurtMarked = true;
               }
            } else {
               iter.remove();
            }
         }
      });
   }

   public static void checkForMahitoTroll(ServerPlayer p1, ServerPlayer p2) {
      boolean p1HasMahito = p1.hasEffect(ModEffects.MAHITO);
      boolean p2HasMahito = p2.hasEffect(ModEffects.MAHITO);
      if (p1HasMahito && !p2HasMahito) {
         startMahitoTroll(p2, p1);
         p1.removeEffect(ModEffects.MAHITO);
      } else if (p2HasMahito && !p1HasMahito) {
         startMahitoTroll(p1, p2);
         p2.removeEffect(ModEffects.MAHITO);
      }
   }

   private static void startMahitoTroll(ServerPlayer victim, ServerPlayer troller) {
      trolledPlayers.put(victim.getUUID(), new MahitoTrollHandler.TrollData(System.currentTimeMillis(), troller.getUUID()));
      if (troller != null) {
         troller.sendOverlayMessage(Component.literal("§c§l☠ You cursed " + victim.getName().getString() + "! ☠"));
      }
   }

   private static void startTroll(ServerPlayer victim, ServerPlayer troller) {
      ServerLevel world = victim.level();
      victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 255, false, false));
      victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 1, false, false));
      victim.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 100, 128, false, false));
      world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.MAHITO, SoundSource.PLAYERS, 2.0F, 1.0F);

      for (ServerPlayer player : world.players()) {
         ServerPlayNetworking.send(player, new MahitoTrollHandler.MahitoAnimPayload(victim.getUUID()));
      }

      world.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1.0, victim.getZ(), 20, 0.5, 1.0, 0.5, 0.02);
      world.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + 1.0, victim.getZ(), 15, 0.4, 0.8, 0.4, 0.01);
      victim.sendOverlayMessage(Component.literal("§4§l☠ MAHITO'S CURSE! ☠"));
      world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 1.5F);
   }

   private static void executeDeath(ServerPlayer victim) {
      ServerLevel world = victim.level();
      double x = victim.getX();
      double y = victim.getY();
      double z = victim.getZ();
      world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 2.0, z, 1, 0.0, 0.0, 0.0, 0.0);
      world.sendParticles(ParticleTypes.FIREWORK, x, y + 2.0, z, 50, 0.5, 0.5, 0.5, 0.3);
      world.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), x, y + 2.0, z, 3, 0.0, 0.0, 0.0, 0.0);
      world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 2.0, z, 30, 0.4, 0.4, 0.4, 0.15);
      world.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), x, y + 2.0, z, 20, 0.3, 0.3, 0.3, 0.1);
      world.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.0F, 1.0F);
      world.playSound(null, x, y, z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 1.2F);
      world.playSound(null, x, y, z, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.5F);
      victim.hurtServer(world, world.damageSources().magic(), Float.MAX_VALUE);

      for (ServerPlayer player : world.players()) {
         if (player != victim) {
            player.sendSystemMessage(Component.literal("§4" + victim.getName().getString() + " §7was trolled by §cMahito's Curse!"));
         }
      }
   }

   public static boolean isBeingTrolled(UUID playerId) {
      return trolledPlayers.containsKey(playerId);
   }

   public static void cleanup(UUID playerId) {
      trolledPlayers.remove(playerId);
   }

   public record MahitoAnimPayload(UUID playerId) implements CustomPacketPayload {
      public static final Type<MahitoTrollHandler.MahitoAnimPayload> ID = new Type(Identifier.fromNamespaceAndPath("cooptest", "mahito_anim"));
      public static final StreamCodec<FriendlyByteBuf, MahitoTrollHandler.MahitoAnimPayload> CODEC = StreamCodec.ofMember(
         (payload, buf) -> buf.writeUUID(payload.playerId), buf -> new MahitoTrollHandler.MahitoAnimPayload(buf.readUUID())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static class TrollData {
      long dapTime;
      boolean trollStarted;
      UUID trollerId;

      TrollData(long time, UUID troller) {
         this.dapTime = time;
         this.trollStarted = false;
         this.trollerId = troller;
      }
   }
}
