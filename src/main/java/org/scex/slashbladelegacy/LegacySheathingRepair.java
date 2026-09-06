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

/** 1.12.2 SoulEater repair timing, using the existing Resharpened XP/soul award. */
public final class LegacySheathingRepair {
    private static final String CREDIT="slashblade_legacy_compat.sheathing_credit";
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
        return LegacyCompat.LEGACY_COMBAT.get() && LegacyCompat.SHEATHING_REPAIR.get()
                && blade.getItem() instanceof ItemSlashBlade && BladeStateAccess.of(blade)
                .map(s->s.getComboRoot().equals(ComboStateRegistry.STANDBY.getId())).orElse(false);
    }
    public static void credit(ItemStack blade,int amount) {
        if(!handles(blade))return;
        // Presence matters: a zero-XP kill repairs at least one, but an empty swing never does.
        CustomData.update(DataComponents.CUSTOM_DATA,blade,tag->tag.putInt(CREDIT,
                (int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,tag.getInt(CREDIT))+Math.max(0,amount))));
    }
    public static void begin(LivingEntity user,LegacyMove move) {
        if(user.level().isClientSide || !(user instanceof Player player))return;
        SHEATHS.remove(player);
        if(handles(player.getMainHandItem()) && (move==LegacyMove.NOUTOU || move==LegacyMove.IAI || move==LegacyMove.S_IAI))
            SHEATHS.put(player,new Sheath(player,move));
    }
    public static void timeout(SlashBladeEvent.NextOfTimeOutComboEvent event) {
        if(event.getUser().level().isClientSide || !(event.getUser() instanceof Player player))return;
        var sheath=SHEATHS.get(player);
        var move=LegacyCombat.move(event.getSlashBladeState().getComboSeq());
        if(sheath!=null && sheath.blade==event.getBlade() && event.getNextCombo().equals(ComboStateRegistry.NONE.getId())
                && (move==LegacyMove.NOUTOU || move==LegacyMove.IAI || move==LegacyMove.S_IAI))sheath.ready=true;
    }
    public static void tick(Player player) {
        var sheath=SHEATHS.get(player);
        if(sheath==null)return;
        if(!player.isAlive() || player.getMainHandItem()!=sheath.blade || !handles(sheath.blade)
                || !player.level().dimension().equals(sheath.dimension)){SHEATHS.remove(player);return;}
        if(!sheath.ready)return;
        SHEATHS.remove(player);
        var state=BladeStateAccess.of(sheath.blade).orElseThrow();
        // Check the applied state after all timeout/motion listeners; canceled transitions cannot repair.
        if(!state.getComboSeq().equals(ComboStateRegistry.NONE.getId())
                || sheath.stationary && (Math.abs(player.getX()-sheath.position.x)>.1 || Math.abs(player.getZ()-sheath.position.z)>.1))return;
        var tag=sheath.blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!tag.contains(CREDIT))return;
        int repair=Math.max(1,tag.getInt(CREDIT));
        CustomData.update(DataComponents.CUSTOM_DATA,sheath.blade,t->t.remove(CREDIT));
        if(state.getProudSoulCount()>=1000)sheath.blade.setDamageValue(Math.max(0,sheath.blade.getDamageValue()-repair));
    }
}
