package org.scex.slashbladelegacy.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Original additive and inverse-color blend functions with scoped modern render state. */
final class LegacyPhantomRenderState extends RenderStateShard {
    private LegacyPhantomRenderState() {super("legacy_phantom",()->{},()->{});}
    private static final TransparencyStateShard ADD=new TransparencyStateShard("legacy_phantom_add",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    private static final TransparencyStateShard INVERSE=new TransparencyStateShard("legacy_phantom_inverse",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,GlStateManager.DestFactor.ZERO);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    static final RenderType NORMAL=type(false),REVERSE=type(true);
    private static RenderType type(boolean inverse) {
        return RenderType.create("legacy_phantom_"+(inverse?"inverse":"add"),DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.TRIANGLES,1536,false,false,
                RenderType.CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER).setOutputState(MAIN_TARGET)
                        .setTransparencyState(inverse?INVERSE:ADD).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
    }
}
