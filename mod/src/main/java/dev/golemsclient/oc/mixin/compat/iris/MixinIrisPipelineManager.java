// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.iris;

import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import dev.golemsclient.oc.compat.iris.IrisLobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public abstract class MixinIrisPipelineManager {
    @ModifyVariable(method = "preparePipeline(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;", at = @At("HEAD"), argsOnly = true, remap = false, require = 0, expect = 0)
    private NamespacedId golems$routeLobby(NamespacedId dimension) {
        if (!IrisLobbyPipelines.armed) {
            return dimension;
        }
        return IrisLobbyPipelines.route(this, dimension);
    }
}
