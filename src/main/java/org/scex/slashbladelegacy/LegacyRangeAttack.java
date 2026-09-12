package org.scex.slashbladelegacy;

import java.util.EnumSet;
import java.util.UUID;
import java.util.WeakHashMap;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;

/** r87 InitProxyClient / ItemSlashBlade.doRangeAttack over the existing server input channel. */
public final class LegacyRangeAttack {
    private LegacyRangeAttack() {}
    public enum Art { SINGLE, SPIRAL, STORM, BLISTERING, HEAVY_RAIN }
    static final String BLISTERING_UNTIL="slashblade_legacy_compat.blistering_until";
    static final String SPIRAL="slashblade_legacy_compat.spiral";
    private static final String SPIRAL_UNTIL="slashblade_legacy_compat.spiral_until";
    private record Press(ItemStack blade,long tick,ResourceLocation dimension) {}
    private static final WeakHashMap<ServerPlayer,Press> PRESSES=new WeakHashMap<>();
    private static final WeakHashMap<ServerPlayer,Long> LAST_RELEASE=new WeakHashMap<>();

    public static boolean enabled() { return LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT); }
    public static int color(mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state) {
        int rgb=state.getColorCode()&0xFFFFFF;
        return state.isEffectColorInverse()?-rgb:rgb;
    }
    public static void clear(Player player) { PRESSES.remove(player);LAST_RELEASE.remove(player); }
    public static void input(InputCommandEvent event) {
        var player=event.getEntity();
        if(!LegacyMode.legacy(player))return;
        boolean before=event.getOld().contains(InputCommand.M_DOWN),after=event.getCurrent().contains(InputCommand.M_DOWN);
        long now=player.level().getGameTime();
        if(!before && after) {
            var press=new Press(player.getMainHandItem(),now,player.level().dimension().location());
            PRESSES.put(player,press);
            event.getState().getScheduler().schedule("scex_legacy_range",now+8,(entity,queue,time)-> {
                if(!(entity instanceof ServerPlayer user) || PRESSES.get(user)!=press || !valid(user,press))return;
                var input=user.getData(CapabilityInputState.INPUT_STATE);
                if(!input.getCommands().contains(InputCommand.M_DOWN))return;
                perform(user,press.blade(),chargedArt(input.getCommands(),input.getLastPressTime(InputCommand.BACK),time));
            });
        }
        if(!before || after)return;
        var press=PRESSES.remove(player);
        // A release also follows charged arts in r87 (except releasing the held blistering volley).
        if(press==null || !valid(player,press) || LAST_RELEASE.getOrDefault(player,Long.MIN_VALUE)==now)return;
        LAST_RELEASE.put(player,now);
        perform(player,press.blade(),Art.SINGLE);
    }
    public static Art chargedArt(EnumSet<InputCommand> keys,long back,long now) {
        if(keys.contains(InputCommand.SNEAK)) {
            if(keys.contains(InputCommand.FORWARD))return back>=0 && back<=now && now-back<14?Art.HEAVY_RAIN:Art.BLISTERING;
            if(keys.contains(InputCommand.BACK))return Art.STORM;
        }
        return Art.SPIRAL;
    }
    private static boolean valid(ServerPlayer player,Press press) {
        return org.scex.slashbladelegacy.LegacyMode.legacy(player) && player.isAlive() && player.getMainHandItem()==press.blade()
                && player.level().dimension().location().equals(press.dimension()) && player.level().getGameTime()>=press.tick();
    }
    public static UUID sourceId(ItemStack blade) {
        var tag=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!tag.hasUUID(SummonedBladeMode.SOURCE)) {
            tag.putUUID(SummonedBladeMode.SOURCE,UUID.randomUUID());
            blade.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        }
        return tag.getUUID(SummonedBladeMode.SOURCE);
    }
    public static ItemStack sourceBlade(Player owner,UUID source) {
        if(source==null)return ItemStack.EMPTY;
        ItemStack result=ItemStack.EMPTY;
        for(int i=0;i<owner.getInventory().getContainerSize();i++) {
            var candidate=owner.getInventory().getItem(i);
            if(candidate.isEmpty() || BladeStateAccess.of(candidate).isEmpty())continue;
            var tag=candidate.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(tag.hasUUID(SummonedBladeMode.SOURCE) && source.equals(tag.getUUID(SummonedBladeMode.SOURCE))) {
                if(!result.isEmpty())return ItemStack.EMPTY;
                result=candidate;
            }
        }
        return result;
    }
    public static void perform(ServerPlayer player,ItemStack blade,Art art) {
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(player) || !player.isAlive() || player.getMainHandItem()!=blade)return;
        var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null || state.isBroken() || state.isSealed() || !SwordType.from(blade).contains(SwordType.BEWITCHED))return;
        int power=blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.POWER));
        if(power<=0)return;
        var data=player.getPersistentData();long now=player.level().getGameTime();
        if(art==Art.SINGLE && now<data.getLong(BLISTERING_UNTIL)) { data.putLong(BLISTERING_UNTIL,now);return; }
        if(art==Art.SPIRAL && data.hasUUID(SPIRAL) && now<data.getLong(SPIRAL_UNTIL)) { data.remove(SPIRAL);return; }
        Entity target=state.getTargetEntity(player.level());
        if(target!=null && !LegacyTargets.attackable(player,target))target=null;
        if(art==Art.STORM && target==null)return;
        int cost=art==Art.SINGLE?1:10;
        if(state.getProudSoulCount()<cost)return;
        UUID source=sourceId(blade);
        if(sourceBlade(player,source)!=blade)return;
        int rank=player.getData(CapabilityConcentrationRank.RANK_POINT).getRank(now).level;
        int lowPower=rank<3?1:power;
        int count=switch(art) { case SINGLE->1;case SPIRAL,STORM->6;case BLISTERING->4+(rank>3?2:0)+(rank>=5?2:0);case HEAVY_RAIN->rank>=5?30:20; };
        double damage=switch(art) { case SINGLE,SPIRAL->lowPower;case STORM->lowPower/2.0;case BLISTERING->power*2.0;case HEAVY_RAIN->1; };
        UUID hold=UUID.randomUUID();
        if(art==Art.BLISTERING)data.putLong(BLISTERING_UNTIL,now+400);
        if(art==Art.SPIRAL) { data.putUUID(SPIRAL,hold);data.putLong(SPIRAL_UNTIL,now+200); }
        int spawned=0;
        for(int i=0;i<count;i++) {
            Entity projectile;
            if(art==Art.SINGLE && SummonedBladeMode.enabled(blade)) {
                var summoned=new LegacySummonedBlade(SummonedBladeMode.BLADE.get(),player.level());
                summoned.initialize(player,lowPower,color(state),source,target);projectile=summoned;
            }else {
                var summoned=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),player.level());
                int index=art==Art.HEAVY_RAIN?i/(count/10):i;
                summoned.initialize(player,source,art,index,damage,color(state),target,hold,
                        SwordType.from(blade).contains(SwordType.FIERCEREDGE));
                projectile=summoned;
            }
            if(player.level().addFreshEntity(projectile))spawned++;
        }
        if(spawned>0) {
            state.setProudSoulCount(state.getProudSoulCount()-cost);
            if(art!=Art.SINGLE)player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    net.minecraft.sounds.SoundSource.PLAYERS,art==Art.BLISTERING?.5f:.7f,1);
        }else {
            if(art==Art.SPIRAL){data.remove(SPIRAL);data.remove(SPIRAL_UNTIL);}
            if(art==Art.BLISTERING)data.remove(BLISTERING_UNTIL);
        }
    }
}
