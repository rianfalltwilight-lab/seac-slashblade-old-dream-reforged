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
    @Override public void onLoad(String pkg){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info){}
}
