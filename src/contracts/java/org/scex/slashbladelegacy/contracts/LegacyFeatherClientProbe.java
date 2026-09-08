package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Actual use input and client travel, integrated-server coordinates sampled after movement packets. */
final class LegacyFeatherClientProbe {
    static boolean enabled(){return Boolean.getBoolean("scex.legacy.featherProbe");}
    private static final List<String> BLADES=List.of("slashblade:sange","prinegorerouse:aeon_blade","si_slashblade:legacy/fox_faerie");
    static final int SCREENSHOTS=8;
    private static int test,stage,tick;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile double serverY;
    private static double startY,endY;
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    static Map<String,Object> report(){return Map.of("cases",List.copyOf(rows),"test",test,"stage",stage,"tick",tick);}
    private static void mouse(Minecraft mc,int action){((org.scex.slashbladelegacy.contracts.mixin.ProbeMouseInvoker)mc.mouseHandler).legacyProbe$press(mc.getWindow().getWindow(),1,action,0);}
    static boolean tick(Minecraft mc,java.util.function.Consumer<String> capture) {
        if(failure!=null)throw new IllegalStateException("Feather fixture",failure);
        if(test>=6)return LockOnClientProbe.tick(mc,capture);
        if(++tick>180)throw new IllegalStateException("Feather timeout "+report());
        int bladeIndex=test/2;boolean hover=test%2==1;
        if(!mc.player.isAlive() || mc.player.isDeadOrDying())throw new IllegalStateException("Feather fixture player died: "+report());
        if(stage==0) {
            stage=1;tick=0;ready=false;mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);
            mc.player.setNoGravity(true);mc.player.setDeltaMovement(Vec3.ZERO);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.options.hideGui=false;
            mc.getSingleplayerServer().execute(()->{try{
                var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();
                p.stopUsingItem();p.setGameMode(GameType.SURVIVAL);p.removeAllEffects();p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);
                p.teleportTo(p.serverLevel(),12,310,12,0,0);p.setHealth(p.getMaxHealth());p.experienceLevel=300;
                var blade=s.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements().filter(h->h.key().location().toString().equals(BLADES.get(bladeIndex))).findFirst().orElseThrow().value().getBlade(s.registryAccess());
                blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.enchant(s.registryAccess().holderOrThrow(Enchantments.FEATHER_FALLING),hover?1:4);
                var state=BladeStateAccess.of(blade).orElseThrow();state.setComboSeq(ComboStateRegistry.NONE.getId());state.setBroken(false);state.setSealed(false);state.setOnClick(false);state.setProudSoulCount(10000);blade.setDamageValue(1);
                p.setItemInHand(InteractionHand.MAIN_HAND,blade);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                if(bladeIndex==2){var item=blade.getItem();long capacity=(long)item.getClass().getMethod("getMaxEnergy").invoke(item);item.getClass().getMethod("setEnergy",ItemStack.class,long.class).invoke(item,blade,capacity);}
                p.inventoryMenu.broadcastChanges();ready=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==1 && ready && tick>=25) {
            ready=false;stage=2;tick=0;
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.setNoGravity(false);ready=true;});
        }else if(stage==2 && ready) {
            mc.player.setNoGravity(false);mc.player.setDeltaMovement(Vec3.ZERO);stage=3;tick=0;mouse(mc,1);
        }else if(stage==3) {
            if(mc.player.isNoGravity() || mc.screen!=null)throw new IllegalStateException("Feather travel fixture inactive");
            if(hover){if(tick%4==1)mouse(mc,0);if(tick%4==0)mouse(mc,1);}
            if(tick==20)startY=mc.player.getY();
            if(tick==60) {
                endY=mc.player.getY();double drop=startY-endY;
                if(hover ? Math.abs(drop)>.5 : drop<.7 || drop>1.5)throw new IllegalStateException("Feather "+(hover?"hover":"hold IV")+" unexpected 40-tick drop="+drop+" "+report());
                ready=false;stage=4;tick=0;
                mc.getSingleplayerServer().execute(()->{serverY=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getY();ready=true;});
                capture.accept("10-feather-"+test+".png");
            }
        }else if(stage==4 && ready && tick>=4 && LegacyClientProbe.captured("10-feather-"+test+".png")) {
            if(Math.abs(serverY-endY)>.7)throw new IllegalStateException("Feather server/client position mismatch");
            rows.add(Map.of("blade",BLADES.get(bladeIndex),"mode",hover?"repeated right Feather I":"held right Feather IV","40_tick_drop",startY-endY,"client_y",endY,"server_y",serverY));
            mouse(mc,0);mc.gameMode.releaseUsingItem(mc.player);startY=mc.player.getY();stage=5;tick=0;
        }else if(stage==5 && tick>=(hover?20:80)) {
            // Releasing a charged addon blade legitimately launches its registered SA,
            // which can suspend the player. Let that art finish before testing free fall.
            if(mc.player.isUsingItem() || startY-mc.player.getY()<3)throw new IllegalStateException("Release did not restore falling: drop="+(startY-mc.player.getY())+" using="+mc.player.isUsingItem()+" combo="+BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getComboSeq());
            test++;stage=0;tick=0;
        }
        return false;
    }
}
