package org.scex.slashbladelegacy.contracts;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Opt-in real renderer/camera regression, independent of server combat timing contracts. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class LegacyViewProbe {
    private static final String[] BLADES=Boolean.getBoolean("scex.legacy.addonView")
            ?new String[]{"slashblade:sange","slashblade_addon:kamuy_lightning","prinegorerouse:aeon_blade","si_slashblade:legacy/fox_faerie"}
            :new String[]{"slashblade:sange","slashblade_addon:kamuy_lightning"};
    private static final float[][] VIEWS={{0,0},{90,0},{180,0},{270,0},{360,0},{360,-60},{360,60},{360,0}};
    private static final List<Map<String,Object>> matrices=new ArrayList<>();
    private static final List<String> captures=new ArrayList<>();
    private static int phase,ticks,blade,view;
    private static long start;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static boolean done;
    private static Path out;
    private static String capture;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("scex.legacy.viewProbe") || done)return;
        var mc=Minecraft.getInstance();
        try {
            if(start==0){start=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(4);mc.options.fov().set(70);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);}
            if(failure!=null)throw new IllegalStateException("Fixture failure",failure);
            if(System.nanoTime()-start>180_000_000_000L)throw new IllegalStateException("Timeout phase "+phase);
            mc.getToasts().clear();
            if(phase==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardingAccessibilityFinished();mc.setScreen(new TitleScreen());}
            if(phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){capture="01-title.png";phase=1;}
            else if(phase==2){phase=3;mc.createWorldOpenFlows().createFreshLevel("slashblade-view-"+System.currentTimeMillis(),
                new LevelSettings("SlashBlade view regression",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(20260907L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());}
            else if(phase==3 && mc.player!=null && mc.level!=null && mc.screen==null){
                phase=4;ticks=0;prepared=false;mc.options.setCameraType(CameraType.FIRST_PERSON);prepare(mc);
            }else if(phase==4 && prepared && ++ticks>=60){phase=5;ticks=0;mc.gui.getChat().clearMessages(true);}
            else if(phase==5){
                ticks++;
                // Same Entity.turn entry as MouseHandler; retain previous-angle interpolation.
                if(ticks<=20)mc.player.turn((VIEWS[view][0]-mc.player.getYRot())/(21-ticks)/.15,
                    (VIEWS[view][1]-mc.player.getXRot())/(21-ticks)/.15);
                if(ticks==30)capture="view-blade-"+blade+"-angle-"+view+".png";
                if(ticks>=32){ticks=0;if(++view==VIEWS.length){view=0;phase=6;}}
            }else if(phase==6){
                ticks++;
                if(ticks==1)mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                if(ticks==2)mc.gameMode.releaseUsingItem(mc.player);
                if(ticks==4)capture="attack-blade-"+blade+".png";
                if(ticks==20){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);capture="third-person-blade-"+blade+".png";}
                if(ticks==30)capture="third-person-stable-blade-"+blade+".png";
                if(ticks>=34){ticks=0;if(++blade==BLADES.length)phase=7;else {phase=4;prepared=false;mc.options.setCameraType(CameraType.FIRST_PERSON);prepare(mc);}}
            }else if(phase==7){
                var gson=new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(out.resolve("view-matrices.json"),gson.toJson(matrices));
                Files.writeString(out.resolve("checks.json"),gson.toJson(Map.of("entry","Entity.turn (MouseHandler path)","blades",BLADES,"views",VIEWS,"matrices",matrices.size(),"captures",captures)));
                var missing=new ArrayList<String>();
                for(var id:BLADES){var h=mc.level.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(x->x.key().location().toString().equals(id)).findFirst().orElseThrow();
                    if(mc.getItemRenderer().getModel(h.value().getBlade(mc.level.registryAccess()),mc.level,mc.player,0)==mc.getModelManager().getMissingModel())missing.add(id);}
                if(!missing.isEmpty() || matrices.isEmpty() || captures.size()!=1+BLADES.length*11)throw new IllegalStateException("Incomplete rendered views "+captures.size()+" "+missing);
                Files.writeString(out.resolve("item-model-audit.json"),gson.toJson(Map.of("registeredItems",BLADES,"missingItemModels",missing)));
                Files.writeString(out.resolve("result.json"),gson.toJson(Map.of("status","captured","screenshots",3,"checks_passed",true,"capture_count",captures.size(),"entry","Camera and actual blade render matrices; visual/position comparison performed separately")));
                done=true;mc.stop();
            }
        }catch(Throwable e){done=true;com.mojang.logging.LogUtils.getLogger().error("SLASHBLADE_CLIENT_VERIFICATION_FAILED",e);try{Files.writeString(out.resolve("failure.txt"),e.toString());}catch(Exception ignored){}mc.stop();}
    }
    private static void prepare(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
        var s=mc.getSingleplayerServer();var l=s.overworld();l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);
        var p=s.getPlayerList().getPlayers().getFirst();p.teleportTo(l,12,-60,12,0,0);p.getAbilities().flying=false;p.onUpdateAbilities();
        var h=s.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(x->x.key().location().toString().equals(BLADES[blade])).findFirst().orElseThrow();
        var item=h.value().getBlade(s.registryAccess());BladeStateAccess.of(item).orElseThrow().setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
        p.setItemInHand(InteractionHand.MAIN_HAND,item);p.setItemInHand(InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();prepared=true;
    }catch(Throwable e){failure=e;}});}
    @SubscribeEvent public static void bladeFrame(mods.flammpfeil.slashblade.event.client.RenderOverrideEvent event){
        if(!Boolean.getBoolean("scex.legacy.viewProbe") || (phase!=5 && phase!=6) || done)return;
        var mc=Minecraft.getInstance();
        if(event.getStack()!=mc.player.getMainHandItem() || !Set.of("blade","sheath").contains(event.getTarget()))return;
        var row=new LinkedHashMap<String,Object>();var state=BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow();
        row.put("blade",blade);row.put("view",view);row.put("phase",phase);row.put("tick",ticks);row.put("target",event.getTarget());
        row.put("yaw",mc.player.getYRot());row.put("pitch",mc.player.getXRot());row.put("camera",mc.options.getCameraType().name());
        row.put("pose",event.getPoseStack().last().pose().get(new float[16]));row.put("modelView",com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix().get(new float[16]));
        row.put("combo",state.getComboSeq().toString());row.put("adjust",state.getAdjust().toString());row.put("swing",mc.player.attackAnim);
        matrices.add(row);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null || done)return;
        try(var img=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){img.writeToFile(out.resolve(capture));captures.add(capture);capture=null;if(phase==1)phase=2;}
        catch(Exception e){failure=e;}
    }
}
