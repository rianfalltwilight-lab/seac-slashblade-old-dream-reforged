package org.scex.slashbladelegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.scex.slashbladelegacy.LegacyCompat;
import org.scex.slashbladelegacy.LegacySummonedBlade;
import org.scex.slashbladelegacy.SummonedBladeMode;

/** Mesh coordinates are generated verbatim from the original Furia renderer, not invented. */
@EventBusSubscriber(modid=LegacyCompat.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LegacyBladeRenderer extends EntityRenderer<LegacySummonedBlade> {
    public LegacyBladeRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SummonedBladeMode.BLADE.get(),LegacyBladeRenderer::new);
        event.registerEntityRenderer(SummonedBladeMode.DRIVE.get(),mods.flammpfeil.slashblade.client.renderer.entity.DriveRenderer::new);
        event.registerEntityRenderer(SummonedBladeMode.UPTHRUST.get(),mods.flammpfeil.slashblade.client.renderer.entity.SummonedSwordRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(LegacySummonedBlade entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png"); // untextured position/color render type
    }
    @Override public void render(LegacySummonedBlade entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        poses.pushPose();
        poses.translate(0,0.5,0);
        poses.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
        poses.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));
        poses.mulPose(Axis.ZP.rotationDegrees(entity.getRoll()));
        float spin=entity.frozenSpin()>=0?entity.frozenSpin():(entity.level().getGameTime()%6+partial)*60;
        poses.mulPose(Axis.YP.rotationDegrees(spin));
        poses.scale(0.01f,0.01f,0.01f);
        int color=Math.abs(entity.getColor()),r=color>>16&255,g=color>>8&255,b=color&255;
        var out=buffers.getBuffer(RenderType.lightning());
        for(int[] triangle:LegacyBladeMesh.FACES) {
            for(int index:new int[]{triangle[0],triangle[1],triangle[2],triangle[2]}) {
                double[] point=LegacyBladeMesh.VERTICES[index];
                out.addVertex(poses.last().pose(),(float)point[0],(float)point[1],(float)point[2]).setColor(r,g,b,255);
            }
        }
        poses.popPose();
        super.render(entity,yaw,partial,poses,buffers,light);
    }
}
