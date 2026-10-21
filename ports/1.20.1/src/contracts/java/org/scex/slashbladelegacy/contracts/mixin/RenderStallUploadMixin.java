package org.scex.slashbladelegacy.contracts.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleTexture.class)
public abstract class RenderStallUploadMixin {
    @Unique private long probe$uploadStart;
    @Inject(method="doLoad",at=@At("HEAD"))
    private void probe$begin(NativeImage image,boolean blur,boolean clamp,CallbackInfo ci) {
        probe$uploadStart=0;
        if(!RenderStallMetrics.active())return;
        RenderStallMetrics.add(RenderStallMetrics.UPLOADS,1);
        RenderStallMetrics.add(RenderStallMetrics.UPLOAD_PIXELS,(long)image.getWidth()*image.getHeight());
        probe$uploadStart=System.nanoTime();
    }
    @Inject(method="doLoad",at=@At("RETURN"))
    private void probe$end(CallbackInfo ci) {
        if(probe$uploadStart==0)return;
        RenderStallMetrics.add(RenderStallMetrics.UPLOAD_NS,System.nanoTime()-probe$uploadStart);probe$uploadStart=0;
    }
}
