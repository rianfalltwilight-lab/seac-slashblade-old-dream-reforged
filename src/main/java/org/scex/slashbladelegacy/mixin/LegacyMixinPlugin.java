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
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        if(mixin.endsWith(".LegacyServerCarryTypeMixin"))
            return org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().getSide()==org.spongepowered.asm.mixin.MixinEnvironment.Side.SERVER;
        if(mixin.endsWith(".LegacyBladePoseMixin") || mixin.endsWith(".RenderStallLayerMixin"))return declaresLayerRender(target);
        if(mixin.endsWith(".BoundedTimelineMixin"))return knownTimeline(target);
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
    /** Read the class resource. Do not run other launch transformers during prepare.
     * Use the bytecode provider only when the resource is not visible to the mixin service. */
    private static ClassNode readNode(String name) throws java.io.IOException,ClassNotFoundException {
        try(var stream=MixinService.getService().getResourceAsStream(name.replace('.','/')+".class")) {
            if(stream!=null) {
                var node=new ClassNode();
                new ClassReader(stream).accept(node,ClassReader.SKIP_FRAMES);
                return node;
            }
        }
        // An absent optional target throws ClassNotFoundException here, as before.
        var node=MixinService.getService().getBytecodeProvider().getClassNode(name);
        System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.WARNING,
                "Class resource is not visible; inspected "+name+" through the bytecode provider");
        return node;
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
                if(method.name.equals("addFaceForRender") && method.desc.equals("(Lcom/mojang/blaze3d/vertex/VertexConsumer;FLorg/joml/Matrix4f;Lorg/joml/Matrix3f;II)V"))
                    face=org.scex.slashbladelegacy.TimelineBytecode.fingerprint(method).equals("051e442e2e68db5f4b62098b2b0b0ff73f003cca84d8f125af9dd69237899b69");
                if(method.name.equals("putVertex") && method.desc.equals("(Lcom/mojang/blaze3d/vertex/VertexConsumer;ILorg/joml/Matrix4f;Lorg/joml/Matrix3f;FFFII)V"))
                    vertex=org.scex.slashbladelegacy.TimelineBytecode.fingerprint(method).equals("786715329f7b805f28ed1096cbe9aea33e996237012b9a7748dcc626becf5a4f");
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
    private static boolean knownTimeline(String target) {
        try {
            var node=readNode(target);
            boolean constructor=false,accept=false;
            for(var method:node.methods) {
                if(method.name.equals("<init>") && method.desc.equals("(Ljava/util/Map;)V"))
                    constructor=org.scex.slashbladelegacy.TimelineBytecode.fingerprint(method).equals("c59e5b100cdda05d4073a8095264f1a3c15565313047a4419ef7a184d51ca97f");
                if(method.name.equals("accept") && method.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;)V"))
                    accept=org.scex.slashbladelegacy.TimelineBytecode.timelineLoopFingerprint(method).equals("5babf8b96e97fd260842ab1a65f223e6cc03e5d28a63feb17f6efc1171e41117");
            }
            boolean apply=constructor && accept;
            System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.INFO,
                    apply?"Enabling bounded SlashBlade timeline queries":"Upstream timeline changed; retaining its implementation");
            return apply;
        } catch(java.io.IOException | ClassNotFoundException failure) {
            System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.WARNING,"Cannot inspect optional timeline optimization; retaining upstream implementation",failure);
            return false;
        }
    }
    @Override public void onLoad(String pkg){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info){
        if(mixin.endsWith(".LegacyBladeMotionApiMixin") && org.scex.slashbladelegacy.BladeMotionApiPatch.PARENT.equals(node.superName)) {
            try {
                var parent=readNode(node.superName.replace('/','.'));
                if(org.scex.slashbladelegacy.BladeMotionApiPatch.apply(node,parent))
                    System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.INFO,"Restored the typed SlashBlade motion getter for existing addons");
            } catch(java.io.IOException | ClassNotFoundException failure) {
                throw new IllegalStateException("Cannot inspect SlashBlade motion manager",failure);
            }
        }
        if(mixin.endsWith(".LegacyBladeStateConstructorMixin") && org.scex.slashbladelegacy.BladeStateConstructorPatch.apply(node))
            System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.INFO,"Restored the old SlashBlade state constructor for existing addons");
        if(mixin.endsWith(".LegacyServerCarryTypeMixin")) {
            int replaced=org.scex.slashbladelegacy.ServerCarryTypePatch.apply(node);
            if(replaced>0)System.getLogger("slashblade_legacy_compat").log(System.Logger.Level.INFO,
                    "Disabled "+replaced+" client carry pose factories on the dedicated server");
        }
    }
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info){}
}
