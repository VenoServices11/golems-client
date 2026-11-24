package dev.golemsclient.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.golemsclient.GolemsClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * How long you play where, like OneClient's play sessions: time in each world and on each server (not while paused),
 * how many sessions, and the time played each day for the last two weeks. Saved only on this PC, in
 * config/golemsclient/play_sessions.json, per Minecraft account.
 */
public final class PlaySessions {
    public static final String WORLD = "world:";
    public static final String SERVER = "server:";

    /** A world or server and the time spent there. */
    public static final class Spot {
        public String name;
        public boolean server;
        public long millis;
        public int sessions;
        public long lastPlayed;
    }

    /** One account's log: its places, and the time played each day (by epoch day). */
    public static final class Log {
        public Map<String, Spot> spots = new HashMap<>();
        public Map<String, Long> days = new HashMap<>();
    }

    private static final class State {
        Map<String, Log> players = new HashMap<>();
    }

    private record Place(String key, String name, boolean server) { }

    private static final class Session {
        final UUID player;
        Place place;
        long lastNanos = System.nanoTime();
        long elapsed;
        long pending;
        long savedAt;
        boolean counted;

        Session(UUID player, Place place) {
            this.player = player;
            this.place = place;
        }
    }

    private static final long MIN_SESSION = 10_000L;
    private static final long SAVE_EVERY = 30_000L;
    private static final long LONGEST_TICK = 5_000L;
    private static final long KEPT_DAYS = 14L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Golem's Client play time");
        t.setDaemon(true);
        return t;
    });
    private static final Object LOCK = new Object();
    private static State state;
    private static Session session;

    private PlaySessions() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PlaySessions::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> stop());
    }

    /** This account's log (a copy to read). */
    public static Log log(UUID player) {
        synchronized (LOCK) {
            Log log = loaded().players.get(player.toString());
            return log == null ? new Log() : GSON.fromJson(GSON.toJson(log), Log.class);
        }
    }

    private static void tick(Minecraft mc) {
        Place place = currentPlace(mc);
        Session open = session;
        if (open != null && (place == null || !open.place.key().equals(place.key()))) {
            advance(open, mc);
            flush(open, true);
            session = null;
        }
        if (place != null && session == null) session = new Session(mc.getUser().getProfileId(), place);
        Session now = session;
        if (now != null) {
            now.place = place;
            advance(now, mc);
            if (now.elapsed - now.savedAt >= SAVE_EVERY) flush(now, true);
        }
    }

    private static void stop() {
        Session open = session;
        if (open == null) return;
        session = null;
        advance(open, Minecraft.getInstance());
        flush(open, false);
    }

    private static void advance(Session open, Minecraft mc) {
        long now = System.nanoTime();
        long delta = Math.min((now - open.lastNanos) / 1_000_000L, LONGEST_TICK);
        open.lastNanos = now;
        if (mc.isPaused()) return;
        open.elapsed += delta;
        open.pending += delta;
    }

    private static void flush(Session open, boolean async) {
        if (open.elapsed < MIN_SESSION) return;
        long millis = open.pending;
        boolean started = !open.counted;
        Place place = open.place;
        open.pending = 0L;
        open.savedAt = open.elapsed;
        open.counted = true;
        Runnable record = () -> record(open.player, place, millis, started);
        if (async) SAVER.submit(record);
        else record.run();
    }

    private static void record(UUID player, Place place, long millis, boolean started) {
        synchronized (LOCK) {
            State s = loaded();
            Log log = s.players.computeIfAbsent(player.toString(), k -> new Log());
            Spot spot = log.spots.computeIfAbsent(place.key(), k -> new Spot());
            spot.name = place.name();
            spot.server = place.server();
            spot.millis += millis;
            if (started) spot.sessions++;
            spot.lastPlayed = System.currentTimeMillis();
            long today = LocalDate.now().toEpochDay();
            log.days.merge(Long.toString(today), millis, Long::sum);
            log.days.keySet().removeIf(day -> today - Long.parseLong(day) >= KEPT_DAYS);
            write(s);
        }
    }

    private static Place currentPlace(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        var integrated = mc.getSingleplayerServer();
        if (mc.hasSingleplayerServer() && integrated != null) {
            Path root = integrated.getWorldPath(LevelResource.ROOT).normalize();
            String folder = root.getFileName() == null ? integrated.getWorldData().getLevelName() : root.getFileName().toString();
            return new Place(WORLD + folder, integrated.getWorldData().getLevelName(), false);
        }
        ServerData data = mc.getCurrentServer();
        if (data == null) return null;
        return new Place(SERVER + data.ip.trim().toLowerCase(Locale.ROOT), data.name, true);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("golemsclient").resolve("play_sessions.json");
    }

    private static State loaded() {
        if (state != null) return state;
        try {
            Path f = file();
            if (Files.exists(f)) state = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), State.class);
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't read the play time", e);
        }
        if (state == null || state.players == null) state = new State();
        return state;
    }

    private static void write(State s) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("play_sessions.json.tmp");
            Files.writeString(tmp, GSON.toJson(s), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't save the play time", e);
        }
    }
}
