// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.entityculling;

import dev.golemsclient.oc.api.lobby.LobbyApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"dev.tr7zw.entityculling.NMSCullingHelper", "dev.tr7zw.entityculling.util.NMSCullingHelper"}, remap = false)
public class MixinNMSCullingHelper {
    @Inject(method = "ignoresCulling", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void golems$keepLobbyEntities(CallbackInfoReturnable<Boolean> cir) {
        if (LobbyApi.isRendering()) cir.setReturnValue(true);
    }
}
