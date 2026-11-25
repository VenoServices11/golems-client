package dev.golemsclient.mixin.oneconfig;

import dev.golemsclient.oneconfig.OneConfigBrand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The name next to the version at the bottom of the menu's sidebar. */
@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.shell.ShellSession", remap = false)
public class OneConfigVersionMixin {
    @ModifyVariable(method = "updateVersionLabel", at = @At("HEAD"), argsOnly = true, remap = false)
    private String golemsclient$ourName(String label) {
        return OneConfigBrand.text(label);
    }
}
