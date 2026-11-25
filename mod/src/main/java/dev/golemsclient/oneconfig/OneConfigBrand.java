package dev.golemsclient.oneconfig;

import dev.golemsclient.GolemsClient;
import dev.golemsclient.menu.MenuData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OneConfig's menu (the mod menu OneClient uses, with Golem's Client's branding): Golem's Client's logo where OneConfig's
 * was, "Golem's Client" where it said "OneConfig", and Golem's Client red as its accent colour. The mixins in
 * dev.golemsclient.mixin.oneconfig call in here; nothing happens without OneConfig.
 */
public final class OneConfigBrand {
    private static final int ONECONFIG_BLUE = 0xFF2B4BFF;
    public static final int GOLEMS_RED = 0xFFE5484D;
    private static final String BRAND = "assets/golemsclient/brand/";
    private static final Map<String, String> LOGOS = Map.of(
            "oneconfig.svg", "logo.svg",
            "oneconfig-white.svg", "logo.svg",
            "oneconfig-mc.svg", "logo.svg",
            "oneconfig-light.svg", "logo-dark.svg",
            "oneconfig-mc-light.svg", "logo-dark.svg",
            "oneconfig-icon.svg", "icon.svg");

    private static boolean checked;

    private OneConfigBrand() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (checked) return;
            checked = true;
            redAccent();
        });
    }

    /** OneConfig's logo files, swapped for Golem's Client's. */
    public static String svg(String path) {
        if (path == null) return null;
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (!path.contains("oneconfig/brand/")) return path;
        String ours = LOGOS.get(name);
        return ours == null ? path : (path.startsWith("/") ? "/" : "") + BRAND + ours;
    }

    /** OneConfig's words, with Golem's Client's name. */
    public static String text(String s) {
        return s == null || !s.contains("OneConfig") ? s : s.replace("OneConfig", "Golem's Client");
    }

    /** OneConfig's accent moves from its blue to Golem's Client red, once (a colour picked afterwards stays). */
    private static void redAccent() {
        MenuData data = MenuData.get();
        if (data.oneConfigAccentSet) return;
        try {
            Class<?> themes = Class.forName("org.polyfrost.oneconfig.internal.ThemeConfig");
            Field accent = themes.getField("accentColor");
            Object color = accent.get(null);
            Integer argb = argb(color);
            if (argb != null && argb != ONECONFIG_BLUE && argb != GOLEMS_RED) {
                // already a colour someone picked
                data.oneConfigAccentSet = true;
                data.save();
                return;
            }
            if (argb == null || argb == ONECONFIG_BLUE) {
                accent.set(null, accent.getType().getConstructor(int.class).newInstance(GOLEMS_RED));
                save(themes);
            }
            data.oneConfigAccentSet = true;
            data.save();
        } catch (ClassNotFoundException ignored) {
            // no OneConfig
        } catch (Throwable e) {
            GolemsClient.LOGGER.warn("Couldn't set OneConfig's accent colour", e);
        }
    }

    private static Integer argb(Object color) {
        if (color == null) return null;
        for (String name : new String[] {"getArgb", "argb", "toArgb", "getRgba", "rgba", "getColor"}) {
            try {
                Object v = color.getClass().getMethod(name).invoke(color);
                if (v instanceof Integer i) return i;
            } catch (Throwable ignored) {
            }
        }
        for (Field f : color.getClass().getDeclaredFields()) {
            if (f.getType() == int.class && !Modifier.isStatic(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    return f.getInt(color);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Saves OneConfig's theme settings (through its instance's save()). */
    private static void save(Class<?> themes) {
        try {
            for (Field f : themes.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && themes.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object instance = f.get(null);
                    if (instance == null) continue;
                    Method save = instance.getClass().getMethod("save");
                    save.invoke(instance);
                    return;
                }
            }
        } catch (Throwable e) {
            GolemsClient.LOGGER.debug("Couldn't save OneConfig's themes: {}", e.toString());
        }
    }

    /** Opens the mods menu (OneConfig's). */
    public static void openMenu(Consumer<String> toast) {
        try {
            Class<?> ui = Class.forName("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI");
            Object instance = ui.getField("INSTANCE").get(null);
            for (Method m : ui.getMethods()) {
                if (m.getName().equals("open") && m.getParameterCount() == 0) {
                    m.invoke(Modifier.isStatic(m.getModifiers()) ? null : instance);
                    return;
                }
            }
        } catch (Throwable e) {
            GolemsClient.LOGGER.debug("Couldn't open OneConfig: {}", e.toString());
        }
        toast.accept("Press Right Shift in game to open the mods menu.");
    }
}
