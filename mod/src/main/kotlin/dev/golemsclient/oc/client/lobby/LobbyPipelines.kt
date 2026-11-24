// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.shaders.UniformType

import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.resources.Identifier
import dev.golemsclient.oc.mixin.client.access.RenderPipelinesAccessor
import java.util.concurrent.ConcurrentHashMap

object LobbyPipelines {
    @Volatile
    var previewing = false

    private val failed = ConcurrentHashMap.newKeySet<RenderPipeline>()

    private fun usable(pipeline: RenderPipeline): Boolean {
        if (pipeline in failed) return false
        if (!RenderSystem.isOnRenderThread()) return true
        val result = runCatching {
            RenderSystem.getDevice().precompilePipeline(pipeline).isValid
            
        }
        if (result.getOrDefault(false)) return true
        if (failed.add(pipeline)) LOBBY_LOGGER.warn("Lobby pipeline {} failed to compile, falling back to vanilla rendering for it", pipeline.location, result.exceptionOrNull())
        return false
    }

    private fun pick(lobby: RenderPipeline, original: RenderPipeline): RenderPipeline = if (usable(lobby)) lobby else original

    fun compiled(): Boolean = terrain.all(::usable)

    private val SHADER = Identifier.fromNamespaceAndPath("golemsclient", "core/lobby_terrain")
    private val HOLOGRAM = Identifier.fromNamespaceAndPath("golemsclient", "core/lobby_hologram")

    private fun pipeline(name: String, multiDraw: Boolean, cutout: Float?, translucent: Boolean): RenderPipeline {
        val snippet = RenderPipelinesAccessor.`golems$terrainSnippet`()
        
        val builder = RenderPipeline.builder(snippet)
            .withLocation(Identifier.fromNamespaceAndPath("golemsclient", "pipeline/lobby_$name"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
        if (translucent) builder.withBlend(BlendFunction.TRANSLUCENT)
        
        cutout?.let { builder.withShaderDefine("ALPHA_CUTOUT", it) }
        return builder.build()
    }

    private val solid by lazy { pipeline("solid", false, null, false) }
    private val solidMultiDraw by lazy { pipeline("solid_multidraw", true, null, false) }
    private val cutout by lazy { pipeline("cutout", false, 0.5f, false) }
    private val cutoutMultiDraw by lazy { pipeline("cutout_multidraw", true, 0.5f, false) }
    private val translucent by lazy { pipeline("translucent", false, 0.1f, true) }
    private val translucentMultiDraw by lazy { pipeline("translucent_multidraw", true, 0.1f, true) }
    private val tripwire by lazy { pipeline("tripwire", false, 0.1f, true) }

    private val terrain by lazy {
        listOf(
            solid,
            cutout,
            translucent,
            tripwire,
        )
    }

    private val hologram by lazy {
        val builder = RenderPipeline.builder(RenderPipelinesAccessor.`golems$entitySnippet`())
            .withLocation(Identifier.fromNamespaceAndPath("golemsclient", "pipeline/lobby_hologram"))
            .withVertexShader(HOLOGRAM)
            .withFragmentShader(HOLOGRAM)
            .withCull(false)
        builder.withSampler("Sampler1")
        
        builder.withBlend(BlendFunction.TRANSLUCENT)
        
        builder.withUniform("Globals", UniformType.UNIFORM_BUFFER)
        
        builder.build()
    }

    private fun entityFormat(pipeline: RenderPipeline) = pipeline.vertexFormat === DefaultVertexFormat.NEW_ENTITY
    

    @JvmStatic
    fun hologram(original: RenderPipeline): RenderPipeline = if (LobbyHologram.drawing && entityFormat(original) && usable(hologram)) hologram else original


    @JvmStatic
    fun select(layer: ChunkSectionLayer, multiDraw: Boolean, original: RenderPipeline): RenderPipeline {
        if (!LobbyScene.terrainActive()) return original
        val lobby = when (layer) {
            ChunkSectionLayer.SOLID -> if (multiDraw) solidMultiDraw else solid
            ChunkSectionLayer.CUTOUT -> if (multiDraw) cutoutMultiDraw else cutout
            ChunkSectionLayer.TRANSLUCENT -> if (multiDraw) translucentMultiDraw else translucent
            ChunkSectionLayer.TRIPWIRE -> tripwire
        }
        return pick(lobby, original)
    }
}
