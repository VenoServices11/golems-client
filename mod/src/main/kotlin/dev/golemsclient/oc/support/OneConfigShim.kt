// Golem's Client: small stand-ins for the OneConfig pieces OneClient's lobby used, so the lobby runs without OneConfig.
package dev.golemsclient.oc.support

import dev.golemsclient.GolemsClient
import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import java.net.URI
import java.util.concurrent.Executors

/** OneConfig's "reduce motion" accessibility switch. Golem's Client has none yet, so motion is always on. */
object Motion {
    @JvmStatic
    val reduced: Boolean get() = false
}

object OneConfigUI {
    @JvmStatic
    fun isOneConfigScreen(screen: Any?): Boolean = false
}

object OneConfigTheme {
    class Shade(private val value: Float) {
        fun luminance(): Float = value
    }

    class Theme(val pageBackground: Shade, val textColor: Shade)

    /** Golem's Client's menus are dark. */
    val active: Theme? get() = Theme(Shade(0.05f), Shade(0.95f))
}

object ThemeConfig {
    class Accent(val argb: Int)

    val accentColor: Accent get() = Accent(GolemsClient.ACCENT)
}

object UiSounds {
    @JvmStatic
    fun releaseAmbience() {
    }
}

object Multithreading {
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "Golem's Client worker").apply { isDaemon = true } }

    @JvmStatic
    fun submit(task: Runnable) {
        pool.submit(task)
    }
}

object DesktopHelper {
    @JvmStatic
    fun browse(uri: URI) {
        Util.getPlatform().openUri(uri)
    }
}

object Platform {
    class Compatibility {
        fun fps(): Int = Minecraft.getInstance().fps
    }

    private val compatibility = Compatibility()

    @JvmStatic
    fun compatibility(): Compatibility = compatibility
}
