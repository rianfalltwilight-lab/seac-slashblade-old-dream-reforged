package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemSlashBlade.class)
public abstract class LegacyInventoryMixin {
    @Inject(method="inventoryTick",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$inventory(ItemStack blade,Level level,Entity entity,int slot,boolean selected,CallbackInfo ci) {
        if(LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){LegacyInventory.tick(blade,level,entity,slot,selected);ci.cancel();}
    }
}
