package org.scex.slashbladelegacy;

import java.util.EnumSet;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.ability.Untouchable;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** r87 AvoidAction: V down with a direction, on the ground or in the air. */
public final class LegacyAvoid {
    // SprintMoveEvent was added after 2.0.5. Honor its cancellation when present.
    // Keep support for SlashBlade versions without this event.
    private static final java.lang.reflect.Constructor<?> SPRINT_EVENT=findSprintEvent();
    private LegacyAvoid() {}
    private static java.lang.reflect.Constructor<?> findSprintEvent(){
        try{return Class.forName("mods.flammpfeil.slashblade.event.ability.SprintMoveEvent").getConstructor(ServerPlayer.class,EnumSet.class);}
        catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException changed){throw new IllegalStateException("Unsupported SprintMoveEvent signature",changed);}
    }
    private static boolean canceled(ServerPlayer player,EnumSet<InputCommand> commands){
        if(SPRINT_EVENT==null)return false;
        try{
            var event=(net.neoforged.bus.api.Event)SPRINT_EVENT.newInstance(player,commands.clone());
            NeoForge.EVENT_BUS.post(event);return ((net.neoforged.bus.api.ICancellableEvent)event).isCanceled();
        }catch(ReflectiveOperationException changed){throw new IllegalStateException("Cannot post SprintMoveEvent",changed);}
    }
    public static void move(ServerPlayer player,EnumSet<InputCommand> commands) {
        if(!player.isAlive() || !(player.getMainHandItem().getItem() instanceof ItemSlashBlade)
                || !commands.contains(InputCommand.SPRINT))return;
        var data=player.getPersistentData();long now=player.level().getGameTime();
        // Preserve r87's strict comparisons, including the future timestamps: 5 ticks
        // between accepted taps and recovery after 21 ticks from the previous dodge.
        if(Math.abs(data.getLong("SB.AvoidTimeout")-now)<=2)return;
        int count=Math.abs(data.getLong("SB.AvoidComboTimeout")-now)>10?0:data.getInt("SB.AvoidCombo");
        if(count>=3)return;
        var input=SlayerStyleArts.getInput(commands);
        if(input.horizontalDistanceSqr()==0)return;
        if(canceled(player,commands))return;
        if(!player.isAlive() || !(player.getMainHandItem().getItem() instanceof ItemSlashBlade))return;
        // Old keyboard input: physical sneak scales axes by .3, item use by .2;
        // living movement damps both axes by .98 before moveFlying sees them.
        // A separately bound Lock-on sets SNEAK in commands without physical sneak.
        float scale=.98f*(player.isShiftKeyDown()?.3f:1f)*(player.isUsingItem()?.2f:1f);
        input=input.scale(scale);
        if(input.lengthSqr()>1)input=input.normalize();
        input=input.scale(player.isShiftKeyDown()?2.8f:.8f);
        float sin=Mth.sin(player.getYRot()*Mth.DEG_TO_RAD),cos=Mth.cos(player.getYRot()*Mth.DEG_TO_RAD);
        var impulse=new Vec3(input.x*cos-input.z*sin,0,input.z*cos+input.x*sin);
        data.putLong("SB.AvoidTimeout",now+2);data.putLong("SB.AvoidComboTimeout",now+10);data.putInt("SB.AvoidCombo",count+1);
        // Clear the pending TrickUp position rollback before applying this dodge.
        // Otherwise, TrickUp can undo the dodge.
        data.remove(SlayerStyleArts.AVOID_COUNTER_PATH);data.remove(SlayerStyleArts.AVOID_VEC_PATH);
        player.setDeltaMovement(player.getDeltaMovement().add(impulse));
        // Send the accepted horizontal increment.
        // A full server velocity can replace the client's current vertical motion.
        // Native avoid counters can roll back move() after two ticks.
        PacketDistributor.sendToPlayer(player,new LegacyAvoidPayload(impulse.x,impulse.z));
        Untouchable.setUntouchable(player,3);
        player.playNotifySound(SoundEvents.FIRE_EXTINGUISH,SoundSource.PLAYERS,.3f,10f);
    }
}
