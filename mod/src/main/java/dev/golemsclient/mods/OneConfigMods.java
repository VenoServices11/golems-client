package dev.golemsclient.mods;

import dev.golemsclient.GolemsClient;
import net.fabricmc.loader.api.ModContainer;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Node;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * OneConfig's side of the mod menu (only touched when OneConfig is installed): its mods, their settings as a flat list
 * of rows, reading and changing them, and saving. Profiles and HUD pieces too.
 */
public final class OneConfigMods {
    /** One setting: what kind of control it gets ({@code kind} is OneConfig's visualizer name, e.g. "SwitchVisualizer"). */
    public record Setting(String section, String title, String description, String kind, Property<?> property) { }

    private OneConfigMods() {
    }

    static List<Mods.Mod> mods() {
        List<Mods.Mod> out = new ArrayList<>();
        Collection<Tree> trees = ConfigManager.active().trees();
        for (Tree t : trees) {
            String id = t.getID();
            if (id == null || id.startsWith("huds") || id.startsWith("profiles") || Boolean.TRUE.equals(t.getMetadata("hidden"))) continue;
            Object title = t.getTitle();
            if (title == null) continue;
            String name = Mods.text(title);
            if (name.isBlank() || countSettings(t) == 0) continue;
            Optional<ModContainer> mod = Mods.guessMod(id, name);
            String description = mod.map(m -> m.getMetadata().getDescription()).orElse("");
            String category = Mods.text(t.getMetadata("category"));
            if (description.isBlank()) description = category;
            out.add(new Mods.Mod("oc:" + id, name, description, mod.map(m -> m.getMetadata().getId()).orElse(null), t, null));
        }
        return out;
    }

    private static int countSettings(Tree t) {
        int[] n = {0};
        t.onAllProps((k, p) -> n[0]++);
        return n[0];
    }

    /** A mod's settings, in order, each with the section it's in. */
    public static List<Setting> settings(Object settings) {
        List<Setting> out = new ArrayList<>();
        walk((Tree) settings, "", out);
        return out;
    }

    private static void walk(Tree tree, String parent, List<Setting> out) {
        for (Node n : tree.map.values()) {
            if (Boolean.TRUE.equals(n.getMetadata("hidden"))) continue;
            if (n instanceof Tree sub) {
                String title = Mods.text(sub.getTitle());
                walk(sub, title.isBlank() ? parent : title, out);
            } else if (n instanceof Property<?> p) {
                if (!p.canDisplay()) continue;
                Object vis = p.getMetadata("visualizer");
                if (vis == null) continue;
                String kind = vis instanceof Class<?> c ? c.getSimpleName() : vis.getClass().getSimpleName();
                String category = Mods.text(p.getMetadata("category")), sub = Mods.text(p.getMetadata("subcategory"));
                String section = !sub.isBlank() ? (category.isBlank() || category.equalsIgnoreCase("general") ? sub : category + " · " + sub)
                        : !category.isBlank() ? category : parent;
                out.add(new Setting(section, Mods.text(p.getTitle()), Mods.text(p.description), kind, p));
            }
        }
    }

    /** The mod's on/off switch ("enabled" at the top of its settings), or null. */
    @SuppressWarnings("unchecked")
    public static Property<Boolean> enabled(Object settings) {
        if (!(settings instanceof Tree t)) return null;
        Node n = t.map.get("enabled");
        if (n instanceof Property<?> p && (p.type == Boolean.class || p.type == boolean.class)) return (Property<Boolean>) p;
        return null;
    }

    public static Object get(Property<?> p) {
        return p.get();
    }

    /** Sets a setting and saves the mod's settings. */
    public static void set(Object settings, Property<?> p, Object value) {
        try {
            p.setAs(value);
            ConfigManager.active().save((Tree) settings);
        } catch (Throwable e) {
            GolemsClient.LOGGER.warn("Couldn't change {}", p.getID(), e);
        }
    }

    public static Class<?> type(Property<?> p) {
        return p.type;
    }

    public static <M> M meta(Property<?> p, String key) {
        return p.getMetadata(key);
    }

    // ------------------------------------------------------------------ profiles

    public static List<String> profiles() {
        return ConfigManager.profiles();
    }

    public static String activeProfile() {
        return ConfigManager.activeProfile();
    }

    public static void openProfile(String name) {
        ConfigManager.openProfile(name);
        Mods.reload();
    }

    public static void createProfile(String name) {
        ConfigManager.createProfile(name);
    }

    public static void cloneProfile(String from, String name) {
        ConfigManager.cloneProfile(from, name);
    }

    public static void renameProfile(String from, String name) {
        ConfigManager.renameProfile(from, name);
    }

    public static void deleteProfile(String name) {
        ConfigManager.deleteProfile(name);
    }

    public static boolean isFavoriteProfile(String name) {
        return ConfigManager.isFavoriteProfile(name);
    }

    public static void setFavoriteProfile(String name, boolean on) {
        ConfigManager.setFavoriteProfile(name, on);
    }

    // ------------------------------------------------------------------ HUD pieces

    /** A HUD piece: its name, and whether it's showing. */
    public record HudPiece(Object hud, String name, String category) { }

    public static List<HudPiece> huds() {
        List<HudPiece> out = new ArrayList<>();
        try {
            Class<?> manager = Class.forName("org.polyfrost.oneconfig.api.hud.v1.HudManager");
            Object instance = manager.getField("INSTANCE").get(null);
            List<?> list = (List<?>) manager.getMethod("instances").invoke(instance);
            for (Object hud : list) {
                String name = hud instanceof org.polyfrost.oneconfig.api.config.v1.Config c ? Mods.text(c.getTree().getTitle()) : hud.toString();
                String category = "";
                try {
                    Object cat = hud.getClass().getMethod("getCategory").invoke(hud);
                    category = Mods.text(cat.getClass().getMethod("getName").invoke(cat));
                } catch (Throwable ignored) {
                }
                out.add(new HudPiece(hud, name, category));
            }
            out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        } catch (Throwable e) {
            GolemsClient.LOGGER.debug("Couldn't list the HUD pieces: {}", e.toString());
        }
        return out;
    }

    public static boolean hudShown(HudPiece piece) {
        try {
            return !(boolean) piece.hud().getClass().getMethod("getHidden").invoke(piece.hud());
        } catch (Throwable e) {
            return true;
        }
    }

    public static void setHudShown(HudPiece piece, boolean shown) {
        try {
            piece.hud().getClass().getMethod("setHidden", boolean.class).invoke(piece.hud(), !shown);
            if (piece.hud() instanceof org.polyfrost.oneconfig.api.config.v1.Config c) c.save();
        } catch (Throwable e) {
            GolemsClient.LOGGER.warn("Couldn't change a HUD piece", e);
        }
    }

    /** OneConfig's HUD layout editor (drag and resize over the game). */
    public static void openHudEditor() {
        try {
            Class<?> manager = Class.forName("org.polyfrost.oneconfig.api.hud.v1.HudManager");
            manager.getMethod("openEditor").invoke(manager.getField("INSTANCE").get(null));
        } catch (Throwable e) {
            GolemsClient.LOGGER.warn("Couldn't open the HUD editor", e);
        }
    }

    /** OneConfig's own screen for a mod's settings (for the few kinds of setting the menu doesn't draw yet). */
    public static void openClassic(Object settings) {
        try {
            Class<?> screens = Class.forName("org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt");
            screens.getMethod("openUI", Tree.class).invoke(null, settings);
        } catch (Throwable e) {
            GolemsClient.LOGGER.warn("Couldn't open OneConfig's screen", e);
        }
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
