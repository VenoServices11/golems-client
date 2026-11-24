package dev.golemsclient.lobby;

import dev.golemsclient.GolemsClient;
import dev.golemsclient.menu.MenuData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Scenes saved from servers you play: when you leave a server (after playing there a while), the spot where you were
 * becomes a lobby scene named after the server ("DonutSMP"), replacing the last one from that server. Off in the
 * lobby settings. Nothing is sent anywhere; scenes stay in config/golemsclient/scenes.
 */
public final class Captures {
    private static final long MIN_PLAY_MS = 60_000L;
    private static long joinedAt;
    private static String joinedServer;

    private Captures() {
    }

    /** Called every tick: notes when you joined a server. */
    public static void tick(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        boolean onServer = mc.level != null && server != null && !mc.isLocalServer();
        String key = onServer ? server.ip : null;
        if (key == null) {
            joinedServer = null;
        } else if (!key.equals(joinedServer)) {
            joinedServer = key;
            joinedAt = System.currentTimeMillis();
        }
    }

    /** Called right before leaving a world: saves the scene if you were on a server long enough. */
    public static void beforeLeaving(Minecraft mc) {
        try {
            ServerData server = mc.getCurrentServer();
            if (!MenuData.get().captureScenes || mc.level == null || mc.player == null || server == null || mc.isLocalServer()) return;
            if (joinedServer == null || System.currentTimeMillis() - joinedAt < MIN_PLAY_MS) return;
            long started = System.currentTimeMillis();
            Scene s = ShotPicker.shoot(mc.level, mc.player.blockPosition(), false, null, 64, 30, 40);
            if (s == null) {
                GolemsClient.LOGGER.info("No good lobby shot where you were on {}", server.ip);
                return;
            }
            String name = server.name == null || server.name.isBlank() ? server.ip : server.name;
            String file = server.ip.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
            s.id = "capture:" + file;
            s.name = name;
            Path path = Scenes.capturesDir().resolve(file + ".nbt");
            s.write(path);
            GolemsClient.LOGGER.info("Saved a lobby scene from {} in {} ms", server.ip, System.currentTimeMillis() - started);
        } catch (Throwable t) {
            GolemsClient.LOGGER.warn("Couldn't save a lobby scene from this server", t);
        }
    }
}
