package dev.golemsclient.mixin.oneconfig;

import dev.golemsclient.oneconfig.OneConfigBrand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** OneConfig's logos are loaded here: Golem's Client's logo loads in their place. */
@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.components.IconResource", remap = false)
public class OneConfigIconMixin {
    @ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true, remap = false)
    private String golemsclient$ourLogo(String path) {
        return OneConfigBrand.svg(path);
    }
}
