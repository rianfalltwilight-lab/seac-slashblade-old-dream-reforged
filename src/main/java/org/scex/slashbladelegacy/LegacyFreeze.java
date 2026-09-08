package org.scex.slashbladelegacy;

/** r87 HeavyRain's separate freeze timer; ordinary stun remains the framework's AI interruption. */
public final class LegacyFreeze {
    private static final String KEY="slashblade_legacy_compat.freeze_until";
    private LegacyFreeze() {}
    public static void apply(net.minecraft.world.entity.LivingEntity target,int ticks) {
        if(!(target instanceof net.minecraft.world.entity.Mob) || target.level().isClientSide || ticks<=0)return;
        long now=target.level().getGameTime(),old=target.getPersistentData().getLong(KEY);
        target.getPersistentData().putLong(KEY,Math.max(old>now+200?now:old,now+Math.min(ticks,200)));
    }
    public static void tick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        var entity=event.getEntity();
        if(entity.level().isClientSide || !(entity instanceof net.minecraft.world.entity.Mob) || !entity.getPersistentData().contains(KEY))return;
        long left=entity.getPersistentData().getLong(KEY)-entity.level().getGameTime();
        if(!LegacyRangeAttack.enabled() || left<=0 || left>200)entity.getPersistentData().remove(KEY);
        else event.setCanceled(true);
    }
}
