package dev.golemsclient.render;

import dev.golemsclient.GolemsClient;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inter (Regular to Bold) and JetBrains Mono, rasterised with stb_truetype at exactly the pixel size they are drawn
 * at (one glyph sheet per face and size, made when first needed), so text is sharp at every window size and GUI
 * scale, on OpenGL and Vulkan.
 */
final class Fonts {
    /** Latin-1 plus a few typographic extras. */
    private static final int FIRST = 32, LAST = 255;
    private static final int[] EXTRA = {0x2022, 0x2192, 0x2190, 0x2026, 0x2018, 0x2019, 0x201C, 0x201D, 0x2013, 0x2014, 0x2122,
            0x2715, 0x2713};

    /** One face at one pixel size. */
    static final class Sheet {
        final GuiTexture texture;
        final STBTTPackedchar.Buffer chars;
        final Map<Integer, STBTTPackedchar.Buffer> extra = new java.util.HashMap<>();
        final int size;
        final int width, height;
        final float ascent;

        Sheet(GuiTexture texture, STBTTPackedchar.Buffer chars, int size, int width, int height, float ascent) {
            this.texture = texture;
            this.chars = chars;
            this.size = size;
            this.width = width;
            this.height = height;
            this.ascent = ascent;
        }

        /** The quad for a character at pen (x, y baseline); advances x. False if this sheet doesn't have it. */
        boolean quad(int cp, FloatBuffer x, FloatBuffer y, STBTTAlignedQuad q) {
            if (cp >= FIRST && cp <= LAST) {
                STBTruetype.stbtt_GetPackedQuad(chars, width, height, cp - FIRST, x, y, q, false);
                return true;
            }
            STBTTPackedchar.Buffer one = extra.get(cp);
            if (one == null) return false;
            STBTruetype.stbtt_GetPackedQuad(one, width, height, 0, x, y, q, false);
            return true;
        }

        float advance(int cp) {
            if (cp >= FIRST && cp <= LAST) return chars.get(cp - FIRST).xadvance();
            STBTTPackedchar.Buffer one = extra.get(cp);
            return one != null ? one.get(0).xadvance() : chars.get('?' - FIRST).xadvance();
        }
    }

    private static final Map<Overlay.Face, ByteBuffer> FILES = new EnumMap<>(Overlay.Face.class);
    private static final Map<Overlay.Face, STBTTFontinfo> INFO = new EnumMap<>(Overlay.Face.class);
    private static final Map<Long, Sheet> SHEETS = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Sheet> eldest) {
            if (size() <= 48) return false;
            Textures.drop(eldest.getValue().texture);
            return true;
        }
    };
    private static boolean broken;

    private Fonts() {
    }

    /** The sheet for this face at px real pixels, made if needed; null if the font can't be read. */
    static Sheet sheet(Overlay.Face face, int px) {
        if (broken) return null;
        px = Math.max(5, Math.min(160, px));
        long key = ((long) face.ordinal() << 32) | px;
        Sheet s = SHEETS.get(key);
        if (s != null) return s;
        try {
            s = build(face, px);
        } catch (Throwable t) {
            GolemsClient.LOGGER.error("Couldn't make the {} font at {}px", face, px, t);
            broken = true;
            return null;
        }
        SHEETS.put(key, s);
        return s;
    }

    private static Sheet build(Overlay.Face face, int px) throws Exception {
        ByteBuffer ttf = file(face);
        STBTTFontinfo info = INFO.get(face);
        int over = px < 24 ? 2 : 1;
        int side = px <= 16 ? 256 : px <= 40 ? 512 : px <= 80 ? 1024 : 2048;
        while (true) {
            ByteBuffer alpha = MemoryUtil.memCalloc(side * side);
            STBTTPackContext pc = STBTTPackContext.malloc();
            try {
                STBTruetype.stbtt_PackBegin(pc, alpha, side, side, 0, 1, MemoryUtil.NULL);
                STBTruetype.stbtt_PackSetOversampling(pc, over, 1);
                STBTTPackedchar.Buffer chars = STBTTPackedchar.malloc(LAST - FIRST + 1);
                boolean ok = STBTruetype.stbtt_PackFontRange(pc, ttf, 0, px, FIRST, chars);
                Map<Integer, STBTTPackedchar.Buffer> extra = new java.util.HashMap<>();
                for (int cp : EXTRA) {
                    if (STBTruetype.stbtt_FindGlyphIndex(info, cp) == 0) continue;
                    STBTTPackedchar.Buffer one = STBTTPackedchar.malloc(1);
                    if (STBTruetype.stbtt_PackFontRange(pc, ttf, 0, px, cp, one)) extra.put(cp, one);
                    else one.free();
                }
                STBTruetype.stbtt_PackEnd(pc);
                if (!ok) {
                    chars.free();
                    extra.values().forEach(STBTTPackedchar.Buffer::free);
                    if (side >= 4096) throw new IllegalStateException("glyphs don't fit");
                    side *= 2;
                    continue;
                }
                // white glyphs, coverage in alpha
                ByteBuffer rgba = MemoryUtil.memAlloc(side * side * 4);
                try {
                    for (int i = 0; i < side * side; i++) {
                        rgba.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put(alpha.get(i));
                    }
                    rgba.flip();
                    GuiTexture tex = new GuiTexture("font " + face + " " + px, rgba, side, side, false);
                    float ascent;
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer a = stack.mallocInt(1), d = stack.mallocInt(1), g = stack.mallocInt(1);
                        STBTruetype.stbtt_GetFontVMetrics(info, a, d, g);
                        ascent = a.get(0) * STBTruetype.stbtt_ScaleForPixelHeight(info, px);
                    }
                    Sheet s = new Sheet(tex, chars, px, side, side, ascent);
                    s.extra.putAll(extra);
                    return s;
                } finally {
                    MemoryUtil.memFree(rgba);
                }
            } finally {
                pc.free();
                MemoryUtil.memFree(alpha);
            }
        }
    }

    private static ByteBuffer file(Overlay.Face face) throws Exception {
        ByteBuffer buf = FILES.get(face);
        if (buf != null) return buf;
        String name = switch (face) {
            case REGULAR -> "Inter-Regular.ttf";
            case MEDIUM -> "Inter-Medium.ttf";
            case SEMIBOLD -> "Inter-SemiBold.ttf";
            case BOLD -> "Inter-Bold.ttf";
            case MONO -> "JetBrainsMono-Medium.ttf";
        };
        byte[] data;
        try (InputStream in = Fonts.class.getResourceAsStream("/assets/golemsclient/fonts/" + name)) {
            if (in == null) throw new IllegalStateException("missing font " + name);
            data = in.readAllBytes();
        }
        // stb reads the font from this buffer for as long as the game runs: it is never freed
        buf = MemoryUtil.memAlloc(data.length);
        buf.put(data).flip();
        STBTTFontinfo info = STBTTFontinfo.malloc();
        if (!STBTruetype.stbtt_InitFont(info, buf)) throw new IllegalStateException("can't read font " + name);
        FILES.put(face, buf);
        INFO.put(face, info);
        return buf;
    }
}
