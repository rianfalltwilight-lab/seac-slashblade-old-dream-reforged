package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import org.scex.slashbladelegacy.LegacyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** r87's 100-point bands transported using the existing 300-unit rank component and packets. */
@Mixin(value=IConcentrationRank.class,remap=false)
public interface LegacyRankScaleMixin {
    private boolean legacyCompat$enabled(){return org.scex.slashbladelegacy.LegacyMode.legacyRank((IConcentrationRank)(Object)this);}
    @Inject(method="getRank(J)Lmods/flammpfeil/slashblade/capability/concentrationrank/IConcentrationRank$ConcentrationRanks;",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$threshold(long time,CallbackInfoReturnable<IConcentrationRank.ConcentrationRanks> cir) {
        if(!legacyCompat$enabled())return;
        var rank=(IConcentrationRank)(Object)this;double points=rank.getRankPoint(time)*100.0/rank.getUnitCapacity();
        int band=(int)(points/100);if(points>550)band++;if(points>575)band++;
        cir.setReturnValue(IConcentrationRank.ConcentrationRanks.values()[Math.clamp(band,0,7)]);
    }
    @Inject(method="getRankPoint(J)J",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$decay(long time,CallbackInfoReturnable<Long> cir) {
        if(!legacyCompat$enabled())return;
        var rank=(IConcentrationRank)(Object)this;long point=Math.max(0,rank.getRawRankPoint());long elapsed=Math.max(0,time-rank.getLastUpdate());
        long fraction=point%rank.getUnitCapacity();long reduced=(long)Math.min(fraction,elapsed*(rank.getUnitCapacity()/100.0));
        cir.setReturnValue(point-reduced);
    }
    @Inject(method="getRankProgress(J)F",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$progress(long time,CallbackInfoReturnable<Float> cir) {
        if(!legacyCompat$enabled())return;
        var rank=(IConcentrationRank)(Object)this;cir.setReturnValue((rank.getRankPoint(time)%rank.getUnitCapacity())/(float)rank.getUnitCapacity());
    }
    @Inject(method="getMaxCapacity()J",at=@At("HEAD"),cancellable=true)
    private void legacyCompat$cap(CallbackInfoReturnable<Long> cir) {
        if(legacyCompat$enabled())cir.setReturnValue(599*((IConcentrationRank)(Object)this).getUnitCapacity()/100);
    }
}
