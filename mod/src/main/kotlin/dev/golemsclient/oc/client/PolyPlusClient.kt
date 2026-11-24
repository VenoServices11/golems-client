// Golem's Client: what OneClient's lobby needs from PolyPlus's client object and settings, without OneConfig.
package dev.golemsclient.oc.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import dev.golemsclient.oc.client.lobby.LobbyCaptureFrequency
import dev.golemsclient.oc.client.lobby.LobbyCaptureView
import dev.golemsclient.oc.client.lobby.LobbyMusic
import dev.golemsclient.oc.client.lobby.LobbyReveal
import dev.golemsclient.oc.client.lobby.LobbySceneStore
import dev.golemsclient.oc.client.lobby.LobbyTime
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import java.nio.file.Files
import kotlin.math.roundToInt

object PolyPlusClient {
    private val LOGGER = LogManager.getLogger("Golem's Client")

    private val EXCEPTION_HANDLER = CoroutineExceptionHandler { _, throwable ->
        LOGGER.error("Uncaught exception in a Golem's Client task", throwable)
    }

    @JvmField
    val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Default + EXCEPTION_HANDLER)

    @JvmField
    val JSON = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
}

object PolyPlusConfig {
    /** OneClient's first-launch setup; Golem's Client has its own, so this counts as done. */
    @JvmField
    var onboardingCompleted = true

    /** The branded startup (the logo over the loading screen that flies into the menu's top bar). */
    @JvmField
    var brandedStartup = true
}

/** The lobby's settings, kept in config/golemsclient/lobby.json. */
object PolyPlusLobbyConfig {
    private val LOGGER = LogManager.getLogger("Golem's Client")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val file = FabricLoader.getInstance().configDir.resolve("golemsclient").resolve("lobby.json")

    var lobbyReveal: LobbyReveal = LobbyReveal.RANDOM
    var lobbyTime: LobbyTime = LobbyTime.CAPTURED
    var lobbyVignette = true
    var lobbyMusic: LobbyMusic = LobbyMusic.MATCH_SCENE
    var lobbyMusicVolume = 100f
    var lobbyMusicMuted = false
    var muteLobbyMusicUnfocused = false
    var muteAmbienceDuringMedia = true
    var lobbyCaptureFrequency: LobbyCaptureFrequency = LobbyCaptureFrequency.NORMAL
    var lobbyCaptureView: LobbyCaptureView = LobbyCaptureView.BEST
    var sceneCaptureLimit = 0f
        set(value) {
            field = value
            LobbySceneStore.captureLimit = value.roundToInt()
        }

    init {
        load()
        LobbySceneStore.captureLimit = sceneCaptureLimit.roundToInt()
    }

    fun inheritMusicVolume() {
    }

    private inline fun <reified E : Enum<E>> enumOr(json: JsonObject, key: String, fallback: E): E =
        json.get(key)?.asString?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

    fun load() {
        try {
            if (!Files.exists(file)) return
            val json = GSON.fromJson(Files.readString(file), JsonObject::class.java) ?: return
            lobbyReveal = enumOr(json, "lobbyReveal", lobbyReveal)
            lobbyTime = enumOr(json, "lobbyTime", lobbyTime)
            lobbyMusic = enumOr(json, "lobbyMusic", lobbyMusic)
            lobbyCaptureFrequency = enumOr(json, "lobbyCaptureFrequency", lobbyCaptureFrequency)
            lobbyCaptureView = enumOr(json, "lobbyCaptureView", lobbyCaptureView)
            json.get("lobbyVignette")?.let { lobbyVignette = it.asBoolean }
            json.get("lobbyMusicVolume")?.let { lobbyMusicVolume = it.asFloat }
            json.get("lobbyMusicMuted")?.let { lobbyMusicMuted = it.asBoolean }
            json.get("muteLobbyMusicUnfocused")?.let { muteLobbyMusicUnfocused = it.asBoolean }
            json.get("muteAmbienceDuringMedia")?.let { muteAmbienceDuringMedia = it.asBoolean }
            json.get("sceneCaptureLimit")?.let { sceneCaptureLimit = it.asFloat }
        } catch (e: Exception) {
            LOGGER.warn("Couldn't read the lobby settings", e)
        }
    }

    fun save() {
        try {
            val json = JsonObject()
            json.addProperty("lobbyReveal", lobbyReveal.name)
            json.addProperty("lobbyTime", lobbyTime.name)
            json.addProperty("lobbyMusic", lobbyMusic.name)
            json.addProperty("lobbyCaptureFrequency", lobbyCaptureFrequency.name)
            json.addProperty("lobbyCaptureView", lobbyCaptureView.name)
            json.addProperty("lobbyVignette", lobbyVignette)
            json.addProperty("lobbyMusicVolume", lobbyMusicVolume)
            json.addProperty("lobbyMusicMuted", lobbyMusicMuted)
            json.addProperty("muteLobbyMusicUnfocused", muteLobbyMusicUnfocused)
            json.addProperty("muteAmbienceDuringMedia", muteAmbienceDuringMedia)
            json.addProperty("sceneCaptureLimit", sceneCaptureLimit)
            Files.createDirectories(file.parent)
            Files.writeString(file, GSON.toJson(json))
        } catch (e: Exception) {
            LOGGER.warn("Couldn't save the lobby settings", e)
        }
    }
}
