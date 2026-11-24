package dev.golemsclient.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.golemsclient.GolemsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What the main menu remembers for this instance (config/golemsclient/menu.json): the servers played most recently,
 * whether the play card was last on Singleplayer or Multiplayer, and the last announcement dismissed.
 */
public final class MenuData {
    public static final int MAX_RECENT = 6;

    /** A server played, newest first in {@link #servers}. */
    public static final class Server {
        public String name;
        public String ip;
        public long lastPlayed;

        Server(String name, String ip, long lastPlayed) {
            this.name = name;
            this.ip = ip;
            this.lastPlayed = lastPlayed;
        }
    }

    public List<Server> servers = new ArrayList<>();
    public boolean singleplayer;
    public String dismissedAnnouncement = "";
    /** The newest News post looked at (a dot on the News tab until there's a newer one). */
    public String lastNewsSeen = "";
    /** The music volume before the menu's mute button was pressed (-1 when not muted by it). */
    public double mutedMusicVolume = -1;
    /** The G badge on Golem's Client players' nametags and in the tab list. */
    public boolean showBadges = true;

    // the pause menu (OneClient's): "flat" (blurred game, your player model) or "stepback" (the camera films your player)
    public String pauseStyle = "flat";
    /** Step back pans the camera even with reduced motion (OneClient's "Always pan camera"). */
    public boolean pauseAlwaysPan = false;
    /** Flat shows your player model. */
    public boolean pausePlayer = true;

    /** The first-launch setup was finished (or skipped). */
    public boolean setupDone = false;

    // the lobby (the world behind the menu)
    public boolean lobbyEnabled = true;
    /** "auto" (the season's scene) or a scene id. */
    public String lobbyScene = "auto";
    /** "scene" (as captured), "day", "sunset", "night" or "real" (follows your clock). */
    public String lobbyTime = "scene";
    public boolean lobbyOpening = true;
    public boolean captureScenes = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MenuData instance;

    public static MenuData get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** Remembers a server as just played (called when joining one, from anywhere). */
    public static void played(String name, String ip) {
        if (ip == null || ip.isBlank()) return;
        MenuData d = get();
        d.servers.removeIf(s -> s.ip.equalsIgnoreCase(ip));
        d.servers.add(0, new Server(name == null || name.isBlank() ? ip : name, ip, System.currentTimeMillis()));
        while (d.servers.size() > MAX_RECENT) d.servers.remove(d.servers.size() - 1);
        d.singleplayer = false;
        d.save();
    }

    public void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("menu.json.tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't save the menu's settings", e);
        }
    }

    private static MenuData load() {
        try {
            Path file = file();
            if (Files.exists(file)) {
                MenuData d = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MenuData.class);
                if (d != null) {
                    if (d.servers == null) d.servers = new ArrayList<>();
                    d.servers.removeIf(s -> s == null || s.ip == null || s.ip.isBlank());
                    if (d.dismissedAnnouncement == null) d.dismissedAnnouncement = "";
                    if (d.lastNewsSeen == null) d.lastNewsSeen = "";
                    if (d.lobbyScene == null) d.lobbyScene = "auto";
                    if (d.lobbyTime == null) d.lobbyTime = "scene";
                    return d;
                }
            }
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't read the menu's settings", e);
        }
        return new MenuData();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("golemsclient").resolve("menu.json");
    }
}
