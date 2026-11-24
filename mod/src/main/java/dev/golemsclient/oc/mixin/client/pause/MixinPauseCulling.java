// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
package dev.golemsclient.oc.mixin.client.pause;

import dev.golemsclient.oc.client.gui.pausescreen.PauseStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.SectionOcclusionGraph;

@Mixin(SectionOcclusionGraph.class)
public abstract class MixinPauseCulling {
    @Shadow
    public abstract void invalidate();

    @Inject(method = "update", at = @At("HEAD"))
    private void golems$regraphStagedCamera(CallbackInfo ci) {
        if (PauseStage.consumeRegraph()) this.invalidate();
    }
}
