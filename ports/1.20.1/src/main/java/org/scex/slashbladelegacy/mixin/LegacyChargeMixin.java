package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import org.scex.slashbladelegacy.BladeStateAccess;
import org.scex.slashbladelegacy.LegacyAdditionalAttack;
import org.scex.slashbladelegacy.LegacyCompat;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;

/** 具体状态类接管默认方法。现代模式调用原接口实现。 */
@Mixin(value=SlashBladeState.class,remap=false)
public abstract class LegacyChargeMixin implements ISlashBladeState {
    @Override
    public java.util.Map.Entry<Integer,ResourceLocation> resolvCurrentComboStateTicks(LivingEntity user) {
        if(!LegacyMode.legacy(user))return ISlashBladeState.super.resolvCurrentComboStateTicks(user);
        var registry=ComboStateRegistry.REGISTRY.get();
        var none=ComboStateRegistry.NONE.getId();
        var current=registry.containsKey(getComboSeq())?getComboSeq():none;
        var combo=registry.getValue(current);
        int time=(int)mods.flammpfeil.slashblade.util.TimeValueHelper.getMSecFromTicks(getElapsedTime(user));
        while(combo!=null && !current.equals(none) && combo.getTimeoutMS()<time) {
            time-=combo.getTimeoutMS();
            current=org.scex.slashbladelegacy.BladeEvents.timeout(user.getMainHandItem(),this,user,combo.getNextOfTimeout(user));
            updateComboSeq(user,current);
            // Upstream 1.9.65 keeps the first combo here, so a full timeout can repeat Noutou.
            combo=registry.getValue(current);
        }
        return java.util.Map.entry((int)mods.flammpfeil.slashblade.util.TimeValueHelper.getTicksFromMSec(time),current);
    }

    @Override
    public void updateComboSeq(LivingEntity user,ResourceLocation combo) {
        ISlashBladeState.super.updateComboSeq(user,combo);
        // 连段回到 NONE 时清除蓄力，避免后续连段再次发出剑气。
        if(LegacyMode.legacy(user) && getComboSeq().equals(ComboStateRegistry.NONE.getId())
                && BladeStateAccess.of(user.getMainHandItem())
                    .map(state->state.getComboSeq().equals(ComboStateRegistry.NONE.getId())).orElse(false))
            LegacyAdditionalAttack.clearCharged(user.getMainHandItem());
    }

    @Override
    public ResourceLocation doChargeAction(LivingEntity user,int elapsed) {
        if(LegacyMode.enabled(user,LegacyCompat.LEGACY_CHARGE) && elapsed<=15)
            return ComboStateRegistry.NONE.getId();
        var result=ISlashBladeState.super.doChargeAction(user,elapsed);
        LegacyAdditionalAttack.markCharged(user,result);
        return result;
    }

    @Override
    public int getFullChargeTicks(LivingEntity user) {
        int ticks=ISlashBladeState.super.getFullChargeTicks(user);
        return LegacyMode.enabled(user,LegacyCompat.LEGACY_CHARGE) && ticks==9?15:ticks;
    }
}
