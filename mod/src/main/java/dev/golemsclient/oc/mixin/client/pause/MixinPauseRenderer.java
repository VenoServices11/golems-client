// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
package dev.golemsclient.oc.mixin.client.pause;

import dev.golemsclient.oc.client.gui.pausescreen.PauseStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(GameRenderer.class)
public class MixinPauseRenderer {
    @Unique
    private static final Matrix4f golems$bobPose = new Matrix4f();

    @Unique
    private static final Matrix3f golems$bobNormal = new Matrix3f();

    @Inject(method = "render", at = @At("HEAD"))
    private void golems$stepPauseStage(CallbackInfo ci) {
        PauseStage.frame();
    }

    @Inject(method = {"bobHurt", "bobView"}, at = @At("HEAD"))
    private void golems$keepPauseBob(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        golems$bobPose.set(poseStack.last().pose());
        golems$bobNormal.set(poseStack.last().normal());
    }

    @Inject(method = {"bobHurt", "bobView"}, at = @At("RETURN"))
    private void golems$fadePauseBob(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        float keep = PauseStage.bobKeep();
        if (keep >= 1.0F) return;
        PoseStack.Pose last = poseStack.last();
        golems$bobPose.lerp(last.pose(), keep, last.pose());
        golems$bobNormal.lerp(last.normal(), keep, last.normal());
    }

    @Inject(method = "getFov", at = @At("HEAD"))
    private void golems$pauseFovPass(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<?> cir) {
        PauseStage.fovPass(useFovSetting);
    }
    
}
