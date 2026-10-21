package org.scex.slashbladelegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class LegacyPhantomRenderer<T extends mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword> extends EntityRenderer<T> {
    private static final ResourceLocation TEXTURE=ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    public LegacyPhantomRenderer(EntityRendererProvider.Context context) {super(context);}
    @Override public ResourceLocation getTextureLocation(T entity) {return TEXTURE;}
    @Override public void render(T entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial,entity.yRotO,entity.getYRot())));
        poses.mulPose(Axis.XP.rotationDegrees(-Mth.rotLerp(partial,entity.xRotO,entity.getXRot())));
        poses.mulPose(Axis.ZP.rotationDegrees(entity.getRoll()));poses.scale(.00225f,.00225f,.0045f);
        int color=Math.abs(entity.getColor()),r=color>>16&255,g=color>>8&255,b=color&255;
        var out=buffers.getBuffer(entity.getColor()<0?LegacyPhantomRenderState.REVERSE:LegacyPhantomRenderState.NORMAL);
        var matrix=poses.last().pose();
        for(int[] face:LegacyPhantomMesh.FACES)for(int index:face) {
            double[] p=LegacyPhantomMesh.VERTICES[index];out.addVertex(matrix,(float)p[0],(float)p[1],(float)p[2]).setColor(r,g,b,255);
        }
        poses.popPose();super.render(entity,yaw,partial,poses,buffers,light);
    }
}
