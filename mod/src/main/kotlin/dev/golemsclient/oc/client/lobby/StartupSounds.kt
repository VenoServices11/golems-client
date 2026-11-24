// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.InactiveProfiler
import dev.golemsclient.oc.mixin.client.access.SimplePreparableReloadListenerInvoker

import net.minecraft.util.Util

object StartupSounds {
    @Volatile
    private var waiting = false

    @Volatile
    private var loaded = false

    fun prepare() {
        if (!StartupOverlay.enabled()) return
        runCatching { Files.createDirectories(Paths.get("oneconfig", "sounds", "assets", "oneconfig", "sounds")) }
            .onFailure { LOBBY_LOGGER.warn("Could not prepare the OneConfig sound pack folder", it) }
    }

    fun gameLoaded() {
        loaded = true
        if (waiting) {
            waiting = false
            reloadSounds(Minecraft.getInstance())
        }
    }

    @JvmStatic
    fun reload(mc: Minecraft, original: Operation<CompletableFuture<Void>>): CompletableFuture<Void> {
        if (!StartupOverlay.enabled()) return original.call(mc)
        if (loaded) reloadSounds(mc) else waiting = true
        return CompletableFuture.completedFuture(null)
    }

    private fun reloadSounds(mc: Minecraft) {
        val listener = mc.soundManager as SimplePreparableReloadListenerInvoker
        val manager: ResourceManager = mc.resourceManager
        CompletableFuture
            .supplyAsync({ listener.`golems$prepare`(manager, InactiveProfiler.INSTANCE) }, Util.ioPool())
            .thenAcceptAsync({ listener.`golems$apply`(it, manager, InactiveProfiler.INSTANCE) }, LobbyCompat.mainExecutor(mc))
            .exceptionally {
                LOBBY_LOGGER.warn("Could not reload sounds after downloading them", it)
                null
            }
    }
}
