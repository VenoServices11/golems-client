package dev.golemsclient.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import dev.golemsclient.WindowIcon;
import dev.golemsclient.render.Overlay;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Shadow
    public abstract long handle();

    /** The red G in the title bar and taskbar instead of the grass block. */
    @Inject(method = "setIcon", at = @At("HEAD"), cancellable = true)
    private void golemsclient$icon(PackResources pack, IconSet set, CallbackInfo ci) {
        if (WindowIcon.apply(handle())) ci.cancel();
    }

    /** Golem's Client's smooth text and shapes go on top of the finished frame, right before it's shown. */
    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void golemsclient$overlay(CallbackInfo ci) {
        Overlay.renderFrame();
    }
}
