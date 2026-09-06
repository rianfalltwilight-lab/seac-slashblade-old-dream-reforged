package org.scex.slashbladelegacy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;

public final class HostileTargeting {
    private static final ResourceLocation GAIA=ResourceLocation.parse("botania:gaia_guardian");
    public static final TagKey<EntityType<?>> ADDITIONAL=TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.parse("slashblade_legacy_compat:additional_hostile_targets"));
    public static final ThreadLocal<LivingEntity> REVENGE_CONTEXT=new ThreadLocal<>();
    private HostileTargeting() {}
    public static boolean isAdditionalHostile(LivingEntity target) {
        if(GAIA.equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())))
            return LegacyCompat.GAIA_TARGETING.get();
        return LegacyCompat.HOSTILE_TARGETING.get() && target.getType().is(ADDITIONAL)
                && !(target instanceof OwnableEntity ownable && ownable.getOwnerUUID()!=null);
    }
}
