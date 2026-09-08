package org.scex.slashbladelegacy.mixin;

import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.NeoForge;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** r87 breaks after exceeding maximum damage, preserving the framework's public break callback. */
@Mixin(ItemSlashBlade.class)
public abstract class LegacyDamageBoundaryMixin {
    private static boolean applies(ItemStack blade){return LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && !blade.is(LegacyDurability.FRAMEWORK_BOUNDARY);}
    @Inject(method="setDamage",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$setDamage(ItemStack blade,int damage,CallbackInfo ci) {
        if(!applies(blade))return;
        var state=BladeStateAccess.of(blade).orElseThrow();
        if(damage<=0 && !state.isSealed())state.setBroken(false);
        state.setDamage(Math.clamp(damage,0,Math.max(0,blade.getMaxDamage())));ci.cancel();
    }
    @Inject(method="damageItem",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$break(ItemStack blade,int amount,LivingEntity entity,Consumer<Item> onBroken,CallbackInfoReturnable<Integer> cir) {
        if(!applies(blade))return;
        cir.setReturnValue(0);
        int maximum=blade.getMaxDamage();if(maximum<=0 || amount<=0)return;
        var state=BladeStateAccess.of(blade).orElseThrow();
        if((long)blade.getDamageValue()+amount<=maximum){cir.setReturnValue(amount);return;}
        blade.setDamageValue(maximum);
        if(state.isBroken() || NeoForge.EVENT_BUS.post(new SlashBladeEvent.BreakEvent(blade,state)).isCanceled())return;
        state.setBroken(true);onBroken.accept(blade.getItem());
        if(entity instanceof ServerPlayer player)CriteriaTriggers.CONSUME_ITEM.trigger(player,blade);
        if(entity instanceof Player player)player.awardStat(Stats.ITEM_BROKEN.get(blade.getItem()));
        if(((ItemSlashBlade)(Object)this).isDestructable(blade))blade.shrink(1);
    }
}
