package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.world.entity.player.Player;
import org.scex.slashbladelegacy.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保留 SJAP 触发条件。弹体轨迹与旧版幻影刃 SA 一致。 */
@Pseudo
@Mixin(targets="cn.mmf.slashblade_addon.specialeffect.BurstDrive",remap=false)
public abstract class LegacyBurstDriveMixin {
    @Inject(method="onDoingSlash",at=@At("HEAD"),cancellable=true)
    private static void legacyCompat$trajectory(SlashBladeEvent.DoSlashEvent event,CallbackInfo ci) {
        if(!(event.getUser() instanceof Player player) || !LegacyMode.legacy(player))return;
        ci.cancel();
        if(player.level().isClientSide || event.isCanceled() || player.experienceLevel<30
                || !event.getSlashBladeState().hasSpecialEffect(LegacyNamedBlades.BURST_DRIVE))return;
        var drive=LegacyArts.drive(player,event.getBlade(),(float)event.getDamage(),1.5f,event.getRoll(),false,10);
        drive.setColor(event.getSlashBladeState().getColorCode());
        drive.setVector(player.getYRot()-(float)Math.toDegrees(event.getYRot()),player.getXRot(),1.5f);
    }
}
