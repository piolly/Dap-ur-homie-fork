package com.cooptest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;


public class MahitoTrollHandler {
    
    private static final Map<UUID, TrollData> trolledPlayers = new HashMap<>();
    
    private static final long TROLL_START_DELAY_MS = 2000;  // 2 sec before troll starts
    private static final long TROLL_DEATH_DELAY_MS = 4000;  // 4 more sec until death (6 total)
    
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
    
    public record MahitoAnimPayload(UUID playerId) implements CustomPacketPayload {
        public static final Type<MahitoAnimPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("cooptest", "mahito_anim"));
        
        public static final StreamCodec<FriendlyByteBuf, MahitoAnimPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> buf.writeUUID(payload.playerId),
            buf -> new MahitoAnimPayload(buf.readUUID())
        );
        
        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    
    public static void register() {
        PayloadTypeRegistry.playS2C().register(MahitoAnimPayload.ID, MahitoAnimPayload.CODEC);
        
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            
            Iterator<Map.Entry<UUID, TrollData>> iter = trolledPlayers.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<UUID, TrollData> entry = iter.next();
                UUID victimId = entry.getKey();
                TrollData data = entry.getValue();
                
                ServerPlayer victim = server.getPlayerList().getPlayer(victimId);
                if (victim == null || !victim.isAlive()) {
                    iter.remove();
                    continue;
                }
                
                long elapsed = now - data.dapTime;
                
                if (!data.trollStarted && elapsed >= TROLL_START_DELAY_MS) {
                    data.trollStarted = true;
                    startTroll(victim, server.getPlayerList().getPlayer(data.trollerId));
                }
                
                if (data.trollStarted && elapsed >= TROLL_START_DELAY_MS + TROLL_DEATH_DELAY_MS) {
                    executeDeath(victim);
                    iter.remove();
                }
                
                if (data.trollStarted) {
                    victim.setDeltaMovement(0, victim.getDeltaMovement().y, 0);
                    victim.hurtMarked = true;
                }
            }
        });
    }
    
    /**
     * Called when a dap happens - check if troller has mahito effect
     */
    public static void checkForMahitoTroll(ServerPlayer p1, ServerPlayer p2) {
        // Check if either player has mahito effect
        boolean p1HasMahito = p1.hasEffect(ModEffects.MAHITO);
        boolean p2HasMahito = p2.hasEffect(ModEffects.MAHITO);
        
        if (p1HasMahito && !p2HasMahito) {
            // P1 trolls P2
            startMahitoTroll(p2, p1);
            p1.removeEffect(ModEffects.MAHITO);
        } else if (p2HasMahito && !p1HasMahito) {
            // P2 trolls P1
            startMahitoTroll(p1, p2);
            p2.removeEffect(ModEffects.MAHITO);
        }
        // If both have it, nothing happens (they cancel out)
    }
    
    private static void startMahitoTroll(ServerPlayer victim, ServerPlayer troller) {
        trolledPlayers.put(victim.getUUID(), new TrollData(System.currentTimeMillis(), troller.getUUID()));
        
        // Notify troller
        if (troller != null) {
            troller.displayClientMessage(Component.literal("§c§l☠ You cursed " + victim.getName().getString() + "! ☠"), true);
        }
    }
    
    private static void startTroll(ServerPlayer victim, ServerPlayer troller) {
        ServerLevel world = victim.level();
        
        // Freeze movement + levitation
        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 255, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 1, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 100, 128, false, false)); // Prevent jumping
        
        // Play mahito sound
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
            ModSounds.MAHITO, SoundSource.PLAYERS, 2.0f, 1.0f);
        
        // Play animation - broadcast to all clients
        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, new MahitoAnimPayload(victim.getUUID()));
        }
        
        // Ominous particles around victim
        world.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1, victim.getZ(), 
            20, 0.5, 1.0, 0.5, 0.02);
        world.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + 1, victim.getZ(), 
            15, 0.4, 0.8, 0.4, 0.01);
        
        // Message
        victim.displayClientMessage(Component.literal("§4§l☠ MAHITO'S CURSE! ☠"), true);
        
        // Sound effects
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
            SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5f, 1.5f);
    }
    
    private static void executeDeath(ServerPlayer victim) {
        ServerLevel world = victim.level();
        double x = victim.getX();
        double y = victim.getY();
        double z = victim.getZ();
        
        // Firework explosion on head!
        world.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 2, z, 1, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.FIREWORK, x, y + 2, z, 50, 0.5, 0.5, 0.5, 0.3);
        world.sendParticles((ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)), x, y + 2, z, 3, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 2, z, 30, 0.4, 0.4, 0.4, 0.15);
//        world.spawnParticles((ParticleEffect)ParticleTypes.DRAGON_BREATH, x, y + 2, z, 20, 0.3, 0.3, 0.3, 0.1);
        
        // Explosion sounds
        world.playSound(null, x, y, z,
            SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, x, y, z,
            SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 1.2f);
        world.playSound(null, x, y, z,
            SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.5f);
        
        // Kill with custom death message
        victim.hurtServer(world, world.damageSources().magic(), 9999.0F);
        
        // Announce
        for (ServerPlayer player : world.players()) {
            if (player != victim) {
                player.displayClientMessage(Component.literal("§4" + victim.getName().getString() + " §7was trolled by §cMahito's Curse!"), false);
            }
        }
    }
    
    /**
     * Check if a player is currently being trolled
     */
    public static boolean isBeingTrolled(UUID playerId) {
        return trolledPlayers.containsKey(playerId);
    }
    
    /**
     * Cleanup when player disconnects
     */
    public static void cleanup(UUID playerId) {
        trolledPlayers.remove(playerId);
    }
}
