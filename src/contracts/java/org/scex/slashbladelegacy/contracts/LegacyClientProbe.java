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
    private static final List<String> chain=new ArrayList<>();
    private static final Map<String,Object> results=new LinkedHashMap<>();
    private static final List<String> blades=new ArrayList<>();
    private static double startHeight;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("scex.legacy.clientProbe") || done)return;
        var mc=Minecraft.getInstance();
        try {
            if(started==0){
                started=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(4);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            }
            if(failure!=null)throw new IllegalStateException("Server fixture failed",failure);
            if(System.nanoTime()-started>180_000_000_000L)throw new IllegalStateException("Timeout phase "+phase);
            mc.getToasts().clear();
            if(phase==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardingAccessibilityFinished();mc.setScreen(new TitleScreen());}
            if(phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){capture="01-title.png";phase=1;}
            else if(phase==2){
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
                        equip(mc,blades.getFirst());prepared=true;
                    }catch(Throwable e){failure=e;}
                });
            }else if(phase==4 && prepared && ++ticks>=30){
                ticks=0;phase=5;mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            }else if(phase==5){
                ticks++;
                if(ticks==1){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
                if(ticks==2)mc.gameMode.releaseUsingItem(mc.player);
                if(ticks==4){
                    var state=BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow();
                    chain.add(state.getComboSeq().toString());capture="blade-"+bladeIndex+"-click-"+click+".png";
                }
                if(ticks>=8){
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
                phase=8;ticks=0;prepared=false;
                mc.getSingleplayerServer().execute(()->{
                    try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.teleportTo(p.serverLevel(),12,79,12,0,0);p.setOnGround(false);
                        BladeStateAccess.of(p.getMainHandItem()).orElseThrow().updateComboSeq(p,LegacyCombat.id(LegacyMove.HELM_BRAKER));prepared=true;
                    }catch(Throwable e){failure=e;}
                });
            }else if(phase==8 && prepared){
                ticks++;if(ticks==1)startHeight=mc.player.getY();
                if(ticks==3)capture="03-helm-descent.png";
                if(ticks>3 && mc.player.onGround()){
                    results.put("helm_client_landing_ticks",ticks);results.put("helm_height",mc.player.getY());
                    require(ticks<=10 && Math.abs(mc.player.getY()-71)<.01,"Slow/missed physical landing: "+ticks+" "+mc.player.getY());
                    phase=9;ticks=0;
                }
                if(ticks>20)throw new IllegalStateException("Helm did not land from eight blocks");
            }else if(phase==9 && ++ticks>=2){
                var move=LegacyCombat.move(BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq());
                require(move!=LegacyMove.HELM_BRAKER,"Air animation remained after landing");
                results.put("landing_combo",move.toString());capture="04-landing.png";phase=10;
            }else if(phase==11){
                phase=12;ticks=0;click=0;chain.clear();mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }else if(phase==12){
                ticks++;
                if(ticks==1)mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                if(ticks==2)mc.gameMode.releaseUsingItem(mc.player);
                if(ticks==4){chain.add(BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq().toString());capture="first-person-click-"+click+".png";}
                if(ticks>=8){ticks=0;if(++click==3){
                    require(new HashSet<>(chain).size()==3,"First-person repeated one combo: "+chain);
                    results.put("first_person_right_chain",List.copyOf(chain));phase=13;
                }}
            }else if(phase==13){
                Files.writeString(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));
                var missing=new ArrayList<String>();
                for(var id:blades){var h=mc.level.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(x->x.key().location().toString().equals(id)).findFirst().orElseThrow();
                    var item=h.value().getBlade(mc.level.registryAccess());
                    if(mc.getItemRenderer().getModel(item,mc.level,mc.player,0)==mc.getModelManager().getMissingModel())missing.add(id);
                }
                Files.writeString(out.resolve("item-model-audit.json"),new GsonBuilder().create().toJson(Map.of("registeredItems",blades,"missingItemModels",missing)));
                require(missing.isEmpty(),"Missing blade models: "+missing);
                Files.writeString(out.resolve("result.json"),"{\"status\":\"captured\",\"screenshots\":4,\"checks_passed\":true,\"entry\":\"MultiPlayerGameMode.useItem packets and integrated server\"}");
                done=true;mc.stop();
            }
        }catch(Throwable e){done=true;com.mojang.logging.LogUtils.getLogger().error("SLASHBLADE_CLIENT_VERIFICATION_FAILED",e);try{Files.writeString(out.resolve("failure.txt"),e.toString());Files.writeString(out.resolve("checks.json"),new GsonBuilder().create().toJson(results));}catch(Exception ignored){}mc.stop();}
    }
    private static void equip(Minecraft mc,String id){
        var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();
        var h=s.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(x->x.key().location().toString().equals(id)).findFirst().orElseThrow();
        var blade=h.value().getBlade(s.registryAccess());var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());
        p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);p.experienceLevel=0;p.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null || done)return;
        try(var img=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){img.writeToFile(out.resolve(capture));capture=null;if(phase==1 || phase==6 || phase==10)phase++;}
        catch(Exception e){failure=e;}
    }
    private static void require(boolean v,String m){if(!v)throw new IllegalStateException(m);}
}
