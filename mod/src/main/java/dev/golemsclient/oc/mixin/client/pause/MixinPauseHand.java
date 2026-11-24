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

import net.minecraft.client.renderer.ItemInHandRenderer;


@Mixin(ItemInHandRenderer.class)

public class MixinPauseHand {
    @Unique
    private static final float GOLEMS_SINK = 0.7F;

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void golems$sinkHand(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        poseStack.translate(0.0F, -PauseStage.handSink() * GOLEMS_SINK, 0.0F);
    }
    
}
