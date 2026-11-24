// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.server.RegistryLayer
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader

class LobbyRegistries private constructor(
    val access: RegistryAccess.Frozen,
    private val staticTags: List<Registry.PendingTags<*>>,
) {
    fun bindStaticTags() {
        if (Minecraft.getInstance().level != null) return
        staticTags.forEach { it.apply() }
    }

    companion object {
        @Volatile
        private var vanillaOnly = false

        private val PRESETS = setOf(
            Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
            Registries.WORLD_PRESET,
            Registries.FLAT_LEVEL_GENERATOR_PRESET,
        )

        fun load(): LobbyRegistries {
            if (!vanillaOnly) {
                try {
                    return load(trustedPacks(), true)
                } catch (error: Throwable) {
                    vanillaOnly = true
                    LOBBY_LOGGER.warn("Lobby registries failed to load with mod data, retrying with vanilla data only", error)
                }
            }
            return load(listOf<PackResources>(ServerPacksSource.createVanillaPackSource()), false)
            
        }

        private fun trustedPacks(): List<PackResources> {
            val mc = Minecraft.getInstance()
            val packs = ServerPacksSource.createPackRepository(mc.gameDirectory.toPath().resolve("golemsclient/lobby/datapacks"), mc.directoryValidator())
            packs.reload()
            packs.setSelected(listOf("vanilla"))
            return packs.openAllSelected()
        }

        private fun load(sources: List<PackResources>, full: Boolean): LobbyRegistries {
            return MultiPackResourceManager(PackType.SERVER_DATA, sources).use { resources ->
                val layers = RegistryLayer.createRegistryAccess()
                val staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC))
                val worldLayer = RegistryLayer.WORLDGEN
                val allWorld = RegistryDataLoader.WORLDGEN_REGISTRIES
                
                val worldRegistries = if (full) allWorld else allWorld.filter { it.key() !in PRESETS }
                val dimensionRegistries = if (full) RegistryDataLoader.DIMENSION_REGISTRIES else emptyList()
                val worldContext = layers.getAccessForLoading(worldLayer)
                val lookups: List<HolderLookup.RegistryLookup<*>> = TagLoader.buildUpdatedLookups(worldContext, staticTags)
                val world = RegistryDataLoader.load(resources, lookups, worldRegistries)
                val dimensionContext = lookups + world.listRegistries().toList()
                val dimensions = RegistryDataLoader.load(resources, dimensionContext, dimensionRegistries)
                
                val merged = layers.replaceFrom(worldLayer, world, dimensions)
                LobbyRegistries(merged.compositeAccess(), if (full) staticTags else emptyList())
            }
        }

    }
}
