package org.scex.slashbladelegacy.mixin;
import mods.flammpfeil.slashblade.event.Scheduler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.timers.TimerQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Scheduler.class,remap=false)
public interface LegacySchedulerAccessor {
    @Accessor("queue") TimerQueue<LivingEntity> legacy$queue();
}
