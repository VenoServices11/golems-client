// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.iris;

import com.llamalad7.mixinextras.sugar.Local;
import dev.golemsclient.oc.compat.iris.IrisLobbyReveal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class MixinIrisTransformPatcher {
    @ModifyVariable(method = "patchSodium", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false, require = 0, expect = 0)
    private static String golems$lobbyVertex(
        String vertex,
        @Local(argsOnly = true, ordinal = 0) String name,
        @Local(argsOnly = true, ordinal = 2) String geometry,
        @Local(argsOnly = true, ordinal = 3) String tessControl,
        @Local(argsOnly = true, ordinal = 4) String tessEval,
        @Local(argsOnly = true, ordinal = 5) String fragment
    ) {
        return IrisLobbyReveal.vertex(name, vertex, geometry, tessControl, tessEval, fragment);
    }

    @ModifyVariable(method = "patchSodium", at = @At("HEAD"), argsOnly = true, ordinal = 5, remap = false, require = 0, expect = 0)
    private static String golems$lobbyFragment(
        String fragment,
        @Local(argsOnly = true, ordinal = 0) String name,
        @Local(argsOnly = true, ordinal = 1) String vertex,
        @Local(argsOnly = true, ordinal = 2) String geometry,
        @Local(argsOnly = true, ordinal = 3) String tessControl,
        @Local(argsOnly = true, ordinal = 4) String tessEval
    ) {
        return IrisLobbyReveal.fragment(name, vertex, geometry, tessControl, tessEval, fragment);
    }
}
