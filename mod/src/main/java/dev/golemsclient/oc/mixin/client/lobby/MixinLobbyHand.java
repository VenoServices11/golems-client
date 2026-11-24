// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.ItemInHandRenderer;


@Mixin(ItemInHandRenderer.class)

public class MixinLobbyHand {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    
    private void golems$hideLobbyHand(CallbackInfo ci) {
        if (LobbyScene.terrainActive() || LobbyScene.ownsFrame()) ci.cancel();
    }
}
