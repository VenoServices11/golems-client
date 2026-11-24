package dev.golemsclient;

import dev.golemsclient.menu.MainMenu;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

/**
 * Where you are, for your Discord status: the launcher reads these lines from the game's log ("[GolemsStatus] lobby",
 * "singleplayer", "multiplayer" or "server donutsmp.net") and shows "In the lobby", "Singleplayer" or "Playing on donutsmp.net".
 * A line is written only when it changes.
 */
public final class DiscordStatus {
    private static String last;
    private static int ticks;

    private DiscordStatus() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++ticks % 20 != 0) return;
            String now = place(mc);
            if (now == null || now.equals(last)) return;
            last = now;
            GolemsClient.LOGGER.info("[GolemsStatus] {}", now);
        });
    }

    /** Where you are, or null while it's not clear (loading, a settings screen before the menu showed). */
    private static String place(Minecraft mc) {
        if (mc.level == null) return mc.screen instanceof MainMenu ? "lobby" : null;
        if (mc.hasSingleplayerServer()) return "singleplayer";
        ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null) return null;
        String host = server.ip.trim().toLowerCase(Locale.ROOT);
        if (host.endsWith(":25565")) host = host.substring(0, host.length() - 6);
        if (host.isEmpty() || host.length() > 64 || !host.matches("[a-z0-9._:\\[\\]-]+")) return "multiplayer";
        return "server " + host;
    }
}
