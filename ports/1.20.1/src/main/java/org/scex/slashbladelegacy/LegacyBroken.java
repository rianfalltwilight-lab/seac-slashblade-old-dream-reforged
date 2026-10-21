package org.scex.slashbladelegacy;

import java.util.*;
import java.util.function.Consumer;
import org.scex.slashbladelegacy.BladeStateAccess;
import org.scex.slashbladelegacy.BladeApi;
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
    public static void callback(ItemStack blade,LivingEntity user,Consumer<LivingEntity> original,LivingEntity brokenEntity) {
        Break before=CURRENT.get(),active=new Break(blade);CURRENT.set(active);
        try {original.accept(brokenEntity);if(!active.handled && user!=null)reward(blade,user);}
        finally {if(before==null)CURRENT.remove();else CURRENT.set(before);}
    }
    public static void reward(ItemStack fallback,LivingEntity user) {
        var current=CURRENT.get();
        if(current!=null){if(current.handled)return;current.handled=true;}
        var blade=current==null?fallback:current.blade;
        if(user.level().isClientSide)return;
        var state=BladeStateAccess.of(blade).orElse(null);if(state==null)return;
        int souls=Math.max(0,state.getProudSoulCount());int spent=souls>1000?Math.min(8,Math.max(0,(souls-800)/100)):souls/100;
        state.setProudSoulCount(souls-spent*100);user.spawnAtLocation(new ItemStack(BladeApi.PROUDSOUL.get(),spent+1));
        var enchantments=EnchantmentHelper.getEnchantments(blade);if(enchantments.isEmpty())return;
        var lookup=net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS;
        var unbreaking=Enchantments.UNBREAKING;int durability=enchantments.getOrDefault(unbreaking,0);
        boolean rare=durability>0 && enchantments.getOrDefault(Enchantments.MOB_LOOTING,0)>0;
        if(!rare)for(int i=0;i<durability;i++){rare=user.getRandom().nextFloat()<.3f;if(rare)break;}
        var pool=new ArrayList<Enchantment>();
        if(!rare){
            pool.addAll(LegacyEnchantments.SWORD);
            var iron=new ItemStack(Items.IRON_SWORD);
            lookup.getValues().stream().filter(e->!LegacyEnchantments.vanilla(e) && e.canEnchant(iron)).forEach(pool::add);
        }
        // The duplicate Unbreaking entry in the normal pool is intentional in r87.
        pool.addAll(LegacyEnchantments.RARE);
        var tiny=new ItemStack(BladeApi.PROUDSOUL_TINY.get());tiny.enchant(pool.get(user.getRandom().nextInt(pool.size())),1);user.spawnAtLocation(tiny);
        if(enchantments.size()>5) {
            var updated=new HashMap<>(enchantments);
            if(durability>0) {
                if(durability==1)updated.remove(unbreaking);else updated.put(unbreaking,durability-1);
                var extra=new ItemStack(BladeApi.PROUDSOUL_TINY.get());
                extra.enchant(LegacyEnchantments.RARE.get(user.getRandom().nextInt(LegacyEnchantments.RARE.size())),1);user.spawnAtLocation(extra);
            } else {
                var entries=new ArrayList<>(enchantments.entrySet());var selected=entries.get(user.getRandom().nextInt(entries.size()));
                updated.remove(selected.getKey());var soul=new ItemStack(BladeApi.PROUDSOUL.get());
                soul.enchant(selected.getKey(),selected.getValue());user.spawnAtLocation(soul);
            }
            EnchantmentHelper.setEnchantments(updated,blade);
        }
    }
}
