// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import net.minecraft.client.Minecraft
import dev.golemsclient.oc.support.UiSounds
import dev.golemsclient.oc.client.PolyPlusLobbyConfig
import dev.golemsclient.oc.client.gui.pausescreen.PauseEvergreenMedia
import dev.golemsclient.oc.client.utils.ClientPlatform
import dev.golemsclient.oc.compat.Mods

object AmbienceQuiet {
    private const val CHECK_TICKS = 10

    private var ticks = 0
    private var watching = false

    @JvmStatic
    var quiet = false
        private set

    fun tick() {
        if (++ticks < CHECK_TICKS) return
        ticks = 0
        val watch = PolyPlusLobbyConfig.muteAmbienceDuringMedia && Mods.evergreenHud && PauseEvergreenMedia.supported() && menusOpen()
        if (watch != watching) {
            watching = watch
            if (watch) PauseEvergreenMedia.acquire() else PauseEvergreenMedia.release()
        }
        val now = PolyPlusLobbyConfig.lobbyMusicMuted || watching && PauseEvergreenMedia.playing()
        if (now == quiet) return
        quiet = now
        runCatching { (UiSounds as Any as UiAmbienceAccess).`golems$setQuiet`(now) }
    }

    private fun menusOpen(): Boolean = Minecraft.getInstance().level == null || ClientPlatform.currentScreen() != null
}
