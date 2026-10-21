package org.scex.slashbladelegacy.mixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.scex.slashbladelegacy.LegacyMode;
import org.scex.slashbladelegacy.client.LegacyKeyMappings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Add the lock-on key to SlashBlade commands without changing physical movement input. */
@Pseudo
@Mixin(targets={"mods.flammpfeil.slashblade.event.handler.MoveInputHandler",
        "mods.flammpfeil.slashblade.event.MoveInputHandler"},remap=false)
public abstract class LegacyMoveInputMixin {
    @ModifyExpressionValue(method="onPlayerPostTick",at=@At(value="FIELD",
            target="Lnet/minecraft/client/player/Input;shiftKeyDown:Z",remap=true))
    private static boolean legacyCompat$lockOn(boolean physicalSneak) {
        var client=Minecraft.getInstance();
        return physicalSneak || client.screen==null && LegacyMode.legacy(client.player) && LegacyKeyMappings.LOCK_ON.isDown();
    }
}
