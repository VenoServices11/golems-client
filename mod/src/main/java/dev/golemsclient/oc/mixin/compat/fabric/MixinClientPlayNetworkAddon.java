// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.fabric;

import dev.golemsclient.oc.client.lobby.LobbyCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon", remap = false)
public class MixinClientPlayNetworkAddon {
    @Inject(method = "invokeInitEvent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void golems$quietLobbyConnection(CallbackInfo ci) {
        if (LobbyCompat.quietConnection()) ci.cancel();
    }
}
