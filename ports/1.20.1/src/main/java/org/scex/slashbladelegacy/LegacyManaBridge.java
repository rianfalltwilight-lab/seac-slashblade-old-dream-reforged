package org.scex.slashbladelegacy;

import java.lang.reflect.Method;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.TickEvent;

/** Botania 存在时，调用其原生魔力、脉冲和池 API。 */
public final class LegacyManaBridge {
    private static final ResourceLocation TERRA_BLADE=new ResourceLocation("botania:terra_sword");
    private record Api(Object handler,Method consume,Method burst,Class<?> pool,Method mana,Method receive) {}
    private static Api api;
    private LegacyManaBridge(){}
    private static Api api() {
        if(api!=null)return api;
        if(!ModList.get().isLoaded("botania"))return null;
        try {
            var handler=Class.forName("vazkii.botania.api.mana.ManaItemHandler");
            var receiver=Class.forName("vazkii.botania.api.mana.ManaReceiver");
            api=new Api(handler.getMethod("instance").invoke(null),handler.getMethod("requestManaExactForTool",ItemStack.class,Player.class,int.class,boolean.class),
                    Class.forName("vazkii.botania.common.item.equipment.tool.terrasteel.TerraBladeItem").getMethod("getBurst",Player.class,ItemStack.class),
                    Class.forName("vazkii.botania.api.mana.ManaPool"),receiver.getMethod("getCurrentMana"),receiver.getMethod("receiveMana",int.class));
            return api;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Botania Terra Blade API is incompatible",failure);}
    }
    private static ItemStack terraSword(){return new ItemStack(BuiltInRegistries.ITEM.get(TERRA_BLADE));}
    public static boolean consume(Player player,int amount) {
        var bridge=api();if(bridge==null)return false;
        var sword=terraSword();if(sword.isEmpty())return false;
        try{return (boolean)bridge.consume.invoke(bridge.handler,sword,player,amount,true);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Botania mana request failed",failure);}
    }
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var player=event.player;if(player.level().isClientSide || !LegacyMode.legacy(player))return;
        var blade=player.getMainHandItem();
        var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null)return;
        LegacyNamedBlades.seed(blade,state);
        if(player.swingTime!=4 || player.experienceLevel<10 || !state.hasSpecialEffect(LegacyNamedBlades.MANA_BURST))return;
        var move=LegacyCombat.visualMove(state.getComboSeq());
        if(move==LegacyMove.NONE || move==LegacyMove.NOUTOU || move.scabbard || !consume(player,100))return;
        try{player.level().addFreshEntity((Entity)api().burst.invoke(null,player,terraSword()));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Botania Terra Blade burst failed",failure);}
    }
    public static void entityTick(Entity entity) {
        if(entity.level().isClientSide)return;
        ItemStack blade;
        if(entity instanceof BladeStandEntity stand)blade=stand.getItem();else if(entity instanceof ItemEntity item)blade=item.getItem();else return;
        // Most ticking items are not blades. Test the item type before the mode data.
        if(!(blade.getItem() instanceof mods.flammpfeil.slashblade.item.ItemSlashBlade) || !LegacyMode.legacy(blade))return;
        var state=BladeStateAccess.of(blade).orElse(null);
        if(state==null)return;
        LegacyNamedBlades.seed(blade,state);
        if(!blade.isDamaged() || !state.hasSpecialEffect(LegacyNamedBlades.MANA_REPAIR))return;
        var bridge=api();if(bridge==null)return;
        var pos=entity instanceof BladeStandEntity?entity.blockPosition().below():entity.blockPosition();
        var pool=entity.level().getBlockEntity(pos);
        if(pool==null || !bridge.pool.isInstance(pool))return;
        try {
            if((int)bridge.mana.invoke(pool)<100)return;
            bridge.receive.invoke(pool,-100);blade.setDamageValue(blade.getDamageValue()-1);
            if(blade.getDamageValue()==0)state.setBroken(false);
            if(entity instanceof BladeStandEntity stand)stand.setItem(blade);else ((ItemEntity)entity).setItem(blade);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Botania mana pool repair failed",failure);}
    }
}
