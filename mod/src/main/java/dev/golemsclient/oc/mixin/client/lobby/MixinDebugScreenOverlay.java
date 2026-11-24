// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay {
    @Inject(method = "render3dCrosshair", at = @At("HEAD"), cancellable = true)
    private void golems$hideLobbyCrosshair(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }
}

