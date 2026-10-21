package org.scex.slashbladelegacy;

import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;

public final class LegacyAdditionalAttack {
    private static final String CHARGED="scex_legacy_charged";
    private LegacyAdditionalAttack(){}
    // Test the flag on the live tag. Copy only when the flag changes.
    public static void clearCharged(net.minecraft.world.item.ItemStack blade) {
        if(!LegacyTags.read(blade).contains(CHARGED))return;
        var data=LegacyTags.copy(blade);
        data.remove(CHARGED);blade.setTag(data);
    }
    public static void markCharged(net.minecraft.world.entity.LivingEntity user,net.minecraft.resources.ResourceLocation result) {
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(user) || result==null || result.equals(ComboStateRegistry.NONE.getId()))return;
        var blade=user.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null || LegacyTags.read(blade).getBoolean(CHARGED))return;
        var data=LegacyTags.copy(blade);data.putBoolean(CHARGED,true);
        blade.setTag(data);
    }
    public static void attack(Player player,LegacyMove move) {
        if(player.level().isClientSide || move.scabbard || move==LegacyMove.NOUTOU)return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        int rank=LegacyCombat.rank(player);
        if(LegacyTags.read(blade).getBoolean(CHARGED)) {
            var data=LegacyTags.copy(blade);
            data.remove(CHARGED);blade.setTag(data);
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
            blade.hurtAndBreak(amount,player,broken->{
                broken.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND);
                mods.flammpfeil.slashblade.item.ItemSlashBlade.getOnBroken(before).accept(player);
            });
        }
    }
    private static void spawn(Player player,LegacyMove move,float speed,boolean multi,boolean finisher) {
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        int power=blade.getEnchantmentLevel(Enchantments.POWER_ARROWS);
        float damage=state.getBaseAttackModifier();
        if(LegacyCombat.rank(player)>=5)damage+=state.getAttackAmplifier()*(.5f+power/5f);
        var drive=new LegacyDrive(SummonedBladeMode.DRIVE.get(),player.level());
        drive.initialize(player,blade,damage,speed,90-(finisher?Math.abs(move.direction):move.direction),multi);
        drive.setDimension(finisher && SwordType.from(blade).contains(SwordType.FIERCEREDGE));
        player.level().addFreshEntity(drive);
    }
}
