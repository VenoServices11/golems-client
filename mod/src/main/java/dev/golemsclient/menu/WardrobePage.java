package dev.golemsclient.menu;

import dev.golemsclient.GolemsClient;
import dev.golemsclient.oc.LobbyBridge;
import dev.golemsclient.render.GuiTexture;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import dev.golemsclient.render.Textures;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Wardrobe page, laid out like OneClient's: the camera moves in close on your player in the lobby, "Equipped" on
 * the left (cosmetic slots, which come with Golem's Client's own server later; your cape works now), and the Wardrobe
 * panel on the right: your skins (a library you add PNGs to), your Mojang capes and the skins you wore before.
 */
final class WardrobePage {
    private enum View { HOME, SKINS, CAPES, HISTORY }

    private static final float LEFT_W = 150f, RIGHT_W = 214f;
    private static final String[][] MAIN = {{"Head", ""}, {"Capes", "cape"}, {"Wings", ""}, {"Aura", ""}, {"Pet", ""}, {"Emote", ""}};
    private static final String[] ACCESSORIES = {"Face", "Back", "Shoulders", "Gloves", "Feet"};

    private final MenuScreen m;
    private View view = View.HOME;
    private float scroll;
    private float listY0, listY1, listContent;
    private boolean addSlim;
    private boolean picking;

    WardrobePage(MenuScreen menu) {
        this.m = menu;
    }

    void open() {
        view = View.HOME;
        scroll = 0f;
        if (m instanceof MainMenu) LobbyBridge.wardrobe(true);
        if (SkinService.profile() == null) SkinService.refresh();
        SkinService.Profile p = SkinService.profile();
        addSlim = p != null && p.skin() != null && p.skin().slim();
    }

    void close() {
        if (m instanceof MainMenu) LobbyBridge.wardrobe(false);
    }

    /** Esc: back a step. True when it was used. */
    boolean back() {
        if (view == View.HOME) return false;
        view = View.HOME;
        scroll = 0f;
        return true;
    }

    void draw() {
        drawEquipped();
        drawPanel();
    }

    // ------------------------------------------------------------------ Equipped (left)

    private void drawEquipped() {
        float x0 = MenuScreen.PAD, x1 = x0 + LEFT_W, y0 = 50f, y1 = MenuScreen.H - 26f;
        float slide = (1f - m.fade) * -8f;
        x0 += slide;
        x1 += slide;
        m.card(x0, y0, x1, y1);
        float x = x0 + 12f, y = y0 + 12f;
        m.text(Face.BOLD, "Equipped", x, y, 9.5f, MenuScreen.TEXT, Align.LEFT);
        m.iconButton("refresh", x1 - 16f, y + 5f, "Read your skin and capes again", SkinService::refresh);
        y += 22f;
        m.text(Face.MONO, "MAIN", x, y, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
        y += 10f;
        SkinService.Profile p = SkinService.profile();
        for (String[] slot : MAIN) {
            if (slot[1].equals("cape")) {
                SkinService.Cape cape = activeCape(p);
                y = slotRow(x, y, x1 - x0 - 24f, "Capes", cape == null ? (p != null ? "None" : SkinService.problem() != null ? "Unavailable" : "Loading...") : cape.alias(), cape, true,
                        () -> show(View.CAPES));
            } else {
                y = slotRow(x, y, x1 - x0 - 24f, slot[0], "Coming soon", null, false, null);
            }
        }
        m.rect(x, y + 2f, x1 - 12f, y + 2.5f, 0f, MenuScreen.LINE);
        y += 10f;
        m.text(Face.MONO, "ACCESSORIES", x, y, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
        y += 10f;
        for (String slot : ACCESSORIES) y = slotRow(x, y, x1 - x0 - 24f, slot, "Coming soon", null, false, null);
    }

    private float slotRow(float x, float y, float iw, String name, String sub, SkinService.Cape cape, boolean live, Runnable action) {
        float h = 26f;
        boolean over = live && m.inside(x - 4f, y - 1f, x + iw + 4f, y + h - 1f);
        float hv = m.anim("slot:" + name, over);
        if (hv > 0.01f) m.rect(x - 4f, y - 1f, x + iw + 4f, y + h - 1f, 4f, MenuScreen.alpha(0x14FFFFFF, hv));
        // the slot's picture: the cape's front, or a plus
        float bx = x, by = y + 3f, bs = 18f;
        m.rect(bx, by, bx + bs, by + bs, 3f, 0x0FFFFFFF);
        if (cape != null) {
            drawCape(cape.url(), bx + 4.5f, by + 1.5f, 15f);
        } else {
            m.text(Face.MEDIUM, "+", bx + bs / 2f, by + bs / 2f - 9f * 0.6f, 9f, live ? MenuScreen.TEXT_2 : MenuScreen.TEXT_3, Align.CENTER);
        }
        m.text(Face.MEDIUM, name, x + bs + 8f, y + 4f, 7.5f, live ? MenuScreen.TEXT : MenuScreen.TEXT_2, Align.LEFT);
        m.text(Face.REGULAR, m.fit(Face.REGULAR, sub, 6f, iw - bs - 10f), x + bs + 8f, y + 14f, 6f, MenuScreen.TEXT_3, Align.LEFT);
        if (action != null) {
            m.hit(x - 4f, y - 1f, x + iw + 4f, y + h - 1f, action);
        } else if (m.inside(x - 4f, y - 1f, x + iw + 4f, y + h - 1f)) {
            m.tooltip = "Cosmetics are coming with Golem's Client's own server.";
        }
        return y + h;
    }

    // ------------------------------------------------------------------ Wardrobe (right)

    private void drawPanel() {
        float x1 = m.w - MenuScreen.PAD, x0 = x1 - RIGHT_W, y0 = 50f, y1 = MenuScreen.H - 26f;
        float slide = (1f - m.fade) * 8f;
        x0 += slide;
        x1 += slide;
        m.card(x0, y0, x1, y1);
        float x = x0 + 12f, y = y0 + 12f, iw = x1 - x0 - 24f;
        String title = switch (view) {
            case HOME -> "Wardrobe";
            case SKINS -> "Skins";
            case CAPES -> "Capes";
            case HISTORY -> "History";
        };
        float tx = x;
        if (view != View.HOME) {
            backArrow(x + 5f, y + 5f);
            tx += 16f;
        }
        m.text(Face.BOLD, title, tx, y, 9.5f, MenuScreen.TEXT, Align.LEFT);
        if (SkinService.busy()) m.text(Face.MONO, "SAVING...", x1 - 12f, y + 2f, 5.5f, MenuScreen.ACCENT, Align.RIGHT);
        y += 22f;

        SkinService.Profile p = SkinService.profile();
        if (p == null) {
            if (SkinService.loading() || SkinService.problem() == null) {
                m.text(Face.REGULAR, "Loading your skin and capes...", x, y + 4f, 7f, MenuScreen.TEXT_2, Align.LEFT);
            } else {
                wrap(SkinService.problem(), x, y + 4f, iw, MenuScreen.TEXT_2);
                button(x, y + 34f, iw, "Try again", false, SkinService::refresh);
            }
            return;
        }
        switch (view) {
            case HOME -> drawHome(p, x, y, iw, y1);
            case SKINS -> drawSkins(p, x, y, iw, y1);
            case CAPES -> drawCapes(p, x, y, iw, y1);
            case HISTORY -> drawHistory(p, x, y, iw, y1);
        }
    }

    private void drawHome(SkinService.Profile p, float x, float y, float iw, float y1) {
        // what you're wearing now
        m.rect(x, y, x + iw, y + 62f, 6f, 0x0AFFFFFF);
        if (p.skin() != null) drawSkin(p.skin().url(), p.skin().slim(), x + 10f, y + 7f, 48f);
        m.text(Face.SEMIBOLD, m.fit(Face.SEMIBOLD, p.name(), 8f, iw - 50f), x + 44f, y + 16f, 8f, MenuScreen.TEXT, Align.LEFT);
        String arms = p.skin() == null ? "Default skin" : (p.skin().slim() ? "Slim arms" : "Classic arms");
        SkinService.Cape cape = activeCape(p);
        m.text(Face.REGULAR, arms + (cape == null ? "" : " · " + cape.alias()), x + 44f, y + 28f, 6.5f, MenuScreen.TEXT_2, Align.LEFT);
        y += 72f;
        y = row(x, y, iw, "shirt", "Skins", "Your skin library", true, () -> show(View.SKINS));
        y = row(x, y, iw, "image", "Capes", p.capes().isEmpty() ? "No capes on this account" : p.capes().size() + " on this account", true,
                () -> show(View.CAPES));
        y = row(x, y, iw, "refresh", "History", "Skins you wore before", true, () -> show(View.HISTORY));
        y = row(x, y, iw, "users", "Outfits", "Coming soon", false, null);
        y = row(x, y, iw, "zap", "Emote wheel", "Coming soon", false, null);
        y += 8f;
        m.text(Face.MONO, "COSMETICS", x, y, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
        y += 10f;
        wrap("Hats, wings, pets and emotes are coming with Golem's Client's own server.", x, y, iw, MenuScreen.TEXT_3);
    }

    private void drawSkins(SkinService.Profile p, float x, float y, float iw, float y1) {
        button(x, y, iw, picking ? "Choosing..." : "Add a skin from a file", true, this::pick);
        y += 24f;
        m.text(Face.REGULAR, "or drop PNG files on the window", x + iw / 2f, y, 6f, MenuScreen.TEXT_3, Align.CENTER);
        y += 12f;
        // the arms new skins get
        float cw = (iw - 3f) / 2f;
        chip(x, y, cw, "Classic arms", !addSlim, () -> addSlim = false);
        chip(x + cw + 3f, y, cw, "Slim arms", addSlim, () -> addSlim = true);
        y += 26f;
        List<SkinService.Saved> lib = SkinService.library();
        if (lib.isEmpty()) {
            wrap("Your library is empty. Add skins here to switch between them in one click.", x, y + 2f, iw, MenuScreen.TEXT_2);
            return;
        }
        // a grid of three, scrolling
        int cols = 3;
        float gap = 5f, cw3 = (iw - gap * (cols - 1)) / cols, ch = 84f;
        beginList(y, y1 - 10f, (float) Math.ceil(lib.size() / (double) cols) * (ch + gap));
        for (int i = 0; i < lib.size(); i++) {
            SkinService.Saved s = lib.get(i);
            float cx = x + (i % cols) * (cw3 + gap), cy = y + (i / cols) * (ch + gap) - scroll;
            if (cy + ch < listY0 || cy > listY1) continue;
            boolean over = m.inside(cx, Math.max(cy, listY0), cx + cw3, Math.min(cy + ch, listY1));
            float hv = m.anim("lib:" + s.id, over);
            m.rect(cx, cy, cx + cw3, cy + ch, 5f, MenuScreen.mix(0x0AFFFFFF, 0x1AFFFFFF, hv));
            byte[] png = SkinService.read(s);
            if (png != null) drawSkinBytes("lib:" + s.id, png, s.slim, cx + cw3 / 2f - 14f, cy + 6f, 56f);
            m.text(Face.MEDIUM, m.fit(Face.MEDIUM, s.name, 6.5f, cw3 - 8f), cx + cw3 / 2f, cy + ch - 15f, 6.5f, MenuScreen.TEXT, Align.CENTER);
            m.text(Face.MONO, s.slim ? "SLIM" : "CLASSIC", cx + cw3 / 2f, cy + ch - 7f, 4.5f, MenuScreen.TEXT_3, Align.CENTER);
            if (over) {
                // a small X in the corner takes it out of the library
                float bx = cx + cw3 - 9f, by = cy + 9f;
                boolean onX = m.inside(bx - 6f, by - 6f, bx + 6f, by + 6f);
                Overlay.icon("close", (bx - 3.5f) * m.u, (by - 3.5f) * m.u, 7f * m.u, m.c(onX ? MenuScreen.TEXT : MenuScreen.TEXT_3));
                // the X first, so it wins over the card
                m.hit(bx - 6f, by - 6f, bx + 6f, by + 6f, () -> {
                    SkinService.remove(s);
                    Textures.release("skin:lib:" + s.id);
                });
                if (png != null) m.hit(cx, cy, cx + cw3, cy + ch, () -> SkinService.wear(png, s.slim));
                if (!onX) m.tooltip = "Wear " + s.name;
            }
        }
        endList();
    }

    private void drawCapes(SkinService.Profile p, float x, float y, float iw, float y1) {
        SkinService.Cape active = activeCape(p);
        y = capeRow(x, y, iw, null, "No cape", "Hide your cape", active == null);
        if (p.capes().isEmpty()) {
            wrap("This account has no Mojang capes. Capes from events and Minecraft purchases show up here.", x, y + 4f, iw, MenuScreen.TEXT_2);
            return;
        }
        beginList(y, y1 - 10f, p.capes().size() * 36f);
        float cy = y - scroll;
        for (SkinService.Cape cape : p.capes()) {
            if (cy + 34f >= listY0 && cy <= listY1) capeRow(x, cy, iw, cape, cape.alias(), "Mojang cape", cape.active());
            cy += 36f;
        }
        endList();
    }

    private float capeRow(float x, float y, float iw, SkinService.Cape cape, String name, String sub, boolean on) {
        float h = 32f;
        boolean over = m.inside(x, y, x + iw, y + h);
        float hv = m.anim("cape:" + name, over);
        m.rect(x, y, x + iw, y + h, 5f, on ? 0x26E5484D : MenuScreen.mix(0x0AFFFFFF, 0x1AFFFFFF, hv));
        if (on) m.outline(x, y, x + iw, y + h, 5f, 0x80E5484D);
        if (cape != null) drawCape(cape.url(), x + 8f, y + 4f, 24f);
        m.text(Face.SEMIBOLD, m.fit(Face.SEMIBOLD, name, 7.5f, iw - 60f), x + 30f, y + 7f, 7.5f, MenuScreen.TEXT, Align.LEFT);
        m.text(Face.REGULAR, sub, x + 30f, y + 18f, 6f, MenuScreen.TEXT_2, Align.LEFT);
        if (on) m.text(Face.MONO, "ON", x + iw - 10f, y + h / 2f - 3f, 5.5f, MenuScreen.ACCENT, Align.RIGHT);
        if (!on) m.hit(x, y, x + iw, y + h, () -> SkinService.cape(cape == null ? null : cape.id()));
        return y + h + 4f;
    }

    private void drawHistory(SkinService.Profile p, float x, float y, float iw, float y1) {
        List<SkinService.Worn> worn = SkinService.history();
        if (worn.isEmpty()) {
            wrap("Skins you wear from now on show up here, so you can go back to one.", x, y + 2f, iw, MenuScreen.TEXT_2);
            return;
        }
        String now = p.skin() == null ? "" : p.skin().url();
        float h = 52f;
        beginList(y, y1 - 10f, worn.size() * (h + 4f));
        float ry = y - scroll;
        for (SkinService.Worn w : worn) {
            if (ry + h >= listY0 && ry <= listY1) {
                boolean current = w.url.equals(now);
                boolean over = m.inside(x, Math.max(ry, listY0), x + iw, Math.min(ry + h, listY1));
                float hv = m.anim("worn:" + w.url, over);
                m.rect(x, ry, x + iw, ry + h, 5f, current ? 0x26E5484D : MenuScreen.mix(0x0AFFFFFF, 0x1AFFFFFF, hv));
                drawSkin(w.url, w.slim, x + 8f, ry + 6f, 40f);
                m.text(Face.SEMIBOLD, current ? "Wearing now" : "Worn " + MenuScreen.ago(w.at), x + 36f, ry + 15f, 7.5f, MenuScreen.TEXT,
                        Align.LEFT);
                m.text(Face.REGULAR, w.slim ? "Slim arms" : "Classic arms", x + 36f, ry + 27f, 6f, MenuScreen.TEXT_2, Align.LEFT);
                if (!current && over) m.hit(x, ry, x + iw, ry + h, () -> SkinService.wear(w));
            }
            ry += h + 4f;
        }
        endList();
    }

    // ------------------------------------------------------------------ adding skins

    private void pick() {
        if (picking) return;
        picking = true;
        boolean slim = addSlim;
        Thread t = new Thread(() -> {
            String chosen = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                chosen = TinyFileDialogs.tinyfd_openFileDialog("Add a skin", "", filters, "Minecraft skin (PNG)", true);
            } catch (Throwable e) {
                GolemsClient.LOGGER.warn("Couldn't open the file picker", e);
            }
            String result = chosen;
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                picking = false;
                if (result != null) add(java.util.Arrays.stream(result.split("\\|")).map(Path::of).toList(), slim);
            });
        }, "Golem's Client skin picker");
        t.setDaemon(true);
        t.start();
    }

    void drop(List<Path> files) {
        add(files.stream().filter(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")).toList(), addSlim);
    }

    private void add(List<Path> files, boolean slim) {
        int added = 0;
        String problem = null;
        for (Path f : files) {
            if (!Files.isRegularFile(f)) continue;
            String why = SkinService.add(f, slim);
            if (why == null) added++;
            else problem = why;
        }
        if (added > 0) {
            view = View.SKINS;
            m.showToast(added == 1 ? "Added to your skins. Click it to wear it." : "Added " + added + " skins.");
        } else if (problem != null) {
            m.showToast(problem);
        }
    }

    // ------------------------------------------------------------------ scrolling lists

    private void beginList(float y0, float y1, float content) {
        listY0 = y0;
        listY1 = y1;
        listContent = content;
        scroll = MenuScreen.clamp(scroll, 0f, Math.max(0f, content - (y1 - y0)));
        Overlay.clip(0f, y0 * m.u, m.w * m.u, y1 * m.u);
    }

    private void endList() {
        Overlay.unclip();
        float view = listY1 - listY0;
        if (listContent > view) {
            float x = m.w - MenuScreen.PAD - 5f;
            float bar = view * view / listContent, at = (view - bar) * scroll / (listContent - view);
            m.rect(x, listY0 + at, x + 1.5f, listY0 + at + bar, 0.75f, 0x40FFFFFF);
        }
    }

    boolean scroll(double mouseY, double amount) {
        float y = (float) mouseY / m.u;
        if (view == View.HOME || y < listY0 || y > listY1) return false;
        scroll -= (float) amount * 30f;
        return true;
    }

    // ------------------------------------------------------------------ pieces

    private void show(View v) {
        view = v;
        scroll = 0f;
    }

    private SkinService.Cape activeCape(SkinService.Profile p) {
        if (p == null) return null;
        for (SkinService.Cape c : p.capes()) if (c.active()) return c;
        return null;
    }

    private float row(float x, float y, float iw, String icon, String title, String sub, boolean live, Runnable action) {
        float h = 30f;
        boolean over = live && m.inside(x, y, x + iw, y + h);
        float hv = m.anim("wrow:" + title, over);
        if (hv > 0.01f) m.rect(x - 4f, y, x + iw + 4f, y + h, 5f, MenuScreen.alpha(0x14FFFFFF, hv));
        Overlay.icon(icon, (x + 2f) * m.u, (y + h / 2f - 5f) * m.u, 10f * m.u, m.c(live ? MenuScreen.TEXT_2 : MenuScreen.TEXT_3));
        m.text(Face.MEDIUM, title, x + 20f, y + 7f, 7.5f, live ? MenuScreen.TEXT : MenuScreen.TEXT_2, Align.LEFT);
        m.text(Face.REGULAR, sub, x + 20f, y + 18f, 6f, MenuScreen.TEXT_3, Align.LEFT);
        if (live) {
            Overlay.icon("next", (x + iw - 9f) * m.u, (y + h / 2f - 4f) * m.u, 8f * m.u, m.c(MenuScreen.mix(MenuScreen.TEXT_3, MenuScreen.TEXT, hv)));
            m.hit(x - 4f, y, x + iw + 4f, y + h, action);
        }
        return y + h + 2f;
    }

    private void backArrow(float cx, float cy) {
        boolean over = m.inside(cx - 7f, cy - 7f, cx + 7f, cy + 7f);
        float hv = m.anim("wback", over);
        Overlay.icon("prev", (cx - 4.5f) * m.u, (cy - 4.5f) * m.u, 9f * m.u, m.c(MenuScreen.mix(MenuScreen.TEXT_2, MenuScreen.TEXT, hv)));
        m.hit(cx - 7f, cy - 7f, cx + 7f, cy + 7f, this::back);
    }

    private void button(float x, float y, float iw, String label, boolean primary, Runnable action) {
        boolean over = m.inside(x, y, x + iw, y + 20f);
        float hv = m.anim("wbtn:" + label, over);
        int fill = primary ? MenuScreen.mix(MenuScreen.ACCENT, 0xFFF0595E, hv) : MenuScreen.mix(0x14FFFFFF, 0x2EFFFFFF, hv);
        m.rect(x, y, x + iw, y + 20f, 5f, fill);
        m.text(Face.SEMIBOLD, label, x + iw / 2f, y + 10f - 7f * 0.6f, 7f, primary ? 0xFFFFFFFF : MenuScreen.TEXT, Align.CENTER);
        m.hit(x, y, x + iw, y + 20f, action);
    }

    private void chip(float x, float y, float cw, String label, boolean on, Runnable action) {
        boolean over = m.inside(x, y, x + cw, y + 18f);
        float hv = m.anim("wchip:" + label, over);
        m.rect(x, y, x + cw, y + 18f, 4f, on ? MenuScreen.ACCENT : MenuScreen.mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        m.text(Face.MEDIUM, label, x + cw / 2f, y + 9f - 6.5f * 0.6f, 6.5f, on ? 0xFFFFFFFF : MenuScreen.mix(MenuScreen.TEXT_2, MenuScreen.TEXT, hv),
                Align.CENTER);
        m.hit(x, y, x + cw, y + 18f, action);
    }

    /** Text broken into lines that fit the width. */
    private void wrap(String s, float x, float y, float iw, int color) {
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (m.textW(Face.REGULAR, next, 6.5f) > iw && !line.isEmpty()) {
                m.text(Face.REGULAR, line.toString(), x, y, 6.5f, color, Align.LEFT);
                y += 10f;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) m.text(Face.REGULAR, line.toString(), x, y, 6.5f, color, Align.LEFT);
    }

    // ------------------------------------------------------------------ skin and cape pictures

    private void drawSkin(String url, boolean slim, float x, float y, float h) {
        byte[] png = SkinService.picture(url);
        if (png == null) {
            m.rect(x + h * 0.125f, y, x + h * 0.375f, y + h * 0.25f, 2f, 0x14FFFFFF);
            return;
        }
        drawSkinBytes("skin:url:" + url, png, slim, x, y, h);
    }

    /** A skin seen from the front, flat: h units tall, h/2 wide. */
    private void drawSkinBytes(String key, byte[] png, boolean slim, float x, float y, float h) {
        int[] size = SkinService.pngSize(png);
        if (size == null) return;
        GuiTexture t = Textures.image(key, png, true, 0f);
        if (t == null) return;
        boolean modern = size[1] == 64;
        float px = h / 32f;
        int aw = slim ? 3 : 4;
        // head and hat
        part(t, modern, x, y, px, 4, 0, 8, 8, 8, 8, false);
        part(t, modern, x, y, px, 4, 0, 8, 8, 40, 8, false);
        // body
        part(t, modern, x, y, px, 4, 8, 8, 12, 20, 20, false);
        // right arm (on the left as you look at it) and right leg
        part(t, modern, x, y, px, 4 - aw, 8, aw, 12, 44, 20, false);
        part(t, modern, x, y, px, 4, 20, 4, 12, 4, 20, false);
        if (modern) {
            part(t, true, x, y, px, 12, 8, aw, 12, 36, 52, false);
            part(t, true, x, y, px, 8, 20, 4, 12, 20, 52, false);
            // the outer layer
            part(t, true, x, y, px, 4, 8, 8, 12, 20, 36, false);
            part(t, true, x, y, px, 4 - aw, 8, aw, 12, 44, 36, false);
            part(t, true, x, y, px, 12, 8, aw, 12, 52, 52, false);
            part(t, true, x, y, px, 4, 20, 4, 12, 4, 36, false);
            part(t, true, x, y, px, 8, 20, 4, 12, 4, 52, false);
        } else {
            // old skins have one arm and one leg, mirrored for the other side
            part(t, false, x, y, px, 12, 8, aw, 12, 44, 20, true);
            part(t, false, x, y, px, 8, 20, 4, 12, 4, 20, true);
        }
    }

    private void part(GuiTexture t, boolean modern, float x, float y, float px, int gx, int gy, int gw, int gh, int su, int sv, boolean flip) {
        float texH = modern ? 64f : 32f;
        float u0 = su / 64f, u1 = (su + gw) / 64f, v0 = sv / texH, v1 = (sv + gh) / texH;
        float x0 = (x + gx * px) * m.u, y0 = (y + gy * px) * m.u, x1 = (x + (gx + gw) * px) * m.u, y1 = (y + (gy + gh) * px) * m.u;
        Overlay.texture(t, x0, y0, x1, y1, flip ? u1 : u0, v0, flip ? u0 : u1, v1, m.c(0xFFFFFFFF));
    }

    /** A cape's front: h units tall. */
    private void drawCape(String url, float x, float y, float h) {
        byte[] png = SkinService.picture(url);
        int[] size = SkinService.pngSize(png);
        if (png == null || size == null) return;
        GuiTexture t = Textures.image("cape:" + url, png, true, 0f);
        if (t == null) return;
        float w = h * 10f / 16f;
        Overlay.texture(t, x * m.u, y * m.u, (x + w) * m.u, (y + h) * m.u, 1f / 64f, 1f / 32f, 11f / 64f, 17f / 32f, m.c(0xFFFFFFFF));
    }
}
