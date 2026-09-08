package org.scex.slashbladelegacy.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import org.scex.slashbladelegacy.*;

/** Original r87 slashdim.obj/png and procedural five-shell/five-wind animation. */
public final class LegacyArtRenderer extends EntityRenderer<LegacyArtEntity> {
    private static final ResourceLocation MODEL=ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"model/legacy17/slashdim.obj");
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"model/legacy17/slashdim.png");
    public LegacyArtRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(LegacyArtEntity entity){return TEXTURE;}
    @Override public void render(LegacyArtEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        if(entity.mode()!=LegacyArtEntity.Mode.DIMENSION)return;
        var model=BladeModelManager.getInstance().getModel(MODEL);int color=entity.getColor();
        float[] hsb=java.awt.Color.RGBtoHSB(color>>16&255,color>>8&255,color&255,null);
        int base=java.awt.Color.HSBtoRGB(hsb[0]+.5f,hsb[1],.2f)&0xffffff;
        double alpha=Math.sin(Math.PI*.5*Math.clamp((entity.lifetime()-entity.age()-partial)/entity.lifetime(),0,1));
        poses.pushPose();
        try {
            poses.scale(.01f,.01f,.01f);poses.pushPose();
            for(int i=0;i<5;i++){poses.scale(.95f,.95f,.95f);model.tessellateOnly(buffers.getBuffer(States.REVERSE),poses,15728880,base|((int)(0x66*alpha)<<24),"base");}
            poses.popPose();
            for(int i=0;i<3;i++) {
                float wave=(entity.age()+5*i+partial)%15,scale=1+.03f*wave;
                poses.pushPose();poses.scale(scale,scale,scale);
                model.tessellateOnly(buffers.getBuffer(States.REVERSE),poses,15728880,base|((int)(0x88*(15-wave)/15)<<24),"base");poses.popPose();
            }
            for(int i=0;i<5;i++) {
                double ticks=entity.age()+partial+entity.seed()+i*7,progress=ticks%28/28;
                // Preserve the original negative integer alpha wrap, not a newly clamped fade.
                int rgba=(color&0xffffff)|((int)Math.min(0,255*Math.sin(2*Math.PI*progress))<<24);
                float scale=(float)(.4+progress);poses.pushPose();poses.mulPose(Axis.XP.rotationDegrees(72*i));poses.mulPose(Axis.YP.rotationDegrees(30));
                poses.scale(scale,scale,scale);poses.mulPose(Axis.ZP.rotationDegrees((float)(18*ticks)));
                model.tessellateOnly(buffers.getBuffer(States.ADD),poses,15728880,rgba,"wind");poses.popPose();
            }
        }finally{poses.popPose();}
    }
    private static final class States extends RenderStateShard {
        private States(){super("legacy_dimension",()->{},()->{});}
        private static RenderType type(boolean reverse) {
            var transparency=new TransparencyStateShard("legacy_dimension_"+reverse,()->{
                RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
                RenderSystem.blendEquation(reverse?org.lwjgl.opengl.GL14.GL_FUNC_REVERSE_SUBTRACT:org.lwjgl.opengl.GL14.GL_FUNC_ADD);
            },()->{RenderSystem.blendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
            return RenderType.create("legacy_dimension_"+reverse,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.TRIANGLES,4096,false,false,
                    RenderType.CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER).setTextureState(new TextureStateShard(TEXTURE,false,false))
                            .setOutputState(MAIN_TARGET).setTransparencyState(transparency).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
        }
        private static final RenderType REVERSE=type(true),ADD=type(false);
    }
}
