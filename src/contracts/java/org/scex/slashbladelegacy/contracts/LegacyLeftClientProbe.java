package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Raw mouse-key click -> Minecraft picking -> attack packet -> real server player damage. */
final class LegacyLeftClientProbe {
    static boolean enabled(){return Boolean.getBoolean("scex.legacy.leftProbe");}
    private static final List<String> BLADES=List.of("slashblade:sange","prinegorerouse:aeon_blade","si_slashblade:legacy/fox_faerie","minecraft:diamond_sword");
    static final int SCREENSHOTS=1+BLADES.size()*3;
    private static final List<String> inputTrace=new ArrayList<>();
    private static boolean observing;
    private static int test,stage,tick,click;
    private static volatile boolean prepared,pending;
    private static volatile Throwable failure;
    private static volatile Map<String,Object> snapshot=Map.of();
    private static UUID targetId;
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static int attackEvents,incomingEvents;
    private static java.util.function.Consumer<AttackEntityEvent> attack;
    private static java.util.function.Consumer<LivingIncomingDamageEvent> incoming;
    static Map<String,Object> report(){return Map.of("cases",List.copyOf(rows),"last_snapshot",snapshot,"test",test,"stage",stage,"click",click,"input_trace",List.copyOf(inputTrace));}
    static boolean tick(Minecraft mc,java.util.function.Consumer<String> capture){
        if(!observing){
            observing=true;
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre.class,e->{if(e.getButton()==0)inputTrace.add("mouse test="+test+" click="+click+" action="+e.getAction()+" canceled="+e.isCanceled());});
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered.class,e->{if(e.isAttack())inputTrace.add("attack test="+test+" click="+click+" canceled="+e.isCanceled()+" swing="+e.shouldSwingHand());});
        }
        if(failure!=null)throw new IllegalStateException("Left fixture",failure);
        if(test>=BLADES.size())return true;
        if(++tick>120)throw new IllegalStateException("Raw left timeout: "+report());
        if(stage==0){
            stage=1;tick=0;prepared=false;click=0;snapshot=Map.of();mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;
            mc.options.keyUse.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyShift.setDown(false);mc.setCameraEntity(mc.player);
            mc.getSingleplayerServer().execute(()->{try{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().getFirst();
                for(var e:p.level().getEntities(p,new AABB(0,65,0,40,90,40),e->!(e instanceof net.minecraft.world.entity.player.Player)))e.discard();
                p.setGameMode(GameType.SURVIVAL);p.teleportTo(p.serverLevel(),12,71,12,0,0);p.stopUsingItem();p.removeAllEffects();p.experienceLevel=300;p.setHealth(p.getMaxHealth());
                var blade=test==3?new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD):server.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(h->h.key().location().toString().equals(BLADES.get(test))).findFirst().orElseThrow().value().getBlade(server.registryAccess());
                BladeStateAccess.of(blade).ifPresent(state->{state.setComboSeq(ComboStateRegistry.NONE.getId());state.setOnClick(false);state.setBroken(false);state.setSealed(false);state.setDamage(1);state.setProudSoulCount(10000);});
                p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                if(test==2){var item=blade.getItem();long capacity=(long)item.getClass().getMethod("getMaxEnergy").invoke(item);item.getClass().getMethod("setEnergy",ItemStack.class,long.class).invoke(item,blade,capacity);}
                var target=EntityType.HUSK.create(p.level());target.setPos(12,71,14.5);target.setNoAi(true);target.setPersistenceRequired();target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(1000);
                p.level().addFreshEntity(target);targetId=target.getUUID();attackEvents=0;incomingEvents=0;
                if(attack==null){attack=e->{if(e.getTarget().getUUID().equals(targetId))attackEvents++;};incoming=e->{if(e.getEntity().getUUID().equals(targetId))incomingEvents++;};NeoForge.EVENT_BUS.addListener(attack);NeoForge.EVENT_BUS.addListener(incoming);}
                p.inventoryMenu.broadcastChanges();prepared=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==1 && prepared && tick>=25){
            if(!(mc.hitResult instanceof EntityHitResult hit) || !hit.getEntity().getUUID().equals(targetId))throw new IllegalStateException("Mouse is not over target: "+mc.hitResult);
            stage=2;tick=0;
        }else if(stage==2){
            if(tick==1 || tick==2){
                inputTrace.add("before test="+test+" click="+click+" tick="+tick+" screen="+mc.screen+" busy="+mc.player.isHandsBusy()+" using="+mc.player.isUsingItem()+" key="+mc.options.keyAttack.getKey().getName());
                ((org.scex.slashbladelegacy.contracts.mixin.ProbeMouseInvoker)mc.mouseHandler).legacyProbe$press(mc.getWindow().getWindow(),0,tick==1?1:0,0);
            }
            if(tick>=8 && !pending){pending=true;mc.getSingleplayerServer().execute(()->{try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var target=(LivingEntity)p.serverLevel().getEntity(targetId);var s=BladeStateAccess.of(p.getMainHandItem()).orElse(null);
                snapshot=Map.of("blade",BLADES.get(test),"click",click,"health",target.getHealth(),"hurt_time",target.hurtTime,"combo",s==null?"vanilla":s.getComboSeq().toString(),"on_click",s!=null && s.onClick(),"attack_events",attackEvents,"incoming_events",incomingEvents,"input",p.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).getCommands().toString());
            }catch(Throwable e){failure=e;}finally{pending=false;}});}
            if(tick>=10 && !snapshot.isEmpty() && ((Number)snapshot.get("click")).intValue()==click){
                float last=click==0?1000:((Number)rows.getLast().get("health")).floatValue();
                if(((Number)snapshot.get("health")).floatValue()>=last)throw new IllegalStateException("Raw left did not damage: "+report());
                var clientTarget=mc.level.getEntity(((LivingEntity)java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->e.getUUID().equals(targetId)).findFirst().orElseThrow()).getId());
                if(((LivingEntity)clientTarget).getHealth()!=((Number)snapshot.get("health")).floatValue())return false;
                rows.add(new LinkedHashMap<>(snapshot));capture.accept("09-left-"+test+"-"+click+".png");stage=3;tick=0;
            }
        }else if(stage==3 && LegacyClientProbe.captured("09-left-"+test+"-"+click+".png") && tick>=8){
            if(++click<3){stage=2;tick=0;snapshot=Map.of();}else{test++;stage=0;tick=0;if(test==BLADES.size()){NeoForge.EVENT_BUS.unregister(attack);NeoForge.EVENT_BUS.unregister(incoming);}}
        }
        return test>=BLADES.size();
    }
}
