package dev.golemsclient.mixin;

import dev.golemsclient.menu.MainMenu;
import dev.golemsclient.menu.PauseMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Golem's Client's screens instead of Minecraft's: the main menu (with the lobby behind it) and the pause menu. */
@Mixin(Minecraft.class)
public abstract class TitleScreenSwapMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen golemsclient$screens(Screen screen) {
        if (!MainMenu.enabled()) return screen;
        Minecraft mc = (Minecraft) (Object) this;

        // the main menu (OneClient's lobby draws itself behind it, without loading a world)
        if (screen instanceof TitleScreen || (screen == null && mc.level == null)) return new MainMenu();
        // the pause menu (not F3+Esc's pause without a menu, and not other mods' screens built on it)
        if (screen != null && screen.getClass() == PauseScreen.class && ((PauseScreen) screen).showsPauseMenu()) {
            return new PauseMenu();
        }
        return screen;
    }
}
