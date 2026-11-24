// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

enum class LobbyMusic(private val label: String) {
    MATCH_SCENE("Match scene"),
    DAY("Day"),
    NIGHT("Night"),
    SUNSET("Sunset"),
    AURORA("Aurora"),
    HOME("Home"),
    CAVE("Cave"),
    NETHER("Nether"),
    THE_END("The End"),
    MINECRAFT("Minecraft music"),
    OFF("Off");

    override fun toString(): String = label
}
