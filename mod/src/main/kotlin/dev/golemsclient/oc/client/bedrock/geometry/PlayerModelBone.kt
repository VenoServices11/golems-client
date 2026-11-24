// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.bedrock.geometry

import net.minecraft.client.model.geom.ModelPart
import org.joml.Vector3f
import dev.golemsclient.oc.client.bedrock.BedrockConstants
import dev.golemsclient.oc.client.render.PolyPlayerModel as PlayerModel
import dev.golemsclient.oc.client.render.PoseStack


enum class PlayerModelBone(val serializedName: String) {
    ROOT("root"),
    HEAD("head"),
    HELMET("helmet"),
    BODY("body"),
    RIGHT_ARM("rightArm"),
    LEFT_ARM("leftArm"),
    RIGHT_LEG("rightLeg"),
    LEFT_LEG("leftLeg");

    fun resolve(model: PlayerModel): ModelPart {
        return checkNotNull(MODEL_PART_ACCESSOR[this]) {
            "No model part mapping for $serializedName"
        }.invoke(model)
    }

    fun overlayPart(model: PlayerModel): ModelPart? = OVERLAY_ACCESSOR[this]?.invoke(model)

    fun attachmentChain(): List<PlayerModelBone> = ATTACHMENT_CHAIN[this] ?: listOf(this)

    fun translateAndRotateChain(model: PlayerModel, poseStack: PoseStack) {
        for (bone in attachmentChain()) {
            bone.resolve(model).translateAndRotate(poseStack)
        }
    }

    companion object {
        private fun normalize(name: String): String = name.lowercase().replace("_", "")

        private val BY_BEDROCK_NAME = entries.associateBy { normalize(it.serializedName) }

        private val MODEL_PART_ACCESSOR = mapOf(
            ROOT to { model: PlayerModel -> model.root() },
            HEAD to { model: PlayerModel -> model.head },
            HELMET to { model: PlayerModel -> model.hat },
            BODY to { model: PlayerModel -> model.body },
            RIGHT_ARM to { model: PlayerModel -> model.rightArm },
            LEFT_ARM to { model: PlayerModel -> model.leftArm },
            RIGHT_LEG to { model: PlayerModel -> model.rightLeg },
            LEFT_LEG to { model: PlayerModel -> model.leftLeg },
        )

        private val OVERLAY_ACCESSOR = mapOf(
            RIGHT_ARM to { model: PlayerModel -> model.rightSleeve },
            LEFT_ARM to { model: PlayerModel -> model.leftSleeve },
            RIGHT_LEG to { model: PlayerModel -> model.rightPants },
            LEFT_LEG to { model: PlayerModel -> model.leftPants },
            BODY to { model: PlayerModel -> model.jacket },
        )

        private val ATTACHMENT_CHAIN = mapOf(
            ROOT to listOf(ROOT),
            BODY to listOf(ROOT, BODY),
            HEAD to listOf(ROOT, HEAD),
            HELMET to listOf(ROOT, HEAD, HELMET),
            RIGHT_ARM to listOf(ROOT, RIGHT_ARM),
            LEFT_ARM to listOf(ROOT, LEFT_ARM),
            RIGHT_LEG to listOf(ROOT, RIGHT_LEG),
            LEFT_LEG to listOf(ROOT, LEFT_LEG),
        )

        fun fromBedrockNameOrNull(name: String): PlayerModelBone? = BY_BEDROCK_NAME[normalize(name)]

        fun toModelOffset(bedrockPivot: Vector3f): Vector3f {
            return Vector3f(
                bedrockPivot.x,
                BedrockConstants.PLAYER_MODEL_HEIGHT - bedrockPivot.y,
                bedrockPivot.z,
            )
        }
    }
}
