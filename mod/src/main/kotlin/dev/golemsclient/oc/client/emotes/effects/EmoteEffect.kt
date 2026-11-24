// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.emotes.effects

import net.minecraft.resources.Identifier
import dev.golemsclient.oc.client.bedrock.model.BedrockEffectModel

data class EmoteEffect(
    val texture: Identifier,
    val model: BedrockEffectModel,
)
