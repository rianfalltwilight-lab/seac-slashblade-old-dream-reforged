package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.world.entity.LivingEntity;

/** Adjust delays only for swords from legacy Gale and Lightning SA. */
public final class LegacyAddonArtTiming {
    private static final String SINGLE_DELAY="slashblade_legacy_compat.single_sa_delay";
    private LegacyAddonArtTiming() {}
    public static int gale(EntityAbstractSummonedSword sword,int delay) {
        if(sword.getOwner() instanceof LivingEntity owner && LegacyMode.legacy(owner)) {
            sword.setColor(0xFF3333);
            return configure(sword,7);
        }
        return delay;
    }
    public static int lightning(EntityAbstractSummonedSword sword,int delay,boolean finalSword) {
        if(sword.getOwner() instanceof LivingEntity owner && LegacyMode.legacy(owner))
            return configure(sword,finalSword?17+Math.max(0,delay-21)/2:7+Math.max(0,delay-20)/2);
        return delay;
    }
    private static int configure(EntityAbstractSummonedSword sword,int delay) {
        sword.getPersistentData().putBoolean(SINGLE_DELAY,true);
        return delay;
    }
    public static int repeatedDelay(EntityAbstractSummonedSword sword,int delay) {
        return sword.getPersistentData().getBoolean(SINGLE_DELAY)?0:delay;
    }
}
