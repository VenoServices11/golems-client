// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.render

import net.minecraft.client.renderer.entity.state.HumanoidRenderState



data class PlayerRenderContext(
    val ageInTicks: Float,
    val walkAnimationSpeed: Float,
    val isCrouching: Boolean,
    val isOnGround: Boolean,
    val isInWater: Boolean,
    val swimAmount: Float,
    val isInvisible: Boolean,
) {
    companion object {
        fun from(state: HumanoidRenderState): PlayerRenderContext = PlayerRenderContext(
            ageInTicks = state.ageInTicks,
            walkAnimationSpeed = state.walkAnimationSpeed,
            isCrouching = state.isCrouching,
            isOnGround = state.walkAnimationSpeed > 0.01f || state.isCrouching,
            isInWater = state.isInWater,
            swimAmount = state.swimAmount,
            isInvisible = state.isInvisible,
        )
    }
}
