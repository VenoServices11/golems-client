package dev.golemsclient.menu;

import dev.golemsclient.oc.LobbyBridge;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * The first-launch setup, laid out like OneClient's (ported from PolyPlus, GPL-3.0, by Polyfrost): a card over the
 * lobby on the left, which the startup logo flies into and grows out of, while your player turns to look at it and
 * nods as you go. Three pages: what Golem's Client sends, look and feel, and gameplay; Back, Skip setup and Next at the
 * bottom.
 */
final class SetupPage {
    private static final String[] PAGES = {"Welcome", "Look and feel", "Gameplay"};
    private static final float CW = 360f, CH = 272f;
    // OneClient's card starts as a pill around the logo (300 x 84 of 880 x 660, its middle 74 from the top)
    private static final float PILL_W = CW * 300f / 880f, PILL_H = CH * 84f / 660f, PILL_CENTER = CH * 74f / 660f;

    private final MainMenu m;
    private final MenuData data = MenuData.get();
    private int page;
    private boolean showing;
    private float x0, y0;
    private float pageAnim = 1f, pageDir = 1f;

    SetupPage(MainMenu menu) {
        this.m = menu;
        showing = !data.setupDone;
    }

    boolean showing() {
        return showing;
    }

    /** The menu opened: your player looks at the card again. */
    void open() {
        if (showing) LobbyBridge.setupShowing(true);
    }

    // ------------------------------------------------------------------ the card

    void draw() {
        // left of the middle, like OneClient's (it moves right a little on wide windows)
        float extra = Math.max(0f, m.w - MenuScreen.H * 16f / 9f);
        x0 = MenuScreen.PAD + 24f + extra * (1f + 0.58f) / 4f;
        y0 = MenuScreen.H / 2f - CH / 2f + 6f;
        float x1 = x0 + CW, y1 = y0 + CH;
        LobbyBridge.setupCard((x0 + CW / 2f) / m.w * 2.0 - 1.0, 1.0 - (y0 + CH / 2f) / MenuScreen.H * 2.0);

        // where the startup logo lands (page 1's lockup), every frame so a resize keeps it right
        float markS = 18f, wordH = 12f, wordW = wordH * (1130f / 128f), gap = 8f;
        float lx = x0 + CW / 2f - (markS + gap + wordW) / 2f, ly = y0 + 30f;
        if (page == 0) {
            LobbyBridge.setupTargets(new float[] {lx * m.u, ly * m.u, markS * m.u, markS * m.u},
                    new float[] {(lx + markS + gap) * m.u, (ly + markS / 2f - wordH / 2f) * m.u, wordW * m.u, wordH * m.u}, MenuScreen.TEXT);
        }

        // the card grows out of a pill around the logo as the startup animation lands
        float grow = LobbyBridge.startupActive() ? LobbyBridge.cardGrow() : 1f;
        float sw = PILL_W + (CW - PILL_W) * grow, sh = PILL_H + (CH - PILL_H) * grow;
        float rise = (1f - grow) * (PILL_CENTER - CH / 2f);
        float cx = x0 + CW / 2f, cy = y0 + CH / 2f + rise;
        float saved = m.fade;
        // more solid than the menu's cards, so the lobby doesn't show through the text
        m.rect(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f, 8f, 0xF2141417);
        m.outline(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f, 8f, MenuScreen.LINE);
        float content = MenuScreen.clamp((grow - 0.45f) / 0.55f, 0f, 1f);
        m.fade = saved * content;
        if (m.fade <= 0.01f) {
            m.fade = saved;
            return;
        }

        pageAnim = m.approach(pageAnim, 1f, 12f);
        float shift = (1f - pageAnim) * 14f * pageDir;
        float pageFade = m.fade;
        m.fade = pageFade * (0.25f + 0.75f * pageAnim);
        Overlay.clip(x0 * m.u, y0 * m.u, x1 * m.u, y1 * m.u);
        switch (page) {
            case 0 -> welcome(x0 + shift, lx + shift, ly, markS, wordW, wordH, gap);
            case 1 -> lookAndFeel(x0 + shift);
            default -> gameplay(x0 + shift);
        }
        Overlay.unclip();
        m.fade = pageFade;
        nav(x0, y1);
        m.fade = saved;
    }

    private void header(float x, String kicker, String title) {
        m.text(Face.REGULAR, kicker, x + CW / 2f, y0 + 18f, 7.5f, MenuScreen.TEXT_2, Align.CENTER);
        m.text(Face.BOLD, title, x + CW / 2f, y0 + 31f, 13f, MenuScreen.TEXT, Align.CENTER);
    }

    // ------------------------------------------------------------------ page 1: welcome and what's sent

    private void welcome(float x, float lx, float ly, float markS, float wordW, float wordH, float gap) {
        m.text(Face.REGULAR, "Welcome to", x + CW / 2f, y0 + 14f, 7.5f, MenuScreen.TEXT_2, Align.CENTER);
        // the logo and name (the startup logo lands exactly here)
        Overlay.image("/assets/golemsclient/textures/logo.png", lx * m.u, ly * m.u, (lx + markS) * m.u, (ly + markS) * m.u, m.c(0xFFFFFFFF));
        Overlay.image("/assets/golemsclient/textures/wordmark.png", (lx + markS + gap) * m.u, (ly + markS / 2f - wordH / 2f) * m.u,
                (lx + markS + gap + wordW) * m.u, (ly + markS / 2f + wordH / 2f) * m.u, m.c(MenuScreen.TEXT));

        float px = x + 22f, iw = CW - 44f, y = y0 + 62f;
        m.text(Face.MONO, "WHAT GOLEM'S CLIENT SENDS", px, y, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
        y += 11f;
        y = sent(px, y, iw, "user", "Your player ID", "So Golem's Client players get the G badge on their nametags.");
        y = sent(px, y, iw, "stats", "Play-time stats", "The launcher counts how many people play and on which version.");
        y = sent(px, y, iw, "news", "News and announcements", "Fetched from Golem's Client's server for the News page.");
        y = sent(px, y, iw, "discord", "Your Discord status", "Where you play, shown by the launcher on your computer only.");
        y += 3f;
        m.rect(px, y, px + iw, y + 22f, 4f, 0x143DD68C);
        Overlay.icon("zap", (px + 8f) * m.u, (y + 6.5f) * m.u, 9f * m.u, m.c(MenuScreen.LIVE));
        m.text(Face.MEDIUM, "Never sent: your Microsoft login, password or email.", px + 23f, y + 11f - 6.5f * 0.6f, 6.5f,
                MenuScreen.TEXT, Align.LEFT);
    }

    private float sent(float x, float y, float iw, String icon, String title, String sub) {
        m.rect(x, y, x + 20f, y + 20f, 4f, 0x0FFFFFFF);
        Overlay.icon(icon, (x + 5.5f) * m.u, (y + 5.5f) * m.u, 9f * m.u, m.c(MenuScreen.TEXT_2));
        m.text(Face.SEMIBOLD, title, x + 27f, y + 2f, 7f, MenuScreen.TEXT, Align.LEFT);
        m.text(Face.REGULAR, m.fit(Face.REGULAR, sub, 6f, iw - 27f), x + 27f, y + 11.5f, 6f, MenuScreen.TEXT_2, Align.LEFT);
        return y + 25f;
    }

    // ------------------------------------------------------------------ page 2: look and feel

    private void lookAndFeel(float x) {
        header(x, "Look and feel", "Make it yours");
        float px = x + 22f, iw = CW - 44f, y = y0 + 56f;
        Options o = Minecraft.getInstance().options;

        m.text(Face.MONO, "INTERFACE SIZE", px, y, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
        m.text(Face.REGULAR, "Minecraft's own screens and HUD", px + iw, y - 0.5f, 6f, MenuScreen.TEXT_2, Align.RIGHT);
        y += 10f;
        int max = Math.max(1, Minecraft.getInstance().getWindow().calculateScale(0, Minecraft.getInstance().isEnforceUnicode()));
        int count = Math.min(max, 6) + 1;
        int current = o.guiScale().get();
        float cw = (iw - 3f * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            final int scale = i;
            String label = i == 0 ? "Auto" : String.valueOf(i);
            chip(px + i * (cw + 3f), y, cw, label, current == i || (i == 0 && current > max), () -> {
                o.guiScale().set(scale);
                Minecraft.getInstance().resizeDisplay();
            });
        }
        y += 30f;

        m.text(Face.MONO, "PAUSE SCREEN", px, y, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
        y += 10f;
        float half = (iw - 8f) / 2f;
        boolean step = "stepback".equals(data.pauseStyle);
        styleCard(px, y, half, "Flat", "The game blurs, your player turns beside the menu", !step, false, () -> {
            data.pauseStyle = "flat";
            data.save();
        });
        styleCard(px + half + 8f, y, half, "Step back", "The camera glides round to your player in the world", step, true, () -> {
            data.pauseStyle = "stepback";
            data.save();
        });
    }

    /** A pause screen style, with a little drawing of it. */
    private void styleCard(float x, float y, float cw, String title, String sub, boolean on, boolean stepBack, Runnable pick) {
        float h = 112f;
        boolean over = m.inside(x, y, x + cw, y + h);
        float hv = m.anim("style:" + title, over);
        m.rect(x, y, x + cw, y + h, 6f, on ? 0x1FE5484D : MenuScreen.mix(0x0AFFFFFF, 0x17FFFFFF, hv));
        m.outline(x, y, x + cw, y + h, 6f, on ? MenuScreen.alpha(MenuScreen.ACCENT, 0.85f) : MenuScreen.mix(MenuScreen.LINE, 0x33FFFFFF, hv));
        // the drawing: a scene, your player, and the menu on the right
        float px0 = x + 7f, py0 = y + 7f, px1 = x + cw - 7f, py1 = y + 68f;
        if (stepBack) {
            m.rect(px0, py0, px1, py1, 3f, 0xFF3B5A7A);
            m.rect(px0, py0 + (py1 - py0) * 0.62f, px1, py1, 0f, 0xFF4E7A3A);
            m.rect(px0, py0 + (py1 - py0) * 0.62f, px1, py0 + (py1 - py0) * 0.66f, 0f, 0xFF6A9A4C);
        } else {
            m.rect(px0, py0, px1, py1, 3f, 0xFF2A3440);
            m.rect(px0, py0 + (py1 - py0) * 0.62f, px1, py1, 0f, 0xFF2E3A2C);
        }
        m.rect(px0 + (px1 - px0) * 0.6f, py0, px1, py1, 0f, 0xB00E0E10);
        for (int i = 0; i < 4; i++) {
            float ly = py0 + 12f + i * 9f;
            m.rect(px0 + (px1 - px0) * 0.66f, ly, px1 - 6f, ly + (i == 0 ? 5f : 3f), 1f, i == 0 ? MenuScreen.ACCENT : 0x40FFFFFF);
        }
        float fx = px0 + (px1 - px0) * (stepBack ? 0.32f : 0.3f), fs = stepBack ? 1.25f : 1f;
        float head = 9f * fs, fy = py1 - 6f - 30f * fs;
        m.drawHead(fx - head / 2f, fy, head, 1f);
        m.rect(fx - 5f * fs, fy + head, fx + 5f * fs, fy + head + 11f * fs, 0f, 0xFF2BA4A4);
        m.rect(fx - 5f * fs, fy + head + 11f * fs, fx + 5f * fs, fy + head + 21f * fs, 0f, 0xFF3A3A9A);
        // the title, the line under it, and the pick
        m.text(Face.SEMIBOLD, title, x + 9f, y + 75f, 7.5f, MenuScreen.TEXT, Align.LEFT);
        wrap2(sub, x + 9f, y + 86f, cw - 18f);
        if (on) Overlay.circle((x + cw - 12f) * m.u, (y + 79f) * m.u, 3f * m.u, m.c(MenuScreen.ACCENT));
        m.hit(x, y, x + cw, y + h, pick);
    }

    /** Text on up to two lines. */
    private void wrap2(String s, float x, float y, float width) {
        String[] words = s.split(" ");
        StringBuilder a = new StringBuilder();
        int i = 0;
        for (; i < words.length; i++) {
            String next = a.isEmpty() ? words[i] : a + " " + words[i];
            if (m.textW(Face.REGULAR, next, 6f) > width && !a.isEmpty()) break;
            a = new StringBuilder(next);
        }
        m.text(Face.REGULAR, a.toString(), x, y, 6f, MenuScreen.TEXT_2, Align.LEFT);
        if (i < words.length) {
            String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
            m.text(Face.REGULAR, m.fit(Face.REGULAR, rest, 6f, width), x, y + 9f, 6f, MenuScreen.TEXT_2, Align.LEFT);
        }
    }

    // ------------------------------------------------------------------ page 3: gameplay

    private void gameplay(float x) {
        header(x, "Gameplay", "How you play");
        Options o = Minecraft.getInstance().options;
        float px = x + 22f, iw = CW - 44f, y = y0 + 56f;
        float half = (iw - 8f) / 2f, h = 68f;
        switchCard(px, y, half, h, "Toggle sprint", "Press sprint once instead of holding it", o.toggleSprint().get(),
                () -> o.toggleSprint().set(!o.toggleSprint().get()));
        switchCard(px + half + 8f, y, half, h, "Toggle sneak", "Press sneak once instead of holding it", o.toggleCrouch().get(),
                () -> o.toggleCrouch().set(!o.toggleCrouch().get()));
        y += h + 8f;
        switchCard(px, y, half, h, "View bobbing", "The camera sways as you walk", o.bobView().get(),
                () -> o.bobView().set(!o.bobView().get()));
        fovCard(px + half + 8f, y, half, h, o);
    }

    private void switchCard(float x, float y, float cw, float h, String title, String sub, boolean on, Runnable flip) {
        boolean over = m.inside(x, y, x + cw, y + h);
        float hv = m.anim("card:" + title, over);
        m.rect(x, y, x + cw, y + h, 6f, MenuScreen.mix(0x0AFFFFFF, 0x17FFFFFF, hv));
        m.outline(x, y, x + cw, y + h, 6f, MenuScreen.mix(MenuScreen.LINE, 0x33FFFFFF, hv));
        m.text(Face.SEMIBOLD, title, x + 10f, y + 10f, 7.5f, MenuScreen.TEXT, Align.LEFT);
        wrap2(sub, x + 10f, y + 22f, cw - 20f);
        float sw = 22f, sh = 12f, sx = x + 10f, sy = y + h - 10f - sh;
        float k = m.anim("tg:" + title, on);
        m.rect(sx, sy, sx + sw, sy + sh, sh / 2f, MenuScreen.mix(0xFF2A2A30, MenuScreen.ACCENT, k));
        Overlay.circle((sx + sh / 2f + (sw - sh) * k) * m.u, (sy + sh / 2f) * m.u, (sh / 2f - 2f) * m.u, m.c(0xFFFFFFFF));
        m.text(Face.MONO, on ? "ON" : "OFF", sx + sw + 6f, sy + 3.5f, 5.5f, on ? MenuScreen.TEXT : MenuScreen.TEXT_3, Align.LEFT);
        m.hit(x, y, x + cw, y + h, flip);
    }

    private boolean draggingFov;
    private float fovX0, fovX1;

    private void fovCard(float x, float y, float cw, float h, Options o) {
        m.rect(x, y, x + cw, y + h, 6f, 0x0AFFFFFF);
        m.outline(x, y, x + cw, y + h, 6f, MenuScreen.LINE);
        m.text(Face.SEMIBOLD, "FOV effects", x + 10f, y + 10f, 7.5f, MenuScreen.TEXT, Align.LEFT);
        wrap2("How much sprinting and potions zoom the view", x + 10f, y + 22f, cw - 20f);
        fovX0 = x + 10f;
        fovX1 = x + cw - 36f;
        float v = o.fovEffectScale().get().floatValue();
        float sy = y + h - 16f;
        m.rect(fovX0, sy + 5f, fovX1, sy + 7f, 1f, 0x26FFFFFF);
        m.rect(fovX0, sy + 5f, fovX0 + (fovX1 - fovX0) * v, sy + 7f, 1f, MenuScreen.ACCENT);
        Overlay.rect((fovX0 + (fovX1 - fovX0) * v - 3.5f) * m.u, (sy + 2.5f) * m.u, (fovX0 + (fovX1 - fovX0) * v + 3.5f) * m.u,
                (sy + 9.5f) * m.u, 0f, m.c(0xFFFFFFFF));
        m.text(Face.MONO, Math.round(v * 100) + "%", x + cw - 10f, sy + 2f, 6f, MenuScreen.TEXT_2, Align.RIGHT);
        m.hit(fovX0 - 4f, sy - 2f, fovX1 + 4f, sy + 14f, () -> {
            draggingFov = true;
            dragFov(m.mx);
        });
    }

    /** The mouse moved while the FOV slider is held. True while it's held. */
    boolean drag(float mouseX) {
        if (!draggingFov) return false;
        dragFov(mouseX);
        return true;
    }

    boolean release() {
        boolean was = draggingFov;
        draggingFov = false;
        return was;
    }

    private void dragFov(float mouseX) {
        double v = MenuScreen.clamp((mouseX - fovX0) / Math.max(1f, fovX1 - fovX0), 0f, 1f);
        Minecraft.getInstance().options.fovEffectScale().set(Math.round(v * 20) / 20.0);
    }

    // ------------------------------------------------------------------ the bottom: Back, Skip setup, Next

    private void nav(float x, float y1) {
        float by = y1 - 30f, bh = 20f;
        boolean last = page == PAGES.length - 1;
        if (page > 0) {
            button(x + 14f, by, 64f, bh, "Back", "arrow_left", false, this::back);
            ghost(x + 14f + 64f + 10f, by, bh, "Skip setup", this::finish);
        }
        // the dots
        for (int i = 0; i < PAGES.length; i++) {
            float dx = x + CW / 2f + (i - (PAGES.length - 1) / 2f) * 9f;
            float k = m.anim("dot" + i, i == page);
            m.rect(dx - 2f - 2f * k, by + bh / 2f - 2f, dx + 2f + 2f * k, by + bh / 2f + 2f, 2f,
                    MenuScreen.mix(0x40FFFFFF, MenuScreen.ACCENT, k));
        }
        String next = page == 0 ? "Got it" : last ? "Finish" : "Next";
        button(x + CW - 14f - 78f, by, 78f, bh, next, "arrow", true, this::next);
    }

    private void button(float x, float y, float bw, float bh, String label, String icon, boolean primary, Runnable action) {
        boolean over = m.inside(x, y, x + bw, y + bh);
        float hv = m.anim("setupbtn:" + label, over);
        if (primary) {
            m.rect(x, y, x + bw, y + bh, 5f, MenuScreen.mix(MenuScreen.ACCENT, 0xFFF0676B, hv));
        } else {
            m.rect(x, y, x + bw, y + bh, 5f, MenuScreen.mix(0x0FFFFFFF, 0x24FFFFFF, hv));
            m.outline(x, y, x + bw, y + bh, 5f, MenuScreen.LINE);
        }
        float tw = m.textW(Face.SEMIBOLD, label, 7f), is = 7f, total = tw + 5f + is;
        float tx = x + bw / 2f - total / 2f;
        int ink = primary ? 0xFFFFFFFF : MenuScreen.mix(MenuScreen.TEXT_2, MenuScreen.TEXT, hv);
        if (icon.equals("arrow_left")) {
            Overlay.icon(icon, tx * m.u, (y + bh / 2f - is / 2f) * m.u, is * m.u, m.c(ink));
            m.text(Face.SEMIBOLD, label, tx + is + 5f, y + bh / 2f - 7f * 0.6f, 7f, ink, Align.LEFT);
        } else {
            m.text(Face.SEMIBOLD, label, tx, y + bh / 2f - 7f * 0.6f, 7f, ink, Align.LEFT);
            Overlay.icon(icon, (tx + tw + 5f) * m.u, (y + bh / 2f - is / 2f) * m.u, is * m.u, m.c(ink));
        }
        m.hit(x, y, x + bw, y + bh, action);
    }

    private void ghost(float x, float y, float bh, String label, Runnable action) {
        float tw = m.textW(Face.MEDIUM, label, 6.5f);
        boolean over = m.inside(x - 4f, y, x + tw + 4f, y + bh);
        float hv = m.anim("ghost:" + label, over);
        m.text(Face.MEDIUM, label, x, y + bh / 2f - 6.5f * 0.6f, 6.5f, MenuScreen.mix(MenuScreen.TEXT_3, MenuScreen.TEXT, hv), Align.LEFT);
        m.hit(x - 4f, y, x + tw + 4f, y + bh, action);
    }

    private void chip(float x, float y, float cw, String label, boolean on, Runnable action) {
        boolean over = m.inside(x, y, x + cw, y + 18f);
        float hv = m.anim("schip:" + label, over);
        m.rect(x, y, x + cw, y + 18f, 4f, on ? 0x33E5484D : MenuScreen.mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        m.outline(x, y, x + cw, y + 18f, 4f, on ? MenuScreen.alpha(MenuScreen.ACCENT, 0.8f) : MenuScreen.LINE);
        m.text(Face.MEDIUM, label, x + cw / 2f, y + 9f - 7f * 0.6f, 7f, on ? MenuScreen.TEXT : MenuScreen.mix(MenuScreen.TEXT_2, MenuScreen.TEXT, hv),
                Align.CENTER);
        m.hit(x, y, x + cw, y + 18f, action);
    }

    // ------------------------------------------------------------------ moving between pages

    void next() {
        if (page == PAGES.length - 1) {
            finish();
            return;
        }
        page++;
        pageAnim = 0f;
        pageDir = 1f;
        LobbyBridge.nod();
    }

    /** Esc: back a page (nothing on the first). */
    void back() {
        if (page == 0) return;
        page--;
        pageAnim = 0f;
        pageDir = -1f;
        LobbyBridge.glance();
    }

    private void finish() {
        showing = false;
        data.setupDone = true;
        data.save();
        Minecraft.getInstance().options.save();
        LobbyBridge.setupShowing(false);
        LobbyBridge.wave();
        m.setupFinished();
    }

    /** Dev screenshots only. */
    void devFinish() {
        finish();
    }

    /** Dev screenshots only. */
    void devPage(int index) {
        page = Math.max(0, Math.min(PAGES.length - 1, index));
        pageAnim = 1f;
    }
}
