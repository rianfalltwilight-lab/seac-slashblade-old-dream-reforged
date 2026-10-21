package org.scex.slashbladelegacy;

import java.util.Optional;
import java.util.Map;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Read the blade capability without creating item NBT. */
public final class BladeStateAccess {
    private BladeStateAccess() {}
    public static Optional<ISlashBladeState> of(ItemStack blade) {
        return blade.getCapability(ItemSlashBlade.BLADESTATE).resolve();
    }

    /** Resolve elapsed combo time without sending events or writing blade state. */
    public static Map.Entry<Integer,ResourceLocation> peek(ISlashBladeState state,LivingEntity entity) {
        var registry=ComboStateRegistry.REGISTRY.get();
        ResourceLocation current=registry.containsKey(state.getComboSeq())?state.getComboSeq():ComboStateRegistry.NONE.getId();
        var combo=registry.getValue(current);
        if(combo==null)combo=ComboStateRegistry.NONE.get();
        int time=(int)TimeValueHelper.getMSecFromTicks(state.getElapsedTime(entity));
        while(combo!=null && !current.equals(ComboStateRegistry.NONE.getId()) && combo.getTimeoutMS()<time) {
            time-=combo.getTimeoutMS();
            current=combo.getNextOfTimeout(entity);
            combo=registry.getValue(current);
            if(combo==null) {
                current=ComboStateRegistry.NONE.getId();
                combo=ComboStateRegistry.NONE.get();
            }
        }
        return Map.entry((int)TimeValueHelper.getTicksFromMSec(time),current);
    }
}
