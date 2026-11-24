package dev.golemsclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * A texture Golem's Client made itself (glyph atlases, rounded corners, icons, server icons...), uploaded through
 * Minecraft's renderer so it works on OpenGL and Vulkan alike. Smooth (linear) sampling unless pixelated.
 */
public final class GuiTexture extends AbstractTexture {
    private final int width;
    private final int height;

    /** From tightly packed RGBA bytes (straight alpha), width x height. The buffer is not kept. */
    public GuiTexture(String label, ByteBuffer rgba, int width, int height, boolean pixelated) {
        this.width = width;
        this.height = height;
        var device = RenderSystem.getDevice();
        this.texture = device.createTexture(() -> "Golem's Client " + label, 5, TextureFormat.RGBA8, width, height, 1, 1);
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(pixelated ? FilterMode.NEAREST : FilterMode.LINEAR);
        this.textureView = device.createTextureView(this.texture);
        try (NativeImage image = new NativeImage(width, height, false)) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(rgba), image.getPointer(), (long) width * height * 4L);
            device.createCommandEncoder().writeToTexture(this.texture, image);
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
