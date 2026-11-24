// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection
import net.minecraft.server.packs.resources.ResourceManager

import net.minecraft.client.gui.GuiGraphics


import net.minecraft.client.renderer.RenderPipelines

import net.minecraft.client.renderer.texture.MipmapStrategy

import net.minecraft.client.renderer.texture.ReloadableTexture
import net.minecraft.client.renderer.texture.TextureContents

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import dev.golemsclient.oc.client.utils.ClientPlatform
import kotlin.math.max

internal class StartupArt(val name: String, val height: Int, val width: Int) {
    val id: Identifier = Identifier.fromNamespaceAndPath("golemsclient", "startup/$name")
    val path = "/assets/golemsclient/startup/$name.png"
}

private fun openArt(path: String) = StartupArt::class.java.getResourceAsStream(path) ?: error("Missing startup art $path")

private class StartupTexture(id: Identifier, private val path: String) : ReloadableTexture(id) {
    override fun loadContents(resourceManager: ResourceManager): TextureContents {
        val image = openArt(path).use { NativeImage.read(it) }
        return TextureContents(image, TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0f))
    }
}

internal object StartupTextures {
    private val mark = listOf(StartupArt("mark_64", 64, 64), StartupArt("mark_128", 128, 128), StartupArt("mark_256", 256, 256))
    private val word = listOf(StartupArt("word_54", 54, 476), StartupArt("word_108", 108, 953), StartupArt("word_216", 216, 1905))
    val pixel = StartupArt("pixel", 1, 1)
    private var loaded = false

    fun mark(pixels: Float): StartupArt = mark.firstOrNull { it.height >= pixels } ?: mark.last()

    fun word(pixels: Float): StartupArt = word.firstOrNull { it.height >= pixels } ?: word.last()

    fun load(mc: Minecraft) {
        if (loaded) return
        loaded = true
        (mark + word + pixel).forEach { register(mc, it) }
    }

    fun release(mc: Minecraft) {
        if (!loaded) return
        loaded = false
        (mark + word + pixel).forEach {
            mc.textureManager.release(it.id)
        }
    }

    private fun register(mc: Minecraft, art: StartupArt) {
        mc.textureManager.registerAndLoad(art.id, StartupTexture(art.id, art.path))
    }
}

internal typealias StartupGraphics = GuiGraphics


internal class StartupCanvas(private val mc: Minecraft, private val g: StartupGraphics) {
    val width: Float = g.guiWidth().toFloat()
    val height: Float = g.guiHeight().toFloat()
    val pixelScale: Float = mc.window.width / max(width, 1f)

    fun drawScreenBelow(partial: Float) {
        val screen = ClientPlatform.currentScreen() ?: return
        screen.renderWithTooltipAndSubtitles(g, 0, 0, partial)
        
        raise()
    }

    fun raise() {
        g.nextStratum()
    }

    fun begin() {
    }

    fun end() {
    }

    fun quad(art: StartupArt, x: Float, y: Float, w: Float, h: Float, argb: Int) {
        if (w <= 0f || h <= 0f) return
        val texWidth = art.width
        val texHeight = art.height
        g.pose().pushMatrix()
        g.pose().translate(x, y)
        g.pose().scale(w / texWidth, h / texHeight)
        g.blit(RenderPipelines.GUI_TEXTURED, art.id, 0, 0, 0f, 0f, texWidth, texHeight, texWidth, texHeight, texWidth, texHeight, argb)
        g.pose().popMatrix()
    }

}
