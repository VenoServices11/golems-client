// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.evergreenhud;

import dev.golemsclient.menu.MainMenu;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import dev.golemsclient.oc.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.media.MediaHud", remap = false)
public class MixinEvergreenMediaHud {
    @Inject(method = "click", at = @At("HEAD"), cancellable = true, require = 0)
    private void golems$ignoreClicksBehindMenu(CallbackInfo ci) {
        if (LobbyScene.showing() || ClientPlatform.INSTANCE.currentScreen() instanceof MainMenu) ci.cancel();
    }
}
