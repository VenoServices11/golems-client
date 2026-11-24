// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.gui.pausescreen

import net.minecraft.client.Minecraft

import net.minecraft.client.CameraType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

internal class PauseVec(@JvmField val x: Double, @JvmField val y: Double, @JvmField val z: Double) {
    fun add(other: PauseVec) = PauseVec(x + other.x, y + other.y, z + other.z)
    fun sub(other: PauseVec) = PauseVec(x - other.x, y - other.y, z - other.z)
    fun scale(by: Double) = PauseVec(x * by, y * by, z * by)
    fun dot(other: PauseVec) = x * other.x + y * other.y + z * other.z
    fun length() = Math.sqrt(dot(this))
    fun lerp(to: PauseVec, t: Double) = add(to.sub(this).scale(t))
}

internal object PausePlatform {
    private const val FIRST_PERSON = 0
    private const val THIRD_PERSON = 1
    private const val JITTER = 0.1
    private const val CORNERS = 8

    fun player(): LivingEntity? {
        val mc = Minecraft.getInstance()
        return if (mc.level != null) mc.player else null
    }

    fun feet(entity: LivingEntity, partial: Float): PauseVec {
        return PauseVec(
            entity.xo + (entity.x - entity.xo) * partial,
            entity.yo + (entity.y - entity.yo) * partial,
            entity.zo + (entity.z - entity.zo) * partial,
        )
    }

    fun eyeHeight(entity: LivingEntity): Double = entity.eyeHeight.toDouble()

    fun height(entity: LivingEntity): Double =
        entity.bbHeight.toDouble()

    fun yaw(entity: LivingEntity, partial: Float): Float =
        entity.getViewYRot(partial)

    fun pitch(entity: LivingEntity, partial: Float): Float =
        entity.getViewXRot(partial)

    fun bodyYaw(entity: LivingEntity): Float =
        entity.yBodyRot

    fun headYaw(entity: LivingEntity): Float =
        entity.yHeadRot

    private fun clip(entity: LivingEntity, from: PauseVec, to: PauseVec): Double {
        val reach = to.sub(from).length()
        val start = Vec3(from.x, from.y, from.z)
        val hit = entity.level().clip(ClipContext(start, Vec3(to.x, to.y, to.z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity))
        return if (hit.type == HitResult.Type.MISS) reach else start.distanceTo(hit.location)
    }

    fun clearance(entity: LivingEntity, from: PauseVec, to: PauseVec): Double {
        var shortest = to.sub(from).length()
        for (corner in 0 until CORNERS) {
            val offset = PauseVec(
                ((corner and 1) * 2 - 1) * JITTER,
                ((corner shr 1 and 1) * 2 - 1) * JITTER,
                ((corner shr 2 and 1) * 2 - 1) * JITTER,
            )
            shortest = minOf(shortest, clip(entity, from.add(offset), to.add(offset)))
        }
        return shortest
    }


    fun canStage(entity: LivingEntity): Boolean {
        val spectating = (entity as? Player)?.isSpectator == true
        return !spectating && !entity.isPassenger && !entity.isSleeping && !entity.isDeadOrDying &&
            !entity.isFallFlying && !entity.isSwimming && !entity.isUnderWater && !entity.isInLava
    }

    var cameraMode: Int
        get() {
            return Minecraft.getInstance().options.cameraType.ordinal
        }
        set(value) {
            Minecraft.getInstance().options.setCameraType(CameraType.entries[value])
        }

    fun firstPerson(mode: Int): Boolean = mode == FIRST_PERSON

    fun thirdPerson(mode: Int): Boolean = mode == THIRD_PERSON

    fun showBody() {
        cameraMode = THIRD_PERSON
    }

    var hudHidden: Boolean
        get() {
            return Minecraft.getInstance().options.hideGui
            
        }
        set(value) {
            Minecraft.getInstance().options.hideGui = value
            
        }

    fun motionScale(): Float {
        return Minecraft.getInstance().options.screenEffectScale().get().toFloat()
    }

    fun userFov(): Float {
        val options = Minecraft.getInstance().options
        return options.fov().get().toFloat()
    }

    fun aspect(): Float {
        val mc = Minecraft.getInstance()
        return mc.window.width.toFloat() / mc.window.height.coerceAtLeast(1)
    }

    fun gamePaused(): Boolean = Minecraft.getInstance().isPaused

    fun pausesWorld(): Boolean {
        val server = Minecraft.getInstance().singleplayerServer
        return server != null && !server.isPublished
    }

    fun skyDarkness(entity: LivingEntity): Int {
        return entity.level().skyDarken
    }
}
