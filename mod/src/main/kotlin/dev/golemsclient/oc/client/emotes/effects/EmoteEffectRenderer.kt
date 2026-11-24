// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.emotes.effects

import net.minecraft.client.renderer.entity.LivingEntityRenderer
import dev.golemsclient.oc.client.bedrock.render.BedrockAttachedModelRenderer
import dev.golemsclient.oc.client.emotes.playback.EmotePlaybackSnapshot
import dev.golemsclient.oc.client.render.PolyPlayerModel as PlayerModel
import dev.golemsclient.oc.client.render.PoseStack


import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState

import dev.golemsclient.oc.client.render.PlayerRenderContext




object EmoteEffectRenderer {
    fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        playerModel: PlayerModel,
        snapshot: EmotePlaybackSnapshot,
    ) {
        val renderContext = PlayerRenderContext.from(state)
        if (renderContext.isInvisible || snapshot.playerWeight <= 0f) return
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        BedrockAttachedModelRenderer.submit(poseStack, submitNodeCollector, lightCoords, overlay, playerModel, draws(snapshot))
    }

    private fun draws(snapshot: EmotePlaybackSnapshot): List<BedrockAttachedModelRenderer.DrawCall> =
        snapshot.emote.effects.map { effect ->
            BedrockAttachedModelRenderer.DrawCall(
                model = effect.model,
                texture = effect.texture,
                sample = snapshot.sample,
                poseWeight = snapshot.effectPoseWeight,
            )
        }
}
