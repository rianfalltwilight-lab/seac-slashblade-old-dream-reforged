package org.scex.slashbladelegacy.mixin;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import org.scex.slashbladelegacy.LegacyNamedBlades;
import org.scex.slashbladelegacy.LegacyNamedBladeStackAccessor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=SlashBladeState.class,remap=false)
public abstract class LegacyNamedBladeArtMixin implements ISlashBladeState {
    @Override
    public SlashArts getSlashArts() {
        if(this instanceof LegacyNamedBladeStackAccessor access) {
            var corrected=LegacyNamedBlades.art(this,access.legacyCompat$blade());
            if(corrected!=null)return corrected;
        }
        return ISlashBladeState.super.getSlashArts();
    }
}
