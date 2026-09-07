package org.scex.slashbladelegacy.contracts;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.scex.slashbladelegacy.*;

/** Real disconnect/config unload/openWorld, with the same tag-update tooltip entry as recipe search. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class LegacyLifecycleProbe {
    private static final String WORLD="slash-config-lifecycle";
    private static int phase,ticks,unloadedQueries;
    private static long start;
    private static boolean done;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static ItemStack cachedBlade=ItemStack.EMPTY;
    private static Path out;
    private static String capture;
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final List<Map<String,Object>> tags=new ArrayList<>();
    private static boolean enabled(){return Boolean.getBoolean("scex.legacy.lifecycleProbe");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled() || done)return;
        var mc=Minecraft.getInstance();
        try{
            if(start==0){start=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(4);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);}
            if(failure!=null)throw new IllegalStateException("Lifecycle failed",failure);
            if(System.nanoTime()-start>180_000_000_000L)throw new IllegalStateException("Timeout phase "+phase);
            mc.getToasts().clear();
            if(phase==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardingAccessibilityFinished();mc.setScreen(new TitleScreen());}
            if(phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){capture="01-title.png";phase=1;}
            else if(phase==2){phase=3;ticks=0;mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings("SlashBlade config lifecycle",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(20260907L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());}
            else if(phase==3 && mc.level!=null && mc.player!=null && mc.screen==null && ++ticks>=40){
                require(LegacyCompat.SPEC.isLoaded(),"Config not loaded in first world");
                cachedBlade=blade(mc.level.registryAccess());BladeStateAccess.of(cachedBlade).orElseThrow().setBroken(true);
                var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean(SummonedBladeMode.MODE,true);
                cachedBlade.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
                phase=4;prepared=false;auditLoaded(mc,"first_world");
            }else if(phase==4 && prepared){capture="02-first-world.png";phase=5;}
            else if(phase==6){phase=7;ticks=0;mc.level.disconnect();mc.disconnect(new TitleScreen());}
            else if(phase==7 && mc.screen instanceof TitleScreen && mc.getSingleplayerServer()==null && ++ticks>=20){
                require(!LegacyCompat.SPEC.isLoaded(),"Config was not actually unloaded");report.put("observed_config_unloaded",true);
                phase=8;ticks=0;mc.createWorldOpenFlows().openWorld(WORLD,()->failure=new IllegalStateException("Reopen cancelled"));
            }else if(phase==8 && mc.level!=null && mc.player!=null && mc.screen==null && ++ticks>=40){
                require(LegacyCompat.SPEC.isLoaded(),"Reopened config did not load");require(unloadedQueries>=2,"Unloaded tag tooltip path was not exercised");
                phase=9;prepared=false;auditLoaded(mc,"reopened_world");
            }else if(phase==9 && prepared){capture="03-reopened-world.png";phase=10;}
            else if(phase==11){
                require(mc.getItemRenderer().getModel(blade(mc.level.registryAccess()),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"Blade model missing");
                report.put("world_reopened",true);report.put("unloaded_tooltip_queries",unloadedQueries);report.put("tag_callbacks",tags);
                var gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(out.resolve("checks.json"),gson.toJson(report));
                Files.writeString(out.resolve("item-model-audit.json"),gson.toJson(Map.of("registeredItems",List.of("slashblade:sange"),"missingItemModels",List.of())));
                Files.writeString(out.resolve("result.json"),gson.toJson(Map.of("status","captured","screenshots",3,"checks_passed",true,"entry","Actual config unload and same-process openWorld with tag-update tooltip queries")));
                done=true;mc.stop();
            }
        }catch(Throwable e){done=true;com.mojang.logging.LogUtils.getLogger().error("SLASHBLADE_CLIENT_VERIFICATION_FAILED",e);
            try{report.put("phase",phase);report.put("tag_callbacks",tags);Files.writeString(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
                var w=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(w));Files.writeString(out.resolve("failure.txt"),w.toString());}catch(Exception ignored){}mc.stop();}
    }
    @SubscribeEvent public static void tags(TagsUpdatedEvent event){
        if(!enabled() || done || phase!=8)return;
        try{
            boolean loaded=LegacyCompat.SPEC.isLoaded();
            for(var stack:List.of(new ItemStack(Items.DIRT),cachedBlade)){
                var row=new LinkedHashMap<String,Object>();row.put("config_loaded",loaded);row.put("item",stack.getItem().toString());tags.add(row);
                var lines=stack.getTooltipLines(Item.TooltipContext.of(event.getRegistryAccess()),null,TooltipFlag.Default.NORMAL);
                row.put("tooltip_lines",lines.size());require(!lines.isEmpty(),"Empty tooltip");if(!loaded)unloadedQueries++;
            }
        }catch(Throwable e){failure=e;throw new IllegalStateException("Tag-update tooltip failed",e);}
    }
    private static ItemStack blade(net.minecraft.core.RegistryAccess access){return access.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow().value().getBlade(access);}
    private static void auditLoaded(Minecraft mc,String key){mc.getSingleplayerServer().execute(()->{try{
        var stack=blade(mc.getSingleplayerServer().registryAccess());var state=BladeStateAccess.of(stack).orElseThrow();state.setBroken(false);
        double healthy=amount(stack,net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);state.setBroken(true);state.setDamage(state.getMaxDamage()-1);
        var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean(SummonedBladeMode.MODE,true);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        boolean reach=LegacyCompat.BROKEN_REACH.get(),damage=LegacyCompat.BROKEN_DAMAGE.get(),sb=LegacyCompat.SUMMONED_BLADE.get();
        try{
            LegacyCompat.BROKEN_REACH.set(false);LegacyCompat.BROKEN_DAMAGE.set(false);LegacyCompat.SUMMONED_BLADE.set(false);
            double nativeReach=amount(stack,net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
            require(nativeReach==1.25 && amount(stack,net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==-1.5 && !SummonedBladeMode.enabled(stack),"Loaded false config ignored");
            LegacyCompat.BROKEN_REACH.set(true);LegacyCompat.BROKEN_DAMAGE.set(true);LegacyCompat.SUMMONED_BLADE.set(true);
            require(amount(stack,net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE)==healthy && amount(stack,net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==2 && SummonedBladeMode.enabled(stack),"Loaded true config ignored");
            report.put(key,Map.of("config_loaded",LegacyCompat.SPEC.isLoaded(),"live_false_true_settings_verified",true,"native_broken_reach",nativeReach,"restored_reach",healthy));
        }finally{LegacyCompat.BROKEN_REACH.set(reach);LegacyCompat.BROKEN_DAMAGE.set(damage);LegacyCompat.SUMMONED_BLADE.set(sb);}
        prepared=true;
    }catch(Throwable e){failure=e;}});}
    private static double amount(ItemStack s,net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a){return s.getAttributeModifiers().modifiers().stream().filter(e->e.attribute().equals(a)).mapToDouble(e->e.modifier().amount()).sum();}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture==null || done)return;
        try(var img=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){img.writeToFile(out.resolve(capture));capture=null;phase++;}catch(Exception e){failure=e;}}
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
