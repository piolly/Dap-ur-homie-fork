package com.cooptest;

import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.client.BlackHoodClientHandler;
import com.cooptest.client.BullyDapClientHandler;
import com.cooptest.client.CatchClientHandler;
import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.ClapClientHandler;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.CoopCameraShakeHandler;
import com.cooptest.client.CoopChromaHandler;
import com.cooptest.client.CoopClientSettings;
import com.cooptest.client.CoopImpactHandler;
import com.cooptest.client.CoopRadialBlurHandler;
import com.cooptest.client.CoopScreenSquishHandler;
import com.cooptest.client.CoopShockwaveRenderer;
import com.cooptest.client.CoopSpeedLinesRenderer;
import com.cooptest.client.DapFlairClientHandler;
import com.cooptest.client.DapHoldClientHandler;
import com.cooptest.client.DapRunClientHandler;
import com.cooptest.client.DuoPoseClientHandler;
import com.cooptest.client.FallDapClientHandler;
import com.cooptest.client.FusionClientHandler;
import com.cooptest.client.GrabClientEffects;
import com.cooptest.client.GrabClientNetworking;
import com.cooptest.client.GroundPoundClientHandler;
import com.cooptest.client.HeavenDapClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.HighFivePassClientHandler;
import com.cooptest.client.HuddleClientHandler;
import com.cooptest.client.KickClientHandler;
import com.cooptest.client.MahitoClientHandler;
import com.cooptest.client.MarioJumpClientHandler;
import com.cooptest.client.MeteorStrikeClientHandler;
import com.cooptest.client.PushClientHandler;
import com.cooptest.client.QTEClientHandler;
import com.cooptest.client.SikeFollowUpClientHandler;
import com.cooptest.client.SitClientHandler;
import com.cooptest.client.SlapClientHandler;
import com.cooptest.client.SpearStrikeClientHandler;
import com.cooptest.client.SpinClientHandler;
import com.cooptest.client.StrongSlapClientHandler;
import com.cooptest.client.ThrowPowerHUD;
import com.cooptest.client.TrajectoryRenderer;
import com.cooptest.highfive.client.HighFiveShakeClientHandler;
import com.cooptest.highfive.client.ReadyFiveClientHandler;
import com.cooptest.highfive.client.ReadyHugClientHandler;
import com.cooptest.highfive.client.ReadyPushClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import com.cooptest.spin.client.HandSpinRenderSync;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class TestCoopClient implements ClientModInitializer {
   public void onInitializeClient() {
      PoseNetworking.registerClientReceiver();
      GrabClientNetworking.register();
      GrabInputHandler.register();
      GrabClientEffects.register();
      ThrowPowerHUD.register();
      TrajectoryRenderer.register();
      HighFiveClientHandler.register();
      HighFivePassClientHandler.register();
      HighFiveShakeClientHandler.register();
      ReadyFiveClientHandler.register();
      ReadyHugClientHandler.register();
      ChargedDapClientHandler.register();
      PushClientHandler.register();
      ReadyPushClientHandler.register();
      CatchClientHandler.register();
      MahitoClientHandler.register();
      FallDapClientHandler.register();
      DapHoldClientHandler.register();
      MarioJumpClientHandler.register();
      BullyDapClientHandler.register();
      HeavenDapClientHandler.register();
      QTEClientHandler.registerReceivers();
      ClapClientHandler.register();
      DapFusionHandler.registerClientPayloads();
      FusionClientHandler.register();
      MeteorStrikeHandler.registerClientPayloads();
      MeteorStrikeClientHandler.register();
      KickClientHandler.register();
      SpinClientHandler.register();
      GroundPoundClientHandler.register();
      HandSpinClientHandler.register();
      HandSpinRenderSync.register();
      BrosClientHandler.register();
      SpearStrikeClientHandler.register();
      CoopClientSettings.register();
      DapRunClientHandler.register();
      DapFlairClientHandler.register();
      SlapClientHandler.register();
      HuddleClientHandler.register();
      StrongSlapClientHandler.register();
      SikeFollowUpClientHandler.register();
      DuoPoseClientHandler.register();
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (client.player != null) {
                  if (client.player.getMainHandItem().isEmpty()) {
                     boolean hasTodo = client.player
                        .hasEffect((Holder)BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("testcoop", "todo")).orElse(null));
                     if (hasTodo) {
                        long win = client.getWindow().handle();
                        boolean rmbHeld = net.minecraft.client.Minecraft.getInstance().options.keyUse.isDown();
                        ClientPlayNetworking.send(new ClapHandler.TodoRightClickPayload(rmbHeld));
                     }
                  }
               }
            }
         );
      CoopAnimationHandler.register();
      SitClientHandler.register();
      BlackHoodClientHandler.register();
      com.cooptest.meme.SpinYeetClientHandler.register();
      com.cooptest.client.BonkClientHandler.register();
      com.cooptest.client.DivineFlamComboClient.register();
      ClientPlayNetworking.registerGlobalReceiver(
         NormalFacingDapHandler.FaceDapSessionPayload.ID,
         (payload, context) -> context.client().execute(() -> ChargedDapClientHandler.setInFaceDapSession(payload.active()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         NormalFacingDapHandler.FaceDapShakePayload.ID,
         (payload, context) -> context.client().execute(() -> CoopCameraShakeHandler.shake(payload.amount(), payload.durationMs()))
      );
      if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
         try {
            Class<?> apiClass = Class.forName("traben.entity_model_features.EMFAnimationApi");
            Method registerMethod = null;

            for (Method m : apiClass.getMethods()) {
               if (m.getName().equals("registerPauseCondition") && m.getParameterCount() == 1) {
                  registerMethod = m;
                  break;
               }
            }

            if (registerMethod != null) {
               Class<?> conditionType = registerMethod.getParameterTypes()[0];
               Object condition = Proxy.newProxyInstance(
                  conditionType.getClassLoader(),
                  new Class[]{conditionType},
                  (proxy, method, args) -> {
                     if (method.getDeclaringClass() == Object.class) {
                        String n = method.getName();
                        if (n.equals("hashCode")) {
                           return System.identityHashCode(proxy);
                        }
                        if (n.equals("equals")) {
                           return proxy == args[0];
                        }
                        return "CoopEMFPauseCondition";
                     }
                     try {
                        Object entity = args[0];
                        java.util.UUID uuid = (java.util.UUID)entity.getClass().getMethod("etf$getUuid").invoke(entity);
                        if (uuid == null) {
                           return false;
                        }
                        return CoopAnimationHandler.isAnimating(uuid);
                     } catch (Exception e) {
                        return false;
                     }
                  }
               );
               registerMethod.invoke(null, condition);
            }
         } catch (Exception var7) {
         }
      }

      ClientCommandRegistrationCallback.EVENT.register((ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> {
         dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("impacttest").executes(ctx -> {
            CoopImpactHandler.start(CoopImpactHandler.REGULAR_DAP_SEQUENCE, 33L, true);
            CoopCameraShakeHandler.shake(0.6F, CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L);
            return 1;
         }));
         dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("impactperfect").executes(ctx -> {
            CoopImpactHandler.start(CoopImpactHandler.PERFECT_DAP_SEQUENCE, 33L, true);
            CoopCameraShakeHandler.shake(0.6F, CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L);
            CoopChromaHandler.start();
            CoopRadialBlurHandler.start();
            CoopSpeedLinesRenderer.start();
            CoopScreenSquishHandler.trigger();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               CoopShockwaveRenderer.start(mc.player.position().add(0.0, 1.0, 0.0));
            }

            return 1;
         }));
         dispatcher.register((LiteralArgumentBuilder)ClientCommands.literal("impactheaven").executes(ctx -> {
            CoopImpactHandler.start(CoopImpactHandler.HEAVEN_DAP_SEQUENCE, 33L, false);
            CoopCameraShakeHandler.shake(0.6F, CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L);
            return 1;
         }));
      });
   }
}
