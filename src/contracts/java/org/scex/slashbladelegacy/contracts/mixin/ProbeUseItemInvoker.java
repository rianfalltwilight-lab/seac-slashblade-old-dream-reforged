package org.scex.slashbladelegacy.contracts.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface ProbeUseItemInvoker {
    @Invoker("startUseItem") void legacyProbe$useItem();
}
