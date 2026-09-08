package org.scex.slashbladelegacy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.scex.slashbladelegacy.client.LegacyHeldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.model.BladeFirstPersonRender",remap=false)
public abstract class LegacyFirstPersonMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$eyeSpace(PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci) {
        var player=Minecraft.getInstance().player;
        if(player!=null && LegacyHeldRenderer.handles(player)) {
            LegacyHeldRenderer.firstPerson(poses,buffers,light);ci.cancel();
        }
    }
}
