package org.scex.slashbladelegacy.client;

import java.awt.Color;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;

/** Resolve the OBJ API once and keep old render state local to each draw. */
public final class BladeObjRender {
    @FunctionalInterface
    private interface Draw {
        void render(WavefrontObject model,VertexConsumer consumer,PoseStack poses,int light,int color,String[] parts) throws Throwable;
    }
    private static final Draw DRAW=resolve();
    private BladeObjRender() {}

    public static void tessellateOnly(WavefrontObject model,VertexConsumer consumer,PoseStack poses,int light,int color,String... parts) {
        try {
            DRAW.render(model,consumer,poses,light,color,parts);
        } catch(Throwable failure) {
            throw new IllegalStateException("Cannot render the SlashBlade OBJ model",failure);
        }
    }

    private static Draw resolve() {
        var lookup=MethodHandles.publicLookup();
        try {
            var method=lookup.findVirtual(WavefrontObject.class,"tessellateOnly",MethodType.methodType(
                    void.class,VertexConsumer.class,PoseStack.class,int.class,int.class,String[].class)).asFixedArity();
            return (model,consumer,poses,light,color,parts)->{
                method.invokeExact(model,consumer,poses,light,color,parts);
            };
        } catch(NoSuchMethodException oldApi) {
            return oldDraw();
        } catch(IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access the SlashBlade OBJ renderer",failure);
        }
    }

    private static Draw oldDraw() {
        var lookup=MethodHandles.publicLookup();
        try {
            MethodHandle method=lookup.findVirtual(WavefrontObject.class,"tessellateOnly",
                    MethodType.methodType(void.class,VertexConsumer.class,String[].class)).asFixedArity();
            MethodHandle getMatrix=lookup.findStaticGetter(Face.class,"matrix",PoseStack.class);
            MethodHandle setMatrix=lookup.findStaticSetter(Face.class,"matrix",PoseStack.class);
            MethodHandle getColor=lookup.findStaticGetter(Face.class,"col",Color.class);
            MethodHandle setColor=lookup.findStaticSetter(Face.class,"col",Color.class);
            MethodHandle getLight=lookup.findStaticGetter(Face.class,"lightmap",int.class);
            MethodHandle setLight=lookup.findStaticSetter(Face.class,"lightmap",int.class);
            return (model,consumer,poses,light,color,parts)->{
                PoseStack previousMatrix=(PoseStack)getMatrix.invokeExact();
                Color previousColor=(Color)getColor.invokeExact();
                int previousLight=(int)getLight.invokeExact();
                try {
                    setMatrix.invokeExact(poses);
                    setColor.invokeExact(new Color(color,true));
                    setLight.invokeExact(light);
                    method.invokeExact(model,consumer,parts);
                } finally {
                    setMatrix.invokeExact(previousMatrix);
                    setColor.invokeExact(previousColor);
                    setLight.invokeExact(previousLight);
                }
            };
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("SlashBlade does not provide a supported OBJ renderer",failure);
        }
    }
}
