// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.PlayerModelPart
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.phys.Vec3
import dev.golemsclient.oc.client.cosmetics.CapeResolver
import dev.golemsclient.oc.client.cosmetics.LocalAccountSkin
import dev.golemsclient.oc.client.cosmetics.LocalSkinOverride
import kotlin.math.hypot

class LobbyAvatar(
    level: ClientLevel,
    profile: GameProfile,
    val displayName: String,
    val isLocal: Boolean,
    rest: LobbySlot,
    private val skinSource: () -> PlayerSkin,
) : RemotePlayer(level, profile) {
    enum class Arrival { WAITING, ARRIVING, LANDED }

    var rest = rest
        private set
    var arrival = Arrival.WAITING
        private set
    var landedTick = -1L
        private set

    var whereabouts: String? = null
        private set
    var projection = 0.0
        private set
    var dismissed = false
        private set

    private var hidden = false
    private var phantom = false

    val spectral: Boolean get() = whereabouts != null
    val projecting: Boolean get() = !hidden && projection > 0.0
    val vanished: Boolean get() = dismissed && projection <= 0.0

    private var motion: LobbyMotion? = null
    private var landedThisTick = false

    val gaze by lazy { LobbyGaze(uuid.hashCode()) }


    override fun getSkin(): PlayerSkin {
        if (!isLocal) return CapeResolver.applyCape(uuid, gameProfile, skinSource())
        val skin = CapeResolver.applyCape(uuid, LocalAccountSkin.profile(), LocalAccountSkin.skin())
        val chosen = LobbyWardrobe.preview?.skin?.let { LocalSkinOverride.withSkin(skin, it) } ?: LocalSkinOverride.apply(uuid, skin)
        return LobbyWardrobe.withPreviewCape(chosen)
    }

    override fun shouldRender(x: Double, y: Double, z: Double): Boolean =
        !hidden && !phantom && projection < SOLID_SWAP && arrival != Arrival.WAITING && super.shouldRender(x, y, z)

    override fun isModelPartShown(part: PlayerModelPart): Boolean = true

    override fun distanceToSqr(pos: Vec3): Double = 0.0

    override fun tick() {
        val body = yBodyRot
        val head = yHeadRot
        val waiting = arrival == Arrival.WAITING
        val moving = stepArrival()
        if (!moving && arrival == Arrival.LANDED) followBody()
        super.tick()
        if (moving) {
            yBodyRot = yRot
            yHeadRot = yRot
            if (!waiting) {
                yBodyRotO = yBodyRot + Mth.wrapDegrees(body - yBodyRot)
                yHeadRotO = yHeadRot + Mth.wrapDegrees(head - yHeadRot)
            }
        }
    }

    private fun followBody() {
        yRot = yBodyRot
        yRotO = yBodyRot
    }

    private fun stepArrival(): Boolean {
        val current = motion ?: return false
        if (arrival == Arrival.LANDED) {
            current.settle(this)
            return false
        }
        val elapsed = LobbyScene.tick() - current.startTick
        if (elapsed < 0L) return false
        if (arrival == Arrival.WAITING) {
            arrival = Arrival.ARRIVING
            current.begin(this)
            setOldPosAndRot()
        }
        if (current.advance(this, elapsed)) {
            arrival = Arrival.LANDED
            landedThisTick = true
            if (landedTick < 0L) landedTick = LobbyScene.tick()
        }
        return true
    }

    fun consumeLanded(): Boolean = landedThisTick.also { landedThisTick = false }

    fun stride(step: Vec3?) {
        isSprinting = step != null && hypot(step.x, step.z) > SPRINT_STEP
        deltaMovement = step ?: Vec3.ZERO
    }

    val climb: Double get() = maxUpStep().toDouble()

    val fallRate: Double get() = gravity

    val bumped: Boolean get() = horizontalCollision

    val bumpedVertically: Boolean get() = verticalCollision

    fun walk(delta: Vec3) = move(MoverType.SELF, delta)

    fun leap(): Double {
        jumpFromGround()
        return deltaMovement.y
    }

    fun halt() {
        motion = null
        landedThisTick = false
        stride(null)
        arrival = Arrival.LANDED
        if (landedTick < 0L) landedTick = LobbyScene.tick()
    }

    fun arriveNow() {
        motion = null
        landedThisTick = false
        stride(null)
        walkAnimation.stop()
        if (arrival == Arrival.LANDED) return
        arrival = Arrival.LANDED
        if (landedTick < 0L) landedTick = LobbyScene.tick()
        place(rest.pos, rest.yaw)
        face(rest.yaw, 0f)
        setOldPosAndRot()
    }

    fun begin(next: LobbyMotion) {
        motion = next
    }

    fun moveTo(slot: LobbySlot) {
        rest = slot
    }

    fun travel(next: LobbyMotion) {
        motion = next
        landedThisTick = false
        arrival = Arrival.ARRIVING
        next.begin(this)
    }

    fun project(place: String?): Boolean {
        val changed = (place != null) != spectral
        whereabouts = place
        dismissed = false
        if (place == null) phantom = false
        return changed
    }

    fun conjure(place: String) {
        whereabouts = place
        phantom = true
        arriveNow()
    }

    fun dismiss() {
        dismissed = true
        phantom = true
    }

    fun settleProjection(elapsed: Double) {
        if (arrival != Arrival.LANDED) return
        val target = if (spectral && !dismissed) 1.0 else 0.0
        val step = elapsed / PROJECTION_SECONDS
        projection = if (projection < target) minOf(target, projection + step) else maxOf(target, projection - step)
    }

    fun fade(amount: Double) {
        hidden = amount >= 1.0
        getAttribute(Attributes.SCALE)?.baseValue = (1.0 - amount * amount).coerceAtLeast(MIN_SCALE)
    }

    fun face(yaw: Float, headTurn: Float) {
        yRot = yaw
        yRotO = yaw
        yBodyRot = yaw
        yBodyRotO = yaw
        yHeadRot = yaw + headTurn
        yHeadRotO = yaw + headTurn
    }

    fun place(pos: Vec3, yaw: Float) {
        setPos(pos)
        yRot = yaw
        yBodyRot = yaw
        yHeadRot = yaw
    }

    companion object {
        private const val MIN_SCALE = 0.07
        private const val SOLID_SWAP = 0.5
        private const val PROJECTION_SECONDS = 1.5
        private const val SPRINT_STEP = 0.12
    }
}
