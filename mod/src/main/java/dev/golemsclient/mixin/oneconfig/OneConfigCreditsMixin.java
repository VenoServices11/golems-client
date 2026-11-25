package dev.golemsclient.mixin.oneconfig;

import dev.golemsclient.oneconfig.OneConfigBrand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The menu's built-in pages, without OneConfig's Credits page (the credit stays in the source and licence). */
@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.navigation.BuiltinPages", remap = false)
public class OneConfigCreditsMixin {
    @Inject(method = "register()V", at = @At("RETURN"), remap = false)
    private static void golemsclient$noCredits(CallbackInfo ci) {
        OneConfigBrand.hideCredits();
    }
}
