package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.item.ItemProudSoul;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/** r87 material factors and one refine per operation; named addon blade state stays on the copy. */
public final class LegacyAnvil {
    private LegacyAnvil() {}
    public static void update(AnvilUpdateEvent event) {
        if(event.isCanceled() || !event.getOutput().isEmpty())return;
        var left=event.getLeft();var material=event.getRight();
        if(!(left.getItem() instanceof ItemSlashBlade) || !(material.getItem() instanceof ItemProudSoul))return;
        var output=left.copy();var state=BladeStateAccess.of(output).orElse(null);if(state==null)return;
        int minimum,souls;float factor;
        if(material.is(SlashBladeItems.PROUDSOUL.get())){minimum=2;souls=200;factor=.4f;}
        else if(material.is(SlashBladeItems.PROUDSOUL_INGOT.get())){minimum=3;souls=400;factor=.6f;}
        else if(material.is(SlashBladeItems.PROUDSOUL_SPHERE.get())){minimum=4;souls=400;factor=.7f;}
        else if(material.is(SlashBladeItems.PROUDSOUL_TINY.get())){minimum=1;souls=100;factor=.2f;}
        else{
            minimum=5;souls=500;factor=1;
            if(LegacyBladeSouls.named(material)){
                if(!LegacyBladeSouls.apply(output,material,event.getPlayer().registryAccess())){event.setCanceled(true);return;}
                souls=-1000;
            }
        }
        long cost=event.getCost();
        for(var entry:EnchantmentHelper.getEnchantmentsForCrafting(left).entrySet()) {
            int weight=entry.getKey().value().getWeight();
            cost+=(long)(switch(weight){case 1->8;case 2->4;case 5->2;case 10->1;default->0;})*entry.getIntValue();
        }
        int levelCost=(int)Math.min(Integer.MAX_VALUE,Math.max(minimum,cost));
        var progress=new RefineProgressEvent(output,state,1,levelCost,0,state.getRefine()+1,event);
        if(NeoForge.EVENT_BUS.post(progress).isCanceled()){event.setCanceled(true);return;}
        var settlement=new RefineSettlementEvent(output,state,progress.getMaterialCost(),progress.getLevelCost(),progress.getRefineResult(),event);
        if(NeoForge.EVENT_BUS.post(settlement).isCanceled()){event.setCanceled(true);return;}
        if(settlement.getMaterialCost()<=0 || settlement.getMaterialCost()>material.getCount() || settlement.getCostResult()<0)return;
        state.setRefine(settlement.getRefineResult());state.setProudSoulCount(state.getProudSoulCount()+souls);
        output.setDamageValue(output.getDamageValue()-Math.min(output.getDamageValue(),(int)(output.getMaxDamage()*factor)));
        if(event.getName()!=null) {
            if(event.getName().isBlank())output.remove(DataComponents.CUSTOM_NAME);
            else output.set(DataComponents.CUSTOM_NAME,Component.literal(event.getName()));
        }
        ItemSlashBlade.updateRarity(output);
        event.setMaterialCost(settlement.getMaterialCost());event.setCost(settlement.getCostResult());event.setOutput(output);
    }
}
