// Golem's Client: what the main menu (Java) needs from OneClient's lobby (ported from PolyPlus, GPL-3.0, by Polyfrost).
// It plays the part OneClient's own lobby screen and its panels played: starts the scene, passes on clicks, drags and
// keys, leaves the scene before joining a world or server, and draws the startup overlay over the menu.
package dev.golemsclient.oc

import dev.golemsclient.oc.client.PolyPlusLobbyConfig
import dev.golemsclient.oc.client.PolyPlusConfig
import dev.golemsclient.oc.client.lobby.LobbyAntics
import dev.golemsclient.oc.client.lobby.LobbyGrab
import dev.golemsclient.oc.client.lobby.LobbyFraming
import dev.golemsclient.oc.client.lobby.LobbyHologram
import dev.golemsclient.oc.client.lobby.LobbyMusic
import dev.golemsclient.oc.client.lobby.LobbyReveal
import dev.golemsclient.oc.client.lobby.LobbyScene
import dev.golemsclient.oc.client.lobby.LobbySceneEntry
import dev.golemsclient.oc.client.lobby.LobbySceneStore
import dev.golemsclient.oc.client.lobby.LobbyTime
import dev.golemsclient.oc.client.lobby.LobbyWardrobe
import dev.golemsclient.oc.client.lobby.SceneMode
import dev.golemsclient.oc.client.lobby.StartupOverlay
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

object LobbyBridge {
    /** A name tag over a player in the scene, in GUI pixels (x at the middle, y at the bottom of the tag). */
    class Tag(@JvmField val name: String, @JvmField val x: Float, @JvmField val y: Float, @JvmField val local: Boolean)

    /** A scene the lobby can show. */
    class Scene(@JvmField val id: String, @JvmField val name: String, @JvmField val builtIn: Boolean)

    // ------------------------------------------------------------------ the frame

    /** Called every frame the menu is drawn: starts the scene if it isn't running. True when the scene is showing. */
    @JvmStatic
    fun frame(): Boolean {
        LobbyScene.start()
        return LobbyScene.showing()
    }

    @JvmStatic
    fun showing(): Boolean = LobbyScene.showing()

    /** The lobby is showing (in the menu, or while its scene is being drawn or ticked). */
    @JvmStatic
    fun inLobby(): Boolean = LobbyScene.terrainActive() || LobbyScene.showing()

    /** The scene's opening (the run-in) is still playing. */
    @JvmStatic
    fun introPlaying(): Boolean = LobbyScene.introPlaying()

    /** OneClient's lobby can't run here (another mod is in the way): the menu draws its own background. */
    @JvmStatic
    fun cannotRun(): Boolean = LobbyScene.cannotRun()

    /** How visible the menu's panels should be: they fade in as the scene finishes revealing itself. */
    @JvmStatic
    fun panelAlpha(): Float = if (LobbyWardrobe.open) 1f else LobbyScene.overlayAlpha

    /** 0 to 1 while the camera leaves the scene before joining a world or server. */
    @JvmStatic
    fun departProgress(): Float = LobbyScene.departProgress

    /** The Wardrobe page is open: the camera moves in close on your player, like OneClient's. */
    @JvmStatic
    fun wardrobe(open: Boolean) {
        LobbyWardrobe.open = open
    }

    @JvmStatic
    fun accent(argb: Int) {
        LobbyHologram.accent = argb and 0xFFFFFF
    }

    @JvmStatic
    fun tags(guiWidth: Int, guiHeight: Int): List<Tag> = LobbyScene.labels.map {
        Tag(it.name, (it.x + 1f) * 0.5f * guiWidth, (1f - it.y) * 0.5f * guiHeight, it.isLocal)
    }

    @JvmStatic
    fun focused(): String? = LobbyScene.focused

    @JvmStatic
    fun focus(name: String?) {
        if (name == null) LobbyScene.clearFocus() else LobbyScene.focus(name)
    }

    // ------------------------------------------------------------------ input

    /** A click while the intro plays skips it. True when the click was used. */
    @JvmStatic
    fun skipIntro(): Boolean = StartupOverlay.active || LobbyScene.skipIntro()

    @JvmStatic
    fun key(key: Int, calm: Boolean) = LobbyAntics.key(key, calm)

    /** A click on your player (x and y from 0 to 1 across the window): a little reaction. */
    @JvmStatic
    fun click(x: Double, y: Double): Boolean = LobbyAntics.click(x, y)

    @JvmStatic
    fun touches(x: Double, y: Double): Boolean = LobbyGrab.touches(x, y)

    @JvmStatic
    fun grab(x: Double, y: Double): Boolean = LobbyGrab.grab(x, y)

    @JvmStatic
    fun drag(x: Double, y: Double) = LobbyGrab.drag(x, y)

    @JvmStatic
    fun release() = LobbyGrab.release()

    @JvmStatic
    fun cancelGrab() = LobbyGrab.cancel()

    // ------------------------------------------------------------------ leaving and coming back

    /** The camera pulls away from the scene, then [action] runs (joining a world or a server). */
    @JvmStatic
    fun depart(action: Runnable) {
        LobbyScene.depart { action.run() }
    }

    /** The menu opened (again): the scene welcomes you back. */
    @JvmStatic
    fun arrive() = LobbyScene.arrive()

    // ------------------------------------------------------------------ the startup overlay

    @JvmStatic
    fun startupActive(): Boolean = StartupOverlay.active

    /** Where the logo, the name and the line land at the end of the startup animation, in GUI pixels. */
    @JvmStatic
    fun startupTargets(guiWidth: Float, mark: FloatArray, word: FloatArray, line: FloatArray?) {
        StartupOverlay.rootWidth = guiWidth
        StartupOverlay.navMark = StartupOverlay.Box(mark[0], mark[1], mark[2], mark[3])
        StartupOverlay.navWord = StartupOverlay.Box(word[0], word[1], word[2], word[3])
        StartupOverlay.navLine = line?.let { StartupOverlay.Box(it[0], it[1], it[2], it[3]) }
    }

    /** Draws the startup overlay over the menu while it's still running. */
    @JvmStatic
    fun startupFrame(g: GuiGraphics) = StartupOverlay.screenFrame(Minecraft.getInstance(), g)

    // ------------------------------------------------------------------ customizing

    @JvmStatic
    fun scenes(): List<Scene> = LobbySceneStore.usableScenes.map { Scene(it.id, sceneName(it), it.bundled) }

    private fun sceneName(entry: LobbySceneEntry): String =
        if (entry.bundled) entry.label else LobbySceneStore.worldName(entry)

    /** The scene picked in the menu, or null for "Automatic". */
    @JvmStatic
    fun pickedScene(): String? = if (LobbySceneStore.mode == SceneMode.PICK) LobbySceneStore.pinned else null

    @JvmStatic
    fun currentScene(): String? = LobbyScene.currentId

    @JvmStatic
    fun pickScene(id: String?) {
        LobbySceneStore.choose(id)
        LobbyScene.reload()
    }

    @JvmStatic
    fun capturing(): Boolean = LobbySceneStore.captureEnabled

    @JvmStatic
    fun setCapturing(on: Boolean) {
        LobbySceneStore.captureEnabled = on
    }

    @JvmStatic
    fun times(): Array<LobbyTime> = LobbyTime.entries.toTypedArray()

    @JvmStatic
    fun time(): LobbyTime = PolyPlusLobbyConfig.lobbyTime

    @JvmStatic
    fun setTime(time: LobbyTime) {
        PolyPlusLobbyConfig.lobbyTime = time
        PolyPlusLobbyConfig.save()
    }

    @JvmStatic
    fun reveals(): Array<LobbyReveal> = LobbyReveal.entries.toTypedArray()

    @JvmStatic
    fun reveal(): LobbyReveal = PolyPlusLobbyConfig.lobbyReveal

    @JvmStatic
    fun setReveal(reveal: LobbyReveal) {
        PolyPlusLobbyConfig.lobbyReveal = reveal
        PolyPlusLobbyConfig.save()
    }

    @JvmStatic
    fun musics(): Array<LobbyMusic> = LobbyMusic.entries.toTypedArray()

    @JvmStatic
    fun music(): LobbyMusic = PolyPlusLobbyConfig.lobbyMusic

    @JvmStatic
    fun setMusic(music: LobbyMusic) {
        PolyPlusLobbyConfig.lobbyMusic = music
        PolyPlusLobbyConfig.save()
    }

    /** The lobby music's volume, 0 to 1. */
    @JvmStatic
    fun musicVolume(): Float = PolyPlusLobbyConfig.lobbyMusicVolume / 100f

    @JvmStatic
    fun setMusicVolume(volume: Float) {
        PolyPlusLobbyConfig.lobbyMusicVolume = (volume * 100f).coerceIn(0f, 100f)
    }

    @JvmStatic
    fun saveSettings() = PolyPlusLobbyConfig.save()

    @JvmStatic
    fun musicMuted(): Boolean = PolyPlusLobbyConfig.lobbyMusicMuted

    @JvmStatic
    fun setMusicMuted(muted: Boolean) {
        PolyPlusLobbyConfig.lobbyMusicMuted = muted
        PolyPlusLobbyConfig.save()
    }

    // ------------------------------------------------------------------ the first-launch setup

    /** The setup is waiting (decided at startup, so the startup logo can fly into its card). */
    @JvmStatic
    fun setupPending(pending: Boolean) {
        StartupOverlay.setupPending = pending
        PolyPlusConfig.onboardingCompleted = !pending
    }

    /** The setup card is showing: your player in the lobby turns to look at it. */
    @JvmStatic
    fun setupShowing(showing: Boolean) {
        LobbyFraming.onboarding = showing
        if (!showing) PolyPlusConfig.onboardingCompleted = true
    }

    /** Where the setup card's middle is, -1 to 1 across the window (y up). */
    @JvmStatic
    fun setupCard(x: Double, y: Double) = LobbyFraming.card(x, y)

    /** Where the logo and the name land in the setup card, in GUI pixels, and the colour they turn. */
    @JvmStatic
    fun setupTargets(mark: FloatArray, word: FloatArray, ink: Int) {
        StartupOverlay.cardMark = StartupOverlay.Box(mark[0], mark[1], mark[2], mark[3])
        StartupOverlay.cardWord = StartupOverlay.Box(word[0], word[1], word[2], word[3])
        StartupOverlay.cardInk = ink and 0xFFFFFF
    }

    /** 0 to 1 as the setup card grows out of the startup logo (1 when there was no startup). */
    @JvmStatic
    fun cardGrow(): Float = StartupOverlay.cardGrow

    /** Your player nods (next page) or waves (finished). */
    @JvmStatic
    fun nod() = LobbyFraming.react(LobbyFraming.Reaction.NOD)

    @JvmStatic
    fun wave() = LobbyFraming.react(LobbyFraming.Reaction.WAVE)

    @JvmStatic
    fun glance() = LobbyFraming.react(LobbyFraming.Reaction.GLANCE)
}
