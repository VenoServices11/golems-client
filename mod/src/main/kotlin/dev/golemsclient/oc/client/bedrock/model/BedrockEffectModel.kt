// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.bedrock.model

import dev.golemsclient.oc.client.bedrock.geometry.BedrockGeometry
import dev.golemsclient.oc.client.bedrock.geometry.PlayerModelBone
import dev.golemsclient.oc.client.bedrock.geometry.initialEffectPosition
import dev.golemsclient.oc.client.bedrock.geometry.renderableBoneNames
import dev.golemsclient.oc.client.bedrock.geometry.topLevelBoneName
import dev.golemsclient.oc.client.bedrock.playback.BoneTransform
import dev.golemsclient.oc.client.bedrock.render.BedrockBoneRenderer

class BedrockEffectModel private constructor(
    val geometry: BedrockGeometry,
    val attachments: List<EffectAttachment>,
    private val bonesByName: Map<String, BedrockBoneRenderer>,
) {
    data class EffectAttachment(
        val attachBone: PlayerModelBone,
        val rootBoneName: String,
        val rootBone: BedrockBoneRenderer,
    )

    fun resetPose() {
        bonesByName.values.forEach(BedrockBoneRenderer::resetPose)
    }

    fun applyPose(sample: Map<String, BoneTransform>, weight: Float) {
        BedrockBoneRenderer.applyPose(bonesByName, geometry.bones.keys, sample, weight)
    }

    companion object {
        fun build(geometry: BedrockGeometry, playerGeometry: BedrockGeometry): BedrockEffectModel {
            val boneTree = BedrockBoneTreeBuilder(geometry, propagateLightLevels = true) { bone ->
                bone.initialEffectPosition(geometry.bones, playerGeometry)
            }
            val attachments = buildEffectAttachments(geometry, boneTree::buildBone)
            return BedrockEffectModel(geometry, attachments, boneTree.bones)
        }

        private fun buildEffectAttachments(
            geometry: BedrockGeometry,
            buildBone: (String) -> BedrockBoneRenderer,
        ): List<EffectAttachment> {
            val renderableBones = geometry.renderableBoneNames()

            val attachments = geometry.bones.mapNotNull { (boneName, bone) ->
                val attachBone = PlayerModelBone.fromBedrockNameOrNull(bone.parent) ?: return@mapNotNull null
                if (boneName !in renderableBones) return@mapNotNull null

                EffectAttachment(attachBone, boneName, buildBone(boneName))
            }

            if (attachments.isNotEmpty())
                return attachments

            val fallbackRoot = geometry.topLevelBoneName() ?: return emptyList()

            return listOf(
                EffectAttachment(PlayerModelBone.ROOT, fallbackRoot, buildBone(fallbackRoot)),
            )
        }
    }
}
