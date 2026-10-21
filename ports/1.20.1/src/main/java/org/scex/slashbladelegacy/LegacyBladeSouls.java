package org.scex.slashbladelegacy;

import java.util.*;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.SwordType;
import org.scex.slashbladelegacy.BladeApi;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** r87 burning-stand extraction and named crystals, resolved through actual framework definitions. */
public final class LegacyBladeSouls {
    public static final String DEFINITION="Legacy17BladeSoul",BIRTH="Legacy17SoulBirth";
    private static final String LOT="Legacy17LastLotNumber",FIRE="Legacy17StandFireUntil",FLOAT="Legacy17SoulFloatUntil";
    // The six native named souls actually registered in r87. Optional wrapping mods are not installed.
    public static final List<ResourceLocation> POOL=List.of("agito_rust","orotiagito_rust","fox_white","fox_black","yasha","yasha_true")
            .stream().map(id->new ResourceLocation("slashblade",id)).toList();
    private LegacyBladeSouls() {}
    public static void ignite(mods.flammpfeil.slashblade.entity.BladeStandEntity stand) {
        stand.setRemainingFireTicks(400);stand.setSharedFlagOnFire(true);
        stand.getPersistentData().putLong(FIRE,stand.level().getGameTime()+400);
    }
    public static boolean named(ItemStack soul){return soul.is(BladeApi.PROUDSOUL_CRYSTAL.get()) && LegacyTags.read(soul).contains(DEFINITION);}
    public static ItemStack crystal(ServerPlayer player,ResourceLocation key) {
        var definition=player.level().registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).get(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,key)).orElse(null);
        if(definition==null)return ItemStack.EMPTY;
        var result=new ItemStack(BladeApi.PROUDSOUL_CRYSTAL.get());var data=new CompoundTag();data.putString(DEFINITION,key.toString());
        result.setTag(data);
        result.setHoverName(Component.translatable("slashblade_legacy_compat.blade_soul",Component.translatable(definition.value().getTranslationKey())));return result;
    }
    public static void stand(SlashBladeEvent.BladeStandAttackEvent event) {
        if(!org.scex.slashbladelegacy.LegacyMode.legacy(event.getDamageSource().getEntity()) || event.isCanceled()
                || !(event.getDamageSource().getEntity() instanceof ServerPlayer player) || event.getDamageSource().getDirectEntity()!=player)return;
        var stand=event.getBladeStand();var blade=event.getBlade();var soul=player.getMainHandItem();
        if(blade.isEmpty())return;
        if(soul.is(BladeApi.PROUDSOUL_TINY.get()) && !soul.isEnchanted()) {
            event.setCanceled(true);
            if(stand.isOnFire()){stand.clearFire();stand.setSharedFlagOnFire(false);stand.getPersistentData().remove(FIRE);stand.playSound(SoundEvents.ENDER_DRAGON_FLAP,.5f,.5f);return;}
            // r87 consumes even in creative. Extinguishing remains free.
            soul.shrink(1);ignite(stand);event.getSlashBladeState().setProudSoulCount(event.getSlashBladeState().getProudSoulCount()+50);
            stand.setItem(blade);stand.playSound(SoundEvents.FIRECHARGE_USE,.5f,1);return;
        }
        if(!soul.is(BladeApi.PROUDSOUL.get()) || !stand.isOnFire())return;
        event.setCanceled(true);var state=event.getSlashBladeState();if(state.getProudSoulCount()<400)return;
        ItemStack result;int next=-1;
        if(soul.isEnchanted()) {
            var registry=player.level().registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
            var pool=POOL.stream().filter(key->registry.get(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,key)).isPresent()).toList();
            if(pool.isEmpty())return;
            boolean dual=stand.currentType==BladeApi.BLADESTAND_2.get();
            next=dual?Math.floorMod((long)stand.getPersistentData().getInt(LOT)+1,pool.size()):-1;
            result=crystal(player,pool.get(dual?next:player.getRandom().nextInt(pool.size())));
        } else result=new ItemStack(BladeApi.PROUDSOUL_CRYSTAL.get());
        if(result.isEmpty())return;
        var data=LegacyTags.copy(result);data.putLong(BIRTH,player.level().getGameTime());result.setTag(data);
        var drop=new ItemEntity(player.level(),stand.getX(),stand.getY()+2,stand.getZ(),result);drop.setPickUpDelay(10);
        var floating=drop.getPersistentData();floating.putLong(FLOAT,player.level().getGameTime()+1000);floating.putDouble("Legacy17FloatX",drop.getX());floating.putDouble("Legacy17FloatY",drop.getY());floating.putDouble("Legacy17FloatZ",drop.getZ());
        if(!player.level().addFreshEntity(drop))return;
        state.setProudSoulCount(state.getProudSoulCount()-400);soul.shrink(1);stand.setItem(blade);
        if(next>=0)stand.getPersistentData().putInt(LOT,next);
        player.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT,stand.getX(),stand.getY()+1,stand.getZ(),12,.2,.3,.2,.1);
    }
    public static void tick(net.minecraft.world.entity.Entity entity) {
        // getPersistentData creates and saves a tag. Read it only for stands and soul crystals, which can carry these timers.
        if(entity.level().isClientSide || !(entity instanceof mods.flammpfeil.slashblade.entity.BladeStandEntity
                || entity instanceof ItemEntity drop && drop.getItem().is(BladeApi.PROUDSOUL_CRYSTAL.get())))return;
        var data=entity.getPersistentData();long now=entity.level().getGameTime();
        if(entity instanceof mods.flammpfeil.slashblade.entity.BladeStandEntity && data.contains(FIRE)) {
            long remaining=data.getLong(FIRE)-now;
            if(remaining<=0 || remaining>400 || !LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){entity.clearFire();entity.setSharedFlagOnFire(false);data.remove(FIRE);}
            else {entity.setRemainingFireTicks((int)remaining);entity.setSharedFlagOnFire(true);}
        }else if(entity instanceof ItemEntity item && data.contains(FLOAT)) {
            long remaining=data.getLong(FLOAT)-now;
            if(remaining<=0 || remaining>1000 || !LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT)){data.remove(FLOAT);return;}
            item.setPos(data.getDouble("Legacy17FloatX"),data.getDouble("Legacy17FloatY"),data.getDouble("Legacy17FloatZ"));item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if(item.tickCount>5){var tag=LegacyTags.copy(item.getItem());if(tag.contains(BIRTH)){tag.remove(BIRTH);item.getItem().setTag(tag);}}
            if(item.tickCount%5==0)((net.minecraft.server.level.ServerLevel)item.level()).sendParticles(ParticleTypes.PORTAL,item.getX(),item.getY()+.25,item.getZ(),2,.25,.25,.25,.025);
        }
    }
    /** Only an unnamed native blank may change identity; existing addon blades cannot be overwritten. */
    public static boolean apply(ItemStack output,ItemStack soul,net.minecraft.core.HolderLookup.Provider registries) {
        var state=BladeStateAccess.of(output).orElseThrow();
        var blank=BladeStateAccess.of(new ItemStack(BladeApi.SLASHBLADE.get())).orElseThrow();
        if(!output.is(BladeApi.SLASHBLADE.get()) || !state.getTranslationKey().equals(blank.getTranslationKey()) || state.getProudSoulCount()<1000)return false;
        var key=ResourceLocation.tryParse(LegacyTags.read(soul).getString(DEFINITION));if(key==null)return false;
        var definition=registries.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).get(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,key)).orElse(null);
        if(definition==null || !BladeApi.getItemName(definition.value()).equals(new ResourceLocation("slashblade:slashblade")))return false;
        var template=definition.value().getBlade();if(template.isEmpty() || template.getItem()!=output.getItem())return false;
        var from=BladeStateAccess.of(template).orElseThrow();
        state.setTranslationKey(from.getTranslationKey());state.setBaseAttackModifier(from.getBaseAttackModifier());state.setMaxDamage(from.getMaxDamage());
        state.setSlashArtsKey(from.getSlashArtsKey());from.getModel().ifPresent(state::setModel);from.getTexture().ifPresent(state::setTexture);
        state.setCarryType(from.getCarryType());state.setColorCode(from.getColorCode());state.setEffectColorInverse(from.isEffectColorInverse());
        state.setDefaultBewitched(from.isDefaultBewitched());
        return true;
    }
}
