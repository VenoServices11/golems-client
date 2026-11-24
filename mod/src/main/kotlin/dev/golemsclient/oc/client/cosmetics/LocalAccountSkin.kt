// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.cosmetics

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import dev.golemsclient.oc.client.PolyPlusClient
import dev.golemsclient.oc.client.utils.ClientPlatform
import java.util.UUID
import net.minecraft.world.entity.player.PlayerSkin
import java.util.function.Supplier

object LocalAccountSkin {
    private val LOGGER = LogManager.getLogger("Golem's Client")
    private val RETRY_MILLIS = longArrayOf(5_000L, 30_000L, 120_000L, 600_000L)
    private const val TEXTURES = "textures"

    private class Resolved(val id: UUID, val profile: GameProfile)

    @Volatile
    private var resolved: Resolved? = null
    @Volatile
    private var bare: GameProfile? = null
    private var pendingId: UUID? = null
    private var fetching = false
    private var attempt = 0
    private var retryAt = 0L

    fun profile(): GameProfile {
        val id = ClientPlatform.localPlayerUuid()
        val name = ClientPlatform.localPlayerName()
        resolved?.let { if (it.id == id) return it.profile }
        launchProfile(id)?.let { if (hasTextures(it)) return remember(id, it) }
        request(id, name)
        return bare?.takeIf { it.id == id && it.name == name } ?: GameProfile(id, name).also { bare = it }
    }

    fun onAccountSwitched(id: UUID, profile: GameProfile?) {
        synchronized(this) {
            pendingId = null
            attempt = 0
            retryAt = 0L
            fetching = false
            resolved = profile?.takeIf { it.id == id && hasTextures(it) }?.let { Resolved(id, it) }
        }
    }

    private fun hasTextures(profile: GameProfile): Boolean = profile.properties.containsKey(TEXTURES)

    private fun remember(id: UUID, profile: GameProfile): GameProfile {
        synchronized(this) {
            resolved?.let { if (it.id == id) return it.profile }
            resolved = Resolved(id, profile)
        }
        return profile
    }

    private fun request(id: UUID, name: String) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (pendingId != id) {
                pendingId = id
                attempt = 0
                retryAt = 0L
                fetching = false
            }
            if (fetching || now < retryAt) return
            fetching = true
        }
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val fetched = runCatching { fetch(id, name) }
                .onFailure { LOGGER.debug("Could not fetch the profile for {}", id, it) }
                .getOrNull()
                ?.takeIf { it.id == id && hasTextures(it) }
            synchronized(this@LocalAccountSkin) {
                if (pendingId != id) return@launch
                fetching = false
                if (fetched != null) {
                    resolved = Resolved(id, fetched)
                } else {
                    retryAt = System.currentTimeMillis() + RETRY_MILLIS[attempt.coerceAtMost(RETRY_MILLIS.lastIndex)]
                    attempt++
                }
            }
        }
    }


    private fun launchProfile(id: UUID): GameProfile? = runCatching { Minecraft.getInstance().gameProfile }.getOrNull()?.takeIf { it.id == id }

    private fun launchTexture(profile: GameProfile) = Minecraft.getInstance()
        .services().sessionService()
        .getTextures(profile).skin()

    private fun fetch(id: UUID, name: String): GameProfile? = Minecraft.getInstance()
        .services().sessionService()
        .fetchProfile(id, true)?.profile()

    private var lookup: Pair<GameProfile, Supplier<PlayerSkin>>? = null

    fun skin(): PlayerSkin {
        val profile = profile()
        val current = lookup?.takeIf { it.first === profile }
            ?: (profile to Minecraft.getInstance().skinManager.createLookup(profile, false)).also { lookup = it }
        return current.second.get()
    }
}
