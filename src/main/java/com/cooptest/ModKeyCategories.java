package com.cooptest;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class ModKeyCategories {
    public static final KeyMapping.Category COOPMOVES =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("testcoop", "coopmoves"));
}