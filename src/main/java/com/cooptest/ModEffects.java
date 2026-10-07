package com.cooptest;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;


public class ModEffects {

    public static Holder<MobEffect> MAHITO;

    public static void register() {
        MAHITO = Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath("testcoop", "mahito"),
                new MahitoEffect()
        );
    }
}