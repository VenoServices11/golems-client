package dev.golemsclient.mixin.oneconfig;

import dev.golemsclient.oneconfig.OneConfigBrand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** OneConfig's default accent colour is Golem's Client red instead of its blue. */
@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ThemeConfig", remap = false)
public class OneConfigAccentMixin {
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 0xFF2B4BFF), remap = false)
    private static int golemsclient$red(int blue) {
        return OneConfigBrand.GOLEMS_RED;
    }
}
