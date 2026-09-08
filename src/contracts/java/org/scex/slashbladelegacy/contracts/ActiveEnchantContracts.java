package org.scex.slashbladelegacy.contracts;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

final class ActiveEnchantContracts {
    static void run(ServerPlayer source,Map<String,Object> report) throws Exception {
        var player=new Guard17Contracts.VulnerablePlayer(source.serverLevel());player.setPos(source.position());
        var original=player.getMainHandItem();var blade=source.getMainHandItem().copy();var rows=new ArrayList<String>();report.put("active_enchantments",rows);
        player.setGameMode(GameType.SURVIVAL);player.experienceLevel=100;player.removeAllEffects();player.stopUsingItem();
        blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,blade);
        try {
            var fire=player.registryAccess().holderOrThrow(Enchantments.FIRE_PROTECTION);blade.enchant(fire,4);
            player.setHealth(player.getMaxHealth());player.invulnerableTime=0;float health=player.getHealth();
            player.hurt(player.damageSources().onFire(),1);
            check(player.getHealth()<health,"passive blade no longer cancels all fire damage");rows.add("idle fire protection is not passive immunity");
            player.startUsingItem(InteractionHand.MAIN_HAND);player.igniteForSeconds(10);player.setSharedFlagOnFire(true);player.setDeltaMovement(Vec3.ZERO);player.zza=1;
            NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Tick(player,blade,blade.getUseDuration(player)-15));
            check(player.getRemainingFireTicks()>0 && player.getDeltaMovement().horizontalDistance()>.4,"burning acceleration and charge boundary");
            var effect=player.getEffect(MobEffects.FIRE_RESISTANCE);check(effect!=null && effect.getAmplifier()==3,"active fire resistance level");
            player.invulnerableTime=0;health=player.getHealth();player.hurt(player.damageSources().lava(),4);check(player.getHealth()==health,"old active fire resistance covers lava");
            NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Tick(player,blade,blade.getUseDuration(player)-16));
            check(player.getRemainingFireTicks()==0,"old charge extinguishes after 15 ticks");rows.add("active fire resistance IV, acceleration, tick 16 extinguish, lava coverage");
            player.stopUsingItem();player.removeAllEffects();player.zza=0;player.setSharedFlagOnFire(false);
            blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.enchant(player.registryAccess().holderOrThrow(Enchantments.RESPIRATION),3);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));check(!player.hasEffect(MobEffects.WATER_BREATHING),"respiration requires use");
            player.startUsingItem(InteractionHand.MAIN_HAND);NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            check(player.getEffect(MobEffects.WATER_BREATHING).getAmplifier()==2,"active respiration III");rows.add("respiration requires active mainhand use, amplifier II");player.stopUsingItem();
            for(var key:org.scex.slashbladelegacy.LegacyEnchantments.RARE) {
                if(key.equals(Enchantments.UNBREAKING))continue;
                var menu=new AnvilMenu(0,player.getInventory());var clean=blade.copy();clean.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
                menu.getSlot(0).set(clean);menu.getSlot(1).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(player.registryAccess().holderOrThrow(key),1)));menu.createResult();
                check(menu.getSlot(2).getItem().isEmpty(),"non-sword book rejected "+key);
            }
            var menu=new AnvilMenu(0,player.getInventory());var clean=blade.copy();clean.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
            var unbreaking=player.registryAccess().holderOrThrow(Enchantments.UNBREAKING);clean.enchant(unbreaking,3);
            menu.getSlot(0).set(clean);menu.getSlot(1).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(unbreaking,3)));menu.createResult();
            check(menu.getSlot(2).getItem().getEnchantmentLevel(unbreaking)==3,"regular anvil keeps Unbreaking III ceiling");
            rows.add("seven non-sword books rejected by real AnvilMenu; Unbreaking III + III stays III");
            bossDrops(player,blade,rows);
        }finally{player.stopUsingItem();player.removeAllEffects();player.zza=0;player.setHealth(player.getMaxHealth());player.setItemInHand(InteractionHand.MAIN_HAND,original);}
    }
    private static void bossDrops(ServerPlayer player,ItemStack blade,List<String> rows) {
        var items=new ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> observer=e->{if(e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)items.add(item);};
        NeoForge.EVENT_BUS.addListener(observer);
        var mobs=new ArrayList<net.minecraft.world.entity.Mob>();
        try {
            player.removeAllEffects();player.setOnGround(true);player.setYRot(0);
            blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);blade.enchant(player.registryAccess().holderOrThrow(Enchantments.SHARPNESS),1);
            var state=mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess.of(blade).orElseThrow();
            for(int i=0;i<3;i++) {
                var target=i==2?net.minecraft.world.entity.EntityType.WITHER.create(player.level()):net.minecraft.world.entity.EntityType.HUSK.create(player.level());
                target.setPos(player.getX(),player.getY(),player.getZ()+2);target.setNoAi(true);target.setHealth(1);mobs.add(target);player.level().addFreshEntity(target);
                if(i==1)target.setCustomName(net.minecraft.network.chat.Component.literal("Named hostile"));
                state.setComboSeq(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId());state.setLastActionTime(player.level().getGameTime());state.setOnClick(false);
                InputClock.next(player);
                int before=items.size();player.attack(target);
                check(!target.isAlive(),"boss reward fixture killed "+i);
                long souls=items.subList(before,items.size()).stream().filter(e->e.getItem().is(mods.flammpfeil.slashblade.registry.SlashBladeItems.PROUDSOUL.get()) && e.getItem().isEnchanted()).count();
                check(souls==(i==0?0:1),"r87 named hostile/boss enchanted soul "+i);
                if(i==2)check(items.subList(before,items.size()).stream().anyMatch(e->org.scex.slashbladelegacy.LegacyBladeSouls.named(e.getItem())),"boss named crystal");
                int once=items.size();blade.hurtEnemy(target,player);check(items.size()==once,"repeat post-hit cannot duplicate reward");
            }
            rows.add("actual melee kills: ordinary hostile no soul; named hostile enchanted soul; Wither enchanted soul + named crystal; no duplicate reward");
        } finally {NeoForge.EVENT_BUS.unregister(observer);for(var mob:mobs)mob.discard();for(var item:items)item.discard();}
    }
    private static void check(boolean passed,String message){if(!passed)throw new AssertionError("Active enchantment: "+message);}
}
