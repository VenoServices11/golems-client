// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import net.minecraft.client.Minecraft
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import org.joml.Quaternionf
import net.minecraft.client.renderer.state.CameraRenderState

import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

object LobbyHologram {
    const val STEPS = 1023
    private const val SEED_BITS = 10
    private const val SEED_MASK = 15
    private const val PREPASS = 1 shl 14
    private const val ACCENT_SHIFT = 16

    @JvmField
    var drawing = false

    @JvmField
    var extracting = false

    @Volatile
    var accent = 0x1A7CFF

    private fun red() = (accent shr 16) and 0xFF

    private fun green() = (accent shr 8) and 0xFF

    private fun blue() = accent and 0xFF

    private fun coded(avatar: LobbyAvatar, prepass: Boolean): Int {
        val low = (avatar.projection * STEPS).roundToInt().coerceIn(0, STEPS) or (seed(avatar) shl SEED_BITS) or if (prepass) PREPASS else 0
        val tint = (red() shr 3 shl 10) or (green() shr 3 shl 5) or (blue() shr 3)
        return low or (tint shl ACCENT_SHIFT)
    }

    private fun seed(avatar: LobbyAvatar) = avatar.uuid.hashCode() and SEED_MASK

    fun reset() {
        drawing = false
        extracting = false
        figures.clear()
    }

    private class Figure(val avatar: LobbyAvatar, val state: EntityRenderState)

    private val figures = ArrayList<Figure>()

    fun extract(avatars: List<LobbyAvatar>, partialTicks: Float) {
        figures.clear()
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        extracting = true
        try {
            for (avatar in avatars) {
                if (!avatar.projecting) continue
                figures += Figure(avatar, dispatcher.extractEntity(avatar, partialTicks).also { it.shadowPieces.clear() })
            }
        } finally {
            extracting = false
        }
    }

    private val camera by lazy {
        CameraRenderState().apply {
            orientation = Quaternionf()
            pos = Vec3.ZERO
            entityPos = Vec3.ZERO
            
        }
    }

    private fun figure(mc: Minecraft, figure: Figure, prepass: Boolean) {
        figure.state.lightCoords = coded(figure.avatar, prepass)
        val features = mc.gameRenderer.featureRenderDispatcher
        mc.entityRenderDispatcher.submit(figure.state, camera, 0.0, 0.0, 0.0, PoseStack(), features.submitNodeStorage)
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
        
    }


    fun draw(renderer: GameRenderer) {
        val mc = Minecraft.getInstance()
        if (figures.isEmpty()) return
        val visible = figures
        val origin = LobbyCompat.cameraPosition(renderer)
        val view = Matrix4f().rotation(LobbyCompat.camera(renderer).rotation().conjugate(Quaternionf()))
        val stack = RenderSystem.getModelViewStack()
        drawing = true
        try {
            for (figure in visible) {
                val feet = figure.avatar.position().subtract(origin)
                stack.pushMatrix()
                stack.mul(view)
                stack.translate(feet.x.toFloat(), feet.y.toFloat(), feet.z.toFloat())
                try {
                    figure(mc, figure, true)
                    figure(mc, figure, false)
                } finally {
                    stack.popMatrix()
                }
            }
        } finally {
            drawing = false
        }
    }

}
