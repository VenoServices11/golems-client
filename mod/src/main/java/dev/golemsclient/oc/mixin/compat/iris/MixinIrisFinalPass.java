// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.iris;

import dev.golemsclient.oc.compat.iris.IrisDepthModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public class MixinIrisFinalPass {
    @Inject(method = "finalizeLevelRendering()V", at = @At("TAIL"), remap = false, require = 0)
    private void golems$probeDepth(CallbackInfo ci) {
        IrisDepthModel.afterFinalPass();
    }
}
