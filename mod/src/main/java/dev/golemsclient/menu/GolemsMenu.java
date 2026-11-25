package dev.golemsclient.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.golemsclient.GolemsClient;
import dev.golemsclient.mods.Mods;
import dev.golemsclient.mods.OneConfigMods;
import dev.golemsclient.render.GuiTexture;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import dev.golemsclient.render.Textures;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Golem's Client's mod menu (in place of OneConfig's, which OneClient uses): Mods (a grid of cards; a card opens the
 * mod's settings, drawn here for OneConfig mods, or the mod's own screen), HUD (pieces on and off, and the layout
 * editor), Profiles (whole sets of mod settings) and Keybinds (every key in one list, clashes marked).
 */
public final class GolemsMenu extends MenuScreen {
    private static final String[] TABS = {"Mods", "HUD", "Profiles", "Keybinds"};

    private final Screen parent;
    private final MenuData data = MenuData.get();
    private int page;
    private float tabX = -1f, tabW;
    private float scroll, listY0, listY1;
    private String query = "";
    private boolean favouritesOnly;
    private Mods.Mod open;
    private List<OneConfigMods.Setting> openSettings = List.of();
    // typing: the search box, a text setting, or a profile name
    private enum Typing { NONE, SEARCH, TEXT, NEW_PROFILE, RENAME_PROFILE }
    private Typing typing = Typing.NONE;
    private String typed = "";
    private OneConfigMods.Setting editingText;
    private String renaming;
    // keybinds: the one waiting for a key
    private KeyMapping capturing;
    // a slider being dragged
    private OneConfigMods.Setting dragging;
    private float dragX0, dragX1;
    private String pendingDelete;
    private final Map<String, Optional<GuiTexture>> icons = new HashMap<>();

    private GolemsMenu(Screen parent) {
        super(Component.literal("Golem's Client"));
        this.parent = parent;
    }

    /** Opens the menu (unless it's already open). */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GolemsMenu) return;
        mc.setScreen(new GolemsMenu(mc.screen));
    }

    public static void open(int page) {
        open();
        if (Minecraft.getInstance().screen instanceof GolemsMenu m) m.setPage(page);
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x990E0E10);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        beginFrame(g, mouseX, mouseY);
        drawTop();
        float x0 = PAD, x1 = w - PAD, y0 = 46f + (1f - fade) * 8f, y1 = H - 16f;
        card(x0, y0, x1, y1);
        switch (page) {
            case 0 -> {
                if (open != null) drawModSettings(x0, y0, x1, y1);
                else drawMods(x0, y0, x1, y1);
            }
            case 1 -> drawHud(x0, y0, x1, y1);
            case 2 -> drawProfiles(x0, y0, x1, y1);
            default -> drawKeybinds(x0, y0, x1, y1);
        }
        endFrame(g);
    }

    private void drawTop() {
        float y = 14f;
        Overlay.image("/assets/golemsclient/textures/logo.png", PAD * u, y * u, (PAD + 18f) * u, (y + 18f) * u, c(0xFFFFFFFF));
        float nameH = 8.5f, nameW = nameH * (1130f / 128f);
        Overlay.image("/assets/golemsclient/textures/wordmark.png", (PAD + 25f) * u, (y + 9f - nameH / 2f) * u,
                (PAD + 25f + nameW) * u, (y + 9f + nameH / 2f) * u, c(TEXT));
        float size = 8.5f, gap = 22f, total = -gap;
        for (String tab : TABS) total += textW(Face.MEDIUM, tab, size) + gap;
        float x = w / 2f - total / 2f;
        for (int i = 0; i < TABS.length; i++) {
            float tw = textW(Face.MEDIUM, TABS[i], size);
            boolean over = inside(x - 6f, y - 2f, x + tw + 6f, y + 20f);
            float hv = anim("tab" + i, over);
            text(Face.MEDIUM, TABS[i], x, y + 9f - size * 0.6f, size, i == page ? TEXT : mix(TEXT_2, TEXT, hv), Align.LEFT);
            if (i == page) {
                if (tabX < 0f) {
                    tabX = x;
                    tabW = tw;
                }
                tabX = approach(tabX, x, 14f);
                tabW = approach(tabW, tw, 14f);
            }
            final int index = i;
            hit(x - 6f, y - 2f, x + tw + 6f, y + 20f, () -> setPage(index));
            x += tw + gap;
        }
        rect(tabX, y + 18f, tabX + tabW, y + 19.5f, 0.75f, ACCENT);
        String done = Component.translatable("gui.done").getString();
        float bw = textW(Face.SEMIBOLD, done, 7.5f) + 28f, bx1 = w - PAD, bx0 = bx1 - bw;
        boolean overDone = inside(bx0, y - 1f, bx1, y + 19f);
        rect(bx0, y - 1f, bx1, y + 19f, 5f, mix(ACCENT, 0xFFF0676B, anim("done", overDone)));
        text(Face.SEMIBOLD, done, (bx0 + bx1) / 2f, y + 9f - 7.5f * 0.6f, 7.5f, 0xFFFFFFFF, Align.CENTER);
        hit(bx0, y - 1f, bx1, y + 19f, this::onClose);
    }

    private void setPage(int next) {
        page = next;
        scroll = 0f;
        open = null;
        typing = Typing.NONE;
        capturing = null;
        pendingDelete = null;
    }

    // ------------------------------------------------------------------ Mods: the grid

    private void drawMods(float x0, float y0, float x1, float y1) {
        float px = x0 + 18f, iw = x1 - x0 - 36f, y = y0 + 14f;
        text(Face.BOLD, "Mods", px, y, 12f, TEXT, Align.LEFT);
        // search, and favourites only
        float sw = Math.min(220f, iw * 0.4f), sx1 = px + iw, sx0 = sx1 - sw;
        searchBox(sx0, y - 2f, sx1, y + 16f);
        chip(sx0 - 84f, y - 2f, 78f, "Favourites", favouritesOnly, () -> {
            favouritesOnly = !favouritesOnly;
            scroll = 0f;
        });
        listY0 = y0 + 40f;
        listY1 = y1 - 8f;
        List<Mods.Mod> mods = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (Mods.Mod m : Mods.all()) {
            if (favouritesOnly && !data.favouriteMods.contains(m.key())) continue;
            if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q) && !m.description().toLowerCase(Locale.ROOT).contains(q)) continue;
            mods.add(m);
        }
        // favourites first
        mods.sort((a, b) -> Boolean.compare(data.favouriteMods.contains(b.key()), data.favouriteMods.contains(a.key())));
        if (mods.isEmpty()) {
            empty(x0, x1, listY0 + 40f, Mods.all().isEmpty() ? "No mods with settings found" : "Nothing matches that",
                    Mods.all().isEmpty() ? "Mods that use OneConfig or have their own settings show up here." : "Try another word.");
            return;
        }
        int cols = Math.max(2, (int) ((iw + 8f) / 158f));
        float cw = (iw - 8f * (cols - 1)) / cols, chh = 70f;
        int rows = (mods.size() + cols - 1) / cols;
        float content = rows * (chh + 8f), view = listY1 - listY0;
        scroll = clamp(scroll, 0f, Math.max(0f, content - view));
        Overlay.clip(0f, listY0 * u, w * u, listY1 * u);
        for (int i = 0; i < mods.size(); i++) {
            float cx = px + (i % cols) * (cw + 8f), cy = listY0 + (i / cols) * (chh + 8f) - scroll;
            if (cy + chh < listY0 || cy > listY1) continue;
            modCard(mods.get(i), cx, cy, cw, chh);
        }
        Overlay.unclip();
        scrollbar(x1, content, view);
    }

    private void modCard(Mods.Mod m, float x, float y, float cw, float h) {
        boolean visible = y >= listY0 - 1f && y + h <= listY1 + 1f;
        boolean over = inside(x, y, x + cw, y + h) && my >= listY0 && my <= listY1;
        float hv = anim("mod:" + m.key(), over);
        rect(x, y, x + cw, y + h, 6f, mix(0x0AFFFFFF, 0x17FFFFFF, hv));
        outline(x, y, x + cw, y + h, 6f, mix(LINE, 0x33FFFFFF, hv));
        // icon
        GuiTexture icon = icon(m);
        if (icon != null) Overlay.texture(icon, (x + 10f) * u, (y + 10f) * u, (x + 30f) * u, (y + 30f) * u, 0f, 0f, 1f, 1f, c(0xFFFFFFFF));
        else letterTile(m.name(), x + 10f, y + 10f, 20f);
        text(Face.SEMIBOLD, fit(Face.SEMIBOLD, m.name(), 8f, cw - 62f), x + 37f, y + 11f, 8f, TEXT, Align.LEFT);
        text(Face.MONO, m.hasSettings() ? "SETTINGS" : "OPENS ITS OWN SCREEN", x + 37f, y + 22f, 4.5f, TEXT_3, Align.LEFT);
        String d = m.description() == null ? "" : m.description().replaceAll("\\s+", " ");
        text(Face.REGULAR, fit(Face.REGULAR, d, 6f, cw - 20f), x + 10f, y + 38f, 6f, TEXT_2, Align.LEFT);
        // favourite
        boolean fav = data.favouriteMods.contains(m.key());
        float sx = x + cw - 16f, sy = y + 13f;
        boolean overStar = inside(sx - 7f, sy - 7f, sx + 7f, sy + 7f);
        Overlay.icon(fav ? "star_on" : "star", (sx - 4.5f) * u, (sy - 4.5f) * u, 9f * u, c(fav ? 0xFFE8BA5C : mix(TEXT_3, TEXT, anim("star:" + m.key(), overStar))));
        // its switch, when it has one
        var enabled = m.hasSettings() ? OneConfigMods.enabled(m.settings()) : null;
        if (enabled != null) {
            boolean on = Boolean.TRUE.equals(enabled.get());
            toggle(x + cw - 10f, y + h - 13f, on, "tm:" + m.key(), visible ? () -> OneConfigMods.set(m.settings(), enabled, !on) : null);
        } else if (!m.hasSettings()) {
            Overlay.icon("arrow", (x + cw - 18f) * u, (y + h - 17f) * u, 8f * u, c(TEXT_3));
        }
        if (!visible) return;
        hit(sx - 7f, sy - 7f, sx + 7f, sy + 7f, () -> {
            if (!data.favouriteMods.remove(m.key())) data.favouriteMods.add(m.key());
            data.save();
        });
        hit(x, y, x + cw, y + h, () -> openMod(m));
    }

    private void openMod(Mods.Mod m) {
        if (m.hasSettings()) {
            open = m;
            openSettings = OneConfigMods.settings(m.settings());
            scroll = 0f;
            return;
        }
        Screen s = m.screen().apply(this);
        if (s != null) minecraft.setScreen(s);
        else showToast("Couldn't open " + m.name() + "'s settings.");
    }

    private GuiTexture icon(Mods.Mod m) {
        if (m.modId() == null) return null;
        Optional<GuiTexture> cached = icons.get(m.modId());
        if (cached == null) {
            cached = Optional.empty();
            try {
                Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(m.modId());
                Optional<String> path = mod.flatMap(c -> c.getMetadata().getIconPath(64));
                if (mod.isPresent() && path.isPresent()) {
                    Optional<Path> file = mod.get().findPath(path.get());
                    if (file.isPresent() && path.get().toLowerCase(Locale.ROOT).endsWith(".png")) {
                        cached = Optional.ofNullable(Textures.image("modicon:" + m.modId(), Files.readAllBytes(file.get()), false, 0.2f));
                    }
                }
            } catch (Exception e) {
                GolemsClient.LOGGER.debug("No icon for {}: {}", m.modId(), e.toString());
            }
            icons.put(m.modId(), cached);
        }
        return cached.orElse(null);
    }

    // ------------------------------------------------------------------ Mods: one mod's settings

    private void drawModSettings(float x0, float y0, float x1, float y1) {
        float px = x0 + 18f, iw = x1 - x0 - 36f, y = y0 + 14f;
        boolean overBack = inside(px - 4f, y - 2f, px + 14f, y + 16f);
        Overlay.icon("arrow_left", px * u, (y + 1f) * u, 10f * u, c(mix(TEXT_2, TEXT, anim("back", overBack))));
        hit(px - 4f, y - 2f, px + 14f, y + 16f, () -> {
            open = null;
            scroll = 0f;
            typing = Typing.NONE;
        });
        GuiTexture icon = icon(open);
        float tx = px + 20f;
        if (icon != null) {
            Overlay.texture(icon, tx * u, (y - 1f) * u, (tx + 16f) * u, (y + 15f) * u, 0f, 0f, 1f, 1f, c(0xFFFFFFFF));
            tx += 22f;
        }
        text(Face.BOLD, open.name(), tx, y, 12f, TEXT, Align.LEFT);
        String classic = "Classic menu";
        float cbw = textW(Face.MEDIUM, classic, 6.5f) + 20f;
        smallButton(px + iw - cbw, y - 2f, cbw, 18f, classic, () -> OneConfigMods.openClassic(open.settings()));
        listY0 = y0 + 40f;
        listY1 = y1 - 8f;
        Overlay.clip(0f, listY0 * u, w * u, listY1 * u);
        float ry = listY0 - scroll;
        String section = null;
        for (OneConfigMods.Setting s : openSettings) {
            if (!s.section().equals(section)) {
                if (section != null) ry += 8f;
                section = s.section();
                if (!section.isBlank()) {
                    if (ry > listY0 - 20f && ry < listY1) text(Face.MONO, section.toUpperCase(Locale.ROOT), px, ry + 2f, 5.5f, TEXT_3, Align.LEFT);
                    ry += 13f;
                }
            }
            float rh = 30f;
            if (ry + rh > listY0 && ry < listY1) settingRow(s, px, ry, px + iw, rh);
            ry += rh;
        }
        Overlay.unclip();
        float content = ry + scroll - listY0;
        scroll = clamp(scroll, 0f, Math.max(0f, content - (listY1 - listY0)));
        scrollbar(x1, content, listY1 - listY0);
        if (openSettings.isEmpty()) empty(x0, x1, listY0 + 40f, "No settings here", "Try the classic menu for this one.");
    }

    private void settingRow(OneConfigMods.Setting s, float x0, float y, float x1, float h) {
        boolean overRow = inside(x0 - 8f, y, x1 + 8f, y + h);
        float hv = anim("row:" + System.identityHashCode(s), overRow);
        if (hv > 0.01f) rect(x0 - 8f, y + 1f, x1 + 8f, y + h - 1f, 4f, alpha(0x0DFFFFFF, hv));
        float cw = Math.min(170f, (x1 - x0) * 0.45f), cx0 = x1 - cw, labelW = cx0 - x0 - 16f;
        boolean info = s.kind().equals("InfoVisualizer");
        if (!s.description().isBlank()) {
            text(Face.SEMIBOLD, fit(Face.SEMIBOLD, s.title(), 7.5f, info ? x1 - x0 : labelW), x0, y + 6f, 7.5f, TEXT, Align.LEFT);
            text(Face.REGULAR, fit(Face.REGULAR, s.description().replaceAll("\\s+", " "), 6f, info ? x1 - x0 : labelW), x0, y + 17f, 6f, TEXT_3, Align.LEFT);
            if (overRow && textW(Face.REGULAR, s.description(), 6f) > labelW) tooltip = s.description();
        } else {
            text(Face.SEMIBOLD, fit(Face.SEMIBOLD, s.title(), 7.5f, labelW), x0, y + h / 2f - 7.5f * 0.6f, 7.5f, TEXT, Align.LEFT);
        }
        rect(x0, y + h - 0.5f, x1, y + h, 0f, 0x0FFFFFFF);
        if (info) return;
        float cy = y + h / 2f;
        boolean live = cy > listY0 + 4f && cy < listY1 - 4f;
        Object value = s.property().get();
        Object settings = open.settings();
        switch (s.kind()) {
            case "SwitchVisualizer", "CheckboxVisualizer" -> toggle(x1, cy, Boolean.TRUE.equals(value), "t:" + System.identityHashCode(s),
                    live ? () -> OneConfigMods.set(settings, s.property(), !Boolean.TRUE.equals(value)) : null);
            case "SliderVisualizer", "NumberVisualizer" -> slider(s, cx0, cy, x1, value, live);
            case "DropdownVisualizer", "RadioVisualizer" -> {
                List<Object> values = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                Class<?> type = OneConfigMods.type(s.property());
                List<String> named = OneConfigMods.meta(s.property(), "optionLabels");
                if (type.isEnum()) {
                    for (Object e : type.getEnumConstants()) {
                        values.add(e);
                        labels.add(e.toString());
                    }
                } else {
                    List<String> options = OneConfigMods.meta(s.property(), "options");
                    if (options != null) for (int i = 0; i < options.size(); i++) {
                        values.add(i);
                        labels.add(Mods.text(options.get(i)));
                    }
                }
                if (named != null) for (int i = 0; i < Math.min(named.size(), labels.size()); i++) labels.set(i, Mods.text(named.get(i)));
                if (values.isEmpty()) {
                    smallButton(x1 - 60f, cy - 9f, 60f, 18f, "Open", () -> OneConfigMods.openClassic(settings));
                    return;
                }
                int at = Math.max(0, values.indexOf(value));
                stepper(cx0, cy, x1, labels.get(at), "c:" + System.identityHashCode(s), live,
                        step -> OneConfigMods.set(settings, s.property(), values.get(Math.floorMod(at + step, values.size()))));
            }
            case "TextVisualizer" -> {
                boolean editing = typing == Typing.TEXT && editingText == s;
                String shown = editing ? typed : String.valueOf(value == null ? "" : value);
                rect(cx0, cy - 10f, x1, cy + 10f, 5f, 0x0FFFFFFF);
                outline(cx0, cy - 10f, x1, cy + 10f, 5f, editing ? alpha(ACCENT, 0.8f) : LINE);
                text(Face.REGULAR, fit(Face.REGULAR, shown, 7f, cw - 16f), cx0 + 8f, cy - 7f * 0.6f, 7f, TEXT, Align.LEFT);
                if (editing) caret(cx0 + 8f + Math.min(cw - 16f, textW(Face.REGULAR, shown, 7f)), cy);
                if (live) hit(cx0, cy - 10f, x1, cy + 10f, () -> {
                    typing = Typing.TEXT;
                    editingText = s;
                    typed = String.valueOf(value == null ? "" : value);
                });
            }
            case "ButtonVisualizer" -> {
                Object label = OneConfigMods.meta(s.property(), "text");
                Object run = OneConfigMods.meta(s.property(), "runnable");
                smallButton(x1 - 90f, cy - 9f, 90f, 18f, label == null ? "Run" : Mods.text(label), () -> {
                    if (run instanceof Runnable r) r.run();
                });
            }
            case "KeybindVisualizer" -> smallButton(x1 - 90f, cy - 9f, 90f, 18f, "Keybinds", () -> setPage(3));
            case "ColorVisualizer" -> {
                int argb = argb(value);
                rect(x1 - 52f, cy - 8f, x1 - 36f, cy + 8f, 3f, argb);
                outline(x1 - 52f, cy - 8f, x1 - 36f, cy + 8f, 3f, 0x40FFFFFF);
                smallButton(x1 - 30f, cy - 9f, 30f, 18f, "Edit", () -> OneConfigMods.openClassic(settings));
            }
            default -> smallButton(x1 - 60f, cy - 9f, 60f, 18f, "Open", () -> OneConfigMods.openClassic(settings));
        }
    }

    private static int argb(Object color) {
        if (color == null) return 0xFF808080;
        for (String name : new String[] {"getArgb", "argb", "toArgb", "getRgba"}) {
            try {
                Object v = color.getClass().getMethod(name).invoke(color);
                if (v instanceof Integer i) return i;
            } catch (Throwable ignored) {
            }
        }
        return 0xFF808080;
    }

    private void slider(OneConfigMods.Setting s, float x0, float cy, float x1, Object value, boolean live) {
        Number min = OneConfigMods.meta(s.property(), "min"), max = OneConfigMods.meta(s.property(), "max");
        double lo = min == null ? 0 : min.doubleValue(), hi = max == null ? 100 : max.doubleValue();
        double v = value instanceof Number n ? n.doubleValue() : lo;
        double p = hi > lo ? (v - lo) / (hi - lo) : 0;
        String unit = OneConfigMods.meta(s.property(), "unit");
        boolean whole = value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte;
        String label = (whole ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", ""))
                + (unit == null ? "" : unit);
        float vw = 60f, tx0 = x0 + 4f, tx1 = x1 - vw - 6f;
        rect(tx0, cy - 1f, tx1, cy + 1f, 1f, 0x26FFFFFF);
        float kx = tx0 + (tx1 - tx0) * (float) Math.max(0, Math.min(1, p));
        rect(tx0, cy - 1f, kx, cy + 1f, 1f, ACCENT);
        Overlay.rect((kx - 3.5f) * u, (cy - 3.5f) * u, (kx + 3.5f) * u, (cy + 3.5f) * u, 0f, c(0xFFFFFFFF));
        text(Face.MONO, fit(Face.MONO, label, 6f, vw), x1, cy - 6f * 0.62f, 6f, TEXT_2, Align.RIGHT);
        if (live) hit(tx0 - 4f, cy - 8f, tx1 + 4f, cy + 8f, () -> {
            dragging = s;
            dragX0 = tx0;
            dragX1 = tx1;
            dragTo(mx);
        });
    }

    private void dragTo(float mouseX) {
        if (dragging == null || open == null) return;
        var p = dragging.property();
        Number min = OneConfigMods.meta(p, "min"), max = OneConfigMods.meta(p, "max"), step = OneConfigMods.meta(p, "step");
        double lo = min == null ? 0 : min.doubleValue(), hi = max == null ? 100 : max.doubleValue();
        double t = clamp((mouseX - dragX0) / Math.max(1f, dragX1 - dragX0), 0f, 1f);
        double v = lo + t * (hi - lo);
        if (step != null && step.doubleValue() > 0) v = lo + Math.round((v - lo) / step.doubleValue()) * step.doubleValue();
        Class<?> type = OneConfigMods.type(p);
        Object out;
        if (type == Integer.class || type == int.class) out = (int) Math.round(v);
        else if (type == Long.class || type == long.class) out = Math.round(v);
        else if (type == Double.class || type == double.class) out = v;
        else if (type == Short.class || type == short.class) out = (short) Math.round(v);
        else if (type == Byte.class || type == byte.class) out = (byte) Math.round(v);
        else out = (float) v;
        OneConfigMods.set(open.settings(), p, out);
    }

    // ------------------------------------------------------------------ HUD

    private void drawHud(float x0, float y0, float x1, float y1) {
        float px = x0 + 18f, iw = x1 - x0 - 36f, y = y0 + 14f;
        text(Face.BOLD, "HUD", px, y, 12f, TEXT, Align.LEFT);
        String edit = "Edit layout";
        float ew = textW(Face.SEMIBOLD, edit, 7f) + 30f;
        boolean overEdit = inside(px + iw - ew, y - 2f, px + iw, y + 16f);
        rect(px + iw - ew, y - 2f, px + iw, y + 16f, 5f, mix(ACCENT, 0xFFF0676B, anim("edit", overEdit)));
        text(Face.SEMIBOLD, edit, px + iw - ew / 2f, y + 7f - 7f * 0.6f, 7f, 0xFFFFFFFF, Align.CENTER);
        hit(px + iw - ew, y - 2f, px + iw, y + 16f, () -> {
            minecraft.setScreen(null);
            OneConfigMods.openHudEditor();
        });
        text(Face.REGULAR, "Turn pieces on and off here; Edit layout lets you drag and resize them over the game.", px, y + 18f, 6.5f, TEXT_2, Align.LEFT);
        listY0 = y0 + 46f;
        listY1 = y1 - 8f;
        if (!FabricLoader.getInstance().isModLoaded("oneconfig")) {
            empty(x0, x1, listY0 + 40f, "No HUD pieces", "HUD pieces come with OneConfig mods like EvergreenHUD.");
            return;
        }
        List<OneConfigMods.HudPiece> huds = OneConfigMods.huds();
        if (huds.isEmpty()) {
            empty(x0, x1, listY0 + 40f, "No HUD pieces yet", "Add some with Edit layout.");
            return;
        }
        Overlay.clip(0f, listY0 * u, w * u, listY1 * u);
        float ry = listY0 - scroll;
        for (OneConfigMods.HudPiece hpc : huds) {
            if (ry + 28f > listY0 && ry < listY1) {
                text(Face.SEMIBOLD, fit(Face.SEMIBOLD, hpc.name(), 7.5f, iw - 80f), px, ry + 6f, 7.5f, TEXT, Align.LEFT);
                if (!hpc.category().isBlank()) text(Face.REGULAR, hpc.category(), px, ry + 16f, 6f, TEXT_3, Align.LEFT);
                boolean on = OneConfigMods.hudShown(hpc);
                float cy = ry + 14f;
                toggle(px + iw, cy, on, "h:" + System.identityHashCode(hpc.hud()),
                        cy > listY0 + 4f && cy < listY1 - 4f ? () -> OneConfigMods.setHudShown(hpc, !on) : null);
                rect(px, ry + 27.5f, px + iw, ry + 28f, 0f, 0x0FFFFFFF);
            }
            ry += 28f;
        }
        Overlay.unclip();
        float content = ry + scroll - listY0;
        scroll = clamp(scroll, 0f, Math.max(0f, content - (listY1 - listY0)));
        scrollbar(x1, content, listY1 - listY0);
    }

    // ------------------------------------------------------------------ Profiles

    private void drawProfiles(float x0, float y0, float x1, float y1) {
        float px = x0 + 18f, iw = x1 - x0 - 36f, y = y0 + 14f;
        text(Face.BOLD, "Profiles", px, y, 12f, TEXT, Align.LEFT);
        text(Face.REGULAR, "A profile is a whole set of mod settings. Switch between them, e.g. one for streaming and one for PvP.",
                px, y + 18f, 6.5f, TEXT_2, Align.LEFT);
        if (!FabricLoader.getInstance().isModLoaded("oneconfig")) {
            empty(x0, x1, y0 + 80f, "Profiles need OneConfig", "They save OneConfig mods' settings.");
            return;
        }
        // a new profile
        float nx1 = px + iw, nx0 = nx1 - 180f, ny = y - 2f;
        boolean naming = typing == Typing.NEW_PROFILE;
        if (naming) {
            rect(nx0, ny, nx1 - 50f, ny + 18f, 5f, 0x14FFFFFF);
            outline(nx0, ny, nx1 - 50f, ny + 18f, 5f, alpha(ACCENT, 0.8f));
            text(Face.REGULAR, typed.isEmpty() ? "Name..." : fit(Face.REGULAR, typed, 7f, 110f), nx0 + 8f, ny + 9f - 7f * 0.6f, 7f, typed.isEmpty() ? TEXT_3 : TEXT, Align.LEFT);
            caret(nx0 + 8f + textW(Face.REGULAR, typed, 7f), ny + 9f);
            smallButton(nx1 - 44f, ny, 44f, 18f, "Create", this::submitTyping);
        } else {
            smallButton(nx1 - 90f, ny, 90f, 18f, "New profile", () -> {
                typing = Typing.NEW_PROFILE;
                typed = "";
            });
        }
        listY0 = y0 + 46f;
        listY1 = y1 - 8f;
        List<String> profiles;
        String active;
        try {
            profiles = OneConfigMods.profiles();
            active = OneConfigMods.activeProfile();
        } catch (Throwable e) {
            empty(x0, x1, listY0 + 40f, "Couldn't read the profiles", e.toString());
            return;
        }
        float ry = listY0 - scroll;
        Overlay.clip(0f, listY0 * u, w * u, listY1 * u);
        for (String name : profiles) {
            if (ry + 30f > listY0 && ry < listY1) profileRow(name, name.equals(active), px, ry, px + iw);
            ry += 32f;
        }
        Overlay.unclip();
        float content = ry + scroll - listY0;
        scroll = clamp(scroll, 0f, Math.max(0f, content - (listY1 - listY0)));
        scrollbar(x1, content, listY1 - listY0);
    }

    private void profileRow(String name, boolean active, float x0, float y, float x1) {
        float h = 28f, cy = y + h / 2f;
        boolean visible = cy > listY0 + 4f && cy < listY1 - 4f;
        rect(x0, y, x1, y + h, 5f, active ? 0x1FE5484D : 0x0AFFFFFF);
        outline(x0, y, x1, y + h, 5f, active ? alpha(ACCENT, 0.7f) : LINE);
        boolean renaming = typing == Typing.RENAME_PROFILE && name.equals(this.renaming);
        String shown = renaming ? typed : name;
        text(Face.SEMIBOLD, fit(Face.SEMIBOLD, shown, 8f, x1 - x0 - 260f), x0 + 12f, cy - 8f * 0.6f, 8f, TEXT, Align.LEFT);
        if (renaming) caret(x0 + 12f + textW(Face.SEMIBOLD, shown, 8f), cy);
        if (active) text(Face.MONO, "ACTIVE", x0 + 16f + textW(Face.SEMIBOLD, shown, 8f), cy - 5f * 0.62f, 5f, ACCENT, Align.LEFT);
        if (!visible) return;
        float bx = x1 - 8f;
        boolean deleting = name.equals(pendingDelete);
        bx = rowButton(bx, cy, deleting ? "Really delete?" : "Delete", deleting, () -> {
            if (name.equals(pendingDelete)) {
                OneConfigMods.deleteProfile(name);
                pendingDelete = null;
            } else {
                pendingDelete = name;
            }
        });
        bx = rowButton(bx, cy, renaming ? "Save" : "Rename", false, () -> {
            if (renaming) {
                submitTyping();
            } else {
                typing = Typing.RENAME_PROFILE;
                this.renaming = name;
                typed = name;
            }
        });
        bx = rowButton(bx, cy, "Copy", false, () -> {
            String copy = name + " copy";
            int n = 2;
            while (OneConfigMods.profiles().contains(copy)) copy = name + " copy " + n++;
            OneConfigMods.cloneProfile(name, copy);
        });
        if (!active) rowButton(bx, cy, "Switch to", false, () -> {
            OneConfigMods.openProfile(name);
            showToast("Switched to " + name + ".");
        });
    }

    private float rowButton(float x1, float cy, String label, boolean danger, Runnable action) {
        float bw = textW(Face.MEDIUM, label, 6.5f) + 16f, x0 = x1 - bw;
        boolean over = inside(x0, cy - 8f, x1, cy + 8f);
        float hv = anim("rb:" + label + cy, over);
        rect(x0, cy - 8f, x1, cy + 8f, 4f, danger ? mix(0x26E5484D, 0x40E5484D, hv) : mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        text(Face.MEDIUM, label, (x0 + x1) / 2f, cy - 6.5f * 0.6f, 6.5f, danger ? 0xFFF0676B : TEXT, Align.CENTER);
        hit(x0, cy - 8f, x1, cy + 8f, action);
        return x0 - 5f;
    }

    // ------------------------------------------------------------------ Keybinds

    private void drawKeybinds(float x0, float y0, float x1, float y1) {
        float px = x0 + 18f, iw = x1 - x0 - 36f, y = y0 + 14f;
        text(Face.BOLD, "Keybinds", px, y, 12f, TEXT, Align.LEFT);
        text(Face.REGULAR, capturing != null ? "Press a key or mouse button. Esc clears it." : "Every key in the game and its mods. Clashes are marked in red.",
                px, y + 18f, 6.5f, capturing != null ? ACCENT : TEXT_2, Align.LEFT);
        float sw = Math.min(220f, iw * 0.4f);
        searchBox(px + iw - sw, y - 2f, px + iw, y + 16f);
        listY0 = y0 + 46f;
        listY1 = y1 - 8f;
        String q = query.toLowerCase(Locale.ROOT).trim();
        Map<String, List<KeyMapping>> groups = new LinkedHashMap<>();
        KeyMapping[] all = minecraft.options.keyMappings.clone();
        Arrays.sort(all);
        for (KeyMapping k : all) {
            String name = Component.translatable(k.getName()).getString();
            String group = category(k);
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !group.toLowerCase(Locale.ROOT).contains(q)) continue;
            groups.computeIfAbsent(group, g -> new ArrayList<>()).add(k);
        }
        Overlay.clip(0f, listY0 * u, w * u, listY1 * u);
        float ry = listY0 - scroll;
        for (var e : groups.entrySet()) {
            if (ry > listY0 - 20f && ry < listY1) text(Face.MONO, e.getKey().toUpperCase(Locale.ROOT), px, ry + 2f, 5.5f, TEXT_3, Align.LEFT);
            ry += 13f;
            for (KeyMapping k : e.getValue()) {
                if (ry + 26f > listY0 && ry < listY1) keyRow(k, all, px, ry, px + iw);
                ry += 26f;
            }
            ry += 8f;
        }
        Overlay.unclip();
        float content = ry + scroll - listY0;
        scroll = clamp(scroll, 0f, Math.max(0f, content - (listY1 - listY0)));
        scrollbar(x1, content, listY1 - listY0);
    }

    private static String category(KeyMapping k) {
        try {
            return k.getCategory().label().getString();
        } catch (Throwable e) {
            return "Other";
        }
    }

    private void keyRow(KeyMapping k, KeyMapping[] all, float x0, float y, float x1) {
        float h = 26f, cy = y + h / 2f;
        boolean clash = false;
        if (!k.isUnbound()) for (KeyMapping o : all) if (o != k && k.same(o)) clash = true;
        text(Face.SEMIBOLD, fit(Face.SEMIBOLD, Component.translatable(k.getName()).getString(), 7.5f, x1 - x0 - 200f), x0, cy - 7.5f * 0.6f, 7.5f, TEXT, Align.LEFT);
        rect(x0, y + h - 0.5f, x1, y + h, 0f, 0x0FFFFFFF);
        boolean visible = cy > listY0 + 4f && cy < listY1 - 4f;
        float bw = 120f, bx1 = x1 - 26f, bx0 = bx1 - bw;
        boolean waiting = capturing == k;
        boolean over = inside(bx0, cy - 9f, bx1, cy + 9f);
        float hv = anim("k:" + k.getName(), over || waiting);
        rect(bx0, cy - 9f, bx1, cy + 9f, 4f, waiting ? 0x33E5484D : mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        outline(bx0, cy - 9f, bx1, cy + 9f, 4f, waiting ? alpha(ACCENT, 0.9f) : clash ? 0xB3E5484D : LINE);
        String label = waiting ? "Press a key..." : k.getTranslatedKeyMessage().getString();
        text(Face.MEDIUM, fit(Face.MEDIUM, label, 7f, bw - 12f), (bx0 + bx1) / 2f, cy - 7f * 0.6f, 7f, clash && !waiting ? 0xFFF0676B : TEXT, Align.CENTER);
        if (!visible) return;
        hit(bx0, cy - 9f, bx1, cy + 9f, () -> capturing = capturing == k ? null : k);
        if (!k.isDefault()) smallIcon("refresh", x1 - 16f, cy, "Back to " + k.getDefaultKey().getDisplayName().getString(), () -> {
            k.setKey(k.getDefaultKey());
            KeyMapping.resetMapping();
            minecraft.options.save();
        });
    }

    private void bind(InputConstants.Key key) {
        if (capturing == null) return;
        capturing.setKey(key);
        KeyMapping.resetMapping();
        minecraft.options.save();
        capturing = null;
    }

    // ------------------------------------------------------------------ bits

    private void searchBox(float x0, float y0, float x1, float y1) {
        boolean focused = typing == Typing.SEARCH;
        rect(x0, y0, x1, y1, 5f, mix(0x0FFFFFFF, 0x1AFFFFFF, anim("search", inside(x0, y0, x1, y1) || focused)));
        outline(x0, y0, x1, y1, 5f, focused ? alpha(ACCENT, 0.8f) : LINE);
        float cy = (y0 + y1) / 2f;
        Overlay.icon("search", (x0 + 6f) * u, (cy - 4f) * u, 8f * u, c(TEXT_3));
        text(Face.REGULAR, query.isEmpty() ? "Search" : fit(Face.REGULAR, query, 7f, x1 - x0 - 28f), x0 + 18f, cy - 7f * 0.6f, 7f, query.isEmpty() ? TEXT_3 : TEXT, Align.LEFT);
        if (focused) caret(x0 + 18f + (query.isEmpty() ? 0f : textW(Face.REGULAR, query, 7f)), cy);
        hit(x0, y0, x1, y1, () -> typing = Typing.SEARCH);
    }

    private void caret(float x, float cy) {
        if ((System.currentTimeMillis() / 530L) % 2L == 0L) rect(x + 1f, cy - 5f, x + 1.75f, cy + 5f, 0f, TEXT);
    }

    private void chip(float x, float y, float cw, String label, boolean on, Runnable action) {
        boolean over = inside(x, y, x + cw, y + 18f);
        rect(x, y, x + cw, y + 18f, 5f, on ? 0x33E5484D : mix(0x0FFFFFFF, 0x24FFFFFF, anim("chip:" + label, over)));
        outline(x, y, x + cw, y + 18f, 5f, on ? alpha(ACCENT, 0.8f) : LINE);
        Overlay.icon(on ? "star_on" : "star", (x + 7f) * u, (y + 5f) * u, 8f * u, c(on ? 0xFFE8BA5C : TEXT_2));
        text(Face.MEDIUM, label, x + 19f, y + 9f - 7f * 0.6f, 7f, on ? TEXT : TEXT_2, Align.LEFT);
        hit(x, y, x + cw, y + 18f, action);
    }

    private void smallButton(float x, float y, float bw, float bh, String label, Runnable action) {
        boolean over = inside(x, y, x + bw, y + bh);
        float hv = anim("sb:" + label + x + y, over);
        rect(x, y, x + bw, y + bh, 5f, mix(0x14FFFFFF, 0x2EFFFFFF, hv));
        outline(x, y, x + bw, y + bh, 5f, LINE);
        text(Face.MEDIUM, fit(Face.MEDIUM, label, 6.5f, bw - 8f), x + bw / 2f, y + bh / 2f - 6.5f * 0.6f, 6.5f, TEXT, Align.CENTER);
        hit(x, y, x + bw, y + bh, action);
    }

    private void toggle(float x1, float cy, boolean on, String id, Runnable flip) {
        float sw = 22f, sh = 12f, sx = x1 - sw, sy = cy - sh / 2f;
        float k = anim(id, on);
        rect(sx, sy, sx + sw, sy + sh, sh / 2f, mix(0xFF2A2A30, ACCENT, k));
        Overlay.circle((sx + sh / 2f + (sw - sh) * k) * u, cy * u, (sh / 2f - 2f) * u, c(0xFFFFFFFF));
        if (flip != null) hit(sx - 6f, sy - 4f, sx + sw + 2f, sy + sh + 4f, flip);
    }

    private void stepper(float x0, float cy, float x1, String label, String id, boolean live, java.util.function.IntConsumer step) {
        float h = 20f, y0 = cy - h / 2f;
        rect(x0, y0, x1, y0 + h, 5f, 0x0FFFFFFF);
        outline(x0, y0, x1, y0 + h, 5f, LINE);
        text(Face.MEDIUM, fit(Face.MEDIUM, label, 7f, x1 - x0 - 36f), (x0 + x1) / 2f, cy - 7f * 0.6f, 7f, TEXT, Align.CENTER);
        for (int dir : new int[] {-1, 1}) {
            float ax0 = dir < 0 ? x0 : x1 - 18f;
            boolean over = inside(ax0, y0, ax0 + 18f, y0 + h);
            Overlay.icon(dir < 0 ? "arrow_left" : "arrow", (ax0 + 5f) * u, (cy - 4f) * u, 8f * u, c(mix(TEXT_2, TEXT, anim(id + dir, over))));
            if (live) hit(ax0, y0, ax0 + 18f, y0 + h, () -> step.accept(dir));
        }
        if (live) hit(x0 + 18f, y0, x1 - 18f, y0 + h, () -> step.accept(1));
    }

    private void scrollbar(float x1, float content, float view) {
        if (content <= view) return;
        float bar = view * view / content, at = (view - bar) * scroll / (content - view);
        rect(x1 - 6f, listY0 + at, x1 - 4.5f, listY0 + at + bar, 0.75f, 0x40FFFFFF);
    }

    private void empty(float x0, float x1, float y, String title, String sub) {
        text(Face.SEMIBOLD, title, (x0 + x1) / 2f, y, 9f, TEXT, Align.CENTER);
        text(Face.REGULAR, sub, (x0 + x1) / 2f, y + 14f, 6.5f, TEXT_2, Align.CENTER);
    }

    // ------------------------------------------------------------------ input

    private void submitTyping() {
        String text = typed.trim();
        try {
            switch (typing) {
                case TEXT -> {
                    if (editingText != null && open != null) OneConfigMods.set(open.settings(), editingText.property(), typed);
                }
                case NEW_PROFILE -> {
                    if (!text.isEmpty()) OneConfigMods.createProfile(text);
                }
                case RENAME_PROFILE -> {
                    if (!text.isEmpty() && renaming != null && !text.equals(renaming)) OneConfigMods.renameProfile(renaming, text);
                }
                default -> { }
            }
        } catch (Throwable e) {
            showToast("Couldn't do that: " + e.getMessage());
        }
        typing = Typing.NONE;
        typed = "";
        editingText = null;
        renaming = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (capturing != null && event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        Typing before = typing;
        if (typing != Typing.SEARCH && typing != Typing.NONE) {
            // a click elsewhere finishes the edit
            boolean hitSomething = clickHit(event);
            if (typing == before) submitTyping();
            return hitSomething || true;
        }
        typing = Typing.NONE;
        if (clickHit(event)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragTo((float) event.x() / u);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (float) scrollY * 30f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (capturing != null) {
            bind(key == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
            return true;
        }
        if (typing != Typing.NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (typing == Typing.SEARCH) query = "";
                typing = Typing.NONE;
                typed = "";
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (typing != Typing.SEARCH) submitTyping();
                else typing = Typing.NONE;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (typing == Typing.SEARCH) {
                    if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                } else if (!typed.isEmpty()) {
                    typed = typed.substring(0, typed.length() - 1);
                }
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (open != null) {
                open = null;
                scroll = 0f;
                return true;
            }
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && event.hasControlDown()) {
            typing = Typing.SEARCH;
            return true;
        }
        if (GolemsMenuKey.matches(event) && minecraft.level != null) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (Character.isISOControl(cp)) return false;
        String ch = new String(Character.toChars(cp));
        if (typing == Typing.NONE && (page == 0 && open == null || page == 3)) typing = Typing.SEARCH;
        if (typing == Typing.SEARCH) {
            if (query.length() < 40) query += ch;
            scroll = 0f;
            return true;
        }
        if (typing != Typing.NONE && typed.length() < 64) {
            typed += ch;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        if (typing != Typing.NONE && typing != Typing.SEARCH) submitTyping();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }

    /** Dev screenshots only. */
    public static void devPage(int index, String openMod) {
        if (!(Minecraft.getInstance().screen instanceof GolemsMenu m)) return;
        m.setPage(index);
        if (openMod != null) for (Mods.Mod mod : Mods.all()) if (mod.name().equalsIgnoreCase(openMod)) m.openMod(mod);
    }
}
