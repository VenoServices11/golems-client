// Golem's Client: what OneClient's pause screen code needs from Golem's Client's own pause menu
// (dev.golemsclient.menu.PauseMenu, laid out like OneClient's).
package dev.golemsclient.oc.client.gui.pausescreen

/** OneClient's pause screen; Golem's Client's is dev.golemsclient.menu.PauseMenu. */
typealias OneClientPauseScreen = dev.golemsclient.menu.PauseMenu

/** The pause menu's settings OneClient's Step back camera reads. */
object PauseStyles {
    /** Pan the camera even with reduced motion (OneClient's "Always pan camera"). */
    @JvmStatic
    val alwaysPan: Boolean get() = dev.golemsclient.menu.MenuData.get().pauseAlwaysPan
}

object PauseEvergreenMedia {
    fun supported(): Boolean = false
    fun acquire() {}
    fun release() {}
    fun playing(): Boolean = false
}

/** What Golem's Client's pause menu (Java) needs from OneClient's Step back camera. */
object PauseBridge {
    /** The camera glides round to your player. False when there's no room (or you're riding, swimming...). */
    @JvmStatic
    fun enter(): Boolean = PauseStage.enter()

    /** The camera glides back (switching to Flat). */
    @JvmStatic
    fun leave() = PauseStage.onScreen(null)

    /** Where the mouse is, -1 to 1 across the window: the camera sways a little with it. */
    @JvmStatic
    fun pointer(x: Float, y: Float) = PauseStage.pointer(x, y)

    /** Where the tag over your head goes, 0 to 1 across the window. */
    @JvmStatic
    fun tagX(): Float = PauseStage.tagX

    @JvmStatic
    fun tagY(): Float = PauseStage.tagY

    /** How far your player has turned to the camera (0 to 1): the tag fades in with it. */
    @JvmStatic
    fun weight(): Float = PauseStage.avatarWeight
}
