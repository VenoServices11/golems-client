package dev.golemsclient.menu;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.golemsclient.GolemsClient;
import dev.golemsclient.media.MediaSession;
import dev.golemsclient.media.NowPlaying;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import dev.golemsclient.render.Textures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What Golem's Client's own screens (the main menu, the pause menu) share: a canvas 450 units tall and as wide as the
 * window's shape allows, cards, text and icons drawn smooth by the overlay, hover and open animations, clickable areas,
 * tooltips, toasts, your head, and the now-playing card. A subclass calls {@link #beginFrame} first in render and
 * {@link #endFrame} last, and records what can be clicked with {@link #hit}.
 */
abstract class MenuScreen extends net.minecraft.client.gui.screens.Screen {
    // the launcher's Iron Dark
    protected static final int TEXT = 0xFFEDEDEF;
    protected static final int TEXT_2 = 0xFF9A9AA2;
    protected static final int TEXT_3 = 0xFF6E6E76;
    protected static final int CARD = 0xD8141417;
    protected static final int CARD_HI = 0xE81C1C21;
    protected static final int LINE = 0x1FFFFFFF;
    protected static final int WELL = 0xFF0E0E10;
    protected static final int ACCENT = GolemsClient.ACCENT;
    protected static final int LIVE = 0xFF3DD68C;

    /** The canvas height in units. */
    protected static final float H = 450f;
    protected static final float PAD = 16f;

    protected record Hit(float x0, float y0, float x1, float y1, Runnable action) {
        boolean inside(float x, float y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    protected final Map<String, Float> anims = new HashMap<>();
    protected final List<Hit> hits = new ArrayList<>();
    protected float u = 1f;      // GUI pixels per unit
    protected float w = 800f;    // canvas width in units
    protected float mx, my;      // the mouse, in units
    protected long openedAt;
    protected long lastFrame;
    protected float dt;
    protected float fade = 1f;
    protected String toast;
    protected long toastAt;
    protected String tooltip;
    protected boolean pointer;
    private Supplier<PlayerSkin> skin;
    private int albumArtVersion = -1;

    protected MenuScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis();
    }

    /** Your skin (loads in the background; the default skin until then). */
    protected Supplier<PlayerSkin> skin() {
        return AccountSkin.lookup();
    }

    /** True while a panel covers the screen (no tooltips then). */
    protected boolean modalOpen() {
        return false;
    }

    /** Where toasts appear (units from the top). */
    protected float toastY() {
        return 48f;
    }

    /** Measures the canvas and the mouse, steps the animations, and forgets last frame's clickable areas. */
    protected void beginFrame(GuiGraphics g, int mouseX, int mouseY) {
        Overlay.begin(g);
        var window = minecraft.getWindow();
        u = window.getGuiScaledHeight() / H;
        w = window.getGuiScaledWidth() / u;
        mx = mouseX / u;
        my = mouseY / u;
        long now = System.currentTimeMillis();
        dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        fade = ease(Math.min(1f, (now - openedAt) / 450f));
        hits.clear();
        tooltip = null;
        pointer = false;
    }

    /** The toast and tooltip on top, and the hand cursor over anything clickable. */
    protected void endFrame(GuiGraphics g) {
        drawToast();
        drawTooltip();
        if (pointer) g.requestCursor(CursorTypes.POINTING_HAND);
        Overlay.end();
    }

    /** Runs the clickable area under the mouse (with a soft click); false if there is none. */
    protected boolean clickHit(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        float x = (float) event.x() / u, y = (float) event.y() / u;
        for (Hit h : hits) {
            if (h.inside(x, y)) {
                h.action().run();
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.35f));
                return true;
            }
        }
        return false;
    }

    protected void drawMusic(float x0, float y0, float x1, float y1) {
        card(x0, y0, x1, y1);
        MediaSession.Snapshot s = NowPlaying.get();
        float art = 30f, ax = x0 + 8f, ay = y0 + 8f;
        dev.golemsclient.render.GuiTexture tex = s != null && s.hasThumbnail() ? albumArt(s) : null;
        if (tex != null) {
            Overlay.texture(tex, ax * u, ay * u, (ax + art) * u, (ay + art) * u, 0f, 0f, 1f, 1f, c(0xFFFFFFFF));
        } else {
            rect(ax, ay, ax + art, ay + art, 4f, 0xFF1F1F24);
            Overlay.icon(s == null ? "volume" : "play", (ax + 9f) * u, (ay + 9f) * u, 12f * u, c(TEXT_3));
        }
        float tx = ax + art + 8f;
        float maxText = x1 - 58f - tx;
        if (s == null) {
            text(Face.SEMIBOLD, "Nothing playing", tx, y0 + 10f, 8f, TEXT, Align.LEFT);
            text(Face.REGULAR, fit(Face.REGULAR, "Play music in Spotify, YouTube or any app", 6.5f, x1 - 10f - tx),
                    tx, y0 + 22f, 6.5f, TEXT_2, Align.LEFT);
            return;
        }
        text(Face.SEMIBOLD, fit(Face.SEMIBOLD, s.title(), 8f, maxText), tx, y0 + 8f, 8f, TEXT, Align.LEFT);
        String by = s.artist() == null || s.artist().isBlank() ? "Now playing" : s.artist();
        text(Face.REGULAR, fit(Face.REGULAR, by, 6.5f, maxText), tx, y0 + 19.5f, 6.5f, TEXT_2, Align.LEFT);
        // progress
        float bx0 = tx, bx1 = x1 - 10f, by0 = y1 - 10f;
        rect(bx0, by0, bx1, by0 + 2f, 1f, 0x26FFFFFF);
        if (s.durationMs() > 0) {
            float p = clamp(NowPlaying.position(s) / (float) s.durationMs(), 0f, 1f);
            if (p > 0.005f) rect(bx0, by0, bx0 + (bx1 - bx0) * p, by0 + 2f, 1f, ACCENT);
        }
        // controls
        boolean playing = s.state() == MediaSession.PlaybackState.PLAYING;
        float cy = y0 + 17f;
        float pr = 9f, pcx = x1 - 22f;
        boolean overPlay = inside(pcx - pr, cy - pr, pcx + pr, cy + pr);
        float hp = anim("music:play", overPlay);
        Overlay.circle(pcx * u, cy * u, pr * u, c(mix(ACCENT, 0xFFF0676B, hp)));
        float is = 8f;
        Overlay.icon(playing ? "pause" : "play", (pcx - is / 2f + (playing ? 0f : 0.6f)) * u, (cy - is / 2f) * u, is * u, c(0xFFFFFFFF));
        hit(pcx - pr, cy - pr, pcx + pr, cy + pr, () -> NowPlaying.send(NowPlaying.Command.TOGGLE));
        if (overPlay) tooltip = playing ? "Pause" : "Play";
        smallIcon("next", pcx + pr + 4f, cy, "Next", () -> NowPlaying.send(NowPlaying.Command.NEXT));
        smallIcon("prev", pcx - pr - 12f, cy, "Previous", () -> NowPlaying.send(NowPlaying.Command.PREVIOUS));
    }

    protected dev.golemsclient.render.GuiTexture albumArt(MediaSession.Snapshot s) {
        if (s.thumbnailVersion() != albumArtVersion) {
            Textures.release("album:" + albumArtVersion);
            albumArtVersion = s.thumbnailVersion();
        }
        return Textures.image("album:" + albumArtVersion, s.thumbnail(), false, 0.14f);
    }

    protected void smallIcon(String icon, float x, float cy, String tip, Runnable action) {
        float s = 8f;
        boolean over = inside(x - 2f, cy - 7f, x + s + 2f, cy + 7f);
        float hv = anim("small:" + icon, over);
        Overlay.icon(icon, x * u, (cy - s / 2f) * u, s * u, c(mix(TEXT_2, TEXT, hv)));
        hit(x - 2f, cy - 7f, x + s + 2f, cy + 7f, action);
        if (over) tooltip = tip;
    }

    protected void smallButton(String icon, float cx, float cy, String tip, Runnable action, boolean on) {
        float r = 8f;
        boolean over = inside(cx - r, cy - r, cx + r, cy + r);
        float hv = anim("btn:" + icon, over);
        rect(cx - r, cy - r, cx + r, cy + r, 4f, on ? 0x33E5484D : mix(0x14FFFFFF, 0x2EFFFFFF, hv));
        float s = 9f;
        Overlay.icon(icon, (cx - s / 2f) * u, (cy - s / 2f) * u, s * u, c(on ? ACCENT : mix(TEXT_2, TEXT, hv)));
        hit(cx - r, cy - r, cx + r, cy + r, action);
        if (over) tooltip = tip;
    }

    /** A round icon button centred at (cx, cy); returns where the next one (to its left) goes. */
    protected float iconButton(String icon, float cx, float cy, String tip, Runnable action) {
        float r = 10f;
        boolean over = inside(cx - r, cy - r, cx + r, cy + r);
        float hv = anim("icon:" + icon, over);
        if (hv > 0.01f) Overlay.circle(cx * u, cy * u, r * u, c(alpha(0x1FFFFFFF, hv)));
        float s = 11f;
        Overlay.icon(icon, (cx - s / 2f) * u, (cy - s / 2f) * u, s * u, c(mix(TEXT_2, TEXT, hv)));
        hit(cx - r, cy - r, cx + r, cy + r, action);
        if (over) tooltip = tip;
        return cx - 2f * r - 4f;
    }

    /** Your face (with the hat layer), from your skin. */
    protected void drawHead(float x, float y, float s, float radius) {
        net.minecraft.resources.Identifier tex = skin().get().body().texturePath();
        Overlay.minecraft(tex, x * u, y * u, (x + s) * u, (y + s) * u, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, c(0xFFFFFFFF));
        Overlay.minecraft(tex, x * u, y * u, (x + s) * u, (y + s) * u, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, c(0xFFFFFFFF));
    }

    protected void letterTile(String name, float x, float y, float s) {
        rect(x, y, x + s, y + s, Math.min(6f, s * 0.2f), 0xFF26262B);
        String letter = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        float size = s * 0.5f;
        text(Face.BOLD, letter, x + s / 2f, y + s / 2f - size * 0.6f, size, TEXT_2, Align.CENTER);
    }

    protected void card(float x0, float y0, float x1, float y1) {
        card(x0, y0, x1, y1, 0f);
    }

    protected void card(float x0, float y0, float x1, float y1, float hover) {
        rect(x0, y0, x1, y1, 8f, mix(CARD, CARD_HI, hover));
        outline(x0, y0, x1, y1, 8f, mix(LINE, 0x33FFFFFF, hover));
    }

    protected void rect(float x0, float y0, float x1, float y1, float r, int argb) {
        Overlay.rect(x0 * u, y0 * u, x1 * u, y1 * u, r * u, c(argb));
    }

    protected void outline(float x0, float y0, float x1, float y1, float r, int argb) {
        Overlay.outline(x0 * u, y0 * u, x1 * u, y1 * u, r * u, c(argb), 1f / Math.max(1f, guiScale()));
    }

    protected void text(Face face, String s, float x, float y, float size, int argb, Align align) {
        Overlay.text(face, s, x * u, y * u, size * u, c(argb), align);
    }

    protected float textW(Face face, String s, float size) {
        return Overlay.width(face, s, size * u) / u;
    }

    /** Shortens text with "..." to fit maxW units. */
    protected String fit(Face face, String s, float size, float maxW) {
        if (s == null) return "";
        if (textW(face, s, size) <= maxW) return s;
        String cut = s;
        while (cut.length() > 1 && textW(face, cut + "...", size) > maxW) cut = cut.substring(0, cut.length() - 1);
        return cut.strip() + "...";
    }

    protected float guiScale() {
        return (float) minecraft.getWindow().getGuiScale();
    }

    protected void hit(float x0, float y0, float x1, float y1, Runnable action) {
        hits.add(new Hit(x0, y0, x1, y1, action));
        if (inside(x0, y0, x1, y1)) pointer = true;
    }

    protected boolean inside(float x0, float y0, float x1, float y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    /** Eases a 0..1 value toward on/off (hover, open), about a tenth of a second. */
    protected float anim(String id, boolean on) {
        float v = anims.getOrDefault(id, on && id.equals("mode") ? 1f : 0f);
        v = approach(v, on ? 1f : 0f, 14f);
        anims.put(id, v);
        return v;
    }

    protected float approach(float from, float to, float speed) {
        float k = 1f - (float) Math.exp(-dt * speed);
        float v = from + (to - from) * k;
        return Math.abs(to - v) < 0.002f ? to : v;
    }

    /** The opening fade applied to a colour. */
    protected int c(int argb) {
        return alpha(argb, fade);
    }

    protected static int alpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        return (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24) | (argb & 0xFFFFFF);
    }

    protected static int mix(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    protected static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    protected static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** "just now", "5 min ago", "2 h ago", "3 days ago" for a time in milliseconds. */
    protected static String ago(long millis) {
        long s = Math.max(0L, (System.currentTimeMillis() - millis) / 1000L);
        if (millis <= 0L) return "a while ago";
        if (s < 60L) return "just now";
        if (s < 3600L) return (s / 60L) + " min ago";
        if (s < 86400L) return (s / 3600L) + " h ago";
        long d = s / 86400L;
        return d == 1L ? "yesterday" : d + " days ago";
    }

    protected static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toFile());
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't open {}", dir, e);
        }
    }

    protected void showToast(String message) {
        toast = message;
        toastAt = System.currentTimeMillis();
    }

    protected void drawTooltip() {
        if (tooltip == null || modalOpen()) return;
        float tw = textW(Face.MEDIUM, tooltip, 6.5f) + 12f;
        float x = clamp(mx - tw / 2f, 4f, w - tw - 4f), y = my + 14f;
        if (y + 16f > H) y = my - 22f;
        rect(x, y, x + tw, y + 15f, 4f, 0xF20E0E10);
        outline(x, y, x + tw, y + 15f, 4f, LINE);
        text(Face.MEDIUM, tooltip, x + tw / 2f, y + 7.5f - 6.5f * 0.6f, 6.5f, TEXT, Align.CENTER);
    }

    protected void drawToast() {
        if (toast == null) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > 3200L) {
            toast = null;
            return;
        }
        float a = Math.min(1f, Math.min(age / 150f, (3200L - age) / 300f));
        float tw = textW(Face.MEDIUM, toast, 7f) + 24f;
        float y0 = toastY();
        y0 += (1f - a) * -4f;
        rect(w / 2f - tw / 2f, y0, w / 2f + tw / 2f, y0 + 20f, 5f, alpha(CARD_HI, a));
        outline(w / 2f - tw / 2f, y0, w / 2f + tw / 2f, y0 + 20f, 5f, alpha(LINE, a));
        text(Face.MEDIUM, toast, w / 2f, y0 + 10f - 7f * 0.6f, 7f, alpha(TEXT, a), Align.CENTER);
    }
}
