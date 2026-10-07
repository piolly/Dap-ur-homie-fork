package com.cooptest.bros.client;

import com.cooptest.bros.BrosHandler;
import com.cooptest.bros.BrosPayloads;
import com.cooptest.bros.BrosPhysics;
import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class BrosClientHandler {
   public static final long ARM_CHARGE_MS = 250L;
   public static final long ARM_H_GRACE_MS = 400L;
   public static boolean DEBUG = false;
   public static final long G_HOLD_MS = 300L;
   public static final long H_HOLD_MS = 300L;
   public static final int STALE_TICKS = 10;
   public static final double CORR_BLEND = 0.35;
   public static final double SNAP_DIST = 1.0;
   public static final float SNAP_DEG = 30.0F;
   public static final double LOCAL_Y_SNAP = 0.75;
   public static final double TURN_VOTE_SCALE = 1.0;
   public static final boolean DOME_PARTICLES = true;
   public static final int DOME_POINTS = 8;
   public static final int DOME_FULL_COLOR = 5629695;
   public static final int DOME_LOW_COLOR = 16728128;
   public static final float DOME_SCALE = 0.9F;
   public static final float LEG_TIME_SCALE = 0.75F;
   public static final float LEG_AMPLITUDE = 1.0F;
   public static final float LEG_SMOOTHING = 0.4F;
   private static final double DOME_HEIGHT = 3.0;
   private static final Map<Integer, BrosClientHandler.Track> tracks = new HashMap<>();
   private static BrosClientHandler.Track localTrack = null;
   private static boolean armed;
   private static boolean prevH;
   private static boolean prevG;
   private static long gPressAt;
   private static long hPressAt;
   private static boolean gHoldSent;
   private static boolean hHoldSent;
   private static long gDownSince;
   private static long hDownSince;
   private static boolean hLock;
   private static boolean releasedSinceEngage;
   private static boolean prevSneak;
   private static double pendingTurn;
   private static final Set<Integer> legDriven = new HashSet<>();
   private static final Set<Integer> legDrivenNext = new HashSet<>();
   private static final int C_DIM = -7697782;
   private static final int C_GREEN = -9699462;
   private static final int C_BG = -2013265920;

   private BrosClientHandler() {
   }

   public static boolean isLegDriven(int entityId) {
      return legDriven.contains(entityId);
   }

   public static boolean isEngaged() {
      return localTrack != null;
   }

   public static boolean blocksDap() {
      return armed || localTrack != null;
   }

   public static boolean blocksGrab() {
      return armed || localTrack != null;
   }

   public static boolean blocksHighFive() {
      return armed || hLock || localTrack != null || isDown(chargeKey());
   }

   public static void addTurnVote(double deg) {
      if (Double.isFinite(deg)) {
         pendingTurn += deg;
      }
   }

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(BrosPayloads.State.ID, (p, ctx) -> {
         try {
            onState(p);
         } catch (Throwable t) {
            fail("state packet", t);
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         try {
            tick(mc);
         } catch (Throwable t) {
            fail("tick", t);
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> resetAll());
      HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickCounter) -> {
         try {
            renderHud(ctx);
         } catch (Throwable var3) {
         }
      });
   }

   private static void fail(String where, Throwable t) {
      System.err.println("[Bros] client " + where + " threw — resetting bros state");
      t.printStackTrace();
      resetAll();
   }

   private static void resetAll() {
      tracks.clear();
      localTrack = null;
      legDriven.clear();
      legDrivenNext.clear();
      armed = false;
      prevH = false;
      gDownSince = 0L;
      hDownSince = 0L;
      hLock = false;
      releasedSinceEngage = false;
      pendingTurn = 0.0;
   }

   private static void onState(BrosPayloads.State p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         BrosClientHandler.Track t = tracks.get(p.idA());
         if (p.phase() == 0) {
            if (t != null && t.idB == p.idB()) {
               removeTrack(t);
            }
         } else {
            if (t != null && t.idB == p.idB() && t.world == mc.level) {
               double ex = p.cx() - t.cx;
               double ez = p.cz() - t.cz;
               float eh = Mth.wrapDegrees(p.heading() - t.h);
               if (!(Math.hypot(ex, ez) > 1.0) && !(Math.abs(eh) > 30.0F)) {
                  t.corrX = ex;
                  t.corrZ = ez;
                  t.corrH = eh;
               } else {
                  t.cx = p.cx();
                  t.cz = p.cz();
                  t.h = p.heading();
                  t.corrX = 0.0;
                  t.corrZ = 0.0;
                  t.corrH = 0.0F;
               }

               if (t.local) {
                  double sy = t.localIsA ? p.ya() : p.yb();
                  if (Math.abs(sy - t.localY) > 0.75) {
                     t.localY = sy;
                     t.localVy = 0.0;
                  }
               }
            } else {
               if (t != null) {
                  removeTrack(t);
               }

               t = new BrosClientHandler.Track();
               t.idA = p.idA();
               t.idB = p.idB();
               t.aLeft = p.aLeft();
               t.world = mc.level;
               t.cx = p.cx();
               t.cz = p.cz();
               t.h = p.heading();
               t.entryTicks = Math.max(1, p.entryTicks());
               int me = mc.player.getId();
               if (me == t.idA || me == t.idB) {
                  t.local = true;
                  t.localIsA = me == t.idA;
                  t.localPlayer = mc.player;
                  t.entryDone = p.phase() == 2;
                  t.entryStartX = mc.player.getX();
                  t.entryStartZ = mc.player.getZ();
                  t.entryStartYaw = mc.player.getYRot();
                  t.localY = mc.player.getY();
                  t.localPrevX = mc.player.getX();
                  t.localPrevY = mc.player.getY();
                  t.localPrevZ = mc.player.getZ();
                  t.camYawPrev = t.camYawNow = mc.player.getYRot();
                  if (localTrack != null && localTrack != t) {
                     tracks.remove(localTrack.idA);
                  }

                  localTrack = t;
                  armed = false;
                  releasedSinceEngage = false;
                  gHoldSent = false;
                  gPressAt = 0L;
                  hHoldSent = false;
                  hPressAt = 0L;
                  prevSneak = isDown(mc.options.keyShift);
                  pendingTurn = 0.0;
               }

               tracks.put(t.idA, t);
            }

            t.phase = p.phase();
            t.vx = p.vx();
            t.vz = p.vz();
            t.turnRate = p.turnRate();
            t.shield = Mth.clamp(p.shield(), 0.0F, 1.0F);
            t.hud = p.hud();
            t.rushCd = p.rushCd();
            t.pulseCd = p.pulseCd();
            t.shieldMax = p.shieldMax();
            t.domeRadius = p.domeRadius();
            t.ticksSincePacket = 0;
         }
      }
   }

   private static void removeTrack(BrosClientHandler.Track t) {
      tracks.remove(t.idA);
      if (t == localTrack) {
         localTrack = null;
         pendingTurn = 0.0;
      }
   }

   private static void tick(Minecraft mc) {
      if (mc.player != null && mc.level != null) {
         handleKeys(mc);
         legDrivenNext.clear();
         Iterator<BrosClientHandler.Track> it = tracks.values().iterator();

         while (it.hasNext()) {
            BrosClientHandler.Track t = it.next();
            boolean stale = ++t.ticksSincePacket > 10;
            boolean wrongWorld = t.world != mc.level;
            boolean respawned = t.local && t.localPlayer != mc.player;
            if (!stale && !wrongWorld && !respawned) {
               integrate(t);
               if (t.local) {
                  if (t.phase == 2) {
                     sendInput(mc);
                  } else {
                     pendingTurn = 0.0;
                  }
               }

               place(mc, t);
               spawnDome(mc, t);
            } else {
               if (t == localTrack) {
                  localTrack = null;
                  pendingTurn = 0.0;
               }

               it.remove();
            }
         }

         legDriven.clear();
         legDriven.addAll(legDrivenNext);
      } else {
         if (!tracks.isEmpty() || armed) {
            resetAll();
         }
      }
   }

   private static void handleKeys(Minecraft mc) {
      boolean screen = mc.screen != null;
      boolean g = !screen && isDown(chargeKey());
      boolean h = !screen && isDown(handKey());
      long now = System.currentTimeMillis();
      if (g) {
         if (gDownSince == 0L) {
            gDownSince = now;
         }
      } else {
         gDownSince = 0L;
      }

      if (h) {
         if (hDownSince == 0L) {
            hDownSince = now;
         }
      } else {
         hDownSince = 0L;
      }

      if (!h) {
         hLock = false;
      }

      boolean sneak = !screen && isDown(mc.options.keyShift);
      if (localTrack != null) {
         boolean gate = releasedSinceEngage;
         if (!g && !h) {
            releasedSinceEngage = true;
         }

         if (h) {
            hLock = true;
         }

         if (sneak && !prevSneak) {
            ClientPlayNetworking.send(BrosPayloads.End.INSTANCE);
         } else if (gate) {
            if (h && !prevH) {
               hPressAt = now;
               hHoldSent = false;
            }

            if (h && hPressAt != 0L && !hHoldSent && now - hPressAt >= 300L) {
               send((byte)3, true);
               hHoldSent = true;
            }

            if (!h && prevH && hPressAt != 0L) {
               if (hHoldSent) {
                  send((byte)3, false);
               } else {
                  send((byte)1, true);
               }

               hHoldSent = false;
               hPressAt = 0L;
            }

            if (g && !prevG) {
               gPressAt = now;
               gHoldSent = false;
            }

            if (g && gPressAt != 0L && !gHoldSent && now - gPressAt >= 300L) {
               send((byte)2, true);
               gHoldSent = true;
            }

            if (!g && prevG && gPressAt != 0L) {
               if (gHoldSent) {
                  send((byte)2, false);
               } else {
                  send((byte)0, true);
               }

               gHoldSent = false;
               gPressAt = 0L;
            }
         }
      } else if (!armed) {
         if (g && h && hDownSince >= gDownSince - 400L && now - gDownSince >= 250L) {
            armed = true;
            debug(mc, "§aARMED §7— walk to your bro");
            ChargedDapClientHandler.cancelChargeForBros();
            ClientPlayNetworking.send(new BrosPayloads.Arm(true));
         }
      } else if (!g || !h) {
         armed = false;
         debug(mc, "§7disarmed (" + (!g ? "G" : "H") + " released)");
         ClientPlayNetworking.send(new BrosPayloads.Arm(false));
      } else if (DEBUG && mc.player != null && mc.player.tickCount % 20 == 0) {
         debug(mc, "§earmed, waiting for a bro");
      }

      if (DEBUG && !armed && localTrack == null && g && mc.player != null && mc.player.tickCount % 20 == 0) {
         debug(mc, "§7G held " + (now - gDownSince) + "ms · H " + (h ? "§adown" : "§cup"));
      }

      prevH = h;
      prevG = g;
      prevSneak = sneak;
   }

   private static void debug(Minecraft mc, String text) {
      if (DEBUG && mc.player != null) {
         mc.player.displayClientMessage(Component.literal("§8[bros] " + text), true);
      }
   }

   private static void send(byte kind, boolean down) {
      ClientPlayNetworking.send(new BrosPayloads.Ability(kind, down));
   }

   private static void sendInput(Minecraft mc) {
      float f = 0.0F;
      float r = 0.0F;
      boolean sprint = false;
      if (mc.screen == null) {
         if (isDown(mc.options.keyUp)) {
            f++;
         }

         if (isDown(mc.options.keyDown)) {
            f--;
         }

         if (isDown(mc.options.keyRight)) {
            r++;
         }

         if (isDown(mc.options.keyLeft)) {
            r--;
         }

         sprint = isDown(mc.options.keySprint);
      }

      float turn = (float)(pendingTurn * 1.0);
      pendingTurn = 0.0;
      ClientPlayNetworking.send(new BrosPayloads.Input(f, r, turn, sprint));
   }

   private static KeyMapping chargeKey() {
      return ChargedDapClientHandler.getChargedDapKey();
   }

   private static KeyMapping handKey() {
      return HighFiveClientHandler.getHighFiveKey();
   }

   private static boolean isDown(KeyMapping kb) {
      if (kb == null) {
         return false;
      }

      Minecraft mc = Minecraft.getInstance();
      int code = KeyMappingHelper.getBoundKeyOf(kb).getValue();
      if (code < 0) {
         return false;
      }

      long win = mc.getWindow().handle();
      return code <= 7 ? GLFW.glfwGetMouseButton(win, code) == 1 : GLFW.glfwGetKey(win, code) == 1;
   }

   private static void integrate(BrosClientHandler.Track t) {
      double dcx = t.corrX * 0.35;
      double dcz = t.corrZ * 0.35;
      float dch = (float)(t.corrH * 0.35);
      t.corrX -= dcx;
      t.corrZ -= dcz;
      t.corrH -= dch;
      t.cx = t.cx + (t.vx + dcx);
      t.cz = t.cz + (t.vz + dcz);
      t.h = Mth.wrapDegrees(t.h + t.turnRate + dch);
   }

   private static void place(Minecraft mc, BrosClientHandler.Track t) {
      Entity ea = mc.level.getEntity(t.idA);
      Entity eb = mc.level.getEntity(t.idB);
      double[] pa = BrosHandler.slotAt(t.cx, t.cz, t.h, t.aLeft);
      double[] pb = BrosHandler.slotAt(t.cx, t.cz, t.h, !t.aLeft);
      if (t.local && !t.entryDone) {
         t.entryTick++;
         double k = BrosHandler.smooth(Math.min(1.0, (double)t.entryTick / t.entryTicks));
         double[] target = t.localIsA ? pa : pb;
         double x = Mth.lerp(k, t.entryStartX, target[0]);
         double z = Mth.lerp(k, t.entryStartZ, target[1]);
         float yaw = t.entryStartYaw + Mth.wrapDegrees(t.h - t.entryStartYaw) * (float)k;
         writeLocal(mc.player, t, x, z, yaw);
         if (t.entryTick >= t.entryTicks) {
            t.entryDone = true;
         }
      } else if (t.phase == 2 || t.local) {
         if (ea != null) {
            if (ea == mc.player) {
               writeLocal(mc.player, t, pa[0], pa[1], t.h);
            } else if (t.phase == 2) {
               writeRemote(ea, t.havePrev ? t.prevAX : ea.getX(), t.havePrev ? t.prevAZ : ea.getZ(), pa[0], pa[1], t);
            }
         }

         if (eb != null) {
            if (eb == mc.player) {
               writeLocal(mc.player, t, pb[0], pb[1], t.h);
            } else if (t.phase == 2) {
               writeRemote(eb, t.havePrev ? t.prevBX : eb.getX(), t.havePrev ? t.prevBZ : eb.getZ(), pb[0], pb[1], t);
            }
         }

         t.prevAX = pa[0];
         t.prevAZ = pa[1];
         t.prevBX = pb[0];
         t.prevBZ = pb[1];
         t.prevH = t.h;
         t.havePrev = true;
      }
   }

   private static void writeLocal(LocalPlayer p, BrosClientHandler.Track t, double x, double z, float yaw) {
      double lastX = t.localPrevX;
      double lastY = t.localPrevY;
      double lastZ = t.localPrevZ;
      BrosPhysics.Result r = BrosPhysics.resolve(p.level(), p, x, z, t.localY, t.localVy, false);
      t.localY = r.y();
      t.localVy = r.vy();
      p.setPos(x, t.localY, z);
      p.setDeltaMovement(Vec3.ZERO);
      p.xo = lastX;
      p.yo = lastY;
      p.zo = lastZ;
      p.xOld = lastX;
      p.yOld = lastY;
      p.zOld = lastZ;
      t.localPrevX = x;
      t.localPrevY = t.localY;
      t.localPrevZ = z;
      driveLegs(p, Math.hypot(x - lastX, z - lastZ));
      t.camYawPrev = t.camYawNow;
      t.camYawNow = yaw;
   }

   private static void writeRemote(Entity e, double lastX, double lastZ, double x, double z, BrosClientHandler.Track t) {
      e.setPos(x, e.getY(), z);
      e.xo = lastX;
      e.zo = lastZ;
      e.xOld = lastX;
      e.zOld = lastZ;
      if (e instanceof LivingEntity le) {
         driveLegs(le, Math.hypot(x - lastX, z - lastZ));
      }

      float prevYaw = t.havePrev ? t.prevH : t.h;
      e.setYRot(t.h);
      e.yRotO = prevYaw;
      if (e instanceof LivingEntity le) {
         le.setYHeadRot(t.h);
         le.setYBodyRot(t.h);
         le.yHeadRotO = prevYaw;
         le.yBodyRotO = prevYaw;
      }
   }

   private static void driveLegs(LivingEntity e, double movedThisTick) {
      float target = Math.min((float)movedThisTick * 4.0F, 1.0F) * 1.0F;
      e.walkAnimation.update(target, 0.4F, 0.75F);
      legDrivenNext.add(e.getId());
   }

   private static void renderHud(GuiGraphicsExtractor ctx) {
      Minecraft mc = Minecraft.getInstance();
      BrosClientHandler.Track t = localTrack;
      if (t != null && mc.player != null && !mc.options.hideGui) {
         if (t.phase == 2) {
            Font tr = mc.font;
            int cx = ctx.guiWidth() / 2;
            int bottom = ctx.guiHeight() - 52;
            boolean filling = (t.hud & 64) != 0;
            int sx = 0;
            int sy = 0;
            if (filling) {
               sx = mc.level.random.nextInt(3) - 1;
               sy = mc.level.random.nextInt(3) - 1;
            }

            float hearts = t.shield * t.shieldMax / 2.0F;
            String label = fmtHearts(hearts) + "❤";
            int barW = 60;
            int barH = 3;
            int rowW = tr.width(label) + 4 + barW;
            int x = cx - rowW / 2 + sx;
            int y = bottom - 20 + sy;
            boolean bunkered = (t.hud & 2048) != 0;
            int shieldColor = bunkered ? -1 : -9699462;
            ctx.drawString(tr, label, x, y, shieldColor, true);
            int bx = x + tr.width(label) + 4;
            int by = y + 3;
            ctx.fill(bx - 1, by - 1, bx + barW + 1, by + barH + 1, -2013265920);
            int fillW = Math.round(barW * t.shield);
            if (fillW > 0) {
               ctx.fill(bx, by, bx + fillW, by + barH, shieldColor);
            }

            String rush = "[" + keyName(chargeKey(), "G") + "] RUSH";
            String pulse = "[" + keyName(handKey(), "H") + "] PULSE";
            boolean pulseAffordable = (t.hud & 512) != 0;
            int gap = 12;
            int x2 = cx - (tr.width(rush) + gap + tr.width(pulse)) / 2;
            int y2 = bottom - 8;
            boolean rushAffordable = (t.hud & 1024) != 0;
            ctx.drawString(tr, rush, x2, y2, t.rushCd == 0 && rushAffordable ? -9699462 : -7697782, true);
            ctx.drawString(tr, pulse, x2 + tr.width(rush) + gap, y2, t.pulseCd == 0 && pulseAffordable ? -9699462 : -7697782, true);
         }
      }
   }

   private static String keyName(KeyMapping kb, String fallback) {
      if (kb == null) {
         return fallback;
      }

      try {
         String n = KeyMappingHelper.getBoundKeyOf(kb).getDisplayName().getString();
         return n.isEmpty() ? fallback : n.toUpperCase();
      } catch (Throwable ignored) {
         return fallback;
      }
   }

   private static String fmtHearts(float h) {
      return Math.abs(h - Math.round(h)) < 0.05F ? String.valueOf(Math.round(h)) : String.format("%.1f", h);
   }

   private static void spawnDome(Minecraft mc, BrosClientHandler.Track t) {
      if (mc.level != null) {
         Entity ea = mc.level.getEntity(t.idA);
         Entity eb = mc.level.getEntity(t.idB);
         double baseY;
         if (ea != null && eb != null) {
            baseY = Math.min(ea.getY(), eb.getY());
         } else if (ea != null) {
            baseY = ea.getY();
         } else {
            if (eb == null) {
               return;
            }

            baseY = eb.getY();
         }

         int color = lerpColor(16728128, 5629695, t.shield);
         DustParticleOptions dust = new DustParticleOptions(color, 0.9F);
         t.domeSpin += 0.09;

         for (int i = 0; i < 8; i++) {
            double a = t.domeSpin + (Math.PI * 2) * i / 8.0;
            double y = baseY + 0.1 + i % 3 * 1.35;
            double r = t.domeRadius * (i % 3 == 2 ? 0.8 : 1.0);
            mc.level.addParticle(dust, false, false, t.cx + Math.cos(a) * r, y, t.cz + Math.sin(a) * r, t.vx, 0.0, t.vz);
         }
      }
   }

   private static int lerpColor(int from, int to, float k) {
      int r = Mth.lerpInt(k, from >> 16 & 0xFF, to >> 16 & 0xFF);
      int g = Mth.lerpInt(k, from >> 8 & 0xFF, to >> 8 & 0xFF);
      int b = Mth.lerpInt(k, from & 0xFF, to & 0xFF);
      return r << 16 | g << 8 | b;
   }

   public static void applyLocalYaw() {
      BrosClientHandler.Track t = localTrack;
      Minecraft mc = Minecraft.getInstance();
      if (t != null && mc.player != null && t.localPlayer == mc.player) {
         float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
         float yaw = t.camYawPrev + Mth.wrapDegrees(t.camYawNow - t.camYawPrev) * td;
         LocalPlayer p = mc.player;
         p.setYRot(yaw);
         p.yRotO = yaw;
         p.setYHeadRot(yaw);
         p.yHeadRotO = yaw;
         p.setYBodyRot(yaw);
         p.yBodyRotO = yaw;
      }
   }

   private static final class Track {
      int idA;
      int idB;
      ClientLevel world;
      boolean aLeft;
      byte phase;
      double cx;
      double cz;
      double vx;
      double vz;
      float h;
      float turnRate;
      float shield = 1.0F;
      float shieldMax = 40.0F;
      float domeRadius = 2.2F;
      int hud;
      int rushCd;
      int pulseCd;
      double domeSpin;
      double corrX;
      double corrZ;
      float corrH;
      int ticksSincePacket;
      boolean local;
      boolean localIsA;
      LocalPlayer localPlayer;
      boolean entryDone;
      int entryTick;
      int entryTicks;
      double entryStartX;
      double entryStartZ;
      float entryStartYaw;
      double localY;
      double localVy;
      double localPrevX;
      double localPrevY;
      double localPrevZ;
      boolean havePrev;
      double prevAX;
      double prevAZ;
      double prevBX;
      double prevBZ;
      float prevH;
      float camYawPrev;
      float camYawNow;
   }
}
