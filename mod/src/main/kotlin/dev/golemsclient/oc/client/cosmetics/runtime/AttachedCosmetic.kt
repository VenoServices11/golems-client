// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.cosmetics.runtime

import net.minecraft.resources.Identifier
import dev.golemsclient.oc.client.bedrock.animation.BedrockAnimation
import dev.golemsclient.oc.client.bedrock.model.BedrockEffectModel
import dev.golemsclient.oc.client.cosmetics.BodySlot

data class AttachedCosmetic(
    val id: Identifier,
    val slot: BodySlot,
    val texture: Identifier,
    val model: BedrockEffectModel,
    val animation: BedrockAnimation? = null,
    val scale: Float = 1f,
    val textureFrameCount: Int = 1,
)
