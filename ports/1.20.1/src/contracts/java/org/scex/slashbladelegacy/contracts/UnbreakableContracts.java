package org.scex.slashbladelegacy.contracts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.scex.slashbladelegacy.BladeStateAccess;
import org.scex.slashbladelegacy.LegacyAdditionalAttack;
import org.scex.slashbladelegacy.LegacyArts;
import org.scex.slashbladelegacy.LegacyCompat;
import org.scex.slashbladelegacy.LegacyMode;

/** Check vanilla Unbreakable and the installed Indestructible enchantment. */
public final class UnbreakableContracts {
    private UnbreakableContracts() {}

    public static void run(MinecraftServer server,Map<String,Object> report) {
        var player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"Unbreakable"));
        player.connection=new ContractItems.PacketSink(server,player);
        player.setPos(8,160,8);
        server.overworld().addNewPlayer(player);
        try {
            run(player,report);
        } finally {
            player.discard();
        }
        // Reuse the real release and scheduler checks for both Super SA settings.
        Super17Contracts.run(server,report);
    }

    private static void run(ServerPlayer player,Map<String,Object> report) {
        var rows=new LinkedHashMap<String,Object>();
        report.put("unbreakable",rows);
        var original=player.getMainHandItem();
        boolean creative=player.getAbilities().instabuild;
        var previousMode=player.getPersistentData().get(LegacyMode.MODERN);
        var blade=ContractItems.blade();
        var state=BladeStateAccess.of(blade).orElseThrow();
        state.setMaxDamage(100);
        state.setProudSoulCount(500);
        state.setBroken(false);
        state.setSealed(false);
        var drops=new ArrayList<ItemEntity>();
        int[] breaks={0},callbacks={0},hits={0};
        Consumer<SlashBladeEvent.BreakEvent> countBreak=event->{if(event.getBlade()==blade)breaks[0]++;};
        Consumer<SlashBladeEvent.HitEvent> countHit=event->{if(event.getBlade()==blade)hits[0]++;};
        Consumer<EntityJoinLevelEvent> collect=event->{
            if(event.getEntity() instanceof ItemEntity item && item.distanceToSqr(player)<4)drops.add(item);
        };
        MinecraftForge.EVENT_BUS.addListener(countBreak);
        MinecraftForge.EVENT_BUS.addListener(countHit);
        MinecraftForge.EVENT_BUS.addListener(collect);
        try {
            player.getAbilities().instabuild=false;
            player.getPersistentData().remove(LegacyMode.MODERN);
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);
            LegacyMode.bind(blade,player);
            blade.setDamageValue(20);
            blade.getOrCreateTag().putBoolean("Unbreakable",true);
            check(!blade.isDamageableItem(),"the root NBT flag disables stack damage");
            var target=EntityType.ZOMBIE.create(player.level());
            check(target!=null,"the hit target exists");
            check(blade.getItem().hurtEnemy(blade,target,player) && hits[0]==1,"the blade still runs its combat hit callback");
            LegacyAdditionalAttack.wear(blade,5,player);
            blade.hurtAndBreak(200,player,entity->callbacks[0]++);
            check(blade.getDamageValue()==20 && !state.isBroken(),"ordinary wear preserves tagged durability");
            check(breaks[0]==0 && callbacks[0]==0 && drops.isEmpty(),"tagged wear has no break effects");
            rows.put("ordinary_wear_preserves_tagged_blade",true);

            int remaining=blade.getItem().damageItem(blade,5,player,entity->callbacks[0]++);
            check(remaining==0 && blade.getDamageValue()==20,"direct damage returns no remaining cost");
            blade.setDamageValue(100);
            remaining=blade.getItem().damageItem(blade,1,player,entity->callbacks[0]++);
            check(remaining==0 && blade.getDamageValue()==100 && !state.isBroken(),"direct damage preserves the break boundary");
            check(blade.getCount()==1 && state.getProudSoulCount()==500
                    && breaks[0]==0 && callbacks[0]==0 && drops.isEmpty(),"direct tagged damage has no break effects");
            rows.put("direct_item_damage_respects_flag",true);

            blade.removeTagKey("Unbreakable");
            blade.setDamageValue(99);
            blade.hurtAndBreak(1,player,entity->callbacks[0]++);
            check(blade.getDamageValue()==100 && !state.isBroken() && breaks[0]==0,"ordinary blade survives exact maximum damage");
            blade.hurtAndBreak(1,player,entity->callbacks[0]++);
            check(blade.getDamageValue()==100 && state.isBroken() && blade.getCount()==1,"ordinary blade breaks after maximum damage");
            check(breaks[0]==1 && callbacks[0]==1 && drops.size()==1 && state.getProudSoulCount()==0,
                    "ordinary break emits its event, callback and soul reward once");
            blade.hurtAndBreak(1,player,entity->callbacks[0]++);
            check(breaks[0]==1 && callbacks[0]==1 && drops.size()==1,"an existing break does not repeat rewards");
            rows.put("removing_flag_restores_legacy_break",true);

            blade.getOrCreateTag().putBoolean("Unbreakable",true);
            LegacyAdditionalAttack.wear(blade,5,player);
            check(state.isBroken() && blade.getDamageValue()==100,"the flag does not repair an existing break");
            blade.setDamageValue(0);
            check(blade.getDamageValue()==0 && !state.isBroken(),"explicit repair still clears the break");
            blade.setDamageValue(35);
            check(blade.getDamageValue()==35,"explicit damage assignment remains available");
            rows.put("repair_and_explicit_assignment_preserved",true);

            blade.getOrCreateTag().putBoolean("Unbreakable",false);
            blade.hurtAndBreak(2,player,entity->callbacks[0]++);
            check(blade.getDamageValue()==37 && !state.isBroken(),"a false flag permits normal wear");
            rows.put("false_flag_permits_wear",true);

            blade.setDamageValue(100);
            blade.enchant(Enchantments.UNBREAKING,1);
            long seed=0;
            while(RandomSource.create(seed).nextInt(2)==0)seed++;
            var expected=RandomSource.create(seed);
            expected.nextInt(2);
            player.getRandom().setSeed(seed);
            int beforeBreaks=breaks[0],beforeCallbacks=callbacks[0],beforeDrops=drops.size();
            blade.hurtAndBreak(1,player,entity->callbacks[0]++);
            check(blade.getDamageValue()==100 && !state.isBroken()
                    && breaks[0]==beforeBreaks && callbacks[0]==beforeCallbacks && drops.size()==beforeDrops,
                    "Unbreaking prevents the break before the item callback");
            check(player.getRandom().nextInt()==expected.nextInt(),"Unbreaking rolls exactly once");
            rows.put("native_unbreaking_before_break_once",true);

            blade.removeTagKey("Enchantments");
            blade.setDamageValue(99);
            check(blade.hurt(1,player.getRandom(),player) && blade.getDamageValue()==100 && !state.isBroken(),
                    "direct native hurt keeps its exact-maximum return outside the legacy scope");
            rows.put("direct_native_hurt_unchanged",true);
            checkIndestructible(player,blade,rows);
        } finally {
            drops.forEach(ItemEntity::discard);
            MinecraftForge.EVENT_BUS.unregister(collect);
            MinecraftForge.EVENT_BUS.unregister(countHit);
            MinecraftForge.EVENT_BUS.unregister(countBreak);
            player.getAbilities().instabuild=creative;
            if(previousMode==null)player.getPersistentData().remove(LegacyMode.MODERN);
            else player.getPersistentData().put(LegacyMode.MODERN,previousMode);
            player.setItemInHand(InteractionHand.MAIN_HAND,original);
        }
    }

    private static void checkIndestructible(ServerPlayer player,ItemStack blade,Map<String,Object> rows) {
        var enchantment=ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("indestructible","indestructible"));
        if(enchantment==null) {
            check(!ModList.get().isLoaded("indestructible"),"the installed mod registers its enchantment");
            rows.put("indestructible_enchantment","not installed");
            return;
        }
        var state=BladeStateAccess.of(blade).orElseThrow();
        blade.removeTagKey("Unbreakable");
        blade.enchant(enchantment,1);
        blade.setDamageValue(20);
        state.setBroken(false);
        int[] callbacks={0},breaks={0},hits={0};
        Consumer<SlashBladeEvent.BreakEvent> countBreak=event->{if(event.getBlade()==blade)breaks[0]++;};
        Consumer<SlashBladeEvent.HitEvent> countHit=event->{if(event.getBlade()==blade)hits[0]++;};
        MinecraftForge.EVENT_BUS.addListener(countBreak);
        MinecraftForge.EVENT_BUS.addListener(countHit);
        boolean respect=LegacyCompat.isEnabled(LegacyCompat.SUPER_RESPECT_UNBREAKABLE);
        try {
            check(blade.isDamageableItem(),"the enchantment does not use the vanilla NBT flag");
            check(!blade.hurt(200,player.getRandom(),player) && blade.getDamageValue()==20,
                    "the real mod cancels native hurt");
            var target=EntityType.ZOMBIE.create(player.level());
            check(target!=null && blade.getItem().hurtEnemy(blade,target,player) && hits[0]==1,
                    "the enchanted blade still runs its combat hit callback");
            LegacyAdditionalAttack.wear(blade,5,player);
            blade.hurtAndBreak(200,player,entity->callbacks[0]++);
            check(blade.getDamageValue()==20 && !state.isBroken(),"the real enchantment protects ordinary legacy wear");
            blade.setDamageValue(100);
            blade.hurtAndBreak(1,player,entity->callbacks[0]++);
            check(blade.getDamageValue()==100 && !state.isBroken() && callbacks[0]==0 && breaks[0]==0,
                    "the real enchantment prevents legacy break effects at the boundary");
            rows.put("indestructible_native_and_legacy_wear",true);

            blade.setDamageValue(20);
            LegacyCompat.SUPER_RESPECT_UNBREAKABLE.set(true);
            LegacyArts.finishSuper(player,blade);
            check(blade.getDamageValue()==20,"the default Super cost respects the real enchantment");
            LegacyCompat.SUPER_RESPECT_UNBREAKABLE.set(false);
            LegacyArts.finishSuper(player,blade);
            check(blade.getDamageValue()==50,"the strict Super cost still sets half durability");
            rows.put("indestructible_super_default_and_strict",true);

            blade.setDamageValue(0);
            check(blade.getDamageValue()==0,"the enchantment permits explicit repair");
            blade.removeTagKey("Enchantments");
            blade.hurtAndBreak(2,player,entity->callbacks[0]++);
            check(blade.getDamageValue()==2 && !state.isBroken(),"removing the enchantment restores normal wear");
            rows.put("indestructible_repair_and_removal",true);
        } finally {
            LegacyCompat.SUPER_RESPECT_UNBREAKABLE.set(respect);
            MinecraftForge.EVENT_BUS.unregister(countHit);
            MinecraftForge.EVENT_BUS.unregister(countBreak);
        }
    }

    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError("Unbreakable: "+message);
    }
}
