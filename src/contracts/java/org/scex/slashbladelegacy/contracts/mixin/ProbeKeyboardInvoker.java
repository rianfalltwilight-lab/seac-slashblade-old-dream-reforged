package org.scex.slashbladelegacy.contracts.mixin;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(KeyboardHandler.class)
public interface ProbeKeyboardInvoker {
    @Invoker("keyPress") void legacyProbe$key(long window,int key,int scan,int action,int modifiers);
}
