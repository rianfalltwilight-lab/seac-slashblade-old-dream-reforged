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

/** r87 material benefits with Resharpened's batch input; each paid unit adds one refine. */
public final class LegacyAnvil {
    private LegacyAnvil() {}
    public static void update(AnvilUpdateEvent event) {
        if(event.isCanceled() || !event.getOutput().isEmpty())return;
        var left=event.getLeft();var material=event.getRight();
        if(!(left.getItem() instanceof ItemSlashBlade) || !(material.getItem() instanceof ItemProudSoul))return;
        var output=left.copy();var state=BladeStateAccess.of(output).orElse(null);if(state==null)return;
        int minimum,souls;float factor;boolean named=false;
        if(material.is(SlashBladeItems.PROUDSOUL.get())){minimum=2;souls=200;factor=.4f;}
        else if(material.is(SlashBladeItems.PROUDSOUL_INGOT.get())){minimum=3;souls=400;factor=.6f;}
        else if(material.is(SlashBladeItems.PROUDSOUL_SPHERE.get())){minimum=4;souls=400;factor=.7f;}
        else if(material.is(SlashBladeItems.PROUDSOUL_TINY.get())){minimum=1;souls=100;factor=.2f;}
        else{
            minimum=5;souls=500;factor=1;
            named=LegacyBladeSouls.named(material);
            if(named){
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
        // Named blade souls transform a blank blade once; ordinary souls may consume the full stack.
        int limit=named?1:Math.min(material.getCount(),material.getMaxStackSize());
        int materialCost=0,totalCost=0,refineResult=state.getRefine();
        for(int attempts=0;attempts<limit && materialCost<limit && refineResult<Integer.MAX_VALUE;attempts++) {
            // A rejected/unaffordable step must not keep mutations made to its output preview.
            var candidate=output.copy();var candidateState=BladeStateAccess.of(candidate).orElseThrow();
            var progress=new RefineProgressEvent(candidate,candidateState,materialCost+1,levelCost,totalCost,refineResult+1,event);
            if(NeoForge.EVENT_BUS.post(progress).isCanceled())break;
            long nextCost=(long)progress.getCostResult()+progress.getLevelCost();
            if(progress.getMaterialCost()<=materialCost || progress.getMaterialCost()>limit
                    || progress.getRefineResult()<refineResult || progress.getLevelCost()<0
                    || nextCost<0 || nextCost>Integer.MAX_VALUE
                    || !event.getPlayer().getAbilities().instabuild && nextCost>event.getPlayer().experienceLevel)break;
            output=candidate;state=candidateState;
            materialCost=progress.getMaterialCost();totalCost=(int)nextCost;refineResult=progress.getRefineResult();
        }
        if(materialCost==0){event.setCanceled(true);return;}
        var settlement=new RefineSettlementEvent(output,state,materialCost,totalCost,refineResult,event);
        if(NeoForge.EVENT_BUS.post(settlement).isCanceled()){event.setCanceled(true);return;}
        if(settlement.getMaterialCost()<=0 || settlement.getMaterialCost()>limit || settlement.getCostResult()<0
                || settlement.getRefineResult()<0){event.setCanceled(true);return;}
        int consumed=settlement.getMaterialCost();
        state.setRefine(settlement.getRefineResult());
        state.setProudSoulCount((int)Math.clamp((long)state.getProudSoulCount()+(long)souls*consumed,0,Integer.MAX_VALUE));
        long repair=(long)(int)(output.getMaxDamage()*factor)*consumed;
        output.setDamageValue(output.getDamageValue()-(int)Math.min(output.getDamageValue(),repair));
        if(event.getName()!=null) {
            if(event.getName().isBlank())output.remove(DataComponents.CUSTOM_NAME);
            else output.set(DataComponents.CUSTOM_NAME,Component.literal(event.getName()));
        }
        ItemSlashBlade.updateRarity(output);
        event.setMaterialCost(settlement.getMaterialCost());event.setCost(settlement.getCostResult());event.setOutput(output);
    }
}
