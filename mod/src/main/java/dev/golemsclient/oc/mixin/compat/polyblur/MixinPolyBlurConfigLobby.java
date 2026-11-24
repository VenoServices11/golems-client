// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.polyblur;

import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.polyfrost.polyblur.client.PolyBlurConfig", remap = false)
public class MixinPolyBlurConfigLobby {
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void golems$sharpLobby(CallbackInfoReturnable<Boolean> cir) {
        if (LobbyScene.terrainActive() || LobbyScene.INSTANCE.showing()) cir.setReturnValue(false);
    }
}
