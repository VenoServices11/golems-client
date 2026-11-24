// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.emotes.playback

import dev.golemsclient.oc.client.bedrock.playback.BoneTransform
import dev.golemsclient.oc.client.emotes.Emote

data class EmotePlaybackSnapshot(
    val emote: Emote,
    val sample: Map<String, BoneTransform>,
    val playerWeight: Float,
    val effectPoseWeight: Float = playerWeight,
)
