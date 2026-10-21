package org.scex.slashbladelegacy;

import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.item.SwordType;
import org.scex.slashbladelegacy.BladeApi;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import java.util.WeakHashMap;
import java.util.UUID;

/** Server input uses the existing authoritative SlashBlade input channel. */
public final class SummonedBladeMode {
    public static final String MODE="RangeAttackType";
    public static final String SOURCE="scex_legacy_source";
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,LegacyCompat.MOD_ID);
    public static final RegistryObject<EntityType<LegacyArtEntity>> ART=ENTITIES.register("art", () ->
            EntityType.Builder.<LegacyArtEntity>of(LegacyArtEntity::new,MobCategory.MISC)
                    .sized(4,4).clientTrackingRange(12).updateInterval(1).build("slashblade_legacy_compat:art"));
    public static final RegistryObject<EntityType<LegacyPhantomSword>> SWORD=ENTITIES.register("phantom_sword", () ->
            EntityType.Builder.<LegacyPhantomSword>of(LegacyPhantomSword::new,MobCategory.MISC)
                    .sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build("slashblade_legacy_compat:phantom_sword"));
    public static final RegistryObject<EntityType<LegacyUpthrust>> UPTHRUST=ENTITIES.register("upthrust", () ->
            EntityType.Builder.<LegacyUpthrust>of(LegacyUpthrust::new,net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).build("slashblade_legacy_compat:upthrust"));
    public static final RegistryObject<EntityType<LegacyDrive>> DRIVE=ENTITIES.register("drive", () ->
            EntityType.Builder.<LegacyDrive>of(LegacyDrive::new,net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1,2).clientTrackingRange(8).updateInterval(1).build("slashblade_legacy_compat:drive"));
    public static final RegistryObject<EntityType<LegacySummonedBlade>> BLADE=ENTITIES.register("summoned_blade", () ->
            EntityType.Builder.<LegacySummonedBlade>of(LegacySummonedBlade::new,MobCategory.MISC)
                    .sized(0.25f,0.25f).clientTrackingRange(8).updateInterval(1).build("slashblade_legacy_compat:summoned_blade"));
    private record Press(ItemStack blade,long tick,ResourceLocation dimension) {}
    private static final WeakHashMap<ServerPlayer,Press> PRESSES=new WeakHashMap<>();
    private static final WeakHashMap<ServerPlayer,Long> LAST_SHOT=new WeakHashMap<>();
    public static boolean enabled(ItemStack stack) {
        return org.scex.slashbladelegacy.LegacyMode.enabled(stack,LegacyCompat.SUMMONED_BLADE) && BladeStateAccess.of(stack).isPresent()
                && LegacyTags.read(stack).getBoolean(MODE);
    }
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!org.scex.slashbladelegacy.LegacyMode.enabled(event.getEntity(),LegacyCompat.SUMMONED_BLADE) || event.getHand()!=InteractionHand.MAIN_HAND
                || !(event.getTarget() instanceof BladeStandEntity stand)) return;
        var soul=event.getItemStack();
        var data=LegacyTags.read(soul);
        if (!soul.is(BladeApi.PROUDSOUL_SPHERE.get()) || soul.isEnchanted()
                || data.contains("SpecialAttackType") || data.contains("SpecialEffectType")) return;
        var blade=stand.getItem();
        if (BladeStateAccess.of(blade).isEmpty() || blade.getEnchantmentLevel(Enchantments.POWER_ARROWS)<=0) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!event.getLevel().isClientSide) {
            var replacement=blade.copy();
            var tag=LegacyTags.copy(replacement);
            tag.putBoolean(MODE,!tag.getBoolean(MODE));
            replacement.setTag(tag);
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
        if(event.isCanceled())return;
        if(!org.scex.slashbladelegacy.LegacyMode.enabled(event.getDamageSource().getEntity(),LegacyCompat.SUMMONED_BLADE) || !(event.getDamageSource().getEntity() instanceof ServerPlayer player)
                || event.getDamageSource().getDirectEntity()!=player)return;
        // Resharpened's soul-on-stand operations are LEFT-click attacks, not EntityInteract.
        var stand=event.getBladeStand();
        var interaction=new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,stand);
        interact(interaction);
        if(interaction.isCanceled())event.setCanceled(true);
    }
    public static void input(BladeInput event) {
        if(org.scex.slashbladelegacy.LegacyMode.modern(event.getEntity()))return;
        if(LegacyRangeAttack.enabled()) { LegacyRangeAttack.input(event);return; }
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
        int power=blade.getEnchantmentLevel(Enchantments.POWER_ARROWS);
        if (power<=0 || state.getProudSoulCount()<1) return;
        var rank=LegacyCapabilities.rank(player).getRank(tick);
        if (rank==null || rank.level<3) power=1;
        var source=LegacyTags.read(blade);
        if (!source.hasUUID(SOURCE)) {
            source=source.copy();
            source.putUUID(SOURCE,UUID.randomUUID()); blade.setTag(source);
        }
        var projectile=new LegacySummonedBlade(BLADE.get(),player.level());
        projectile.initialize(player,power,LegacyRangeAttack.color(state),source.getUUID(SOURCE),state.getTargetEntity(player.level()));
        if (player.level().addFreshEntity(projectile)) {
            state.setProudSoulCount(state.getProudSoulCount()-1);
            LAST_SHOT.put(player,tick);
            player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    net.minecraft.sounds.SoundSource.PLAYERS,0.35f,1f);
        }
    }
    public static void clear(net.minecraft.world.entity.player.Player player){PRESSES.remove(player);LAST_SHOT.remove(player);}
}
