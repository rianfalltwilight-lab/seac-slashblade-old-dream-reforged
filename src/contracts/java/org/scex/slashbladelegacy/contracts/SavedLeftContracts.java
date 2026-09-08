package org.scex.slashbladelegacy.contracts;

import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Anonymous item-only snapshots; no production world or player state is loaded. */
final class SavedLeftContracts {
    static void run(MinecraftServer server,Map<String,Object> report) throws Exception {
        var rows=new ArrayList<Map<String,Object>>();report.put("saved_left",rows);
        var level=server.overworld();int index=0;
        for(String line:Files.readAllLines(Path.of("saved-left.snbt"))) {
            var blade=ItemStack.parse(server.registryAccess(),TagParser.parseTag(line)).orElseThrow();
            var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SavedLeft"+index));
            player.setPos(24,160,0);player.setYRot(0);player.setXRot(0);player.setOnGround(true);level.addNewPlayer(player);
            player.setItemInHand(InteractionHand.MAIN_HAND,blade);player.experienceLevel=300;
            var state=BladeStateAccess.of(blade).orElseThrow();
            var row=new LinkedHashMap<String,Object>();rows.add(row);row.put("index",index++);row.put("definition",state.getTranslationKey());row.put("on_click_before",state.onClick());
            var target=EntityType.HUSK.create(level);target.setPos(24,160,2.5);target.setNoAi(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.setHealth(1000);level.addFreshEntity(target);
            try {
                player.attack(target);
                row.put("health_after",target.getHealth());row.put("combo_after",state.getComboSeq().toString());row.put("on_click_after",state.onClick());row.put("passed",target.getHealth()<1000);
            }catch(Throwable e){row.put("error",e.toString());row.put("passed",false);}
            finally {target.discard();player.discard();}
        }
        if(rows.stream().anyMatch(r->!Boolean.TRUE.equals(r.get("passed"))))throw new IllegalStateException("Saved left failed: "+rows);
    }
}
