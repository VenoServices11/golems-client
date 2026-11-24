package dev.golemsclient.menu;

import dev.golemsclient.GolemsClient;
import dev.golemsclient.mixin.settings.AccessibilityOptionsAccessor;
import dev.golemsclient.mixin.settings.ChatOptionsAccessor;
import dev.golemsclient.mixin.settings.ControlsOptionsAccessor;
import dev.golemsclient.mixin.settings.FontOptionsAccessor;
import dev.golemsclient.mixin.settings.MouseOptionsAccessor;
import dev.golemsclient.mixin.settings.VideoOptionsAccessor;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Minecraft's options, restyled like OneClient's settings (ported from PolyPlus, GPL-3.0, by Polyfrost): categories on
 * the left (Minecraft's, then Golem's Client's), the options as rows with switches, choices and sliders on the right,
 * and a search box over all of them. It opens wherever Minecraft's own Options screen would.
 */
public final class SettingsScreen extends MenuScreen {
    private static boolean passOnce;

    private final Screen parent;
    private final Options o;
    private final MenuData data = MenuData.get();
    private final List<Category> categories = new ArrayList<>();
    private Category current;
    private List<Section> sections = List.of();
    private String query = "";
    private boolean searching;
    private float scroll, contentH, listY0, listY1;
    private float sideScroll;
    private final int startGuiScale;
    private final int startMipmaps;
    // a slider being dragged
    private Row dragging;
    private float dragX0, dragX1;
    private double dragValue = -1;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("options.title"));
        this.parent = parent;
        this.o = net.minecraft.client.Minecraft.getInstance().options;
        this.startGuiScale = o.guiScale().get();
        this.startMipmaps = o.mipmapLevels().get();
        build();
        open(categories.get(0));
    }

    /** True once after a screen here asked for Minecraft's own Options screen ("Classic options"). */
    public static boolean takePass() {
        boolean once = passOnce;
        passOnce = false;
        return once;
    }

    // ------------------------------------------------------------------ the categories

    private record Category(String label, String icon, String owner, Supplier<List<Section>> sections, Runnable external) { }

    private record Section(String title, List<Row> rows) { }

    /** A row: one of Minecraft's options, or a switch, a choice or a button of ours. */
    private static final class Row {
        String label, hint;
        OptionValues.Info info;
        BooleanSupplier on;
        Runnable flip;
        String[] choices;
        IntSupplier chosen;
        IntConsumer choose;
        String button;
        Runnable press;
        BooleanSupplier enabled = () -> true;

        static Row option(OptionInstance<?> opt) {
            OptionValues.Info info = opt == null ? null : OptionValues.read(opt);
            if (info == null) return null;
            Row r = new Row();
            r.info = info;
            r.label = info.caption();
            r.hint = info.hint();
            return r;
        }

        static Row toggle(String label, String hint, BooleanSupplier on, Runnable flip) {
            Row r = new Row();
            r.label = label;
            r.hint = hint;
            r.on = on;
            r.flip = flip;
            return r;
        }

        static Row choice(String label, String hint, String[] choices, IntSupplier chosen, IntConsumer choose) {
            Row r = new Row();
            r.label = label;
            r.hint = hint;
            r.choices = choices;
            r.chosen = chosen;
            r.choose = choose;
            return r;
        }

        static Row action(String label, String hint, String button, Runnable press) {
            Row r = new Row();
            r.label = label;
            r.hint = hint;
            r.button = button;
            r.press = press;
            return r;
        }
    }

    private void build() {
        boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");
        String mc = "MINECRAFT";
        categories.add(new Category("Video", "image", mc, sodium ? null : () -> List.of(
                section("Display", VideoOptionsAccessor.golemsclient$displayOptions(o)),
                section("Quality", VideoOptionsAccessor.golemsclient$qualityOptions(o)),
                section("Preferences", VideoOptionsAccessor.golemsclient$preferenceOptions(o))), sodium ? this::openSodium : null));
        categories.add(new Category("Sound", "volume", mc, this::sound, null));
        categories.add(new Category("Controls", "zap", mc, () -> {
            List<Row> keys = new ArrayList<>();
            keys.add(Row.action(Component.translatable("controls.keybinds").getString(), "Change what every key does", "Open",
                    () -> minecraft.setScreen(new KeyBindsScreen(this, o))));
            return List.of(new Section("Key binds", keys), section("Controls", ControlsOptionsAccessor.golemsclient$options(o)));
        }, null));
        categories.add(new Category("Mouse", "arrow", mc, () -> List.of(section("Mouse", MouseOptionsAccessor.golemsclient$options(o))), null));
        categories.add(new Category("Skin", "shirt", mc, this::skinSections, null));
        categories.add(new Category("Accessibility", "user", mc, () -> List.of(
                section("Accessibility", AccessibilityOptionsAccessor.golemsclient$options(o)),
                section("Font", FontOptionsAccessor.golemsclient$options(o))), null));
        categories.add(new Category("Language", "globe", mc, null,
                () -> minecraft.setScreen(new LanguageSelectScreen(this, o, minecraft.getLanguageManager()))));
        categories.add(new Category("Chat", "chat", mc, () -> List.of(section("Chat", ChatOptionsAccessor.golemsclient$options(o))), null));
        categories.add(new Category("Online", "cloud", mc, () -> List.of(
                section("Servers", o.allowServerListing()),
                section("Realms", o.realmsNotifications())), null));
        categories.add(new Category("Resource packs", "folder", mc, null, () -> minecraft.setScreen(new PackSelectionScreen(
                minecraft.getResourcePackRepository(), repo -> {
                    o.updateResourcePacks(repo);
                    minecraft.setScreen(this);
                }, minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title")))));
        String gc = "GOLEM'S CLIENT";
        categories.add(new Category("Golem's Client", "logo", gc, this::golems, null));
        categories.add(new Category("Mods", "cube", gc, null, this::openMods));
        categories.add(new Category("Classic options", "settings", gc, null, () -> {
            passOnce = true;
            minecraft.setScreen(new OptionsScreen(this, o));
        }));
    }

    private Section section(String title, OptionInstance<?>... options) {
        List<Row> rows = new ArrayList<>();
        for (OptionInstance<?> opt : options) {
            Row r = Row.option(opt);
            if (r == null) continue;
            if (opt == o.narrator()) r.enabled = () -> minecraft.getNarrator().isActive();
            rows.add(r);
        }
        return new Section(title, rows);
    }

    private List<Section> sound() {
        List<OptionInstance<?>> volumes = new ArrayList<>();
        volumes.add(o.getSoundSourceOptionInstance(SoundSource.MASTER));
        for (SoundSource s : SoundSource.values()) if (s != SoundSource.MASTER) volumes.add(o.getSoundSourceOptionInstance(s));
        return List.of(section("Volume", volumes.toArray(new OptionInstance<?>[0])),
                section("Output", o.soundDevice(), o.directionalAudio(), o.showSubtitles()),
                section("Music", o.musicFrequency(), o.musicToast()));
    }

    private List<Section> skinSections() {
        List<Row> parts = new ArrayList<>();
        for (PlayerModelPart part : PlayerModelPart.values()) {
            parts.add(Row.toggle(part.getName().getString(), null, () -> o.isModelPartEnabled(part), () -> {
                o.setModelPart(part, !o.isModelPartEnabled(part));
                o.save();
            }));
        }
        Section hand = section("Hand", o.mainHand());
        return List.of(new Section("Skin layers", parts), hand);
    }

    private List<Section> golems() {
        List<Row> pause = new ArrayList<>();
        pause.add(Row.choice("Style", "How the pause screen looks", new String[] {"Flat", "Step back"},
                () -> "stepback".equals(data.pauseStyle) ? 1 : 0, i -> {
                    data.pauseStyle = i == 1 ? "stepback" : "flat";
                    data.save();
                }));
        pause.add(Row.toggle("Player preview", "Flat: your player turning beside the menu", () -> data.pausePlayer, () -> {
            data.pausePlayer = !data.pausePlayer;
            data.save();
        }));
        pause.add(Row.toggle("Always pan camera", "Step back: even with reduced motion", () -> data.pauseAlwaysPan, () -> {
            data.pauseAlwaysPan = !data.pauseAlwaysPan;
            data.save();
        }));
        List<Row> players = new ArrayList<>();
        players.add(Row.toggle("G badges", "A G in front of Golem's Client players' names", () -> data.showBadges, () -> {
            data.showBadges = !data.showBadges;
            data.save();
        }));
        List<Row> setup = new ArrayList<>();
        setup.add(Row.action("First-launch setup", "Go through it again on the main menu", "Run again", () -> {
            data.setupDone = false;
            data.save();
            dev.golemsclient.oc.LobbyBridge.setupPending(true);
            showToast("The setup shows next time you're on the main menu.");
        }));
        return List.of(new Section("Pause screen", pause), new Section("Players", players), new Section("Setup", setup));
    }

    private void open(Category c) {
        if (c.external() != null) {
            c.external().run();
            return;
        }
        current = c;
        sections = c.sections().get();
        scroll = 0f;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x990E0E10);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        beginFrame(g, mouseX, mouseY);
        drawTop();
        drawSide();
        drawContent();
        endFrame(g);
    }

    private void drawTop() {
        float y = 14f;
        Overlay.image("/assets/golemsclient/textures/logo.png", PAD * u, y * u, (PAD + 18f) * u, (y + 18f) * u, c(0xFFFFFFFF));
        text(Face.BOLD, "Settings", PAD + 26f, y + 9f - 10f * 0.6f, 10f, TEXT, Align.LEFT);

        // search
        float sw = Math.min(240f, w - 340f), sx0 = w / 2f - sw / 2f, sx1 = sx0 + sw, sy0 = y - 1f, sy1 = y + 19f;
        boolean over = inside(sx0, sy0, sx1, sy1);
        float hv = anim("search", over || searching);
        rect(sx0, sy0, sx1, sy1, 5f, mix(0x0FFFFFFF, 0x1AFFFFFF, hv));
        outline(sx0, sy0, sx1, sy1, 5f, searching ? alpha(ACCENT, 0.8f) : LINE);
        Overlay.icon("search", (sx0 + 7f) * u, (y + 9f - 4f) * u, 8f * u, c(TEXT_3));
        String shown = query.isEmpty() ? "Search settings" : query;
        float tx = sx0 + 20f;
        text(Face.REGULAR, fit(Face.REGULAR, shown, 7f, sw - 30f), tx, y + 9f - 7f * 0.6f, 7f, query.isEmpty() ? TEXT_3 : TEXT, Align.LEFT);
        if (searching && (System.currentTimeMillis() / 530L) % 2L == 0L) {
            float cx = tx + (query.isEmpty() ? 0f : Math.min(sw - 30f, textW(Face.REGULAR, query, 7f))) + 1f;
            rect(cx, y + 4f, cx + 0.75f, y + 14f, 0f, TEXT);
        }
        hit(sx0, sy0, sx1, sy1, () -> searching = true);
        if (over) pointer = true;

        // done
        String done = Component.translatable("gui.done").getString();
        float bw = textW(Face.SEMIBOLD, done, 7.5f) + 28f, bx1 = w - PAD, bx0 = bx1 - bw;
        boolean overDone = inside(bx0, sy0, bx1, sy1);
        float hd = anim("done", overDone);
        rect(bx0, sy0, bx1, sy1, 5f, mix(ACCENT, 0xFFF0676B, hd));
        text(Face.SEMIBOLD, done, (bx0 + bx1) / 2f, y + 9f - 7.5f * 0.6f, 7.5f, 0xFFFFFFFF, Align.CENTER);
        hit(bx0, sy0, bx1, sy1, this::onClose);
    }

    private void drawSide() {
        float x0 = PAD, x1 = PAD + 150f, y0 = 46f, y1 = H - 16f;
        float slide = (1f - fade) * -8f;
        x0 += slide;
        x1 += slide;
        card(x0, y0, x1, y1);
        float y = y0 + 10f - sideScroll;
        String group = null;
        Overlay.clip(0f, (y0 + 2f) * u, w * u, (y1 - 2f) * u);
        for (Category c : categories) {
            if (!c.owner().equals(group)) {
                if (group != null) y += 6f;
                group = c.owner();
                text(Face.MONO, group, x0 + 12f, y, 5.5f, TEXT_3, Align.LEFT);
                y += 11f;
            }
            boolean active = c == current && query.isEmpty();
            boolean over = inside(x0 + 6f, y, x1 - 6f, y + 20f);
            float hv = anim("cat:" + c.label(), over || active);
            if (hv > 0.01f) rect(x0 + 6f, y, x1 - 6f, y + 20f, 4f, alpha(active ? 0x24FFFFFF : 0x14FFFFFF, hv));
            if (active) rect(x0 + 6f, y + 5f, x0 + 7.5f, y + 15f, 0.75f, ACCENT);
            if (c.icon().equals("logo")) {
                Overlay.image("/assets/golemsclient/textures/logo.png", (x0 + 13f) * u, (y + 5f) * u, (x0 + 23f) * u, (y + 15f) * u, c(0xFFFFFFFF));
            } else {
                Overlay.icon(c.icon(), (x0 + 13f) * u, (y + 5.5f) * u, 9f * u, c(active ? TEXT : mix(TEXT_2, TEXT, hv)));
            }
            text(Face.MEDIUM, c.label(), x0 + 29f, y + 10f - 7.5f * 0.6f, 7.5f, active ? TEXT : mix(TEXT_2, TEXT, hv), Align.LEFT);
            if (c.external() != null) Overlay.icon("arrow", (x1 - 18f) * u, (y + 6.5f) * u, 7f * u, c(alpha(TEXT_3, 0.8f)));
            float fy = y;
            if (fy + 20f > y0 && fy < y1) hit(x0 + 6f, y, x1 - 6f, y + 20f, () -> {
                query = "";
                searching = false;
                open(c);
            });
            y += 21f;
        }
        Overlay.unclip();
        float content = y + sideScroll - (y0 + 10f);
        sideScroll = clamp(sideScroll, 0f, Math.max(0f, content - (y1 - y0 - 20f)));
    }

    private void drawContent() {
        float x0 = PAD + 158f, x1 = w - PAD, y0 = 46f, y1 = H - 16f;
        float slide = (1f - fade) * 8f;
        x0 += slide;
        x1 += slide;
        card(x0, y0, x1, y1);
        float px = x0 + 18f, iw = x1 - x0 - 36f;
        List<Section> shown;
        String title, owner;
        if (!query.isEmpty()) {
            shown = searchResults();
            title = "Search";
            owner = shown.isEmpty() ? "NOTHING FOUND" : "RESULTS FOR \"" + query.toUpperCase(Locale.ROOT) + "\"";
        } else {
            shown = sections;
            title = current.label();
            owner = current.owner();
        }
        text(Face.BOLD, title, px, y0 + 14f, 12f, TEXT, Align.LEFT);
        text(Face.MONO, fit(Face.MONO, owner, 5.5f, iw), px, y0 + 31f, 5.5f, TEXT_3, Align.LEFT);
        listY0 = y0 + 44f;
        listY1 = y1 - 8f;
        Overlay.clip(0f, listY0 * u, w * u, listY1 * u);
        float y = listY0 - scroll;
        for (Section s : shown) {
            if (s.rows().isEmpty()) continue;
            if (y + 12f > listY0 - 20f && y < listY1) text(Face.MONO, s.title().toUpperCase(Locale.ROOT), px, y + 2f, 5.5f, TEXT_3, Align.LEFT);
            y += 13f;
            for (int i = 0; i < s.rows().size(); i++) {
                Row r = s.rows().get(i);
                float rh = 30f;
                if (y + rh > listY0 && y < listY1) row(r, px, y, px + iw, rh, i == s.rows().size() - 1);
                y += rh;
            }
            y += 12f;
        }
        Overlay.unclip();
        contentH = y + scroll - listY0;
        float view = listY1 - listY0;
        scroll = clamp(scroll, 0f, Math.max(0f, contentH - view));
        if (contentH > view) {
            float bar = view * view / contentH, at = (view - bar) * scroll / (contentH - view);
            rect(x1 - 6f, listY0 + at, x1 - 4.5f, listY0 + at + bar, 0.75f, 0x40FFFFFF);
        }
        if (shown.isEmpty() || shown.stream().allMatch(s -> s.rows().isEmpty())) {
            text(Face.SEMIBOLD, "No settings match that", (x0 + x1) / 2f, listY0 + 40f, 8.5f, TEXT, Align.CENTER);
            text(Face.REGULAR, "Try another word, like \"FOV\" or \"chat\".", (x0 + x1) / 2f, listY0 + 54f, 6.5f, TEXT_2, Align.CENTER);
        }
    }

    private List<Section> searchResults() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Section> out = new ArrayList<>();
        for (Category c : categories) {
            if (c.sections() == null) continue;
            List<Row> hits = new ArrayList<>();
            for (Section s : c.sections().get()) {
                for (Row r : s.rows()) {
                    if (r.label.toLowerCase(Locale.ROOT).contains(q) || (r.hint != null && r.hint.toLowerCase(Locale.ROOT).contains(q))) hits.add(r);
                }
            }
            if (!hits.isEmpty()) out.add(new Section(c.label(), hits));
        }
        return out;
    }

    // ------------------------------------------------------------------ a row

    private void row(Row r, float x0, float y, float x1, float h, boolean last) {
        boolean enabled = r.enabled.getAsBoolean();
        float cw = Math.min(170f, (x1 - x0) * 0.45f), cx0 = x1 - cw;
        float labelW = cx0 - x0 - 30f;
        boolean overRow = inside(x0 - 8f, y, x1 + 8f, y + h);
        float hv = anim("row:" + System.identityHashCode(r), overRow && enabled);
        if (hv > 0.01f) rect(x0 - 8f, y + 1f, x1 + 8f, y + h - 1f, 4f, alpha(0x0DFFFFFF, hv));
        int ink = enabled ? TEXT : TEXT_3;
        if (r.hint != null) {
            text(Face.SEMIBOLD, fit(Face.SEMIBOLD, r.label, 7.5f, labelW), x0, y + 6f, 7.5f, ink, Align.LEFT);
            text(Face.REGULAR, fit(Face.REGULAR, r.hint, 6f, labelW), x0, y + 17f, 6f, TEXT_3, Align.LEFT);
            if (overRow && textW(Face.REGULAR, r.hint, 6f) > labelW) tooltip = r.hint;
        } else {
            text(Face.SEMIBOLD, fit(Face.SEMIBOLD, r.label, 7.5f, labelW), x0, y + h / 2f - 7.5f * 0.6f, 7.5f, ink, Align.LEFT);
        }
        if (!last) rect(x0, y + h - 0.5f, x1, y + h, 0f, 0x0FFFFFFF);
        float cy = y + h / 2f;

        if (r.info != null) {
            OptionValues.Info info = r.info;
            Object value = info.option().get();
            // put it back the way it was
            Object initial = OptionValues.defaultValue(info.option());
            if (enabled && initial != null && !initial.equals(value)) {
                smallIcon("refresh", cx0 - 16f, cy, "Reset to default", () -> {
                    OptionValues.set(info, initial);
                    changed(info);
                });
            }
            switch (info.kind()) {
                case TOGGLE -> toggle(x1, cy, Boolean.TRUE.equals(value), enabled, "t:" + System.identityHashCode(r), () -> {
                    OptionValues.set(info, !Boolean.TRUE.equals(value));
                    changed(info);
                });
                case CHOICE -> {
                    List<Object> list = new ArrayList<>(info.choices());
                    if (!list.contains(value)) list.add(0, value);
                    int at = list.indexOf(value);
                    String label = OptionValues.valueText(info, value);
                    stepper(cx0, cy, x1, label, enabled, "c:" + System.identityHashCode(r), step -> {
                        Object next = list.get(Math.floorMod(at + step, list.size()));
                        OptionValues.set(info, next);
                        changed(info);
                    });
                }
                case SLIDER -> {
                    double p = r == dragging && dragValue >= 0 ? dragValue : OptionValues.toSlider(info, value);
                    Object shownValue = r == dragging && dragValue >= 0 ? OptionValues.fromSlider(info, dragValue) : value;
                    slider(r, cx0, cy, x1, p, OptionValues.valueText(info, shownValue), enabled);
                }
            }
        } else if (r.on != null) {
            toggle(x1, cy, r.on.getAsBoolean(), enabled, "t:" + System.identityHashCode(r), r.flip);
        } else if (r.choices != null) {
            int at = r.chosen.getAsInt();
            stepper(cx0, cy, x1, r.choices[Math.floorMod(at, r.choices.length)], enabled, "c:" + System.identityHashCode(r),
                    step -> r.choose.accept(Math.floorMod(at + step, r.choices.length)));
        } else if (r.button != null) {
            float bw = textW(Face.SEMIBOLD, r.button, 7f) + 24f, bx0 = x1 - bw;
            boolean over = inside(bx0, cy - 10f, x1, cy + 10f);
            float hb = anim("b:" + System.identityHashCode(r), over);
            rect(bx0, cy - 10f, x1, cy + 10f, 5f, mix(0x14FFFFFF, 0x2EFFFFFF, hb));
            outline(bx0, cy - 10f, x1, cy + 10f, 5f, LINE);
            text(Face.SEMIBOLD, r.button, (bx0 + x1) / 2f, cy - 7f * 0.6f, 7f, TEXT, Align.CENTER);
            if (inView(cy)) hit(bx0, cy - 10f, x1, cy + 10f, r.press);
        }
    }

    private boolean inView(float cy) {
        return cy > listY0 + 4f && cy < listY1 - 4f;
    }

    /** After one of Minecraft's options changed: saved, and the interface size takes effect. */
    private void changed(OptionValues.Info info) {
        if ((Object) info.option() == o.guiScale()) minecraft.resizeDisplay();
        o.save();
    }

    private void toggle(float x1, float cy, boolean on, boolean enabled, String id, Runnable flip) {
        float sw = 22f, sh = 12f, sx = x1 - sw, sy = cy - sh / 2f;
        float k = anim(id, on);
        rect(sx, sy, sx + sw, sy + sh, sh / 2f, enabled ? mix(0xFF2A2A30, ACCENT, k) : 0xFF1F1F24);
        Overlay.circle((sx + sh / 2f + (sw - sh) * k) * u, cy * u, (sh / 2f - 2f) * u, c(enabled ? 0xFFFFFFFF : 0xFF6E6E76));
        if (enabled && inView(cy)) hit(sx - 6f, sy - 4f, sx + sw + 2f, sy + sh + 4f, flip);
    }

    private void stepper(float x0, float cy, float x1, String label, boolean enabled, String id, IntConsumer step) {
        float h = 20f, y0 = cy - h / 2f;
        rect(x0, y0, x1, y0 + h, 5f, 0x0FFFFFFF);
        outline(x0, y0, x1, y0 + h, 5f, LINE);
        text(Face.MEDIUM, fit(Face.MEDIUM, label, 7f, x1 - x0 - 36f), (x0 + x1) / 2f, cy - 7f * 0.6f, 7f, enabled ? TEXT : TEXT_3, Align.CENTER);
        for (int dir : new int[] {-1, 1}) {
            float ax0 = dir < 0 ? x0 : x1 - 18f, ax1 = ax0 + 18f;
            boolean over = inside(ax0, y0, ax1, y0 + h);
            float hv = anim(id + dir, over);
            Overlay.icon(dir < 0 ? "arrow_left" : "arrow", (ax0 + 5f) * u, (cy - 4f) * u, 8f * u, c(enabled ? mix(TEXT_2, TEXT, hv) : TEXT_3));
            if (enabled && inView(cy)) hit(ax0, y0, ax1, y0 + h, () -> step.accept(dir));
        }
        if (enabled && inView(cy)) hit(x0 + 18f, y0, x1 - 18f, y0 + h, () -> step.accept(1));
    }

    private void slider(Row r, float x0, float cy, float x1, double p, String label, boolean enabled) {
        float vw = Math.max(34f, Math.min(60f, textW(Face.MONO, label, 6f) + 6f));
        float tx0 = x0 + 4f, tx1 = x1 - vw - 6f;
        rect(tx0, cy - 1f, tx1, cy + 1f, 1f, 0x26FFFFFF);
        float kx = tx0 + (tx1 - tx0) * (float) p;
        rect(tx0, cy - 1f, kx, cy + 1f, 1f, enabled ? ACCENT : TEXT_3);
        Overlay.rect((kx - 3.5f) * u, (cy - 3.5f) * u, (kx + 3.5f) * u, (cy + 3.5f) * u, 0f, c(enabled ? 0xFFFFFFFF : 0xFF6E6E76));
        text(Face.MONO, fit(Face.MONO, label, 6f, vw), x1, cy - 6f * 0.62f, 6f, TEXT_2, Align.RIGHT);
        if (enabled && inView(cy)) {
            hit(tx0 - 4f, cy - 8f, tx1 + 4f, cy + 8f, () -> {
                dragging = r;
                dragX0 = tx0;
                dragX1 = tx1;
                dragTo(mx);
            });
        }
    }

    private void dragTo(float mouseX) {
        if (dragging == null) return;
        double p = clamp((mouseX - dragX0) / Math.max(1f, dragX1 - dragX0), 0f, 1f);
        OptionValues.Info info = dragging.info;
        // snap to the option's own steps, so the text and the knob agree
        Object v = OptionValues.fromSlider(info, p);
        dragValue = OptionValues.toSlider(info, v);
        if (info.live()) {
            OptionValues.set(info, v);
            if ((Object) info.option() == o.guiScale()) return;
        }
    }

    private void endDrag() {
        if (dragging == null) return;
        OptionValues.Info info = dragging.info;
        if (dragValue >= 0) OptionValues.set(info, OptionValues.fromSlider(info, dragValue));
        changed(info);
        dragging = null;
        dragValue = -1;
    }

    // ------------------------------------------------------------------ actions

    private void openSodium() {
        for (String name : new String[] {"net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen",
                "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"}) {
            try {
                Object screen = Class.forName(name).getMethod("createScreen", Screen.class).invoke(null, this);
                if (screen instanceof Screen s) {
                    minecraft.setScreen(s);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        minecraft.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(this, minecraft, o));
    }

    private void openMods() {
        try {
            Class<?> ui = Class.forName("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI");
            Object instance = ui.getField("INSTANCE").get(null);
            for (java.lang.reflect.Method m : ui.getMethods()) {
                if (m.getName().equals("open") && m.getParameterCount() == 0) {
                    m.invoke(instance);
                    return;
                }
            }
            for (java.lang.reflect.Method m : ui.getMethods()) {
                if (m.getName().equals("open$default") && m.getParameterCount() == 4) {
                    m.invoke(null, instance, null, 1, null);
                    return;
                }
            }
        } catch (Throwable e) {
            GolemsClient.LOGGER.debug("Couldn't open OneConfig: {}", e.toString());
        }
        showToast("Press Right Shift in game to open the mods menu.");
    }

    @Override
    public void onClose() {
        if (o.guiScale().get() != startGuiScale) minecraft.resizeDisplay();
        if (o.mipmapLevels().get() != startMipmaps) {
            minecraft.updateMaxMipLevel(o.mipmapLevels().get());
            minecraft.delayTextureReload();
        }
        o.save();
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean wasSearching = searching;
        searching = false;
        if (clickHit(event)) return true;
        if (wasSearching) return true;
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
            endDrag();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float x = (float) mouseX / u;
        if (x < PAD + 154f) sideScroll -= (float) scrollY * 24f;
        else scroll -= (float) scrollY * 30f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!query.isEmpty() || searching) {
                query = "";
                searching = false;
                scroll = 0f;
                return true;
            }
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && event.hasControlDown()) {
            searching = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            scroll = 0f;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (Character.isISOControl(cp) || query.length() >= 40) return false;
        // typing anywhere searches
        query += new String(Character.toChars(cp));
        searching = true;
        scroll = 0f;
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** Dev screenshots only: opens a category by name, or searches. */
    public static void dev(String category, String search) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof SettingsScreen s)) return;
        if (search != null) {
            s.query = search;
            return;
        }
        for (Category c : s.categories) if (c.label().equals(category) && c.sections() != null) s.open(c);
    }
}
