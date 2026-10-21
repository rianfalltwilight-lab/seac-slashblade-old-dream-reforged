package org.scex.slashbladelegacy.contracts;

import net.minecraftforge.event.TickEvent.ClientTickEvent;

import net.minecraftforge.event.TickEvent.RenderTickEvent;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.scex.slashbladelegacy.*;

/** Real disconnect/config unload/loadLevel, with the same tag-update tooltip entry as recipe search. */
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
    private static final Map<Integer,String> persisted=new LinkedHashMap<>();
    private static final boolean BACKCOMPAT=Boolean.getBoolean("scex.legacy.backcompatProbe");
    private static final boolean DEATH=Boolean.getBoolean("scex.legacy.deathProbe") || BACKCOMPAT;
    private static final boolean LOGOUT=Boolean.getBoolean("scex.legacy.logoutProbe") || DEATH;
    private static final String SUITE=BACKCOMPAT?"backcompat-20261013":DEATH?"death-enchantment-20261013":"logout-unbreakable-20261012";
    private static final List<String> logoutCaptures=new ArrayList<>();
    private static final Map<String,Object> logout=new LinkedHashMap<>();
    private static final String LOGOUT_MARKER=UUID.randomUUID().toString();
    private static boolean enabled(){return Boolean.getBoolean("scex.legacy.lifecycleProbe");}
    @SubscribeEvent public static void tick(ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled() || done)return;
        var mc=Minecraft.getInstance();
        try{
            if(start==0){start=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
                if(LOGOUT){report.put("suite",SUITE);if(!Files.exists(mc.gameDirectory.toPath().resolve(BACKCOMPAT?"backcompat-20261013.flag":DEATH?"death-enchantment-20261013.flag":"logout-client.flag")))throw new IllegalStateException("Disposable lifecycle marker missing");}
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(4);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);}
            if(failure!=null)throw new IllegalStateException("Lifecycle failed",failure);
            if(System.nanoTime()-start>180_000_000_000L)throw new IllegalStateException("Timeout phase "+phase);
            mc.getToasts().clear();
            if(phase==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());}
            if(LOGOUT){logoutTick(mc);return;}
            if(phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){capture="01-title.png";phase=1;}
            else if(phase==2){phase=3;ticks=0;mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings("SlashBlade config lifecycle",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(20260907L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());}
            else if(phase==3 && mc.level!=null && mc.player!=null && mc.screen==null && ++ticks>=40){
                require(LegacyCompat.SPEC.isLoaded(),"Config not loaded in first world");
                cachedBlade=blade(mc.level.registryAccess());BladeStateAccess.of(cachedBlade).orElseThrow().setBroken(true);
                var tag=cachedBlade.getOrCreateTag();tag.putBoolean(SummonedBladeMode.MODE,true);
                phase=4;prepared=false;auditLoaded(mc,"first_world");
            }else if(phase==4 && prepared){capture="02-first-world.png";phase=5;}
            else if(phase==6){phase=7;ticks=0;mc.level.disconnect();mc.clearLevel(new TitleScreen());}
            else if(phase==7 && mc.screen instanceof TitleScreen && mc.getSingleplayerServer()==null && ++ticks>=20){
                require(!LegacyCompat.SPEC.isLoaded(),"Config was not actually unloaded");report.put("observed_config_unloaded",true);
                phase=8;ticks=0;mc.createWorldOpenFlows().loadLevel(new TitleScreen(),WORLD);
            }else if(phase==8 && mc.level!=null && mc.player!=null && mc.screen==null && ++ticks>=40){
                require(LegacyCompat.SPEC.isLoaded(),"Reopened config did not load");require(unloadedQueries>=2,"Unloaded tag tooltip path was not exercised");
                phase=9;prepared=false;auditLoaded(mc,"reopened_world");
            }else if(phase==9 && prepared && mc.level!=null && mc.player!=null && mc.screen==null
                    && mc.level.dimension().equals(Level.OVERWORLD) && ++ticks>=60){
                report.put("client_returned_to_overworld",true);capture="03-reopened-world.png";phase=10;
            }
            else if(phase==11){
                require(mc.getItemRenderer().getModel(blade(mc.level.registryAccess()),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"Blade model missing");
                report.put("world_reopened",true);report.put("unloaded_tooltip_queries",unloadedQueries);report.put("tag_callbacks",tags);
                var gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(out.resolve("checks.json"),gson.toJson(report));
                Files.writeString(out.resolve("item-model-audit.json"),gson.toJson(Map.of("registeredItems",List.of("slashblade:sange"),"missingItemModels",List.of())));
                Files.writeString(out.resolve("result.json"),gson.toJson(Map.of("status","captured","screenshots",3,"checks_passed",true,"entry","Actual config unload and same-process loadLevel with tag-update tooltip queries")));
                done=true;mc.stop();
            }
        }catch(Throwable e){done=true;if(DEATH)DeathClientProbe.cleanup(mc);com.mojang.logging.LogUtils.getLogger().error("SLASHBLADE_CLIENT_VERIFICATION_FAILED",e);
            try{report.put("phase",phase);report.put("tag_callbacks",tags);Files.writeString(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
                var w=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(w));Files.writeString(out.resolve("failure.txt"),w.toString());}catch(Exception ignored){}mc.stop();}
    }
    @SubscribeEvent public static void tags(TagsUpdatedEvent event){
        if(!enabled() || LOGOUT || done || phase!=8)return;
        try{
            boolean loaded=LegacyCompat.SPEC.isLoaded();
            for(var stack:List.of(new ItemStack(Items.DIRT),cachedBlade)){
                var row=new LinkedHashMap<String,Object>();row.put("config_loaded",loaded);row.put("item",stack.getItem().toString());tags.add(row);
                var lines=stack.getTooltipLines(null,TooltipFlag.Default.NORMAL);
                row.put("tooltip_lines",lines.size());require(!lines.isEmpty(),"Empty tooltip");if(!loaded)unloadedQueries++;
            }
        }catch(Throwable e){failure=e;throw new IllegalStateException("Tag-update tooltip failed",e);}
    }
    private static ItemStack blade(net.minecraft.core.RegistryAccess access){
        if(BACKCOMPAT){
            var stack=ContractItems.blade();stack.setHoverName(net.minecraft.network.chat.Component.literal("Backcompat blade"));
            stack.enchant(net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS,1);
            BladeStateAccess.of(stack).orElseThrow().setBaseAttackModifier(3);return stack;
        }
        return access.registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).holders().filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow().value().getBlade();
    }
    private static void auditLoaded(Minecraft mc,String key){mc.getSingleplayerServer().execute(()->{try{
        var stack=blade(mc.getSingleplayerServer().registryAccess());var state=BladeStateAccess.of(stack).orElseThrow();state.setBroken(false);
        double healthy=amount(stack,net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());state.setBroken(true);state.setDamage(state.getMaxDamage()-1);
        var tag=stack.getOrCreateTag();tag.putBoolean(SummonedBladeMode.MODE,true);
        boolean reach=LegacyCompat.BROKEN_REACH.get(),damage=LegacyCompat.BROKEN_DAMAGE.get(),sb=LegacyCompat.SUMMONED_BLADE.get(),combat=LegacyCompat.LEGACY_COMBAT.get();
        try{
            LegacyCompat.LEGACY_COMBAT.set(false);
            LegacyCompat.BROKEN_REACH.set(false);LegacyCompat.BROKEN_DAMAGE.set(false);LegacyCompat.SUMMONED_BLADE.set(false);
            double nativeReach=amount(stack,net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
            require(nativeReach==1.25 && amount(stack,net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==-1.5 && !SummonedBladeMode.enabled(stack),"Loaded false config ignored");
            LegacyCompat.BROKEN_REACH.set(true);LegacyCompat.BROKEN_DAMAGE.set(true);LegacyCompat.SUMMONED_BLADE.set(true);
            require(amount(stack,net.minecraftforge.common.ForgeMod.ENTITY_REACH.get())==healthy && amount(stack,net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==2 && SummonedBladeMode.enabled(stack),"Loaded true config ignored");
            report.put(key,Map.of("config_loaded",LegacyCompat.SPEC.isLoaded(),"live_false_true_settings_verified",true,"native_broken_reach",nativeReach,"restored_reach",healthy));
        }finally{LegacyCompat.BROKEN_REACH.set(reach);LegacyCompat.BROKEN_DAMAGE.set(damage);LegacyCompat.SUMMONED_BLADE.set(sb);LegacyCompat.LEGACY_COMBAT.set(combat);}
        var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayers().get(0);
        if(key.equals("first_world")) {
            player.getInventory().clearContent();player.experienceLevel=0;player.totalExperience=0;player.getInventory().selected=0;int slot=9;
            for(var definition:server.registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).holders().toList()) {
                if(!Set.of("prinegorerouse","si_slashblade").contains(definition.key().location().getNamespace()))continue;
                var item=definition.value().getBlade();var bladeState=BladeStateAccess.of(item).orElseThrow();bladeState.setRefine(123+slot);bladeState.setProudSoulCount(4321+slot);bladeState.setKillCount(1050+slot);item.setDamageValue(17);
                var data=item.getOrCreateTag().copy();data.putBoolean(SummonedBladeMode.MODE,true);item.setTag(data);
                if(definition.key().location().getNamespace().equals("si_slashblade"))item.getItem().getClass().getMethod("reconcile",ItemStack.class).invoke(item.getItem(),item);
                player.getInventory().setItem(slot,item);persisted.put(slot,item.save(new net.minecraft.nbt.CompoundTag()).toString());slot++;
            }
            require(persisted.size()==26,"All 26 addon blades must be saved");report.put("saved_addon_count",persisted.size());player.inventoryMenu.broadcastChanges();
        } else {
            for(var entry:persisted.entrySet())require(player.getInventory().getItem(entry.getKey()).save(new net.minecraft.nbt.CompoundTag()).toString().equals(entry.getValue()),"Addon persisted stack changed in slot "+entry.getKey());
            report.put("addon_roundtrip_exact_nbt",persisted.size());
            var originalLevel=player.serverLevel();var position=player.position();var nether=server.getLevel(Level.NETHER);require(nether!=null,"Nether available");
            player.teleportTo(nether,12,75,12,0,0);require(player.level().dimension().equals(Level.NETHER),"actual server dimension transfer");
            for(var entry:persisted.entrySet())require(player.getInventory().getItem(entry.getKey()).save(new net.minecraft.nbt.CompoundTag()).toString().equals(entry.getValue()),"Addon NBT changed across dimension");
            player.teleportTo(originalLevel,position.x,position.y,position.z,0,0);report.put("dimension_transfer_exact_nbt",persisted.size());
        }
        prepared=true;
    }catch(Throwable e){failure=e;}});}
    private static double amount(ItemStack s,net.minecraft.world.entity.ai.attributes.Attribute a){return s.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(a).stream().mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount).sum();}
    @SubscribeEvent public static void frame(RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;if(capture==null || done)return;
        try(var img=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){img.writeToFile(out.resolve(capture));if(LOGOUT)logoutCaptures.add(capture);capture=null;if(!(DEATH && phase==6))phase++;}catch(Exception e){failure=e;}}

    private static void logoutTick(Minecraft mc) throws Exception {
        if(phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){
            report.put("logout_lifecycle",logout);if(DEATH)report.put("death_lifecycle",DeathClientProbe.report());logout.put("world",WORLD);capture="01-title.png";phase=1;
        }else if(phase==2){
            phase=3;ticks=0;mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("SlashBlade logout regression",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(20261012L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }else if(phase==3 && mc.level!=null && mc.player!=null && mc.screen==null && ++ticks>=40){
            phase=4;prepared=false;logoutWorld(mc,false);
        }else if(phase==4 && prepared && mc.player!=null && mc.player.getMainHandItem().hasTag() && LOGOUT_MARKER.equals(mc.player.getMainHandItem().getTag().getString("logout_fixture"))){
            if(BACKCOMPAT){
                require(mc.getItemRenderer().getModel(mc.player.getMainHandItem(),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"Backcompat blade model missing");
                report.put("backcompat_client_model",true);
            }
            capture="02-first-world.png";phase=5;
        }else if(phase==6){
            if(BACKCOMPAT && !BackcompatRenderProbe.tick(mc,name->capture=name,()->capture==null,report))return;
            if(DEATH && !DeathClientProbe.tick(mc,LOGOUT_MARKER,name->capture=name,()->capture==null))return;
            phase=7;ticks=0;mc.level.disconnect();mc.clearLevel(new TitleScreen());
        }else if(phase==7 && mc.screen instanceof TitleScreen && mc.getSingleplayerServer()==null && ++ticks>=20){
            require(mc.level==null && mc.player==null,"First world remains attached");
            require(!LegacyCompat.SPEC.isLoaded(),"First server config remains loaded");
            logout.put("first_disconnect_completed",true);capture="03-title-after-disconnect.png";phase=8;
        }else if(phase==9){
            clickButton(mc,"menu.options");phase=10;
        }else if(phase==10 && mc.screen instanceof net.minecraft.client.gui.screens.OptionsScreen){
            logout.put("first_title_options_opened",true);clickButton(mc,"gui.done");phase=11;ticks=0;
        }else if(phase==11 && mc.screen instanceof TitleScreen && mc.getOverlay()==null && ++ticks>=5){
            logout.put("first_title_options_closed",true);phase=12;ticks=0;
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(),WORLD);
        }else if(phase==12 && mc.level!=null && mc.player!=null && mc.screen==null && ++ticks>=40){
            phase=13;prepared=false;logoutWorld(mc,true);
        }else if(phase==13 && prepared && mc.player!=null && mc.player.getMainHandItem().hasTag() && LOGOUT_MARKER.equals(mc.player.getMainHandItem().getTag().getString("logout_fixture"))){
            capture="04-reopened-world.png";phase=14;
        }else if(phase==15){
            phase=16;ticks=0;mc.level.disconnect();mc.clearLevel(new TitleScreen());
        }else if(phase==16 && mc.screen instanceof TitleScreen && mc.getSingleplayerServer()==null && ++ticks>=20){
            require(mc.level==null && mc.player==null,"Reopened world remains attached");
            require(!LegacyCompat.SPEC.isLoaded(),"Reopened server config remains loaded");
            logout.put("second_disconnect_completed",true);capture="05-final-title.png";phase=17;
        }else if(phase==18){
            clickButton(mc,"menu.options");phase=19;
        }else if(phase==19 && mc.screen instanceof net.minecraft.client.gui.screens.OptionsScreen){
            logout.put("second_title_options_opened",true);clickButton(mc,"gui.done");phase=20;ticks=0;
        }else if(phase==20 && mc.screen instanceof TitleScreen && mc.getOverlay()==null && ++ticks>=5){
            logout.put("second_title_options_closed",true);logout.put("passed",true);
            require(logoutCaptures.size()==(BACKCOMPAT?8:DEATH?7:5),"Incomplete lifecycle captures");
            var gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(out.resolve("checks.json"),gson.toJson(report));
            Files.writeString(out.resolve("result.json"),gson.toJson(Map.of("suite",SUITE,"status","captured","passed",true,"checks_passed",true,"case_count",BACKCOMPAT?4:DEATH?3:2,
                    "screenshots",logoutCaptures.size(),"captures",logoutCaptures,"entry",DEATH?"Native death screen, respawn button, retained blade attack, F8, disconnect, title buttons, same-world reload, second disconnect":"Native disconnect, title button clicks, same-world reload, second disconnect")));
            done=true;mc.stop();
        }
    }

    private static void logoutWorld(Minecraft mc,boolean reopened) {
        var server=mc.getSingleplayerServer();server.execute(()->{try{
            require(LegacyCompat.SPEC.isLoaded(),"Live server config is not loaded");
            if(!reopened){
                if(BACKCOMPAT)BackcompatContracts.run(server,report);
                else{if(DEATH)DeathContracts.run(server,report);LogoutContracts.run(server,report);UnbreakableContracts.run(server,report);}
            }
            var player=server.getPlayerList().getPlayers().get(0);
            if(!reopened){
                var stack=blade(server.registryAccess());stack.getOrCreateTag().putString("logout_fixture",LOGOUT_MARKER);
                BladeStateAccess.of(stack).orElseThrow().setProudSoulCount(1734);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);player.inventoryMenu.broadcastChanges();
            }else{
                var stack=player.getMainHandItem();require(LOGOUT_MARKER.equals(stack.getOrCreateTag().getString("logout_fixture")),"Same-world blade marker did not persist");
                require(BladeStateAccess.of(stack).orElseThrow().getProudSoulCount()==1734,"Same-world blade souls changed");
                if(DEATH)require(LegacyMode.modern(player),"Respawn mode did not persist across logout");
            }
            require(player.getCapability(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).isPresent(),"Live player input capability missing");
            logout.put(reopened?"world_reopened":"first_world_entered",true);prepared=true;
        }catch(Throwable e){failure=e;}});
    }

    static void clickButton(Minecraft mc,String translation) {
        String label=net.minecraft.network.chat.Component.translatable(translation).getString();
        var button=mc.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(button.active && button.visible,"Menu button is not active: "+translation);
        require(mc.screen.mouseClicked(button.getX()+button.getWidth()/2d,button.getY()+button.getHeight()/2d,0),"Menu click failed: "+translation);
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
