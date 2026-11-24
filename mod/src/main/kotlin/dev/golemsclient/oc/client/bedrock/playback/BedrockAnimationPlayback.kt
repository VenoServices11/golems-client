// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.bedrock.playback

import dev.golemsclient.oc.client.bedrock.BedrockConstants
import dev.golemsclient.oc.client.bedrock.animation.BedrockAnimation
import dev.golemsclient.oc.client.bedrock.animation.LoopMode
import dev.golemsclient.oc.client.render.PlayerRenderContext
import kotlin.math.min

import net.minecraft.util.Util



object BedrockAnimationPlayback {
    fun resolveTimeTicks(
        animation: BedrockAnimation,
        elapsedTicks: Float,
        clampOnce: Boolean = false,
    ): Float {
        return when (animation.loop) {
            LoopMode.LOOP -> elapsedTicks % animation.lengthTicks
            LoopMode.HOLD_LAST -> min(elapsedTicks, animation.lengthTicks)
            LoopMode.ONCE -> if (clampOnce) min(elapsedTicks, animation.lengthTicks) else elapsedTicks
        }
    }

    fun elapsedTicksSince(startTimeMs: Long): Float {
        return (Util.getMillis() - startTimeMs) / BedrockConstants.MS_PER_TICK
    }

    fun sampleTimed(
        animation: BedrockAnimation,
        startTimeMs: Long,
        renderContext: PlayerRenderContext?,
        clampOnce: Boolean = false,
        molangVariables: MutableMap<String, Float> = mutableMapOf(),
    ): Map<String, BoneTransform> {
        AnimationSampler.runInitialize(animation, molangVariables)
        val timeTicks = resolveTimeTicks(
            animation = animation,
            elapsedTicks = elapsedTicksSince(startTimeMs),
            clampOnce = clampOnce,
        )
        return AnimationSampler.sample(
            animation = animation,
            timeTicks = timeTicks,
            renderContext = renderContext,
            molangVariables = molangVariables,
        )
    }
}
