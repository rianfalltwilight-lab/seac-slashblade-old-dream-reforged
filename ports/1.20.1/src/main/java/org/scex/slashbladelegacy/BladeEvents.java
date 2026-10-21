package org.scex.slashbladelegacy;

import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Keep newer extension events separate from the older blade API. */
public final class BladeEvents {
    private static final String EVENT="mods.flammpfeil.slashblade.event.";
    private static final boolean NEXT=present("SlashBladeEvent$NextComboEvent"),TIMEOUT=present("SlashBladeEvent$NextOfTimeOutComboEvent"),
            BREAK=present("SlashBladeEvent$BreakEvent"),KILLS=present("SlashBladeEvent$AddKillCountEvent"),
            SOULS=present("SlashBladeEvent$AddProudSoulEvent"),ART=present("SlashBladeEvent$PerformSlashArtEvent"),
            IMPACT=present("SlashBladeEvent$SummonedSwordOnHitEntityEvent"),REFINE=present("RefineProgressEvent"),
            SETTLEMENT=present("RefineSettlementEvent"),ENCHANT=present("bladestand.ProudSoulEnchantmentEvent"),
            COPY=present("bladestand.PreCopySpecialEffectFromBladeEvent");
    public static final boolean UPDATE_ATTACK=present("SlashBladeEvent$UpdateAttackEvent");
    private BladeEvents() {}
    private static boolean present(String name) {
        try {Class.forName(EVENT+name,false,BladeEvents.class.getClassLoader());return true;}
        catch(ClassNotFoundException absent){return false;}
    }
    public static void register() {
        if(NEXT)Next.register();
        if(TIMEOUT)Timeout.register();
        if(UPDATE_ATTACK)Attack.register();
        if(COPY)Copy.register();
    }
    public static ResourceLocation next(ItemStack blade,ISlashBladeState state,LivingEntity user,ResourceLocation next) {
        return NEXT?Next.post(blade,state,user,next):LegacyCombat.nextCombo(blade,state,user,next);
    }
    public static ResourceLocation timeout(ItemStack blade,ISlashBladeState state,LivingEntity user,ResourceLocation next) {
        if(TIMEOUT)return Timeout.post(blade,state,user,next);
        LegacySheathingRepair.timeout(blade,state,user,next);return next;
    }
    public static boolean breaking(ItemStack blade,ISlashBladeState state){return BREAK && Break.post(blade,state);}
    public static int kills(ItemStack blade,ISlashBladeState state,int count){return KILLS?Kills.post(blade,state,count):count;}
    public static int souls(ItemStack blade,ISlashBladeState state,int count){return SOULS?Souls.post(blade,state,count):count;}
    public static ResourceLocation art(LivingEntity user,int ticks,ISlashBladeState state,ResourceLocation combo,SlashArts.ArtsType type) {
        return ART?Art.post(user,ticks,state,combo,type):combo;
    }
    public static void impact(EntityAbstractSummonedSword sword,Entity target){if(IMPACT)Impact.post(sword,target);}
    public record Refine(boolean canceled,int materialCost,int levelCost,int costResult,int refineResult) {}
    public static Refine refine(ItemStack blade,ISlashBladeState state,int material,int level,int cost,int refine,AnvilUpdateEvent original) {
        return REFINE?Progress.post(blade,state,material,level,cost,refine,original):new Refine(false,material,level,cost,refine);
    }
    public static Refine settle(ItemStack blade,ISlashBladeState state,int material,int cost,int refine,AnvilUpdateEvent original) {
        return SETTLEMENT?Settlement.post(blade,state,material,cost,refine,original):new Refine(false,material,0,cost,refine);
    }
    public record SoulEnchant(boolean canceled,Enchantment enchantment,int level,float probability,int cost,boolean tryNext) {}
    public static SoulEnchant enchant(ItemStack blade,ISlashBladeState state,Enchantment enchant,int level,float probability,int cost,SlashBladeEvent.BladeStandAttackEvent original) {
        return ENCHANT?Enchant.post(blade,state,enchant,level,probability,cost,original):new SoulEnchant(false,enchant,level,probability,cost,true);
    }
    public static boolean ownsSoulRoll(Object event){return ENCHANT && Enchant.ROLL.get()==event;}

    private static final class Next {
        static void register(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW,(SlashBladeEvent.NextComboEvent e)->
                e.setNextCombo(LegacyCombat.nextCombo(e.getBlade(),e.getSlashBladeState(),e.getUser(),e.getNextCombo())));}
        static ResourceLocation post(ItemStack blade,ISlashBladeState state,LivingEntity user,ResourceLocation next){
            var event=new SlashBladeEvent.NextComboEvent(blade,state,user,next);MinecraftForge.EVENT_BUS.post(event);return event.getNextCombo();
        }
    }
    private static final class Timeout {
        static void register(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,(SlashBladeEvent.NextOfTimeOutComboEvent e)->
                LegacySheathingRepair.timeout(e.getBlade(),e.getSlashBladeState(),e.getUser(),e.getNextCombo()));}
        static ResourceLocation post(ItemStack blade,ISlashBladeState state,LivingEntity user,ResourceLocation next){
            var event=new SlashBladeEvent.NextOfTimeOutComboEvent(blade,state,user,next);
            return MinecraftForge.EVENT_BUS.post(event)?mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId():event.getNextCombo();
        }
    }
    private static final class Attack {
        static void register(){MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(SlashBladeEvent.UpdateAttackEvent e)->
                e.setNewDamage(LegacyCompat.damage(e.getBlade(),e.getSlashBladeState(),e.getOriginDamage(),e.getNewDamage())));}
    }
    private static final class Copy {
        static void register(){MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH,(mods.flammpfeil.slashblade.event.bladestand.PreCopySpecialEffectFromBladeEvent e)->{
            if(!e.isCanceled() && LegacyNamedBlades.copyEffectPolicy(e.getOriginalEvent(),e.getSEKey())){e.setCopiable(true);e.setRemovable(true);}
        });}
    }
    private static final class Break {
        static boolean post(ItemStack blade,ISlashBladeState state){return MinecraftForge.EVENT_BUS.post(new SlashBladeEvent.BreakEvent(blade,state));}
    }
    private static final class Kills {
        static int post(ItemStack blade,ISlashBladeState state,int count){var event=new SlashBladeEvent.AddKillCountEvent(blade,state,count);MinecraftForge.EVENT_BUS.post(event);return event.getNewCount();}
    }
    private static final class Souls {
        static int post(ItemStack blade,ISlashBladeState state,int count){var event=new SlashBladeEvent.AddProudSoulEvent(blade,state,count);MinecraftForge.EVENT_BUS.post(event);return event.getNewCount();}
    }
    private static final class Art {
        static ResourceLocation post(LivingEntity user,int ticks,ISlashBladeState state,ResourceLocation combo,SlashArts.ArtsType type){
            var event=new SlashBladeEvent.PerformSlashArtEvent(user,ticks,state,combo,type);return MinecraftForge.EVENT_BUS.post(event)?null:event.getComboState();
        }
    }
    private static final class Impact {
        static void post(EntityAbstractSummonedSword sword,Entity target){MinecraftForge.EVENT_BUS.post(new SlashBladeEvent.SummonedSwordOnHitEntityEvent(sword,target));}
    }
    private static final class Progress {
        static Refine post(ItemStack blade,ISlashBladeState state,int material,int level,int cost,int refine,AnvilUpdateEvent original){
            var event=new mods.flammpfeil.slashblade.event.RefineProgressEvent(blade,state,material,level,cost,refine,original);
            boolean canceled=MinecraftForge.EVENT_BUS.post(event);
            return new Refine(canceled,event.getMaterialCost(),event.getLevelCost(),event.getCostResult(),event.getRefineResult());
        }
    }
    private static final class Settlement {
        static Refine post(ItemStack blade,ISlashBladeState state,int material,int cost,int refine,AnvilUpdateEvent original){
            var event=new mods.flammpfeil.slashblade.event.RefineSettlementEvent(blade,state,material,cost,refine,original);
            boolean canceled=MinecraftForge.EVENT_BUS.post(event);
            return new Refine(canceled,event.getMaterialCost(),0,event.getCostResult(),event.getRefineResult());
        }
    }
    private static final class Enchant {
        private static final ThreadLocal<Object> ROLL=new ThreadLocal<>();
        static SoulEnchant post(ItemStack blade,ISlashBladeState state,Enchantment enchant,int level,float probability,int cost,SlashBladeEvent.BladeStandAttackEvent original){
            var event=new mods.flammpfeil.slashblade.event.bladestand.ProudSoulEnchantmentEvent(blade,state,enchant,level,true,probability,cost,original);
            var previous=ROLL.get();ROLL.set(event);
            try{MinecraftForge.EVENT_BUS.post(event);}finally{if(previous==null)ROLL.remove();else ROLL.set(previous);}
            return new SoulEnchant(event.isCanceled(),event.getEnchantment(),event.getEnchantLevel(),event.getProbability(),event.getTotalShrinkCount(),event.willTryNextEnchant());
        }
    }
}
