package org.scex.slashbladelegacy.client;

import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.scex.slashbladelegacy.LegacyCompat;

/** r87 doSwingItem restarts the normal arm clock for every accepted combo, including repeats. */
@EventBusSubscriber(modid=LegacyCompat.MOD_ID,value=Dist.CLIENT)
public final class LegacyBodyAnimations {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void start(BladeMotionEvent event) {
        var entity=event.getEntity();
        if(!entity.level().isClientSide || !LegacyHeldRenderer.handles(entity) || event.getCombo().equals(ComboStateRegistry.NONE.getId()))return;
        entity.swinging=true;entity.swingTime=0;entity.attackAnim=0;entity.oAttackAnim=0;
    }
}
