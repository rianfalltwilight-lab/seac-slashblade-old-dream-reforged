package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import org.scex.slashbladelegacy.BladeApi;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

/** Old inventory repair, followed by the framework scheduler and actual registered SA tick. */
public final class LegacyInventory {
    private LegacyInventory() {}
    public static void tick(ItemStack blade,Level level,Entity entity,int slot,boolean selected) {
        if(entity instanceof LivingEntity living && !living.isAlive())return;
        var state=BladeStateAccess.of(blade).orElse(null);if(state==null)return;
        if(MinecraftForge.EVENT_BUS.post(new SlashBladeEvent.UpdateEvent(blade,state,level,entity,slot,selected)))return;
        if(!level.isClientSide && entity instanceof Player player) {
            if(blade.getBaseRepairCost()!=0) {
                state.setProudSoulCount(state.getProudSoulCount()+(EnchantmentHelper.getEnchantments(blade).size()+1)*100);
                state.setRefine(state.getRefine()+1);blade.setRepairCost(0);
            }
            var type=SwordType.from(blade);
            if(!selected && level.getGameTime()%20==0 && blade.getDamageValue()>0
                    && type.contains(SwordType.BEWITCHED) && !type.contains(SwordType.NOSCABBARD)
                    && slot>=0 && slot<9 && player.getInventory().getItem(slot)==blade) {
                boolean material=false,broken=type.contains(SwordType.BROKEN);
                if(broken) {
                    var plain=new ItemStack(BladeApi.PROUDSOUL_TINY.get());
                    for(var item:player.getInventory().items) {
                        if(!item.is(BladeApi.PROUDSOUL_TINY.get()))continue;
                        // Empty old NBT is equivalent to absent NBT, but named/enchanted souls are protected.
                        var comparison=item.copy();
                        if(LegacyTags.copy(comparison).isEmpty())comparison.setTag(null);
                        if(ItemStack.isSameItemSameTags(comparison,plain)){item.shrink(1);material=true;break;}
                    }
                }
                if(material || player.experienceLevel>0) {
                    int repair=broken?Math.max(1,blade.getMaxDamage()/10):1;
                    blade.setDamageValue(Math.max(0,blade.getDamageValue()-repair));player.causeFoodExhaustion(.025f);
                    if(!material) {
                        state.setProudSoulCount(state.getProudSoulCount()+(broken?20:10));
                        if(broken)player.giveExperienceLevels(-1);
                        else for(int i=0;i<10 && player.experienceLevel>0;i++)player.giveExperiencePoints(-1);
                    }
                }
            }
        }
        if(entity instanceof LivingEntity living) {
            LegacyCapabilities.input(living).getScheduler().onTick(living);
            var combo=ComboStateRegistry.REGISTRY.get().getValue(state.resolvCurrentComboState(living));
            if(BladeApi.isInMainhand(blade,selected,living))
                (combo==null?ComboStateRegistry.NONE.get():combo).tickAction(living);
        }
    }
}
