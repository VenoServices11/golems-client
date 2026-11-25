package dev.golemsclient.mods;

import dev.golemsclient.GolemsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Every mod Golem's Client's mod menu can show: the ones whose settings live in OneConfig (shown and edited in the
 * menu itself), and the others that have their own settings screen (Sodium, Zoomify...), which open as they are.
 */
public final class Mods {
    /** One mod. {@code settings} is OneConfig's settings tree (or null); {@code screen} opens the mod's own screen. */
    public record Mod(String key, String name, String description, String modId, Object settings, Function<Screen, Screen> screen) {
        public boolean hasSettings() {
            return settings != null;
        }
    }

    private static List<Mod> cache;

    private Mods() {
    }

    public static List<Mod> all() {
        if (cache == null) cache = load();
        return cache;
    }

    public static void reload() {
        cache = null;
    }

    private static List<Mod> load() {
        Map<String, Mod> out = new LinkedHashMap<>();
        if (FabricLoader.getInstance().isModLoaded("oneconfig")) {
            try {
                for (Mod m : OneConfigMods.mods()) out.put(m.key(), m);
            } catch (Throwable e) {
                GolemsClient.LOGGER.warn("Couldn't read OneConfig's mods", e);
            }
        }
        // mods with their own settings screen (what Mod Menu shows)
        for (EntrypointContainer<Object> c : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
            try {
                String id = c.getProvider().getMetadata().getId();
                if (id.equals(GolemsClient.MOD_ID) || out.values().stream().anyMatch(m -> id.equals(m.modId()))) continue;
                Object api = c.getEntrypoint();
                Object factory = api.getClass().getMethod("getModConfigScreenFactory").invoke(api);
                if (factory == null) continue;
                java.lang.reflect.Method create = null;
                for (java.lang.reflect.Method m : factory.getClass().getMethods()) {
                    if (m.getName().equals("create") && m.getParameterCount() == 1) create = m;
                }
                if (create == null) continue;
                create.setAccessible(true);
                java.lang.reflect.Method make = create;
                Function<Screen, Screen> open = parent -> {
                    try {
                        return (Screen) make.invoke(factory, parent);
                    } catch (Throwable e) {
                        GolemsClient.LOGGER.warn("Couldn't open {}'s settings", id, e);
                        return null;
                    }
                };
                var meta = c.getProvider().getMetadata();
                out.put("mod:" + id, new Mod("mod:" + id, meta.getName(), meta.getDescription(), id, null, open));
            } catch (Throwable e) {
                GolemsClient.LOGGER.debug("Skipped a mod menu entry: {}", e.toString());
            }
        }
        List<Mod> list = new ArrayList<>(out.values());
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return list;
    }

    /** The Fabric mod a OneConfig settings id most likely belongs to ("polysprint.json" -> polysprint). */
    static Optional<ModContainer> guessMod(String treeId, String title) {
        FabricLoader loader = FabricLoader.getInstance();
        String base = treeId == null ? "" : treeId.toLowerCase(Locale.ROOT);
        int cut = base.indexOf('/');
        if (cut >= 0) base = base.substring(0, cut);
        cut = base.indexOf('.');
        if (cut > 0) base = base.substring(0, cut);
        if (!base.isEmpty() && loader.isModLoaded(base)) return loader.getModContainer(base);
        String plain = title == null ? "" : title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (ModContainer m : loader.getAllMods()) {
            String name = m.getMetadata().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!plain.isEmpty() && (name.equals(plain) || m.getMetadata().getId().equals(plain))) return Optional.of(m);
        }
        return Optional.empty();
    }

    /** A title that might be a translation key ("oneconfig.hud") as words. */
    public static String text(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        if (s.matches("^[a-z0-9_]+(\\.[a-z0-9_]+)+$") && Language.getInstance().has(s)) return Language.getInstance().getOrDefault(s);
        return s;
    }
}
