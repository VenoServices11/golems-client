// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.emotes

import net.minecraft.resources.Identifier
import dev.golemsclient.oc.client.bedrock.animation.BedrockAnimation
import dev.golemsclient.oc.client.emotes.conditions.EmoteRules
import dev.golemsclient.oc.client.emotes.effects.EmoteEffect

data class Emote(
    val id: Identifier,
    val animation: BedrockAnimation,
    val effects: List<EmoteEffect> = emptyList(),
    val rules: EmoteRules = EmoteRules.DEFAULT,
)
