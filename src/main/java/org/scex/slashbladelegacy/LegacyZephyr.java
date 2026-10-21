package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** 西风太刀调用 Thaumcraft 风雷剑的原生行为。 */
public final class LegacyZephyr {
    private static final ResourceLocation ELEMENTAL_SWORD=ResourceLocation.parse("thaumcraft:elemental_sword");
    private LegacyZephyr(){}
    private static Item elemental(ItemStack blade,Player player) {
        if(!LegacyMode.legacy(player))return Items.AIR;
        var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null || state.isBroken() || !state.getTranslationKey().equals("item.slashblade_addon.zephyr"))return Items.AIR;
        return BuiltInRegistries.ITEM.get(ELEMENTAL_SWORD);
    }
    public static void use(LivingEntityUseItemEvent.Tick event) {
        if(!(event.getEntity() instanceof Player player))return;
        var item=elemental(event.getItem(),player);if(item==Items.AIR)return;
        // 旧版传入刀副本，避免风雷剑扣除本刀耐久。
        item.onUseTick(player.level(),player,event.getItem().copy(),event.getDuration());
    }
    public static void hit(SlashBladeEvent.HitEvent event) {
        if(event.isCanceled() || !(event.getUser() instanceof Player player) || player.level().isClientSide
                || !player.getData(CapabilityInputState.INPUT_STATE).getCommands().contains(InputCommand.L_CLICK))return;
        var item=elemental(event.getBlade(),player);if(item==Items.AIR)return;
        item.hurtEnemy(event.getBlade(),event.getTarget(),player);
    }
}
