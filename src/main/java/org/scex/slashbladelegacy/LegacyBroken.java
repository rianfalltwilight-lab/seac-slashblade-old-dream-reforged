package org.scex.slashbladelegacy;

import java.util.*;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;

/** r87 break rewards operate on the real damaged blade, including native callbacks carrying a copy. */
public final class LegacyBroken {
    private static final class Break {final ItemStack blade;boolean handled;Break(ItemStack blade){this.blade=blade;}}
    private static final ThreadLocal<Break> CURRENT=new ThreadLocal<>();
    private LegacyBroken() {}
    public static void callback(ItemStack blade,LivingEntity user,Consumer<Item> original,Item item) {
        Break before=CURRENT.get(),active=new Break(blade);CURRENT.set(active);
        try {original.accept(item);if(!active.handled && user!=null)reward(blade,user);}
        finally {if(before==null)CURRENT.remove();else CURRENT.set(before);}
    }
    public static void reward(ItemStack fallback,LivingEntity user) {
        var current=CURRENT.get();
        if(current!=null){if(current.handled)return;current.handled=true;}
        var blade=current==null?fallback:current.blade;
        if(user.level().isClientSide)return;
        var state=BladeStateAccess.of(blade).orElse(null);if(state==null)return;
        int souls=Math.max(0,state.getProudSoulCount());int spent=souls>1000?Math.min(8,Math.max(0,(souls-800)/100)):souls/100;
        state.setProudSoulCount(souls-spent*100);user.spawnAtLocation(new ItemStack(SlashBladeItems.PROUDSOUL.get(),spent+1));
        var enchantments=EnchantmentHelper.getEnchantmentsForCrafting(blade);if(enchantments.isEmpty())return;
        var lookup=user.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var unbreaking=lookup.getOrThrow(Enchantments.UNBREAKING);int durability=enchantments.getLevel(unbreaking);
        boolean rare=durability>0 && enchantments.getLevel(lookup.getOrThrow(Enchantments.LOOTING))>0;
        if(!rare)for(int i=0;i<durability;i++){rare=user.getRandom().nextFloat()<.3f;if(rare)break;}
        var pool=new ArrayList<Holder<Enchantment>>();
        if(!rare){
            LegacyEnchantments.SWORD.forEach(key->pool.add(lookup.getOrThrow(key)));
            var iron=new ItemStack(Items.IRON_SWORD);
            lookup.listElements().filter(e->!LegacyEnchantments.vanilla(e) && iron.supportsEnchantment(e)).forEach(pool::add);
        }
        // The duplicate Unbreaking entry in the normal pool is intentional in r87.
        LegacyEnchantments.RARE.forEach(key->pool.add(lookup.getOrThrow(key)));
        var tiny=new ItemStack(SlashBladeItems.PROUDSOUL_TINY.get());tiny.enchant(pool.get(user.getRandom().nextInt(pool.size())),1);user.spawnAtLocation(tiny);
        if(enchantments.size()>5) {
            var updated=new ItemEnchantments.Mutable(enchantments);
            if(durability>0) {
                updated.set(unbreaking,durability-1);
                var extra=new ItemStack(SlashBladeItems.PROUDSOUL_TINY.get());
                extra.enchant(lookup.getOrThrow(LegacyEnchantments.RARE.get(user.getRandom().nextInt(LegacyEnchantments.RARE.size()))),1);user.spawnAtLocation(extra);
            } else {
                var entries=new ArrayList<>(enchantments.entrySet());var selected=entries.get(user.getRandom().nextInt(entries.size()));
                updated.set(selected.getKey(),0);var soul=new ItemStack(SlashBladeItems.PROUDSOUL.get());
                soul.enchant(selected.getKey(),selected.getIntValue());user.spawnAtLocation(soul);
            }
            EnchantmentHelper.setEnchantments(blade,updated.toImmutable());
        }
    }
}
