package org.scex.slashbladelegacy;

import java.util.List;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.bladestand.ProudSoulEnchantmentEvent;
import mods.flammpfeil.slashblade.item.ItemProudSoul;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.tags.EntityTypeTags;
import net.neoforged.neoforge.common.NeoForge;

/** r87 EnchantHelper and ItemSWaeponMaterial; existing blade components are never filtered. */
public final class LegacyEnchantments {
    public static final List<ResourceKey<Enchantment>> SWORD = List.of(Enchantments.SHARPNESS, Enchantments.SMITE,
            Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT,
            Enchantments.LOOTING, Enchantments.UNBREAKING);
    public static final List<ResourceKey<Enchantment>> RARE = List.of(Enchantments.POWER, Enchantments.PUNCH,
            Enchantments.THORNS, Enchantments.FIRE_PROTECTION, Enchantments.FEATHER_FALLING,
            Enchantments.FORTUNE, Enchantments.RESPIRATION, Enchantments.UNBREAKING);
    private LegacyEnchantments() {}
    private static final ThreadLocal<ProudSoulEnchantmentEvent> SOUL_ROLL=new ThreadLocal<>();
    public static boolean ownsSoulRoll(ProudSoulEnchantmentEvent event){return SOUL_ROLL.get()==event;}

    public static boolean vanilla(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(k -> k.location().getNamespace().equals("minecraft")).orElse(false);
    }
    public static boolean sword(Holder<Enchantment> enchantment) { return SWORD.stream().anyMatch(enchantment::is); }
    public static boolean rare(Holder<Enchantment> enchantment) { return RARE.stream().anyMatch(enchantment::is); }

    public static void postHit(Player player, ItemStack blade, Entity target, DamageSource source) {
        var level=(ServerLevel)player.level();
        // Victim armor (including Thorns) remains on the actual equipment API exactly once.
        EnchantmentHelper.doPostAttackEffectsWithItemSource(level,target,source,null);
        if(!LegacyDamage.holding(player,blade))return;
        EnchantmentHelper.runIterationOnItem(blade,EquipmentSlot.MAINHAND,player,(enchantment,amount,item)->{
            if(!LegacyDamage.holding(player,blade))return;
            if(enchantment.is(Enchantments.BANE_OF_ARTHROPODS)) {
                if(amount>0 && target instanceof LivingEntity living && target.getType().is(EntityTypeTags.ARTHROPOD))
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20+player.getRandom().nextInt(10*amount),3));
            } else enchantment.value().doPostAttack(level,amount,item,EnchantmentTarget.ATTACKER,target,source);
        });
    }

    public static void stand(SlashBladeEvent.BladeStandAttackEvent event) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || event.isCanceled()
                || !(event.getDamageSource().getEntity() instanceof Player player)
                || !(player.level() instanceof ServerLevel level))return;
        var soul=player.getMainHandItem();var stand=event.getBladeStand();var blade=event.getBlade();
        if(!(soul.getItem() instanceof ItemProudSoul) || !soul.isEnchanted() || blade.isEmpty() || stand.isOnFire())return;
        var data=soul.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(data.contains("SpecialAttackType") || data.contains("SpecialEffectType"))return;
        float probability=soul.is(SlashBladeItems.PROUDSOUL.get())?.5f:soul.is(SlashBladeItems.PROUDSOUL_INGOT.get())?.75f
                :soul.is(SlashBladeItems.PROUDSOUL_SPHERE.get()) || soul.is(SlashBladeItems.PROUDSOUL_CRYSTAL.get())?1:.25f;
        var updated=new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(blade));
        // One draw is shared by every enchantment on this material, as in r87.
        float roll=player.getRandom().nextFloat();int cost=player.isCreative()?0:1;int spheres=0;boolean changed=false;
        for(var entry:EnchantmentHelper.getEnchantmentsForCrafting(soul).entrySet()) {
            var enchantment=entry.getKey();int current=updated.getLevel(enchantment);
            int maximum=enchantment.is(Enchantments.UNBREAKING)?5:enchantment.value().getMaxLevel();
            int next=current==0?1:Math.min(current+1,maximum);
            var extension=new ProudSoulEnchantmentEvent(blade,event.getSlashBladeState(),enchantment,next,true,probability,cost,event);
            var previous=SOUL_ROLL.get();SOUL_ROLL.set(extension);
            try{NeoForge.EVENT_BUS.post(extension);}finally{if(previous==null)SOUL_ROLL.remove();else SOUL_ROLL.set(previous);}
            if(extension.isCanceled()){event.setCanceled(true);return;}
            cost=Math.max(0,extension.getTotalShrinkCount());
            if(roll<extension.getProbability()) {
                updated.set(extension.getEnchantment(),extension.getEnchantLevel());changed=true;
                if(current>=maximum && extension.getProbability()>.7f)spheres++;
            }
            if(!extension.willTryNextEnchant())break;
        }
        event.setCanceled(true);
        if(player.getMainHandItem()!=soul || stand.getItem()!=blade || soul.getCount()<cost)return;
        soul.shrink(cost);EnchantmentHelper.setEnchantments(blade,updated.toImmutable());
        stand.setItem(blade); // Mark the frame's synchronized stack dirty for observing clients.
        for(int i=0;i<spheres;i++) {
            var sphere=new ItemStack(SlashBladeItems.PROUDSOUL_SPHERE.get());var tag=new CompoundTag();
            tag.putString("SpecialAttackType",event.getSlashBladeState().getSlashArtsKey().toString());
            sphere.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));stand.spawnAtLocation(sphere);
        }
        if(changed)level.sendParticles(ParticleTypes.CRIT,stand.getX(),stand.getY()+1,stand.getZ(),12,.2,.3,.2,.1);
    }
}
