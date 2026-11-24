// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client: the lobby menu's layout sizes (in design pixels of a 1600x900 screen) that the lobby
// camera uses to keep your player clear of the panels.
package dev.golemsclient.oc.client.gui.lobbyshell

class Dp(val value: Float) {
    operator fun plus(other: Dp) = Dp(value + other.value)
}

object LobbyLayout {
    const val DESIGN_WIDTH = 1600f
    const val DESIGN_HEIGHT = 900f

    val Margin = Dp(28f)
    val Gap = Dp(16f)
    val Top = Dp(72f)
    val UnitWidth = Dp(350f)
    val BarHeight = Dp(52f)
    val BarEnd = Margin + UnitWidth + Gap
    val PageBottom = Margin + BarHeight + Gap
}
