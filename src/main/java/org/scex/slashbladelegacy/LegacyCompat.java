package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.ReachModifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/** 注册兼容功能，修正攻击与触及距离。 */
@Mod(LegacyCompat.MOD_ID)
public final class LegacyCompat {
    public static final String MOD_ID = "slashblade_legacy_compat";
    private static final ResourceLocation REACH = ResourceLocation.fromNamespaceAndPath("slashblade", "mainhand_reach");
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BROKEN_DAMAGE;
    public static final ModConfigSpec.BooleanValue BROKEN_REACH;
    public static final ModConfigSpec.BooleanValue SUMMONED_BLADE;
    public static final ModConfigSpec.BooleanValue LEGACY_CHARGE;
    public static final ModConfigSpec.BooleanValue GAIA_TARGETING;
    public static final ModConfigSpec.BooleanValue HOSTILE_TARGETING;
    public static final ModConfigSpec.BooleanValue LEGACY_COMBAT;
    public static final ModConfigSpec.BooleanValue SHEATHING_REPAIR;
    public static final ModConfigSpec.BooleanValue LEGACY_RANK;
    public static final ModConfigSpec.BooleanValue LEGACY_TAUNT;
    public static final ModConfigSpec.BooleanValue SUPER_RESPECT_UNBREAKABLE;
    public static final ModConfigSpec.BooleanValue FRIENDLY_TARGETS;
    public static final ModConfigSpec.BooleanValue SOUL_FEEDING;
    static {
        var builder = new ModConfigSpec.Builder();
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
                "Honor the Unbreakable component for legacy Super SA. Disable for strict r87 half-durability payment.").define("superArtsRespectUnbreakable",true);
        FRIENDLY_TARGETS=builder.comment("怀旧右键范围攻击/剑技是否选中友好生物。仍保护已驯服宠物、盟友、乘骑对象；主动攻击你的生物仍可反击。",
                "Allow friendly targets in legacy area attacks and arts. Independent of Resharped friendlyEnable; PvP remains separate.").define("attackFriendlyEntities",false);
        SOUL_FEEDING=builder.comment("允许在刀挂台上直接砸普通耀魂增加耀魂值。潜行左键优先喂入；普通左键优先保留点火、抽奖、SB和SE操作。",
                "Consume one plain soul per feed, even in creative; tiny/fragment/ingot/sphere/crystal/trapezohedron grant 100/200/400/400/500/500 souls.").define("enableDirectSoulFeeding",true);
        SPEC = builder.build();
    }
    /** 离开世界后仍可能触发物品或搜索回调。此时 SERVER 配置已卸载。 */
    public static boolean isEnabled(ModConfigSpec.BooleanValue option) {
        if (!SPEC.isLoaded()) return false;
        try {
            return option.get();
        } catch (IllegalStateException unavailable) {
            // 配置可能在客户端回调期间卸载。配置仍加载时继续抛出错误。
            if (!SPEC.isLoaded()) return false;
            throw unavailable;
        }
    }
    public LegacyCompat(net.neoforged.bus.api.IEventBus modBus, ModContainer container) {
        // SERVER 配置同步到客户端。修改后重启并重新装备物品。
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
        modBus.addListener(LegacyAvoidPayload::register);
        modBus.addListener(LegacyModePayload::register);
        LegacyMode.register();
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LegacyCompat::damage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LegacyCompat::reach);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LegacyCompat::validateMelee);
        SummonedBladeMode.ENTITIES.register(modBus);
        LegacyArts.initialize();
        LegacyNamedBlades.initialize();
        LegacyCombat.COMBOS.register(modBus);
        LegacyArts.ARTS.register(modBus);
        LegacyNamedBlades.ARTS.register(modBus);
        LegacyNamedBlades.EFFECTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LegacyCombat::nextCombo);
        NeoForge.EVENT_BUS.addListener(LegacyCombat::tick);
        NeoForge.EVENT_BUS.addListener(LegacyAirControl::tick);
        NeoForge.EVENT_BUS.addListener(LegacyLockOn::tick);
        NeoForge.EVENT_BUS.addListener(LegacyFireResistance::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {LegacyJustGuard.clear(event.getEntity());LegacyRangeAttack.clear(event.getEntity());});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> LegacyRank.synchronize(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> LegacyRank.synchronize(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) -> LegacyRank.synchronize(event.getEntity()));
        NeoForge.EVENT_BUS.addListener(LegacyFreeze::tick);
        NeoForge.EVENT_BUS.addListener(LegacyArtEntity::teleport);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LegacySheathingRepair::timeout);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LegacyTaunt::experience);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SummonedBladeMode::interact);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LegacySoulFeeding::sneaking);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SummonedBladeMode::attackStand);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LegacyBladeSouls::stand);
        NeoForge.EVENT_BUS.addListener(LegacyBladeSouls::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LegacyEnchantments::stand);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LegacySoulFeeding::fallback);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SummonedBladeMode::input);
        NeoForge.EVENT_BUS.addListener(SummonedBladeMode::tooltip);
        NeoForge.EVENT_BUS.addListener(LegacyDamageTooltip::tooltip);
        NeoForge.EVENT_BUS.addListener(LegacyBladeStandInteraction::block);
        NeoForge.EVENT_BUS.addListener(LegacyBladeStandInteraction::entity);
        NeoForge.EVENT_BUS.addListener(LegacyManaBridge::playerTick);
        NeoForge.EVENT_BUS.addListener(LegacyManaBridge::entityTick);
        NeoForge.EVENT_BUS.addListener(LegacyZephyr::use);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,LegacyZephyr::hit);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,LegacyNamedBlades::copyEffectPolicy);
    }
    private static void validateMelee(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
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
                || mods.flammpfeil.slashblade.util.TargetSelector.distanceSqrBetweenEntity(target,player) >= reach*reach)
            event.setCanceled(true);
    }
    private static void damage(SlashBladeEvent.UpdateAttackEvent event) {
        if (org.scex.slashbladelegacy.LegacyMode.legacy(event.getBlade())) {
            var state = event.getSlashBladeState();
            event.setNewDamage(state.isBroken() || state.isSealed() ? 2
                    : state.getBaseAttackModifier() + state.getAttackAmplifier());
            return;
        }
        if (!org.scex.slashbladelegacy.LegacyMode.enabled(event.getBlade(),BROKEN_DAMAGE) || !(event.getBlade().getItem() instanceof ItemSlashBlade)
                || !event.getSlashBladeState().isBroken()) return;
        // 仅替换重锋的默认公式结果，保留其他模组的覆盖值。
        // 旧 updateAttackAmplifier：(2 - base) + base = +2，玩家总伤害为 3。
        if (Double.compare(event.getOriginDamage(), -1.5) == 0
                && Double.compare(event.getNewDamage(), event.getOriginDamage()) == 0) {
            event.setNewDamage(2.0);
        }
    }
    private static void reach(ItemAttributeModifierEvent event) {
        var stack = event.getItemStack();
        if (!org.scex.slashbladelegacy.LegacyMode.enabled(stack,BROKEN_REACH) || !(stack.getItem() instanceof ItemSlashBlade)
                || !stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                    net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().isEmpty()
                || !BladeStateAccess.of(stack).map(s -> s.isBroken()).orElse(false)) return;
        var entry = event.getModifiers().stream().filter(e ->
                e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)
                && e.modifier().id().equals(REACH)
                && e.slot() == EquipmentSlotGroup.MAINHAND
                && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
                && Double.compare(e.modifier().amount(), ReachModifier.BrokendReach()) == 0).findFirst();
        if (entry.isPresent()) event.replaceModifier(Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(REACH, ReachModifier.BladeReach(), AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }
}
