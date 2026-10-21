package org.scex.slashbladelegacy.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.*;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EnchantmentMenu.class)
public abstract class LegacyEnchantingModeMixin {
    @Unique private Player legacy$player;
    @Inject(method="<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",at=@At("RETURN"))
    private void modeOwner(int id,Inventory inventory,ContainerLevelAccess access,CallbackInfo ci){legacy$player=inventory.player;}
    @WrapMethod(method="slotsChanged")
    private void modePreview(Container container,Operation<Void> original){
        if(legacy$player==null){original.call(container);return;}
        try(var scope=LegacyMode.context(legacy$player)){LegacyMode.bind(container.getItem(0),legacy$player);original.call(container);}
    }
    @WrapMethod(method="clickMenuButton")
    private boolean modeEnchant(Player user,int id,Operation<Boolean> original){
        try(var scope=LegacyMode.context(user)){return original.call(user,id);}
    }
}
