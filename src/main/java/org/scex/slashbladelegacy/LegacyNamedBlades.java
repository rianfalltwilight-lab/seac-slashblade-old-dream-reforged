package org.scex.slashbladelegacy;

import java.util.Comparator;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 修正指定刀的默认 SA。保留已转移的 SA。 */
public final class LegacyNamedBlades {
    public static final DeferredRegister<SlashArts> ARTS=DeferredRegister.create(SlashArts.REGISTRY_KEY,LegacyCompat.MOD_ID);
    public static final DeferredRegister<SpecialEffect> EFFECTS=DeferredRegister.create(SpecialEffect.REGISTRY_KEY,LegacyCompat.MOD_ID);
    public static final ResourceLocation PRAYER=id("prayer"),TERRA=id("terra_swords"),MANA_BURST=id("mana_burst"),MANA_REPAIR=id("mana_pool_repair");
    public static final ResourceLocation BURST_DRIVE=ResourceLocation.parse("slashblade_addon:burst_drive");
    private static final ResourceLocation PRAYER_COMBO=id("sa_prayer"),TERRA_COMBO=id("sa_terra_swords");
    private static final ResourceLocation KOSEKI_DEFAULT=ResourceLocation.parse("slashblade:drive_vertical"),
            KAMUY_DEFAULT=ResourceLocation.parse("slashblade:circle_slash"),TERRA_DEFAULT=ResourceLocation.parse("slashblade_addon:rapid_blistering_swords");
    private static final String SEEDED="slashblade_legacy_compat.terra_effects_added";
    static {
        // 动作回调负责扣费。原生释放不再扣费。
        ARTS.register(PRAYER.getPath(),()->new SlashArts(user->combo(PRAYER)).setProudSoulCost(0));
        ARTS.register(TERRA.getPath(),()->new SlashArts(user->combo(TERRA)).setProudSoulCost(0));
        EFFECTS.register(MANA_BURST.getPath(),()->new SpecialEffect(10,true,true));
        EFFECTS.register(MANA_REPAIR.getPath(),()->new SpecialEffect(1,true,true));
        register(PRAYER);register(TERRA);
    }
    private LegacyNamedBlades(){}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,path);}
    private static ResourceLocation combo(ResourceLocation art){return art.equals(PRAYER)?PRAYER_COMBO:TERRA_COMBO;}
    public static void initialize(){}
    private static void register(ResourceLocation art) {
        LegacyCombat.COMBOS.register(combo(art).getPath(),()->ComboState.Builder.newInstance().startAndEnd(0,0).speed(1).priority(50)
                .timeout(LegacyMove.KIRIOROSI.resetTicks*50).next(user->ComboStateRegistry.NONE.getId())
                .nextOfTimeout(user->LegacyCombat.id(LegacyMove.NOUTOU))
                .clickAction(user->{if(user instanceof Player player){if(art.equals(PRAYER))prayer(player);else terra(player);}}).build());
    }
    public static LegacyMove visual(ResourceLocation key){return key.equals(PRAYER_COMBO)?LegacyMove.NOUTOU:key.equals(TERRA_COMBO)?LegacyMove.KIRIOROSI:null;}
    public static ResourceLocation correctedArt(String bladeName,ResourceLocation original) {
        if(bladeName.equals("item.slashblade.koseki") && original.equals(KOSEKI_DEFAULT))return LegacyArts.key(LegacyArts.Art.WITHER);
        if(bladeName.equals("item.slashblade_addon.kamuy_none") && original.equals(KAMUY_DEFAULT))return PRAYER;
        if(bladeName.equals("item.slashblade_addon.terra_blade") && original.equals(TERRA_DEFAULT))return TERRA;
        return original;
    }
    public static SlashArts art(ISlashBladeState state,ItemStack blade) {
        if(!LegacyMode.legacy(blade))return null;
        var old=state.getSlashArtsKey();var corrected=correctedArt(state.getTranslationKey(),old);
        return old.equals(corrected)?null:SlashArtsRegistry.REGISTRY.get(corrected);
    }
    public static void copyEffectPolicy(mods.flammpfeil.slashblade.event.bladestand.PreCopySpecialEffectFromBladeEvent event) {
        if(!event.isCanceled() && LegacyMode.legacy(event.getOriginalEvent().getDamageSource().getEntity())
                && event.getSEKey().equals(BURST_DRIVE)) {
            // 旧附属只实现 ISpecialEffect，默认允许复制和移除。
            event.setCopiable(true);event.setRemovable(true);
        }
    }
    /** 只补一次默认 SE。移除后不再补回。 */
    public static void seed(ItemStack blade) {
        var data=BladeStateAccess.getData(blade).orElse(null);
        if(data!=null && !data.translationKey().equals("item.slashblade_addon.terra_blade"))return;
        if(!LegacyMode.legacy(blade))return;
        // 缺少组件时仍经 of 初始化新刀。
        var state=BladeStateAccess.of(blade).orElse(null);
        if(state!=null)seed(blade,state);
    }
    public static void seed(ItemStack blade,ISlashBladeState state) {
        var data=blade.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY);
        if(data.contains(SEEDED) || !state.getTranslationKey().equals("item.slashblade_addon.terra_blade") || !LegacyMode.legacy(blade))return;
        state.addSpecialEffect(MANA_BURST);state.addSpecialEffect(MANA_REPAIR);
        CustomData.update(DataComponents.CUSTOM_DATA,blade,tag->tag.putBoolean(SEEDED,true));
    }
    public static void prayer(Player player) {
        if(!(player.level() instanceof ServerLevel level))return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);if(state==null)return;
        boolean success=state.getProudSoulCount()>=10;
        if(success){state.setProudSoulCount(state.getProudSoulCount()-10);var rank=player.getData(CapabilityConcentrationRank.RANK_POINT);LegacyRank.awardAction(player,rank,"Provoke",-1);}
        level.sendParticles(success?ParticleTypes.WITCH:ParticleTypes.LARGE_SMOKE,player.getX(),player.getY()+.25,player.getZ(),20,.5,.25,.5,.02);
    }
    public static void terra(Player player) {
        LegacySheathingRepair.begin(player,LegacyMove.KIRIOROSI);player.swing(InteractionHand.MAIN_HAND);
        if(player.level().isClientSide)return;
        var blade=player.getMainHandItem();var state=BladeStateAccess.of(blade).orElse(null);if(state==null)return;
        var target=target(player,state);if(target==null)return;
        if(state.getProudSoulCount()>=40)state.setProudSoulCount(state.getProudSoulCount()-40);else LegacyAdditionalAttack.wear(blade,10,player);
        if(!LegacyDamage.holding(player,blade))return;
        if(LegacyDamage.hit(player,target,LegacyMove.SLASH_DIM,target.getBoundingBox(),true,1,"PhantomSword",.2f)) {
            player.crit(target);target.setDeltaMovement(Vec3.ZERO);target.hurtTime=0;target.invulnerableTime=0;
            mods.flammpfeil.slashblade.ability.StunManager.setStun(target);
        }
        if(!LegacyDamage.holding(player,blade))return;
        int count=3;
        // 每增加一发消耗 100 mana，最多七发。
        for(int i=1;i<LegacyCombat.rank(player) && count<7;i++)if(LegacyManaBridge.consume(player,100))count++;
        float damage=1+state.getAttackAmplifier()*LegacyArts.power(player,blade)/5f;
        for(int i=0;i<count;i++) {
            var sword=new LegacyPhantomSword(SummonedBladeMode.SWORD.get(),player.level());
            sword.initializeTerra(player,LegacyRangeAttack.sourceId(blade),i,damage,target);player.level().addFreshEntity(sword);
        }
    }
    private static LivingEntity target(Player player,ISlashBladeState state) {
        var locked=state.getTargetEntity(player.level());
        if(locked instanceof LivingEntity living && player.distanceToSqr(living)<900 && LegacyTargets.attackable(player,living))return living;
        for(int distance=2;distance<20;distance+=2) {
            var found=LegacyTargets.within(player,player.getBoundingBox().inflate(2,.25,2).move(player.getLookAngle().scale(distance))).stream()
                    .filter(entity->entity instanceof LivingEntity && player.distanceToSqr(entity)<900).min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
            if(found instanceof LivingEntity living)return living;
        }
        return null;
    }
}
