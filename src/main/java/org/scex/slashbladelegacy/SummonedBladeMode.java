package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.WeakHashMap;
import java.util.UUID;

/** Server input uses the existing authoritative SlashBlade input channel. */
public final class SummonedBladeMode {
    public static final String MODE="RangeAttackType";
    public static final String SOURCE="scex_legacy_source";
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,LegacyCompat.MOD_ID);
    public static final DeferredHolder<EntityType<?>,EntityType<LegacyUpthrust>> UPTHRUST=ENTITIES.register("upthrust", () ->
            EntityType.Builder.<LegacyUpthrust>of(LegacyUpthrust::new,net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build("slashblade_legacy_compat:upthrust"));
    public static final DeferredHolder<EntityType<?>,EntityType<LegacyDrive>> DRIVE=ENTITIES.register("drive", () ->
            EntityType.Builder.<LegacyDrive>of(LegacyDrive::new,net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1,2).clientTrackingRange(8).updateInterval(1).build("slashblade_legacy_compat:drive"));
    public static final DeferredHolder<EntityType<?>,EntityType<LegacySummonedBlade>> BLADE=ENTITIES.register("summoned_blade", () ->
            EntityType.Builder.<LegacySummonedBlade>of(LegacySummonedBlade::new,MobCategory.MISC)
                    .sized(0.25f,0.25f).clientTrackingRange(8).updateInterval(1).build("slashblade_legacy_compat:summoned_blade"));
    private record Press(ItemStack blade,long tick,ResourceLocation dimension) {}
    private static final WeakHashMap<ServerPlayer,Press> PRESSES=new WeakHashMap<>();
    private static final WeakHashMap<ServerPlayer,Long> LAST_SHOT=new WeakHashMap<>();
    public static boolean enabled(ItemStack stack) {
        return LegacyCompat.SUMMONED_BLADE.get() && BladeStateAccess.of(stack).isPresent()
                && stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean(MODE);
    }
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!LegacyCompat.SUMMONED_BLADE.get() || event.getHand()!=InteractionHand.MAIN_HAND
                || !(event.getTarget() instanceof BladeStandEntity stand)) return;
        var soul=event.getItemStack();
        var data=soul.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if (!soul.is(SlashBladeItems.PROUDSOUL_SPHERE.get()) || soul.isEnchanted()
                || data.contains("SpecialAttackType") || data.contains("SpecialEffectType")) return;
        var blade=stand.getItem();
        if (BladeStateAccess.of(blade).isEmpty() || blade.getEnchantmentLevel(event.getLevel().registryAccess()
                .holderOrThrow(Enchantments.POWER))<=0) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!event.getLevel().isClientSide) {
            var replacement=blade.copy();
            var tag=replacement.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            tag.putBoolean(MODE,!tag.getBoolean(MODE));
            replacement.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            stand.setItem(replacement);
            if (!event.getEntity().getAbilities().instabuild) soul.shrink(1);
            event.getEntity().displayClientMessage(Component.translatable(tag.getBoolean(MODE)
                    ? "slashblade_legacy_compat.sb.blade" : "slashblade_legacy_compat.sb.sword"),true);
        }
    }
    public static void tooltip(ItemTooltipEvent event) {
        if (enabled(event.getItemStack())) event.getToolTip().add(Component.translatable("slashblade_legacy_compat.sb.blade"));
    }
    public static void attackStand(mods.flammpfeil.slashblade.event.SlashBladeEvent.BladeStandAttackEvent event) {
        if(!LegacyCompat.SUMMONED_BLADE.get() || !(event.getDamageSource().getEntity() instanceof ServerPlayer player)
                || event.getDamageSource().getDirectEntity()!=player)return;
        // Resharpened's soul-on-stand operations are LEFT-click attacks, not EntityInteract.
        var stand=event.getBladeStand();
        var interaction=new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,stand);
        interact(interaction);
        if(interaction.isCanceled())event.setCanceled(true);
    }
    public static void input(InputCommandEvent event) {
        var player=event.getEntity();
        boolean was=event.getOld().contains(InputCommand.M_DOWN), now=event.getCurrent().contains(InputCommand.M_DOWN);
        long tick=player.level().getGameTime();
        if (!was && now) {
            if (enabled(player.getMainHandItem())) PRESSES.put(player,new Press(player.getMainHandItem(),tick,player.level().dimension().location()));
            else PRESSES.remove(player);
        }
        if (!was || now) return;
        var press=PRESSES.remove(player);
        if (press==null || press.blade()!=player.getMainHandItem() || !player.isAlive()
                || !press.dimension().equals(player.level().dimension().location()) || tick<press.tick()
                || tick-press.tick()>=10 || tick-LAST_SHOT.getOrDefault(player,-100L)<2) return;
        var blade=press.blade();
        var state=BladeStateAccess.of(blade).orElse(null);
        if (!enabled(blade) || state==null || state.isBroken() || state.isSealed()
                || !SwordType.from(blade).contains(SwordType.BEWITCHED)) return;
        int power=blade.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.POWER));
        if (power<=0 || state.getProudSoulCount()<1) return;
        var rank=player.getData(CapabilityConcentrationRank.RANK_POINT).getRank(tick);
        if (rank==null || rank.level<3) power=1;
        var source=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if (!source.hasUUID(SOURCE)) {
            source.putUUID(SOURCE,UUID.randomUUID()); blade.set(DataComponents.CUSTOM_DATA,CustomData.of(source));
        }
        var projectile=new LegacySummonedBlade(BLADE.get(),player.level());
        projectile.initialize(player,power,state.getColorCode(),source.getUUID(SOURCE),state.getTargetEntity(player.level()));
        if (player.level().addFreshEntity(projectile)) {
            state.setProudSoulCount(state.getProudSoulCount()-1);
            LAST_SHOT.put(player,tick);
            player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    net.minecraft.sounds.SoundSource.PLAYERS,0.35f,1f);
        }
    }
}
