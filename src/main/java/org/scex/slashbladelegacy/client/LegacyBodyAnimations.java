package org.scex.slashbladelegacy.client;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.scex.slashbladelegacy.*;

/** Optional client bridge; reuses dependency motion assets, with no redistributed or invented frames. */
@EventBusSubscriber(modid=LegacyCompat.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LegacyBodyAnimations {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if(ModList.get().isLoaded("playeranimator"))event.enqueueWork(LegacyBodyAnimations::register);
    }
    @SuppressWarnings("unchecked") private static void register() {
        try {
            // Keep player-animation-lib optional and out of the dedicated-server class linkage.
            var overrider=Class.forName("mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider");
            var instance=overrider.getMethod("getInstance").invoke(null);
            var map=(Map<ResourceLocation,Object>)overrider.getMethod("getAnimation").invoke(instance);
            var type=Class.forName("mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation");
            var constructor=type.getConstructor(ResourceLocation.class,double.class,double.class,boolean.class);
            int count=0;
            for(var move:LegacyMove.values())if(move!=LegacyMove.NONE) {
                int start=LegacyCombat.animationStart(move);
                var animation=constructor.newInstance(ResourceLocation.fromNamespaceAndPath("slashblade","model/pa/player_motion.vmd"),
                        (double)start,(double)start+9,false);
                if(move.aerial())type.getMethod("setBlendLegs",boolean.class).invoke(animation,false);
                map.put(LegacyCombat.id(move),animation);count++;
            }
            com.mojang.logging.LogUtils.getLogger().info("SCEX legacy body animations registered: {}",count);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Resharpened player animation bridge failed",failure);}
    }
}
