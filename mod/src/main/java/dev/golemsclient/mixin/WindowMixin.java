package dev.golemsclient.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
    /** Dev screenshots only: the finished frame, right before it's shown. */
    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void golemsclient$devShots(CallbackInfo ci) {
        if (dev.golemsclient.DevShots.ON || dev.golemsclient.SceneMaker.ON) dev.golemsclient.DevShots.afterFrame();
    }
}
