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
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.scex.slashbladelegacy.*;
import org.scex.slashbladelegacy.client.LegacyKeyMappings;

/** Actual GUI keyboard and mouse inputs against an integrated server; no direct mode writes. */
@EventBusSubscriber(modid="slashblade_legacy_contracts",value=Dist.CLIENT)
public final class DualModeClientProbe {
    private static final List<Map<String,Object>> rows=new ArrayList<>(),frames=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static final Set<String> captures=new HashSet<>();
    private static final List<String> swords=Collections.synchronizedList(new ArrayList<>());
    private static int stage,tick,click,camera,mode,life;
    private static long started,requested;
    private static boolean done;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> serverSample;
    private static boolean sampleRequested;
    private static boolean clickCaptured;
    private static Path out;
    private static String capture;
    private static boolean enabled(){return Boolean.getBoolean("scex.legacy.dualModeProbe");}
    private static void write(String f,Object v)throws Exception{Files.writeString(out.resolve(f),new GsonBuilder().setPrettyPrinting().create().toJson(v));}
    private static void check(boolean v,String s){if(!v)failures.add(s);}
    private static net.minecraft.server.level.ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();}
    private static void server(Minecraft mc,Runnable action){ready=false;mc.getSingleplayerServer().execute(()->{try{action.run();ready=true;}catch(Throwable e){failure=e;}});}
    private static void key(Minecraft mc,KeyMapping mapping,int action){
        var key=mapping.getKey();
        if(key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE)
            ((org.scex.slashbladelegacy.contracts.mixin.ProbeMouseInvoker)mc.mouseHandler).legacyProbe$press(mc.getWindow().getWindow(),key.getValue(),action,0);
        else ((org.scex.slashbladelegacy.contracts.mixin.ProbeKeyboardInvoker)mc.keyboardHandler).legacyProbe$key(mc.getWindow().getWindow(),key.getValue(),0,action,0);
    }
    private static void equip(Minecraft mc){
        var p=player(mc);var d=p.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(h->h.key().location().toString().equals("slashblade:sange")).findFirst().orElseThrow();
        var main=d.value().getBlade(p.registryAccess());var off=d.value().getBlade(p.registryAccess());
        for(var b:List.of(main,off)){b.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);b.enchant(p.registryAccess().holderOrThrow(Enchantments.POWER),1);var s=BladeStateAccess.of(b).orElseThrow();s.setComboSeq(ComboStateRegistry.NONE.getId());s.setBroken(false);s.setSealed(false);s.setProudSoulCount(1000);s.setSpecialEffects(new net.minecraft.nbt.ListTag());b.setDamageValue(0);}
        BladeStateAccess.of(off).orElseThrow().setColorCode(0xff3030);p.setItemInHand(InteractionHand.MAIN_HAND,main);p.setItemInHand(InteractionHand.OFF_HAND,off);LegacyMode.bindInventory(p);p.inventoryMenu.broadcastChanges();
    }
    private static Map<String,Object> sample(net.minecraft.world.entity.player.Player p){var r=p.getData(CapabilityConcentrationRank.RANK_POINT);var s=BladeStateAccess.of(p.getMainHandItem()).orElseThrow();return Map.of("modern",LegacyMode.modern(p),"rank_cap",r.getMaxCapacity(),"dimension",p.level().dimension().location().toString(),"combo",s.getComboSeq().toString(),"souls",s.getProudSoulCount(),"entity",p.getId());}
    private static Map<String,Object> diagnostics(net.minecraft.server.level.ServerPlayer p){
        var result=new LinkedHashMap<String,Object>();result.put("sample",sample(p));result.put("pending",LegacyMode.pending(p));result.put("ready",LegacyMode.ready(p));result.put("ground",p.onGround());result.put("using",p.isUsingItem());result.put("swinging",p.swinging);result.put("inventory_menu",p.containerMenu==p.inventoryMenu);result.put("commands",p.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).getCommands().toString());
        result.put("blade_states",List.of(p.getMainHandItem(),p.getOffhandItem()).stream().map(b->BladeStateAccess.of(b).map(s->Map.of("combo",s.getComboSeq().toString(),"peek",s.peekCurrentComboStateTicks(p).toString(),"action",s.getLastActionTime())).orElse(Map.of())).toList());
        result.put("time",p.level().getGameTime());result.put("movement",List.of("sb.avoid.counter","sb.avoid.trickup","sb.airtrick.counter","SB.AvoidTimeout").stream().map(k->k+"="+p.getPersistentData().get(k)).toList());
        try{var field=LegacyMode.class.getDeclaredField("OWNED");field.setAccessible(true);var entities=(Collection<?>)((Map<?,?>)field.get(null)).get(p.getUUID());result.put("tracked_projectiles",entities==null?List.of():entities.stream().map(x->{var e=(net.minecraft.world.entity.Entity)x;return Map.of("type",e.getType().toString(),"age",e.tickCount,"removed",e.isRemoved(),"owner_match",((mods.flammpfeil.slashblade.entity.IShootable)e).getShooter()==p);}).toList());}catch(Exception e){result.put("diagnostic_error",e.toString());}
        return result;
    }
    @SubscribeEvent public static void join(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){if(enabled() && !e.getLevel().isClientSide() && e.getEntity() instanceof EntityAbstractSummonedSword)swords.add(e.getEntity().getType().toString());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled() || done)return;var mc=Minecraft.getInstance();
        try {
            if(started==0){if(!Files.exists(mc.gameDirectory.toPath().resolve("dual-mode-client.flag")))throw new IllegalStateException("Disposable marker missing");started=System.nanoTime();out=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(out);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.renderDistance().set(3);mc.options.simulationDistance().set(3);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);}
            if(failure!=null)throw new IllegalStateException("Server fixture",failure);
            if(System.nanoTime()-started>240_000_000_000L)throw new IllegalStateException("Timeout "+stage+"/"+tick+"/"+mode);
            if(stage==0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardingAccessibilityFinished();mc.setScreen(new TitleScreen());}
            if(stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null){stage=1;mc.createWorldOpenFlows().createFreshLevel("dual-mode-"+System.currentTimeMillis(),new LevelSettings("Isolated mode validation",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20260912,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());}
            else if(stage==1 && mc.player!=null && mc.level!=null && mc.screen==null){stage=2;tick=0;server(mc,()->{var s=mc.getSingleplayerServer();var p=player(mc);s.overworld().setDayTime(6000);s.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);s.overworld().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,s);p.teleportTo(s.overworld(),12,-60,12,0,0);p.getAbilities().flying=false;p.onUpdateAbilities();equip(mc);});}
            else if(stage==2 && ready){
                if(++tick==1){org.lwjgl.glfw.GLFW.glfwShowWindow(mc.getWindow().getWindow());org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());}
                if(tick>=40){tick=0;stage=3;mc.options.setCameraType(camera==0?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);mc.gui.getChat().clearMessages(true);}
            }
            else if(stage==3){
                ++tick;if(tick==1){clickCaptured=false;mc.options.keyUse.setDown(true);KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(1));}if(tick==2)mc.options.keyUse.setDown(false);
                if(tick>=4 && !clickCaptured){var actual=BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq();
                    var wanted=LegacyCombat.id(new LegacyMove[]{LegacyMove.FORCE1,LegacyMove.FORCE2,LegacyMove.FORCE3,LegacyMove.FORCE4,LegacyMove.FORCE5,LegacyMove.FORCE6}[click]);
                    boolean accepted=mode==1?actual.getNamespace().equals("slashblade") && !actual.equals(ComboStateRegistry.NONE.getId()):actual.equals(wanted);
                    // A loaded addon client may receive the server combo after tick 4. Sample its actual arrival.
                    if(accepted || tick>=12){check(accepted,"Mode "+mode+" click "+click+": "+actual);clickCaptured=true;rows.add(Map.of("mode",mode,"camera",camera,"click",click,"combo",actual.toString(),"observed_client_ticks",tick));capture="mode-"+mode+"-view-"+camera+"-click-"+click+".png";}
                }
                if(tick>=8 && clickCaptured && capture==null){tick=0;if(++click==6){click=0;if(++camera<2){stage=2;server(mc,()->equip(mc));}else{stage=4;server(mc,()->{equip(mc);swords.clear();});}}}
            }else if(stage==4 && ready){
                ++tick;if(tick==20)key(mc,SlashBladeKeyMappings.KEY_SUMMON_BLADE,1);if(tick==22)key(mc,SlashBladeKeyMappings.KEY_SUMMON_BLADE,0);
                if(tick==27)capture="mode-"+mode+"-phantom.png";
                if(tick>=80){check(swords.size()==1,"Mode "+mode+" phantom count "+swords);if(swords.size()==1)check(swords.getFirst().startsWith(mode==1?"entity.slashblade.":"entity.slashblade_legacy_compat."),"Mode "+mode+" phantom type "+swords);rows.add(Map.of("mode",mode,"swords",List.copyOf(swords)));tick=0;if(mode==2)stage=10;else if(mode==1)stage=7;else stage=5;}
            }else if(stage==5){
                if(++tick==1){requested=System.nanoTime();key(mc,LegacyKeyMappings.TOGGLE_MODE,1);}if(tick==2)key(mc,LegacyKeyMappings.TOGGLE_MODE,0);
                if(tick==30)server(mc,()->serverSample=diagnostics(player(mc)));
                if(tick==32 && ready && serverSample!=null)rows.add(Map.of("switch_diagnostics",serverSample,"window_active",mc.isWindowActive(),"mode_key",LegacyKeyMappings.TOGGLE_MODE.getKey().toString()));
                if(tick>2 && LegacyMode.modern(mc.player)==(mode==0)){stage=6;server(mc,()->serverSample=sample(player(mc)));}
                if(tick>180)throw new IllegalStateException("No mode acknowledgement");
            }else if(stage==6 && ready){
                boolean want=mode==0;check(Boolean.valueOf(want).equals(serverSample.get("modern")),"Server ack differs "+serverSample);check(LegacyMode.modern(mc.player)==want,"Client mode mismatch");check(mc.player.getData(CapabilityConcentrationRank.RANK_POINT).getMaxCapacity()==(want?1799:1797),"Client rank scale mismatch");
                rows.add(Map.of("switch_from",mode,"ack_observed_ms",(System.nanoTime()-requested)/1_000_000.0,"server",serverSample));mode++;camera=0;click=0;tick=0;stage=2;server(mc,()->equip(mc));
            }else if(stage==7){
                stage=8;tick=0;sampleRequested=false;serverSample=null;server(mc,()->{var p=player(mc);var s=mc.getSingleplayerServer();if(life<2){var l=s.getLevel(life==0?Level.NETHER:Level.OVERWORLD);for(int x=10;x<=14;x++)for(int z=10;z<=14;z++)l.setBlockAndUpdate(new BlockPos(x,69,z),net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState());p.teleportTo(l,12,70,12,0,0);}else{p.setHealth(0);p.die(p.damageSources().genericKill());}});
            }else if(stage==8 && ready){
                ++tick;if(life==2 && mc.player!=null && mc.player.isDeadOrDying()){mc.player.respawn();mc.setScreen(null);tick=0;}
                if(tick>=45 && !sampleRequested && mc.player!=null && mc.screen==null){sampleRequested=true;server(mc,()->serverSample=sample(player(mc)));}
                if(tick>=47 && ready && serverSample!=null && mc.player!=null && mc.screen==null){var client=sample(mc.player);check(client.equals(serverSample) && LegacyMode.modern(mc.player),"Modern lifecycle "+life+" server "+serverSample+" client "+client);rows.add(Map.of("life",life,"server",serverSample,"client",client));capture="mode-life-"+life+".png";stage=9;}
            }else if(stage==9 && capture==null){if(++life<3)stage=7;else{stage=5;tick=0;}}
            else if(stage==10){check(frames.stream().anyMatch(r->r.get("mode").equals(1)),"No native renderer frames");check(frames.stream().anyMatch(r->r.get("mode").equals(2)),"No restored old renderer frames");write("checks.json",rows);write("frames.json",frames);write("result.json",Map.of("passed",failures.isEmpty(),"failures",failures,"captures",captures,"renderer",com.mojang.blaze3d.platform.GlUtil.getRenderer(),"scope","GUI key request and server ack, two-way modes, combo renders in two views, single projectiles, dimension and death; integrated single client"));done=true;mc.stop();}
        }catch(Throwable e){done=true;try{write("frames.json",frames);write("failure.json",Map.of("error",e.toString(),"stage",stage,"tick",tick,"mode",mode,"checks",rows,"failures",failures));}catch(Exception ignored){}com.mojang.logging.LogUtils.getLogger().error("DUAL_MODE_CLIENT_FAILED",e);mc.stop();}
    }
    @SubscribeEvent public static void bladeFrame(mods.flammpfeil.slashblade.event.client.RenderOverrideEvent e){if(!enabled() || done || stage!=3 || capture==null || !Set.of("blade","sheath").contains(e.getTarget()))return;var mc=Minecraft.getInstance();frames.add(Map.of("mode",mode,"camera",camera,"click",click,"hand",e.getStack()==mc.player.getMainHandItem()?"main":e.getStack()==mc.player.getOffhandItem()?"off":"other","part",e.getTarget(),"pose",e.getPoseStack().last().pose().get(new float[16])));}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(!enabled() || done || capture==null)return;try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out.resolve(capture));captures.add(capture);capture=null;}catch(Exception ex){failure=ex;}}
}
