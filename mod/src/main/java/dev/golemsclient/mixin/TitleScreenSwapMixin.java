package dev.golemsclient.mixin;

import dev.golemsclient.lobby.Lobby;
import dev.golemsclient.menu.MainMenu;
import dev.golemsclient.menu.PauseMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Golem's Client's screens instead of Minecraft's: the main menu (with the lobby behind it), the pause menu, and the
 * startup screen while the lobby loads or is left (instead of "Loading terrain" and "Saving world").
 */
@Mixin(Minecraft.class)
public abstract class TitleScreenSwapMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen golemsclient$screens(Screen screen) {
        if (!MainMenu.enabled()) return screen;
        Minecraft mc = (Minecraft) (Object) this;

        // while the lobby is being made, or being left: its own screen instead of the loading/saving ones
        boolean lobbyBusy = Lobby.phase() == Lobby.Phase.PENDING || Lobby.phase() == Lobby.Phase.LOADING || Lobby.active();
        if (lobbyBusy && (screen instanceof GenericMessageScreen || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen)) {
            return Lobby.screen();
        }
        if (Lobby.isLeaving() && (screen instanceof GenericMessageScreen || screen instanceof ProgressScreen)) return Lobby.screen();
        // in the lobby, closing a screen goes back to the menu (or the startup screen while it reveals itself)
        if (screen == null && Lobby.active()) {
            return Lobby.phase() == Lobby.Phase.READY ? new MainMenu() : Lobby.screen();
        }
        if (screen == null && (Lobby.phase() == Lobby.Phase.PENDING || Lobby.phase() == Lobby.Phase.LOADING)) return Lobby.screen();

        if (screen instanceof TitleScreen || (screen == null && mc.level == null)) {
            if (Lobby.busy()) return Lobby.phase() == Lobby.Phase.READY && Lobby.active() ? new MainMenu() : Lobby.screen();
            return Lobby.enabled() ? Lobby.request() : new MainMenu();
        }
        // the pause menu (not F3+Esc's pause without a menu, and not other mods' screens built on it)
        if (screen != null && screen.getClass() == PauseScreen.class && ((PauseScreen) screen).showsPauseMenu()) {
            return new PauseMenu();
        }
        return screen;
    }
}
