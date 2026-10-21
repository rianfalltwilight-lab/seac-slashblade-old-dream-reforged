package org.scex.slashbladelegacy;

import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.ReachModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;

/** 注册兼容功能，修正攻击与触及距离。 */
@Mod(LegacyCompat.MOD_ID)
public final class LegacyCompat {
    public static final String MOD_ID = "slashblade_legacy_compat";
    private static final java.util.UUID REACH = java.util.UUID.fromString("2D988C13-595B-4E58-B254-39BB6FA077FE");
    private static final java.util.UUID ATTACK = java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BROKEN_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue BROKEN_REACH;
    public static final ForgeConfigSpec.BooleanValue SUMMONED_BLADE;
    public static final ForgeConfigSpec.BooleanValue LEGACY_CHARGE;
    public static final ForgeConfigSpec.BooleanValue GAIA_TARGETING;
    public static final ForgeConfigSpec.BooleanValue HOSTILE_TARGETING;
    public static final ForgeConfigSpec.BooleanValue LEGACY_COMBAT;
    public static final ForgeConfigSpec.BooleanValue SHEATHING_REPAIR;
    public static final ForgeConfigSpec.BooleanValue LEGACY_RANK;
    public static final ForgeConfigSpec.BooleanValue LEGACY_TAUNT;
    public static final ForgeConfigSpec.BooleanValue SUPER_RESPECT_UNBREAKABLE;
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_TARGETS;
    public static final ForgeConfigSpec.BooleanValue SOUL_FEEDING;
    static {
        var builder = new ForgeConfigSpec.Builder();
        BROKEN_DAMAGE = builder.comment("Restore the +2 weapon damage modifier documented in legacy 1.7/1.12.2 for broken blades. Does not repair the blade.").define("restoreBrokenDamage", true);
        BROKEN_REACH = builder.comment("Use the normal blade reach modifier for broken blades only. Does not change block reach or normal blades.").define("restoreBrokenReach", true);
        SUMMONED_BLADE = builder.comment("Enable legacy SB blade mode. Experimental until client and multiplayer acceptance is complete.").define("restoreSummonedBlade", true);
        LEGACY_CHARGE = builder.comment("Restore legacy charge cue at tick 15; release at 16-18 is just, 19+ normal. Does not replace the modern combo graph.").define("restoreLegacyChargeWindow", true);
        GAIA_TARGETING = builder.comment("Allow Botania Gaia Guardian through SlashBlade's Enemy-only filter without requiring a prior hit. Does not alter Gaia damage rules.").define("fixGaiaTargeting", true);
        HOSTILE_TARGETING = builder.comment("Preserve attacker-aware target filtering when copied for area attacks; allow audited additional_hostile_targets tag. Does not automatically attack all MONSTER category entities.").define("fixHostileTargeting", true);
        LEGACY_COMBAT = builder.comment("Use the 1.7.10 r87 combo graph and immediate melee for all blades, retaining registered addon identities. Development candidate; acceptance pending.").define("restoreLegacyCombat", true);
        SHEATHING_REPAIR = builder.comment("Defer default-root kill XP repair until successful legacy sheathing; requires 1000 proud souls. No extra soul award.").define("restoreSheathingRepair", true);
        LEGACY_RANK=builder.comment("Restore legacy melee rank awards and repeat-move diminishing returns; preserve native rank HUD and networking.").define("restoreLegacyRank",true);
        LEGACY_TAUNT=builder.comment("Completed stationary sheathing taunts visible hostile mobs within legacy 10/5/10 expansion; 30s Strength II, Speed II, Resistance I, particles, sound and rank.").define("restoreLegacyTaunt",true);
        SUPER_RESPECT_UNBREAKABLE=builder.comment("怀旧超SA尊重无法破坏：true 不扣无法破坏刀的耐久；false 严格按r87强制设置为半耐久。",
                "Honor the Unbreakable tag for legacy Super SA. Disable for strict r87 half-durability payment.").define("superArtsRespectUnbreakable",true);
        FRIENDLY_TARGETS=builder.comment("怀旧右键范围攻击/剑技是否选中友好生物。仍保护已驯服宠物、盟友、乘骑对象；主动攻击你的生物仍可反击。",
                "Allow friendly targets in legacy area attacks and arts. Independent of Resharped friendlyEnable; PvP remains separate.").define("attackFriendlyEntities",false);
        SOUL_FEEDING=builder.comment("允许在刀挂台上直接砸普通耀魂增加耀魂值。潜行左键优先喂入；普通左键优先保留点火、抽奖、SB和SE操作。",
                "Consume one plain soul per feed, even in creative; tiny/fragment/ingot/sphere/crystal/trapezohedron grant 100/200/400/400/500/500 souls.").define("enableDirectSoulFeeding",true);
        SPEC = builder.build();
    }
    /** 离开世界后仍可能触发物品或搜索回调。此时 SERVER 配置已卸载。 */
    public static boolean isEnabled(ForgeConfigSpec.BooleanValue option) {
        if (!SPEC.isLoaded()) return false;
        try {
            return option.get();
        } catch (IllegalStateException unavailable) {
            // 配置可能在客户端回调期间卸载。配置仍加载时继续抛出错误。
            if (!SPEC.isLoaded()) return false;
            throw unavailable;
        }
    }
    public LegacyCompat() {
        var modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        // SERVER 配置同步到客户端。修改后重启并重新装备物品。
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
        LegacyNetwork.register();
        LegacyMode.register();
        BladeEvents.register();
        if(!BladeEvents.UPDATE_ATTACK)MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LegacyCompat::oldAttackAttributes);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, LegacyCompat::reach);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LegacyCompat::validateMelee);
        SummonedBladeMode.ENTITIES.register(modBus);
        LegacyArts.initialize();
        LegacyNamedBlades.initialize();
        LegacyCombat.COMBOS.register(modBus);
        LegacyArts.ARTS.register(modBus);
        LegacyNamedBlades.ARTS.register(modBus);
        LegacyNamedBlades.EFFECTS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(LegacyCombat::tick);
        MinecraftForge.EVENT_BUS.addListener(LegacyAirControl::tick);
        MinecraftForge.EVENT_BUS.addListener(LegacyLockOn::tick);
        MinecraftForge.EVENT_BUS.addListener(LegacyFireResistance::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {LegacyJustGuard.clear(event.getEntity());LegacyRangeAttack.clear(event.getEntity());});
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> LegacyRank.synchronize(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> LegacyRank.synchronize(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) -> LegacyRank.synchronize(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener(LegacyFreeze::tick);
        MinecraftForge.EVENT_BUS.addListener(LegacyArtEntity::teleport);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, LegacyTaunt::experience);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SummonedBladeMode::interact);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LegacySoulFeeding::sneaking);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SummonedBladeMode::attackStand);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, LegacyBladeSouls::stand);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, LegacyEnchantments::stand);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, LegacySoulFeeding::fallback);
        BladeInput.listen(EventPriority.HIGH, SummonedBladeMode::input);
        MinecraftForge.EVENT_BUS.addListener(SummonedBladeMode::tooltip);
        MinecraftForge.EVENT_BUS.addListener(LegacyDamageTooltip::tooltip);
        MinecraftForge.EVENT_BUS.addListener(LegacyBladeStandInteraction::block);
        MinecraftForge.EVENT_BUS.addListener(LegacyBladeStandInteraction::entity);
        MinecraftForge.EVENT_BUS.addListener(LegacyManaBridge::playerTick);
        MinecraftForge.EVENT_BUS.addListener(LegacyZephyr::use);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,LegacyZephyr::hit);
    }
    private static void validateMelee(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        var player = event.getEntity();
        // 重锋模式使用原生范围判定，允许命中方块后的目标。
        if(!LegacyMode.legacy(player))return;
        var stack = player.getMainHandItem();
        // AttackManager 在结算连段伤害时设置此标记。
        if (!(stack.getItem() instanceof ItemSlashBlade)
                || !BladeStateAccess.of(stack).map(s -> s.onClick()).orElse(false)) return;
        var target = event.getTarget();
        // 当前伤害上下文匹配目标和范围时，使用旧版区域规则。
        if (LegacyDamage.scoped(player, target, stack)) return;
        double reach = mods.flammpfeil.slashblade.util.TargetSelector.getResolvedReach(player);
        if (!Double.isFinite(reach) || reach <= 0 || !player.hasLineOfSight(target)
                || target.distanceToSqr(player) >= reach*reach)
            event.setCanceled(true);
    }
    static double damage(net.minecraft.world.item.ItemStack blade,mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state,double origin,double current) {
        if(LegacyMode.legacy(blade))return state.isBroken() || state.isSealed()?2:state.getBaseAttackModifier()+state.getAttackAmplifier();
        if(LegacyMode.enabled(blade,BROKEN_DAMAGE) && blade.getItem() instanceof ItemSlashBlade && state.isBroken()
                && Double.compare(origin,-1.5)==0 && Double.compare(current,origin)==0)return 2;
        return current;
    }
    private static void oldAttackAttributes(ItemAttributeModifierEvent event) {
        var blade=event.getItemStack();
        if(event.getSlotType()!=EquipmentSlot.MAINHAND || !(blade.getItem() instanceof ItemSlashBlade)
                || blade.hasTag() && blade.getTag().contains("AttributeModifiers",9))return;
        var state=BladeStateAccess.of(blade).orElse(null);if(state==null)return;
        var modifier=event.getModifiers().get(Attributes.ATTACK_DAMAGE).stream().filter(value->value.getId().equals(ATTACK)
                && value.getOperation()==AttributeModifier.Operation.ADDITION).findFirst().orElse(null);
        if(modifier==null)return;
        double amount=damage(blade,state,modifier.getAmount(),modifier.getAmount());
        // The old native formula has no final subtraction of one.
        if(state.isBroken() && LegacyMode.enabled(blade,BROKEN_DAMAGE) && Double.compare(modifier.getAmount(),-.5)==0)amount=2;
        if(Double.compare(amount,modifier.getAmount())!=0){
            event.removeModifier(Attributes.ATTACK_DAMAGE,modifier);
            event.addModifier(Attributes.ATTACK_DAMAGE,new AttributeModifier(ATTACK,modifier.getName(),amount,modifier.getOperation()));
        }
    }
    private static void reach(ItemAttributeModifierEvent event) {
        var stack = event.getItemStack();
        if (event.getSlotType() != EquipmentSlot.MAINHAND
                || !LegacyMode.enabled(stack, BROKEN_REACH) || !(stack.getItem() instanceof ItemSlashBlade)
                || stack.hasTag() && stack.getTag().contains("AttributeModifiers", 9)
                || !BladeStateAccess.of(stack).map(state -> state.isBroken()).orElse(false)) return;
        var attribute = net.minecraftforge.common.ForgeMod.ENTITY_REACH.get();
        var original = event.getModifiers().get(attribute).stream().filter(modifier ->
                modifier.getId().equals(REACH) && modifier.getOperation() == AttributeModifier.Operation.ADDITION
                && Double.compare(modifier.getAmount(), ReachModifier.BrokendReach()) == 0).findFirst();
        original.ifPresent(modifier -> {
            event.removeModifier(attribute, modifier);
            event.addModifier(attribute, new AttributeModifier(REACH, "Reach amplifier",
                    ReachModifier.BladeReach(), AttributeModifier.Operation.ADDITION));
        });
    }
}
