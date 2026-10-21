package org.scex.slashbladelegacy.contracts;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.scex.slashbladelegacy.LegacyDrive;
import org.scex.slashbladelegacy.SummonedBladeMode;

/** Draw the production Drive mesh in a real framebuffer. Compare both draw orders. */
public final class DriveRenderProbe {
    private static final int SIZE=256;
    private static final List<String> IMAGES=List.of("drive-00-target-reference.png","drive-01-zero-drive-first.png",
            "drive-02-zero-old-depth.png","drive-03-fade-drive-first.png","drive-04-fade-target-first.png",
            "drive-05-fade-old-depth.png","drive-06-wall-reference.png","drive-07-wall-before-drive.png");

    private DriveRenderProbe() {}

    public static List<String> run(Path output)throws Exception {
        RenderSystem.assertOnRenderThread();
        var mc=Minecraft.getInstance();
        if(mc.level==null)throw new IllegalStateException("Drive probe needs a client world");
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting=RenderSystem.getVertexSorting();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        float[] color=RenderSystem.getShaderColor().clone();
        var shader=RenderSystem.getShader();
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        var target=new TextureTarget(SIZE,SIZE,true,Minecraft.ON_OSX);
        target.setClearColor(.05f,.05f,.05f,1);
        var rows=new ArrayList<Map<String,Object>>();
        var checks=new LinkedHashMap<String,Boolean>();
        RenderSystem.getModelViewStack().pushMatrix();
        try(var memory=new ByteBufferBuilder(4096)) {
            RenderSystem.getModelViewStack().identity();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-.7f,.7f,-1.2f,1.2f,.1f,10),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderFogStart(100);RenderSystem.setShaderFogEnd(200);
            var buffers=MultiBufferSource.immediate(memory);
            var drive=new LegacyDrive(SummonedBladeMode.DRIVE.get(),mc.level);
            // This unchanged phantom channel is the exact channel that Drive used before the fix.
            var field=Class.forName("org.scex.slashbladelegacy.client.LegacyPhantomRenderState").getDeclaredField("NORMAL");
            field.setAccessible(true);var oldDepth=(RenderType)field.get(null);
            int[][] pixels=new int[IMAGES.size()][];
            for(int test=0;test<IMAGES.size();test++) {
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                boolean wall=test>=6,reference=test==0 || test==6,targetFirst=test==4 || test==7;
                boolean oldChannel=test==2 || test==5;
                int age=test==1 || test==2?100:75;
                String[] channel={"none"};
                boolean restored=true;
                if(targetFirst || reference)plane(buffers,wall?-1:-5);
                if(!reference) {
                    var tag=new CompoundTag();drive.addAdditionalSaveData(tag);tag.putInt("LegacyAge",age);drive.readAdditionalSaveData(tag);
                    drive.setLifetime(100);drive.setColor(0x3333FF);drive.setRotationRoll(0);drive.setYRot(65);drive.setXRot(0);
                    var poses=new PoseStack();poses.translate(0,0,-3);
                    MultiBufferSource routed=type->{channel[0]=type.toString();return buffers.getBuffer(oldChannel?oldDepth:type);};
                    mc.getEntityRenderDispatcher().getRenderer(drive).render(drive,65,0,poses,routed,0xF000F0);
                    buffers.endBatch();
                    restored=restored(target);
                }
                if(!targetFirst && !reference)plane(buffers,-5);
                checks.put(IMAGES.get(test)+"/state_restored",restored && restored(target));
                pixels[test]=capture(target,output.resolve(IMAGES.get(test)));
                rows.add(Map.of("image",IMAGES.get(test),"age",age,"lifetime",100,"alpha",reference?0:Math.round((1-age/100f)*(1-age/100f)*255),
                        "old_depth_control",oldChannel,"target_first",targetFirst,"wall",wall,"production_channel",channel[0]));
            }
            int zeroOld=changed(pixels[0],pixels[2]),fadeOld=changed(pixels[0],pixels[5]),visible=changed(pixels[0],pixels[4]);
            checks.put("zero_alpha_does_not_hide_later_target",Arrays.equals(pixels[0],pixels[1]));
            checks.put("fading_drive_does_not_hide_later_target",Arrays.equals(pixels[0],pixels[3]));
            checks.put("fading_drive_is_visible_over_earlier_target",visible>20);
            checks.put("opaque_wall_blocks_drive",Arrays.equals(pixels[6],pixels[7]));
            checks.put("old_channel_reproduces_zero_alpha_occlusion",zeroOld>20);
            checks.put("old_channel_reproduces_fading_occlusion",fadeOld>20);
            boolean passed=checks.values().stream().allMatch(Boolean::booleanValue);
            var report=new LinkedHashMap<String,Object>();report.put("passed",passed);report.put("checks",checks);report.put("cases",rows);
            report.put("changed_pixels",Map.of("zero_old_depth",zeroOld,"fade_old_depth",fadeOld,"visible_fade",visible));
            report.put("renderer",com.mojang.blaze3d.platform.GlUtil.getRenderer());
            report.put("scope","Production Drive renderer and mesh; real 256x256 GPU framebuffer. The retained phantom channel reproduces the old Drive depth rule. These are deterministic draw-order fixtures, not gameplay or shader-pack acceptance.");
            Files.writeString(output.resolve("drive-render-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if(!passed)throw new IllegalStateException("Drive framebuffer checks failed: "+checks);
            var captures=new ArrayList<>(IMAGES);captures.addAll(trailColors(output,target,buffers));return captures;
        } finally {
            target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);RenderSystem.setShader(()->shader);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        }
    }

    private static void plane(MultiBufferSource.BufferSource buffers,float z) {
        var out=buffers.getBuffer(RenderType.debugQuads());
        out.addVertex(-.7f,-1.2f,z).setColor(40,180,60,255);out.addVertex(.7f,-1.2f,z).setColor(40,180,60,255);
        out.addVertex(.7f,1.2f,z).setColor(40,180,60,255);out.addVertex(-.7f,1.2f,z).setColor(40,180,60,255);
        buffers.endBatch();
    }

    private static int changed(int[] first,int[] second) {
        int count=0;for(int i=0;i<first.length;i++)if(first[i]!=second[i])count++;return count;
    }

    private static List<String> trailColors(Path output,TextureTarget target,MultiBufferSource.BufferSource buffers)throws Exception {
        var names=List.of("trail-00-blue.png","trail-01-red.png","trail-02-green.png","trail-03-white.png","trail-04-blue-old-decode.png");
        int[] colors={0x3333FF,0xFF3333,0x33FF33,0xFFFFFF};
        var method=Class.forName("org.scex.slashbladelegacy.client.LegacyHeldRenderer").getDeclaredMethod("trail",
                ItemStack.class,PoseStack.class,MultiBufferSource.class,int.class,boolean.class,int.class,float.class,float.class,boolean.class);
        method.setAccessible(true);
        var blade=new ItemStack(SlashBladeItems.SLASHBLADE.get());var state=BladeStateAccess.of(blade).orElseThrow();
        var checks=new LinkedHashMap<String,Boolean>();var rows=new ArrayList<Map<String,Object>>();
        int[] blue=null,oldBlue=null;
        for(int i=0;i<colors.length;i++) {
            state.setColorCode(colors[i]);state.setEffectColorInverse(false);int argb=state.getColorCode();
            int[][] images=new int[3][];
            for(int pass=0;pass<3;pass++) {
                if(pass==2)state.setEffectColorInverse(true);
                int input=pass==1?colors[i]:state.getColorCode();
                target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var poses=new PoseStack();poses.translate(.65,1.2,-3);poses.scale(.004f,.004f,.004f);
                method.invoke(null,blade,poses,buffers,0xF000F0,false,input,.5f,1f,false);buffers.endBatch();
                images[pass]=capture(target,pass==0?output.resolve(names.get(i)):null);
                checks.put(names.get(i)+"/state_restored_"+pass,restored(target));
            }
            checks.put(names.get(i)+"/argb_is_negative",argb<0);
            checks.put(names.get(i)+"/argb_equals_positive_rgb",Arrays.equals(images[0],images[1]));
            // r87 uses the same two-pass trail for normal and inverse phantom colors.
            checks.put(names.get(i)+"/inverse_flag_keeps_trail_rgb",Arrays.equals(images[0],images[2]));
            rows.add(Map.of("image",names.get(i),"state_argb",String.format("%08X",argb),"expected_rgb",String.format("%06X",colors[i]),
                    "rgb_difference_pixels",changed(images[0],images[1]),"inverse_difference_pixels",changed(images[0],images[2])));
            if(i==0)blue=images[0];
        }
        state.setColorCode(colors[0]);state.setEffectColorInverse(false);
        int oldDecode=Math.abs(state.getColorCode())&0xFFFFFF;
        target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        var poses=new PoseStack();poses.translate(.65,1.2,-3);poses.scale(.004f,.004f,.004f);
        method.invoke(null,blade,poses,buffers,0xF000F0,false,oldDecode,.5f,1f,false);buffers.endBatch();
        oldBlue=capture(target,output.resolve(names.get(4)));
        int difference=changed(blue,oldBlue),bluePixels=0;
        for(int pixel:blue) {
            int red=pixel&255,green=pixel>>8&255,blueChannel=pixel>>16&255;
            if(blueChannel>red+20 && blueChannel>green+20)bluePixels++;
        }
        checks.put("old_abs_decode_changes_blue",difference>20);checks.put("blue_trail_has_blue_pixels",bluePixels>20);
        boolean passed=checks.values().stream().allMatch(Boolean::booleanValue);
        var report=new LinkedHashMap<String,Object>();report.put("passed",passed);report.put("checks",checks);report.put("cases",rows);
        report.put("old_decode_control",Map.of("image",names.get(4),"rgb",String.format("%06X",oldDecode),"changed_pixels",difference));
        report.put("blue_pixels",bluePixels);report.put("scope","Actual blade state ARGB and production held trail method, model, texture and two-pass blending. Positive RGB reference and inverse-flag controls use the same GPU path.");
        Files.writeString(output.resolve("held-color-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if(!passed)throw new IllegalStateException("Held color framebuffer checks failed: "+checks);
        return names;
    }

    private static int[] capture(TextureTarget target,Path output)throws Exception {
        try(var image=Screenshot.takeScreenshot(target)) {
            if(output!=null)image.writeToFile(output);
            var pixels=new int[SIZE*SIZE];
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)pixels[y*SIZE+x]=image.getPixelRGBA(x,y);
            return pixels;
        }
    }

    private static boolean restored(TextureTarget target) {
        return GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) && !GL11.glIsEnabled(GL11.GL_BLEND)
                && GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_RGB)==GL14.GL_FUNC_ADD
                && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==target.frameBufferId;
    }
}
