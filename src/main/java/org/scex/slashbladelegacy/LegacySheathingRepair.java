package org.scex.slashbladelegacy;

import java.util.Map;
import java.util.WeakHashMap;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/** r87 SoulEater: stationary kill credit, souls, repair and capped healing at completed sheathing. */
public final class LegacySheathingRepair {
    private static final String CREDIT="slashblade_legacy_compat.sheathing_credit";
    private static final String POSITION=CREDIT+"_position",COUNT=CREDIT+"_count",DIMENSION=CREDIT+"_dimension";
    private static final Map<Player,Sheath> SHEATHS=new WeakHashMap<>();
    private static final class Sheath {
        final ItemStack blade;
        final Vec3 position;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        boolean ready;
        final boolean stationary;
        Sheath(Player player,LegacyMove move){blade=player.getMainHandItem();position=player.position();dimension=player.level().dimension();stationary=move==LegacyMove.NOUTOU;}
    }
    private LegacySheathingRepair() {}
    public static boolean handles(ItemStack blade) {
        return LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) && LegacyCompat.isEnabled(LegacyCompat.SHEATHING_REPAIR)
                && blade.getItem() instanceof ItemSlashBlade && BladeStateAccess.of(blade)
                .isPresent();
    }
    private static int position(Player player) {return (int)((player.getX()+player.getY()+player.getZ())*10);}
    public static void credit(Player player,ItemStack blade,int amount) {
        if(!handles(blade))return;
        // The hash is the actual r87 rule, including movement between the kill and sheathing.
        CustomData.update(DataComponents.CUSTOM_DATA,blade,tag->{
            boolean same=tag.contains(POSITION) && tag.getInt(POSITION)==position(player)
                    && tag.getString(DIMENSION).equals(player.level().dimension().location().toString());
            tag.putInt(CREDIT,(int)Math.min(Integer.MAX_VALUE,(same?(long)Math.max(0,tag.getInt(CREDIT)):0)+Math.max(0,amount)));
            tag.putInt(COUNT,same?Math.min(Integer.MAX_VALUE-1,tag.getInt(COUNT))+1:1);
            tag.putInt(POSITION,position(player));tag.putString(DIMENSION,player.level().dimension().location().toString());
        });
    }
    public static void begin(LivingEntity user,LegacyMove move) {
        if(user.level().isClientSide || !(user instanceof Player player))return;
        SHEATHS.remove(player);
        if((handles(player.getMainHandItem()) || LegacyTaunt.handles(player.getMainHandItem())) && (move==LegacyMove.NOUTOU || move==LegacyMove.IAI || move==LegacyMove.S_IAI))
            SHEATHS.put(player,new Sheath(player,move));
    }
    public static void timeout(SlashBladeEvent.NextOfTimeOutComboEvent event) {
        if(event.getUser().level().isClientSide || !(event.getUser() instanceof Player player))return;
        var sheath=SHEATHS.get(player);
        var move=LegacyCombat.visualMove(event.getSlashBladeState().getComboSeq());
        if(sheath!=null && sheath.blade==event.getBlade() && event.getNextCombo().equals(ComboStateRegistry.NONE.getId())
                && (move==LegacyMove.NOUTOU || move==LegacyMove.IAI || move==LegacyMove.S_IAI))sheath.ready=true;
    }
    public static void tick(Player player) {
        var sheath=SHEATHS.get(player);
        if(sheath==null)return;
        if(!player.isAlive() || player.getMainHandItem()!=sheath.blade || !(handles(sheath.blade) || LegacyTaunt.handles(sheath.blade))
                || !player.level().dimension().equals(sheath.dimension)){SHEATHS.remove(player);return;}
        if(!sheath.ready)return;
        SHEATHS.remove(player);
        var state=BladeStateAccess.of(sheath.blade).orElseThrow();
        // Check the applied state after all timeout/motion listeners; canceled transitions cannot repair.
        if(!state.getComboSeq().equals(ComboStateRegistry.NONE.getId())
                || sheath.stationary && (Math.abs(player.getX()-sheath.position.x)>.1 || Math.abs(player.getZ()-sheath.position.z)>.1))return;
        var tag=sheath.blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(handles(sheath.blade) && tag.contains(CREDIT)
                && (!tag.contains(POSITION) || tag.getInt(POSITION)==position(player)
                    && tag.getString(DIMENSION).equals(player.level().dimension().location().toString()))) {
            int repair=Math.max(1,tag.getInt(CREDIT));
            // dev.13 credits already awarded souls; only the new positioned format defers them.
            if(tag.contains(POSITION))state.setProudSoulCount((int)Math.min(Integer.MAX_VALUE,(long)state.getProudSoulCount()+Math.max(0,tag.getInt(CREDIT))));
            int kills=tag.getInt(COUNT);
            CustomData.update(DataComponents.CUSTOM_DATA,sheath.blade,t->{t.remove(CREDIT);t.remove(POSITION);t.remove(COUNT);t.remove(DIMENSION);});
            LegacyRank.awardAction(player,player.getData(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT),"KillNoutou",-.5f);
            if(state.getProudSoulCount()>=1000) {
                sheath.blade.setDamageValue(Math.max(0,sheath.blade.getDamageValue()-repair));
                if(player.getHealth()<player.getMaxHealth() && kills>0) {
                    player.heal(Math.min(kills,player.getMaxHealth()/10));player.magicCrit(player);player.causeFoodExhaustion(1);
                }
            }
        }
        if(sheath.stationary)LegacyTaunt.fire(player,sheath.blade);
    }
}
