package dev.golemsclient.menu;

import dev.golemsclient.GolemsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.server.network.EventLoopGroupHolder;

/**
 * Asks one server how many players are on and how long an answer takes, the way the server list does, for the main
 * menu's play card. Saved servers keep their name and icon from the server list.
 */
final class ServerPing {
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    /** Icons servers sent this session, so recents keep theirs after another one is picked. */
    private static final java.util.Map<String, byte[]> ICONS = new java.util.concurrent.ConcurrentHashMap<>();
    private final ServerList saved;
    private ServerData data;
    private boolean pinged;
    private long startedAt;

    ServerPing() {
        saved = new ServerList(Minecraft.getInstance());
        saved.load();
    }

    /** The server shown now (pinged once when first shown). */
    ServerData show(String name, String ip) {
        if (data == null || !data.ip.equalsIgnoreCase(ip)) {
            ServerData known = saved.get(ip);
            data = new ServerData(known != null ? known.name : name, ip, ServerData.Type.OTHER);
            byte[] icon = ICONS.get(ip.toLowerCase(java.util.Locale.ROOT));
            if (icon == null && known != null) icon = known.getIconBytes();
            if (icon != null) data.setIconBytes(icon);
            data.ping = -1L;
            pinged = false;
        }
        if (!pinged) {
            pinged = true;
            startedAt = System.currentTimeMillis();
            ServerData target = data;
            Thread t = new Thread(() -> {
                try {
                    pinger.pingServer(target, () -> { }, () -> { },
                            EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport()));
                } catch (Exception e) {
                    GolemsClient.LOGGER.debug("Couldn't ping {}", target.ip, e);
                    target.ping = -2L;
                }
            }, "Golem's Client server ping");
            t.setDaemon(true);
            t.start();
        }
        return data;
    }

    /** True while the answer may still come (a few seconds after asking). */
    boolean waiting() {
        return data != null && data.players == null && System.currentTimeMillis() - startedAt < 7000L;
    }

    /** A server's icon: from its answer if it was pinged here, else from the saved server list. */
    byte[] icon(String ip) {
        if (data != null && data.ip.equalsIgnoreCase(ip) && data.getIconBytes() != null) {
            ICONS.put(ip.toLowerCase(java.util.Locale.ROOT), data.getIconBytes());
        }
        byte[] seen = ICONS.get(ip.toLowerCase(java.util.Locale.ROOT));
        if (seen != null) return seen;
        ServerData known = saved.get(ip);
        return known != null ? known.getIconBytes() : null;
    }

    /** Pings again (the refresh button). */
    void refresh() {
        pinged = false;
    }

    void tick() {
        pinger.tick();
    }

    void stop() {
        pinger.removeAll();
    }
}
