// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.api.lobby

import net.minecraft.world.level.Level
import org.jetbrains.annotations.ApiStatus
import dev.golemsclient.oc.client.lobby.LobbyEvents
import dev.golemsclient.oc.client.lobby.LobbyScene
import java.util.Optional

@ApiStatus.Experimental
object LobbyApi {
    const val API_VERSION = 1

    @JvmStatic
    fun isAvailable(): Boolean = LobbyScene.available()

    @JvmStatic
    fun phase(): LobbyPhase = LobbyScene.apiPhase()

    @JvmStatic
    fun addListener(listener: LobbyListener) {
        LobbyEvents.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: LobbyListener) {
        LobbyEvents.remove(listener)
    }

    @JvmStatic
    fun isLobbyLevel(level: Level?): Boolean = LobbyScene.isSceneLevel(level)

    @JvmStatic
    fun isRendering(): Boolean = LobbyScene.terrainActive()

    @JvmStatic
    fun isBuilding(): Boolean = LobbyScene.building

    @JvmStatic
    fun state(): Optional<LobbyState> = Optional.ofNullable(LobbyScene.state())

    @JvmStatic
    fun isSceneAvailable(sceneId: String): Boolean = LobbyScene.findScene(sceneId) != null

    @JvmStatic
    fun start(sceneId: String, inputs: LobbyInputs, mode: LobbyMode): Optional<LobbyHandle> =
        Optional.ofNullable(LobbyScene.startSession(sceneId, inputs, mode))
}
