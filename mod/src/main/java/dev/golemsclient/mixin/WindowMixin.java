package dev.golemsclient.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.golemsclient.render.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
    /** Golem's Client's smooth text and shapes go on top of the finished frame, right before it's shown. */
    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void golemsclient$overlay(CallbackInfo ci) {
        Overlay.renderFrame();
    }
}
