package org.scex.slashbladelegacy.mixin;
import java.util.List;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ItemStack.class)
public abstract class LegacyTooltipModeMixin {
    @WrapMethod(method="getTooltipLines")
    private List<Component> modeTooltip(Item.TooltipContext context,Player viewer,TooltipFlag flags,Operation<List<Component>> original){
        if(viewer==null)return original.call(context,viewer,flags);
        try(var scope=LegacyMode.context(viewer)){return original.call(context,viewer,flags);}
    }
}
