package org.scex.slashbladelegacy;

import java.util.function.Consumer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import org.apache.commons.lang3.mutable.MutableFloat;

/** Apply old Unbreaking before the framework evaluates a break, exactly once. */
public final class LegacyDurability {
    /** Energy-backed items must break at zero EU; the last ordinary durability point is not free EU. */
    public static final net.minecraft.tags.TagKey<Item> FRAMEWORK_BOUNDARY=net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"framework_durability_boundary"));
    private LegacyDurability() {}
    public static void hurt(ItemStack blade,int amount,ServerLevel level,LivingEntity user,Consumer<Item> onBreak) {
        if(!blade.isDamageableItem() || user!=null && user.hasInfiniteMaterials() || amount<=0)return;
        int unbreaking=blade.getEnchantmentLevel(level.registryAccess().holderOrThrow(Enchantments.UNBREAKING));
        var random=user==null?level.getRandom():user.getRandom();
        if(unbreaking>0){int prevented=0;for(int i=0;i<amount;i++)if(random.nextInt(unbreaking+1)>0)prevented++;amount-=prevented;}
        var adjusted=new MutableFloat(amount);
        EnchantmentHelper.runIterationOnItem(blade,(enchantment,lvl)->{
            if(!enchantment.is(Enchantments.UNBREAKING))enchantment.value().modifyDurabilityChange(level,lvl,blade,adjusted);
        });
        amount=adjusted.intValue();if(amount<=0)return;
        Consumer<Item> callback=item->LegacyBroken.callback(blade,user,onBreak,item);
        amount=blade.getItem().damageItem(blade,amount,user,callback);
        if(amount<=0 || blade.isEmpty())return;
        if(user instanceof ServerPlayer player)CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player,blade,blade.getDamageValue()+amount);
        int damage=blade.getDamageValue()+amount;blade.setDamageValue(damage);
        if(damage>blade.getMaxDamage()){var item=blade.getItem();blade.shrink(1);callback.accept(item);}
    }
}
