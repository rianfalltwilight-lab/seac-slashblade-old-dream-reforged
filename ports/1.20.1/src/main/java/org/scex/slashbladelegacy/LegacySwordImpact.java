package org.scex.slashbladelegacy;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;

/** Framework notification for an accepted old sword impact, without entering modern damage. */
final class LegacySwordImpact {
    private static final ThreadLocal<Set<Entity>> NOTIFYING=new ThreadLocal<>();
    private LegacySwordImpact() {}
    static boolean notifying(Entity sword){var active=NOTIFYING.get();return active!=null && active.contains(sword);}
    static void post(EntityAbstractSummonedSword sword,Entity target){
        if(sword.level().isClientSide)return;
        var active=NOTIFYING.get();
        if(active==null){active=Collections.newSetFromMap(new IdentityHashMap<>());NOTIFYING.set(active);}
        if(!active.add(sword))return;
        try {BladeEvents.impact(sword,target);}
        finally {active.remove(sword);if(active.isEmpty())NOTIFYING.remove();}
    }
}
