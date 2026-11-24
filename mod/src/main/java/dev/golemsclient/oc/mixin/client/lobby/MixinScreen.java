// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.screens.Screen;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {
    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void golems$keepLobbyBackdrop(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (LobbyScene.showing()) ci.cancel();
    }
    
}
