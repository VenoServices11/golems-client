// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.Overlay;
import dev.golemsclient.oc.client.lobby.StartupOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;


@Mixin(Minecraft.class)

public class MixinInitialScreens {
    @ModifyExpressionValue(method = "addInitialScreens", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;onboardAccessibility:Z"))
    private boolean golems$keepAccessibilityOnCard(boolean pending) {
        return pending;
    }

    @Inject(method = "setOverlay", at = @At("HEAD"), cancellable = true)
    private void golems$keepStartupOverlay(Overlay overlay, CallbackInfo ci) {
        if (overlay == null && StartupOverlay.holdsOverlay()) ci.cancel();
    }
}
