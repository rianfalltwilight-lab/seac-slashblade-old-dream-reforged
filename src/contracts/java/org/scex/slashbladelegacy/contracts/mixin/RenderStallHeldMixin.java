package org.scex.slashbladelegacy.contracts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.client.LegacyHeldRenderer;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=LegacyHeldRenderer.class,remap=false)
public abstract class RenderStallHeldMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;peekCurrentComboStateTicks(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Map$Entry;"))
    private static Map.Entry<Integer,ResourceLocation> probe$peek(ISlashBladeState state,LivingEntity user,Operation<Map.Entry<Integer,ResourceLocation>> original) {
        if(!RenderStallMetrics.active())return original.call(state,user);
        RenderStallMetrics.add(RenderStallMetrics.PEEK_CALLS,1);long start=System.nanoTime();
        try {return original.call(state,user);}
        finally {RenderStallMetrics.add(RenderStallMetrics.PEEK_NS,System.nanoTime()-start);}
    }
}
