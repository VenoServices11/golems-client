// Golem's Client: OneClient's parties run on Polyfrost's servers. Until Golem's Client has its own, nobody is in a
// party, so the lobby only ever shows your own scenes and your own player.
package dev.golemsclient.oc.client.party

import dev.golemsclient.oc.client.lobby.LobbySceneEntry
import net.minecraft.SharedConstants
import java.util.UUID

object PartyScene {
    val mcVersion: String get() = SharedConstants.getCurrentVersion().name()

    fun startEntries(): List<LobbySceneEntry> = emptyList()

    fun speaking(): Set<UUID> = emptySet()

    fun followedClock(): Long? = null

    fun followed(): Any? = null
}
