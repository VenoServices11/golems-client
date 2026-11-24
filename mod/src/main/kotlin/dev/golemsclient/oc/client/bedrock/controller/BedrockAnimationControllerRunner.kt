// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.bedrock.controller

import org.joml.Vector3f
import dev.golemsclient.oc.client.bedrock.BedrockConstants
import dev.golemsclient.oc.client.bedrock.animation.BedrockAnimation
import dev.golemsclient.oc.client.bedrock.molang.MolangContext
import dev.golemsclient.oc.client.bedrock.molang.MolangEvaluator
import dev.golemsclient.oc.client.bedrock.playback.AnimationSampler
import dev.golemsclient.oc.client.bedrock.playback.BedrockAnimationPlayback
import dev.golemsclient.oc.client.bedrock.playback.BoneTransform
import dev.golemsclient.oc.client.render.PlayerRenderContext

class BedrockAnimationControllerRunner(
    private val controller: BedrockAnimationController,
    private val animations: Map<String, BedrockAnimation>,
) {
    var currentState: String = controller.initialState
        private set

    private var timeInStateSeconds = 0f

    fun sample(
        deltaSeconds: Float,
        molangVariables: MutableMap<String, Float>,
        renderContext: PlayerRenderContext? = null,
    ): Map<String, BoneTransform> {
        timeInStateSeconds += deltaSeconds
        evaluateTransitions(molangVariables, renderContext)

        val state = controller.states[currentState] ?: return emptyMap()
        val context = MolangContext.forAnimation(
            animTimeSeconds = timeInStateSeconds,
            renderContext = renderContext,
            variables = molangVariables,
        )

        var result: Map<String, BoneTransform> = emptyMap()
        for (stateAnimation in state.animations) {
            val animation = animations[stateAnimation.animationName] ?: continue
            val weight = MolangEvaluator.eval(stateAnimation.weight, context).toFloat().coerceIn(0f, 1f)
            if (weight <= 0f) continue

            val elapsedTicks = timeInStateSeconds * BedrockConstants.TICKS_PER_SECOND
            val timeTicks = BedrockAnimationPlayback.resolveTimeTicks(animation, elapsedTicks)
            val sample = AnimationSampler.sample(
                animation = animation,
                timeTicks = timeTicks,
                renderContext = renderContext,
                molangVariables = molangVariables,
            )
            result = blend(result, sample, weight)
        }

        return result
    }

    private fun evaluateTransitions(molangVariables: MutableMap<String, Float>, renderContext: PlayerRenderContext?) {
        val state = controller.states[currentState] ?: return
        if (state.transitions.isEmpty()) return

        val context = MolangContext.forAnimation(
            animTimeSeconds = timeInStateSeconds,
            renderContext = renderContext,
            variables = molangVariables,
        )

        for (transition in state.transitions) {
            val target = controller.states[transition.targetState] ?: continue
            if (MolangEvaluator.eval(transition.condition, context) == 0.0) continue

            MolangEvaluator.execute(state.onExit, context)
            currentState = transition.targetState
            timeInStateSeconds = 0f

            val entryContext = MolangContext.forAnimation(
                animTimeSeconds = 0f,
                renderContext = renderContext,
                variables = molangVariables,
            )
            MolangEvaluator.execute(target.onEntry, entryContext)
            return
        }
    }

    private fun blend(base: Map<String, BoneTransform>, add: Map<String, BoneTransform>, weight: Float): Map<String, BoneTransform> {
        if (base.isEmpty()) {
            return if (weight >= 1f) add else add.mapValues { (_, transform) -> lerp(BoneTransform(), transform, weight) }
        }

        val result = LinkedHashMap<String, BoneTransform>(base)
        for ((bone, addTransform) in add) {
            result[bone] = lerp(base[bone] ?: BoneTransform(), addTransform, weight)
        }
        return result
    }

    private fun lerp(from: BoneTransform, to: BoneTransform, weight: Float): BoneTransform = BoneTransform(
        position = Vector3f(from.position).lerp(to.position, weight),
        rotation = Vector3f(from.rotation).lerp(to.rotation, weight),
        scale = Vector3f(from.scale).lerp(to.scale, weight),
    )
}
