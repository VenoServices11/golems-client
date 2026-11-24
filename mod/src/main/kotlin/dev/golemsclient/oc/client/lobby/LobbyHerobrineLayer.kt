// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.entity.layers.EyesLayer
import dev.golemsclient.oc.client.render.PolyPlayerModel as PlayerModel
import dev.golemsclient.oc.client.render.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes

class LobbyHerobrineLayer(renderer: RenderLayerParent<AvatarRenderState, PlayerModel>) : EyesLayer<AvatarRenderState, PlayerModel>(renderer) {
    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (!LobbyHerobrine.shows(state.id)) return
        val body = LobbyHerobrine.bodyAlpha()
        val skin = LobbyHerobrine.skinTexture()
        if (state.isInvisible && body > 0f && skin != null) {
            submitNodeCollector.submitModel(parentModel, state, poseStack, translucent(skin), lightCoords, OverlayTexture.NO_OVERLAY, tint(body), null, state.outlineColor, null)
            
        }
        val eyes = LobbyHerobrine.eyesAlpha()
        if (eyes <= 0f) return
        submitNodeCollector.order(1).submitModel(parentModel, state, poseStack, renderType(), lightCoords, OverlayTexture.NO_OVERLAY, glow(eyes), null, state.outlineColor, null)
        
    }


    private fun tint(alpha: Float): Int = (channel(alpha) shl 24) or 0xFFFFFF

    private fun glow(alpha: Float): Int {
        val level = channel(alpha)
        return (0xFF shl 24) or (level shl 16) or (level shl 8) or level
    }

    private fun channel(alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 255f).toInt()

    private fun translucent(texture: Identifier): RenderType =
        RenderTypes.entityTranslucent(texture)

    override fun renderType(): RenderType = EYES

    private companion object {
        const val FULL_BRIGHT = 0xF000F0

        val EYES: RenderType by lazy { RenderTypes.eyes(LobbyHerobrine.EYES) }
    }
}
