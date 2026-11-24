package dev.golemsclient.menu;

import dev.golemsclient.render.GuiTexture;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Screenshots page, laid out like OneClient's: sort by newest, oldest, largest or name, search by name, how many
 * there are and how much space they take, big or small tiles, and the folder. A click opens a screenshot large, with
 * buttons to open it, show it in its folder or delete it; arrow keys step through them.
 */
final class ScreenshotsPage {
    private static final String[] SORTS = {"Newest", "Oldest", "Largest", "Name"};

    private final MainMenu m;
    private int sort;
    private boolean small;
    private String search = "";
    private boolean typing;
    private float scroll;
    private float gridY0, gridY1;
    private ScreenshotStore.Shot open;
    private long confirmDeleteAt;
    private List<ScreenshotStore.Shot> visible = List.of();
    private long lastList;

    ScreenshotsPage(MainMenu menu) {
        this.m = menu;
    }

    void open() {
        scroll = 0f;
        open = null;
        typing = false;
        ScreenshotStore.refresh();
        lastList = System.currentTimeMillis();
    }

    void close() {
        open = null;
        typing = false;
        ScreenshotStore.release();
    }

    /** While the search box is being typed in or a screenshot is open, keys go here. */
    boolean typing() {
        return typing;
    }

    void draw() {
        // new screenshots show up while the page is open
        if (System.currentTimeMillis() - lastList > 4000L && open == null) {
            lastList = System.currentTimeMillis();
            ScreenshotStore.refresh();
        }
        float x0 = MenuScreen.PAD, x1 = m.w - MenuScreen.PAD, y0 = 50f + (1f - m.fade) * 8f, y1 = MenuScreen.H - 26f;
        m.card(x0, y0, x1, y1);
        if (open != null) {
            // one screenshot, large: nothing else on the page takes clicks
            drawViewer(x0, y0, x1, y1);
            return;
        }
        float x = x0 + 14f, y = y0 + 12f;
        m.text(Face.BOLD, "Screenshots", x, y, 10f, MenuScreen.TEXT, Align.LEFT);
        searchBox(x1 - 14f - 150f, y - 3f, x1 - 14f, y + 13f);
        y += 24f;

        // the toolbar
        float cx = x;
        for (int i = 0; i < SORTS.length; i++) {
            final int index = i;
            cx = chip(cx, y, SORTS[i], sort == i, () -> {
                sort = index;
                scroll = 0f;
            }) + 4f;
        }
        visible = sorted(ScreenshotStore.shots());
        long bytes = 0L;
        for (ScreenshotStore.Shot s : visible) bytes += s.size();
        float rx = x1 - 14f;
        rx = iconButton(rx, y, "folder", "Open the screenshots folder", () -> MenuScreen.openFolder(ScreenshotStore.dir()));
        rx = iconButton(rx - 4f, y, small ? "image" : "cube", small ? "Bigger tiles" : "Smaller tiles", () -> small = !small);
        String count = visible.size() + (visible.size() == 1 ? " screenshot" : " screenshots");
        m.text(Face.SEMIBOLD, count, rx - 8f, y + 1f, 7f, MenuScreen.TEXT, Align.RIGHT);
        m.text(Face.MONO, size(bytes), rx - 8f, y + 10f, 5.5f, MenuScreen.TEXT_3, Align.RIGHT);
        y += 26f;

        gridY0 = y;
        gridY1 = y1 - 10f;
        if (visible.isEmpty()) {
            drawEmpty((x0 + x1) / 2f, (gridY0 + gridY1) / 2f - 20f);
        } else {
            drawGrid(x, x1 - 14f);
        }
    }

    private void drawEmpty(float cx, float cy) {
        boolean searching = !search.isEmpty() && !ScreenshotStore.shots().isEmpty();
        Overlay.icon("camera", (cx - 7f) * m.u, (cy - 7f) * m.u, 14f * m.u, m.c(MenuScreen.TEXT_2));
        m.text(Face.SEMIBOLD, searching ? "Nothing matches" : "No screenshots yet", cx, cy + 14f, 9f, MenuScreen.TEXT, Align.CENTER);
        m.text(Face.REGULAR, searching ? "Try another name." : "Press your screenshot key in game and it shows up here.", cx, cy + 28f, 6.5f,
                MenuScreen.TEXT_2, Align.CENTER);
        if (!searching) {
            String label = "Open screenshots folder";
            float bw = m.textW(Face.MEDIUM, label, 7f) + 30f, bx = cx - bw / 2f, by = cy + 42f;
            boolean over = m.inside(bx, by, bx + bw, by + 18f);
            float hv = m.anim("shotsFolder", over);
            m.rect(bx, by, bx + bw, by + 18f, 5f, MenuScreen.mix(0x14FFFFFF, 0x2EFFFFFF, hv));
            m.outline(bx, by, bx + bw, by + 18f, 5f, MenuScreen.LINE);
            Overlay.icon("folder", (bx + 9f) * m.u, (by + 4.5f) * m.u, 9f * m.u, m.c(MenuScreen.TEXT));
            m.text(Face.MEDIUM, label, bx + 22f, by + 9f - 7f * 0.6f, 7f, MenuScreen.TEXT, Align.LEFT);
            m.hit(bx, by, bx + bw, by + 18f, () -> MenuScreen.openFolder(ScreenshotStore.dir()));
        }
    }

    private void drawGrid(float x0, float x1) {
        int cols = small ? 6 : 4;
        float gap = 6f, tw = (x1 - x0 - gap * (cols - 1)) / cols, th = tw * 9f / 16f;
        int rows = (visible.size() + cols - 1) / cols;
        float content = rows * (th + gap), view = gridY1 - gridY0;
        scroll = MenuScreen.clamp(scroll, 0f, Math.max(0f, content - view));
        int maxW = small ? 192 : 288;
        Overlay.clip(0f, gridY0 * m.u, m.w * m.u, gridY1 * m.u);
        for (int i = 0; i < visible.size(); i++) {
            ScreenshotStore.Shot s = visible.get(i);
            float tx = x0 + (i % cols) * (tw + gap), ty = gridY0 + (i / cols) * (th + gap) - scroll;
            if (ty + th < gridY0 || ty > gridY1) continue;
            m.rect(tx, ty, tx + tw, ty + th, 4f, 0x0FFFFFFF);
            GuiTexture t = ScreenshotStore.picture(s, maxW);
            if (t != null) Overlay.texture(t, tx * m.u, ty * m.u, (tx + tw) * m.u, (ty + th) * m.u, 0f, 0f, 1f, 1f, m.c(0xFFFFFFFF));
            boolean over = open == null && m.inside(tx, Math.max(ty, gridY0), tx + tw, Math.min(ty + th, gridY1));
            float hv = m.anim("shot:" + s.path(), over);
            if (hv > 0.01f) {
                // the name and date on a dark strip at the bottom
                m.rect(tx, ty + th - 22f, tx + tw, ty + th, 0f, MenuScreen.alpha(0xCC0E0E10, hv));
                m.text(Face.MEDIUM, m.fit(Face.MEDIUM, s.name(), 6.5f, tw - 12f), tx + 6f, ty + th - 19f, 6.5f, MenuScreen.alpha(MenuScreen.TEXT, hv),
                        Align.LEFT);
                m.text(Face.REGULAR, MenuScreen.ago(s.modified()), tx + 6f, ty + th - 9f, 5.5f, MenuScreen.alpha(MenuScreen.TEXT_2, hv), Align.LEFT);
                m.outline(tx, ty, tx + tw, ty + th, 4f, MenuScreen.alpha(0x80FFFFFF, hv));
            }
            if (over) m.hit(tx, Math.max(ty, gridY0), tx + tw, Math.min(ty + th, gridY1), () -> show(s));
        }
        Overlay.unclip();
        if (content > view) {
            float bar = view * view / content, at = (view - bar) * scroll / (content - view);
            m.rect(x1 + 6f, gridY0 + at, x1 + 7.5f, gridY0 + at + bar, 0.75f, 0x40FFFFFF);
        }
    }

    // ------------------------------------------------------------------ one screenshot, large

    private void show(ScreenshotStore.Shot s) {
        open = s;
        confirmDeleteAt = 0L;
        typing = false;
    }

    private void drawViewer(float x0, float y0, float x1, float y1) {
        float a = 1f;
        m.rect(x0, y0, x1, y1, 8f, 0xF00E0E10);
        // a click outside the picture and the bar closes it; the hits added before it win
        float px0 = x0 + 40f, px1 = x1 - 40f, py0 = y0 + 14f, py1 = y1 - 40f;
        GuiTexture big = ScreenshotStore.picture(open, 1280);
        GuiTexture small = ScreenshotStore.picture(open, 288);
        GuiTexture t = big != null ? big : small;
        if (t != null) {
            float iw = t.width(), ih = t.height(), k = Math.min((px1 - px0) / iw, (py1 - py0) / ih);
            float w = iw * k, h = ih * k, ix = (px0 + px1) / 2f - w / 2f, iy = (py0 + py1) / 2f - h / 2f;
            Overlay.texture(t, ix * m.u, iy * m.u, (ix + w) * m.u, (iy + h) * m.u, 0f, 0f, 1f, 1f, m.c(MenuScreen.alpha(0xFFFFFFFF, a)));
        }
        // stepping through them
        int index = visible.indexOf(open);
        if (index > 0) arrow(x0 + 20f, (py0 + py1) / 2f, false, () -> show(visible.get(index - 1)));
        if (index >= 0 && index < visible.size() - 1) arrow(x1 - 20f, (py0 + py1) / 2f, true, () -> show(visible.get(index + 1)));
        // the bar: name, when, size, and the buttons
        float by = y1 - 30f;
        m.text(Face.SEMIBOLD, m.fit(Face.SEMIBOLD, open.name(), 8f, (x1 - x0) * 0.4f), x0 + 16f, by + 2f, 8f, MenuScreen.TEXT, Align.LEFT);
        m.text(Face.REGULAR, MenuScreen.ago(open.modified()) + " · " + size(open.size()), x0 + 16f, by + 13f, 6f, MenuScreen.TEXT_2, Align.LEFT);
        float bx = x1 - 16f;
        ScreenshotStore.Shot shown = open;
        boolean confirming = System.currentTimeMillis() - confirmDeleteAt < 3000L;
        bx = button(bx, by, confirming ? "Click again to delete" : "Delete", confirming, () -> {
            if (System.currentTimeMillis() - confirmDeleteAt < 3000L) {
                int at = visible.indexOf(shown);
                if (ScreenshotStore.delete(shown)) {
                    m.showToast("Screenshot deleted.");
                    List<ScreenshotStore.Shot> left = sorted(ScreenshotStore.shots());
                    open = left.isEmpty() ? null : left.get(Math.min(at, left.size() - 1));
                }
                confirmDeleteAt = 0L;
            } else {
                confirmDeleteAt = System.currentTimeMillis();
            }
        }) - 6f;
        bx = button(bx, by, "Show in folder", false, () -> Util.getPlatform().openFile(shown.path().getParent().toFile())) - 6f;
        bx = button(bx, by, "Open", false, () -> Util.getPlatform().openFile(shown.path().toFile())) - 6f;
        button(bx, by, "Close", false, () -> open = null);
        // anything else on the page is under the viewer
        m.hit(x0, y0, x1, y1, () -> { });
    }

    private void arrow(float cx, float cy, boolean right, Runnable action) {
        boolean over = m.inside(cx - 10f, cy - 14f, cx + 10f, cy + 14f);
        float hv = m.anim("shotArrow:" + right, over);
        m.rect(cx - 10f, cy - 14f, cx + 10f, cy + 14f, 5f, MenuScreen.mix(0x14FFFFFF, 0x2EFFFFFF, hv));
        Overlay.icon(right ? "next" : "prev", (cx - 5f) * m.u, (cy - 5f) * m.u, 10f * m.u, m.c(MenuScreen.TEXT));
        m.hit(cx - 10f, cy - 14f, cx + 10f, cy + 14f, action);
    }

    /** Keys while a screenshot is open or the search is being typed in. True when used. */
    boolean key(int key) {
        if (open != null) {
            int index = visible.indexOf(open);
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) open = null;
            else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT && index > 0) show(visible.get(index - 1));
            else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT && index >= 0 && index < visible.size() - 1) show(visible.get(index + 1));
            else return false;
            return true;
        }
        if (!typing) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            typing = false;
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            scroll = 0f;
        }
        return true;
    }

    boolean type(int codepoint) {
        if (!typing || open != null) return false;
        if (Character.isISOControl(codepoint) || search.length() >= 40) return true;
        search += new String(Character.toChars(codepoint));
        scroll = 0f;
        return true;
    }

    boolean scroll(double mouseY, double amount) {
        float y = (float) mouseY / m.u;
        if (open != null || y < gridY0 || y > gridY1) return false;
        scroll -= (float) amount * 40f;
        return true;
    }

    // ------------------------------------------------------------------ pieces

    private List<ScreenshotStore.Shot> sorted(List<ScreenshotStore.Shot> all) {
        List<ScreenshotStore.Shot> out = new ArrayList<>();
        String q = search.toLowerCase(Locale.ROOT);
        for (ScreenshotStore.Shot s : all) if (q.isEmpty() || s.name().toLowerCase(Locale.ROOT).contains(q)) out.add(s);
        Comparator<ScreenshotStore.Shot> by = switch (sort) {
            case 1 -> Comparator.comparingLong(ScreenshotStore.Shot::modified);
            case 2 -> Comparator.comparingLong(ScreenshotStore.Shot::size).reversed();
            case 3 -> Comparator.comparing(s -> s.name().toLowerCase(Locale.ROOT));
            default -> Comparator.comparingLong(ScreenshotStore.Shot::modified).reversed();
        };
        out.sort(by);
        return out;
    }

    private void searchBox(float x0, float y0, float x1, float y1) {
        boolean over = m.inside(x0, y0, x1, y1);
        float focus = m.anim("shotsSearch", typing);
        m.rect(x0, y0, x1, y1, 4f, 0x0FFFFFFF);
        m.outline(x0, y0, x1, y1, 4f, MenuScreen.mix(MenuScreen.LINE, MenuScreen.ACCENT, focus));
        Overlay.icon("globe", (x0 + 5f) * m.u, (y0 + 4f) * m.u, 8f * m.u, m.c(MenuScreen.TEXT_3));
        boolean caret = typing && (System.currentTimeMillis() / 500L) % 2L == 0L;
        String shown = search.isEmpty() && !typing ? "Search screenshots" : search;
        float tx = x0 + 17f;
        m.text(Face.REGULAR, m.fit(Face.REGULAR, shown, 6.5f, x1 - tx - 6f), tx, y0 + 8f - 6.5f * 0.6f, 6.5f,
                search.isEmpty() && !typing ? MenuScreen.TEXT_3 : MenuScreen.TEXT, Align.LEFT);
        if (caret) {
            float cx = tx + m.textW(Face.REGULAR, search, 6.5f) + 1f;
            m.rect(cx, y0 + 3.5f, cx + 0.6f, y1 - 3.5f, 0f, MenuScreen.TEXT);
        }
        m.hit(x0, y0, x1, y1, () -> typing = true);
        // a click anywhere else stops typing (added last, so everything else wins)
        if (typing) m.hits.add(new MenuScreen.Hit(0f, 0f, m.w, MenuScreen.H, () -> typing = false));
    }

    private float chip(float x, float y, String label, boolean on, Runnable action) {
        float cw = m.textW(Face.MEDIUM, label, 7f) + 18f;
        boolean over = m.inside(x, y, x + cw, y + 16f);
        float hv = m.anim("shotsSort:" + label, over);
        m.rect(x, y, x + cw, y + 16f, 4f, on ? MenuScreen.ACCENT : MenuScreen.mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        if (!on) m.outline(x, y, x + cw, y + 16f, 4f, MenuScreen.LINE);
        m.text(Face.MEDIUM, label, x + cw / 2f, y + 8f - 7f * 0.6f, 7f, on ? 0xFFFFFFFF : MenuScreen.mix(MenuScreen.TEXT_2, MenuScreen.TEXT, hv),
                Align.CENTER);
        m.hit(x, y, x + cw, y + 16f, action);
        return x + cw;
    }

    /** A small square icon button ending at x1. Returns its left edge. */
    private float iconButton(float x1, float y, String icon, String tip, Runnable action) {
        float x0 = x1 - 18f;
        boolean over = m.inside(x0, y - 1f, x1, y + 17f);
        float hv = m.anim("shotsBtn:" + icon, over);
        m.rect(x0, y - 1f, x1, y + 17f, 4f, MenuScreen.mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        Overlay.icon(icon, (x0 + 4.5f) * m.u, (y + 3.5f) * m.u, 9f * m.u, m.c(MenuScreen.TEXT));
        m.hit(x0, y - 1f, x1, y + 17f, action);
        if (over) m.tooltip = tip;
        return x0;
    }

    /** A button ending at x1. Returns its left edge. */
    private float button(float x1, float y, String label, boolean danger, Runnable action) {
        float bw = m.textW(Face.MEDIUM, label, 7f) + 20f, x0 = x1 - bw;
        boolean over = m.inside(x0, y, x1, y + 18f);
        float hv = m.anim("shotsAct:" + label, over);
        int fill = danger ? MenuScreen.mix(MenuScreen.ACCENT, 0xFFF0595E, hv) : MenuScreen.mix(0x14FFFFFF, 0x2EFFFFFF, hv);
        m.rect(x0, y, x1, y + 18f, 5f, fill);
        m.text(Face.MEDIUM, label, x0 + bw / 2f, y + 9f - 7f * 0.6f, 7f, danger ? 0xFFFFFFFF : MenuScreen.TEXT, Align.CENTER);
        m.hit(x0, y, x1, y + 18f, action);
        return x0;
    }

    private static String size(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
