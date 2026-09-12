package org.scex.slashbladelegacy.contracts;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.SlashBladeKeyMappings;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.scex.slashbladelegacy.*;

/** Opt-in disposable integrated-client regression. Uses actual input mappings, packets and render events. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class StabilityClientProbe {
    private static final List<Map<String,Object>> rows=new ArrayList<>(),frames=new ArrayList<>();
    private static final Set<String> captures=new HashSet<>();
    private static int stage,tick,click,camera,rankCase;
    private static long started;
    private static boolean done;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> serverSample;
    private static boolean sampleRequested;
    private static Path out;
    private static String capture;
    private static final List<String> failures=new ArrayList<>();
    private static final List<Map<String,Object>> swords=Collections.synchronizedList(new ArrayList<>());
    private static boolean enabled(){return Boolean.getBoolean("scex.legacy.stabilityProbe");}
    private static void write(String file,Object data) throws Exception {
        Files.writeString(out.resolve(file),new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }
    private static void check(boolean ok,String why){if(!ok)failures.add(why);}
    private static void server(Minecraft mc,Runnable action){ready=false;mc.getSingleplayerServer().execute(()->{try{action.run();ready=true;}catch(Throwable e){failure=e;}});}
    private static net.minecraft.server.level.ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    private static void key(Minecraft mc,int action){
        var key=SlashBladeKeyMappings.KEY_SUMMON_BLADE.getKey();
        if(key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE)
            ((org.scex.slashbladelegacy.contracts.mixin.ProbeMouseInvoker)mc.mouseHandler).legacyProbe$press(mc.getWindow().getWindow(),key.getValue(),action,0);
        else ((org.scex.slashbladelegacy.contracts.mixin.ProbeKeyboardInvoker)mc.keyboardHandler).legacyProbe$key(mc.getWindow().getWindow(),key.getValue(),0,action,0);
    }
    private static void equip(Minecraft mc){
        var p=player(mc);var definitions=p.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
        var definition=definitions.listElements().filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow();
        var main=definition.value().getBlade(p.registryAccess());var off=definition.value().getBlade(p.registryAccess());
        for(var blade:List.of(main,off)){
            blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.enchant(p.registryAccess().holderOrThrow(Enchantments.POWER),1);
            var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(ComboStateRegistry.NONE.getId());state.setBroken(false);state.setSealed(false);state.setProudSoulCount(1000);state.setSpecialEffects(new net.minecraft.nbt.ListTag());blade.setDamageValue(0);
        }
        BladeStateAccess.of(off).orElseThrow().setColorCode(0xff3030);
        p.setItemInHand(InteractionHand.MAIN_HAND,main);p.setItemInHand(InteractionHand.OFF_HAND,off);p.inventoryMenu.broadcastChanges();
    }
    private static Map<String,Object> sample(net.minecraft.world.entity.player.Player p){
        var rank=p.getData(CapabilityConcentrationRank.RANK_POINT);var state=BladeStateAccess.of(p.getMainHandItem()).orElseThrow();
        double[] damage={p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)};
        p.getMainHandItem().getAttributeModifiers().forEach(EquipmentSlot.MAINHAND,(attribute,modifier)->{if(attribute.equals(Attributes.ATTACK_DAMAGE))damage[0]+=modifier.amount();});
        return Map.of("dimension",p.level().dimension().location().toString(),"rank",rank.getRank(p.level().getGameTime()).level,"amp",state.getAttackAmplifier(),"tooltip_damage",damage[0],"combo",state.getComboSeq().toString(),"entity",p.getId());
    }
    @SubscribeEvent public static void join(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){
        if(enabled() && !e.getLevel().isClientSide() && e.getEntity() instanceof EntityAbstractSummonedSword s)
            swords.add(Map.of("type",s.getType().toString(),"uuid",s.getUUID().toString(),"pickable",s.canBeHitByProjectile()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled() || done)return;var mc=Minecraft.getInstance();
        try {
            if(started==0){
                if(!Files.exists(mc.gameDirectory.toPath().resolve("stability-client.flag")))throw new IllegalStateException("Disposable client marker missing");
                started=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(3);mc.options.simulationDistance().set(3);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            }
            if(failure!=null)throw new IllegalStateException("Fixture failed",failure);
            if(System.nanoTime()-started>240_000_000_000L)throw new IllegalStateException("Timeout "+stage+"/"+tick);
            if(stage==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardingAccessibilityFinished();mc.setScreen(new TitleScreen());}
            if(stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){
                stage=1;mc.createWorldOpenFlows().createFreshLevel("stability-"+System.currentTimeMillis(),new LevelSettings("Isolated stability regression",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20260912,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
            }else if(stage==1 && mc.player!=null && mc.level!=null && mc.screen==null){
                stage=2;tick=0;server(mc,()->{
                    var s=mc.getSingleplayerServer();var p=player(mc);var l=s.overworld();l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);l.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,s);
                    p.teleportTo(l,12,-60,12,0,0);p.getAbilities().flying=false;p.onUpdateAbilities();equip(mc);
                });
            }else if(stage==2 && ready && ++tick>=40){
                tick=0;stage=3;mc.options.setCameraType(camera==0?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);mc.gui.getChat().clearMessages(true);
            }else if(stage==3){
                ++tick;
                if(tick==1){mc.options.keyUse.setDown(true);KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(1));}
                if(tick==2)mc.options.keyUse.setDown(false);
                if(tick==4){
                    var actual=BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq().toString();
                    String wanted=LegacyCombat.id(new LegacyMove[]{LegacyMove.FORCE1,LegacyMove.FORCE2,LegacyMove.FORCE3,LegacyMove.FORCE4,LegacyMove.FORCE5,LegacyMove.FORCE6}[click]).toString();
                    check(actual.equals(wanted),"camera "+camera+" click "+click+": "+actual+" expected "+wanted);
                    rows.add(Map.of("camera",camera,"click",click,"client_combo",actual));capture="dual-"+camera+"-"+click+".png";
                }
                if(tick>=8 && capture==null){tick=0;if(++click==6){click=0;if(++camera<2){stage=2;server(mc,()->equip(mc));}else{stage=4;server(mc,()->{equip(mc);swords.clear();});}}}
            }else if(stage==4 && ready){
                ++tick;if(tick==20)key(mc,1);if(tick==22)key(mc,0);
                if(tick==27)capture="phantom-single.png";
                if(tick>=40){check(swords.size()==1,"single phantom count "+swords);rows.add(Map.of("single_phantoms",List.copyOf(swords)));tick=0;stage=5;}
            }else if(stage==5){
                stage=6;tick=0;sampleRequested=false;serverSample=null;server(mc,()->{
                    var p=player(mc);var s=mc.getSingleplayerServer();
                    if(rankCase==2 || rankCase==3){var level=s.getLevel(rankCase==2?Level.NETHER:Level.OVERWORLD);for(int x=10;x<=14;x++)for(int z=10;z<=14;z++)level.setBlockAndUpdate(new BlockPos(x,69,z),net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState());p.teleportTo(level,12,70,12,0,0);}
                    if(rankCase==0 || rankCase==1){var rank=p.getData(CapabilityConcentrationRank.RANK_POINT);rank.setRawRankPoint(rankCase==0?0:rank.getUnitCapacity()*4);rank.setLastUpdte(p.level().getGameTime());LegacyRank.synchronize(p);}
                    if(rankCase==4){p.setHealth(0);p.die(p.damageSources().genericKill());}
                });
            }else if(stage==6 && ready){
                ++tick;if(rankCase==4 && mc.player!=null && mc.player.isDeadOrDying()){mc.player.respawn();mc.setScreen(null);tick=0;}
                // DownloadTerrainScreen can outlive the first 45 client ticks. Sample after it
                // closes rather than miss a one-tick deadline and hang the fixture indefinitely.
                if(tick>=45 && !sampleRequested && mc.player!=null && mc.screen==null){sampleRequested=true;server(mc,()->serverSample=sample(player(mc)));}
                if(tick>=47 && ready && serverSample!=null && mc.player!=null && mc.screen==null){
                    var client=sample(mc.player);check(client.equals(serverSample),"rank case "+rankCase+" server="+serverSample+" client="+client);
                    rows.add(Map.of("rank_case",rankCase,"server",serverSample,"client",client));capture="rank-"+rankCase+".png";serverSample=null;stage=7;
                }
            }else if(stage==7 && capture==null){if(++rankCase<5){stage=5;}else stage=8;}
            else if(stage==8){
                check(!frames.isEmpty(),"No actual blade renderer events");write("checks.json",rows);write("frames.json",frames);write("result.json",Map.of("passed",failures.isEmpty(),"failures",failures,"captures",captures,"renderer",com.mojang.blaze3d.platform.GlUtil.getRenderer(),"scope","Actual client mappings, packets, renderer and dimension/respawn synchronization; not multiplayer or full pack"));done=true;mc.stop();
            }
        }catch(Throwable e){done=true;try{write("frames.json",frames);write("failure.json",Map.of("error",e.toString(),"stage",stage,"tick",tick,"checks",rows,"failures",failures));}catch(Exception ignored){}com.mojang.logging.LogUtils.getLogger().error("STABILITY_CLIENT_FAILED",e);mc.stop();}
    }
    @SubscribeEvent public static void bladeFrame(mods.flammpfeil.slashblade.event.client.RenderOverrideEvent event){
        if(!enabled() || done || stage!=3 || tick!=4 || !Set.of("blade","sheath").contains(event.getTarget()))return;
        var mc=Minecraft.getInstance();String hand=event.getStack()==mc.player.getMainHandItem()?"main":event.getStack()==mc.player.getOffhandItem()?"off":"other";
        frames.add(Map.of("camera",camera,"click",click,"hand",hand,"part",event.getTarget(),"pose",event.getPoseStack().last().pose().get(new float[16])));
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!enabled() || done || capture==null)return;
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out.resolve(capture));captures.add(capture);capture=null;}catch(Exception e){failure=e;}
    }
}
