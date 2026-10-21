package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;

/** 旧梦按输入解析连段。仅实际点击消耗输入预算。 */
@Mixin(value=SlashBladeState.class,remap=false)
public abstract class LegacyInputBudgetMixin implements ISlashBladeState {
    @Override
    public ResourceLocation progressCombo(LivingEntity user,boolean virtual) {
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(user) || !(user instanceof net.minecraft.world.entity.player.Player player))return ISlashBladeState.super.progressCombo(user,virtual);
        var blade=player.getMainHandItem();ISlashBladeState state=this;
        if(BladeStateAccess.of(blade).isEmpty()
                || SwordType.from(blade).contains(SwordType.NOSCABBARD))return ISlashBladeState.super.progressCombo(user,virtual);
        var commands=org.scex.slashbladelegacy.LegacyCapabilities.input(player).getCommands();
        if(!commands.contains(InputCommand.R_CLICK) && !commands.contains(InputCommand.L_CLICK))return ISlashBladeState.super.progressCombo(user,virtual);
        if(!virtual && player instanceof ServerPlayer serverPlayer && !org.scex.slashbladelegacy.LegacyInputBudget.accept(serverPlayer)) {
            return ComboStateRegistry.NONE.getId();
        }
        var currentId=state.resolvCurrentComboState(player);
        var current=ComboStateRegistry.REGISTRY.get().getValue(currentId);
        if(current==null){return ComboStateRegistry.NONE.getId();}
        var next=org.scex.slashbladelegacy.BladeEvents.next(blade,state,player,current.getNext(player));
        if(next==null || !ComboStateRegistry.REGISTRY.get().containsKey(next) || next.equals(currentId)) {
            return ComboStateRegistry.NONE.getId();
        }
        // 已接受的旧版输入不再被其他 comboRoot 优先级替换。
        if(!virtual && player.getMainHandItem()==blade)state.updateComboSeq(player,next);
        return next;
    }
}
