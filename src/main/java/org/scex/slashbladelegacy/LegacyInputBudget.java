package org.scex.slashbladelegacy;

import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

public final class LegacyInputBudget {
    private static final WeakHashMap<ServerPlayer,Long> CLICKS=new WeakHashMap<>();
    private LegacyInputBudget(){}
    public static void clear(net.minecraft.world.entity.player.Player player){CLICKS.remove(player);}
    public static boolean accept(ServerPlayer player) {
        long tick=player.server.overworld().getGameTime();
        Long previous=CLICKS.get(player);
        if(previous!=null && previous==tick)return false;
        CLICKS.put(player,tick);return true;
    }
}
