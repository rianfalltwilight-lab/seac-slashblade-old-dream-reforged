package org.scex.slashbladelegacy;

import java.util.EnumSet;
import java.util.Optional;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** r87 ItemSlashBlade.onUpdate lock-on. The SNEAK command is the lock-on input. */
public final class LegacyLockOn {
    private LegacyLockOn() {}
    /** Replace the upstream press and release rule in legacy mode. */
    public static void input(ServerPlayer player,EnumSet<InputCommand> old,EnumSet<InputCommand> current) {
        boolean before=old.contains(InputCommand.SNEAK),after=current.contains(InputCommand.SNEAK);
        if(before==after)return;
        var state=BladeStateAccess.of(player.getMainHandItem()).orElse(null);
        if(state==null)return;
        // r87 clears the lock when the lock-on input ends.
        // Test the stored ID. An unloaded or removed target still leaves an ID.
        if(!after) {
            if(locked(state))state.setTargetEntityId((Entity)null);
            return;
        }
        acquire(player,state);
    }
    /** r87 searches on each tick while the input is held and no live target is locked. */
    public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator() || !LegacyMode.legacy(player))return;
        if(!player.getData(CapabilityInputState.INPUT_STATE).getCommands().contains(InputCommand.SNEAK))return;
        var state=BladeStateAccess.of(player.getMainHandItem()).orElse(null);
        if(state==null)return;
        var current=state.getTargetEntity(player.level());
        if(current!=null && current.isAlive())return;
        acquire(player,state);
    }
    private static void acquire(ServerPlayer player,ISlashBladeState state) {
        // Manual lock: any creature under the crosshair, up to 20 blocks.
        LivingEntity target=ray(player,20,0,false);
        // Calibrated lock: an attackable, visible target near the crosshair, up to 10 blocks.
        if(target==null)target=ray(player,10,5,true);
        // Automatic lock: the nearest attackable, visible target in the 10 x 5 x 10 area.
        if(target==null)target=nearest(player);
        // Do not write an unchanged empty lock on each tick.
        if(target!=null || locked(state))state.setTargetEntityId(target);
    }
    /** SlashBlade stores -1 when no entity is locked. */
    private static boolean locked(ISlashBladeState state) { return state.getTargetEntityId()>=0; }
    /** r87 getRayTrace. The first block limits the ray. A border above zero enables the attackable filter. */
    private static LivingEntity ray(Player player,double reach,double border,boolean attackable) {
        var start=player.getEyePosition();var look=player.getLookAngle();var end=start.add(look.scale(reach));
        var block=player.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        double nearest=block.getType()==HitResult.Type.MISS?reach*reach:start.distanceToSqr(block.getLocation());
        LivingEntity result=null;
        for(var entity:player.level().getEntities(player,player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1+border),Entity::isPickable)) {
            var bounds=entity.getBoundingBox().inflate(entity.getPickRadius()+border);
            var hit=bounds.contains(start)?Optional.of(start):bounds.clip(start,end);
            if(hit.isEmpty())continue;
            double distance=start.distanceToSqr(hit.get());
            if(distance>nearest)continue;
            // Test the cheap intersection first. Then test the target rules and line of sight.
            var living=(entity instanceof PartEntity<?> part?part.getParent():entity) instanceof LivingEntity e?e:null;
            if(living==null || !(attackable?LegacyTargets.attackable(player,living) && player.hasLineOfSight(living):lockable(player,living)))continue;
            result=living;nearest=distance;
        }
        return result;
    }
    /** The manual lock accepts any living entity. It does not make the entity attackable. */
    private static boolean lockable(Player player,LivingEntity living) {
        return living!=player && living.isAlive() && !living.isSpectator() && !(living instanceof ArmorStand)
                && !living.hasPassenger(passenger->passenger==player);
    }
    private static LivingEntity nearest(Player player) {
        LivingEntity result=null;double nearest=Double.MAX_VALUE;
        for(var living:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(10,5,10),e->e!=player)) {
            double distance=player.distanceToSqr(living);
            if(distance>=nearest || !LegacyTargets.attackable(player,living) || !player.hasLineOfSight(living))continue;
            result=living;nearest=distance;
        }
        return result;
    }
}
