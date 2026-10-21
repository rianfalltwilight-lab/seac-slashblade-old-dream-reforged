package org.scex.slashbladelegacy.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Check optional upstream fixes without binding the mod to a release number. */
public final class LegacyMixinPlugin implements IMixinConfigPlugin {
    private Boolean addonTimingCompatible;
    private static final String MELEE="(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V";
    private static final String OLD_MELEE="(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZ)V";
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        if(mixin.endsWith(".LegacyManagedMeleeMixin"))return declares(target,"doMeleeAttack",MELEE,OLD_MELEE);
        if(mixin.endsWith(".LegacyManagedMeleeOldMixin"))return declares(target,"doMeleeAttack",OLD_MELEE,MELEE)
                && !declares(target,"doMeleeAttack",MELEE,OLD_MELEE);
        if(mixin.endsWith(".LegacyAirTrickMixin"))return declares(target,"executeAirTrick",
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;)Z");
        if(mixin.endsWith(".LegacyLockOnMixin"))return instanceInput(target);
        if(mixin.endsWith(".LegacySuperReleaseMixin"))return declares(target,"releaseSSA","(Lnet/minecraft/server/level/ServerPlayer;)V");
        if(mixin.endsWith(".LegacyRefineLimitMixin"))return declares(target,"refineLimitCheck",
                "(Lmods/flammpfeil/slashblade/event/RefineProgressEvent;)V");
        if(mixin.endsWith(".LegacyAddonSwordDelayMixin") || mixin.endsWith(".LegacyGaleTimingMixin")
                || mixin.endsWith(".LegacyLightningTimingMixin"))return addonTimingCompatible();
        if(mixin.endsWith(".LegacyBladePoseMixin") || mixin.endsWith(".RenderStallLayerMixin"))return declaresLayerRender(target);
        if(mixin.endsWith(".LegacyObjVertexMixin"))return knownObjWriter(target);
        if(!mixin.endsWith(".LegacySoulProbabilityMixin"))return true;
        try {
            var node=readNode(target);
            String descriptor="(Lmods/flammpfeil/slashblade/event/bladestand/ProudSoulEnchantmentEvent;)V";
            var named=node.methods.stream().filter(m->m.name.equals("proudSoulEnchantmentProbabilityCheck")).toList();
            if(named.isEmpty())return false;
            if(named.stream().anyMatch(m->m.desc.equals(descriptor)))return true;
            throw new IllegalStateException("SlashBlade soul probability callback changed signature; requires a compatibility update");
        }catch(java.io.IOException | ClassNotFoundException failure){throw new IllegalStateException("Cannot inspect SlashBlade soul probability callback",failure);}
    }
    private static ClassNode readNode(String name) throws java.io.IOException,ClassNotFoundException {
        // Read the class resource without running other launch transformers during prepare.
        try(var stream=MixinService.getService().getResourceAsStream(name.replace('.','/')+".class")) {
            if(stream==null)throw new ClassNotFoundException(name);
            var node=new ClassNode();
            new ClassReader(stream).accept(node,ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    private static boolean declares(String target,String name,String descriptor,String... alternatives) {
        try {
            var node=readNode(target);
            var named=node.methods.stream().filter(method->method.name.equals(name)).toList();
            if(named.isEmpty())return false;
            if(named.stream().anyMatch(method->method.desc.equals(descriptor)))return true;
            if(named.stream().anyMatch(method->java.util.Arrays.asList(alternatives).contains(method.desc)))return false;
            throw new IllegalStateException("SlashBlade method changed signature: "+target+"."+name);
        } catch(ClassNotFoundException absent) {
            return false;
        } catch(java.io.IOException failure) {
            throw new IllegalStateException("Cannot inspect SlashBlade method: "+target+"."+name,failure);
        }
    }
    /** The lock-on hook needs an instance onInputChange that takes one input event. */
    private static boolean instanceInput(String target) {
        try {
            var node=readNode(target);
            var named=node.methods.stream().filter(method->method.name.equals("onInputChange")).toList();
            boolean apply=named.stream().anyMatch(method->(method.access & org.objectweb.asm.Opcodes.ACC_STATIC)==0
                    && (method.desc.equals("(Lmods/flammpfeil/slashblade/event/handler/InputCommandEvent;)V")
                        || method.desc.equals("(Lmods/flammpfeil/slashblade/event/InputCommandEvent;)V")));
            if(!apply)System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.WARNING,
                    "SlashBlade lock-on input entry changed; legacy mode keeps the upstream lock-on search");
            return apply;
        } catch(ClassNotFoundException absent) {
            return false;
        } catch(java.io.IOException failure) {
            throw new IllegalStateException("Cannot inspect SlashBlade lock-on input",failure);
        }
    }
    private boolean addonTimingCompatible() {
        if(addonTimingCompatible!=null)return addonTimingCompatible;
        try {
            addonTimingCompatible=org.scex.slashbladelegacy.LegacyAddonCompatibility.supportsTiming(
                    readNode("cn.mmf.slashblade_addon.entity.BlisteringSwordsEntity"),
                    readNode("cn.mmf.slashblade_addon.specialattacks.GaleSwords"),
                    readNode("cn.mmf.slashblade_addon.specialattacks.LightingSwords"));
            System.getLogger("slashblade_legacy_compat").log(
                    addonTimingCompatible?System.Logger.Level.INFO:System.Logger.Level.WARNING,
                    addonTimingCompatible?"Enabling SJAP timing compatibility":
                            "SJAP timing targets changed; retaining upstream Gale and Lightning timing");
        } catch(ClassNotFoundException absent) {
            addonTimingCompatible=false;
        } catch(java.io.IOException failure) {
            addonTimingCompatible=false;
            System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.WARNING,
                    "Cannot inspect optional SJAP timing hooks; retaining upstream timing",failure);
        }
        return addonTimingCompatible;
    }
    private static boolean declaresLayerRender(String target) {
        try {
            var node=readNode(target);
            // The shared render entry moved from LayerMainBlade to LayerSlashBlade.
            return node.methods.stream().anyMatch(method->method.name.equals("render") && method.desc.equals(
                    "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V"));
        } catch(ClassNotFoundException absent) {
            return false;
        } catch(java.io.IOException failure) {
            throw new IllegalStateException("Cannot inspect SlashBlade render layer",failure);
        }
    }
    private static boolean knownObjWriter(String target) {
        try {
            var node=readNode(target);
            boolean face=false,vertex=false;
            for(var method:node.methods) {
                if(method.name.equals("addFaceForRender") && method.desc.equals("(Lcom/mojang/blaze3d/vertex/VertexConsumer;FLorg/joml/Matrix4f;II)V"))
                    face=org.scex.slashbladelegacy.TimelineBytecode.fingerprint(method).equals("a80ec5f1d883fcc18184d17ae04e676e5a868847c0827bc2e209334fbbed62aa");
                if(method.name.equals("putVertex") && method.desc.equals("(Lcom/mojang/blaze3d/vertex/VertexConsumer;ILorg/joml/Matrix4f;FFFII)V"))
                    vertex=java.util.Set.of("d136943410f6cd45b98dd8054fd276247fad5ccc42c9557aca51117d213f0af6", "1bb03142a32f854328cf6fc3fbac462b217f585afb1e7d3fd19a4d736eea1ff1").contains(org.scex.slashbladelegacy.TimelineBytecode.fingerprint(method));
            }
            boolean apply=face && vertex;
            System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.INFO,
                    apply?"Enabling bulk SlashBlade OBJ vertex writes":"Upstream OBJ writer changed; retaining its implementation");
            return apply;
        } catch(java.io.IOException | ClassNotFoundException failure) {
            System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.WARNING,"Cannot inspect optional OBJ optimization; retaining upstream implementation",failure);
            return false;
        }
    }
    @Override public void onLoad(String pkg){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info){
    }
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info){}
}
