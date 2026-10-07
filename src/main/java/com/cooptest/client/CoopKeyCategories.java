package com.cooptest.client;

import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

public final class CoopKeyCategories {
   public static final Category COOPMOVES = Category.register(Identifier.fromNamespaceAndPath("coopmoves", "coopmoves"));
   public static final Category COOPTEST = Category.register(Identifier.fromNamespaceAndPath("cooptest", "cooptest"));

   private CoopKeyCategories() {
   }
}
