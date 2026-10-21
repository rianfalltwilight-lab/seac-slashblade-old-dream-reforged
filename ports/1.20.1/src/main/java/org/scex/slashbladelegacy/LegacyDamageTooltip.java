package org.scex.slashbladelegacy;

import java.util.Locale;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/** The original rank damage preview; this does not change attack attributes. */
public final class LegacyDamageTooltip {
    private LegacyDamageTooltip() {}
    public static void tooltip(ItemTooltipEvent event) {
        var player=event.getEntity();
        if(player==null || !LegacyMode.legacy(player))return;
        var blade=event.getItemStack();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null)return;
        float base=state.getBaseAttackModifier(),bonus=10f+state.getRefine();
        var lines=event.getToolTip();
        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.slashblade_legacy_compat.rank_damage"));
        lines.add(Component.literal(SwordType.from(blade).contains(SwordType.FIERCEREDGE)?"§6B-A§r / §4S-SSS§r / §5Limit":"§6B-SS§r / §4SSS§r / §5Limit"));
        lines.add(Component.literal(String.format(Locale.ROOT,"§6+%.1f§r / §4+%.1f§r / §5+%.1f",base,base+Math.min(bonus,player.experienceLevel),base+bonus)));
    }
}
