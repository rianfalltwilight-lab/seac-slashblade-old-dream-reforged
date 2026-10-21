package org.scex.slashbladelegacy;

import java.util.List;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import org.scex.slashbladelegacy.BladeApi;
import net.minecraft.core.Holder;
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
import net.minecraft.world.item.enchantment.*;
import net.minecraft.tags.EntityTypeTags;
import net.minecraftforge.common.MinecraftForge;

/** r87 EnchantHelper and ItemSWaeponMaterial; existing blade components are never filtered. */
public final class LegacyEnchantments {
    public static final List<Enchantment> SWORD = List.of(Enchantments.SHARPNESS, Enchantments.SMITE,
            Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT,
            Enchantments.MOB_LOOTING, Enchantments.UNBREAKING);
    public static final List<Enchantment> RARE = List.of(Enchantments.POWER_ARROWS, Enchantments.PUNCH_ARROWS,
            Enchantments.THORNS, Enchantments.FIRE_PROTECTION, Enchantments.FALL_PROTECTION,
            Enchantments.BLOCK_FORTUNE, Enchantments.RESPIRATION, Enchantments.UNBREAKING);
    private LegacyEnchantments() {}
    public static boolean ownsSoulRoll(Object event){return BladeEvents.ownsSoulRoll(event);}

    public static boolean vanilla(Enchantment enchantment) {
        var id=net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        return id!=null && id.getNamespace().equals("minecraft");
    }
    public static boolean sword(Enchantment enchantment) { return SWORD.contains(enchantment); }
    public static boolean rare(Enchantment enchantment) { return RARE.contains(enchantment); }

    public static void postHit(Player player, ItemStack blade, Entity target, DamageSource source) {
        // 受击护甲（含荆棘）只走一次原生回调。
        if(target instanceof LivingEntity living)EnchantmentHelper.doPostHurtEffects(living,player);
        if(!LegacyDamage.holding(player,blade))return;
        for(var entry:blade.getAllEnchantments().entrySet()) {
            if(!LegacyDamage.holding(player,blade))return;
            var enchantment=entry.getKey();int amount=entry.getValue();
            if(enchantment==Enchantments.BANE_OF_ARTHROPODS) {
                if(amount>0 && target instanceof LivingEntity living && living.getMobType()==MobType.ARTHROPOD)
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20+player.getRandom().nextInt(10*amount),3));
            } else enchantment.doPostAttack(player,target,amount);
        }
    }

    public static void stand(SlashBladeEvent.BladeStandAttackEvent event) {
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(event.getDamageSource().getEntity()) || event.isCanceled()
                || !(event.getDamageSource().getEntity() instanceof Player player)
                || !(player.level() instanceof ServerLevel level))return;
        var soul=player.getMainHandItem();var stand=event.getBladeStand();var blade=event.getBlade();
        if(!(BladeApi.isProudSoul(soul)) || !soul.isEnchanted() || blade.isEmpty() || stand.isOnFire())return;
        var data=LegacyTags.copy(soul);
        if(data.contains("SpecialAttackType") || data.contains("SpecialEffectType"))return;
        float probability=soul.is(BladeApi.PROUDSOUL.get())?.5f:soul.is(BladeApi.PROUDSOUL_INGOT.get())?.75f
                :soul.is(BladeApi.PROUDSOUL_SPHERE.get()) || soul.is(BladeApi.PROUDSOUL_CRYSTAL.get())?1:.25f;
        var updated=new java.util.HashMap<>(EnchantmentHelper.getEnchantments(blade));
        // One draw is shared by every enchantment on this material, as in r87.
        float roll=player.getRandom().nextFloat();int cost=player.isCreative()?0:1;int spheres=0;boolean changed=false;
        for(var entry:EnchantmentHelper.getEnchantments(soul).entrySet()) {
            var enchantment=entry.getKey();int current=updated.getOrDefault(enchantment,0);
            int maximum=enchantment==Enchantments.UNBREAKING?5:enchantment.getMaxLevel();
            int next=current==0?1:Math.min(current+1,maximum);
            var extension=BladeEvents.enchant(blade,event.getSlashBladeState(),enchantment,next,probability,cost,event);
            if(extension.canceled()){event.setCanceled(true);return;}
            cost=Math.max(0,extension.cost());
            if(roll<extension.probability()) {
                updated.put(extension.enchantment(),extension.level());changed=true;
                if(current>=maximum && extension.probability()>.7f)spheres++;
            }
            if(!extension.tryNext())break;
        }
        event.setCanceled(true);
        if(player.getMainHandItem()!=soul || stand.getItem()!=blade || soul.getCount()<cost)return;
        soul.shrink(cost);EnchantmentHelper.setEnchantments(updated,blade);
        stand.setItem(blade); // Mark the frame's synchronized stack dirty for observing clients.
        for(int i=0;i<spheres;i++) {
            var sphere=new ItemStack(BladeApi.PROUDSOUL_SPHERE.get());var tag=new CompoundTag();
            var state=event.getSlashBladeState();
            tag.putString("SpecialAttackType",LegacyNamedBlades.correctedArt(state.getTranslationKey(),state.getSlashArtsKey()).toString());
            sphere.setTag(tag);stand.spawnAtLocation(sphere);
        }
        if(changed)level.sendParticles(ParticleTypes.CRIT,stand.getX(),stand.getY()+1,stand.getZ(),12,.2,.3,.2,.1);
    }
}
