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

/**
 * Textures for the overlay: PNGs from the mod's jar (made once, kept), and images made at run time from bytes (server
 * icons, album art, world icons) under a key, which can be let go again. Render thread only.
 */
public final class Textures {
    private static final Map<String, Integer> CACHE = new HashMap<>();
    private static final Map<String, Integer> MADE = new HashMap<>();
    private static final java.util.List<Integer> RELEASE = new java.util.ArrayList<>();
    private static final java.util.Set<Integer> OURS = new java.util.HashSet<>();
    private static final java.util.Set<Integer> PREPARED = new java.util.HashSet<>();

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
        MADE.clear();
        OURS.clear();
        PREPARED.clear();
        RELEASE.clear();
    }

    /** An image (PNG or JPEG bytes) as a texture, made the first time this key is asked for; 0 if it can't be read. */
    public static int image(String key, byte[] bytes, boolean pixelated) {
        Integer made = MADE.get(key);
        if (made != null) return made;
        int tex = bytes == null || bytes.length == 0 ? 0 : upload(bytes, key, pixelated);
        MADE.put(key, tex);
        return tex;
    }

    /** True when an image was already made under this key (even if it couldn't be read). */
    public static boolean has(String key) {
        return MADE.containsKey(key);
    }

    /** Lets go of an image made with {@link #image}; it is deleted after the current frame is drawn. */
    public static void release(String key) {
        Integer tex = MADE.remove(key);
        if (tex != null && tex > 0) RELEASE.add(tex);
    }

    static void releasePending() {
        if (RELEASE.isEmpty()) return;
        for (int tex : RELEASE) {
            OURS.remove(tex);
            GL11.glDeleteTextures(tex);
        }
        RELEASE.clear();
    }

    /**
     * Minecraft's own textures (skins) are drawn by the overlay without Minecraft's sampler settings: make them crisp
     * and use only their first level, once per texture.
     */
    static void prepareForeign(int tex) {
        if (OURS.contains(tex) || !PREPARED.add(tex)) return;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
    }

    private static int load(String path) {
        byte[] png;
        try (InputStream in = Textures.class.getResourceAsStream(path)) {
            if (in == null) return 0;
            png = in.readAllBytes();
        } catch (Exception e) {
            return 0;
        }
        return upload(png, path, false);
    }

    private static int upload(byte[] png, String path, boolean pixelated) {
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
                if (pixelated) {
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                } else {
                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                }
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
                OURS.add(tex);
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
