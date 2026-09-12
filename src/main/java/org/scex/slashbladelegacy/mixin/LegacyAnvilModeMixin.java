package org.scex.slashbladelegacy.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.inventory.AnvilMenu;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(AnvilMenu.class)
public abstract class LegacyAnvilModeMixin {
    @WrapMethod(method="createResult")
    private void modeResult(Operation<Void> original){
        var user=((LegacyAnvilPlayerAccessor)this).legacy$player();
        try(var scope=LegacyMode.context(user)){
            LegacyMode.bind(((AnvilMenu)(Object)this).getSlot(0).getItem(),user);original.call();
        }
    }
}
