package org.scex.slashbladelegacy;

import java.util.List;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.data.tag.SlashBladeEntityTypeTagProvider.EntityTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

/** r87 ConfigEntityListManager defaults, evaluated on existing entities without spawning probes. */
public final class LegacyTargets {
    private LegacyTargets() {}

    public static boolean attackable(Player player, Entity entity) {
        Entity parent = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        if (!(parent instanceof LivingEntity living) || parent == player || !living.isAlive()
                || living.isSpectator() || player.isAlliedTo(living)
                || living.hasPassenger(passenger -> passenger == player)
                || living.getType().is(EntityTypeTags.ATTACKABLE_BLACKLIST)) return false;
        // Keep the server's explicit PvP setting and modern noncombat decoration protection.
        if (living instanceof Player other) return !other.getAbilities().invulnerable
                && SlashBladeConfig.PVP_ENABLE.get() && (player.getServer()==null || player.getServer().isPvpAllowed()) && player.canHarmPlayer(other);
        if (living instanceof ArmorStand) return false;
        if (living instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) return false;
        if (living instanceof Enemy || HostileTargeting.isAdditionalHostile(living)) return true;
        if (living instanceof Mob mob && (mob.getTarget() == player || mob.getLastHurtByMob() == player)) return true;
        if (SlashBladeConfig.FRIENDLY_ENABLE.get()) return true;
        // 1.7.10 accepted other living entities by default. IMob was an override, not a requirement.
        return defaultAttackable(living);
    }

    public static boolean defaultAttackable(LivingEntity living) {
        if(living instanceof Enemy)return true;
        return !(living instanceof Animal || living instanceof AmbientCreature || living instanceof WaterAnimal
                || living instanceof AbstractGolem || living instanceof OwnableEntity || living instanceof Merchant
                || living instanceof ArmorStand || living instanceof Player);
    }

    public static List<Entity> within(Player player, AABB bounds) {
        // NeoForge's spatial query includes multipart hitboxes with an intersection check.
        // Do not append the native radial reach filter: r87 getBBofCombo is the complete hit region.
        return player.level().getEntities(player, bounds, entity -> attackable(player, entity));
    }
}
