package org.scex.slashbladelegacy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade",remap=false)
public abstract class LegacyBladePoseMixin {
    @Shadow public abstract void renderOffhandItem(PoseStack poses,MultiBufferSource buffers,int light,LivingEntity entity);
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$originalBladeTransforms(PoseStack poses,MultiBufferSource buffers,int light,LivingEntity entity,
                    float limbSwing,float limbAmount,float partial,float age,float yaw,float pitch,CallbackInfo ci) {
        if(!LegacyCompat.LEGACY_COMBAT.get())return;
        var blade=entity.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null || !state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId()))return;
        if(!state.getCarryType().name().equals("DEFAULT") && !state.getCarryType().name().equals("KATANA"))return;
        var current=state.peekCurrentComboStateTicks(entity);
        var move=LegacyCombat.move(current.getValue());
        // Keep independent SA/addon animation states, rather than interpreting them as legacy idle.
        if(move==LegacyMove.NONE && !current.getValue().equals(ComboStateRegistry.NONE.getId()))return;
        if(entity.getType().is(mods.flammpfeil.slashblade.data.tag.SlashBladeEntityTypeTagProvider.EntityTypeTags.RENDER_LAYER_BLACKLIST))return;
        float duration=6;
        if(net.minecraft.world.effect.MobEffectUtil.hasDigSpeed(entity))duration-=1+net.minecraft.world.effect.MobEffectUtil.getDigSpeedAmplification(entity);
        else if(entity.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN))duration+=(1+entity.getEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN).getAmplifier())*2;
        float progress=LegacyBladePose.progress(move,(current.getKey()+partial)/Math.max(1,duration));
        var model=BladeModelManager.getInstance().getModel(state.getModel().orElse(DefaultResources.resourceDefaultModel));
        var texture=state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
        renderOffhandItem(poses,buffers,light,entity);
        for(boolean sheath:new boolean[]{false,true}) {
            poses.pushPose();
            try {
                poses.mulPose(LegacyBladePose.matrix(move,progress,sheath));
                String part=sheath?"sheath":state.isBroken()?"blade_damaged":"blade";
                BladeRenderState.renderOverrided(blade,model,part,texture,poses,buffers,light);
                BladeRenderState.renderOverridedLuminous(blade,model,part+"_luminous",texture,poses,buffers,light);
                if(!sheath && !move.scabbard) {
                    BladeRenderState.renderOverrided(blade,model,part+"_unsheathe",texture,poses,buffers,light);
                    BladeRenderState.renderOverridedLuminous(blade,model,part+"_unsheathe_luminous",texture,poses,buffers,light);
                }
            }finally{poses.popPose();}
        }
        ci.cancel();
    }
}
