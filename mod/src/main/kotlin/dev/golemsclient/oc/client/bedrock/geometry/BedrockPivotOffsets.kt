// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.bedrock.geometry

import org.joml.Vector3f
import net.minecraft.util.Mth

fun bedrockPivotOffset(pivot: Vector3f, reference: Vector3f): Vector3f = Vector3f(
    pivot.x - reference.x,
    reference.y - pivot.y,
    pivot.z - reference.z,
)

fun Vector3f.bedrockRotationRadians(): Vector3f = Vector3f(this).mul(Mth.DEG_TO_RAD)

fun BedrockBone.initialEffectPosition(
    bones: Map<String, BedrockBone>,
    playerGeometry: BedrockGeometry,
): Vector3f {
    PlayerModelBone.fromBedrockNameOrNull(parent)?.let { attachBone ->
        val playerPivot = playerGeometry.bones[attachBone.serializedName]?.pivot ?: Vector3f()
        return bedrockPivotOffset(pivot, playerPivot)
    }

    bones[parent]?.let { parentBone ->
        return bedrockPivotOffset(pivot, parentBone.pivot)
    }

    return PlayerModelBone.toModelOffset(pivot)
}

fun BedrockBone.initialEffectRotation(): Vector3f = rotation.bedrockRotationRadians()
