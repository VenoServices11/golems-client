package dev.golemsclient.render;

import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGuiIO;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Inter (Regular to Bold) and JetBrains Mono, made at a ladder of pixel sizes for the window's current GUI scale, so
 * text is drawn from glyphs made near its size and stays sharp. Rebuilt when the GUI scale changes.
 */
final class Fonts {
    /** Text sizes in GUI pixels the ladder covers. */
    private static final float[] LADDER = {6f, 7f, 8f, 9f, 10f, 12f, 14f, 17f, 21f, 26f, 32f};

    private static final Map<Overlay.Face, ImFont[]> FONTS = new EnumMap<>(Overlay.Face.class);
    private static final Map<Overlay.Face, byte[]> FILES = new EnumMap<>(Overlay.Face.class);
    private static float builtFor = -1f;

    private Fonts() {
    }

    /** Makes the fonts for this many window pixels per GUI pixel; true when they were (re)made. */
    static boolean rebuildFor(ImGuiIO io, float pixelsPerGui) {
        float scale = Math.max(1f, Math.round(pixelsPerGui * 4f) / 4f);
        if (Math.abs(scale - builtFor) < 0.001f && !FONTS.isEmpty()) return false;
        builtFor = scale;
        ImFontAtlas atlas = io.getFonts();
        atlas.clear();
        FONTS.clear();
        try {
            for (Overlay.Face face : Overlay.Face.values()) {
                byte[] data = file(face);
                ImFont[] ladder = new ImFont[LADDER.length];
                for (int i = 0; i < LADDER.length; i++) ladder[i] = add(atlas, data, LADDER[i] * scale);
                FONTS.put(face, ladder);
            }
        } catch (Exception e) {
            atlas.clear();
            ImFont fallback = atlas.addFontDefault();
            for (Overlay.Face face : Overlay.Face.values()) FONTS.put(face, new ImFont[]{fallback});
        }
        atlas.build();
        return true;
    }

    /** The font of this face made closest to px window pixels (a little bigger is fine, much smaller is not). */
    static ImFont pick(Overlay.Face face, float px) {
        ImFont[] ladder = FONTS.get(face);
        if (ladder == null) return null;
        ImFont best = null;
        float bestScore = Float.MAX_VALUE;
        for (ImFont f : ladder) {
            float r = f.getFontSize() / Math.max(1f, px);
            float score = r >= 1f ? r - 1f : (1f / r - 1f) * 2.5f;
            if (score < bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return best;
    }

    static void forget() {
        FONTS.clear();
        builtFor = -1f;
    }

    private static byte[] file(Overlay.Face face) throws Exception {
        byte[] data = FILES.get(face);
        if (data != null) return data;
        String name = switch (face) {
            case REGULAR -> "Inter-Regular.ttf";
            case MEDIUM -> "Inter-Medium.ttf";
            case SEMIBOLD -> "Inter-SemiBold.ttf";
            case BOLD -> "Inter-Bold.ttf";
            case MONO -> "JetBrainsMono-Medium.ttf";
        };
        try (InputStream in = Fonts.class.getResourceAsStream("/assets/golemsclient/fonts/" + name)) {
            if (in == null) throw new IllegalStateException("missing font " + name);
            data = in.readAllBytes();
        }
        FILES.put(face, data);
        return data;
    }

    private static ImFont add(ImFontAtlas atlas, byte[] data, float px) {
        ImFontConfig cfg = new ImFontConfig();
        cfg.setOversampleH(px < 20f ? 2 : 1);
        cfg.setOversampleV(1);
        cfg.setPixelSnapH(false);
        ImFont f = atlas.addFontFromMemoryTTF(data, Math.max(6f, Math.round(px)), cfg);
        cfg.destroy();
        return f;
    }
}
