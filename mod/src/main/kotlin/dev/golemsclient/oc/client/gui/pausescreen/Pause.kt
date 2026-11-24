// Golem's Client: hooks OneClient's lobby shares with OneClient's pause screen. Golem's Client has its own pause menu,
// so these are idle for now.
package dev.golemsclient.oc.client.gui.pausescreen

import net.minecraft.client.gui.screens.Screen

/** OneClient's pause screen; Golem's Client's is dev.golemsclient.menu.PauseMenu. */
typealias OneClientPauseScreen = dev.golemsclient.menu.PauseMenu

object PauseEvergreenMedia {
    fun supported(): Boolean = false
    fun acquire() {}
    fun release() {}
    fun playing(): Boolean = false
}

@Suppress("unused")
private fun unusedScreen(screen: Screen?) = screen
