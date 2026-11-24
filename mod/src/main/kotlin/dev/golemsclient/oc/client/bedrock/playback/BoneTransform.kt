// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.bedrock.playback

import org.joml.Vector3f

data class BoneTransform(
    val position: Vector3f = Vector3f(),
    val rotation: Vector3f = Vector3f(),
    val scale: Vector3f = Vector3f(1f, 1f, 1f),
)
