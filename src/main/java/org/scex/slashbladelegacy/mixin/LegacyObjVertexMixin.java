package org.scex.slashbladelegacy.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Face.class,remap=false)
public abstract class LegacyObjVertexMixin {
    @Shadow public Vertex[] vertices;
    @Shadow public Vertex[] vertexNormals;
    @Shadow public Vertex faceNormal;
    @Shadow public TextureCoordinate[] textureCoordinates;
    @Shadow public boolean[] texUSign;
    @Shadow public boolean[] texVSign;
    @Shadow @Final private static ThreadLocal<Vector3f> NORMAL_TMP;
    @Shadow public abstract Vertex calculateFaceNormal();

    @Inject(method="addFaceForRender(Lcom/mojang/blaze3d/vertex/VertexConsumer;FLorg/joml/Matrix4f;Lorg/joml/Matrix3f;II)V",
            at=@At("HEAD"),cancellable=true)
    private void legacyCompat$writeVertices(VertexConsumer consumer,float textureOffset,Matrix4f pose,
                                           Matrix3f normalTransform,int light,int color,CallbackInfo ci) {
        // Keep custom Face and consumer method dispatch.
        if(((Object)this).getClass()!=Face.class || consumer.getClass()!=BufferBuilder.class)return;
        legacyCompat$writeBulk(consumer,textureOffset,pose,normalTransform,light,color);
        ci.cancel();
    }

    @Unique
    private void legacyCompat$writeBulk(VertexConsumer consumer,float textureOffset,Matrix4f pose,
                                       Matrix3f normalTransform,int light,int color) {
        if(faceNormal==null)faceNormal=calculateFaceNormal();

        var temporary=NORMAL_TMP.get();
        var uv=Face.uvOperator;
        boolean hideAtZeroY=Face.alphaOverride.equals(Face.alphaOverrideYZZ);
        boolean textured=textureCoordinates!=null && textureCoordinates.length>0;
        for(int i=0;i<vertices.length;i++){
            var vertex=vertices[i];
            pose.transformPosition(vertex.x,vertex.y,vertex.z,temporary);
            // Save position values before reusing the vector for the normal.
            float x=temporary.x(),y=temporary.y(),z=temporary.z();
            var normal=vertexNormals==null?faceNormal:vertexNormals[i];
            temporary.set(normal.x,normal.y,normal.z).mul(normalTransform).normalize();

            float u=0,v=0;
            if(textured){
                var coordinates=textureCoordinates[i];
                float offsetU=texUSign!=null && texUSign[i]?-textureOffset:textureOffset;
                float offsetV=texVSign!=null && texVSign[i]?-textureOffset:textureOffset;
                u=coordinates.u*uv.x()+uv.z()+offsetU;
                v=coordinates.v*uv.y()+uv.w()+offsetV;
            }
            int tint=hideAtZeroY && vertex.y==0?color&0x00FFFFFF:color;
            consumer.addVertex(x,y,z,tint,u,v,OverlayTexture.NO_OVERLAY,light,
                    temporary.x(),temporary.y(),temporary.z());
        }
    }
}
