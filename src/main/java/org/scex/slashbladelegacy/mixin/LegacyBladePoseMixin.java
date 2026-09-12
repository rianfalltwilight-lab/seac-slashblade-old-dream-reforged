package org.scex.slashbladelegacy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.client.LegacyHeldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade",remap=false)
public abstract class LegacyBladePoseMixin {
    @Shadow public abstract void renderOffhandItem(PoseStack poses,MultiBufferSource buffers,int light,LivingEntity entity);
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$originalPose(PoseStack poses,MultiBufferSource buffers,int light,LivingEntity entity,
                                          float limb,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci) {
        if(!LegacyHeldRenderer.handles(entity))return;
        // This framework carry renderer is static and does not consume VMD animation.
        if(!org.scex.slashbladelegacy.LegacyDualWield.hasOffhandBlade(entity))renderOffhandItem(poses,buffers,light,entity);
        LegacyHeldRenderer.render(poses,buffers,light,entity,partial,true);
        ci.cancel();
    }
}
