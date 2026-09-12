package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;

/** Item-only callbacks inside these operations inherit the actual actor, including addon reentry. */
@Mixin(value=ItemSlashBlade.class,remap=false)
public abstract class LegacyItemModeMixin {
    @WrapMethod(method="use")
    private InteractionResultHolder<ItemStack> modeUse(Level level,Player player,InteractionHand hand,Operation<InteractionResultHolder<ItemStack>> original){
        try(var scope=LegacyMode.context(player)){LegacyMode.bind(player.getItemInHand(hand),player);return original.call(level,player,hand);}
    }
    @WrapMethod(method="onLeftClickEntity")
    private boolean modeLeft(ItemStack blade,Player player,Entity target,Operation<Boolean> original){
        try(var scope=LegacyMode.context(player)){LegacyMode.bind(blade,player);return original.call(blade,player,target);}
    }
    @WrapMethod(method="hurtEnemy")
    private boolean modeHit(ItemStack blade,LivingEntity target,LivingEntity user,Operation<Boolean> original){
        try(var scope=LegacyMode.context(user)){LegacyMode.bind(blade,user);return original.call(blade,target,user);}
    }
    @WrapMethod(method="inventoryTick")
    private void modeInventory(ItemStack blade,Level level,Entity user,int slot,boolean selected,Operation<Void> original){
        try(var scope=LegacyMode.context(user)){LegacyMode.bind(blade,user);original.call(blade,level,user,slot,selected);}
    }
    @WrapMethod(method="releaseUsing")
    private void modeRelease(ItemStack blade,Level level,LivingEntity user,int time,Operation<Void> original){
        try(var scope=LegacyMode.context(user)){LegacyMode.bind(blade,user);original.call(blade,level,user,time);}
    }
}
