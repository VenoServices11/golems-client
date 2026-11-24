// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

enum class LobbyRealm {
    OVERWORLD,
    NETHER,
    END;

    companion object {
        fun of(dimension: String): LobbyRealm = when (dimension.substringAfter(':').lowercase()) {
            "the_nether", "-1" -> NETHER
            "the_end", "1" -> END
            else -> OVERWORLD
        }
    }
}
