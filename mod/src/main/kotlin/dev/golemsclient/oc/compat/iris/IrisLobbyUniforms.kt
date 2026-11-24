// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.compat.iris

import java.util.function.Supplier
import net.irisshaders.iris.gl.uniform.UniformHolder
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency
import dev.golemsclient.oc.client.lobby.LOBBY_LOGGER
import org.joml.Vector4f
import dev.golemsclient.oc.client.lobby.LobbyWaveState

fun interface IrisBuildFlag {
    fun building(): Boolean
}

object IrisLobbyUniforms {
    const val WAVE = "golemsclient_LobbyWave"

    private val ZERO = Vector4f(0f, 0f, 0f, 0f)
    private val wave = Supplier<Vector4f> { if (IrisLobbyReveal.ready()) LobbyWaveState.vector() else ZERO }

    @JvmStatic
    @Volatile
    var buildFlag: IrisBuildFlag = IrisBuildFlag { false }

    @Volatile
    private var registered = 0

    @Volatile
    private var failed = false

    @JvmStatic
    fun available(): Boolean = registered > 0 && !failed

    @JvmStatic
    fun register(uniforms: UniformHolder) {
        if (failed || !buildFlag.building()) return
        runCatching { uniforms.uniform4f(UniformUpdateFrequency.PER_FRAME, WAVE, wave) }
            .onSuccess { registered++ }
            .onFailure {
                failed = true
                LOBBY_LOGGER.warn("Lobby wave uniform registration failed, WAVE_UNIFORM missing", it)
            }
    }
}
