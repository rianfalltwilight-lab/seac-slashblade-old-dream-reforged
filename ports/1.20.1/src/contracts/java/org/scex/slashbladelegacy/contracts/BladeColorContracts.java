package org.scex.slashbladelegacy.contracts;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import org.scex.slashbladelegacy.BladeStateAccess;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.scex.slashbladelegacy.*;

/** 检查两条输入路径生成的飞刀颜色和独立反色标记。 */
final class BladeColorContracts {
    static void run(MinecraftServer server,Map<String,Object> report) {
        var rows=new ArrayList<Map<String,Object>>();report.put("blade_color",rows);
        var level=server.overworld();
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(),"BladeColorContract"));
        player.setPos(0,180,0);level.addNewPlayer(player);
        boolean combat=LegacyCompat.LEGACY_COMBAT.get(),summoned=LegacyCompat.SUMMONED_BLADE.get();
        var shots=new ArrayList<LegacySummonedBlade>();
        Consumer<EntityJoinLevelEvent> collect=event->{
            if(event.getEntity() instanceof LegacySummonedBlade blade && blade.getOwner()==player)shots.add(blade);
        };
        MinecraftForge.EVENT_BUS.addListener(collect);
        try {
            LegacyCompat.SUMMONED_BLADE.set(true);
            for(boolean full:List.of(true,false))for(int rgb:new int[]{0x3333FF,0xFF3030,0x35C611})for(boolean inverse:List.of(false,true)) {
                shots.forEach(LegacySummonedBlade::discard);shots.clear();
                LegacyRangeAttack.clear(player);SummonedBladeMode.clear(player);LegacyCompat.LEGACY_COMBAT.set(full);
                var item=new ItemStack(SlashBladeItems.SLASHBLADE.get());
                var tag=new CompoundTag();tag.putBoolean(SummonedBladeMode.MODE,true);item.setTag(tag);
                var state=BladeStateAccess.of(item).orElseThrow();state.setDefaultBewitched(true);state.setProudSoulCount(10);
                state.setColorCode(rgb);state.setEffectColorInverse(inverse);
                item.enchant(Enchantments.POWER_ARROWS,1);
                player.setItemInHand(InteractionHand.MAIN_HAND,item);LegacyMode.bindInventory(player);
                player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow().getCommands().clear();
                input(player,true);input(player,false);
                int expected=inverse?-rgb:rgb;
                boolean passed=state.getColorCode()==(0xFF000000|rgb) && shots.size()==1
                        && shots.get(0).getColor()==expected && state.getProudSoulCount()==9;
                rows.add(Map.of("full_legacy_combat",full,"rgb",rgb,"raw_argb",state.getColorCode(),"inverse",inverse,
                        "projectile_colors",shots.stream().map(LegacySummonedBlade::getColor).toList(),"passed",passed));
                if(!passed)throw new AssertionError("Blade color input: "+rows.get(rows.size()-1));
            }
        } finally {
            shots.forEach(LegacySummonedBlade::discard);MinecraftForge.EVENT_BUS.unregister(collect);
            LegacyRangeAttack.clear(player);SummonedBladeMode.clear(player);player.discard();
            LegacyCompat.LEGACY_COMBAT.set(combat);LegacyCompat.SUMMONED_BLADE.set(summoned);
        }
    }
    private static void input(ServerPlayer player,boolean down) {
        InputClock.next(player);var state=player.getCapability(CapabilityInputState.INPUT_STATE).resolve().orElseThrow();
        var old=state.getCommands().clone();var next=EnumSet.noneOf(InputCommand.class);
        if(down)next.add(InputCommand.M_DOWN);
        state.getCommands().clear();state.getCommands().addAll(next);InputCommandEvent.onInputChange(player,state,old,next);
    }
}
