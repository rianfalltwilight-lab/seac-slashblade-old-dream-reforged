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
    @Shadow public abstract Vertex calculateFaceNormal();
    @Unique private static final ThreadLocal<Vector3f> legacyCompat$vector=ThreadLocal.withInitial(Vector3f::new);
    @Unique private static final ThreadLocal<Matrix3f> legacyCompat$normalMatrix=ThreadLocal.withInitial(Matrix3f::new);

    @Inject(method="addFaceForRender(Lcom/mojang/blaze3d/vertex/VertexConsumer;FLorg/joml/Matrix4f;II)V",
            at=@At("HEAD"),cancellable=true)
    private void legacyCompat$writeVertices(VertexConsumer consumer,float textureOffset,Matrix4f pose,int light,int color,CallbackInfo ci) {
        if(!legacyCompat$canWrite(consumer))return;
        legacyCompat$writeBulk(consumer,textureOffset,pose,light,color);
        ci.cancel();
    }

    @Unique
    private boolean legacyCompat$canWrite(VertexConsumer consumer) {
        // Keep custom faces, consumers and alpha callbacks on the upstream path.
        return ((Object)this).getClass()==Face.class && consumer.getClass()==BufferBuilder.class
                && (Face.alphaOverride==Face.alphaNoOverride || Face.alphaOverride==Face.alphaOverrideYZZ);
    }

    @Unique
    private void legacyCompat$writeBulk(VertexConsumer consumer,float textureOffset,Matrix4f pose,int light,int color) {
        if(faceNormal==null)faceNormal=calculateFaceNormal();

        var temporary=legacyCompat$vector.get();
        var normalTransform=legacyCompat$normalMatrix.get().set(pose);
        var uv=Face.uvOperator;
        boolean hideAtZeroY=Face.alphaOverride==Face.alphaOverrideYZZ;
        boolean textured=textureCoordinates!=null && textureCoordinates.length>0;
        float averageU=0,averageV=0;
        if(textured) {
            for(var coordinates:textureCoordinates) {
                averageU+=coordinates.u*uv.x()+uv.z();
                averageV+=coordinates.v*uv.y()+uv.w();
            }
            averageU/=textureCoordinates.length;averageV/=textureCoordinates.length;
        }
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
                u=coordinates.u*uv.x()+uv.z();v=coordinates.v*uv.y()+uv.w();
                u+=u>averageU?-textureOffset:textureOffset;
                v+=v>averageV?-textureOffset:textureOffset;
            }
            int alpha=hideAtZeroY && vertex.y==0?0:color>>>24;
            consumer.vertex(x,y,z).color(color>>16&255,color>>8&255,color&255,alpha)
                    .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(temporary.x(),temporary.y(),temporary.z()).endVertex();
        }
    }
}
