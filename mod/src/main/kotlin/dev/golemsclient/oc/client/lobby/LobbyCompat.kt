// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import com.mojang.blaze3d.pipeline.RenderTarget
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.Executor
import net.minecraft.client.Camera
import java.net.InetAddress
import java.net.InetSocketAddress
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import dev.golemsclient.oc.mixin.client.access.ConnectionAccessor
import net.minecraft.client.renderer.LevelRenderer

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.PalettedContainerRO
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.Vec3
import com.mojang.serialization.Codec
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.Holder
import net.minecraft.world.level.chunk.PalettedContainerFactory

class LobbyContainers(private val factory: PalettedContainerFactory) {
    val blockCodec: Codec<PalettedContainer<BlockState>> get() = factory.blockStatesContainerCodec()
    val biomeCodec: Codec<PalettedContainerRO<Holder<Biome>>> get() = factory.biomeContainerCodec()

    fun emptyBlocks(): PalettedContainer<BlockState> = factory.createForBlockStates()

    fun emptySection() = LevelChunkSection(factory)
}


object LobbyCompat {
    @Volatile
    private var quiet = false

    fun feet(entity: Entity): BlockPos = entity.blockPosition()

    @JvmStatic
    fun quietConnection(): Boolean = quiet

    fun <T> quietly(block: () -> T): T {
        quiet = true
        try {
            return block()
        } finally {
            quiet = false
        }
    }

    fun offlineConnection(): Connection {
        val connection = Connection(PacketFlow.CLIENTBOUND)
        (connection as ConnectionAccessor).`golems$setAddress`(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        return connection
    }

    fun blockAt(point: Vec3): BlockPos = BlockPos.containing(point)

    fun blockAt(x: Double, y: Double, z: Double): BlockPos = BlockPos.containing(x, y, z)

    fun between(from: BlockPos, to: BlockPos): Iterable<BlockPos> = BlockPos.betweenClosed(from, to)

    fun mainExecutor(mc: Minecraft): Executor = mc

    fun gameTime(level: ClientLevel): Long = level.gameTime

    fun removeEntity(level: ClientLevel, entity: Entity) = level.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)

    fun mouseX(mc: Minecraft): Double = mc.mouseHandler.xpos()

    fun mouseY(mc: Minecraft): Double = mc.mouseHandler.ypos()

    fun sectionsDone(mc: Minecraft): Boolean = mc.levelRenderer.hasRenderedAllSections() && levelSink(mc).countRenderedSections() > 0

    fun renderedSections(mc: Minecraft): Int = levelSink(mc).countRenderedSections()

    private const val SODIUM_HAS_LIGHT_DATA = 2
    private const val MAX_LIGHT_PASSES = 64
    private const val VULKANMOD_LIGHT_READY: Byte = 2

    private val sodiumTracker: Pair<Method, Method>? by lazy {
        runCatching {
            val holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder")
            val tracker = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker")
            holder.getMethod("get", ClientLevel::class.java) to
                tracker.getMethod("onChunkStatusAdded", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        }.getOrNull()
    }

    private val vulkanModStatus: Pair<Field, Method>? by lazy {
        runCatching {
            val map = Class.forName("net.vulkanmod.render.chunk.ChunkStatusMap")
            map.getField("INSTANCE") to map.getMethod("setChunkStatus", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Byte::class.javaPrimitiveType)
        }.getOrNull()
    }

    // Sodium and VulkanMod only build sections once vanilla's light packet handler has run, which the lobby bypasses
    fun lightReady(level: ClientLevel, x: Int, z: Int) {
        sodiumTracker?.let { (get, added) -> added.invoke(get.invoke(null, level), x, z, SODIUM_HAS_LIGHT_DATA) }
        vulkanModStatus?.let { (instance, set) -> set.invoke(instance.get(null), x, z, VULKANMOD_LIGHT_READY) }
    }

    fun drainLight(level: ClientLevel) {
        val lightEngine = level.lightEngine
        var passes = 0
        while (lightEngine.hasLightWork() && passes++ < MAX_LIGHT_PASSES) lightEngine.runLightUpdates()
    }

    fun requeueChunks(mc: Minecraft, level: ClientLevel, centerX: Int, centerZ: Int, radius: Int) {
        val cache = level.chunkSource
        for (x in centerX - radius..centerX + radius) {
            for (z in centerZ - radius..centerZ + radius) {
                val chunk = cache.getChunk(x, z, ChunkStatus.FULL, false) ?: continue
                mc.levelRenderer.onChunkReadyToRender(chunk.pos)
                
            }
        }
    }

    fun containers(level: ClientLevel) = LobbyContainers(level.palettedContainerFactory())

    fun copySection(section: LevelChunkSection): LevelChunkSection = section.copy()

    fun levelSink(mc: Minecraft): LevelRenderer = mc.levelRenderer

    fun camera(renderer: GameRenderer): Camera = renderer.mainCamera

    fun mainTarget(renderer: GameRenderer): RenderTarget = Minecraft.getInstance().mainRenderTarget
    

    fun clockTime(level: ClientLevel): Long = level.dayTime
    

    fun setGameTime(level: ClientLevel, time: Long) = level.setTimeFromServer(time, level.dayTime, false)

    fun setDayTime(level: ClientLevel, time: Long) = level.setTimeFromServer(0L, time, false)
    

    fun chunkOf(pos: BlockPos): ChunkPos = ChunkPos(pos)

    fun chunkKey(x: Int, z: Int): Long = ChunkPos.asLong(x, z)
    

    fun blockEntityPos(chunk: ChunkPos, tag: CompoundTag): BlockPos = BlockEntity.getPosFromTag(chunk, tag)

    fun snapEntity(entity: Entity, pos: Vec3, yaw: Float) = entity.snapTo(pos.x, pos.y, pos.z, yaw, 0f)

    fun dataVersion(): Int = SharedConstants.getCurrentVersion().dataVersion().version()

    fun protocolVersion(): Int = SharedConstants.getCurrentVersion().protocolVersion()

    fun project(renderer: GameRenderer, point: Vec3, fov: Float): Vec3 = renderer.projectPointToScreen(point)

    fun cameraPosition(renderer: GameRenderer): Vec3 = camera(renderer).position()
}
