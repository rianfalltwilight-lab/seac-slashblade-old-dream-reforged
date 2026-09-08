package org.scex.slashbladelegacy.contracts.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Test-only entry through mouse pre/post events and key mapping, without bypassing cancellation. */
@Mixin(MouseHandler.class)
public interface ProbeMouseInvoker {
    @Invoker("onPress") void legacyProbe$press(long window,int button,int action,int modifiers);
}
