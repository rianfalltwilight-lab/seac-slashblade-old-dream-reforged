package org.scex.slashbladelegacy.contracts;

import java.util.*;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.Vec3;
import org.scex.slashbladelegacy.*;

final class Guard17Contracts {
    /** Only fake-network/invulnerability fixtures differ; hurt and all damage events are real. */
    private static final class VulnerablePlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        VulnerablePlayer(ServerLevel level) throws Exception {
            super(level,new GameProfile(UUID.randomUUID(),"Guard17"));
            var spawn=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");spawn.setAccessible(true);spawn.setInt(this,0);
        }
        @Override public boolean isInvulnerableTo(DamageSource source){return false;}
        void elapsed(int ticks){useItemRemaining=getUseItem().getUseDuration(this)-ticks;}
    }
    static void run(ServerPlayer source,Map<String,Object> report) throws Exception {
        var level=source.serverLevel();var player=new VulnerablePlayer(level);
        player.setPos(24,160,0);player.setYRot(0);player.setOnGround(true);player.getAbilities().instabuild=false;
        var blade=source.getMainHandItem().copy();blade.remove(DataComponents.CUSTOM_DATA);blade.set(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        blade.setDamageValue(1);var state=BladeStateAccess.of(blade).orElseThrow();state.setSpecialEffects(new net.minecraft.nbt.ListTag());
        state.setBroken(false);state.setSealed(false);state.setKillCount(0);state.setComboSeq(ComboStateRegistry.NONE.getId());
        player.setItemInHand(InteractionHand.MAIN_HAND,blade);
        var enemy=EntityType.ZOMBIE.create(level);enemy.setPos(24,160,10);enemy.setNoAi(true);
        enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);enemy.getAttribute(Attributes.ARMOR).setBaseValue(0);enemy.setHealth(500);level.addFreshEntity(enemy);
        var entities=new ArrayList<Entity>();
        try {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,100,0));
            InputClock.use(player,blade);var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);long rankBefore=rank.getRawRankPoint();
            advance(player,6);float hp=player.getHealth();
            check(!player.hurt(level.damageSources().mobAttack(enemy),4) && player.getHealth()==hp,"unenchanted use guards at tick six");
            check(rank.getRawRankPoint()>rankBefore,"JustGuard rating missing");
            enemy.setPos(24,160,2);float before=enemy.getHealth();
            advance(player,1);LegacyJustGuard.tick(player);
            check(enemy.getHealth()<before && state.getComboSeq().equals(LegacyCombat.id(LegacyMove.BATTOU)),"one real counter Battou");
            float counter=before-enemy.getHealth();before=enemy.getHealth();
            LegacyJustGuard.tick(player);check(enemy.getHealth()==before,"duplicate guard tick hit");
            player.addEffect(new MobEffectInstance(MobEffects.POISON,100,0));
            for(int i=0;i<4;i++){player.setDeltaMovement(1,-2,3);advance(player,1);LegacyJustGuard.tick(player);check(player.getY()==160.5 && player.getDeltaMovement().equals(Vec3.ZERO),"five-tick height freeze");}
            check(player.getEffect(MobEffects.DAMAGE_BOOST).getDuration()==100 && !player.hasEffect(MobEffects.POISON),"guard potion snapshot restore");
            advance(player,1);player.setDeltaMovement(1,2,3);LegacyJustGuard.tick(player);check(player.getDeltaMovement().y==2,"freeze ended");
            reset(player,enemy);InputClock.use(player,blade);advance(player,7);hp=player.getHealth();
            check(player.hurt(level.damageSources().mobAttack(enemy),4) && player.getHealth()<hp,"guard closed at tick seven");
            reset(player,enemy);player.setOnGround(false);state.setBroken(true);InputClock.use(player,blade);hp=player.getHealth();
            check(!player.hurt(level.damageSources().mobAttack(enemy),4) && player.getHealth()==hp,"air and broken blades can JustGuard");
            advance(player,1);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));player.setDeltaMovement(0,-1,0);double y=player.getY();LegacyJustGuard.tick(player);
            check(player.getY()==y && player.getDeltaMovement().y==-1,"swap cancels pending counter/freeze");
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);state.setBroken(false);blade.setDamageValue(1);
            reset(player,enemy);InputClock.use(player,blade);hp=player.getHealth();
            check(player.hurt(level.damageSources().drown(),4) && player.getHealth()<hp,"unblockable environmental damage not guarded");
            reset(player,enemy);blade.enchant(level.registryAccess().holderOrThrow(Enchantments.THORNS),1);player.setShiftKeyDown(true);
            hp=player.getHealth();check(player.hurt(level.damageSources().mobAttack(enemy),4) && player.getHealth()<hp,"modern crouch-only guard disabled");
            reset(player,enemy);InputClock.use(player,blade);player.elapsed(14);
            check(!LegacyProjectileGuard.barrierAvailable(player,14),"barrier requires fifteen ticks");player.elapsed(15);
            check(LegacyProjectileGuard.barrierAvailable(player,15),"barrier active at fifteen");
            var a=EntityType.ARROW.create(level);a.setPos(24,161,1);a.setOwner(enemy);level.addFreshEntity(a);entities.add(a);
            var b=EntityType.SNOWBALL.create(level);b.setPos(24,161,-1);b.setOwner(enemy);level.addFreshEntity(b);entities.add(b);
            var own=EntityType.ARROW.create(level);own.setPos(24,161,1);own.setOwner(player);level.addFreshEntity(own);entities.add(own);
            int wear=blade.getDamageValue();player.swinging=false;LegacyProjectileGuard.tick(player);
            check(a.isRemoved() && b.isRemoved() && !own.isRemoved(),"barrier destruction and owner protection");
            check(blade.getDamageValue()==wear+2,"barrier one durability per projectile");
            player.setOnGround(false);check(!LegacyProjectileGuard.barrierAvailable(player,15),"barrier off in air");player.setOnGround(true);
            state.setBroken(true);check(!LegacyProjectileGuard.barrierAvailable(player,15),"barrier off when broken");state.setBroken(false);
            blade.enchant(level.registryAccess().holderOrThrow(Enchantments.RESPIRATION),3);LegacyRespiration.tick(player);
            check(player.getEffect(MobEffects.WATER_BREATHING).getAmplifier()==2 && player.getEffect(MobEffects.WATER_BREATHING).getDuration()==2,"Respiration III held-use breathing");
            player.stopUsingItem();player.removeAllEffects();LegacyRespiration.tick(player);check(!player.hasEffect(MobEffects.WATER_BREATHING),"breathing requires use");
            enemy.setOnGround(true);LegacyCombat.impact(player,enemy,LegacyMove.SLASH_EDGE);check(enemy.getDeltaMovement().y==.2,"ground slash lifts target");
            blade.enchant(level.registryAccess().holderOrThrow(Enchantments.FEATHER_FALLING),1);LegacyCombat.impact(player,enemy,LegacyMove.SLASH_EDGE);check(enemy.getDeltaMovement().y==.3,"Feather Falling lift");
            enemy.setDeltaMovement(0,2,0);LegacyCombat.impact(player,enemy,LegacyMove.KIRIOROSI);check(enemy.getDeltaMovement().y==-.2,"Kiriorosi clears upward speed");
            blade.enchant(level.registryAccess().holderOrThrow(Enchantments.FORTUNE),2);LegacyCombat.impact(player,enemy,LegacyMove.SAYA1);
            var savedEnemy=new net.minecraft.nbt.CompoundTag();enemy.addAdditionalSaveData(savedEnemy);
            check(savedEnemy.getList("ArmorDropChances",5).getFloat(3)==.99f && savedEnemy.getList("HandDropChances",5).getFloat(0)==.99f,"Fortune II equipment drop chance");
            report.put("legacy17_guard",Map.of("hurt_pipeline",true,"window_ticks",7,"freeze_ticks",5,"counter_damage",counter,"swap_cleanup",true,"barrier_per_projectile_wear",2,"respiration",true,"impact_and_fortune",true));
        } finally {LegacyJustGuard.clear(player);player.stopUsingItem();player.removeAllEffects();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);entities.forEach(Entity::discard);enemy.discard();}
    }
    private static void advance(ServerPlayer player,int ticks){for(int i=0;i<ticks;i++)InputClock.next(player);}
    private static void reset(VulnerablePlayer player,LivingEntity enemy){
        LegacyJustGuard.clear(player);player.stopUsingItem();advance(player,30);player.invulnerableTime=0;player.setHealth(player.getMaxHealth());player.removeAllEffects();player.setOnGround(true);enemy.setPos(24,160,10);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
