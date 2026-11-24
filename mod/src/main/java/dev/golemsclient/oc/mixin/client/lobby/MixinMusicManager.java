// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import net.minecraft.client.sounds.MusicManager;
import dev.golemsclient.oc.client.lobby.LobbyAmbience;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class MixinMusicManager {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void golems$holdForLobbyAmbience(CallbackInfo ci) {
        if (LobbyAmbience.holdsMusic()) ci.cancel();
    }
}
