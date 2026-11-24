package dev.golemsclient.render;

import dev.golemsclient.GolemsClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/** PNGs from the mod's jar as OpenGL textures for the overlay (made once, kept). */
final class Textures {
    private static final Map<String, Integer> CACHE = new HashMap<>();

    private Textures() {
    }

    static int get(String path) {
        Integer cached = CACHE.get(path);
        if (cached != null) return cached;
        int tex = load(path);
        CACHE.put(path, tex);
        return tex;
    }

    static void forget() {
        CACHE.clear();
    }

    private static int load(String path) {
        byte[] png;
        try (InputStream in = Textures.class.getResourceAsStream(path)) {
            if (in == null) return 0;
            png = in.readAllBytes();
        } catch (Exception e) {
            return 0;
        }
        ByteBuffer file = MemoryUtil.memAlloc(png.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            file.put(png).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(file, w, h, c, 4);
            if (pixels == null) return 0;
            try {
                int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                int tex = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w.get(0), h.get(0), 0, GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE, pixels);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
                return tex;
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } catch (Exception | LinkageError e) {
            GolemsClient.LOGGER.warn("Couldn't load {}", path, e);
            return 0;
        } finally {
            MemoryUtil.memFree(file);
        }
    }
}
