package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.world.entity.LivingEntity;

/** Apply only to the swords emitted by Gale/Lightning SA; other SJAP formations keep their timing. */
public final class LegacyAddonArtTiming {
    private static final String SINGLE_DELAY="slashblade_legacy_compat.single_sa_delay";
    private LegacyAddonArtTiming() {}
    public static void gale(EntityAbstractSummonedSword sword,int delay) {
        if(sword.getOwner() instanceof LivingEntity owner && LegacyMode.legacy(owner)) {
            sword.setColor(0xFF3333);
            configure(sword,7);
        }else sword.setDelay(delay);
    }
    public static void lightning(EntityAbstractSummonedSword sword,int delay,boolean finalSword) {
        if(sword.getOwner() instanceof LivingEntity owner && LegacyMode.legacy(owner))
            configure(sword,finalSword?17+Math.max(0,delay-21)/2:7+Math.max(0,delay-20)/2);
        else sword.setDelay(delay);
    }
    private static void configure(EntityAbstractSummonedSword sword,int delay) {
        sword.setDelay(delay);
        sword.getPersistentData().putBoolean(SINGLE_DELAY,true);
    }
    public static int repeatedDelay(EntityAbstractSummonedSword sword) {
        return sword.getPersistentData().getBoolean(SINGLE_DELAY)?0:sword.getDelay();
    }
}
