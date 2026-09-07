package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;

public final class LegacyAdditionalAttack {
    private static final String CHARGED="scex_legacy_charged";
    private LegacyAdditionalAttack(){}
    public static void markCharged(net.minecraft.world.entity.LivingEntity user,net.minecraft.resources.ResourceLocation result) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || result==null || result.equals(ComboStateRegistry.NONE.getId()))return;
        var blade=user.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null || !state.getComboRoot().equals(ComboStateRegistry.STANDBY.getId()))return;
        var data=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.putBoolean(CHARGED,true);
        blade.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    public static void attack(Player player,LegacyMove move) {
        if(player.level().isClientSide || move.scabbard || move==LegacyMove.NOUTOU)return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        int rank=LegacyCombat.rank(player);
        var data=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(data.getBoolean(CHARGED)) {
            data.remove(CHARGED);blade.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            if(rank>=4 && !state.isBroken() && SwordType.from(blade).contains(SwordType.BEWITCHED)) {
                if(state.getProudSoulCount()>=10)state.setProudSoulCount(state.getProudSoulCount()-10);
                else wear(blade,5,player);
                if(blade.isEmpty())return;
                spawn(player,move,.75f,false,false);
            }
        }
        if(move==LegacyMove.S_SLASH_BLADE || move==LegacyMove.FORCE6 || (move==LegacyMove.FORCE5 && rank>4))
            spawn(player,move,.1f,rank<=5,true); // Original variable name is misleading: <=5 actually enables multi-hit.
    }
    public static void wear(net.minecraft.world.item.ItemStack blade,int amount,Player player) {
        if(player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var before=blade.copy();
            blade.hurtAndBreak(amount,level,player,item->{
                player.onEquippedItemBroken(item,net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                mods.flammpfeil.slashblade.item.ItemSlashBlade.getOnBroken(before).accept(player);
            });
        }
    }
    private static void spawn(Player player,LegacyMove move,float speed,boolean multi,boolean finisher) {
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        int power=blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.POWER));
        float damage=state.getBaseAttackModifier();
        if(LegacyCombat.rank(player)>=5)damage+=state.getAttackAmplifier()*(.5f+power/5f);
        var drive=new LegacyDrive(SummonedBladeMode.DRIVE.get(),player.level());
        drive.initialize(player,blade,damage,speed,LegacyCombat.slashRoll(move),multi);
        player.level().addFreshEntity(drive);
    }
}
