// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client: servers could tell OneClient whether its scenes may be captured there. Golem's Client
// doesn't speak that protocol yet, so every server is "unset" and your own per-server choice decides.
package dev.golemsclient.oc.client.serverapi

object ServerApiProtocol {
    enum class ScenePolicy {
        UNSET,
        ALLOW,
        DENY;

        companion object {
            fun fromWire(value: Int): ScenePolicy = entries.getOrElse(value) { UNSET }
        }
    }
}

object OneClientServerApi {
    val isConnected: Boolean get() = false
    val scenePolicy: ServerApiProtocol.ScenePolicy get() = ServerApiProtocol.ScenePolicy.UNSET
}
