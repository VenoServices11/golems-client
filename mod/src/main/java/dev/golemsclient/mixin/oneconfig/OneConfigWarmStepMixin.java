package dev.golemsclient.mixin.oneconfig;

import dev.golemsclient.menu.PauseMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * OneConfig only gets its menu ready on the main menu, and throws away what it had done as soon as you leave it.
 * Golem's Client lets it carry on on any menu screen (server list, connecting, loading the world, the pause menu),
 * where a short hitch goes unnoticed, so the menu is ready even if you join a world straight away.
 * Never while you're playing with no screen open, or typing in chat.
 */
@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen", remap = false)
public class OneConfigWarmStepMixin {
    @ModifyVariable(method = "warmStep(Z)Z", at = @At("HEAD"), argsOnly = true, remap = false)
    private static boolean golemsclient$anyMenu(boolean allowed) {
        if (allowed) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return false;
        Screen screen = mc.screen;
        if (screen == null || screen instanceof ChatScreen) return false;
        if (screen.getClass().getName().startsWith("org.polyfrost.")) return false;
        if (mc.level == null) return true;
        return screen instanceof PauseScreen || screen instanceof PauseMenu || screen instanceof LevelLoadingScreen;
    }
}
