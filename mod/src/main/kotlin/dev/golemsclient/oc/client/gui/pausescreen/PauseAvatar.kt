// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.gui.pausescreen

import dev.golemsclient.oc.client.lobby.LobbyEase
import kotlin.math.sin

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

import net.minecraft.client.model.player.PlayerModel

import net.minecraft.client.renderer.entity.state.AvatarRenderState

object PauseAvatar {
    private const val HEAD_RATE = 7.0
    private const val BODY_RATE = 3.0
    private const val HEAD_YAW_REACH = 34.0
    private const val HEAD_PITCH_REACH = 18.0
    private const val HEAD_PITCH_REST = -2.0
    private const val STANCE = 22f
    private const val HEAD_BODY_LIMIT = 48f
    private const val HEAD_LEAD = 40f
    private const val BODY_LEAD = 60f
    private const val SCREEN_X = -0.3
    private const val SCREEN_Y = 0.25
    private const val BREATH_RATE = 1.5f
    private const val BREATH_PIXELS = 0.22f
    private const val TICKS_PER_SECOND = 20f
    private const val KEY_LIGHT = 13
    private const val FULL_SKY = 15
    private const val BLOCK_SHIFT = 4
    private const val SKY_SHIFT = 20
    private const val LEVEL_MASK = 15

    private val head = LobbyEase()
    private val pitch = LobbyEase()
    private val body = LobbyEase()
    private var clock = 0f
    private var weight = 0f
    private var ageBase = 0f
    private var marked: Any? = null

    private fun wrap(degrees: Float): Float {
        var angle = degrees % 360f
        if (angle >= 180f) angle -= 360f
        if (angle < -180f) angle += 360f
        return angle
    }

    private fun blend(from: Float, to: Float, amount: Float): Float = from + wrap(to - from) * amount

    private fun isLocal(entity: Entity?): Boolean = weight > 0f && entity != null && entity === PausePlatform.player()

    internal fun begin(entity: LivingEntity) {
        if (weight <= 0f) {
            head.snap(PausePlatform.headYaw(entity).toDouble())
            pitch.snap(PausePlatform.pitch(entity, 1f).toDouble())
            body.snap(PausePlatform.bodyYaw(entity).toDouble())
            clock = 0f
            ageBase = -1f
        }
    }

    internal fun end() {
        weight = 0f
        marked = null
    }

    internal fun step(entity: LivingEntity, dt: Float) {
        clock += dt
        weight = PauseStage.avatarWeight
        val facing = PausePlatform.yaw(entity, 1f) + PauseStage.arc
        val stance = facing - STANCE
        val cursorX = PauseStage.pointerX - SCREEN_X
        val cursorY = PauseStage.pointerY - SCREEN_Y
        val gaze = facing - (cursorX * HEAD_YAW_REACH).toFloat()
        val look = HEAD_PITCH_REST - cursorY * HEAD_PITCH_REACH
        val headNow = head.value.toFloat()
        val bodyNow = body.value.toFloat()
        head.step((headNow + wrap(gaze - headNow).coerceIn(-HEAD_LEAD, HEAD_LEAD)).toDouble(), HEAD_RATE, dt.toDouble())
        pitch.step(look.coerceIn(-HEAD_PITCH_REACH, HEAD_PITCH_REACH), HEAD_RATE, dt.toDouble())
        body.step((bodyNow + wrap(stance - bodyNow).coerceIn(-BODY_LEAD, BODY_LEAD)).toDouble(), BODY_RATE, dt.toDouble())
    }

    private fun bodyNow(real: Float): Float = blend(real, body.value.toFloat(), weight)

    private fun headNow(real: Float, bodyYaw: Float): Float {
        val turned = blend(real, head.value.toFloat(), weight)
        return bodyYaw + wrap(turned - bodyYaw).coerceIn(-HEAD_BODY_LIMIT, HEAD_BODY_LIMIT)
    }

    private fun pitchNow(real: Float): Float = real + (pitch.value.toFloat() - real) * weight

    private fun age(real: Float): Float {
        if (ageBase < 0f) ageBase = real - clock * TICKS_PER_SECOND
        return ageBase + clock * TICKS_PER_SECOND
    }

    @JvmStatic
    fun hidesName(entity: Any?): Boolean = isLocal(entity as? Entity)

    @JvmStatic
    fun light(entity: Entity?, packed: Int): Int {
        if (!isLocal(entity)) return packed
        val living = entity as? LivingEntity ?: return packed
        val blockLight = packed shr BLOCK_SHIFT and LEVEL_MASK
        val skyLight = packed shr SKY_SHIFT and LEVEL_MASK
        val daylight = (skyLight - PausePlatform.skyDarkness(living)).coerceIn(0, FULL_SKY)
        val wanted = maxOf(blockLight, ((KEY_LIGHT - daylight).coerceAtLeast(0) * weight).toInt() + (blockLight * (1f - weight)).toInt())
        return (wanted shl BLOCK_SHIFT) or (skyLight shl SKY_SHIFT)
    }

    @JvmStatic
    fun breathe(model: PlayerModel, entity: Any?) {
        if (weight <= 0f || (entity != null && entity !== marked && !isLocal(entity as? Entity))) return
        val rise = (sin(clock * BREATH_RATE) * 0.5f + 0.5f) * BREATH_PIXELS * weight
        model.head.y -= rise
        model.body.y -= rise
        model.rightArm.y -= rise
        model.leftArm.y -= rise
    }

    @JvmStatic
    fun apply(entity: Any?, state: AvatarRenderState) {
        if (entity !is LivingEntity || !isLocal(entity)) return
        marked = state
        val bodyYaw = bodyNow(state.bodyRot)
        val realHead = state.bodyRot + state.yRot
        state.yRot = wrap(headNow(realHead, bodyYaw) - bodyYaw)
        state.bodyRot = bodyYaw
        state.xRot = pitchNow(state.xRot)
        state.ageInTicks = age(state.ageInTicks)
        state.nameTag = null
        state.scoreText = null
        state.walkAnimationSpeed *= 1f - weight
        state.attackTime *= 1f - weight
        
    }
}
