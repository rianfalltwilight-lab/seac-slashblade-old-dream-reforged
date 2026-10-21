package org.scex.slashbladelegacy.contracts.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.scex.slashbladelegacy.contracts.RenderStallMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=WavefrontObject.class,remap=false)
public abstract class RenderStallObjMixin {
    @Shadow public ArrayList<GroupObject> groupObjects;
    @Unique private long probe$parseStart,probe$geometryStart;
    @Unique private long[] probe$faces,probe$vertices;
    @Inject(method="loadObjModel",at=@At("HEAD"))
    private void probe$parseBegin(CallbackInfo ci) {
        probe$parseStart=0;
        if(!RenderStallMetrics.active())return;
        RenderStallMetrics.add(RenderStallMetrics.OBJ_PARSES,1);probe$parseStart=System.nanoTime();
    }
    @Inject(method="loadObjModel",at=@At("RETURN"))
    private void probe$parseEnd(CallbackInfo ci) {
        if(probe$parseStart==0)return;
        RenderStallMetrics.add(RenderStallMetrics.OBJ_NS,System.nanoTime()-probe$parseStart);probe$parseStart=0;
    }
    @Inject(method="tessellateOnly(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;II[Ljava/lang/String;)V",at=@At("HEAD"),require=0)
    private void probe$geometryBegin(VertexConsumer consumer,PoseStack poses,int light,int color,String[] names,CallbackInfo ci) {
        probe$beginGeometry(names);
    }
    @Inject(method="tessellateOnly(Lcom/mojang/blaze3d/vertex/VertexConsumer;[Ljava/lang/String;)V",at=@At("HEAD"),require=0)
    private void probe$geometryBeginOld(VertexConsumer consumer,String[] names,CallbackInfo ci) {
        probe$beginGeometry(names);
    }
    @Unique private void probe$beginGeometry(String[] names) {
        probe$geometryStart=0;
        if(!RenderStallMetrics.active())return;
        probe$countGeometry(names);
        RenderStallMetrics.add(RenderStallMetrics.TESSELLATE_CALLS,1);probe$geometryStart=System.nanoTime();
    }
    @Inject(method="tessellateOnly",at=@At("RETURN"))
    private void probe$geometryEnd(CallbackInfo ci) {
        if(probe$geometryStart==0)return;
        RenderStallMetrics.add(RenderStallMetrics.TESSELLATE_NS,System.nanoTime()-probe$geometryStart);probe$geometryStart=0;
    }
    @Unique private void probe$countGeometry(String[] names) {
        if(probe$faces==null) {
            // Read each group's topology once. Do not add a hook to each face or vertex.
            probe$faces=new long[groupObjects.size()];probe$vertices=new long[groupObjects.size()];
            for(int i=0;i<groupObjects.size();i++) {
                var faces=groupObjects.get(i).faces;probe$faces[i]=faces.size();
                for(int j=0;j<faces.size();j++)probe$vertices[i]+=faces.get(j).vertices.length;
            }
        }
        for(String name:names) {
            // This version draws every matching group in list order.
            for(int i=0;i<groupObjects.size();i++)if(name.equalsIgnoreCase(groupObjects.get(i).name)) {
                RenderStallMetrics.add(RenderStallMetrics.FACES,probe$faces[i]);
                RenderStallMetrics.add(RenderStallMetrics.VERTICES,probe$vertices[i]);
            }
        }
    }
}
