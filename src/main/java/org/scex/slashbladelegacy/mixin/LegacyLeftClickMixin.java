package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ItemSlashBlade.class,remap=false)
public abstract class LegacyLeftClickMixin {
    @Inject(method="onLeftClickEntity",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$leftHitWindow(ItemStack stack,Player player,Entity entity,CallbackInfoReturnable<Boolean> cir) {
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(player))return;
        var state=BladeStateAccess.of(stack).orElse(null);
        if(state==null || state.onClick())return;
        if(entity instanceof LivingEntity target && target.hurtDuration!=0 && target.hurtDuration-target.hurtTime<6) {
            player.swingTime=0;player.swinging=true;cir.setReturnValue(true);return;
        }
        if(mods.flammpfeil.slashblade.item.SwordType.from(stack).contains(mods.flammpfeil.slashblade.item.SwordType.NOSCABBARD)) {
            org.scex.slashbladelegacy.LegacyDamage.update(player);
            org.scex.slashbladelegacy.LegacyDamage.hit(player,entity,org.scex.slashbladelegacy.LegacyMove.NONE,entity.getBoundingBox(),false);
            cir.setReturnValue(true);return;
        }
        var input=player.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).getCommands();
        boolean previous=input.contains(mods.flammpfeil.slashblade.util.InputCommand.L_CLICK);
        try {
            input.add(mods.flammpfeil.slashblade.util.InputCommand.L_CLICK);
            var result=state.progressCombo(player);
            if(!result.equals(ComboStateRegistry.NONE.getId()) && org.scex.slashbladelegacy.LegacyDamage.holding(player,stack))
                org.scex.slashbladelegacy.LegacyDamage.hit(player,entity,org.scex.slashbladelegacy.LegacyCombat.move(result),entity.getBoundingBox(),false);
        } finally {if(!previous)input.remove(mods.flammpfeil.slashblade.util.InputCommand.L_CLICK);}
        // The outer Player.attack already posted its protection event. This completed the old hit.
        cir.setReturnValue(true);
    }
}
