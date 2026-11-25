package dev.golemsclient.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.golemsclient.GolemsClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The key that opens Golem's Client's mod menu (Right Shift unless you pick another, in setup or Keybinds). OneConfig's
 * own Right Shift is turned off, so its menu doesn't open as well.
 */
public final class GolemsMenuKey {
    private static KeyMapping key;
    private static boolean quietedOneConfig;

    private GolemsMenuKey() {
    }

    public static void register() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.golemsclient.menu", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!quietedOneConfig) quietOneConfig();
            while (key.consumeClick()) GolemsMenu.open();
        });
    }

    public static KeyMapping key() {
        return key;
    }

    /** True when this key press is the menu key (to close the menu with it too). */
    public static boolean matches(KeyEvent event) {
        return key != null && key.matches(event);
    }

    /** OneConfig's Right Shift (and its quick menu's backslash) do nothing now: Golem's Client's menu opens instead. */
    private static void quietOneConfig() {
        quietedOneConfig = true;
        try {
            Class<?> config = Class.forName("org.polyfrost.oneconfig.internal.OneConfigConfig");
            kotlin.jvm.functions.Function1<Boolean, Boolean> none = pressed -> true;
            config.getMethod("setOpenAction", kotlin.jvm.functions.Function1.class).invoke(null, none);
            config.getMethod("setMinimalOpenAction", kotlin.jvm.functions.Function1.class).invoke(null, none);
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable e) {
            GolemsClient.LOGGER.warn("Couldn't turn off OneConfig's menu key", e);
        }
    }
}
