package org.scex.slashbladelegacy.contracts.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;

/** Include client ticks between rendered frames. */
@Mixin(value=ComboState.TimeLineTickAction.class,remap=false)
public abstract class RenderStallTimelineMixin {
    @WrapMethod(method="accept(Lnet/minecraft/world/entity/LivingEntity;)V")
    private void probe$timeline(LivingEntity user,Operation<Void> original) {
        if(!RenderStallMetrics.stageActive()){original.call(user);return;}
        long start=System.nanoTime();
        try {original.call(user);}
        finally {RenderStallMetrics.timeline(System.nanoTime()-start);}
    }
}
