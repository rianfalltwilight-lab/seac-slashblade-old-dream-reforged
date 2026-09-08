package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.SlashBladeKeyMappings;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.client.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import org.scex.slashbladelegacy.*;

/** Real KeyboardHandler -> key mapping -> MoveCommandMessage -> server release and entity ticks. */
final class LegacySuperClientProbe {
    static boolean enabled(){return Boolean.getBoolean("scex.legacy.superProbe");}
    static final int SCREENSHOTS=10;
    private static final List<String> BLADES=List.of("slashblade:sange","prinegorerouse:aeon_blade","si_slashblade:legacy/fox_faerie");
    private static int test,stage,tick;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile int managers,damage,maximum,souls,stun;
    private static volatile float health;
    private static volatile boolean pressed;
    private static volatile net.minecraft.world.entity.monster.Husk target;
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    static Map<String,Object> report(){return Map.of("cases",List.copyOf(rows),"test",test,"stage",stage,"tick",tick);}
    private static void key(Minecraft mc,int action){((org.scex.slashbladelegacy.contracts.mixin.ProbeKeyboardInvoker)mc.keyboardHandler).legacyProbe$key(mc.getWindow().getWindow(),SlashBladeKeyMappings.KEY_SPECIAL_MOVE.getKey().getValue(),0,action,0);}
    static void release(Minecraft mc){if(mc.player!=null)key(mc,0);}
    private static void sample(Minecraft mc){
        ready=false;mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var blade=p.getMainHandItem();
            managers=p.level().getEntitiesOfClass(LegacyArtEntity.class,p.getBoundingBox().inflate(40),e->!e.isRemoved() && e.mode()==LegacyArtEntity.Mode.JUDGEMENT).size();
            damage=blade.getDamageValue();maximum=blade.getMaxDamage();souls=BladeStateAccess.of(blade).orElseThrow().getProudSoulCount();
            health=target.getHealth();var slow=target.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);stun=slow==null?-1:slow.getAmplifier();
            pressed=p.getData(CapabilityInputState.INPUT_STATE.get()).getCommands().contains(InputCommand.SPRINT);ready=true;
        }catch(Throwable e){failure=e;}});
    }
    static boolean tick(Minecraft mc,java.util.function.Consumer<String> capture){
        if(failure!=null)throw new IllegalStateException("Super fixture",failure);
        if(test==BLADES.size())return true;
        if(++tick>180)throw new IllegalStateException("Super timeout "+report());
        if(stage==0){
            if(Arrays.stream(mc.options.keyMappings).noneMatch(k->k==SlashBladeKeyMappings.KEY_SPECIAL_MOVE))throw new IllegalStateException("Special action missing in Controls");
            release(mc);mc.options.keyShift.setDown(false);mc.options.keyUse.setDown(false);mc.options.keyUp.setDown(false);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.fallDistance=0;
            mc.options.setCameraType(CameraType.FIRST_PERSON);stage=1;tick=0;ready=false;
            mc.getSingleplayerServer().execute(()->{try{
                var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();
                if(target!=null)target.discard();for(var e:p.level().getEntitiesOfClass(LegacyArtEntity.class,p.getBoundingBox().inflate(60)))e.discard();
                p.stopUsingItem();p.removeAllEffects();p.setGameMode(GameType.SURVIVAL);p.setNoGravity(false);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.teleportTo(p.serverLevel(),12,71,12,0,0);p.setHealth(p.getMaxHealth());
                var blade=s.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(h->h.key().location().toString().equals(BLADES.get(test))).findFirst().orElseThrow().value().getBlade(s.registryAccess());
                blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.enchant(s.registryAccess().holderOrThrow(Enchantments.POWER),1);
                var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(ComboStateRegistry.NONE.getId());state.setBroken(false);state.setSealed(false);state.setKillCount(1000);state.setProudSoulCount(1000);state.setSpecialEffects(new net.minecraft.nbt.ListTag());blade.setDamageValue(0);
                if(test==2){var item=blade.getItem();long capacity=(long)item.getClass().getMethod("getMaxEnergy").invoke(item);item.getClass().getMethod("setEnergy",ItemStack.class,long.class).invoke(item,blade,capacity);}
                p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                target=EntityType.HUSK.create(p.level());target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.setNoAi(true);target.setPos(12,71,16);p.level().addFreshEntity(target);p.inventoryMenu.broadcastChanges();ready=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==1 && ready && tick>=25){key(mc,1);stage=2;tick=0;}
        else if(stage==2 && tick==35){sample(mc);stage=3;tick=0;}
        else if(stage==3 && ready){
            if(!pressed || managers!=0 || damage!=0 || health!=1000)throw new IllegalStateException("V hold auto fired / input absent: "+managers+" damage="+damage+" pressed="+pressed);
            capture.accept("12-super-"+test+"-charge.png");stage=4;tick=0;
        }else if(stage==4 && LegacyClientProbe.captured("12-super-"+test+"-charge.png")){
            key(mc,0);stage=5;tick=0;
        }else if(stage==5 && tick==8){sample(mc);stage=6;tick=0;}
        else if(stage==6 && ready){
            if(pressed || managers!=1 || stun!=30 || damage!=0 || souls!=1000)throw new IllegalStateException("V release did not launch old Super: "+managers+" stun="+stun+" damage="+damage);
            capture.accept("12-super-"+test+"-release.png");stage=7;tick=0;
        }else if(stage==7 && tick==40){sample(mc);stage=8;tick=0;}
        else if(stage==8 && ready){
            // Five drives and two dimension fields can still hit after the manager sets
            // half wear at tick 30. Exact tick-30 assignment is covered by Super17Contracts.
            if(managers!=0 || health>=1000 || damage<maximum/2 || damage-maximum/2>12)throw new IllegalStateException("Super hit/finish failure: health="+health+" damage="+damage+" / "+maximum);
            rows.add(Map.of("blade",BLADES.get(test),"holding_35_ticks_no_attack",true,"release_stun_amplifier",30,"final_target_hp",health,"final_damage",damage,"maximum",maximum,"post_manager_wear",damage-maximum/2,"final_souls",souls,"raw_keyboard_packets",true));
            capture.accept("12-super-"+test+"-finish.png");stage=9;tick=0;
        }else if(stage==9 && LegacyClientProbe.captured("12-super-"+test+"-finish.png")){test++;stage=0;tick=0;}
        return false;
    }
}
