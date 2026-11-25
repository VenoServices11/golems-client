package dev.golemsclient.mixin.oneconfig;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golemsclient.oneconfig.OneConfigBrand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Every word in OneConfig's menu comes through here: "OneConfig" becomes "Golem's Client". */
@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.components.I18nKt", remap = false)
public class OneConfigTextMixin {
    @ModifyReturnValue(method = {
            "translated(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            "translated(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"
    }, at = @At("RETURN"), remap = false)
    private static String golemsclient$ourName(String original) {
        return OneConfigBrand.text(original);
    }
}
