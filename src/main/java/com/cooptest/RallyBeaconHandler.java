package com.cooptest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDeath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class RallyBeaconHandler {
   public static final long BEACON_DURATION_MS = 60000L;
   public static final boolean BEACON_DEBUG = false;
   public static final float REVIVE_COST_HEARTS = 2.0F;
   public static final float REVIVE_COST_FLOOR = 2.0F;
   public static final float REVIVE_HEALTH = 2.0F;
   public static final int REGEN_TICKS = 200;
   public static final int REGEN_AMPLIFIER = 1;
   public static final int RESISTANCE_TICKS = 100;
   public static final int RESISTANCE_AMP = 2;
   public static final int PARTICLE_INTERVAL_TICKS = 4;
   public static final double BEACON_HEIGHT = 40.0;
   public static final String[] REVIVE_LINES = new String[]{
      "§6§lIT'S NOT OVER.", "§6§lGET UP.", "§6§lTHEY NEED YOU.", "§6§lNOT TODAY.", "§6§lONE MORE ROUND.", "§6§lSTAY IN IT.", "§6§lYOUR HOMIES GOT YOU."
   };
   private static final Map<UUID, RallyBeaconHandler.Beacon> beacons = new HashMap<>();
   private static final Map<UUID, UUID> memberBeacon = new HashMap<>();

   private static boolean enabled() {
      return CoopMovesConfig.get().enableRallyBeacon;
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(RallyBeaconHandler::tick);
      registerDebugCommand();
      ServerLivingEntityEvents.ALLOW_DEATH.register((AllowDeath)(entity, source, amount) -> {
         if (!enabled()) {
            return true;
         } else {
            return entity instanceof ServerPlayer player ? !tryRevive(player) : true;
         }
      });
   }

   public static void light(ServerLevel world, Vec3 centre, Set<UUID> members) {
      if (enabled() && !members.isEmpty()) {
         UUID id = UUID.randomUUID();
         long ms = CoopMovesConfig.get().rallyBeaconMs;
         RallyBeaconHandler.Beacon b = new RallyBeaconHandler.Beacon(
            centre, UUID.nameUUIDFromBytes(world.dimension().identifier().toString().getBytes()), new HashSet<>(members), System.currentTimeMillis() + ms
         );
         beacons.put(id, b);

         for (int i = 0; i < 48; i++) {
            double a = (Math.PI * 2) * i / 48.0;
            world.sendParticles(
               ParticleTypes.TOTEM_OF_UNDYING, centre.x + Math.cos(a) * 1.5, centre.y + 0.2, centre.z + Math.sin(a) * 1.5, 2, 0.05, 0.1, 0.05, 0.05
            );
         }

         for (double y = 0.0; y < 40.0; y += 0.4) {
            world.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y + y, centre.z, 2, 0.1, 0.03, 0.1, 0.0);
            if ((int)(y * 2.0) % 3 == 0) {
               world.sendParticles(ParticleTypes.GLOW, centre.x, centre.y + y, centre.z, 1, 0.18, 0.03, 0.18, 0.0);
            }
         }

         MinecraftServer server = world.getServer();

         for (UUID uid : members) {
            memberBeacon.put(uid, id);
            if (server != null) {
               ServerPlayer p = server.getPlayerList().getPlayer(uid);
               if (p != null) {
                  p.sendSystemMessage(Component.literal("§6§lRALLY §7— one save for the group, " + formatDuration(ms)));
               }
            }
         }

         world.playSound(null, centre.x, centre.y, centre.z, ModSounds.TRUE_FRIENDSHIP, SoundSource.PLAYERS, 1.2F, 0.9F);
      }
   }

   private static boolean tryRevive(ServerPlayer player) {
      UUID bid = memberBeacon.get(player.getUUID());
      if (bid == null) {
         return false;
      }

      RallyBeaconHandler.Beacon b = beacons.get(bid);
      if (b == null) {
         memberBeacon.remove(player.getUUID());
         return false;
      }

      if (System.currentTimeMillis() > b.expiresAt) {
         return false;
      }

      if (b.spent) {
         return false;
      }

      if (!b.used.add(player.getUUID())) {
         return false;
      }

      b.spent = true;
      ServerLevel world = player.level();
      player.setHealth(2.0F);
      player.removeAllEffects();
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, true));
      player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 2, false, true));
      player.clearFire();
      player.setDeltaMovement(Vec3.ZERO);
      player.hurtMarked = true;
      player.teleportTo(world, b.pos.x, b.pos.y, b.pos.z, Set.of(), player.getYRot(), player.getXRot(), false);
      String line = REVIVE_LINES[(int)(Math.random() * REVIVE_LINES.length)];
      player.sendSystemMessage(Component.literal(line));
      player.sendOverlayMessage(Component.literal("§7The huddle brought you back."));
      world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, b.pos.x, b.pos.y + 1.0, b.pos.z, 90, 0.6, 0.9, 0.6, 0.45);
      world.sendParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y + 1.0, b.pos.z, 40, 0.3, 1.2, 0.3, 0.3);
      world.playSound(null, b.pos.x, b.pos.y, b.pos.z, ModSounds.TRUE_FRIENDSHIP, SoundSource.PLAYERS, 1.4F, 1.0F);
      MinecraftServer server = world.getServer();
      if (server != null) {
         for (UUID uid : b.members) {
            if (!uid.equals(player.getUUID())) {
               ServerPlayer other = server.getPlayerList().getPlayer(uid);
               if (other != null && other.isAlive()) {
                  other.sendSystemMessage(Component.literal("§6" + player.getName().getString() + " §7was saved by the huddle."));
                  float cost = 4.0F;
                  float left = other.getHealth() - cost;
                  if (left < 2.0F) {
                     left = Math.min(other.getHealth(), 2.0F);
                  }

                  if (left < other.getHealth()) {
                     other.setHealth(left);
                     other.sendOverlayMessage(Component.literal("§c-2❤ §7you gave your strength"));
                     ServerLevel ow = other.level();
                     ow.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, other.getX(), other.getY() + 1.0, other.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
                  }
               }
            }
         }
      }

      if (server != null) {
         for (UUID uid : b.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(uid);
            if (m != null) {
               m.sendOverlayMessage(Component.literal("§8The rally is spent."));
            }
         }
      }

      world.sendParticles(ParticleTypes.SMOKE, b.pos.x, b.pos.y + 0.5, b.pos.z, 40, 0.8, 0.5, 0.8, 0.02);
      world.playSound(null, b.pos.x, b.pos.y, b.pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.9F);
      beacons.values().remove(b);

      for (UUID uid : b.members) {
         memberBeacon.remove(uid);
      }

      return true;
   }

   private static String formatDuration(long ms) {
      long s = Math.max(0L, ms / 1000L);
      return s % 60L == 0L && s >= 60L ? s / 60L + "m" : s + "s";
   }

   private static void tick(MinecraftServer server) {
      if (!beacons.isEmpty()) {
         long now = System.currentTimeMillis();
         beacons.entrySet()
            .removeIf(
               e -> {
                  RallyBeaconHandler.Beacon b = e.getValue();
                  if (now > b.expiresAt) {
                     for (UUID uid : b.members) {
                        if (e.getKey().equals(memberBeacon.get(uid))) {
                           memberBeacon.remove(uid);
                        }

                        ServerPlayer p = server.getPlayerList().getPlayer(uid);
                        if (p != null) {
                           p.sendOverlayMessage(Component.literal("§8The rally fades."));
                        }
                     }

                     return true;
                  } else {
                     if (++b.ticks % 4 == 0) {
                        ServerLevel w = null;

                        for (UUID uid : b.members) {
                           ServerPlayer p = server.getPlayerList().getPlayer(uid);
                           if (p != null) {
                              w = p.level();
                              break;
                           }
                        }

                        if (w != null) {
                           double phase = b.ticks / 4.0 % 20.0;

                           for (double y = phase; y < 40.0; y += 20.0) {
                              for (double dy = 0.0; dy < 3.0; dy += 0.5) {
                                 w.sendParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y + y + dy, b.pos.z, 1, 0.1, 0.05, 0.1, 0.0);
                              }
                           }

                           double spin = b.ticks / 18.0;

                           for (int i = 0; i < 10; i++) {
                              double a = (Math.PI * 2) * i / 10.0 + spin;
                              w.sendParticles(
                                 ParticleTypes.TOTEM_OF_UNDYING,
                                 b.pos.x + Math.cos(a) * 1.6,
                                 b.pos.y + 0.15,
                                 b.pos.z + Math.sin(a) * 1.6,
                                 1,
                                 0.02,
                                 0.02,
                                 0.02,
                                 0.0
                              );
                           }
                        }
                     }

                     return false;
                  }
               }
            );
      }
   }

   private static void registerDebugCommand() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)Commands.literal("testbeacon")
                  .executes(
                     ctx -> {
                        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                        Set<UUID> solo = new HashSet<>();
                        solo.add(p.getUUID());
                        light(p.level(), p.position(), solo);
                        p.displayClientMessage(
                           Component.literal("§6/testbeacon §7lit on you — take lethal damage to test the save. §8/kill bypasses it by design."), false
                        );
                        return 1;
                     }
                  )
            )
         );
   }

   public static void cleanup(UUID playerId) {
      memberBeacon.remove(playerId);

      for (RallyBeaconHandler.Beacon b : beacons.values()) {
         b.members.remove(playerId);
      }
   }

   private static final class Beacon {
      final Vec3 pos;
      final UUID worldKey;
      final Set<UUID> members;
      final Set<UUID> used = new HashSet<>();
      boolean spent = false;
      final long expiresAt;
      int ticks = 0;

      Beacon(Vec3 pos, UUID worldKey, Set<UUID> members, long expiresAt) {
         this.pos = pos;
         this.worldKey = worldKey;
         this.members = members;
         this.expiresAt = expiresAt;
      }
   }
}
