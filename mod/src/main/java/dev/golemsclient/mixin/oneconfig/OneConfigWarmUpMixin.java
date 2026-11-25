package dev.golemsclient.mixin.oneconfig;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golemsclient.menu.MainMenu;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * OneConfig gets its menu ready in the background while you're on the main menu, so it opens instantly later. It only
 * knew Minecraft's title screen (and OneClient's); Golem's Client's main menu counts too, or the first open is slow.
 */
@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.MainMenuFpsSampler", remap = false)
public class OneConfigWarmUpMixin {
    @ModifyReturnValue(method = "isMainMenu(Ljava/lang/Object;)Z", at = @At("RETURN"), remap = false)
    private static boolean golemsclient$ourMainMenu(boolean original, Object screen) {
        return original || (screen instanceof MainMenu && Minecraft.getInstance().getOverlay() == null);
    }
}
