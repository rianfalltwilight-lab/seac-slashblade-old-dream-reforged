package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.*;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Real PlayerAdvancements (FakePlayer disables awards); vanilla attack and loaded criteria/rewards. */
final class Advancement17Contracts {
    private static int checks;
    static void run(MinecraftServer server,Map<String,Object> report) {
        checks=0;
        var player=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"R87Advancement"),ClientInformation.createDefault());
        // Only borrow FakePlayer's no-op outbound transport; the actor and its
        // PlayerAdvancements remain a normal ServerPlayer, with real persistence.
        player.connection=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"R87AdvTransport")).connection;
        player.initInventoryMenu();
        player.setPos(0,160,32);server.overworld().addNewPlayer(player);
        var target=EntityType.ZOMBIE.create(server.overworld());target.setNoAi(true);target.setPos(0,160,34);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);server.overworld().addFreshEntity(target);
        var first=server.getAdvancements().get(ResourceLocation.parse("slashblade:root"));
        var wood=server.getAdvancements().get(ResourceLocation.parse("slashblade:blade/s_wood"));
        try {
            check(first!=null && wood!=null,"existing IDs load successfully");
            check(first.value().rewards().loot().isEmpty() && wood.value().rewards().loot().isEmpty(),"loaded advancements contain no loot rewards");
            var progress=player.getAdvancements();
            check(!progress.getOrStartProgress(first).isDone() && !progress.getOrStartProgress(wood).isDone(),"fresh survival progress");
            for(var stack:List.of(new ItemStack(Items.IRON_SWORD),new ItemStack(SlashBladeItems.SLASHBLADE.get()))) {
                player.setItemInHand(InteractionHand.MAIN_HAND,stack);target.invulnerableTime=0;player.attack(target);
                check(!progress.getOrStartProgress(first).isDone(),"other swords do not complete wooden-sword achievement");
            }
            var sword=new ItemStack(Items.WOODEN_SWORD);sword.setDamageValue(1);player.setItemInHand(InteractionHand.MAIN_HAND,sword);
            target.invulnerableTime=0;player.attack(target);check(!progress.getOrStartProgress(first).isDone(),"already damaged wooden sword is rejected");
            sword=new ItemStack(Items.WOODEN_SWORD);player.setItemInHand(InteractionHand.MAIN_HAND,sword);
            CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(player,target,player.damageSources().indirectMagic(target,player),1,1,false);
            check(!progress.getOrStartProgress(first).isDone(),"holding new wooden sword does not turn indirect magic into first melee");
            target.invulnerableTime=0;player.attack(target);
            check(progress.getOrStartProgress(first).isDone() && sword.getDamageValue()>0,"actual new wooden sword attack unlocks before wear");
            check(souls(player)==0,"first achievement grants no soul items in r87");
            var wrong=new ItemStack(SlashBladeItems.SLASHBLADE.get());
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player,player.getInventory(),wrong);
            check(!progress.getOrStartProgress(wood).isDone(),"generic blade is not wooden blade");
            var menu=new net.minecraft.world.inventory.CraftingMenu(1,player.getInventory(),
                    net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(),player.blockPosition()));
            player.containerMenu=menu;player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            menu.getSlot(3).set(new ItemStack(Items.OAK_LOG));menu.getSlot(5).set(new ItemStack(Items.OAK_LOG));menu.getSlot(7).set(sword);
            check(menu.getSlot(0).getItem().is(SlashBladeItems.SLASHBLADE_WOOD.get()),"actual wooden-blade recipe matches the used sword and logs");
            menu.quickMoveStack(player,0);player.inventoryMenu.broadcastChanges();
            check(player.getInventory().items.stream().anyMatch(s->s.is(SlashBladeItems.SLASHBLADE_WOOD.get()))
                    && progress.getOrStartProgress(wood).isDone(),"server crafting result reaches inventory and unlocks wooden-blade progress");
            player.containerMenu=player.inventoryMenu;
            check(souls(player)==0,"wooden-blade achievement grants no soul items in r87");
            check(!progress.award(first,"attackTheEntity") && !progress.award(wood,"crafting"),"completed existing criteria are not awarded twice");
            progress.save();progress.reload(server.getAdvancements());
            check(progress.getOrStartProgress(first).isDone() && progress.getOrStartProgress(wood).isDone() && souls(player)==0,"saved completion reload preserves IDs and no extra loot");
            report.put("legacy17_first_advancements",Map.of("checks",checks,"actual_wooden_sword_attack",true,
                    "server_menu_crafting",true,"item_rewards",0,"save_reload",true,
                    "scope","server crafting menu, real melee and advancement-file reload; not a GUI session"));
        } finally {player.getAdvancements().stopListening();target.discard();player.discard();}
    }
    private static int souls(ServerPlayer player) {
        int result=0;for(var stack:player.getInventory().items)
            if(stack.is(SlashBladeItems.PROUDSOUL.get()) || stack.is(SlashBladeItems.PROUDSOUL_TINY.get()) || stack.is(SlashBladeItems.PROUDSOUL_SPHERE.get()))result+=stack.getCount();
        return result;
    }
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError("r87 advancement: "+message);}
}
