package org.scex.slashbladelegacy.contracts;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import org.scex.slashbladelegacy.BladeStateAccess;
import org.scex.slashbladelegacy.LegacyCapabilities;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.client.LegacyKeyMappings;
import org.scex.slashbladelegacy.contracts.mixin.ProbeKeyboardInvoker;

/** Keep the real death screen open before the native respawn request. */
final class DeathClientProbe {
    private static int stage,ticks,screenTicks;
    private static volatile int deadServerTicks,targetId;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static ServerPlayer original;
    private static Mob target;
    private static Consumer<TickEvent.PlayerTickEvent> observer;
    private static final Map<String,Object> report=new LinkedHashMap<>();

    private DeathClientProbe() {}
    static Map<String,Object> report(){return report;}

    static boolean tick(Minecraft mc,String marker,Consumer<String> capture,BooleanSupplier captured) {
        if(failure!=null)throw new IllegalStateException("Death client fixture",failure);
        if(++ticks>240)throw new IllegalStateException("Death client timeout: stage="+stage+", dead_ticks="+deadServerTicks+", screen_ticks="+screenTicks);
        if(stage==0){
            stage=1;ticks=0;
            server(mc,()->{
                var server=mc.getSingleplayerServer();original=player(mc);
                require(!LegacyMode.modern(original),"Death starts in legacy mode");
                var level=original.serverLevel();
                level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(int x=5;x<=11;x++)for(int z=5;z<=12;z++)level.setBlockAndUpdate(new BlockPos(x,159,z),Blocks.SMOOTH_QUARTZ.defaultBlockState());
                original.setGameMode(GameType.SURVIVAL);original.teleportTo(level,8,160,8,0,0);
                original.setRespawnPosition(Level.OVERWORLD,new BlockPos(8,160,8),0,true,false);
                observer=e->{if(e.phase==TickEvent.Phase.END && e.player==original && original.isDeadOrDying())deadServerTicks++;};
                MinecraftForge.EVENT_BUS.addListener(observer);
                original.setHealth(0);original.die(original.damageSources().genericKill());
            });
        }else if(stage==1 && ready && mc.screen instanceof DeathScreen){
            if(++screenTicks>=25 && deadServerTicks>=25){
                report.put("death_screen_client_ticks",screenTicks);report.put("dead_server_ticks",deadServerTicks);
                capture.accept("death-screen.png");stage=2;ticks=0;
            }
        }else if(stage==2 && captured.getAsBoolean()){
            LegacyLifecycleProbe.clickButton(mc,"deathScreen.respawn");
            report.put("native_respawn_button",true);stage=3;ticks=0;
        }else if(stage==3 && mc.player!=null && mc.player.isAlive() && mc.screen==null && ticks>=40){
            stage=4;ticks=0;
            server(mc,()->{
                var player=player(mc);require(player!=original && player.isAlive(),"Respawn uses a new live player");
                require(player.getCapability(CapabilityInputState.INPUT_STATE).isPresent(),"Respawn input is present");
                LegacyCapabilities.rank(player);
                require(marker.equals(player.getMainHandItem().getOrCreateTag().getString("logout_fixture")),"Respawn retains the original blade");
                require(BladeStateAccess.of(player.getMainHandItem()).orElseThrow().getProudSoulCount()==1734,"Respawn retains blade souls");
                player.teleportTo(player.serverLevel(),8,160,8,0,0);
                target=EntityType.PIG.create(player.level());target.setPos(8,160,10.5);target.setNoAi(true);target.setPersistenceRequired();
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(100);
                player.level().addFreshEntity(target);targetId=target.getId();
                report.put("respawn_retained_blade",true);report.put("respawn_new_player",true);
            });
        }else if(stage==4 && ready && ticks>=20 && mc.level.getEntity(targetId)!=null){
            require(mc.player.getMainHandItem().hasTag() && marker.equals(mc.player.getMainHandItem().getTag().getString("logout_fixture")),"Client holds the retained blade");
            mc.gameMode.attack(mc.player,mc.level.getEntity(targetId));mc.player.swing(InteractionHand.MAIN_HAND);
            stage=5;ticks=0;
        }else if(stage==5 && ticks>=12){
            stage=6;ticks=0;
            server(mc,()->{
                float health=target.getHealth();require(health<100,"Retained blade damages the target after respawn: "+health);
                report.put("post_respawn_attack",Map.of("health_before",100,"health_after",health,"entry","MultiPlayerGameMode.attack","passed",true));
                target.discard();target=null;
            });
        }else if(stage==6 && ready){
            GLFW.glfwShowWindow(mc.getWindow().getWindow());GLFW.glfwFocusWindow(mc.getWindow().getWindow());stage=7;ticks=0;
        }else if(stage==7 && ticks>=5 && mc.isWindowActive()){
            require(!LegacyMode.modern(mc.player),"Client mode remains legacy before F8");
            key(mc,GLFW.GLFW_PRESS);stage=8;ticks=0;
        }else if(stage==8){
            key(mc,GLFW.GLFW_RELEASE);stage=9;ticks=0;
        }else if(stage==9 && LegacyMode.modern(mc.player)){
            stage=10;ticks=0;
            server(mc,()->{
                var player=player(mc);require(player.isAlive() && LegacyMode.modern(player),"F8 applies on the server");
                require(!LegacyMode.pending(player) && LegacyCapabilities.rank(player).getMaxCapacity()==1799,"F8 binds the new rank mode");
                report.put("post_respawn_mode",Map.of("input","F8 keyboard press and release","server_modern",true,"client_acknowledged",true,"rank_capacity",1799,"passed",true));
            });
        }else if(stage==10 && ready){
            capture.accept("death-respawn-mode.png");stage=11;ticks=0;
        }else if(stage==11 && captured.getAsBoolean()){
            cleanup(mc);report.put("passed",true);stage=12;
        }
        return stage==12;
    }

    static void cleanup(Minecraft mc){
        key(mc,GLFW.GLFW_RELEASE);
        if(observer!=null){MinecraftForge.EVENT_BUS.unregister(observer);observer=null;}
        if(target!=null && mc.getSingleplayerServer()!=null)mc.getSingleplayerServer().execute(()->{if(target!=null){target.discard();target=null;}});
    }
    private static void server(Minecraft mc,Runnable work){ready=false;mc.getSingleplayerServer().execute(()->{try{work.run();ready=true;}catch(Throwable e){failure=e;}});}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);}
    private static void key(Minecraft mc,int action){
        var key=LegacyKeyMappings.TOGGLE_MODE.getKey();
        require(key.getValue()==GLFW.GLFW_KEY_F8,"Mode key is F8");
        ((ProbeKeyboardInvoker)mc.keyboardHandler).legacyProbe$key(mc.getWindow().getWindow(),key.getValue(),0,action,0);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
