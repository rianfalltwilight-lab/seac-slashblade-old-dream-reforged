package org.scex.slashbladelegacy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EnumSet;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.ForgeConfigSpec;

/** Use stable registry IDs across the old and new SlashBlade APIs. */
public final class BladeApi {
    public static final RegistryObject<Item> PROUDSOUL=item("proudsoul");
    public static final RegistryObject<Item> PROUDSOUL_INGOT=item("proudsoul_ingot");
    public static final RegistryObject<Item> PROUDSOUL_TINY=item("proudsoul_tiny");
    public static final RegistryObject<Item> PROUDSOUL_SPHERE=item("proudsoul_sphere");
    public static final RegistryObject<Item> PROUDSOUL_CRYSTAL=item("proudsoul_crystal");
    public static final RegistryObject<Item> PROUDSOUL_TRAPEZOHEDRON=item("proudsoul_trapezohedron");
    public static final RegistryObject<Item> SLASHBLADE=item("slashblade");
    public static final RegistryObject<Item> BLADESTAND_1=item("bladestand_1");
    public static final RegistryObject<Item> BLADESTAND_1_W=item("bladestand_1w");
    public static final RegistryObject<Item> BLADESTAND_2=item("bladestand_2");
    public static final RegistryObject<Item> BLADESTAND_V=item("bladestand_v");
    public static final TagKey<EntityType<?>> RENDER_LAYER_BLACKLIST=TagKey.create(
            Registries.ENTITY_TYPE,new ResourceLocation("slashblade","blacklist/render_layer"));
    private static final MethodHandle SET_COST=costSetter();
    private static final MethodHandle ITEM_NAME=itemNameGetter();
    private static final Class<?> PROUDSOUL_CLASS=proudSoulClass();
    private static final MethodHandle DAMAGE_SCALE=damageScaler();
    private static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER=damageMultiplier();
    private static final MethodHandle SLASH_YAW=slashYawGetter();

    private BladeApi() {}

    private static RegistryObject<Item> item(String path) {
        return RegistryObject.create(new ResourceLocation("slashblade",path),ForgeRegistries.ITEMS);
    }

    public static boolean isProudSoul(ItemStack stack) {
        if(PROUDSOUL_CLASS!=null && PROUDSOUL_CLASS.isInstance(stack.getItem()))return true;
        return stack.is(PROUDSOUL.get()) || stack.is(PROUDSOUL_INGOT.get()) || stack.is(PROUDSOUL_TINY.get())
                || stack.is(PROUDSOUL_SPHERE.get()) || stack.is(PROUDSOUL_CRYSTAL.get()) || stack.is(PROUDSOUL_TRAPEZOHEDRON.get());
    }

    public static SlashArts setProudSoulCost(SlashArts art,int cost) {
        try {
            SET_COST.invokeExact(art,cost);
            return art;
        } catch(Throwable failure) {
            throw new IllegalStateException("Cannot set the SlashBlade art cost",failure);
        }
    }

    public static ResourceLocation getItemName(SlashBladeDefinition definition) {
        // Old definitions always use the regular blade in getBlade().
        if(ITEM_NAME==null)return SLASHBLADE.getId();
        try {
            return (ResourceLocation)ITEM_NAME.invokeExact(definition);
        } catch(Throwable failure) {
            throw new IllegalStateException("Cannot read the SlashBlade item ID",failure);
        }
    }

    public static boolean isInMainhand(ItemStack blade,boolean selected,LivingEntity user) {
        return selected && blade.equals(user.getMainHandItem(),false);
    }

    public static Vec3 getInput(EnumSet<InputCommand> commands) {
        int strafe=(commands.contains(InputCommand.LEFT)?1:0)-(commands.contains(InputCommand.RIGHT)?1:0);
        int forward=(commands.contains(InputCommand.FORWARD)?1:0)-(commands.contains(InputCommand.BACK)?1:0);
        return new Vec3(strafe,0,forward);
    }

    /** Old summoned swords apply their stored damage without a global scale. */
    public static double damageScale(LivingEntity user) {
        if(DAMAGE_SCALE==null)return 1;
        try {
            return (float)DAMAGE_SCALE.invokeExact(user)*DAMAGE_MULTIPLIER.get();
        } catch(Throwable failure) {
            throw new IllegalStateException("Cannot read the SlashBlade damage scale",failure);
        }
    }

    /** Old slash events use the user's yaw without an offset. */
    public static float slashYawOffset(SlashBladeEvent.DoSlashEvent event) {
        if(SLASH_YAW==null)return 0;
        try {
            return (float)SLASH_YAW.invokeExact(event);
        } catch(Throwable failure) {
            throw new IllegalStateException("Cannot read the SlashBlade yaw offset",failure);
        }
    }

    private static MethodHandle slashYawGetter() {
        try {
            return MethodHandles.publicLookup().findVirtual(SlashBladeEvent.DoSlashEvent.class,"getYRot",
                    MethodType.methodType(float.class));
        } catch(NoSuchMethodException oldApi) {
            return null;
        } catch(IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access the SlashBlade yaw offset",failure);
        }
    }

    private static MethodHandle costSetter() {
        try {
            return MethodHandles.publicLookup().unreflect(SlashArts.class.getMethod("setProudSoulCost",int.class))
                    .asType(MethodType.methodType(void.class,SlashArts.class,int.class));
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("SlashBlade does not provide an art cost setter",failure);
        }
    }

    private static MethodHandle itemNameGetter() {
        try {
            return MethodHandles.publicLookup().findVirtual(SlashBladeDefinition.class,"getItemName",
                    MethodType.methodType(ResourceLocation.class));
        } catch(NoSuchMethodException oldApi) {
            return null;
        } catch(IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access the SlashBlade item ID",failure);
        }
    }

    private static Class<?> proudSoulClass() {
        try {
            return Class.forName("mods.flammpfeil.slashblade.item.ItemProudSoul",false,BladeApi.class.getClassLoader());
        } catch(ClassNotFoundException oldApi) {
            return null;
        }
    }

    private static MethodHandle damageScaler() {
        try {
            return MethodHandles.publicLookup().findStatic(AttackManager.class,"getSlashBladeDamageScale",
                    MethodType.methodType(float.class,LivingEntity.class));
        } catch(NoSuchMethodException oldApi) {
            return null;
        } catch(IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access the SlashBlade damage scale",failure);
        }
    }

    private static ForgeConfigSpec.DoubleValue damageMultiplier() {
        if(DAMAGE_SCALE==null)return null;
        try {
            return (ForgeConfigSpec.DoubleValue)SlashBladeConfig.class.getField("SLASHBLADE_DAMAGE_MULTIPLIER").get(null);
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot access the SlashBlade damage multiplier",failure);
        }
    }
}
