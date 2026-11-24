package dev.golemsclient.mixin;

import dev.golemsclient.menu.MainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Whenever the game opens its main menu, Golem's Client's main menu opens instead. */
@Mixin(Minecraft.class)
public abstract class TitleScreenSwapMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen golemsclient$mainMenu(Screen screen) {
        if (!MainMenu.enabled()) return screen;
        if (screen instanceof TitleScreen) return new MainMenu();
        // closing a screen with no world behind it goes back to the main menu: ours, not the vanilla one
        if (screen == null && ((Minecraft) (Object) this).level == null) return new MainMenu();
        return screen;
    }
}
