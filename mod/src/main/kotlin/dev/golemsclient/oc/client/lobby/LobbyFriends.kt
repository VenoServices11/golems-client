// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.world.entity.player.PlayerSkin
import dev.golemsclient.friends.Friends
import dev.golemsclient.oc.client.PolyPlusClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

class LobbyFriend(val uuid: UUID, val name: String, val whereabouts: String? = null)

class LobbyCast(val party: List<LobbyFriend>, val background: List<LobbyFriend>)

object LobbyFriends {
    private const val REFRESH_TICKS = 1200

    private val EMPTY = LobbyCast(emptyList(), emptyList())

    private val profiles = ConcurrentHashMap<UUID, GameProfile>()
    private val fetching = ConcurrentHashMap.newKeySet<UUID>()
    private val retries = ConcurrentHashMap<UUID, Retry>()
    private val RETRY_MILLIS = longArrayOf(30_000L, 120_000L, 600_000L)
    private const val FALLBACK_RETRY_MILLIS = 300_000L
    private const val REFRESH_MILLIS = 1_800_000L

    private class Retry(val attempt: Int, val at: Long)
    private val lookups = HashMap<UUID, Pair<GameProfile, Supplier<PlayerSkin>>>()
    private var ticks = 0

    fun refresh() {
        // Golem's Client: friends come from Golem's Client's own server (dev.golemsclient.friends.Friends polls it)
    }

    fun tick() {
        if (++ticks < REFRESH_TICKS) return
        ticks = 0
        refresh()
    }

    /**
     * Golem's Client: your online friends stand in the lobby. Friends in the launcher are there in person; friends
     * playing somewhere are shown as a projection with where they are ("Playing on donutsmp.net").
     */
    fun cast(): LobbyCast {
        val state = Friends.state() ?: return EMPTY
        val me = Minecraft.getInstance().user?.profileId
        // your party stands next to you; your other online friends further back
        val party = dev.golemsclient.friends.Parties.party()?.members().orEmpty()
            .filter { it.uuid() != me && it.online() && !it.name().isNullOrEmpty() }
        val inParty = party.map { it.uuid() }.toSet()
        val partyCast = party.map { member ->
            LobbyFriend(member.uuid(), member.name()!!, member.where()?.takeIf { it.startsWith("Playing") })
        }
        val online = state.friends().filter { it.online() && !it.name().isNullOrEmpty() && it.uuid() !in inParty }
        if (partyCast.isEmpty() && online.isEmpty()) return EMPTY
        return LobbyCast(partyCast, online.map { LobbyFriend(it.uuid(), it.name()!!, if (it.inGame()) it.where() else null) })
    }

    fun skin(friend: LobbyFriend): PlayerSkin {
        val profile = profiles[friend.uuid]
        if (profile == null) {
            fetch(friend)
            return fallback(friend.uuid)
        }
        if (retries.containsKey(friend.uuid)) fetch(friend)
        val lookup = lookups[friend.uuid]?.takeIf { it.first === profile }
            ?: (profile to Minecraft.getInstance().skinManager.createLookup(profile, false)).also { lookups[friend.uuid] = it }
        return lookup.second.get()
    }

    private fun fallback(uuid: UUID): PlayerSkin {
        return DefaultPlayerSkin.get(uuid)
    }

    private fun fetch(friend: LobbyFriend) {
        val retry = retries[friend.uuid]
        if (retry != null && System.currentTimeMillis() < retry.at) return
        if (!fetching.add(friend.uuid)) return
        val attempt = retry?.attempt ?: 0
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            try {
                val mc = Minecraft.getInstance()
                val fetched = runCatching {
                    mc.services().sessionService().fetchProfile(friend.uuid, true)?.profile()
                }.getOrNull()
                when {
                    fetched != null -> {
                        profiles[friend.uuid] = fetched
                        retries[friend.uuid] = Retry(0, System.currentTimeMillis() + REFRESH_MILLIS)
                    }
                    attempt < RETRY_MILLIS.size -> retries[friend.uuid] = Retry(attempt + 1, System.currentTimeMillis() + RETRY_MILLIS[attempt])
                    else -> {
                        profiles.putIfAbsent(friend.uuid, GameProfile(friend.uuid, friend.name))
                        retries[friend.uuid] = Retry(attempt, System.currentTimeMillis() + FALLBACK_RETRY_MILLIS)
                    }
                }
            } finally {
                fetching.remove(friend.uuid)
            }
        }
    }
}
