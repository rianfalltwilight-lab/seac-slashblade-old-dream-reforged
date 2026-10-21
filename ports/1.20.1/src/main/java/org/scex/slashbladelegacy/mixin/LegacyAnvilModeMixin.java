package org.scex.slashbladelegacy.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(AnvilMenu.class)
public abstract class LegacyAnvilModeMixin {
    @WrapMethod(method="createResult")
    private void modeResult(Operation<Void> original){
        var user=((LegacyAnvilPlayerAccessor)this).legacy$player();
        try(var scope=LegacyMode.context(user)){
            var menu=(AnvilMenu)(Object)this;
            LegacyMode.bind(menu.getSlot(0).getItem(),user);
            boolean legacySoul=LegacyMode.legacy(user) && menu.getSlot(0).getItem().getItem() instanceof ItemSlashBlade
                    && org.scex.slashbladelegacy.BladeApi.isProudSoul(menu.getSlot(1).getItem());
            // Forge 1.20 returns on cancellation without clearing the previous result.
            if(legacySoul)menu.getSlot(2).set(ItemStack.EMPTY);
            original.call();
            // The cancelled path also keeps cost=1. Anvil pickup checks cost, not slot contents.
            if(legacySoul && menu.getSlot(2).getItem().isEmpty())menu.setMaximumCost(0);
        }
    }
}
