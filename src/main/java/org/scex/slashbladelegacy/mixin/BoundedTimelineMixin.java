package org.scex.slashbladelegacy.mixin;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only enabled for the verified upstream loop; work scales with registered actions, not world age. */
@Mixin(value=ComboState.TimeLineTickAction.class,remap=false)
public abstract class BoundedTimelineMixin {
    @Shadow @Final private Map<Integer,Consumer<LivingEntity>> timeLine;
    @Unique private int[] scex$actionTicks;

    @Inject(method="<init>(Ljava/util/Map;)V",at=@At("RETURN"))
    private void scex$indexActions(Map<Integer,Consumer<LivingEntity>> actions,CallbackInfo ci) {
        // Upstream owns a private copy and exposes no mutation API. Null keys were never queried.
        scex$actionTicks=timeLine.keySet().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sorted().toArray();
    }

    @Inject(method="accept(Lnet/minecraft/world/entity/LivingEntity;)V",at=@At("HEAD"),cancellable=true)
    private void scex$runRegisteredActions(LivingEntity user,CallbackInfo ci) {
        ci.cancel();
        if(scex$actionTicks.length==0)return;
        int elapsed=(int)ComboState.getElapsed(user);
        var data=user.getPersistentData();
        int last=data.getInt("slashblade.lastProcessedTick");
        if(last>elapsed)return;
        int index=Arrays.binarySearch(scex$actionTicks,last);
        if(index<0)index=-index-1;
        for(;index<scex$actionTicks.length && scex$actionTicks[index]<=elapsed;index++) {
            var action=timeLine.get(scex$actionTicks[index]);
            if(action!=null) {
                action.accept(user);
                // TickAction.andThen relies on unchanged progress when no callback runs,
                // and on this write occurring after each callback (including combo resets).
                data.putInt("slashblade.lastProcessedTick",elapsed+1);
            }
        }
    }
}
