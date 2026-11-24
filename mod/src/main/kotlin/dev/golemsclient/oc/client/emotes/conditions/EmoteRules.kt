// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.emotes.conditions

data class EmoteRules(
    val allowWalking: Boolean = false,
    val allowSprinting: Boolean = false,
    val allowCrouching: Boolean = false,
    val allowFalling: Boolean = true,
    val allowElytraFlying: Boolean = false,
    val allowSwimming: Boolean = false,
    val allowVanillaPose: Boolean = false,
) {
    companion object {
        val DEFAULT = EmoteRules()
    }
}
