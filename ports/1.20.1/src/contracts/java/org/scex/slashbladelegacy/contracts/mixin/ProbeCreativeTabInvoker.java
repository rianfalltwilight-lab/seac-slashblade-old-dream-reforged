package org.scex.slashbladelegacy.contracts.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface ProbeCreativeTabInvoker {
    @Invoker("selectTab") void legacyProbe$selectTab(CreativeModeTab tab);
}
