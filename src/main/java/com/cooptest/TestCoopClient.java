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
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

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
                        boolean rmbHeld = GLFW.glfwGetMouseButton(win, 1) == 1;
                        ClientPlayNetworking.send(new ClapHandler.TodoRightClickPayload(rmbHeld));
                     }
                  }
               }
            }
         );
      CoopAnimationHandler.register();
      SitClientHandler.register();
      BlackHoodClientHandler.register();
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
                     // $VF: Couldn't be decompiled
                     // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
                     // java.lang.RuntimeException: invalid constant type: Ljava/io/Serializable; with value CoopEMFPauseCondition
                     //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent.toJava(ConstExprent.java:364)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.exps.SwitchExprent.toJava(SwitchExprent.java:152)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1054)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent.toJava(ExitExprent.java:85)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:860)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement.toJava(SequenceStatement.java:107)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:860)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement.toJava(IfStatement.java:238)
                     //   at org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement.toJava(RootStatement.java:36)
                     //   at org.jetbrains.java.decompiler.main.ClassWriter.methodLambdaToJava(ClassWriter.java:1017)
                     //
                     // Bytecode:
                     // 00: aload 1
                     // 01: invokevirtual java/lang/reflect/Method.getDeclaringClass ()Ljava/lang/Class;
                     // 04: ldc java/lang/Object
                     // 06: if_acmpne 88
                     // 09: aload 1
                     // 0a: invokevirtual java/lang/reflect/Method.getName ()Ljava/lang/String;
                     // 0d: astore 3
                     // 0e: bipush -1
                     // 0f: istore 4
                     // 11: aload 3
                     // 12: invokevirtual java/lang/String.hashCode ()I
                     // 15: lookupswitch 56 2 -1295482945 43 147696667 27
                     // 30: aload 3
                     // 31: ldc_w "hashCode"
                     // 34: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
                     // 37: ifeq 4d
                     // 3a: bipush 0
                     // 3b: istore 4
                     // 3d: goto 4d
                     // 40: aload 3
                     // 41: ldc_w "equals"
                     // 44: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
                     // 47: ifeq 4d
                     // 4a: bipush 1
                     // 4b: istore 4
                     // 4d: iload 4
                     // 4f: lookupswitch 53 2 0 25 1 35
                     // 68: aload 0
                     // 69: invokestatic java/lang/System.identityHashCode (Ljava/lang/Object;)I
                     // 6c: invokestatic java/lang/Integer.valueOf (I)Ljava/lang/Integer;
                     // 6f: goto 87
                     // 72: aload 0
                     // 73: aload 2
                     // 74: bipush 0
                     // 75: aaload
                     // 76: if_acmpne 7d
                     // 79: bipush 1
                     // 7a: goto 7e
                     // 7d: bipush 0
                     // 7e: invokestatic java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
                     // 81: goto 87
                     // 84: ldc_w "CoopEMFPauseCondition"
                     // 87: areturn
                     // 88: aload 2
                     // 89: bipush 0
                     // 8a: aaload
                     // 8b: astore 3
                     // 8c: aload 3
                     // 8d: invokevirtual java/lang/Object.getClass ()Ljava/lang/Class;
                     // 90: ldc_w "etf$getUuid"
                     // 93: bipush 0
                     // 94: anewarray 270
                     // 97: invokevirtual java/lang/Class.getMethod (Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;
                     // 9a: aload 3
                     // 9b: bipush 0
                     // 9c: anewarray 4
                     // 9f: invokevirtual java/lang/reflect/Method.invoke (Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
                     // a2: checkcast java/util/UUID
                     // a5: astore 4
                     // a7: aload 4
                     // a9: ifnonnull b1
                     // ac: bipush 0
                     // ad: invokestatic java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
                     // b0: areturn
                     // b1: aload 4
                     // b3: invokestatic com/cooptest/client/CoopAnimationHandler.isAnimating (Ljava/util/UUID;)Z
                     // b6: invokestatic java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
                     // b9: areturn
                     // ba: astore 3
                     // bb: bipush 0
                     // bc: invokestatic java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
                     // bf: areturn
                     // try (43 -> 63): 68 java/lang/Exception
                     // try (64 -> 67): 68 java/lang/Exception
                  }
               );
               registerMethod.invoke(null, condition);
            }
         } catch (Exception var7) {
         }
      }

      ClientCommandRegistrationCallback.EVENT.register((ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> {
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("impacttest").executes(ctx -> {
            CoopImpactHandler.start(CoopImpactHandler.REGULAR_DAP_SEQUENCE, 33L, true);
            CoopCameraShakeHandler.shake(0.6F, CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L);
            return 1;
         }));
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("impactperfect").executes(ctx -> {
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
         dispatcher.register((LiteralArgumentBuilder)ClientCommandManager.literal("impactheaven").executes(ctx -> {
            CoopImpactHandler.start(CoopImpactHandler.HEAVEN_DAP_SEQUENCE, 33L, false);
            CoopCameraShakeHandler.shake(0.6F, CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L);
            return 1;
         }));
      });
   }
}
