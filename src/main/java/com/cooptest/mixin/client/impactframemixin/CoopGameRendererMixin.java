package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopChromaHandler;
import com.cooptest.client.CoopRadialBlurHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class CoopGameRendererMixin {
   @Unique
   private static final boolean coop$FORCE_DISABLE_POST_EFFECTS = false;
   @Unique
   private static final Identifier coop$CHROMA_ID = Identifier.fromNamespaceAndPath("cooptest", "chroma");
   @Unique
   private static final Identifier coop$RADIAL_ID = Identifier.fromNamespaceAndPath("cooptest", "radialblur");
   @Unique
   private static boolean coop$chromaLoaded = false;
   @Unique
   private static boolean coop$radialLoaded = false;
   @Unique
   private static boolean coop$chromaBroken = false;
   @Unique
   private static boolean coop$radialBroken = false;

   @Shadow
   public abstract void setPostEffect(Identifier var1);

   @Shadow
   public abstract void clearPostEffect();

   @Inject(method = "render", at = @At("HEAD"))
   private void coopHandlePostEffects(DeltaTracker counter, boolean tick, CallbackInfo ci) {
      boolean wantRadial = CoopRadialBlurHandler.isActive() && !coop$radialBroken;
      boolean wantChroma = CoopChromaHandler.isActive() && !coop$chromaBroken;
      if (wantRadial) {
         if (!coop$radialLoaded) {
            if (this.coop$trySet(coop$RADIAL_ID, "radialblur")) {
               coop$radialLoaded = true;
               coop$chromaLoaded = false;
            } else {
               coop$radialBroken = true;
            }
         }
      } else if (wantChroma) {
         if (!coop$chromaLoaded) {
            if (this.coop$trySet(coop$CHROMA_ID, "chroma")) {
               coop$chromaLoaded = true;
               coop$radialLoaded = false;
            } else {
               coop$chromaBroken = true;
            }
         }
      } else if (coop$chromaLoaded || coop$radialLoaded) {
         try {
            this.clearPostEffect();
         } catch (Throwable var7) {
         }

         coop$chromaLoaded = false;
         coop$radialLoaded = false;
      }
   }

   @Unique
   private boolean coop$trySet(Identifier id, String name) {
      try {
         this.setPostEffect(id);
         return true;
      } catch (Throwable t) {
         System.err
            .println(
               "[COOP] post effect '"
                  + name
                  + "' failed to load and is disabled for this session. Expected at assets/cooptest/post_effect/"
                  + name
                  + ".json in the 1.21.5+ schema. Cause: "
                  + t
            );
         t.printStackTrace();

         try {
            this.clearPostEffect();
         } catch (Throwable var5) {
         }

         return false;
      }
   }
}
