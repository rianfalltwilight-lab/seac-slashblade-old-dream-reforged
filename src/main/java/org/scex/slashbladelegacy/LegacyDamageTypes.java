package org.scex.slashbladelegacy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public final class LegacyDamageTypes {
    public static final ResourceKey<DamageType> DIMENSION=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"dimension"));
    public static final ResourceKey<DamageType> UPTHRUST=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"upthrust"));
    private LegacyDamageTypes(){}
}
