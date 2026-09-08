package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.SlashBladeKeyMappings;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Matched no-V / V travel through real key mappings and both network directions. */
final class LegacyAvoidClientProbe {
    static boolean enabled(){return Boolean.getBoolean("scex.legacy.avoidProbe");}
    static final int SCREENSHOTS=18; // title, eight dodge cases, nine Super regression views
    private record Case(String blade,int strafe,int forward,boolean sneak,boolean ground,boolean wall){}
    private static final List<Case> CASES=List.of(
        new Case("slashblade:sange",0,1,false,false,false),new Case("slashblade:sange",0,-1,false,false,false),
        new Case("prinegorerouse:aeon_blade",1,0,false,false,false),new Case("si_slashblade:legacy/fox_faerie",-1,0,false,false,false),
        new Case("slashblade:sange",1,1,false,false,false),new Case("slashblade:sange",0,-1,true,false,false),
        new Case("slashblade:sange",0,1,false,true,false),new Case("slashblade:sange",0,1,false,true,true));
    private static int test,stage,tick,trial;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile int count;
    private static volatile Vec3 serverPosition;
    private static Vec3 start;
    private static double peak,minimumY,baseline,baselineDistance;
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    static Map<String,Object> report(){return Map.of("cases",List.copyOf(rows),"test",test,"trial",trial,"stage",stage,"tick",tick,"peak",peak);}
    private static void key(Minecraft mc,KeyMapping key,int action){((org.scex.slashbladelegacy.contracts.mixin.ProbeKeyboardInvoker)mc.keyboardHandler).legacyProbe$key(mc.getWindow().getWindow(),key.getKey().getValue(),0,action,0);}
    static void release(Minecraft mc){if(mc.player!=null)for(var k:List.of(SlashBladeKeyMappings.KEY_SPECIAL_MOVE,mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyShift))key(mc,k,0);}
    static boolean tick(Minecraft mc,java.util.function.Consumer<String> capture){
        if(failure!=null)throw new IllegalStateException("Avoid fixture",failure);
        if(test==CASES.size())return true;
        if(++tick>160)throw new IllegalStateException("Avoid timeout "+report());
        var c=CASES.get(test);
        if(stage==0){
            release(mc);mc.options.keyUse.setDown(false);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.fallDistance=0;
            stage=1;tick=0;ready=false;mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.getSingleplayerServer().execute(()->{try{
                var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();var l=p.serverLevel();
                for(int x=-10;x<=34;x++)for(int z=-10;z<=34;z++)l.setBlockAndUpdate(new BlockPos(x,70,z),Blocks.SMOOTH_QUARTZ.defaultBlockState());
                for(int x=8;x<=16;x++)for(int y=71;y<=75;y++)l.setBlockAndUpdate(new BlockPos(x,y,15),(c.wall?Blocks.GLASS:Blocks.AIR).defaultBlockState());
                p.stopUsingItem();p.removeAllEffects();p.setGameMode(GameType.SURVIVAL);p.setNoGravity(false);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.setHealth(p.getMaxHealth());p.teleportTo(l,12,c.ground?71:140,12,0,0);
                var blade=s.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(h->h.key().location().toString().equals(c.blade)).findFirst().orElseThrow().value().getBlade(s.registryAccess());
                blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(ComboStateRegistry.NONE.getId());state.setBroken(false);state.setSealed(false);state.setKillCount(0);state.setSpecialEffects(new net.minecraft.nbt.ListTag());blade.setDamageValue(0);
                p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                for(String k:List.of("SB.AvoidTimeout","SB.AvoidCombo","SB.AvoidComboTimeout"))p.getPersistentData().remove(k);
                p.inventoryMenu.broadcastChanges();ready=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==1 && ready && tick>=12){
            if(c.strafe!=0)key(mc,c.strafe>0?mc.options.keyLeft:mc.options.keyRight,1);
            if(c.forward!=0)key(mc,c.forward>0?mc.options.keyUp:mc.options.keyDown,1);
            if(c.sneak)key(mc,mc.options.keyShift,1);
            stage=2;tick=0;
        }else if(stage==2 && tick==3){
            start=mc.player.position();peak=0;minimumY=0;
            if(trial==1)key(mc,SlashBladeKeyMappings.KEY_SPECIAL_MOVE,1);
            stage=3;tick=0;
        }else if(stage==3){
            peak=Math.max(peak,mc.player.getDeltaMovement().horizontalDistance());minimumY=Math.min(minimumY,mc.player.getDeltaMovement().y);
            if(tick==5)key(mc,SlashBladeKeyMappings.KEY_SPECIAL_MOVE,0);
            if(tick==10){
                release(mc);stage=4;tick=0;ready=false;
                mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();count=p.getPersistentData().getInt("SB.AvoidCombo");serverPosition=p.position();ready=true;}catch(Throwable e){failure=e;}});
            }
        }else if(stage==4 && ready){
            double distance=mc.player.position().subtract(start).horizontalDistance();
            if(trial==0){if(count!=0)throw new IllegalStateException("Control unexpectedly dodged");baseline=peak;baselineDistance=distance;trial=1;stage=0;tick=0;}
            else {
                if(count!=1 || (!c.wall && peak<baseline+.35) || (!c.ground && (minimumY>=-.2 || mc.player.onGround())))throw new IllegalStateException("Dodge missing / vertical travel wrong: "+report()+" baseline="+baseline+" count="+count+" vy="+minimumY);
                if(c.wall && (mc.player.getZ()>14.71 || serverPosition.z>14.71))throw new IllegalStateException("Dodge passed through wall");
                rows.add(Map.of("blade",c.blade,"case",test,"server_accepted",count,"control_peak",baseline,"dodge_peak",peak,"control_distance",baselineDistance,"dodge_distance",distance,"min_vertical_speed",minimumY,"wall_collision",c.wall,"raw_keyboard",true));
                capture.accept("13-avoid-"+test+".png");stage=5;tick=0;
            }
        }else if(stage==5 && LegacyClientProbe.captured("13-avoid-"+test+".png")){test++;trial=0;stage=0;tick=0;}
        return false;
    }
}
