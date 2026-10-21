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
    /** Read-only query. Only putBoolean(CHARGED,true) writes this key. */
    private static final net.minecraft.nbt.CompoundTag CHARGED_TRUE=new net.minecraft.nbt.CompoundTag();
    static {CHARGED_TRUE.putBoolean(CHARGED,true);}
    private LegacyAdditionalAttack(){}
    // Test the flag without a full custom-data copy. Copy only when the flag changes.
    private static boolean charged(net.minecraft.world.item.ItemStack blade) {
        return blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).matchedBy(CHARGED_TRUE);
    }
    public static void clearCharged(net.minecraft.world.item.ItemStack blade) {
        if(!blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).contains(CHARGED))return;
        var data=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        data.remove(CHARGED);blade.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    public static void markCharged(net.minecraft.world.entity.LivingEntity user,net.minecraft.resources.ResourceLocation result) {
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(user) || result==null || result.equals(ComboStateRegistry.NONE.getId()))return;
        var blade=user.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null || charged(blade))return;
        var data=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.putBoolean(CHARGED,true);
        blade.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    public static void attack(Player player,LegacyMove move) {
        if(player.level().isClientSide || move.scabbard || move==LegacyMove.NOUTOU)return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        int rank=LegacyCombat.rank(player);
        if(charged(blade)) {
            var data=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            data.remove(CHARGED);blade.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            if(rank>=4 && !state.isBroken() && SwordType.from(blade).contains(SwordType.BEWITCHED)) {
                if(state.getProudSoulCount()>=10)state.setProudSoulCount(state.getProudSoulCount()-10);
                else wear(blade,5,player);
                if(blade.isEmpty())return;
                spawn(player,move,.75f,true,false); // r87 setter(false) actually enables multi-hit.
            }
        }
        if(move==LegacyMove.S_SLASH_BLADE)
            spawn(player,move,.05f,rank>5,true); // r87 setter(rank<=5) is the single-hit flag.
        else if(move==LegacyMove.FORCE6 || move==LegacyMove.FORCE5 && rank>=5) {
            player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                    net.minecraft.sounds.SoundSource.PLAYERS,.8f,.01f);
            spawn(player,move,.1f,rank>5,true); // 1.12.2 Force finisher speed; no extra soul/durability cost.
        }
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
        drive.initialize(player,blade,damage,speed,90-(finisher?Math.abs(move.direction):move.direction),multi);
        drive.setDimension(finisher && SwordType.from(blade).contains(SwordType.FIERCEREDGE));
        player.level().addFreshEntity(drive);
    }
}
