package org.scex.slashbladelegacy.contracts;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.scex.slashbladelegacy.*;

/** Actual client -> integrated server use packets, motion broadcast and client physics. Dev-only. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class LegacyClientProbe {
    private static int phase,ticks,click,bladeIndex;
    private static long started;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static boolean done;
    private static Path out;
    private static String capture;
    private static final Set<String> captured=new HashSet<>();
    static boolean captured(String name){return captured.contains(name);}
    private static final List<String> chain=new ArrayList<>();
    private static final Map<String,Object> results=new LinkedHashMap<>();
    private static final List<String> blades=new ArrayList<>();
    private static double startHeight;
    private static final boolean CLOCK_AWARE=Boolean.getBoolean("scex.legacy.clockAwareProbe");
    private static final boolean FULL17=Boolean.getBoolean("scex.legacy.full17Probe");
    private static boolean barrierTested;
    private static boolean rangeTested;
    private static boolean artsTested;
    private static boolean gaiaTested;
    private static boolean coverOpened;
    private static int coverTicks;
    private static int airInputStage;
    private static long inputNs,airStartNs,groundNs;
    private static boolean sampled,airObserved;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("scex.legacy.clientProbe") || Boolean.getBoolean("scex.legacy.stabilityProbe") || Boolean.getBoolean("scex.legacy.viewProbe") || Boolean.getBoolean("scex.legacy.lifecycleProbe") || done)return;
        var mc=Minecraft.getInstance();
        try {
            if(started==0){
                started=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(4);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            }
            if(failure!=null)throw new IllegalStateException("Server fixture failed",failure);
            if(System.nanoTime()-started>(LegacyArtsClientProbe.enabled()?240_000_000_000L:180_000_000_000L))throw new IllegalStateException("Timeout phase "+phase);
            mc.getToasts().clear();
            if(phase==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardingAccessibilityFinished();mc.setScreen(new TitleScreen());}
            if(phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){capture="01-title.png";phase=1;}
            else if(phase==2){
                if(Boolean.getBoolean("scex.legacy.coverProbe") && !captured("00-mod-cover.png")) {
                    if(!coverOpened) {
                        var screen=new net.neoforged.neoforge.client.gui.ModListScreen(mc.screen);
                        mc.setScreen(screen);
                        var list=screen.children().stream().filter(net.neoforged.neoforge.client.gui.widget.ModListWidget.class::isInstance)
                                .map(net.neoforged.neoforge.client.gui.widget.ModListWidget.class::cast).findFirst().orElseThrow();
                        var entry=list.children().stream().filter(e->e.getInfo().getModId().equals(LegacyCompat.MOD_ID)).findFirst().orElseThrow();
                        require(entry.getInfo().getLogoFile().orElse("").equals("cover.png"),"Packaged mod cover metadata");
                        list.setSelected(entry);screen.setSelected(entry);coverOpened=true;
                        results.put("mod_cover",Map.of("mod_id",LegacyCompat.MOD_ID,"logo",entry.getInfo().getLogoFile().orElseThrow()));
                    }
                    if(++coverTicks>=5)capture="00-mod-cover.png";
                    return;
                }
                phase=3;
                mc.createWorldOpenFlows().createFreshLevel("slashblade-visual-"+System.currentTimeMillis(),
                    new LevelSettings("SlashBlade isolated input verification",GameType.CREATIVE,false,net.minecraft.world.Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(20260907L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
            }else if(phase==3 && mc.player!=null && mc.level!=null && mc.screen==null){
                phase=4;prepared=false;
                mc.getSingleplayerServer().execute(()->{
                    try {
                        var s=mc.getSingleplayerServer();var l=s.overworld();l.setDayTime(6000);
                        l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);
                        for(int x=7;x<=17;x++)for(int z=7;z<=17;z++)l.setBlockAndUpdate(new BlockPos(x,70,z),net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState());
                        var p=s.getPlayerList().getPlayers().getFirst();p.teleportTo(l,12,71,12,0,0);p.getAbilities().flying=false;p.onUpdateAbilities();
                        blades.add("slashblade:sange");
                        if(net.neoforged.fml.ModList.get().isLoaded("slashblade_addon"))blades.add("slashblade_addon:kamuy_lightning");
                        if(FULL17){blades.add("prinegorerouse:aeon_blade");blades.add("si_slashblade:legacy/fox_faerie");}
                        LegacyDamageProbe.expandCases(blades);
                        equip(mc,blades.getFirst());prepared=true;
                    }catch(Throwable e){failure=e;}
                });
            }else if(phase==4 && prepared && ++ticks>=30){
                if(LegacyLeftClientProbe.enabled()){ticks=0;phase=19;return;}
                if(LegacyFeatherClientProbe.enabled()){ticks=0;phase=20;return;}
                if(LegacyAvoidClientProbe.enabled()){ticks=0;phase=22;return;}
                if(LegacySuperClientProbe.enabled()){ticks=0;phase=21;return;}
                ticks=0;phase=5;mc.options.setCameraType(Boolean.getBoolean("scex.legacy.rawInputProbe")?net.minecraft.client.CameraType.FIRST_PERSON:net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            }else if(phase==5){
                ticks++;
                if(ticks==1){if(!LegacyDamageProbe.beforeClick(mc,blades.get(bladeIndex),click)){ticks=0;return;}inputNs=System.nanoTime();sampled=false;
                    if(Boolean.getBoolean("scex.legacy.rawInputProbe")){mc.options.keyUse.setDown(true);net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(1));}
                    else mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
                if(ticks==2){if(Boolean.getBoolean("scex.legacy.rawInputProbe"))mc.options.keyUse.setDown(false);else mc.gameMode.releaseUsingItem(mc.player);}
                if(captureClick(mc)){
                    var state=BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow();
                    chain.add(state.getComboSeq().toString());capture="blade-"+bladeIndex+"-click-"+click+".png";
                }
                if(advanceClick()){
                    LegacyDamageProbe.finishClick();
                    ticks=0;click++;
                    if(click==3){
                        results.put(blades.get(bladeIndex),List.copyOf(chain));
                        require(chain.equals(List.of(LegacyCombat.id(LegacyMove.SAYA1).toString(),LegacyCombat.id(LegacyMove.SAYA2).toString(),LegacyCombat.id(LegacyMove.BATTOU).toString())),"Client right chain: "+chain);
                        chain.clear();click=0;bladeIndex++;
                        if(bladeIndex<blades.size()){
                            phase=4;prepared=false;
                            mc.getSingleplayerServer().execute(()->{try{equip(mc,blades.get(bladeIndex));prepared=true;}catch(Throwable e){failure=e;}});
                        }else {phase=6;capture="02-right-chain.png";}
                    }
                }
            }else if(phase==7){
                if(LegacyDamageProbe.enabled()){results.put("damage_probe",LegacyDamageProbe.report());if(!FULL17){phase=13;return;}}
                phase=8;ticks=0;prepared=false;airObserved=false;airStartNs=System.nanoTime();LegacyTimingTrace.resetLanding();
                mc.getSingleplayerServer().execute(()->{
                    try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.teleportTo(p.serverLevel(),12,79,12,0,0);p.setOnGround(false);
                        if(FULL17)BladeStateAccess.of(p.getMainHandItem()).orElseThrow().setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
                        else BladeStateAccess.of(p.getMainHandItem()).orElseThrow().updateComboSeq(p,LegacyCombat.id(LegacyMove.HELM_BRAKER));
                        p.inventoryMenu.broadcastChanges();prepared=true;
                    }catch(Throwable e){failure=e;}
                });
            }else if(phase==8 && prepared){
                if(FULL17 && airInputStage<5){
                    if(airInputStage==0 && (mc.player.onGround() || mc.player.getY()<=71))return;
                    airInputStage++;
                    if(airInputStage==1){mc.options.keyShift.setDown(true);mc.options.keyUp.setDown(true);}
                    if(airInputStage==3){mc.options.keyUse.setDown(true);net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(1));}
                    if(airInputStage==4){mc.options.keyUse.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyUp.setDown(false);}
                    return;
                }
                if(CLOCK_AWARE && !airObserved){
                    if(!mc.player.onGround() && mc.player.getY()>71 && LegacyCombat.move(BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq())==LegacyMove.HELM_BRAKER)airObserved=true;
                    else {require(System.nanoTime()-airStartNs<1_000_000_000L,"Client did not receive airborne fixture");return;}
                }
                ticks++;if(ticks==1)startHeight=mc.player.getY();
                if(ticks==3)capture="03-helm-descent.png";
                if(ticks>3 && mc.player.onGround()){
                    results.put("helm_client_landing_ticks",ticks);results.put("helm_height",mc.player.getY());
                    require(ticks<=10 && Math.abs(mc.player.getY()-71)<.01,"Slow/missed physical landing: "+ticks+" "+mc.player.getY());
                    phase=9;ticks=0;groundNs=System.nanoTime();
                }
                if(ticks>20)throw new IllegalStateException("Helm did not land from eight blocks");
            }else if(phase==9 && ++ticks>=2){
                var move=LegacyCombat.move(BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq());
                if(CLOCK_AWARE && (move!=LegacyMove.HELM_LANDING || LegacyTimingTrace.serverLanding().isEmpty())){
                    require(System.nanoTime()-groundNs<1_000_000_000L,"Authoritative landing/client packet missing");return;
                }
                require(move!=LegacyMove.HELM_BRAKER,"Air animation remained after landing");
                if(CLOCK_AWARE){results.put("landing_ack_ms",(System.nanoTime()-groundNs)/1e6);results.put("server_landing",LegacyTimingTrace.serverLanding());}
                results.put("landing_combo",move.toString());capture="04-landing.png";phase=10;
            }else if(phase==11){
                phase=12;ticks=0;click=0;chain.clear();mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }else if(phase==12){
                ticks++;
                if(ticks==1){inputNs=System.nanoTime();sampled=false;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
                if(ticks==2)mc.gameMode.releaseUsingItem(mc.player);
                if(captureClick(mc)){chain.add(BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq().toString());capture="first-person-click-"+click+".png";}
                if(advanceClick()){ticks=0;if(++click==3){
                    require(new HashSet<>(chain).size()==3,"First-person repeated one combo: "+chain);
                    results.put("first_person_right_chain",List.copyOf(chain));phase=13;
                }}
            }else if(phase==13){
                if(LegacyLeftClientProbe.enabled()){barrierTested=true;results.put("legacy17_left_client",LegacyLeftClientProbe.report());}
                if(LegacyFeatherClientProbe.enabled()){barrierTested=true;results.put("legacy17_feather_client",LegacyFeatherClientProbe.report());}
                if(LegacySuperClientProbe.enabled()){barrierTested=true;results.put("legacy17_super_client",LegacySuperClientProbe.report());}
                if(LegacyAvoidClientProbe.enabled())results.put("legacy17_avoid_client",LegacyAvoidClientProbe.report());
                if(FULL17 && !barrierTested){
                    phase=14;ticks=0;prepared=false;
                    mc.getSingleplayerServer().execute(()->{try{
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        p.getMainHandItem().enchant(p.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.THORNS),1);
                        BladeStateAccess.of(p.getMainHandItem()).orElseThrow().setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
                        p.inventoryMenu.broadcastChanges();prepared=true;
                    }catch(Throwable e){failure=e;}});return;
                }
                if(FULL17 && LegacyRangeClientProbe.enabled() && !rangeTested){phase=16;ticks=0;return;}
                if(rangeTested)results.put("legacy17_range_client",LegacyRangeClientProbe.report());
                if(FULL17 && LegacyArtsClientProbe.enabled() && !artsTested){phase=17;ticks=0;return;}
                if(artsTested)results.put("legacy17_arts_client",LegacyArtsClientProbe.report());
                if(FULL17 && LegacyGaiaClientProbe.enabled() && !gaiaTested){phase=18;ticks=0;return;}
                if(gaiaTested)results.put("legacy17_gaia_client",LegacyGaiaClientProbe.report());
                results.put("original_assertion_failures",LegacyTimingTrace.failures());
                results.put("clock_aware_input_and_ack",CLOCK_AWARE);
                Files.writeString(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));
                var missing=new ArrayList<String>();
                for(var id:blades){var h=mc.level.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(x->x.key().location().toString().equals(id)).findFirst().orElseThrow();
                    var item=h.value().getBlade(mc.level.registryAccess());
                    if(mc.getItemRenderer().getModel(item,mc.level,mc.player,0)==mc.getModelManager().getMissingModel())missing.add(id);
                }
                Files.writeString(out.resolve("item-model-audit.json"),new GsonBuilder().create().toJson(Map.of("registeredItems",blades,"missingItemModels",missing)));
                require(missing.isEmpty(),"Missing blade models: "+missing);
                Files.writeString(out.resolve("result.json"),new GsonBuilder().create().toJson(Map.of("status","captured","screenshots",(LegacyAvoidClientProbe.enabled()?LegacyAvoidClientProbe.SCREENSHOTS:LegacySuperClientProbe.enabled()?LegacySuperClientProbe.SCREENSHOTS:LegacyFeatherClientProbe.enabled()?LegacyFeatherClientProbe.SCREENSHOTS:LegacyLeftClientProbe.enabled()?LegacyLeftClientProbe.SCREENSHOTS:(FULL17?(LegacyRangeClientProbe.enabled()?23:8):4)+(LegacyArtsClientProbe.enabled()?LegacyArtsClientProbe.SCREENSHOTS:0)+(LegacyGaiaClientProbe.enabled()?LegacyGaiaClientProbe.COUNT:0))+(coverOpened?1:0),"checks_passed",true,"entry",LegacySuperClientProbe.enabled()?"Raw KeyboardHandler, V key mapping, MoveCommandMessage and server entity ticks":LegacyLeftClientProbe.enabled()?"Raw left mouse mapping, Minecraft picking, attack packets and real player":"MultiPlayerGameMode.useItem packets and integrated server")));
                if(!LegacyTimingTrace.failures().isEmpty())Files.writeString(out.resolve("result.json"),new GsonBuilder().create().toJson(Map.of("status","diagnostic_failure","screenshots",4,"checks_passed",false,"original_assertion_failures",LegacyTimingTrace.failures())));
                done=true;mc.stop();
            }else if(phase==14 && prepared){
                ticks++;
                if(ticks==5)mc.options.keyShift.setDown(true);
                if(ticks==8){mc.options.keyUse.setDown(true);net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(1));}
                if(ticks==35){
                    require(LegacyProjectileGuard.barrierAvailable(mc.player,mc.player.getTicksUsingItem()),"actual held-input barrier unavailable");
                    results.put("barrier_input_ticks",mc.player.getTicksUsingItem());capture="05-barrier.png";
                }
                if(ticks==40){mc.options.keyUse.setDown(false);mc.options.keyShift.setDown(false);barrierTested=true;phase=15;ticks=0;}
            }else if(phase==15 && ++ticks>=20){phase=13;}
            else if(phase==16 && LegacyRangeClientProbe.tick(mc,name->capture=name)){rangeTested=true;phase=13;}
            else if(phase==17 && LegacyArtsClientProbe.tick(mc,name->capture=name)){artsTested=true;phase=13;}
            else if(phase==18 && LegacyGaiaClientProbe.tick(mc,name->capture=name)){gaiaTested=true;phase=13;}
            else if(phase==19){try{if(LegacyLeftClientProbe.tick(mc,name->capture=name))phase=13;}catch(Throwable e){results.put("legacy17_left_client",LegacyLeftClientProbe.report());throw e;}}
            else if(phase==20){try{if(LegacyFeatherClientProbe.tick(mc,name->capture=name))phase=13;}catch(Throwable e){results.put("legacy17_feather_client",LegacyFeatherClientProbe.report());throw e;}}
            else if(phase==21){try{if(LegacySuperClientProbe.tick(mc,name->capture=name))phase=13;}catch(Throwable e){LegacySuperClientProbe.release(mc);results.put("legacy17_super_client",LegacySuperClientProbe.report());throw e;}}
            else if(phase==22){try{if(LegacyAvoidClientProbe.tick(mc,name->capture=name))phase=21;}catch(Throwable e){LegacyAvoidClientProbe.release(mc);results.put("legacy17_avoid_client",LegacyAvoidClientProbe.report());throw e;}}
        }catch(Throwable e){done=true;LegacyRangeClientProbe.release(mc);LegacyArtsClientProbe.release(mc);LegacyGaiaClientProbe.release(mc);results.put("legacy17_gaia_client",LegacyGaiaClientProbe.report());mc.options.keyUse.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyUp.setDown(false);results.put("legacy17_range_client",LegacyRangeClientProbe.report());results.put("legacy17_arts_client",LegacyArtsClientProbe.report());results.put("damage_probe",LegacyDamageProbe.report());com.mojang.logging.LogUtils.getLogger().error("SLASHBLADE_CLIENT_VERIFICATION_FAILED",e);try{Files.writeString(out.resolve("failure.txt"),e.toString());Files.writeString(out.resolve("checks.json"),new GsonBuilder().create().toJson(results));}catch(Exception ignored){}mc.stop();}
    }
    private static void equip(Minecraft mc,String id){
        var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();
        var h=s.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(x->x.key().location().toString().equals(id)).findFirst().orElseThrow();
        var blade=h.value().getBlade(s.registryAccess());var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
        p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);p.experienceLevel=0;p.inventoryMenu.broadcastChanges();
        LegacyDamageProbe.equip(p);
    }
    private static boolean captureClick(Minecraft mc){
        if(!CLOCK_AWARE)return ticks==4;
        if(ticks<4 || sampled)return false;
        var expected=LegacyCombat.id(new LegacyMove[]{LegacyMove.SAYA1,LegacyMove.SAYA2,LegacyMove.BATTOU}[click]).toString();
        var state=BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow();var server=LegacyTimingTrace.serverSnapshot();
        if(state.getComboSeq().toString().equals(expected) && expected.equals(server.get("combo"))
                && Objects.equals(state.getLastActionTime(),server.get("action_tick"))){sampled=true;return true;}
        require(System.nanoTime()-inputNs<1_000_000_000L,"Expected server/client committed combo "+expected+", client="+state.getComboSeq()+", server="+server);
        return false;
    }
    private static boolean advanceClick(){return ticks>=8 && (!CLOCK_AWARE || sampled && System.nanoTime()-inputNs>=400_000_000L);}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null || done)return;
        try(var img=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){img.writeToFile(out.resolve(capture));captured.add(capture);capture=null;if(phase==1 || phase==6 || phase==10)phase++;}
        catch(Exception e){failure=e;}
    }
    private static void require(boolean v,String m){if(!v && !LegacyTimingTrace.observeFailure(m))throw new IllegalStateException(m);}
}

