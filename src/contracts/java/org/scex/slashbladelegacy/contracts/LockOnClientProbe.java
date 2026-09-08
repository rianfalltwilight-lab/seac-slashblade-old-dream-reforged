package org.scex.slashbladelegacy.contracts;

import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.client.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.scex.slashbladelegacy.client.LegacyKeyMappings;

final class LockOnClientProbe {
    private static int stage,tick;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile int targetId,serverTarget;
    private static volatile boolean serverCrouching;
    private static InputConstants.Key originalKey;
    static boolean tick(Minecraft mc,java.util.function.Consumer<String> capture) {
        if(failure!=null)throw new IllegalStateException("Lock-on fixture",failure);
        if(++tick>140)throw new IllegalStateException("Lock-on timeout stage="+stage);
        if(stage==0) {
            if(Arrays.stream(mc.options.keyMappings).noneMatch(k->k==LegacyKeyMappings.LOCK_ON))throw new IllegalStateException("Lock-on missing from Controls");
            originalKey=LegacyKeyMappings.LOCK_ON.getKey();LegacyKeyMappings.LOCK_ON.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));KeyMapping.resetMapping();
            mc.options.keyShift.setDown(false);mc.player.fallDistance=0;mc.player.setDeltaMovement(Vec3.ZERO);stage=1;tick=0;ready=false;
            mc.getSingleplayerServer().execute(()->{try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.stopUsingItem();p.setNoGravity(false);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.teleportTo(p.serverLevel(),12,71,12,0,0);
                var target=EntityType.HUSK.create(p.level());target.setPos(12,71,16);target.setNoAi(true);p.level().addFreshEntity(target);targetId=target.getId();ready=true;
            }catch(Throwable e){failure=e;}});
        }else if(stage==1 && ready && tick>=25) {
            KeyMapping.set(LegacyKeyMappings.LOCK_ON.getKey(),true);stage=2;tick=0;
        }else if(stage==2 && tick>=15) {
            var target=BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getTargetEntity(mc.level);
            if(target==null || target.getId()!=targetId || mc.player.isCrouching())throw new IllegalStateException("Rebound key did not independently lock target");
            stage=3;tick=0;ready=false;
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var t=BladeStateAccess.of(p.getMainHandItem()).orElseThrow().getTargetEntity(p.level());serverTarget=t==null?-1:t.getId();serverCrouching=p.isCrouching();ready=true;});
            capture.accept("11-lock-on.png");
        }else if(stage==3 && ready && LegacyClientProbe.captured("11-lock-on.png")) {
            if(serverTarget!=targetId || serverCrouching)throw new IllegalStateException("Server lock-on mismatch");
            KeyMapping.set(LegacyKeyMappings.LOCK_ON.getKey(),false);stage=4;tick=0;
        }else if(stage==4 && tick>=15) {
            if(BladeStateAccess.of(mc.player.getMainHandItem()).orElseThrow().getTargetEntity(mc.level)!=null)throw new IllegalStateException("Lock-on release stuck");
            LegacyKeyMappings.LOCK_ON.setKey(originalKey);KeyMapping.resetMapping();return true;
        }
        return false;
    }
}
