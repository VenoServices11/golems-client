// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.blur;

import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "eu.midnightdust.blur.BlurRenderer", remap = false)
public class MixinBlurRenderer {
    @Inject(method = "shouldBlur", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void golems$keepOwnScreensSharp(CallbackInfoReturnable<Boolean> cir) {
        if (LobbyScene.showing()) cir.setReturnValue(false);
    }
}
