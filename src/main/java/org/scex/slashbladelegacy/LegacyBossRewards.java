package org.scex.slashbladelegacy;

import java.util.WeakHashMap;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;

/** r87 DefeatTheBoss, reached after a successful blade hit finishes killing its target. */
public final class LegacyBossRewards {
    private static final TagKey<EntityType<?>> BOSSES=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"legacy_bosses"));
    private static final WeakHashMap<LivingEntity,Boolean> REWARDED=new WeakHashMap<>();
    private LegacyBossRewards() {}
    public static void hit(ItemStack blade,LivingEntity target,LivingEntity attacker) {
        if(!LegacyCompat.isEnabled(LegacyCompat.LEGACY_COMBAT) || !(attacker instanceof ServerPlayer player)
                || BladeStateAccess.of(blade).isEmpty() || target.isAlive() || target.deathTime!=0 || !(target instanceof Mob))return;
        boolean boss=target instanceof WitherBoss || target instanceof EnderDragon || target.getType().is(BOSSES);
        if(!(target instanceof Enemy) && !boss || !boss && !target.hasCustomName() || REWARDED.put(target,true)!=null)return;
        var soul=new ItemStack(SlashBladeItems.PROUDSOUL.get());
        soul.enchant(player.registryAccess().holderOrThrow(LegacyEnchantments.RARE.get(player.getRandom().nextInt(LegacyEnchantments.RARE.size()))),1);
        player.spawnAtLocation(soul);
        if(boss) {
            var keys=LegacyBladeSouls.POOL;
            var named=LegacyBladeSouls.crystal(player,keys.get(player.getRandom().nextInt(keys.size())));
            if(!named.isEmpty())player.spawnAtLocation(named);
        }
    }
}
