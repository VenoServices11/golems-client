// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import com.mojang.blaze3d.audio.Channel;
import dev.golemsclient.oc.client.lobby.LobbySoundGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class MixinChannel {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void golems$holdLobbyStart(CallbackInfo ci) {
        if (LobbySoundGate.play((Channel) (Object) this)) ci.cancel();
    }
}
