package org.scex.slashbladelegacy.contracts;

import java.util.*;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import org.scex.slashbladelegacy.*;

/** Public input events, without substituting the movement implementation. */
final class Avoid17Contracts {
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static void check(String name,boolean pass){rows.add(Map.of("case",name,"passed",pass));}
    private static boolean near(double a,double b){return Math.abs(a-b)<.00001;}
    static void run(MinecraftServer server,Map<String,Object> report){
        rows.clear();report.put("avoid_input",rows);
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"AvoidContract"));
        try {
            for(boolean ground:List.of(false,true))for(var direction:List.of(InputCommand.FORWARD,InputCommand.BACK,InputCommand.LEFT,InputCommand.RIGHT)){
                reset(p);p.setOnGround(ground);var start=p.position();press(p,direction);
                var v=p.getDeltaMovement();double x=direction==InputCommand.LEFT?.784:direction==InputCommand.RIGHT?-.784:0;
                double z=direction==InputCommand.FORWARD?.784:direction==InputCommand.BACK?-.784:0;
                check((ground?"ground":"air")+" "+direction,near(v.x,x) && near(v.z,z) && near(v.y,-.2));
                check("no teleport or forced combo "+ground+direction,p.position().equals(start) && !p.getPersistentData().contains("sb.avoid.vec"));
            }
            reset(p);press(p,InputCommand.FORWARD,InputCommand.LEFT);check("diagonal normalized",near(p.getDeltaMovement().horizontalDistance(),.8));
            reset(p);p.setYRot(90);press(p,InputCommand.FORWARD);check("facing rotates forward",near(p.getDeltaMovement().x,-.784) && near(p.getDeltaMovement().z,0));
            reset(p);p.setDeltaMovement(.1,.4,.2);press(p,InputCommand.RIGHT);check("additive with vertical preserved",near(p.getDeltaMovement().x,-.684) && near(p.getDeltaMovement().y,.4) && near(p.getDeltaMovement().z,.2));
            reset(p);p.setShiftKeyDown(true);press(p,InputCommand.BACK,InputCommand.SNEAK);check("sneaking back avoids horizontally instead of trick down",near(p.getDeltaMovement().z,-.8232) && near(p.getDeltaMovement().y,-.2));
            reset(p);press(p,InputCommand.LEFT,InputCommand.SNEAK);check("separate lock key does not apply physical sneak multiplier",near(p.getDeltaMovement().x,.784));
            reset(p);press(p,InputCommand.FORWARD,InputCommand.BACK);check("opposite inputs do not spend dodge",near(p.getDeltaMovement().z,0) && p.getPersistentData().getInt("SB.AvoidCombo")==0);
            reset(p);press(p);check("stationary V does not dodge",p.getDeltaMovement().equals(new Vec3(0,-.2,0)));
            reset(p);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));press(p,InputCommand.FORWARD);check("ordinary sword untouched",near(p.getDeltaMovement().z,0));
            reset(p);press(p,InputCommand.FORWARD);var first=p.getDeltaMovement();input(p,EnumSet.of(InputCommand.SPRINT,InputCommand.LEFT));check("holding V does not repeat when changing direction",p.getDeltaMovement().equals(first));
            reset(p);press(p,InputCommand.FORWARD);for(int i=0;i<4;i++)InputClock.next(p);tap(p,InputCommand.FORWARD);check("r87 strict interval rejects tick four",near(p.getDeltaMovement().z,.784));
            InputClock.next(p);tap(p,InputCommand.FORWARD);check("r87 strict interval accepts tick five",near(p.getDeltaMovement().z,1.568));
            for(int i=0;i<5;i++)InputClock.next(p);tap(p,InputCommand.FORWARD);check("third consecutive dodge",near(p.getDeltaMovement().z,2.352));
            for(int i=0;i<5;i++)InputClock.next(p);tap(p,InputCommand.FORWARD);check("fourth consecutive dodge rejected",near(p.getDeltaMovement().z,2.352));
            for(int i=0;i<15;i++)InputClock.next(p);tap(p,InputCommand.FORWARD);check("combo still capped at tick twenty",near(p.getDeltaMovement().z,2.352));
            InputClock.next(p);tap(p,InputCommand.FORWARD);check("combo recovers at tick twenty one without landing",near(p.getDeltaMovement().z,3.136));
            check("three tick untouchable",p.getData(CapabilityMobEffect.MOB_EFFECT.get()).isUntouchable(p.level().getGameTime()+2) && !p.getData(CapabilityMobEffect.MOB_EFFECT.get()).isUntouchable(p.level().getGameTime()+3));
            reset(p);press(p,InputCommand.FORWARD,InputCommand.SNEAK);check("forward lock keeps native AirTrick route",p.getPersistentData().getInt("SB.AvoidCombo")==0 && p.getPersistentData().contains("sb.avoid.trickup"));
            tap(p,InputCommand.RIGHT);p.setPos(p.getX()+1,p.getY(),p.getZ());var afterTrick=p.position();
            var nativeArts=mods.flammpfeil.slashblade.ability.SlayerStyleArts.getInstance();nativeArts.handleAvoidCounter(p);nativeArts.handleAvoidCounter(p);
            check("dodge after TrickUp cancels prior position rollback",p.position().equals(afterTrick) && p.getPersistentData().getInt("SB.AvoidCombo")==1);
            reset(p);LegacyCompat.LEGACY_COMBAT.set(false);try{press(p,InputCommand.FORWARD);check("disabled config restores upstream air gate",near(p.getDeltaMovement().z,0));}finally{LegacyCompat.LEGACY_COMBAT.set(true);}
        }finally{p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.discard();}
        long failed=rows.stream().filter(r->Boolean.FALSE.equals(r.get("passed"))).count();
        if(failed>0)throw new AssertionError("Avoid contracts failed "+failed+" / "+rows.size());
    }
    private static void reset(ServerPlayer p){
        p.setPos(8,180,8);p.setOnGround(false);p.setShiftKeyDown(false);p.setYRot(0);p.setDeltaMovement(0,-.2,0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SlashBladeItems.SLASHBLADE.get()));p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        for(String key:List.of("SB.AvoidTimeout","SB.AvoidCombo","SB.AvoidComboTimeout","sb.avoid.vec","sb.avoid.counter","sb.avoid.trickup"))p.getPersistentData().remove(key);
        p.getData(CapabilityInputState.INPUT_STATE.get()).getCommands().clear();for(int i=0;i<100;i++)InputClock.next(p);
    }
    private static void tap(ServerPlayer p,InputCommand... directions){input(p,EnumSet.noneOf(InputCommand.class));press(p,directions);}
    private static void press(ServerPlayer p,InputCommand... directions){var set=EnumSet.of(InputCommand.SPRINT);Collections.addAll(set,directions);input(p,set);}
    private static void input(ServerPlayer p,EnumSet<InputCommand> next){var s=p.getData(CapabilityInputState.INPUT_STATE.get());var old=s.getCommands().clone();s.getCommands().clear();s.getCommands().addAll(next);InputCommandEvent.onInputChange(p,s,old,next);}
}
