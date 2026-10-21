package org.scex.slashbladelegacy.contracts.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade",
        "mods.flammpfeil.slashblade.client.renderer.layers.LayerSlashBlade"},remap=false)
public abstract class RenderStallLayerMixin {
    @WrapMethod(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
    private void probe$layer(PoseStack poses,MultiBufferSource buffers,int light,LivingEntity user,
                             float limb,float amount,float partial,float age,float yaw,float pitch,Operation<Void> original) {
        if(!RenderStallMetrics.active()){original.call(poses,buffers,light,user,limb,amount,partial,age,yaw,pitch);return;}
        RenderStallMetrics.add(RenderStallMetrics.LAYER_CALLS,1);long start=System.nanoTime();
        try {original.call(poses,buffers,light,user,limb,amount,partial,age,yaw,pitch);}
        finally {RenderStallMetrics.add(RenderStallMetrics.LAYER_NS,System.nanoTime()-start);}
    }
    @WrapOperation(method="lambda$render$1",
            at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;peekCurrentComboStateTicks(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Map$Entry;"))
    private Map.Entry<Integer,ResourceLocation> probe$peek(ISlashBladeState state,LivingEntity user,Operation<Map.Entry<Integer,ResourceLocation>> original) {
        if(!RenderStallMetrics.active())return original.call(state,user);
        RenderStallMetrics.add(RenderStallMetrics.PEEK_CALLS,1);long start=System.nanoTime();
        try {return original.call(state,user);}
        finally {RenderStallMetrics.add(RenderStallMetrics.PEEK_NS,System.nanoTime()-start);}
    }
}
