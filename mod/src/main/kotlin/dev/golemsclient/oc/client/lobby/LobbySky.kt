// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.buffers.GpuBuffer

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer

import com.mojang.blaze3d.resource.GraphicsResourceAllocator
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth

import net.minecraft.world.attribute.EnvironmentAttribute
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import dev.golemsclient.oc.client.PolyPlusLobbyConfig
import dev.golemsclient.oc.client.lobby.shader.DepthModel
import dev.golemsclient.oc.client.lobby.shader.LobbyShaderBridge
import dev.golemsclient.oc.client.lobby.shader.LobbyShaderBridges
import dev.golemsclient.oc.mixin.client.access.PostChainAccessor
import dev.golemsclient.oc.mixin.client.access.PostPassAccessor
import kotlin.math.tan

object LobbySky {
    val POST_EFFECT: Identifier = Identifier.fromNamespaceAndPath("golemsclient", "lobby")

    @JvmStatic
    fun ownsPostEffect(id: Identifier): Boolean = id == POST_EFFECT

    private const val BLOCK = "LobbySky"
    private const val GLASS_BLOCK = "LobbyGlass"
    private const val BYTES = 8 * 4 * Float.SIZE_BYTES

    var mood = LobbyMood.NEUTRAL

    private val NIGHT_LIGHT = Vector3f(0.5f, 0.66f, 0.86f)
    private val NIGHT_SKY = Vector3f(0.015f, 0.03f, 0.09f)
    private val NIGHT_FOG = Vector3f(0.05f, 0.09f, 0.17f)
    private val DAY_HAZE = Vector3f(0.52f, 0.7f, 0.98f)
    private const val MOONLIGHT = 0.34f
    private const val DEPTH_OF_FIELD = 0.9f
    private const val PACK_DEPTH_OF_FIELD = 0.35f
    private const val INTERIOR_DEPTH_OF_FIELD = 0.45f
    private const val DEPTH_FORWARD = 1
    private const val DEPTH_REVERSE = 2
    private const val DEPTH_UNKNOWN = 3
    private const val RING_IN_POST = 1
    private const val RING_MASKED = 2
    private const val RING_SHIFT = 4
    private const val NEAR = 0.05f
    private val NATIVE_DEPTH = DepthModel.FORWARD
    
    private val data = MemoryUtil.memAlloc(BYTES)
    private val glass = MemoryUtil.memAlloc(LobbyGlass.BLOCK_BYTES)
    private val fogSeen = Vector3f(1f, 1f, 1f)

    private val WEAK_GPUS = listOf("llvmpipe", "softpipe", "software", "mesa x11", "swiftshader", "microsoft basic render", "gl4es", "mali", "adreno", "powervr", "tegra", "v3d")

    private val STRONG_INTEL = listOf("arc", "(dg2", "(bmg", "(lnl")

    private val reducedEffects: Boolean by lazy {
        val gpu = runCatching { gpuName() }.getOrNull().orEmpty().lowercase()
        val weak = ("intel" in gpu && STRONG_INTEL.none { it in gpu }) || WEAK_GPUS.any { it in gpu }
        if (weak) LOBBY_LOGGER.info("Using reduced lobby effects on {}", gpu)
        weak
    }

    private fun gpuName(): String = RenderSystem.getDevice().renderer
    

    private val untinted: Boolean get() = mood.interior || mood.realm != LobbyRealm.OVERWORLD

    private fun skyLight(color: Vector3f): Vector3f = if (untinted) color else color.lerp(NIGHT_LIGHT, 0.34f * mood.night)

    private fun sky(color: Vector3f): Vector3f = if (untinted) color else color.add(Vector3f(NIGHT_SKY).mul(mood.night))

    private fun fog(color: Vector3f): Vector3f =
        if (untinted) color.also { fogSeen.set(it) } else color.lerp(DAY_HAZE, 0.45f * (1f - mood.night) * (1f - mood.golden)).lerp(NIGHT_FOG, 0.7f * mood.night).also { fogSeen.set(it) }

    @JvmStatic
    fun skyLightFactor(value: Float): Float = value.coerceAtLeast(MOONLIGHT * mood.night)

    @JvmStatic
    fun adjust(attribute: EnvironmentAttribute<*>, value: Any?): Any? = when {
        attribute === EnvironmentAttributes.SKY_LIGHT_COLOR -> tint(value, ::skyLight)
        attribute === EnvironmentAttributes.SKY_LIGHT_FACTOR -> skyLightFactor(value as Float)
        attribute === EnvironmentAttributes.SKY_COLOR -> tint(value, ::sky)
        attribute === EnvironmentAttributes.FOG_COLOR -> tint(value, ::fog)
        else -> value
    }



    private inline fun tint(value: Any?, change: (Vector3f) -> Vector3f): Any? {
        val argb = value as Int
        val color = change(Vector3f((argb shr 16 and 0xFF) / 255f, (argb shr 8 and 0xFF) / 255f, (argb and 0xFF) / 255f))
        return (argb and -0x1000000) or (channel(color.x) shl 16) or (channel(color.y) shl 8) or channel(color.z)
    }

    private fun channel(value: Float): Int = Mth.floor(value.coerceIn(0f, 1f) * 255f)
    

    fun upload(mc: Minecraft, fov: Float, forward: Vec3, right: Vec3, up: Vec3, seconds: Float, focus: Float, mood: LobbyMood) {
        val tanHalf = tan(Math.toRadians(fov / 2.0)).toFloat()
        val aspect = mc.window.width.toFloat() / mc.window.height.coerceAtLeast(1)
        data.clear()
        put(forward, tanHalf)
        put(right, aspect)
        put(up, seconds)
        val depthFar = maxOf(mc.options.effectiveRenderDistance * 64f, mc.options.cloudRange().get() * 16f)
        val bridge = LobbyShaderBridges.current
        val pack = bridge.packActive()
        val model = if (pack) bridge.depthModel() else NATIVE_DEPTH
        val quality = if (reducedEffects) 1f else 0f
        val vignette = if (PolyPlusLobbyConfig.lobbyVignette) 1f else 0f
        if (model == NATIVE_DEPTH || model == DepthModel.UNKNOWN) {
            data.putFloat(vignette).putFloat(quality).putFloat(NEAR).putFloat(depthFar)
        } else {
            data.putFloat(vignette).putFloat(quality).putFloat(depthFar).putFloat(NEAR)
        }
        data.putFloat(mood.aurora).putFloat(mood.night).putFloat(mood.exposure).putFloat(mood.golden)
        val lens = if (pack && bridge.packDepthOfField()) DEPTH_OF_FIELD * PACK_DEPTH_OF_FIELD else DEPTH_OF_FIELD
        val depthOfField = if (mood.interior) lens * INTERIOR_DEPTH_OF_FIELD else lens
        data.putFloat(focus).putFloat(depthOfField).putFloat(LobbyReveal.shaderMode(LobbyScene.introProgress())).putFloat(LobbyScene.introProgress())
        val wave = LobbyWaveState.vector()
        data.putFloat(wave.x).putFloat(wave.y).putFloat(wave.z).putFloat(wave.w)
        if (pack) {
            data.putFloat(fogSeen.x).putFloat(fogSeen.y).putFloat(fogSeen.z).putFloat(flags(bridge, model))
        } else {
            data.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f)
        }
        data.flip()
        val chain = mc.shaderManager.getPostChain(POST_EFFECT, LevelTargetBundle.MAIN_TARGETS) ?: return
        val device = RenderSystem.getDevice()
        for (pass in (chain as PostChainAccessor).`golems$passes`()) {
            val uniforms = (pass as PostPassAccessor).`golems$customUniforms`()
            var buffer = uniforms[BLOCK] ?: continue
            if (buffer.usage() and GpuBuffer.USAGE_COPY_DST == 0) {
                buffer.close()
                buffer = device.createBuffer({ "golemsclient lobby sky" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, bufferSize(BYTES))
                uniforms[BLOCK] = buffer
            }
            device.createCommandEncoder().writeToBuffer(buffer.slice(), data)
            data.rewind()
        }
        uploadGlass(chain as PostChainAccessor)
    }

    private val glassSent = MemoryUtil.memAlloc(LobbyGlass.BLOCK_BYTES)
    private val glassTargets = ArrayList<GpuBuffer>()

    private fun uploadGlass(chain: PostChainAccessor) {
        val device = RenderSystem.getDevice()
        glass.clear()
        LobbyGlass.write(glass)
        glass.flip()
        glassSent.clear()
        val changed = glass != glassSent
        if (changed) {
            glassSent.put(glass)
            glass.rewind()
        }
        var index = 0
        for (pass in chain.`golems$passes`()) {
            val uniforms = (pass as PostPassAccessor).`golems$customUniforms`()
            var buffer = uniforms[GLASS_BLOCK] ?: continue
            if (buffer.size() != bufferSize(LobbyGlass.BLOCK_BYTES)) {
                buffer.close()
                buffer = device.createBuffer({ "golemsclient lobby glass" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, bufferSize(LobbyGlass.BLOCK_BYTES))
                uniforms[GLASS_BLOCK] = buffer
            }
            val known = index < glassTargets.size && glassTargets[index] === buffer
            if (changed || !known) {
                device.createCommandEncoder().writeToBuffer(buffer.slice(), glass)
                glass.rewind()
                if (index < glassTargets.size) glassTargets[index] = buffer else glassTargets += buffer
            }
            index++
        }
        while (glassTargets.size > index) glassTargets.removeAt(glassTargets.size - 1)
    }

    fun process(renderer: GameRenderer, resources: GraphicsResourceAllocator) {
        val chain = Minecraft.getInstance().shaderManager.getPostChain(POST_EFFECT, LevelTargetBundle.MAIN_TARGETS) ?: return
        val target = LobbyCompat.mainTarget(renderer)
        // VulkanMod on 26.1 leaves the main target without a depth view, which the chain's depth inputs require
        if (target.depthTextureView == null) return
        chain.process(target, resources)
    }
    




    private fun bufferSize(bytes: Int): Long = bytes.toLong()

    private fun flags(bridge: LobbyShaderBridge, model: DepthModel): Float {
        val depth = when (model) {
            DepthModel.FORWARD -> DEPTH_FORWARD
            DepthModel.REVERSE -> DEPTH_REVERSE
            DepthModel.UNKNOWN -> return DEPTH_UNKNOWN.toFloat()
        }
        val ring = when {
            !bridge.revealInTerrain() -> RING_MASKED
            bridge.ringInPost() -> RING_IN_POST
            else -> 0
        }
        return (depth + ring * RING_SHIFT).toFloat()
    }

    private fun put(vector: Vec3, w: Float) {
        data.putFloat(vector.x.toFloat()).putFloat(vector.y.toFloat()).putFloat(vector.z.toFloat()).putFloat(w)
    }
}
