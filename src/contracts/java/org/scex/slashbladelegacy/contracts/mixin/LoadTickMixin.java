package org.scex.slashbladelegacy.contracts.mixin;

import org.scex.slashbladelegacy.contracts.Legacy17LoadProbe;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test mod only. Measures the complete tickServer method, including its save/network work. */
@Mixin(MinecraftServer.class)
public abstract class LoadTickMixin {
    @Inject(method="tickServer",at=@At("HEAD"))
    private void legacy17$begin(CallbackInfo ci){Legacy17LoadProbe.begin();}
    @Inject(method="tickServer",at=@At("RETURN"))
    private void legacy17$end(CallbackInfo ci){Legacy17LoadProbe.end();}
}
