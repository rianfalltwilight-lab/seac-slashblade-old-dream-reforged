package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.neoforged.neoforge.common.NeoForge;
import static org.scex.slashbladelegacy.LegacyMove.*;

/** 双手持刀时使用 1.12.2 ForceEdge 连段。
 * Force1/2/6 和 Stinger 先用副手攻击，再通知主手刀。 */
public final class LegacyDualWield {
    private static final ThreadLocal<Strike> STRIKE=new ThreadLocal<>();
    private static final class Strike {
        final Player player;final Entity target;final ItemStack main,off;final LegacyMove move;
        boolean offAccepted,mainAccepted;int experience=-1;
        Strike(Player player,Entity target,LegacyMove move) {
            this.player=player;this.target=target;this.move=move;main=player.getMainHandItem();off=player.getOffhandItem();
        }
    }
    private LegacyDualWield() {}

    public static boolean hasOffhandBlade(LivingEntity user) {
        var offhand=user.getOffhandItem();
        return !offhand.isEmpty() && offhand.getItem() instanceof ItemSlashBlade
                && BladeStateAccess.of(offhand).isPresent();
    }

    public static boolean active(LivingEntity user) {
        if(!LegacyMode.legacy(user) || !hasOffhandBlade(user))return false;
        var data=user.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY);
        return !data.contains("isThrownOffhand") || !data.copyTag().getBoolean("isThrownOffhand");
    }

    public static LegacyMove next(LegacyMove current) {
        return switch(current) {
            case FORCE1 -> FORCE2;
            case FORCE2 -> FORCE3;
            case FORCE3 -> FORCE4;
            case FORCE4 -> FORCE5;
            case FORCE5 -> FORCE6;
            default -> FORCE1;
        };
    }

    /** 对应 ItemSlashBlade.ComboSequence.mainHandCombo。null 表示仅主手刀运动。 */
    public static LegacyMove mainHandPose(LegacyMove move) {
        return switch(move) {
            case FORCE1,FORCE2,STINGER -> NONE;
            case FORCE6 -> FORCE5;
            default -> null;
        };
    }

    public static boolean hit(Player player,Entity target,LegacyMove move,AABB bounds) {
        if(STRIKE.get()!=null || !active(player) || mainHandPose(move)==null)
            return LegacyDamage.hit(player,target,move,bounds,true);
        var strike=new Strike(player,target,move);
        var mainState=BladeStateAccess.of(strike.main).orElseThrow();
        var offState=BladeStateAccess.of(strike.off).orElseThrow();
        boolean mainClick=mainState.onClick(),offClick=offState.onClick();
        STRIKE.set(strike);mainState.setOnClick(true);offState.setOnClick(true);
        // 交换两个实际物品栈。外部回调通过主手取得攻击刀。
        player.setItemInHand(InteractionHand.MAIN_HAND,strike.off);
        player.setItemInHand(InteractionHand.OFF_HAND,strike.main);
        try {
            LegacyDamage.update(player);
            boolean wasAlive=target.isAlive();
            boolean accepted=LegacyDamage.hit(player,target,move,bounds,true);
            if(accepted && intact(strike)) {
                if(strike.offAccepted)LegacyAdditionalAttack.wear(strike.off,1,player);
                var parent=target instanceof net.neoforged.neoforge.entity.PartEntity<?> part?part.getParent():target;
                if(intact(strike) && parent instanceof LivingEntity living)strike.main.hurtEnemy(living,player);
                // 旧版也调用主手刀的 hitEntity。两刀记录击杀，伤害和掉落只结算一次。
                if(wasAlive && parent instanceof LivingEntity living && !living.isAlive() && living.deathTime==0
                        && intact(strike) && strike.mainAccepted) {
                    var kills=new SlashBladeEvent.AddKillCountEvent(strike.main,mainState,1);
                    NeoForge.EVENT_BUS.post(kills);
                    mainState.setKillCount((int)Math.clamp((long)mainState.getKillCount()+kills.getNewCount(),0,Integer.MAX_VALUE));
                    ItemSlashBlade.updateRarity(strike.main);
                    if(strike.experience>=0 && intact(strike) && LegacySheathingRepair.handles(strike.main)) {
                        var souls=new SlashBladeEvent.AddProudSoulEvent(strike.main,mainState,strike.experience);
                        NeoForge.EVENT_BUS.post(souls);
                        if(intact(strike))LegacySheathingRepair.credit(player,strike.main,souls.getNewCount());
                    }
                }
            }
            return accepted;
        } finally {
            mainState.setOnClick(mainClick);offState.setOnClick(offClick);
            try {restore(strike);} finally {STRIKE.remove();}
        }
    }

    private static boolean intact(Strike s) {
        return s.player.isAlive() && !s.main.isEmpty() && !s.off.isEmpty()
                && s.player.getMainHandItem()==s.off && s.player.getOffhandItem()==s.main;
    }

    /** 只接管临时换手期间的回调。保留公开 HitEvent 和旧版耐久规则。 */
    public static boolean hurtEnemy(ItemStack blade,LivingEntity target,LivingEntity user) {
        var s=STRIKE.get();
        if(s==null || s.player!=user || (s.target!=target
                && !(s.target instanceof net.neoforged.neoforge.entity.PartEntity<?> part && part.getParent()==target))
                || (blade!=s.main && blade!=s.off))return false;
        var state=BladeStateAccess.of(blade).orElseThrow();
        if(NeoForge.EVENT_BUS.post(new SlashBladeEvent.HitEvent(blade,state,target,user)).isCanceled() || !intact(s))return true;
        var move=blade==s.main?s.move:LegacyCombat.visualMove(state.resolvCurrentComboState(user));
        if(blade==s.off)s.offAccepted=true;
        else s.mainAccepted=true;
        if(move!=NONE)LegacyCombat.impact(user,target,move);
        if(blade==s.off && (!move.scabbard && mainHandPose(move)==null
                || mods.flammpfeil.slashblade.item.SwordType.from(blade).contains(mods.flammpfeil.slashblade.item.SwordType.NOSCABBARD)))
            LegacyAdditionalAttack.wear(blade,1,s.player);
        return true;
    }

    private static void restore(Strike s) {
        var player=s.player;
        if(player.getMainHandItem()==s.off && player.getOffhandItem()==s.main) {
            player.setItemInHand(InteractionHand.MAIN_HAND,s.main.isEmpty()?ItemStack.EMPTY:s.main);
            player.setItemInHand(InteractionHand.OFF_HAND,s.off.isEmpty()?ItemStack.EMPTY:s.off);
            return;
        }
        // 附属在回调中替换了手中物品。保留替换结果。
        // 只归还临时槽位中剩余的原物品栈，不恢复已消耗或已移走的物品。
        if(player.getMainHandItem()==s.off) {
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);returnStack(player,s.off);
        }
        if(player.getOffhandItem()==s.main) {
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);returnStack(player,s.main);
        }
    }

    private static void returnStack(Player player,ItemStack stack) {
        if(!stack.isEmpty() && !player.getInventory().add(stack))player.drop(stack,false);
    }

    public static void removeBorrowedMainModifiers(Player player,net.minecraft.world.entity.ai.attributes.AttributeInstance attribute) {
        var s=STRIKE.get();if(s==null || s.player!=player)return;
        s.main.getAttributeModifiers().forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND,(key,modifier)->{
            if(key.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))attribute.removeModifier(modifier.id());
        });
    }

    public static void recordExperience(Player player,LivingEntity target,int amount) {
        var s=STRIKE.get();
        if(s!=null && s.player==player && s.target==target && intact(s))s.experience=Math.max(0,amount);
    }
}
