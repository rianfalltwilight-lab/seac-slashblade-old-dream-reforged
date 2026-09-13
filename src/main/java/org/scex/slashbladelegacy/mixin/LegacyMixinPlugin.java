package org.scex.slashbladelegacy.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Probe an optional upstream callback, without binding the whole mod to a release number. */
public final class LegacyMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        if(mixin.endsWith(".BoundedTimelineMixin"))return knownTimeline(target);
        if(!mixin.endsWith(".LegacySoulProbabilityMixin"))return true;
        try {
            // NeoForge's ModLauncher provider supports transformed bytecode retrieval only.
            var node=MixinService.getService().getBytecodeProvider().getClassNode(target);
            String descriptor="(Lmods/flammpfeil/slashblade/event/bladestand/ProudSoulEnchantmentEvent;)V";
            var named=node.methods.stream().filter(m->m.name.equals("proudSoulEnchantmentProbabilityCheck")).toList();
            if(named.isEmpty())return false;
            if(named.stream().anyMatch(m->m.desc.equals(descriptor)))return true;
            throw new IllegalStateException("SlashBlade soul probability callback changed signature; requires a compatibility update");
        }catch(java.io.IOException | ClassNotFoundException failure){throw new IllegalStateException("Cannot inspect SlashBlade soul probability callback",failure);}
    }
    private static boolean knownTimeline(String target) {
        try {
            var node=MixinService.getService().getBytecodeProvider().getClassNode(target);
            boolean constructor=false,accept=false;
            for(var method:node.methods) {
                if(method.name.equals("<init>") && method.desc.equals("(Ljava/util/Map;)V"))
                    constructor=org.scex.slashbladelegacy.TimelineBytecode.fingerprint(method).equals("c59e5b100cdda05d4073a8095264f1a3c15565313047a4419ef7a184d51ca97f");
                if(method.name.equals("accept") && method.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;)V"))
                    accept=org.scex.slashbladelegacy.TimelineBytecode.fingerprint(method).equals("5babf8b96e97fd260842ab1a65f223e6cc03e5d28a63feb17f6efc1171e41117");
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
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info){}
}
