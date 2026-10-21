package org.scex.slashbladelegacy;

import java.util.function.Consumer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Run native damage protection before the legacy break check. */
public final class LegacyDurability {
    /** Energy-backed items must break at zero EU; the last ordinary durability point is not free EU. */
    public static final net.minecraft.tags.TagKey<Item> FRAMEWORK_BOUNDARY=net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,new net.minecraft.resources.ResourceLocation(LegacyCompat.MOD_ID,"framework_durability_boundary"));
    private record Pending(ItemStack blade,LivingEntity user,Consumer<LivingEntity> onBreak) {}
    private static final ThreadLocal<Pending> PENDING=new ThreadLocal<>();
    private static final ResourceLocation INDESTRUCTIBLE=new ResourceLocation("indestructible","indestructible");
    private LegacyDurability() {}
    public static void hurt(ItemStack blade,int amount,LivingEntity user,Consumer<LivingEntity> onBreak) {
        if(!blade.isDamageableItem() || user instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().instabuild || amount<=0)return;
        var previous=PENDING.get();
        PENDING.set(new Pending(blade,user,onBreak));
        try {
            blade.hurt(amount,user.getRandom(),user instanceof ServerPlayer player?player:null);
        } finally {
            if(previous==null)PENDING.remove();else PENDING.set(previous);
        }
    }
    /** Use the amount left by native protection and Unbreaking. */
    public static boolean applyPending(ItemStack blade,int amount) {
        var pending=PENDING.get();
        if(pending==null || pending.blade()!=blade)return false;
        // Callbacks can cause another damage operation on this stack.
        PENDING.remove();
        apply(blade,amount,pending.user(),pending.onBreak());
        return true;
    }
    private static void apply(ItemStack blade,int amount,LivingEntity user,Consumer<LivingEntity> onBreak) {
        if(amount<=0 || blade.isEmpty())return;
        Consumer<LivingEntity> callback=entity->LegacyBroken.callback(blade,user,onBreak,entity);
        amount=blade.getItem().damageItem(blade,amount,user,callback);
        if(amount<=0 || blade.isEmpty())return;
        if(user instanceof ServerPlayer player)CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player,blade,blade.getDamageValue()+amount);
        int damage=blade.getDamageValue()+amount;blade.setDamageValue(damage);
        if(damage>blade.getMaxDamage()){blade.shrink(1);callback.accept(user);}
    }
    /** The fixed Super cost does not use the random wear path. */
    public static boolean protectsFixedCost(ItemStack blade) {
        if(blade.hasTag() && blade.getTag().getBoolean("Unbreakable"))return true;
        var enchantment=ForgeRegistries.ENCHANTMENTS.getValue(INDESTRUCTIBLE);
        return enchantment!=null && EnchantmentHelper.getItemEnchantmentLevel(enchantment,blade)>0;
    }
}
