package org.scex.slashbladelegacy;

import java.util.WeakHashMap;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/** r87 special-action key: charge for 20 ticks, then release. Existing SPRINT input is the V key. */
public final class LegacySuperArts {
    private record Charge(ItemStack blade,long started) {}
    private static final WeakHashMap<ServerPlayer,Charge> CHARGES=new WeakHashMap<>();
    private LegacySuperArts() {}
    public static boolean eligible(ItemStack blade) {
        var state=BladeStateAccess.of(blade).orElse(null);
        return state!=null && !state.isBroken() && !state.isSealed() && blade.getDamageValue()==0
                && state.getKillCount()>=1000 && SwordType.from(blade).contains(SwordType.BEWITCHED);
    }
    public static void input(InputCommandEvent event) {
        var player=event.getEntity();boolean before=event.getOld().contains(InputCommand.SPRINT),after=event.getCurrent().contains(InputCommand.SPRINT);
        if(!before && after)CHARGES.put(player,new Charge(player.getMainHandItem(),player.level().getGameTime()));
        else if(before && !after) {
            var charge=CHARGES.remove(player);
            if(charge!=null && charge.blade==player.getMainHandItem() && player.level().getGameTime()-charge.started>=20)release(player);
        }
    }
    /** Cancel an interrupted charge before a stale input packet can release it after swapping back. */
    public static void tick(Player player) {
        if(player instanceof ServerPlayer server) {
            var charge=CHARGES.get(server);
            if(charge!=null && (!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !player.isAlive() || player.getMainHandItem()!=charge.blade))CHARGES.remove(server);
        }
    }
    public static void release(ServerPlayer player) {
        var blade=player.getMainHandItem();if(!player.isAlive() || !eligible(blade))return;
        var state=BladeStateAccess.of(blade).orElseThrow();
        var combo=state.getSlashArts().doArts(SlashArts.ArtsType.Super,player);
        var event=new SlashBladeEvent.PerformSlashArtEvent(player,20,state,combo,SlashArts.ArtsType.Super);
        if(NeoForge.EVENT_BUS.post(event).isCanceled() || player.getMainHandItem()!=blade || !eligible(blade))return;
        combo=event.getComboState();
        if(combo==null || combo.equals(ComboStateRegistry.NONE.getId()) || ComboStateRegistry.REGISTRY.get(combo)==null)return;
        // r87 manager pays at tick 30; no early hurtAndBreak, soul fee, ground check or priority gate.
        state.updateComboSeq(player,combo);
    }
}
