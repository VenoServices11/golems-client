// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.compat.iris

import dev.golemsclient.oc.client.lobby.LOBBY_LOGGER
import dev.golemsclient.oc.client.lobby.shader.LobbyShaderBridge
import dev.golemsclient.oc.mixin.PolyPlusMixinPlugin

object IrisProbe {
    private const val ROOT = "net.irisshaders.iris"
    private const val NAMESPACED_ID = "$ROOT.shaderpack.materialmap.NamespacedId"

    val pipelineSlot: Boolean by lazy { available("pipeline slot", ::probePipelineSlot) }

    val bufferGuard: Boolean by lazy { available("shader storage buffer guard", ::probeBufferGuard) }

    val lobbyPipeline: Boolean get() = pipelineSlot && bufferGuard

    fun bridge(): LobbyShaderBridge? {
        if (!PolyPlusMixinPlugin.compatEnabled("iris")) return null
        if (!available("pack query", ::probePack)) return null
        IrisLobbyPipelines.install()
        return IrisLobbyBridge
    }

    private fun probePack() {
        Class.forName("$ROOT.Iris").getMethod("getCurrentPack")
    }

    private fun probePipelineSlot() {
        val id = Class.forName(NAMESPACED_ID)
        val manager = Class.forName("$ROOT.pipeline.PipelineManager")
        manager.getMethod("preparePipeline", id)
        val programs = Class.forName("$ROOT.shaderpack.programs.ProgramSet")
        Class.forName("$ROOT.pipeline.IrisRenderingPipeline").getConstructor(programs)
        Class.forName("$ROOT.shaderpack.ShaderPack").getMethod("getProgramSet", id)
        Class.forName("$ROOT.shaderpack.DimensionId").getField("OVERWORLD")
        IrisAccess.resolvePipelineSlot()
    }

    private fun probeBufferGuard() {
        Class.forName("$ROOT.gl.buffer.ShaderStorageBufferHolder").getMethod("setupBuffers")
        IrisAccess.resolveBufferGuard()
    }

    private fun available(what: String, probe: () -> Unit): Boolean = runCatching(probe).fold(
        onSuccess = { true },
        onFailure = {
            LOBBY_LOGGER.warn("Iris {} unavailable, lobby shader bridge degrades: {}", what, it.toString())
            false
        },
    )
}
