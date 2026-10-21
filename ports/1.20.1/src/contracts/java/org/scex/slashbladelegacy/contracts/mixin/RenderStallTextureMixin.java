package org.scex.slashbladelegacy.contracts.mixin;

import net.minecraft.client.renderer.texture.AbstractTexture;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractTexture.class)
public abstract class RenderStallTextureMixin {
    @Shadow protected boolean blur;
    @Shadow protected boolean mipmap;
    @Unique private long probe$filterStart,probe$cpuStart;
    @Inject(method="setFilter",at=@At("HEAD"))
    private void probe$begin(boolean nextBlur,boolean nextMipmap,CallbackInfo ci) {
        probe$filterStart=0;
        if(!RenderStallMetrics.active())return;
        RenderStallMetrics.add(RenderStallMetrics.FILTER_CALLS,1);
        RenderStallMetrics.add(blur==nextBlur && mipmap==nextMipmap?RenderStallMetrics.FILTER_SAME:RenderStallMetrics.FILTER_CHANGED,1);
        probe$cpuStart=RenderStallMetrics.cpuTime();probe$filterStart=System.nanoTime();
    }
    @Inject(method="setFilter",at=@At("RETURN"))
    private void probe$end(CallbackInfo ci) {
        if(probe$filterStart==0)return;
        RenderStallMetrics.add(RenderStallMetrics.FILTER_NS,System.nanoTime()-probe$filterStart);
        if(probe$cpuStart>=0)RenderStallMetrics.add(RenderStallMetrics.FILTER_CPU_NS,RenderStallMetrics.cpuTime()-probe$cpuStart);
        probe$filterStart=0;
    }
}
