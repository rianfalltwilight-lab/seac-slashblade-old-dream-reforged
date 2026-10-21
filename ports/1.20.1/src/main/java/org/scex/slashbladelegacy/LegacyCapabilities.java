package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.entity.Entity;

/** Access capabilities that SlashBlade attaches to living entities. */
public final class LegacyCapabilities {
    private LegacyCapabilities() {}
    public static IInputState input(Entity entity) {
        return entity.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
    }
    public static IConcentrationRank rank(Entity entity) {
        return entity.getCapability(CapabilityConcentrationRank.RANK_POINT).orElseThrow(IllegalStateException::new);
    }
}
