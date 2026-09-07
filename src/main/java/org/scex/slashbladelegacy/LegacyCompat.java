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

/** Compatibility registration and narrowly scoped attack/reach corrections. */
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
    static {
        var builder = new ModConfigSpec.Builder();
        BROKEN_DAMAGE = builder.comment("Restore the +2 weapon damage modifier documented in legacy 1.7/1.12.2 for broken blades. Does not repair the blade.").define("restoreBrokenDamage", true);
        BROKEN_REACH = builder.comment("Use the normal blade reach modifier for broken blades only. Does not change block reach or normal blades.").define("restoreBrokenReach", true);
        SUMMONED_BLADE = builder.comment("Enable legacy SB blade mode. Experimental until client and multiplayer acceptance is complete.").define("restoreSummonedBlade", true);
        LEGACY_CHARGE = builder.comment("Restore legacy charge cue at tick 15; release at 16-18 is just, 19+ normal. Does not replace the modern combo graph.").define("restoreLegacyChargeWindow", true);
        GAIA_TARGETING = builder.comment("Allow Botania Gaia Guardian through SlashBlade's Enemy-only filter without requiring a prior hit. Does not alter Gaia damage rules.").define("fixGaiaTargeting", true);
        HOSTILE_TARGETING = builder.comment("Preserve attacker-aware target filtering when copied for area attacks; allow audited additional_hostile_targets tag. Does not automatically attack all MONSTER category entities.").define("fixHostileTargeting", true);
        LEGACY_COMBAT = builder.comment("Use the legacy 1.12.2 combo graph and immediate melee for the default combo root. Development candidate; visual and advanced attack parity pending.").define("restoreLegacyCombat", true);
        SHEATHING_REPAIR = builder.comment("Defer default-root kill XP repair until successful legacy sheathing; requires 1000 proud souls. No extra soul award.").define("restoreSheathingRepair", true);
        LEGACY_RANK=builder.comment("Restore legacy melee rank awards and repeat-move diminishing returns; preserve native rank HUD and networking.").define("restoreLegacyRank",true);
        LEGACY_TAUNT=builder.comment("Completed stationary sheathing taunts visible hostile mobs within legacy 10/5/10 expansion; 30s Strength II, Speed II, Resistance I, particles, sound and rank.").define("restoreLegacyTaunt",true);
        SPEC = builder.build();
    }
    /** Item/search callbacks also run between worlds, after SERVER config has been unloaded. */
    public static boolean isEnabled(ModConfigSpec.BooleanValue option) {
        if (!SPEC.isLoaded()) return false;
        try {
            return option.get();
        } catch (IllegalStateException unavailable) {
            // Unload may race a client callback. Never hide an error while the config is loaded.
            if (!SPEC.isLoaded()) return false;
            throw unavailable;
        }
    }
    public LegacyCompat(net.neoforged.bus.api.IEventBus modBus, ModContainer container) {
        // SERVER configs synchronize to clients. Restart/re-equip after changing this development config.
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LegacyCompat::damage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LegacyCompat::reach);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LegacyCompat::validateMelee);
        SummonedBladeMode.ENTITIES.register(modBus);
        LegacyCombat.COMBOS.register(modBus);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LegacyCombat::nextCombo);
        NeoForge.EVENT_BUS.addListener(LegacyCombat::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LegacySheathingRepair::timeout);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LegacyTaunt::experience);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SummonedBladeMode::interact);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SummonedBladeMode::attackStand);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SummonedBladeMode::input);
        NeoForge.EVENT_BUS.addListener(SummonedBladeMode::tooltip);
    }
    private static void validateMelee(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        var player = event.getEntity();
        var stack = player.getMainHandItem();
        // This flag is set by AttackManager around actual combo damage, not input collection.
        if (!(stack.getItem() instanceof ItemSlashBlade)
                || !BladeStateAccess.of(stack).map(s -> s.onClick()).orElse(false)) return;
        var target = event.getTarget();
        double reach = mods.flammpfeil.slashblade.util.TargetSelector.getResolvedReach(player);
        if (!Double.isFinite(reach) || reach <= 0 || !player.hasLineOfSight(target)
                || mods.flammpfeil.slashblade.util.TargetSelector.distanceSqrBetweenEntity(target,player) >= reach*reach)
            event.setCanceled(true);
    }
    private static void damage(SlashBladeEvent.UpdateAttackEvent event) {
        if (!isEnabled(BROKEN_DAMAGE) || !(event.getBlade().getItem() instanceof ItemSlashBlade)
                || !event.getSlashBladeState().isBroken()) return;
        // Exact current formula gate; leave overrides from other mods untouched.
        // Old updateAttackAmplifier: (2 - base) + base = +2, i.e. player total 3.
        if (Double.compare(event.getOriginDamage(), -1.5) == 0
                && Double.compare(event.getNewDamage(), event.getOriginDamage()) == 0) {
            event.setNewDamage(2.0);
        }
    }
    private static void reach(ItemAttributeModifierEvent event) {
        var stack = event.getItemStack();
        if (!isEnabled(BROKEN_REACH) || !(stack.getItem() instanceof ItemSlashBlade)
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
