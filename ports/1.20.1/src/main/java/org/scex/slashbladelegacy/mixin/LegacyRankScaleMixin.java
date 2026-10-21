package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import org.scex.slashbladelegacy.LegacyMode;
import org.spongepowered.asm.mixin.Mixin;

/** 旧版每档 100 分映射到原能力的 300 点，沿用原同步包。 */
@Mixin(value=ConcentrationRank.class,remap=false)
public abstract class LegacyRankScaleMixin implements IConcentrationRank {
    @Override
    public ConcentrationRanks getRank(long time) {
        if(!LegacyMode.legacyRank(this))return IConcentrationRank.super.getRank(time);
        double points=getRankPoint(time)*100.0/getUnitCapacity();
        int band=(int)(points/100);if(points>550)band++;if(points>575)band++;
        return ConcentrationRanks.values()[net.minecraft.util.Mth.clamp(band,0,7)];
    }

    @Override
    public long getRankPoint(long time) {
        if(!LegacyMode.legacyRank(this))return IConcentrationRank.super.getRankPoint(time);
        long point=Math.max(0,getRawRankPoint());long elapsed=Math.max(0,time-getLastUpdate());
        long fraction=point%getUnitCapacity();long reduced=(long)Math.min(fraction,elapsed*(getUnitCapacity()/100.0));
        return point-reduced;
    }

    @Override
    public float getRankProgress(long time) {
        if(!LegacyMode.legacyRank(this))return IConcentrationRank.super.getRankProgress(time);
        return (getRankPoint(time)%getUnitCapacity())/(float)getUnitCapacity();
    }

    @Override
    public long getMaxCapacity() {
        return LegacyMode.legacyRank(this)?599*getUnitCapacity()/100:IConcentrationRank.super.getMaxCapacity();
    }
}
