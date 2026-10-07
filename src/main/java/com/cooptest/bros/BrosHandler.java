package com.cooptest.bros;

import com.cooptest.ChargedDapHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.SpearStrikeHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;

public final class BrosHandler {
   public static final long ARM_TIMEOUT_MS = 7000L;
   public static final double ENGAGE_RANGE = 2.5;
   public static final double ENGAGE_MAX_DY = 1.2;
   public static final double GAP = 0.7;
   public static final int ENTRY_TICKS = 8;
   public static double WALK_SPEED = 0.17;
   public static double SPRINT_MULT = 1.35;
   public static final double PAIR_ACCEL = 0.25;
   public static final float MAX_TURN_DEG_PER_TICK = 6.0F;
   public static final float TURN_SMOOTH = 0.35F;
   public static double MAX_Y_SPLIT = 1.15;
   public static final long INPUT_STALE_MS = 250L;
   public static final double EXTERNAL_MOVE_BREAK = 2.0;
   public static final boolean CANCEL_DAP_CHARGE_ON_ARM = true;
   public static boolean DEBUG = false;
   private static final Map<UUID, Long> armedAt = new HashMap<>();
   private static final Map<UUID, BrosHandler.Input> inputs = new HashMap<>();
   private static final Map<UUID, BrosHandler.Session> byEntity = new HashMap<>();
   private static final List<BrosHandler.Session> sessions = new ArrayList<>();
   private static final BrosHandler.Input ZERO = new BrosHandler.Input();

   private BrosHandler() {
   }

   public static void registerPayloads() {
      BrosPayloads.register();
   }

   public static void register() {
      ServerPlayNetworking.registerGlobalReceiver(BrosPayloads.Arm.ID, (p, ctx) -> onArm(ctx.player(), p.armed()));
      ServerPlayNetworking.registerGlobalReceiver(BrosPayloads.Input.ID, (p, ctx) -> onInput(ctx.player(), p));
      ServerPlayNetworking.registerGlobalReceiver(BrosPayloads.End.ID, (p, ctx) -> onEnd(ctx.server(), ctx.player()));
      ServerPlayNetworking.registerGlobalReceiver(BrosPayloads.Ability.ID, (p, ctx) -> {
         try {
            BrosAbilities.onAbility(ctx.server(), ctx.player(), p.kind(), p.down());
         } catch (Throwable t) {
            System.err.println("[Bros] ability failed: " + t);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register(BrosHandler::tick);
      BrosShield.register();
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> clearAll());
      BrosConfig.apply();
      ServerLifecycleEvents.SERVER_STARTING.register((ServerStarting)server -> BrosConfig.apply());
      BrosArmorStandTest.register();
   }

   public static boolean isEngaged(UUID id) {
      return byEntity.containsKey(id);
   }

   public static boolean isArmed(UUID id) {
      return armedAt.containsKey(id);
   }

   private static float rememberedFor(LivingEntity e) {
      return e instanceof ServerPlayer ? BrosShield.remembered(e.getUUID()) : 1.0F;
   }

   static BrosHandler.Session sessionOf(ServerPlayer p) {
      BrosHandler.Session s = byEntity.get(p.getUUID());
      return s != null && !s.ended ? s : null;
   }

   static BrosHandler.Session domeAt(Entity e) {
      for (BrosHandler.Session s : sessions) {
         if (!s.ended && BrosShield.insideDome(s, e)) {
            return s;
         }
      }

      return null;
   }

   public static void cleanup(MinecraftServer server, UUID id, boolean disconnecting) {
      armedAt.remove(id);
      inputs.remove(id);
      BrosHandler.Session s = byEntity.get(id);
      if (s != null) {
         end(server, s, disconnecting ? id : null, true);
      }

      BrosArmorStandTest.onOwnerLeft(id);
   }

   public static void clearAll() {
      BrosShield.clearMemory();
      sessions.clear();
      byEntity.clear();
      armedAt.clear();
      inputs.clear();
      BrosArmorStandTest.clearAll();
   }

   private static void onArm(ServerPlayer player, boolean armed) {
      UUID id = player.getUUID();
      if (!byEntity.containsKey(id)) {
         if (armed) {
            if (!canJoin(player)) {
               if (DEBUG) {
                  PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                  player.displayClientMessage(
                     Component.literal("§c[bros] can't arm — pose=" + pose + (player.isPassenger() ? " riding" : "") + (player.isVehicle() ? " carrying" : "")),
                     false
                  );
               }

               return;
            }

            if (armedAt.put(id, System.currentTimeMillis()) == null) {
               try {
                  ChargedDapHandler.cleanup(id);
               } catch (Throwable t) {
                  System.err.println("[Bros] clearing dap charge failed: " + t);
               }

               armSound(player, 1.6F);
            }
         } else if (armedAt.remove(id) != null) {
            armSound(player, 0.9F);
         }
      }
   }

   private static void onInput(ServerPlayer player, BrosPayloads.Input p) {
      if (byEntity.containsKey(player.getUUID())) {
         BrosHandler.Input in = inputs.computeIfAbsent(player.getUUID(), k -> new BrosHandler.Input());
         in.forward = finite(Mth.clamp(p.forward(), -1.0F, 1.0F));
         in.right = finite(Mth.clamp(p.right(), -1.0F, 1.0F));
         in.turn = finite(Mth.clamp(p.turn(), -90.0F, 90.0F));
         in.sprint = p.sprint();
         in.receivedAt = System.currentTimeMillis();
      }
   }

   private static void onEnd(MinecraftServer server, ServerPlayer player) {
      BrosHandler.Session s = byEntity.get(player.getUUID());
      if (s != null) {
         end(server, s, null, true);
      }
   }

   private static void tick(MinecraftServer server) {
      long now = System.currentTimeMillis();
      armedAt.values().removeIf(t -> now - t > 7000L);

      try {
         tryEngage(server);
      } catch (Throwable t) {
         System.err.println("[Bros] engage failed, clearing armed state");
         t.printStackTrace();
         armedAt.clear();
      }

      for (BrosHandler.Session s : new ArrayList<>(sessions)) {
         try {
            tickSession(server, s);
         } catch (Throwable t) {
            System.err.println("[Bros] session tick threw — force-ending it");
            t.printStackTrace();
            forceEnd(server, s);
         }
      }
   }

   private static void tryEngage(MinecraftServer server) {
      if (!armedAt.isEmpty()) {
         for (UUID id : new ArrayList<>(armedAt.keySet())) {
            if (armedAt.containsKey(id)) {
               ServerPlayer p = server.getPlayerList().getPlayer(id);
               if (p != null && canJoin(p)) {
                  LivingEntity best = null;
                  double bestD = 2.5;

                  for (UUID oid : armedAt.keySet()) {
                     if (!oid.equals(id)) {
                        ServerPlayer o = server.getPlayerList().getPlayer(oid);
                        if (o != null && canJoin(o)) {
                           double d = engageDist(p, o);
                           if (d <= bestD) {
                              bestD = d;
                              best = o;
                           }
                        }
                     }
                  }

                  for (ArmorStand stand : BrosArmorStandTest.dummies()) {
                     if (!byEntity.containsKey(stand.getUUID())) {
                        double d = engageDist(p, stand);
                        if (d <= bestD) {
                           bestD = d;
                           best = stand;
                        }
                     }
                  }

                  if (best != null) {
                     start(p, best);
                  } else if (DEBUG && p.tickCount % 20 == 0) {
                     p.sendOverlayMessage(Component.literal("§7[bros] armed, no armed bro within 2.5 blocks"));
                  }
               } else {
                  armedAt.remove(id);
               }
            }
         }
      }
   }

   private static boolean canJoin(LivingEntity e) {
      if (e.isRemoved() || !e.isAlive()) {
         return false;
      }

      if (e.isPassenger() || e.isVehicle()) {
         return false;
      }

      if (e.isSleeping()) {
         return false;
      }

      if (e instanceof ServerPlayer p) {
         if (p.isSpectator()) {
            return false;
         }

         PoseState pose = PoseNetworking.poseStates.getOrDefault(p.getUUID(), PoseState.NONE);
         if (pose != PoseState.NONE && pose != PoseState.GRAB_READY) {
            return false;
         }

         if (SpearStrikeHandler.isFlying(p)) {
            return false;
         }
      }

      return true;
   }

   private static double engageDist(LivingEntity a, LivingEntity b) {
      if (a.level() != b.level()) {
         return Double.MAX_VALUE;
      } else {
         return Math.abs(a.getY() - b.getY()) > 1.2 ? Double.MAX_VALUE : Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
      }
   }

   private static void start(LivingEntity a, LivingEntity b) {
      if (b instanceof ServerPlayer && a.getUUID().compareTo(b.getUUID()) > 0) {
         LivingEntity t = a;
         a = b;
         b = t;
      }

      double cx = (a.getX() + b.getX()) * 0.5;
      double cz = (a.getZ() + b.getZ()) * 0.5;
      double axX = b.getX() - a.getX();
      double axZ = b.getZ() - a.getZ();
      double len = Math.hypot(axX, axZ);
      if (len < 1.0E-4) {
         double r = Math.toRadians(a.getYRot());
         axX = -Math.cos(r);
         axZ = -Math.sin(r);
         len = 1.0;
      }

      axX /= len;
      axZ /= len;
      double fx = -axZ;
      double fz = axX;
      double[] fa = forward(a.getYRot());
      double[] fb = forward(b.getYRot());
      double avgX = fa[0] + fb[0];
      double avgZ = fa[1] + fb[1];
      if (Math.hypot(avgX, avgZ) < 0.2) {
         avgX = fa[0];
         avgZ = fa[1];
      }

      if (fx * avgX + fz * avgZ < 0.0) {
         fx = -fx;
         fz = -fz;
      }

      float heading = Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-fx, fz)));
      double[] l = left(heading);
      boolean aLeft = (a.getX() - cx) * l[0] + (a.getZ() - cz) * l[1] >= 0.0;
      BrosHandler.Session s = new BrosHandler.Session(a, b, aLeft, cx, cz, heading);
      float startFrac = Math.min(rememberedFor(a), rememberedFor(b));
      s.shieldHp = BrosShield.SHIELD_HP * startFrac;
      sessions.add(s);
      byEntity.put(a.getUUID(), s);
      byEntity.put(b.getUUID(), s);
      armedAt.remove(a.getUUID());
      armedAt.remove(b.getUUID());
      inputs.remove(a.getUUID());
      inputs.remove(b.getUUID());

      try {
         BrosShield.onStart(s);
      } catch (Throwable var29) {
      }

      try {
         BrosFx.onStart(s);
      } catch (Throwable var28) {
      }
   }

   private static void tickSession(MinecraftServer server, BrosHandler.Session s) {
      if (!s.ended) {
         if (!valid(server, s)) {
            end(server, s, null, true);
         } else {
            s.ticks++;
            Level world = s.world;
            if (s.phase == 1) {
               double k = smooth(Math.min(1.0, s.ticks / 8.0));
               double[] ta = slot(s, true);
               double[] tb = slot(s, false);
               double ax = Mth.lerp(k, s.sax, ta[0]);
               double az = Mth.lerp(k, s.saz, ta[1]);
               double bx = Mth.lerp(k, s.sbx, tb[0]);
               double bz = Mth.lerp(k, s.sbz, tb[1]);
               BrosPhysics.Result ra = BrosPhysics.resolve(world, s.a, ax, az, s.ya, s.vya, false);
               BrosPhysics.Result rb = BrosPhysics.resolve(world, s.b, bx, bz, s.yb, s.vyb, false);
               s.ya = ra.y();
               s.vya = ra.vy();
               s.yb = rb.y();
               s.vyb = rb.vy();
               float yawA = s.syawA + Mth.wrapDegrees(s.heading - s.syawA) * (float)k;
               float yawB = s.syawB + Mth.wrapDegrees(s.heading - s.syawB) * (float)k;
               place(s, true, ax, s.ya, az, yawA);
               place(s, false, bx, s.yb, bz, yawB);
               if (s.ticks >= 8) {
                  s.phase = 2;
               }
            } else if (!BrosAbilities.tickRush(server, s)) {
               walk(world, s);
            }

            if (!s.ended) {
               BrosAbilities.tick(server, s);
               BrosShield.tick(server, s);
               if (!s.ended) {
                  BrosFx.tick(s);
                  sendState(s, s.phase, null);
               }
            }
         }
      }
   }

   private static void walk(Level world, BrosHandler.Session s) {
      BrosHandler.Input ia = inputFor(s.a);
      BrosHandler.Input ib = inputFor(s.b);
      double f = (ia.forward + ib.forward) * 0.5;
      double r = (ia.right + ib.right) * 0.5;
      double mag = Math.hypot(f, r);
      if (mag > 1.0) {
         f /= mag;
         r /= mag;
      }

      double speed = WALK_SPEED * (ia.sprint && ib.sprint ? SPRINT_MULT : 1.0);
      if (s.charging || s.bunkering) {
         speed = 0.0;
      }

      double[] fw = forward(s.heading);
      double[] rt = new double[]{-Math.cos(Math.toRadians(s.heading)), -Math.sin(Math.toRadians(s.heading))};
      double tx = (fw[0] * f + rt[0] * r) * speed;
      double tz = (fw[1] * f + rt[1] * r) * speed;
      s.vx = s.vx + (tx - s.vx) * 0.25;
      s.vz = s.vz + (tz - s.vz) * 0.25;
      if (Math.abs(s.vx) < 1.0E-4) {
         s.vx = 0.0;
      }

      if (Math.abs(s.vz) < 1.0E-4) {
         s.vz = 0.0;
      }

      float target = Mth.clamp((ia.turn + ib.turn) * 0.5F, -6.0F, 6.0F);
      s.turnRate = s.turnRate + (target - s.turnRate) * 0.35F;
      if (target == 0.0F && Math.abs(s.turnRate) < 0.02F) {
         s.turnRate = 0.0F;
      }

      double vx = s.vx;
      double vz = s.vz;
      float tr = s.turnRate;
      double[][] cands = new double[][]{
         {vx, vz, tr}, {vx, 0.0, tr}, {0.0, vz, tr}, {0.0, 0.0, tr}, {vx, vz, 0.0}, {vx, 0.0, 0.0}, {0.0, vz, 0.0}, {0.0, 0.0, 0.0}
      };

      for (double[] c : cands) {
         if (tryCommit(world, s, c[0], c[1], (float)c[2])) {
            break;
         }
      }
   }

   static boolean tryCommit(Level world, BrosHandler.Session s, double dx, double dz, float dh) {
      boolean moving = dx != 0.0 || dz != 0.0 || dh != 0.0F;
      double ncx = s.cx + dx;
      double ncz = s.cz + dz;
      float nh = Mth.wrapDegrees(s.heading + dh);
      double[] pa = slotAt(ncx, ncz, nh, s.aLeft);
      double[] pb = slotAt(ncx, ncz, nh, !s.aLeft);
      BrosPhysics.Result ra = BrosPhysics.resolve(world, s.a, pa[0], pa[1], s.ya, s.vya, moving);
      if (!ra.ok()) {
         return false;
      }

      BrosPhysics.Result rb = BrosPhysics.resolve(world, s.b, pb[0], pb[1], s.yb, s.vyb, moving);
      if (!rb.ok()) {
         return false;
      }

      if (moving) {
         double split = Math.abs(ra.y() - rb.y());
         if (split > MAX_Y_SPLIT && split > Math.abs(s.ya - s.yb)) {
            return false;
         }
      }

      BrosFx.onMoved(s, Math.hypot(dx, dz));
      s.cx = ncx;
      s.cz = ncz;
      s.heading = nh;
      s.vx = dx;
      s.vz = dz;
      s.turnRate = dh;
      s.ya = ra.y();
      s.vya = ra.vy();
      s.yb = rb.y();
      s.vyb = rb.vy();
      place(s, true, pa[0], s.ya, pa[1], nh);
      place(s, false, pb[0], s.yb, pb[1], nh);
      return true;
   }

   static void shatter(MinecraftServer server, BrosHandler.Session s) {
      shatter(server, s, false);
   }

   static void shatter(MinecraftServer server, BrosHandler.Session s, boolean penalty) {
      if (!s.ended) {
         end(server, s, null, false);

         try {
            BrosShield.onShatter(s, penalty);
         } catch (Throwable t) {
            System.err.println("[Bros] shatter fx failed: " + t);
         }
      }
   }

   private static void end(MinecraftServer server, BrosHandler.Session s, UUID skip, boolean gentleFx) {
      if (!s.ended) {
         s.ended = true;
         dropMaps(s);
         float leftAt = s.shieldHp / BrosShield.SHIELD_HP;
         if (s.a instanceof ServerPlayer) {
            BrosShield.remember(s.a.getUUID(), leftAt);
         }

         if (s.b instanceof ServerPlayer) {
            BrosShield.remember(s.b.getUUID(), leftAt);
         }

         if (gentleFx) {
            try {
               BrosShield.onGentleEnd(s);
            } catch (Throwable var8) {
            }
         }

         try {
            BrosFx.onEnd(s, skip);
         } catch (Throwable var7) {
         }

         try {
            sendState(s, (byte)0, skip);
         } catch (Throwable t) {
            System.err.println("[Bros] stop broadcast failed (clients will self-expire)");
         }
      }
   }

   private static void forceEnd(MinecraftServer server, BrosHandler.Session s) {
      try {
         end(server, s, null, false);
      } catch (Throwable t) {
         s.ended = true;
         dropMaps(s);
         BrosFx.forceNone(s);
      }
   }

   private static void dropMaps(BrosHandler.Session s) {
      sessions.remove(s);
      byEntity.remove(s.a.getUUID(), s);
      byEntity.remove(s.b.getUUID(), s);
      inputs.remove(s.a.getUUID());
      inputs.remove(s.b.getUUID());
   }

   private static boolean valid(MinecraftServer server, BrosHandler.Session s) {
      if (!alive(server, s.a) || !alive(server, s.b)) {
         return false;
      } else if (canJoin(s.a) && canJoin(s.b)) {
         return s.a.level() != s.world || s.b.level() != s.world ? false : !moved(s.a, s.lax, s.lay, s.laz) && !moved(s.b, s.lbx, s.lby, s.lbz);
      } else {
         return false;
      }
   }

   private static boolean moved(LivingEntity e, double x, double y, double z) {
      double dx = e.getX() - x;
      double dy = e.getY() - y;
      double dz = e.getZ() - z;
      return dx * dx + dy * dy + dz * dz > 4.0;
   }

   private static boolean alive(MinecraftServer server, LivingEntity e) {
      if (e.isRemoved() || !e.isAlive()) {
         return false;
      } else {
         return e instanceof ServerPlayer p ? server.getPlayerList().getPlayer(p.getUUID()) == p : true;
      }
   }

   private static BrosHandler.Input inputFor(LivingEntity e) {
      if (e instanceof ArmorStand) {
         return BrosArmorStandTest.DUMMY_INPUT;
      }

      BrosHandler.Input in = inputs.get(e.getUUID());
      return in != null && System.currentTimeMillis() - in.receivedAt <= 250L ? in : ZERO;
   }

   private static void place(BrosHandler.Session s, boolean isA, double x, double y, double z, float yaw) {
      LivingEntity e = isA ? s.a : s.b;
      e.setYRot(yaw);
      e.setYHeadRot(yaw);
      e.setYBodyRot(yaw);
      if (e instanceof ServerPlayer p) {
         p.teleportTo(x, y, z);
      } else {
         e.setPos(x, y, z);
      }

      if (isA) {
         s.lax = x;
         s.lay = y;
         s.laz = z;
      } else {
         s.lbx = x;
         s.lby = y;
         s.lbz = z;
      }
   }

   private static void sendState(BrosHandler.Session s, byte phase, UUID skip) {
      BrosPayloads.State st = new BrosPayloads.State(
         s.a.getId(),
         s.b.getId(),
         phase,
         s.aLeft,
         s.cx,
         s.cz,
         s.heading,
         s.turnRate,
         s.vx,
         s.vz,
         s.ya,
         s.yb,
         8,
         Mth.clamp(s.shieldHp / BrosShield.SHIELD_HP, 0.0F, 1.0F),
         BrosAbilities.hudFlags(s),
         Math.max(0, s.rushCdUntil - s.ticks),
         Math.max(0, s.pulseCdUntil - s.ticks),
         BrosShield.SHIELD_HP,
         (float)BrosShield.RADIUS
      );
      Set<ServerPlayer> to = new HashSet<>();
      addIfConnected(to, s.a);
      addIfConnected(to, s.b);
      addTrackers(to, s.a);
      addTrackers(to, s.b);

      for (ServerPlayer p : to) {
         if (skip == null || !p.getUUID().equals(skip)) {
            try {
               ServerPlayNetworking.send(p, st);
            } catch (Throwable var8) {
            }
         }
      }
   }

   private static void addIfConnected(Set<ServerPlayer> to, LivingEntity e) {
      if (e instanceof ServerPlayer p && !p.isRemoved()) {
         to.add(p);
      }
   }

   private static void addTrackers(Set<ServerPlayer> to, LivingEntity e) {
      if (!e.isRemoved()) {
         try {
            to.addAll(PlayerLookup.tracking(e));
         } catch (Throwable var3) {
         }
      }
   }

   private static void armSound(ServerPlayer p, float pitch) {
      p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, pitch);
   }

   private static float finite(float v) {
      return Float.isFinite(v) ? v : 0.0F;
   }

   private static double[] slot(BrosHandler.Session s, boolean isA) {
      return slotAt(s.cx, s.cz, s.heading, isA == s.aLeft);
   }

   public static double[] slotAt(double cx, double cz, float heading, boolean left) {
      double[] l = left(heading);
      double side = left ? 0.35 : -0.35;
      return new double[]{cx + l[0] * side, cz + l[1] * side};
   }

   public static double[] forward(float yawDeg) {
      double r = Math.toRadians(yawDeg);
      return new double[]{-Math.sin(r), Math.cos(r)};
   }

   public static double[] left(float yawDeg) {
      double r = Math.toRadians(yawDeg);
      return new double[]{Math.cos(r), Math.sin(r)};
   }

   public static double smooth(double t) {
      return t * t * (3.0 - 2.0 * t);
   }

   static final class Input {
      float forward;
      float right;
      float turn;
      boolean sprint;
      long receivedAt;
   }

   static final class Session {
      final LivingEntity a;
      final LivingEntity b;
      final Level world;
      final boolean aLeft;
      double cx;
      double cz;
      float heading;
      float turnRate;
      double vx;
      double vz;
      double ya;
      double yb;
      double vya;
      double vyb;
      byte phase = 1;
      int ticks;
      boolean ended;
      float shieldHp = BrosShield.SHIELD_HP;
      int lastHitTick = -100000;
      int rushVoteA = -1;
      int rushVoteB = -1;
      int pulseVoteA = -1;
      int pulseVoteB = -1;
      boolean holdA;
      boolean holdB;
      boolean charging;
      boolean filling;
      boolean lowHp;
      boolean bunkerA;
      boolean bunkerB;
      boolean bunkering;
      int rushCdUntil;
      int pulseCdUntil;
      int rushTicksLeft;
      double rushDx;
      double rushDz;
      final Set<Integer> rushHit = new HashSet<>();
      double stepDist;
      double lax;
      double lay;
      double laz;
      double lbx;
      double lby;
      double lbz;
      final double sax;
      final double saz;
      final double sbx;
      final double sbz;
      final float syawA;
      final float syawB;

      Session(LivingEntity a, LivingEntity b, boolean aLeft, double cx, double cz, float heading) {
         this.a = a;
         this.b = b;
         this.aLeft = aLeft;
         this.world = a.level();
         this.cx = cx;
         this.cz = cz;
         this.heading = heading;
         this.ya = a.getY();
         this.yb = b.getY();
         this.sax = a.getX();
         this.saz = a.getZ();
         this.sbx = b.getX();
         this.sbz = b.getZ();
         this.syawA = a.getYRot();
         this.syawB = b.getYRot();
         this.lax = a.getX();
         this.lay = a.getY();
         this.laz = a.getZ();
         this.lbx = b.getX();
         this.lby = b.getY();
         this.lbz = b.getZ();
      }
   }
}
