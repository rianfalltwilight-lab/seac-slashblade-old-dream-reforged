package org.scex.slashbladelegacy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.scex.slashbladelegacy.*;

/** r87 procedural blade, sheath, afterimages and trail; the dependency supplies only model/render APIs. */
public final class LegacyHeldRenderer {
    private static final ResourceLocation TRAIL = ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"model/legacy17/trail.obj");
    private static final ResourceLocation TRAIL_TEXTURE = ResourceLocation.fromNamespaceAndPath(LegacyCompat.MOD_ID,"model/legacy17/trail.png");
    private LegacyHeldRenderer() {}

    public static boolean handles(LivingEntity entity) {
        return org.scex.slashbladelegacy.LegacyMode.legacy(entity) && BladeStateAccess.of(entity.getMainHandItem()).isPresent()
                && !SwordType.from(entity.getMainHandItem()).contains(SwordType.NOSCABBARD)
                && !entity.getType().is(mods.flammpfeil.slashblade.data.tag.SlashBladeEntityTypeTagProvider.EntityTypeTags.RENDER_LAYER_BLACKLIST);
    }

    public static void firstPerson(PoseStack poses, MultiBufferSource buffers, int light) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null || mc.gameMode==null || mc.options.getCameraType()!=CameraType.FIRST_PERSON
                || mc.options.hideGui || mc.gameMode.isAlwaysFlying() || player.isSleeping())return;
        poses.pushPose();
        try {
            // glLoadIdentity in the original renderer meant eye space. Account for modern global
            // model-view separately so yaw/pitch cannot rotate the held blade through the screen.
            poses.last().pose().set(RenderSystem.getModelViewMatrix()).invert();
            poses.last().normal().set(RenderSystem.getModelViewMatrix()).transpose();
            poses.translate(-.35,-.1,-.8);
            poses.mulPose(Axis.XP.rotationDegrees(-3));poses.mulPose(Axis.ZP.rotationDegrees(180));
            poses.translate(0,.25,0);
            poses.mulPose(Axis.of(new Vector3f(.9f,.1f,0).normalize()).rotationDegrees(-25));
            poses.scale(1.2f,1,1);
            render(poses,buffers,light,player,mc.getTimer().getGameTimeDeltaPartialTick(false),false);
        } finally {poses.popPose();}
    }

    public static void render(PoseStack poses, MultiBufferSource buffers, int light, LivingEntity entity, float partial, boolean adjust) {
        var blade=entity.getMainHandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        if(SwordType.from(blade).contains(SwordType.NOSCABBARD))return;
        var current=state.peekCurrentComboStateTicks(entity).getValue();
        LegacyMove move=LegacyCombat.visualMove(current);
        float swing=entity.getAttackAnim(partial);
        if(move!=LegacyMove.NONE && entity.attackAnim==0)swing=1;
        float progress=LegacyBladePose.progress(move,swing);
        boolean barrier=entity instanceof net.minecraft.world.entity.player.Player player
                && LegacyProjectileGuard.barrierAvailable(player,player.getTicksUsingItem());
        LegacyMove mainPose=LegacyDualWield.active(entity)?LegacyDualWield.mainHandPose(move):null;
        if(adjust && LegacyDualWield.hasOffhandBlade(entity))
            offhandCarry(poses,buffers,light,entity,mainPose!=null);
        var model=BladeModelManager.getInstance().getModel(state.getModel().orElse(DefaultResources.resourceDefaultModel));
        var texture=state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
        poses.pushPose();
        try {
            if(adjust){var offset=state.getAdjust();poses.translate(offset.x/10,-offset.y/10,-offset.z/10);}
            if(mainPose!=null) {
                renderBlade(poses,buffers,light,entity.getOffhandItem(),move,progress,barrier,entity.tickCount+partial);
                renderBlade(poses,buffers,light,blade,mainPose,1,barrier,entity.tickCount+partial);
            } else renderBlade(poses,buffers,light,blade,move,progress,barrier,entity.tickCount+partial);
            poses.pushPose();
            try {
                poses.mulPose(LegacyBladePose.matrix(move,progress,true));
                draw(blade,model,"sheath",texture,poses,buffers,light,1,false);
                draw(blade,model,"sheath_luminous",texture,poses,buffers,light,1,true);
                if(state.isCharged(entity))BladeRenderState.renderChargeEffect(blade,entity.tickCount+partial,model,"effect",
                        ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png"),poses,buffers,light);
            } finally {poses.popPose();}
        } finally {BladeRenderState.resetCol();poses.popPose();}
    }

    private static void renderBlade(PoseStack poses,MultiBufferSource buffers,int light,ItemStack blade,
                                    LegacyMove move,float progress,boolean barrier,float ticks) {
            var state=BladeStateAccess.of(blade).orElseThrow();
            var model=BladeModelManager.getInstance().getModel(state.getModel().orElse(DefaultResources.resourceDefaultModel));
            var texture=state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
            String part=state.isBroken()?"blade_damaged":"blade";
            int copies=move.scabbard || progress==1?1:3;
            for(int blur=0;blur<copies;blur++) {
                float p=progress*(float)Math.pow(.8,blur),opacity=(float)Math.pow(.5,blur);
                poses.pushPose();
                try {
                    poses.mulPose(LegacyBladePose.matrix(move,p,false,barrier,ticks));
                    if(barrier && move==LegacyMove.NONE)p=.5f;
                    draw(blade,model,part,texture,poses,buffers,light,opacity,false);
                    if(!move.scabbard)draw(blade,model,part+"_unsheathe",texture,poses,buffers,light,opacity,false);
                    draw(blade,model,part+"_luminous",texture,poses,buffers,light,opacity,true);
                    if(!move.scabbard)draw(blade,model,part+"_unsheathe_luminous",texture,poses,buffers,light,opacity,true);
                    if(barrier || !move.scabbard && move!=LegacyMove.NOUTOU && move!=LegacyMove.HIRA_TUKI
                            && move!=LegacyMove.STINGER && move!=LegacyMove.HELM_LANDING)
                        trail(blade,poses,buffers,light,state.isBroken(),state.getColorCode(),p,opacity,barrier);
                } finally {poses.popPose();}
            }
    }

    private static void offhandCarry(PoseStack poses,MultiBufferSource buffers,int light,LivingEntity entity,boolean drawn) {
        var blade=entity.getOffhandItem();var state=BladeStateAccess.of(blade).orElseThrow();
        var model=BladeModelManager.getInstance().getModel(state.getModel().orElse(DefaultResources.resourceDefaultModel));
        var texture=state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
        poses.pushPose();
        try {
            if(entity.isCrouching()){poses.translate(0,.203125,0);poses.mulPose(Axis.XP.rotationDegrees(30));}
            poses.translate(0,-state.getAdjust().y/10,0);
            poses.mulPose(LegacyBladePose.offhandCarry());
            if(!drawn) {
                String part=state.isBroken()?"blade_damaged":"blade";
                draw(blade,model,part,texture,poses,buffers,light,1,false);
                draw(blade,model,part+"_luminous",texture,poses,buffers,light,1,true);
            }
            draw(blade,model,"sheath",texture,poses,buffers,light,1,false);
            draw(blade,model,"sheath_luminous",texture,poses,buffers,light,1,true);
        } finally {BladeRenderState.resetCol();poses.popPose();}
    }

    private static void draw(ItemStack blade,WavefrontObject model,String part,ResourceLocation texture,PoseStack poses,
                             MultiBufferSource buffers,int light,float opacity,boolean luminous) {
        BladeRenderState.setCol(((int)(255*opacity)<<24)|0xFFFFFF);
        if(luminous)BladeRenderState.renderOverridedLuminous(blade,model,part,texture,poses,buffers,light);
        else BladeRenderState.renderOverrided(blade,model,part,texture,poses,buffers,light);
    }

    private static void trail(ItemStack blade,PoseStack poses,MultiBufferSource buffers,int light,boolean broken,int color,float progress,float opacity,boolean barrier) {
        float alpha=(float)Math.sin(progress*Math.PI);
        if(alpha<=0)return;
        poses.pushPose();
        try {
            poses.scale(barrier?1:broken?.4f:1,barrier?.8f:broken?.5f:alpha*2,1);
            poses.mulPose(Axis.ZP.rotationDegrees(10*(1-alpha)));
            var model=BladeModelManager.getInstance().getModel(TRAIL);
            int rgb=Math.abs(color)&0xFFFFFF;
            BladeRenderState.setCol(((int)(0x66*alpha*opacity)<<24)|(0xFFFFFF-rgb));
            BladeRenderState.renderOverridedReverseLuminous(blade,model,"obj1",TRAIL_TEXTURE,poses,buffers,light);
            BladeRenderState.setCol(((int)(0xFF*alpha*opacity)<<24)|rgb);
            BladeRenderState.renderOverridedLuminous(blade,model,"obj1",TRAIL_TEXTURE,poses,buffers,light);
        } finally {poses.popPose();}
    }
}
