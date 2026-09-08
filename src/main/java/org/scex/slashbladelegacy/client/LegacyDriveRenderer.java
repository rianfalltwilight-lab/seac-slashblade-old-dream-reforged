package org.scex.slashbladelegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.scex.slashbladelegacy.LegacyDrive;

/** Exact r87 Drive shape, axes, blue tint and quadratic fade. */
public final class LegacyDriveRenderer extends EntityRenderer<LegacyDrive> {
    private static final int[] TRIANGLES={0,1,2,0,2,3};
    public LegacyDriveRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(LegacyDrive entity){return ResourceLocation.withDefaultNamespace("textures/misc/white.png");}
    @Override public void render(LegacyDrive entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));poses.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));
        poses.mulPose(Axis.ZP.rotationDegrees(entity.getRotationRoll()));poses.scale(.25f,1,1);
        float remaining=1-Math.min(entity.getLifetime(),entity.age())/Math.max(1,entity.getLifetime());
        int alpha=Math.round(remaining*remaining*255);var out=buffers.getBuffer(LegacyPhantomRenderState.NORMAL);
        for(int[] face:LegacyDriveMesh.FACES)for(int corner:TRIANGLES){double[] p=LegacyDriveMesh.VERTICES[face[corner]];out.addVertex(poses.last().pose(),(float)p[0],(float)p[1],(float)p[2]).setColor(51,51,255,alpha);}
        poses.popPose();super.render(entity,yaw,partial,poses,buffers,light);
    }
}
