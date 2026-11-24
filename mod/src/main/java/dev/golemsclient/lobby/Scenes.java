package dev.golemsclient.lobby;

import dev.golemsclient.GolemsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The lobby's scenes: the built-in ones in the jar (assets/golemsclient/scenes, listed in index.txt as
 * "id;name;season"), and captures saved from servers you play (config/golemsclient/scenes). Automatic picks the
 * built-in scene for the season.
 */
public final class Scenes {
    /** A scene that can be picked: built-in (resource path) or a capture (file). */
    public record Entry(String id, String name, String season, String resource, Path file, long time) {
        public boolean builtIn() {
            return resource != null;
        }

        public Scene load() throws Exception {
            if (resource != null) {
                try (InputStream in = Scenes.class.getResourceAsStream(resource)) {
                    if (in == null) throw new IllegalStateException("missing scene " + resource);
                    Scene s = Scene.read(in);
                    if (time >= 0) s.time = time;
                    return s;
                }
            }
            return Scene.read(file);
        }
    }

    private Scenes() {
    }

    public static Path capturesDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("golemsclient").resolve("scenes");
    }

    public static List<Entry> builtIn() {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = Scenes.class.getResourceAsStream("/assets/golemsclient/scenes/index.txt")) {
            if (in == null) return out;
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split(";");
                if (p.length < 3) continue;
                long time = -1;
                if (p.length > 3) {
                    try {
                        time = Long.parseLong(p[3].strip());
                    } catch (NumberFormatException ignored) {
                    }
                }
                out.add(new Entry(p[0], p[1], p[2], "/assets/golemsclient/scenes/" + p[0] + ".nbt", null, time));
            }
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't list the lobby scenes", e);
        }
        return out;
    }

    public static List<Entry> captures() {
        List<Entry> out = new ArrayList<>();
        Path dir = capturesDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".nbt")).sorted().forEach(f -> {
                String id = f.getFileName().toString().replaceAll("\\.nbt$", "");
                out.add(new Entry("capture:" + id, id, "", null, f, -1));
            });
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't list the captured scenes", e);
        }
        return out;
    }

    public static List<Entry> all() {
        List<Entry> all = new ArrayList<>(builtIn());
        all.addAll(captures());
        return all;
    }

    public static String season(LocalDate d) {
        Month m = d.getMonth();
        return switch (m) {
            case DECEMBER, JANUARY, FEBRUARY -> "winter";
            case MARCH, APRIL, MAY -> "spring";
            case JUNE, JULY, AUGUST -> "summer";
            default -> "autumn";
        };
    }

    /** The scene to show: the one picked in the lobby settings, else the season's built-in one, else any. */
    public static Entry pick(String chosen) {
        List<Entry> all = all();
        if (all.isEmpty()) return null;
        if (chosen != null && !chosen.isEmpty() && !chosen.equals("auto")) {
            for (Entry e : all) if (e.id().equals(chosen)) return e;
        }
        String season = season(LocalDate.now());
        for (Entry e : all) if (season.equals(e.season())) return e;
        return all.get(0);
    }
}
