package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Budget committed player click transitions, never virtual queries or scripted charge actions. */
@Mixin(value=ISlashBladeState.class,remap=false)
public interface LegacyInputBudgetMixin {
    @Inject(method="progressCombo(Lnet/minecraft/world/entity/LivingEntity;Z)Lnet/minecraft/resources/ResourceLocation;",
            at=@At("HEAD"),cancellable=true)
    private void legacyCompat$budget(LivingEntity user,boolean virtual,CallbackInfoReturnable<ResourceLocation> result) {
        if(virtual || !LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(user instanceof ServerPlayer player))return;
        var blade=player.getMainHandItem();var state=(ISlashBladeState)(Object)this;
        if(BladeStateAccess.of(blade).isEmpty() || !state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId())
                || SwordType.from(blade).contains(SwordType.NOSCABBARD))return;
        var commands=player.getData(CapabilityInputState.INPUT_STATE).getCommands();
        if(!commands.contains(InputCommand.R_CLICK) && !commands.contains(InputCommand.L_CLICK))return;
        if(!org.scex.slashbladelegacy.LegacyInputBudget.accept(player))result.setReturnValue(ComboStateRegistry.NONE.getId());
    }
}
