package org.scex.slashbladelegacy.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the target's temporary eligibility inside one test call, including early returns and exceptions. */
@Mixin(targets="mods.flammpfeil.slashblade.util.TargetSelector$SlashBladeTargetingConditions",remap=false)
public abstract class RevengeContextMixin {
    @WrapMethod(method="test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z")
    private boolean legacyCompat$scopedRevenge(LivingEntity attacker,LivingEntity target,Operation<Boolean> original) {
        var previous=HostileTargeting.REVENGE_CONTEXT.get();HostileTargeting.REVENGE_CONTEXT.remove();
        try{return original.call(attacker,target);}
        finally{if(previous==null)HostileTargeting.REVENGE_CONTEXT.remove();else HostileTargeting.REVENGE_CONTEXT.set(previous);}
    }
    @Redirect(method="test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;addTag(Ljava/lang/String;)Z"))
    private boolean legacyCompat$noPersistentRevengeTag(LivingEntity target,String tag) {
        if(!LegacyCompat.HOSTILE_TARGETING.get())return target.addTag(tag);
        HostileTargeting.REVENGE_CONTEXT.set(target);return true;
    }
}
