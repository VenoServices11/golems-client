package dev.golemsclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.golemsclient.menu.MainMenu;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Like OneClient: the main menu (with the lobby behind it) runs at the monitor's refresh rate plus 60, not Minecraft's
 * 60 frames a second for menus, so the scene and the opening move smoothly. Minimized, it drops to 10.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class MenuFramerateMixin {
    @ModifyReturnValue(method = "getFramerateLimit", at = @At("RETURN"))
    private int golemsclient$menuFramerate(int original) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof MainMenu) || mc.level != null) return original;
        if (mc.getWindow().isIconified()) return Math.min(original, 10);
        int refresh = mc.getWindow().getRefreshRate();
        return (refresh > 0 ? refresh : 60) + 60;
    }
}
