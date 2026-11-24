package dev.golemsclient.render;

import dev.golemsclient.GolemsClient;

/**
 * Golem's Client's loading screen, drawn over Minecraft's (whose logo and bar are skipped): the red G and the
 * Golem's Client name where Mojang's logo was, a thin red progress bar, and a tip under it that changes every few
 * seconds. Everything fades with the loading screen.
 */
public final class LoadingScreen {
    /** The tips, from assets/golemsclient/texts/tips.txt (one per line). */
    private static final String[] TIPS = readTips();
    private static final long TIP_EVERY_MS = 4500L;
    private static final int TEXT = 0xFFEDEDEF;
    private static final int TEXT_SECONDARY = 0xFF9A9AA2;
    private static final int TRACK = 0xFF26262B;

    private static final long STARTED = System.currentTimeMillis();

    private static float firstHalfX = Float.NaN;

    private LoadingScreen() {
    }

    /**
     * Where one half of Mojang's logo would have gone, and how visible (0..1). The logo is drawn in two halves, left
     * then right; on the right half, the G and the name are drawn centred over the whole logo's place.
     */
    public static void logoHalf(int x, int y, int width, int height, float alpha) {
        if (Float.isNaN(firstHalfX)) {
            firstHalfX = x;
            return;
        }
        float x0 = Math.min(firstHalfX, x);
        float x1 = Math.max(firstHalfX, x) + width;
        firstHalfX = Float.NaN;
        if (alpha <= 0.01f) return;

        float h = height;
        float mid = y + h / 2f;
        float g = h * 0.95f;
        float nameH = h * 0.42f;
        float nameW = nameH * (1130f / 128f);
        float gap = h * 0.22f;
        float left = (x0 + x1) / 2f - (g + gap + nameW) / 2f;
        Overlay.image("/assets/golemsclient/textures/logo.png", left, mid - g / 2f, left + g, mid + g / 2f, alpha(0xFFFFFFFF, alpha));
        float nx = left + g + gap;
        Overlay.image("/assets/golemsclient/textures/wordmark.png", nx, mid - nameH / 2f, nx + nameW, mid + nameH / 2f,
                alpha(TEXT, alpha));
    }

    /** The progress bar's place, how far loading is (0..1) and the bar's opacity (0..1): the bar and a tip. */
    public static void progress(int x0, int y0, int x1, int y1, float progress, float opacity) {
        if (opacity <= 0.01f) return;
        float cx = (x0 + x1) / 2f;
        float cy = (y0 + y1) / 2f;

        // a thin rounded track filling with red
        float barW = (x1 - x0) * 0.7f;
        float bx0 = cx - barW / 2f;
        float th = 1.5f;
        Overlay.rect(bx0, cy - th, bx0 + barW, cy + th, th, alpha(TRACK, opacity));
        float fill = barW * Math.max(0f, Math.min(1f, progress));
        if (fill > th * 2f) Overlay.rect(bx0, cy - th, bx0 + fill, cy + th, th, alpha(GolemsClient.ACCENT, opacity));

        // the tip, changing every few seconds with a short fade
        long since = System.currentTimeMillis() - STARTED;
        int index = (int) ((since / TIP_EVERY_MS) % TIPS.length);
        float phase = (since % TIP_EVERY_MS) / (float) TIP_EVERY_MS;
        float tipA = Math.min(1f, Math.min(phase, 1f - phase) * 8f);
        Overlay.text(Overlay.Face.REGULAR, TIPS[index], cx, cy + 9f, 8f, alpha(TEXT_SECONDARY, opacity * tipA), Overlay.Align.CENTER);
    }

    private static String[] readTips() {
        try (var in = LoadingScreen.class.getResourceAsStream("/assets/golemsclient/texts/tips.txt")) {
            if (in != null) {
                var lines = new java.util.ArrayList<>(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList());
                java.util.Collections.shuffle(lines);
                if (!lines.isEmpty()) return lines.toArray(String[]::new);
            }
        } catch (java.io.IOException ignored) {
        }
        return new String[]{"Golem's Client is made by veno15"};
    }

    private static int alpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        int out = Math.round(base * Math.max(0f, Math.min(1f, a)));
        return (out << 24) | (argb & 0xFFFFFF);
    }
}
