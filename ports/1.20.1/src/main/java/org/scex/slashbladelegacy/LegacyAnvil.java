package org.scex.slashbladelegacy;

import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import org.scex.slashbladelegacy.BladeApi;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AnvilUpdateEvent;

/** r87 material benefits with Resharpened's batch input; each paid unit adds one refine. */
public final class LegacyAnvil {
    private LegacyAnvil() {}
    public static void update(AnvilUpdateEvent event) {
        if(event.isCanceled() || !event.getOutput().isEmpty())return;
        var left=event.getLeft();var material=event.getRight();
        if(!(left.getItem() instanceof ItemSlashBlade) || !(BladeApi.isProudSoul(material)))return;
        var output=left.copy();var state=BladeStateAccess.of(output).orElse(null);if(state==null)return;
        int minimum,souls;float factor;boolean named=false;
        if(material.is(BladeApi.PROUDSOUL.get())){minimum=2;souls=200;factor=.4f;}
        else if(material.is(BladeApi.PROUDSOUL_INGOT.get())){minimum=3;souls=400;factor=.6f;}
        else if(material.is(BladeApi.PROUDSOUL_SPHERE.get())){minimum=4;souls=400;factor=.7f;}
        else if(material.is(BladeApi.PROUDSOUL_TINY.get())){minimum=1;souls=100;factor=.2f;}
        else{
            minimum=5;souls=500;factor=1;
            named=LegacyBladeSouls.named(material);
            if(named){
                if(!LegacyBladeSouls.apply(output,material,event.getPlayer().level().registryAccess())){event.setCanceled(true);return;}
                souls=-1000;
            }
        }
        long cost=event.getCost();
        for(var entry:EnchantmentHelper.getEnchantments(left).entrySet()) {
            int weight=entry.getKey().getRarity().getWeight();
            cost+=(long)(switch(weight){case 1->8;case 2->4;case 5->2;case 10->1;default->0;})*entry.getValue();
        }
        int levelCost=(int)Math.min(Integer.MAX_VALUE,Math.max(minimum,cost));
        // Named blade souls transform a blank blade once; ordinary souls may consume the full stack.
        int limit=named?1:Math.min(material.getCount(),material.getMaxStackSize());
        int materialCost=0,totalCost=0,refineResult=state.getRefine();
        for(int attempts=0;attempts<limit && materialCost<limit && refineResult<Integer.MAX_VALUE;attempts++) {
            // A rejected/unaffordable step must not keep mutations made to its output preview.
            var candidate=output.copy();var candidateState=BladeStateAccess.of(candidate).orElseThrow();
            var progress=BladeEvents.refine(candidate,candidateState,materialCost+1,levelCost,totalCost,refineResult+1,event);
            if(progress.canceled())break;
            long nextCost=(long)progress.costResult()+progress.levelCost();
            if(progress.materialCost()<=materialCost || progress.materialCost()>limit
                    || progress.refineResult()<refineResult || progress.levelCost()<0
                    || nextCost<0 || nextCost>Integer.MAX_VALUE
                    || !event.getPlayer().getAbilities().instabuild && nextCost>event.getPlayer().experienceLevel)break;
            output=candidate;state=candidateState;
            materialCost=progress.materialCost();totalCost=(int)nextCost;refineResult=progress.refineResult();
        }
        if(materialCost==0){event.setCanceled(true);return;}
        var settlement=BladeEvents.settle(output,state,materialCost,totalCost,refineResult,event);
        if(settlement.canceled()){event.setCanceled(true);return;}
        if(settlement.materialCost()<=0 || settlement.materialCost()>limit || settlement.costResult()<0
                || settlement.refineResult()<0){event.setCanceled(true);return;}
        int consumed=settlement.materialCost();
        state.setRefine(settlement.refineResult());
        state.setProudSoulCount((int)Math.max(0L,Math.min(Integer.MAX_VALUE,(long)state.getProudSoulCount()+(long)souls*consumed)));
        long repair=(long)(int)(output.getMaxDamage()*factor)*consumed;
        output.setDamageValue(output.getDamageValue()-(int)Math.min(output.getDamageValue(),repair));
        if(event.getName()!=null) {
            if(event.getName().isBlank())output.resetHoverName();
            else output.setHoverName(Component.literal(event.getName()));
        }
        event.setMaterialCost(settlement.materialCost());event.setCost(settlement.costResult());event.setOutput(output);
    }
}
