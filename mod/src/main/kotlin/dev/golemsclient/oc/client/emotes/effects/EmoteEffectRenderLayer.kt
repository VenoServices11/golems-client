// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.emotes.effects

import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import dev.golemsclient.oc.client.emotes.playback.EmoteController
import dev.golemsclient.oc.client.render.PolyPlayerModel as PlayerModel
import dev.golemsclient.oc.client.render.PoseStack

import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState

import dev.golemsclient.oc.client.cosmetics.access.AvatarEmoteRenderAccess




class EmoteEffectRenderLayer(renderer: RenderLayerParent<AvatarRenderState, PlayerModel>) : RenderLayer<AvatarRenderState, PlayerModel>(renderer) {
    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        val snapshot = resolveController(state)?.playbackSnapshot() ?: return
        if (snapshot.emote.effects.isEmpty()) return
        EmoteEffectRenderer.submit(poseStack, submitNodeCollector, lightCoords, state, parentModel, snapshot)
    }
}

private fun resolveController(state: Any): EmoteController? {
    if (state !is AvatarEmoteRenderAccess) return null
    val controller = state.`golems$boundEmoteController`()
    return controller.takeIf { it.isActive }
}
