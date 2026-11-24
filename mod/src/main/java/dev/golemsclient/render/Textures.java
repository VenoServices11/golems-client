package dev.golemsclient.render;

import dev.golemsclient.GolemsClient;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Golem's Client's textures: pictures from the mod's jar (made at the size they are drawn, so they stay crisp),
 * pictures made at run time from bytes (server icons, album art, world icons) under a key, and the anti-aliased
 * corner pieces rounded shapes are made of. Render thread only.
 */
public final class Textures {
    /** A decoded picture: RGBA bytes, straight alpha. */
    record Decoded(int w, int h, byte[] rgba) { }

    private static final Map<String, Decoded> DECODED = new HashMap<>();
    private static final Map<String, GuiTexture> SIZED = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GuiTexture> eldest) {
            if (size() > 96) {
                RELEASE.add(eldest.getValue());
                return true;
            }
            return false;
        }
    };
    private static final Map<String, GuiTexture> MADE = new HashMap<>();
    private static final java.util.Set<String> FAILED = new java.util.HashSet<>();
    private static final Map<Long, GuiTexture> CORNERS = new HashMap<>();
    private static final List<GuiTexture> RELEASE = new ArrayList<>();
    private static final List<GuiTexture> RELEASE_NEXT = new ArrayList<>();

    private Textures() {
    }

    // ------------------------------------------------------------------ pictures from the jar

    /** A PNG from the jar, made at (about) w x h real pixels; null if it can't be read. */
    static GuiTexture resource(String path, int w, int h) {
        Decoded d = DECODED.get(path);
        if (d == null) {
            if (FAILED.contains(path)) return null;
            d = decodeResource(path);
            if (dev.golemsclient.DevShots.ON) GolemsClient.LOGGER.info("[DevShots] picture {} -> {}", path, d == null ? "null" : d.w() + "x" + d.h());
            if (d == null) {
                FAILED.add(path);
                return null;
            }
            DECODED.put(path, d);
        }
        // never bigger than the picture itself; sizes rounded so a resize doesn't make dozens of copies
        int tw = Math.max(1, Math.min(d.w(), w)), th = Math.max(1, Math.min(d.h(), h));
        if (tw < d.w()) {
            th = Math.max(1, Math.round(d.h() * (tw / (float) d.w())));
        } else {
            th = d.h();
        }
        String key = path + "@" + tw + "x" + th;
        GuiTexture t = SIZED.get(key);
        if (t == null) {
            byte[] px = tw == d.w() && th == d.h() ? d.rgba() : shrink(d, tw, th);
            t = upload(key, px, tw, th, false);
            SIZED.put(key, t);
        }
        return t;
    }

    private static Decoded decodeResource(String path) {
        try (InputStream in = Textures.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return decode(in.readAllBytes());
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't read {}", path, e);
            return null;
        }
    }

    // ------------------------------------------------------------------ pictures made at run time

    /**
     * A picture (PNG or JPEG bytes) as a texture, made the first time this key is asked for; null if it can't be read.
     * corner (0..0.5 of the side) rounds its corners off.
     */
    public static GuiTexture image(String key, byte[] bytes, boolean pixelated, float corner) {
        if (MADE.containsKey(key)) return MADE.get(key);
        GuiTexture t = null;
        Decoded d = bytes == null || bytes.length == 0 ? null : decode(bytes);
        if (d != null) {
            byte[] px = d.rgba();
            int w = d.w(), h = d.h();
            // small pixel-art (server icons are 64x64) is scaled up first so rounded corners stay smooth
            if (pixelated && corner > 0f && w <= 64 && h <= 64) {
                int k = 4;
                byte[] big = new byte[w * k * h * k * 4];
                for (int y = 0; y < h * k; y++) {
                    for (int x = 0; x < w * k; x++) System.arraycopy(px, ((y / k) * w + x / k) * 4, big, (y * w * k + x) * 4, 4);
                }
                px = big;
                w *= k;
                h *= k;
                pixelated = false;
            } else if (!pixelated && w > 256) {
                int tw = 256, th = Math.max(1, Math.round(h * (256f / w)));
                px = shrink(new Decoded(w, h, px), tw, th);
                w = tw;
                h = th;
            }
            if (corner > 0f) roundCorners(px, w, h, corner * Math.min(w, h));
            t = upload(key, px, w, h, pixelated);
        }
        MADE.put(key, t);
        return t;
    }

    /** True when a picture was already made under this key (even if it couldn't be read). */
    public static boolean has(String key) {
        return MADE.containsKey(key);
    }

    /** Lets go of a picture made with {@link #image}; deleted once nothing can still be drawing it. */
    public static void release(String key) {
        GuiTexture t = MADE.remove(key);
        if (t != null) RELEASE.add(t);
    }

    /** Frees a texture once nothing can still be drawing it. */
    static void drop(GuiTexture t) {
        if (t != null) RELEASE.add(t);
    }

    /** Called once per frame before drawing: frees what was let go two frames ago. */
    static void frame() {
        for (GuiTexture t : RELEASE_NEXT) {
            try {
                t.close();
            } catch (Exception ignored) {
            }
        }
        RELEASE_NEXT.clear();
        RELEASE_NEXT.addAll(RELEASE);
        RELEASE.clear();
    }

    // ------------------------------------------------------------------ corners

    /**
     * The top-left quarter of a disc (thickness 0) or a ring (thickness > 0), r x r real pixels, anti-aliased: what
     * rounded rectangles, outlines and circles are made of.
     */
    static GuiTexture corner(int r, int thickness) {
        r = Math.max(1, Math.min(256, r));
        thickness = Math.max(0, Math.min(r, thickness));
        long key = ((long) r << 16) | thickness;
        GuiTexture t = CORNERS.get(key);
        if (t != null) return t;
        byte[] px = new byte[r * r * 4];
        float outer = r, inner = thickness > 0 ? r - thickness : -1f;
        int ss = 4; // 4x4 samples per pixel
        for (int y = 0; y < r; y++) {
            for (int x = 0; x < r; x++) {
                int hits = 0;
                for (int sy = 0; sy < ss; sy++) {
                    for (int sx = 0; sx < ss; sx++) {
                        float dx = r - (x + (sx + 0.5f) / ss), dy = r - (y + (sy + 0.5f) / ss);
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        if (d <= outer && d > inner) hits++;
                    }
                }
                int i = (y * r + x) * 4;
                px[i] = px[i + 1] = px[i + 2] = (byte) 0xFF;
                px[i + 3] = (byte) Math.round(255f * hits / (ss * ss));
            }
        }
        t = upload("corner " + r + "/" + thickness, px, r, r, false);
        CORNERS.put(key, t);
        return t;
    }

    // ------------------------------------------------------------------ helpers

    static Decoded decode(byte[] bytes) {
        // PNGs through Minecraft's own reader; anything else (JPEG album art) through stb_image
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            try (com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(
                    com.mojang.blaze3d.platform.NativeImage.Format.RGBA, new java.io.ByteArrayInputStream(bytes))) {
                return copy(img);
            } catch (Exception e) {
                // fall through to stb
            }
        }
        return decodeStb(bytes);
    }

    private static Decoded copy(com.mojang.blaze3d.platform.NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null;
        byte[] out = new byte[w * h * 4];
        MemoryUtil.memByteBuffer(img.getPointer(), out.length).get(out);
        return new Decoded(w, h, out);
    }

    private static Decoded decodeStb(byte[] bytes) {
        ByteBuffer file = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            file.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(file, w, h, c) || w.get(0) <= 0 || h.get(0) <= 0 || w.get(0) > 4096
                    || h.get(0) > 4096) {
                GolemsClient.LOGGER.warn("Couldn't read a picture ({} bytes): {}", bytes.length, STBImage.stbi_failure_reason());
                return null;
            }
            ByteBuffer pixels = STBImage.stbi_load_from_memory(file, w, h, c, 4);
            if (pixels == null) return null;
            try {
                byte[] out = new byte[w.get(0) * h.get(0) * 4];
                pixels.get(out);
                return new Decoded(w.get(0), h.get(0), out);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } catch (Exception | LinkageError e) {
            GolemsClient.LOGGER.warn("Couldn't read a picture", e);
            return null;
        } finally {
            MemoryUtil.memFree(file);
        }
    }

    private static GuiTexture upload(String label, byte[] rgba, int w, int h, boolean pixelated) {
        ByteBuffer buf = MemoryUtil.memAlloc(rgba.length);
        try {
            buf.put(rgba).flip();
            return new GuiTexture(label, buf, w, h, pixelated);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    /** Area-average shrink with premultiplied alpha, so edges don't get dark fringes. */
    private static byte[] shrink(Decoded d, int tw, int th) {
        byte[] src = d.rgba(), out = new byte[tw * th * 4];
        float sx = d.w() / (float) tw, sy = d.h() / (float) th;
        for (int y = 0; y < th; y++) {
            int y0 = (int) (y * sy), y1 = Math.max(y0 + 1, Math.min(d.h(), (int) Math.ceil((y + 1) * sy)));
            for (int x = 0; x < tw; x++) {
                int x0 = (int) (x * sx), x1 = Math.max(x0 + 1, Math.min(d.w(), (int) Math.ceil((x + 1) * sx)));
                long r = 0, g = 0, b = 0, a = 0, n = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        int i = (yy * d.w() + xx) * 4;
                        int al = src[i + 3] & 0xFF;
                        r += (src[i] & 0xFF) * al;
                        g += (src[i + 1] & 0xFF) * al;
                        b += (src[i + 2] & 0xFF) * al;
                        a += al;
                        n++;
                    }
                }
                int o = (y * tw + x) * 4;
                if (a > 0) {
                    out[o] = (byte) (r / a);
                    out[o + 1] = (byte) (g / a);
                    out[o + 2] = (byte) (b / a);
                }
                out[o + 3] = (byte) (a / n);
            }
        }
        return out;
    }

    /** Fades the corners out to a rounded shape of radius rad pixels. */
    private static void roundCorners(byte[] px, int w, int h, float rad) {
        if (rad < 1f) return;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float cx = x < rad ? rad : x >= w - rad ? w - rad : -1, cy = y < rad ? rad : y >= h - rad ? h - rad : -1;
                if (cx < 0 || cy < 0) continue;
                float dx = (x + 0.5f) - cx, dy = (y + 0.5f) - cy;
                float cover = Math.max(0f, Math.min(1f, rad - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f));
                int i = (y * w + x) * 4 + 3;
                px[i] = (byte) Math.round((px[i] & 0xFF) * cover);
            }
        }
    }
}
