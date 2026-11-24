// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.utils

import com.google.gson.JsonPrimitive
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.serialization.JsonOps
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.joml.Quaternionf
import dev.golemsclient.oc.support.DesktopHelper
import dev.golemsclient.oc.support.Multithreading
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import net.minecraft.world.entity.player.PlayerModelType



object ClientPlatform {
    private const val MAIN_SYNC_TIMEOUT_SECONDS = 30L
    private const val VANILLA_UNLIMITED_FRAMERATE = 260
    private const val MAX_UNLIMITED_FRAMERATE = 10_000
    private const val MINIMIZED_FRAME_LIMIT = 10

    val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("windows")

    val isMac: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    val unlimitedFramerate: Int by lazy {
        val codec = Minecraft.getInstance().options.framerateLimit().codec()
        highestAccepted(VANILLA_UNLIMITED_FRAMERATE, MAX_UNLIMITED_FRAMERATE) { codec.parse(JsonOps.INSTANCE, JsonPrimitive(it)).result().isPresent }
    }

    internal fun highestAccepted(floor: Int, ceiling: Int, accepts: (Int) -> Boolean): Int {
        var low = floor
        var high = ceiling
        while (low < high) {
            val mid = low + (high - low + 1) / 2
            if (accepts(mid)) low = mid else high = mid - 1
        }
        return low
    }

    fun monitorRefreshRate(): Int =
        Minecraft.getInstance().window.refreshRate
        

    fun windowMinimized(): Boolean =
        Minecraft.getInstance().window.isIconified

    fun menuFrameLimit(limit: Int): Int =
        if (!inWorld() && windowMinimized()) minOf(limit, MINIMIZED_FRAME_LIMIT) else limit

    fun inWorld(): Boolean =
        Minecraft.getInstance().level != null

    fun runOnMain(action: () -> Unit) {
        val client = Minecraft.getInstance()
        if (client.isSameThread) {
            action()
        } else {
            client.execute(action)
        }
    }

    fun <T> runOnMainSync(action: () -> T): T {
        val client = Minecraft.getInstance()
        if (client.isSameThread) {
            return action()
        }
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        // whichever side claims it first decides, so a task never runs after its caller has given up on it
        val claimed = AtomicBoolean()
        val latch = CountDownLatch(1)
        client.execute {
            if (!claimed.compareAndSet(false, true)) return@execute
            try {
                result.set(action())
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        // Minecraft#disconnect drops queued tasks, which would otherwise leave this waiting forever
        if (!latch.await(MAIN_SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            if (claimed.compareAndSet(false, true)) {
                throw IllegalStateException("Main thread did not run the task within $MAIN_SYNC_TIMEOUT_SECONDS seconds")
            }
            latch.await()
        }
        error.get()?.let { throw it }
        return result.get()
    }

    fun windowFocused(): Boolean = runCatching {
        Minecraft.getInstance().isWindowActive
    }.getOrDefault(true)

    fun currentScreen(): Screen? {
        val mc = Minecraft.getInstance()
        return mc.screen
        
    }

    fun setScreen(screen: Screen?) {
        val mc = Minecraft.getInstance()
        mc.setScreen(screen)
        
    }

    fun openUri(uri: String) {
        Multithreading.submit { DesktopHelper.browse(URI(uri)) }
    }

    @Volatile
    private var offlineUuid: Pair<String, UUID>? = null

    fun localPlayerUuid(): UUID {
        val user = Minecraft.getInstance().user
        val id: UUID? = user.profileId
        return id ?: offlineUuidFor(user.name)
    }

    private fun offlineUuidFor(name: String): UUID {
        offlineUuid?.let { (cachedName, uuid) -> if (cachedName == name) return uuid }
        return UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))
            .also { offlineUuid = name to it }
    }

    fun localPlayerName(): String = Minecraft.getInstance().user.name

    fun localSkinSlim(): Boolean =
        Minecraft.getInstance().player?.skin?.model() == PlayerModelType.SLIM
}


fun PoseStack.rotateBy(rotation: Quaternionf) {
    mulPose(rotation)
    
}
