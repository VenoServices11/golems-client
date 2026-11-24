// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.

package dev.golemsclient.oc.client.lobby

import dev.golemsclient.oc.support.Motion
import dev.golemsclient.oc.client.PolyPlusLobbyConfig

enum class LobbyReveal(val id: String, private val label: String, private val shader: Float) {
    RANDOM("random", "Random", 0f),
    WAVE("wave", "Wave", 0f),
    FOCUS("focus", "Focus pull", 1f),
    LIGHT("light", "First light", 2f);

    val waves: Boolean get() = shader == 0f && !still()

    override fun toString(): String = label

    companion object {
        private var picked = roll(null)

        val active: LobbyReveal
            get() = LobbyScene.drivenReveal() ?: PolyPlusLobbyConfig.lobbyReveal.let { if (it == RANDOM) picked else it }

        fun byId(id: String?): LobbyReveal? = entries.firstOrNull { it != RANDOM && it.id == id }

        fun next() {
            picked = if (StartupOverlay.active) WAVE else roll(picked)
            if (PolyPlusLobbyConfig.lobbyReveal == RANDOM) LOBBY_LOGGER.info("Lobby reveal: {}", picked)
        }

        fun shaderMode(progress: Float): Float = if (progress < 1f && !still()) active.shader else 0f

        private fun still(): Boolean = Motion.reduced && LobbyScene.drivenReveal() == null

        private fun roll(last: LobbyReveal?): LobbyReveal = entries.filter { it != RANDOM && it != last }.random()
    }
}
