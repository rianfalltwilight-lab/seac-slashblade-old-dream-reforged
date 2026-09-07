package org.scex.slashbladelegacy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Keep Resharpened's paired hardpoint/body assets. Retiming both avoids detached blades. */
@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade",remap=false)
public abstract class LegacyBladePoseMixin {
    @Redirect(method="lambda$render$1",at=@At(value="INVOKE",target="Lmods/flammpfeil/slashblade/util/TimeValueHelper;getMSecFromTicks(D)D"))
    private double legacyCompat$pairedMotion(double ticks,LivingEntity entity,float partial,PoseStack poses,
                float motionYOffset,double motionScale,double modelScale,ItemStack stack,MultiBufferSource buffers,int light,ISlashBladeState state) {
        var current=state.peekCurrentComboStateTicks(entity).getValue();
        var move=LegacyCombat.move(current);
        double speed=1;
        if(LegacyCompat.LEGACY_COMBAT.get() && move!=LegacyMove.NONE && state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId()))
            speed=ComboStateRegistry.REGISTRY.get(current).getSpeed();
        return TimeValueHelper.getMSecFromTicks(ticks*speed);
    }
}
