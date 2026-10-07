package com.cooptest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

public class NormalFacingDapHandler {
   private static final double FACE_DIST = 1.3;
   private static final long IMPACT_AT_MS = 420L;
   private static final long HOLD_WINDOW_START_MS = 630L;
   private static final long HOLD_SWAP_AT_MS = 2130L;
   private static final long MOVE_RELEASE_AT_MS = 2540L;
   private static final long HIT_FACE_LEN_MS = 3125L;
   private static final long HOLD_ENTRY_MS = 0L;
   private static final long HOLD_END_MS = 850L;
   private static final long HOLD_GRACE_MS = 300L;
   private static final long MIN_HOLD_MS = 250L;
   private static final float IMPACT_SHAKE_AMOUNT = 3.0F;
   private static final long IMPACT_SHAKE_MS = 80L;
   private static final float GRIP_SHAKE_AMOUNT = 2.0F;
   private static final long GRIP_SHAKE_MS = 150L;
   private static final long IDLE_SHAKE_INTERVAL_MS = 120L;
   private static final float IDLE_SHAKE_AMOUNT = 0.55F;
   private static final long IDLE_SHAKE_MS = 200L;
   private static final long SHAKE_SFX_INTERVAL_MS = 170L;
   private static final long WINDOW_SHAKE_INTERVAL_MS = 180L;
   private static final float WINDOW_SHAKE_AMOUNT = 0.7F;
   private static final long WINDOW_SHAKE_MS = 220L;
   private static final float END_SHAKE_AMOUNT = 0.9F;
   private static final long END_SHAKE_MS = 120L;
   private static final float FIZZLE_SHAKE_AMOUNT = 0.4F;
   private static final long FIZZLE_SHAKE_MS = 100L;
   private static final int HOLD_PARTICLE_INTERVAL = 20;
   private static final boolean SHOW_HOLD_TIMER = true;
   private static final float DAMAGE_CANCEL_THRESHOLD = 4.0F;
   private static final long CLICK_WINDOW = 2000L;
   private static final int ANIM_NONE = 0;
   private static final int ANIM_DAP_HIT_FACE = 81;
   private static final int ANIM_DAP_HOLD = 109;
   private static final int ANIM_HOLD_LOOP = 84;
   private static final int ANIM_DAP_HOLD_END = 111;
   public static final Identifier LOOP_HOLD_ID = Identifier.fromNamespaceAndPath("cooptest", "dap_loop_hold");
   public static final Identifier SESSION_STATE_ID = Identifier.fromNamespaceAndPath("cooptest", "face_dap_session");
   public static final Identifier SHAKE_ID = Identifier.fromNamespaceAndPath("cooptest", "face_dap_shake");
   private static final Map<UUID, NormalFacingDapHandler.Session> sessions = new HashMap<>();
   private static final Map<UUID, UUID> clickMap = new HashMap<>();
   private static final Map<UUID, Long> clickTime = new HashMap<>();

   public static void registerPayloads() {
      PayloadTypeRegistry.serverboundPlay().register(NormalFacingDapHandler.DapLoopHoldPayload.ID, NormalFacingDapHandler.DapLoopHoldPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(NormalFacingDapHandler.FaceDapSessionPayload.ID, NormalFacingDapHandler.FaceDapSessionPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(NormalFacingDapHandler.FaceDapShakePayload.ID, NormalFacingDapHandler.FaceDapShakePayload.CODEC);
   }

   public static boolean isActive(UUID id) {
      return sessions.containsKey(id);
   }

   public static boolean isHolding(UUID id) {
      NormalFacingDapHandler.Session s = sessions.get(id);
      return s != null && (s.stage == NormalFacingDapHandler.Stage.HOLD_ENTRY || s.stage == NormalFacingDapHandler.Stage.HOLD_IDLE);
   }

   public static void onLoopHold(ServerPlayer player) {
      NormalFacingDapHandler.Session s = sessions.get(player.getUUID());
      if (s != null) {
         long now = System.currentTimeMillis();
         if (player.getUUID().equals(s.a)) {
            s.holdA = now;
         } else {
            s.holdB = now;
         }
      }
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(
         NormalFacingDapHandler.DapLoopHoldPayload.ID, (payload, context) -> context.server().execute(() -> onLoopHold(context.player()))
      );
      ServerTickEvents.END_SERVER_TICK.register(NormalFacingDapHandler::tick);
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
         if (entity instanceof ServerPlayer victim) {
            if (amount < 4.0F) {
               return true;
            }

            NormalFacingDapHandler.Session s = sessions.get(victim.getUUID());
            if (s == null) {
               return true;
            }

            MinecraftServer server = victim.level().getServer();
            if (server != null) {
               hardCancel(server, s);
            }

            return true;
         } else {
            return true;
         }
      });
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) {
               return InteractionResult.PASS;
            }

            NormalFacingDapHandler.Session s = sessions.get(sp.getUUID());
            if (s != null && s.stage == NormalFacingDapHandler.Stage.HOLD_IDLE) {
               MinecraftServer server = sp.level().getServer();
               if (server != null) {
                  beginEnd(server, s);
               }

               return InteractionResult.SUCCESS;
            } else {
               return InteractionResult.PASS;
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }

   private static void tick(MinecraftServer server) {
      if (!sessions.isEmpty()) {
         long now = System.currentTimeMillis();

         for (NormalFacingDapHandler.Session s : new HashSet<>(sessions.values())) {
            ServerPlayer a = server.getPlayerList().getPlayer(s.a);
            ServerPlayer b = server.getPlayerList().getPlayer(s.b);
            if (a != null && b != null && a.isAlive() && b.isAlive()) {
               s.tickCounter++;
               if (s.stage == NormalFacingDapHandler.Stage.HIT
                  || s.stage == NormalFacingDapHandler.Stage.HOLD_ENTRY
                  || s.stage == NormalFacingDapHandler.Stage.HOLD_IDLE) {
                  pin(a, s.yawA);
                  pin(b, s.yawB);
               }

               switch (s.stage) {
                  case HIT:
                     tickHit(s, a, b, now);
                     break;
                  case HOLD_ENTRY:
                     if (s.inStage(now) >= 0L) {
                        broadcast(a, 84);
                        broadcast(b, 84);
                        s.stage(NormalFacingDapHandler.Stage.HOLD_IDLE, now);
                        PactHandler.onHandshakeStart(a, b);
                     }
                     break;
                  case HOLD_IDLE:
                     tickHoldIdle(server, s, a, b, now);
                     break;
                  case ENDING:
                     if (s.inStage(now) >= 850L) {
                        cleanup(server, s, true);
                     }
                     break;
                  case CANCELLING:
                     if (s.inStage(now) >= 200L) {
                        cleanup(server, s, true);
                     }
               }
            } else {
               hardCancel(server, s);
            }
         }
      }
   }

   private static void tickHit(NormalFacingDapHandler.Session s, ServerPlayer a, ServerPlayer b, long now) {
      long e = s.elapsed(now);
      if (!s.moveReleased && e >= 2540L) {
         s.moveReleased = true;
         ServerPlayNetworking.send(a, new NormalFacingDapHandler.FaceDapSessionPayload(false));
         ServerPlayNetworking.send(b, new NormalFacingDapHandler.FaceDapSessionPayload(false));
      }

      if (!s.impactDone && e >= 420L) {
         s.impactDone = true;
         PactHandler.onHandshakeCounted(a, b);
         impact(a, b);
         shake(a, b, 3.0F, 80L);
      }

      if (e >= 630L && e < 2130L && now - s.lastIdleShake >= 180L) {
         s.lastIdleShake = now;
         shake(a, b, 0.7F, 220L);
      }

      if (e >= 2130L) {
         boolean bothHeld = s.holding(s.a, now) && s.holding(s.b, now) && s.holdA - s.startMs >= 630L && s.holdB - s.startMs >= 630L;
         if (bothHeld) {
            broadcast(a, 84);
            broadcast(b, 84);
            s.stage(NormalFacingDapHandler.Stage.HOLD_IDLE, now);
            PactHandler.onHandshakeStart(a, b);
            playAt(a, b, SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 0.8F);
            shake(a, b, 2.0F, 150L);
         } else {
            fizzle(a, b);
            shake(a, b, 0.4F, 100L);
            s.stage(NormalFacingDapHandler.Stage.CANCELLING, now);
         }
      }
   }

   private static void tickHoldIdle(MinecraftServer server, NormalFacingDapHandler.Session s, ServerPlayer a, ServerPlayer b, long now) {
      boolean stillHolding = s.holding(s.a, now) && s.holding(s.b, now);
      if (!stillHolding && s.inStage(now) >= 250L) {
         beginEnd(server, s);
      } else {
         PactHandler.onHold(a);
         PactHandler.onHold(b);
         if (now - s.lastIdleShake >= 120L) {
            s.lastIdleShake = now;
            shake(a, b, 0.55F, 200L);
         }

         if (now - s.lastShakeSfx >= 170L) {
            s.lastShakeSfx = now;
            playAt(a, b, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.35F, 1.7F);
         }

         if (s.tickCounter % 20 == 0) {
            ServerLevel w = a.level();
            Vec3 m = mid(a, b);
            w.sendParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 1, 0.03, 0.03, 0.03, 0.005);
         }

         if (!PactHandler.isSealing(s.a)) {
            long held = s.inStage(now) / 1000L;
            Component t = Component.literal("§6\ud83e\udd1d §f" + held + "s");
            a.sendOverlayMessage(t);
            b.sendOverlayMessage(t);
         }
      }
   }

   public static void start(ServerPlayer p1, ServerPlayer p2) {
      UUID id1 = p1.getUUID();
      UUID id2 = p2.getUUID();
      if (!sessions.containsKey(id1) && !sessions.containsKey(id2)) {
         MinecraftServer server = p1.level().getServer();
         if (server != null) {
            if (id1.compareTo(id2) > 0) {
               ServerPlayer tmp = p1;
               p1 = p2;
               p2 = tmp;
               id1 = p1.getUUID();
               id2 = p2.getUUID();
            }

            Vec3 diff = p2.position().subtract(p1.position());
            Vec3 flat = new Vec3(diff.x, 0.0, diff.z).normalize();
            Vec3 mid = p1.position().add(p2.position()).scale(0.5);
            Vec3 pos1 = mid.subtract(flat.scale(0.65));
            Vec3 pos2 = mid.add(flat.scale(0.65));
            float yaw1 = (float)Math.toDegrees(Math.atan2(-flat.x, flat.z));
            float yaw2 = yaw1 + 180.0F;
            p1.teleportTo(p1.level(), pos1.x, p1.getY(), pos1.z, Set.of(), yaw1, p1.getXRot(), false);
            p2.teleportTo(p2.level(), pos2.x, p2.getY(), pos2.z, Set.of(), yaw2, p2.getXRot(), false);
            applyYaw(p1, yaw1);
            applyYaw(p2, yaw2);
            p1.swing(InteractionHand.MAIN_HAND, true);
            p2.swing(InteractionHand.MAIN_HAND, true);
            long now = System.currentTimeMillis();
            NormalFacingDapHandler.Session s = new NormalFacingDapHandler.Session(id1, id2, yaw1, yaw2, now);
            sessions.put(id1, s);
            sessions.put(id2, s);
            ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
            ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
            ServerPlayNetworking.send(p1, new NormalFacingDapHandler.FaceDapSessionPayload(true));
            ServerPlayNetworking.send(p2, new NormalFacingDapHandler.FaceDapSessionPayload(true));
            broadcast(p1, 81);
            broadcast(p2, 81);
         }
      }
   }

   private static void shake(ServerPlayer a, ServerPlayer b, float amount, long ms) {
      NormalFacingDapHandler.FaceDapShakePayload p = new NormalFacingDapHandler.FaceDapShakePayload(amount, ms);
      if (a != null) {
         ServerPlayNetworking.send(a, p);
      }

      if (b != null) {
         ServerPlayNetworking.send(b, p);
      }
   }

   private static void impact(ServerPlayer a, ServerPlayer b) {
      ServerLevel w = a.level();
      Vec3 m = mid(a, b);
      w.playSound(null, m.x, m.y, m.z, ModSounds.DAP_HIT, SoundSource.PLAYERS, 1.5F, 1.0F);
      w.playSound(null, m.x, m.y, m.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
      w.sendParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 12, 0.2, 0.2, 0.2, 0.1);
      w.sendParticles(ParticleTypes.ENCHANTED_HIT, m.x, m.y, m.z, 6, 0.15, 0.15, 0.15, 0.07);
      w.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1), m.x, m.y, m.z, 2, 0.0, 0.0, 0.0, 0.0);
   }

   private static void fizzle(ServerPlayer a, ServerPlayer b) {
      ServerLevel w = a.level();
      Vec3 m = mid(a, b);
      w.playSound(null, m.x, m.y, m.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
      w.playSound(null, m.x, m.y, m.z, ModSounds.DAP_MISS, SoundSource.PLAYERS, 0.7F, 1.0F);
      w.sendParticles(ParticleTypes.SMOKE, m.x, m.y, m.z, 14, 0.2, 0.2, 0.2, 0.02);
   }

   private static void playAt(ServerPlayer a, ServerPlayer b, SoundEvent sound, float vol, float pitch) {
      ServerLevel w = a.level();
      Vec3 m = mid(a, b);
      w.playSound(null, m.x, m.y, m.z, sound, SoundSource.PLAYERS, vol, pitch);
   }

   private static Vec3 mid(ServerPlayer a, ServerPlayer b) {
      return a.position().add(b.position()).scale(0.5).add(0.0, 1.3, 0.0);
   }

   private static void beginEnd(MinecraftServer server, NormalFacingDapHandler.Session s) {
      if (s.stage != NormalFacingDapHandler.Stage.ENDING && s.stage != NormalFacingDapHandler.Stage.CANCELLING) {
         long now = System.currentTimeMillis();
         ServerPlayer a = server.getPlayerList().getPlayer(s.a);
         ServerPlayer b = server.getPlayerList().getPlayer(s.b);
         s.stage(NormalFacingDapHandler.Stage.ENDING, now);

         for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            if (p != null) {
               ServerPlayNetworking.send(p, new ChargedDapHandler.PerfectDapFreezePayload(false));
               ServerPlayNetworking.send(p, new NormalFacingDapHandler.FaceDapSessionPayload(false));
               broadcast(p, 111);
            }
         }

         shake(a, b, 0.9F, 120L);
      }
   }

   private static void hardCancel(MinecraftServer server, NormalFacingDapHandler.Session s) {
      cleanup(server, s, true);
   }

   private static void cleanup(MinecraftServer server, NormalFacingDapHandler.Session s, boolean clearAnim) {
      sessions.remove(s.a);
      sessions.remove(s.b);
      PactHandler.cleanup(s.a);
      PactHandler.cleanup(s.b);
      long cd = System.currentTimeMillis() + ChargedDapHandler.cooldownMs();
      ChargedDapHandler.cooldowns.put(s.a, cd);
      ChargedDapHandler.cooldowns.put(s.b, cd);

      for (UUID id : new UUID[]{s.a, s.b}) {
         ServerPlayer p = server.getPlayerList().getPlayer(id);
         if (p != null) {
            ServerPlayNetworking.send(p, new ChargedDapHandler.PerfectDapFreezePayload(false));
            ServerPlayNetworking.send(p, new NormalFacingDapHandler.FaceDapSessionPayload(false));
            if (clearAnim) {
               broadcast(p, 0);
            }
         }
      }
   }

   public static void cleanup(UUID id) {
      clickMap.remove(id);
      clickTime.remove(id);
      NormalFacingDapHandler.Session s = sessions.get(id);
      if (s != null) {
         sessions.remove(s.a);
         sessions.remove(s.b);
         PactHandler.cleanup(s.a);
         PactHandler.cleanup(s.b);
      }
   }

   public static void recordRightClick(ServerPlayer sp, ServerPlayer target) {
      clickMap.put(sp.getUUID(), target.getUUID());
      clickTime.put(sp.getUUID(), System.currentTimeMillis());
      sp.sendOverlayMessage(Component.literal("§e✦ Waiting for homie..."));
   }

   public static boolean isConfirmed(UUID id1, UUID id2) {
      long now = System.currentTimeMillis();
      UUID c1 = clickMap.get(id1);
      Long t1 = clickTime.get(id1);
      UUID c2 = clickMap.get(id2);
      Long t2 = clickTime.get(id2);
      return id2.equals(c1) && t1 != null && now - t1 < 2000L && id1.equals(c2) && t2 != null && now - t2 < 2000L;
   }

   public static boolean isConfirmedOneSide(UUID who, UUID target) {
      UUID c = clickMap.get(who);
      Long t = clickTime.get(who);
      return target.equals(c) && t != null && System.currentTimeMillis() - t < 2000L;
   }

   public static void clearConfirm(UUID id1, UUID id2) {
      clickMap.remove(id1);
      clickTime.remove(id1);
      if (id2 != null) {
         clickMap.remove(id2);
         clickTime.remove(id2);
      }
   }

   private static void pin(ServerPlayer p, float yaw) {
      p.setDeltaMovement(0.0, 0.0, 0.0);
      p.syncVelocity = true;
      applyYaw(p, yaw);
   }

   private static void applyYaw(ServerPlayer p, float yaw) {
      p.setYRot(yaw);
      p.setYBodyRot(yaw);
      p.setYHeadRot(yaw);
   }

   private static void broadcast(ServerPlayer p, int ordinal) {
      PoseNetworking.broadcastAnimState(p, ordinal);
   }

   public record DapLoopHoldPayload() implements CustomPacketPayload {
      public static final Type<NormalFacingDapHandler.DapLoopHoldPayload> ID = new Type(NormalFacingDapHandler.LOOP_HOLD_ID);
      public static final StreamCodec<FriendlyByteBuf, NormalFacingDapHandler.DapLoopHoldPayload> CODEC = StreamCodec.unit(
         new NormalFacingDapHandler.DapLoopHoldPayload()
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FaceDapSessionPayload(boolean active) implements CustomPacketPayload {
      public static final Type<NormalFacingDapHandler.FaceDapSessionPayload> ID = new Type(NormalFacingDapHandler.SESSION_STATE_ID);
      public static final StreamCodec<FriendlyByteBuf, NormalFacingDapHandler.FaceDapSessionPayload> CODEC = StreamCodec.ofMember(
         (v, buf) -> buf.writeBoolean(v.active()), buf -> new NormalFacingDapHandler.FaceDapSessionPayload(buf.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   public record FaceDapShakePayload(float amount, long durationMs) implements CustomPacketPayload {
      public static final Type<NormalFacingDapHandler.FaceDapShakePayload> ID = new Type(NormalFacingDapHandler.SHAKE_ID);
      public static final StreamCodec<FriendlyByteBuf, NormalFacingDapHandler.FaceDapShakePayload> CODEC = StreamCodec.ofMember((v, buf) -> {
         buf.writeFloat(v.amount());
         buf.writeLong(v.durationMs());
      }, buf -> new NormalFacingDapHandler.FaceDapShakePayload(buf.readFloat(), buf.readLong()));

      public Type<? extends CustomPacketPayload> type() {
         return ID;
      }
   }

   private static final class Session {
      final UUID a;
      final UUID b;
      final float yawA;
      final float yawB;
      NormalFacingDapHandler.Stage stage = NormalFacingDapHandler.Stage.HIT;
      final long startMs;
      long stageStartMs;
      boolean impactDone;
      long holdA;
      long holdB;
      long lastIdleShake;
      long lastShakeSfx;
      boolean moveReleased;
      int tickCounter;

      Session(UUID a, UUID b, float yawA, float yawB, long now) {
         this.a = a;
         this.b = b;
         this.yawA = yawA;
         this.yawB = yawB;
         this.startMs = now;
         this.stageStartMs = now;
      }

      long elapsed(long now) {
         return now - this.startMs;
      }

      long inStage(long now) {
         return now - this.stageStartMs;
      }

      void stage(NormalFacingDapHandler.Stage s, long now) {
         this.stage = s;
         this.stageStartMs = now;
      }

      boolean holding(UUID id, long now) {
         long h = id.equals(this.a) ? this.holdA : this.holdB;
         return h != 0L && now - h <= 300L;
      }
   }

   private enum Stage {
      HIT,
      HOLD_ENTRY,
      HOLD_IDLE,
      ENDING,
      CANCELLING;
   }
}
