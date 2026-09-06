package org.scex.slashbladelegacy.contracts;

import java.util.Map;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import org.scex.slashbladelegacy.*;
import static org.scex.slashbladelegacy.LegacyMove.*;

final class CombatContracts {
    static void run(ServerPlayer player,Map<String,Object> report) {
        var level=player.serverLevel();
        var saved=player.getMainHandItem();var blade=saved.copy();
        var state=BladeStateAccess.of(blade).orElseThrow();
        state.setComboRoot(ComboStateRegistry.STANDBY.getId());state.setComboSeq(ComboStateRegistry.NONE.getId());
        state.setLastActionTime(level.getGameTime());state.setBroken(false);state.setSealed(false);
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.setOnGround(true);
        player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);
        player.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).getCommands().clear();
        var target=EntityType.ZOMBIE.create(level);target.setPos(24,160,2);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        level.getChunk(1,0);require(level.addFreshEntity(target),"Legacy target spawn");
        var sequence=new ArrayList<String>();
        try {
            int durability=blade.getDamageValue();
            for(var expected:new LegacyMove[]{SAYA1,SAYA2,BATTOU}) {
                float before=target.getHealth();
                blade.getItem().use(level,player,InteractionHand.MAIN_HAND);
                var actual=LegacyCombat.move(state.getComboSeq());sequence.add(actual.name());
                require(actual==expected,"Legacy right combo mismatch: "+actual);
                require(target.getHealth()<before,"Legacy click did not damage immediately: "+expected);
                if(expected.scabbard)require(blade.getDamageValue()==durability,"Scabbard consumed durability");
            }
            require(blade.getDamageValue()==durability+1,"Legacy blade strike must consume exactly one durability");
            require(!state.onClick(),"Reentrant damage flag leaked");
            target.setHealth(2);target.invulnerableTime=0;
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            blade.getItem().use(level,player,InteractionHand.MAIN_HAND);
            require(target.getHealth()==1,"Scabbard nonlethal floor");
            blade.getItem().use(level,player,InteractionHand.MAIN_HAND);
            require(target.getHealth()==1,"Repeated scabbard killed target");
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            target.setHealth(20);target.hurtDuration=10;target.hurtTime=5;
            ((mods.flammpfeil.slashblade.item.ItemSlashBlade)blade.getItem()).onLeftClickEntity(blade,player,target);
            require(state.getComboSeq().equals(ComboStateRegistry.NONE.getId()),"Left click accepted before tick6");
            target.hurtTime=4;
            ((mods.flammpfeil.slashblade.item.ItemSlashBlade)blade.getItem()).onLeftClickEntity(blade,player,target);
            require(LegacyCombat.move(state.getComboSeq())==KIRIAGE,"Left click tick6 rejected");
            require(target.getDeltaMovement().y==.6,"Uppercut velocity");
            var saya=ComboStateRegistry.REGISTRY.get(LegacyCombat.id(SAYA1));
            require(saya.getTimeoutMS()==1000,"20tick combo reset");
            require(LegacyCombat.next(SAYA2,true,true,false,false,false,false,false,false,5,8)==S_IAI,"Rank5 tick8 branch");
            require(LegacyCombat.next(SAYA2,true,true,false,false,false,false,false,false,5,9)==BATTOU,"Rank5 tick9 branch");
            require(LegacyCombat.next(A_KIRIOROSI,true,false,false,false,false,false,false,false,0,7)==BATTOU,"Air tick7 branch");
            require(LegacyCombat.next(A_KIRIOROSI,true,false,false,false,false,false,false,false,0,8)==A_KIRIAGE,"Air tick8 branch");
            require(LegacyCombat.next(NONE,true,false,true,true,false,true,false,false,0,0)==CALIBUR,"Buffered Calibur");
            require(LegacyCombat.next(NONE,true,false,true,true,false,true,true,false,0,0)==HELM_BRAKER,"Repeated Calibur gate");
            target.setHealth(20);target.invulnerableTime=0;
            var input=player.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE);
            input.getCommands().addAll(java.util.EnumSet.of(mods.flammpfeil.slashblade.util.InputCommand.SNEAK,mods.flammpfeil.slashblade.util.InputCommand.FORWARD));
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            blade.getItem().use(level,player,InteractionHand.MAIN_HAND);
            require(LegacyCombat.move(state.getComboSeq())==RAPID_SLASH,"Forward sneak rapid slash");
            require(Math.abs(player.getDeltaMovement().z-2.5)<.0001,"Rapid slash initial velocity");
            float beforeRapid=target.getHealth();
            LegacyCombat.tickMotion(player,level.getGameTime()+3);
            require(target.getHealth()<beforeRapid,"Rapid slash tick3 damage");
            float afterRapid=target.getHealth();LegacyCombat.tickMotion(player,level.getGameTime()+3);
            require(target.getHealth()==afterRapid,"Repeated tick duplicated rapid damage");
            player.setItemInHand(InteractionHand.MAIN_HAND,saved);
            LegacyCombat.tickMotion(player,level.getGameTime()+6);
            require(target.getHealth()==afterRapid,"Swapped blade kept scheduled damage");
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            player.setOnGround(false);
            input.getLastPressTimes().put(mods.flammpfeil.slashblade.util.InputCommand.BACK,level.getGameTime());
            state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(level.getGameTime());
            blade.getItem().use(level,player,InteractionHand.MAIN_HAND);
            require(LegacyCombat.move(state.getComboSeq())==CALIBUR,"Actual buffered Calibur input");
            double beforeZ=player.getZ();double beforeY=player.getY();
            LegacyCombat.tickMotion(player,level.getGameTime()+1);
            require(Math.abs(player.getZ()-beforeZ-1.5)<.001 && player.getY()==beforeY,"Calibur horizontal movement and height hold");
            player.setOnGround(true);LegacyCombat.tickMotion(player,level.getGameTime()+2);
            double landedZ=player.getZ();LegacyCombat.tickMotion(player,level.getGameTime()+3);
            require(player.getZ()==landedZ,"Calibur continued after landing");
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            LegacyCombat.hold(player,KIRIAGE,2);require(player.onGround(),"Uppercut jumped early");
            LegacyCombat.hold(player,KIRIAGE,3);
            require(!player.onGround() && Math.abs(player.getDeltaMovement().y-.62)<.001,"Uppercut hold tick3 jump");
            player.setOnGround(true);player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            LegacyCombat.hold(player,RAPID_SLASH,7);
            require(LegacyCombat.move(state.getComboSeq())==RISING_STAR && !player.onGround(),"Rapid hold tick7 RisingStar");
            state.updateComboSeq(player,LegacyCombat.id(HELM_BRAKER));
            double helmY=player.getY();
            LegacyCombat.tickMotion(player,level.getGameTime()+1);require(player.getY()==helmY,"Helm moved before tick2");
            LegacyCombat.tickMotion(player,level.getGameTime()+2);
            require(Math.abs(player.getY()-(helmY-1.5))<.001,"Helm descent");
            player.setOnGround(true);LegacyCombat.tickMotion(player,level.getGameTime()+3);
            for(var tested:LegacyMove.values()) for(float phase:new float[]{0,.5f,1}) {
                var matrix=LegacyBladePose.matrix(tested,LegacyBladePose.progress(tested,phase),false);
                require(matrix.isFinite() && Math.abs(matrix.determinant())>1e-9,"Invalid original pose matrix "+tested);
            }
            require(LegacyBladePose.matrix(SAYA1,.5f,false).equals(LegacyBladePose.matrix(SAYA1,.5f,true)),"Scabbard and blade separated during saya");
            require(LegacyBladePose.matrix(BATTOU,0,true).equals(LegacyBladePose.matrix(BATTOU,1,true)),"Sheath moved with drawn blade");
            report.put("hold_jump_helm_pose",true);
            report.put("legacy_combat",Map.of("right_sequence",sequence,"immediate_damage",true,"nonlethal_scabbard",true,
                    "scabbard_durability",true,"left_tick6_gate",true,"rank_air_branches",true,"reset_20ticks",true,
                    "rapid_tick_swap_guard",true,"calibur_movement_landing",true));
        }finally{target.discard();player.setItemInHand(InteractionHand.MAIN_HAND,saved);player.setPos(0,160,0);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
