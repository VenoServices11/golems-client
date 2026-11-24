// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.iris;

import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import dev.golemsclient.oc.compat.iris.IrisLobbyUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public class MixinIrisCommonUniforms {
    @Inject(
            method = "addDynamicUniforms(Lnet/irisshaders/iris/gl/uniform/DynamicUniformHolder;Lnet/irisshaders/iris/gl/state/FogMode;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private static void golems$lobbyWave(DynamicUniformHolder uniforms, FogMode fogMode, CallbackInfo ci) {
        IrisLobbyUniforms.register(uniforms);
    }
}
