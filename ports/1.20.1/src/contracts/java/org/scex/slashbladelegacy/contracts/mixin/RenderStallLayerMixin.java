package org.scex.slashbladelegacy.contracts.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade",remap=false)
public abstract class RenderStallLayerMixin {
    @WrapMethod(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
    private void probe$layer(PoseStack poses,MultiBufferSource buffers,int light,LivingEntity user,
                             float limb,float amount,float partial,float age,float yaw,float pitch,Operation<Void> original) {
        if(!RenderStallMetrics.active()){original.call(poses,buffers,light,user,limb,amount,partial,age,yaw,pitch);return;}
        RenderStallMetrics.add(RenderStallMetrics.LAYER_CALLS,1);long start=System.nanoTime();
        try {original.call(poses,buffers,light,user,limb,amount,partial,age,yaw,pitch);}
        finally {RenderStallMetrics.add(RenderStallMetrics.LAYER_NS,System.nanoTime()-start);}
    }
}
