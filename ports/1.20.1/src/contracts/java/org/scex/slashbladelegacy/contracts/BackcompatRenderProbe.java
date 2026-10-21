package org.scex.slashbladelegacy.contracts;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.scex.slashbladelegacy.LegacyArtEntity;
import org.scex.slashbladelegacy.SummonedBladeMode;
import org.scex.slashbladelegacy.client.LegacyArtRenderer;

/** Draw the production dimension renderer through the active world render buffers. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class BackcompatRenderProbe {
    private static boolean active;
    private static int stage,ticks,frames;
    private static Throwable failure;
    private static LegacyArtEntity art;

    private BackcompatRenderProbe() {}

    static boolean tick(Minecraft mc,Consumer<String> capture,BooleanSupplier captured,Map<String,Object> report){
        if(stage==3)return true;
        if(failure!=null)throw new IllegalStateException("Backcompat dimension renderer failed",failure);
        if(++ticks>100)throw new IllegalStateException("Backcompat dimension renderer did not finish");
        if(stage==0){
            art=new LegacyArtEntity(SummonedBladeMode.ART.get(),mc.level);art.setColor(0x3333FF);
            if(art.mode()!=LegacyArtEntity.Mode.DIMENSION)throw new IllegalStateException("Fixture mode is not DIMENSION");
            active=true;stage=1;
        }else if(stage==1 && frames>=2){
            capture.accept("backcompat-dimension.png");stage=2;
        }else if(stage==2 && captured.getAsBoolean()){
            active=false;art=null;stage=3;
            report.put("backcompat_dimension_render",Map.of("mode","DIMENSION","renderer",LegacyArtRenderer.class.getName(),
                    "render_callbacks",frames,"entry","RenderLevelStageEvent.AFTER_ENTITIES with the current world buffers","passed",true));
        }
        return stage==3;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(!active || event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();var poses=event.getPoseStack();
        poses.pushPose();
        try{
            var camera=event.getCamera().getPosition();var position=camera.add(mc.player.getLookAngle().scale(6));
            art.setPos(position);poses.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
            var renderer=mc.getEntityRenderDispatcher().getRenderer(art);
            if(!(renderer instanceof LegacyArtRenderer))throw new IllegalStateException("Unexpected dimension renderer");
            var buffers=mc.renderBuffers().bufferSource();renderer.render(art,0,event.getPartialTick(),poses,buffers,15728880);buffers.endBatch();
            frames++;
        }catch(Throwable error){failure=error;active=false;}
        finally{poses.popPose();}
    }
}
