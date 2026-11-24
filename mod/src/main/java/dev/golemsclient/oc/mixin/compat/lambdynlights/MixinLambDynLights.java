// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.lambdynlights;

import net.minecraft.client.Minecraft;
import dev.golemsclient.oc.api.lobby.LobbyApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.LambDynLights", remap = false)
public class MixinLambDynLights {
    @Inject(method = "onEndLevelTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void golems$skipLobbyTick(CallbackInfo ci) {
        if (golems$outsideWorld()) ci.cancel();
    }

    @Inject(method = "addLightSource", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void golems$skipLobbySource(CallbackInfo ci) {
        if (golems$outsideWorld()) ci.cancel();
    }

    @Inject(method = "getDynamicLightLevel", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void golems$noLobbyLight(CallbackInfoReturnable<Double> cir) {
        if (golems$outsideWorld()) cir.setReturnValue(0.0);
    }

    @Unique
    private static boolean golems$outsideWorld() {
        return LobbyApi.isRendering() || Minecraft.getInstance().level == null;
    }
}
