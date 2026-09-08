package org.scex.slashbladelegacy.contracts;

import java.util.*;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.scex.slashbladelegacy.*;

final class AnimationContracts {
    static void run(ServerPlayer player,Map<String,Object> report){
        var state=BladeStateAccess.of(player.getMainHandItem()).orElseThrow();
        player.setOnGround(false);player.setDeltaMovement(0,.6,0);
        state.updateComboSeq(player,LegacyCombat.id(LegacyMove.HELM_BRAKER));
        require(player.getDeltaMovement().y==-1.5,"Helm retained upward velocity on activation");
        player.setOnGround(true);InputClock.next(player);LegacyCombat.tickMotion(player,player.level().getGameTime());
        require(state.getComboSeq().equals(LegacyCombat.id(LegacyMove.HELM_LANDING)),"Landing waited for airborne timeout");
        require(state.getLastActionTime()==player.level().getGameTime(),"Landing inherited old animation clock");
        state.setComboSeq(LegacyCombat.id(LegacyMove.HELM_BRAKER));state.setLastActionTime(player.level().getGameTime()-12);
        mods.flammpfeil.slashblade.event.handler.FallHandler.resetState(player);
        require(state.getComboSeq().equals(LegacyCombat.id(LegacyMove.HELM_LANDING)) && state.getLastActionTime()==player.level().getGameTime(),"Native fall handler bypassed landing clock");
        require(LegacyCombat.slashRoll(LegacyMove.BATTOU)==0,"Horizontal slash plane wrong");
        require(Math.abs(LegacyCombat.slashRoll(LegacyMove.HELM_BRAKER))%180==90,"Vertical slash plane wrong");
        var drive=new mods.flammpfeil.slashblade.entity.EntityDrive(mods.flammpfeil.slashblade.RegistryEvents.Drive,player.level());
        try{drive.setKnockBack(null);var tag=new net.minecraft.nbt.CompoundTag();drive.addAdditionalSaveData(tag);
            drive.readAdditionalSaveData(tag);require(drive.getKnockBack()==mods.flammpfeil.slashblade.util.KnockBacks.cancel,"Null drive knockback did not roundtrip");
        }finally{drive.discard();}
        for(var move:LegacyMove.values())if(move!=LegacyMove.NONE)require(ComboStateRegistry.REGISTRY.get(LegacyCombat.id(move)).getStartFrame()==0 && ComboStateRegistry.REGISTRY.get(LegacyCombat.id(move)).getEndFrame()==0,"Legacy state still selects a VMD clip "+move);
        report.put("dev11_animation_motion",Map.of("immediate_helm_descent",true,"landing_clock",true,"native_fall_handler",true,"horizontal_roll",0,"vertical_roll",270,"null_drive_save",true));
        state.setComboSeq(ComboStateRegistry.NONE.getId());
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

