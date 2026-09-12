package org.scex.slashbladelegacy.mixin;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(value=ConcentrationRank.class,remap=false)
public abstract class LegacyRankModeMixin implements LegacyMode.RankMode {
    @Unique private boolean legacy$modern;
    public void legacy$modern(boolean value){legacy$modern=value;}
    public boolean legacy$modern(){return legacy$modern;}
}
