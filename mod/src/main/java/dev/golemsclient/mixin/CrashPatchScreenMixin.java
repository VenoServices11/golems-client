package dev.golemsclient.mixin;

import dev.golemsclient.menu.CrashScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When CrashPatch (part of OneClient's mods) catches a crash or a disconnect, Golem's Client's crash screen shows
 * instead of CrashPatch's, with the same information and Golem's Client's Discord. Only when CrashPatch is installed.
 */
@Pseudo
@Mixin(targets = "org.polyfrost.crashpatch.client.gui.CrashUI", remap = false)
public class CrashPatchScreenMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true, remap = false)
    private void golemsclient$ourScreen(CallbackInfoReturnable<Screen> cir) {
        Screen ours = CrashScreen.fromCrashPatch(this);
        if (ours != null) cir.setReturnValue(ours);
    }
}
