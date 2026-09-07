package org.scex.slashbladelegacy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anchor the viewmodel in eye space, including Minecraft's global model-view transform. */
@Mixin(targets="mods.flammpfeil.slashblade.client.renderer.model.BladeFirstPersonRender",remap=false)
public abstract class LegacyFirstPersonMixin {
    @Redirect(method="render",at=@At(value="INVOKE",target="Lorg/joml/Matrix4f;identity()Lorg/joml/Matrix4f;"))
    private Matrix4f legacyCompat$eyeSpace(Matrix4f matrix) {
        return matrix.set(RenderSystem.getModelViewMatrix()).invert();
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lorg/joml/Matrix3f;identity()Lorg/joml/Matrix3f;"))
    private Matrix3f legacyCompat$eyeNormals(Matrix3f matrix) {
        // Normal transform of inverse(modelView) is transpose(modelView).
        return matrix.set(RenderSystem.getModelViewMatrix()).transpose();
    }

    @ModifyArg(method="render",at=@At(value="INVOKE",target="Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;",ordinal=0),index=0)
    private float legacyCompat$eyeYaw(float worldYaw) {
        return 0.0F;
    }

    @ModifyArg(method="render",at=@At(value="INVOKE",target="Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;",ordinal=2),index=0)
    private float legacyCompat$eyePitch(float worldPitch) {
        return 0.0F;
    }

    @Inject(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",ordinal=0))
    private void legacyCompat$heldPosition(PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci) {
        // Screen-space placement, before the paired body/blade animation. Third-person is untouched.
        poses.translate(-0.20F,0.0F,0.0F);
    }
}
