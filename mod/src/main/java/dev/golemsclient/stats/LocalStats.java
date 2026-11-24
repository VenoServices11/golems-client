package dev.golemsclient.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golemsclient.GolemsClient;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The Stats page's numbers, like OneClient's: play time from {@link PlaySessions} and each singleplayer world's own
 * statistics (play time, blocks mined, mobs killed, deaths and more) from its stats file. Read in the background.
 */
public final class LocalStats {
    /** Minecraft's own statistics the page totals up, and their names in old and new stats files. */
    public enum Stat {
        PLAY_TICKS(new String[] {"play_time", "play_one_minute"}, "stat.playOneMinute"),
        DEATHS(new String[] {"deaths"}, "stat.deaths"),
        MOB_KILLS(new String[] {"mob_kills"}, "stat.mobKills"),
        PLAYER_KILLS(new String[] {"player_kills"}, "stat.playerKills"),
        WALKED_CM(new String[] {"walk_one_cm", "sprint_one_cm", "crouch_one_cm"}, "stat.walkOneCm"),
        JUMPS(new String[] {"jump"}, "stat.jump"),
        FISH_CAUGHT(new String[] {"fish_caught"}, "stat.fishCaught"),
        VILLAGER_TRADES(new String[] {"traded_with_villager"}, "stat.tradedWithVillager"),
        ANIMALS_BRED(new String[] {"animals_bred"}, "stat.animalsBred");

        final String[] nested;
        final String flat;

        Stat(String[] nested, String flat) {
            this.nested = nested;
            this.flat = flat;
        }
    }

    public record Ranked(String id, String name, long millis) { }

    public record Day(String label, long millis, boolean today) { }

    public record Report(UUID player, List<Ranked> worlds, List<Ranked> servers, List<Day> days, int sessions, long sessionMillis,
                         Map<Stat, Long> totals, long blocksMined, long itemsCrafted, String topMined, String topKilled) {
        public long worldMillis() {
            return worlds.stream().mapToLong(Ranked::millis).sum();
        }

        public long serverMillis() {
            return servers.stream().mapToLong(Ranked::millis).sum();
        }

        public long totalMillis() {
            return worldMillis() + serverMillis();
        }

        public long weekMillis() {
            return days.stream().mapToLong(Day::millis).sum();
        }

        public boolean noWorldStats() {
            return totals.values().stream().allMatch(v -> v == 0L) && blocksMined == 0L;
        }
    }

    private record Tally(Map<Stat, Long> values, Map<String, Long> mined, Map<String, Long> killed, long crafted) { }

    private static volatile Report report;
    private static volatile boolean loading;

    private LocalStats() {
    }

    public static Report report() {
        return report;
    }

    /** Reads everything again (in the background); the page shows the last report until then. */
    public static void refresh() {
        if (loading) return;
        loading = true;
        Minecraft mc = Minecraft.getInstance();
        UUID player = mc.getUser().getProfileId();
        Path saves = mc.gameDirectory.toPath().resolve("saves");
        Thread t = new Thread(() -> {
            try {
                report = build(player, readWorlds(saves, player), PlaySessions.log(player));
            } catch (Exception e) {
                GolemsClient.LOGGER.warn("Couldn't read the stats", e);
            } finally {
                loading = false;
            }
        }, "Golem's Client stats");
        t.setDaemon(true);
        t.start();
    }

    private static Map<String, Tally> readWorlds(Path saves, UUID player) {
        Map<String, Tally> out = new HashMap<>();
        if (!Files.isDirectory(saves)) return out;
        try (Stream<Path> worlds = Files.list(saves)) {
            for (Path world : worlds.toList()) {
                Path best = null;
                for (String folder : new String[] {"players/stats", "stats"}) {
                    Path f = world.resolve(folder).resolve(player + ".json");
                    if (Files.isRegularFile(f) && (best == null || Files.getLastModifiedTime(f).compareTo(Files.getLastModifiedTime(best)) > 0)) {
                        best = f;
                    }
                }
                if (best == null) continue;
                try {
                    out.put(world.getFileName().toString(), parse(Files.readString(best)));
                } catch (Exception e) {
                    GolemsClient.LOGGER.warn("Couldn't read the stats of {}", world.getFileName(), e);
                }
            }
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't list the worlds for stats", e);
        }
        return out;
    }

    private static Tally parse(String text) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        Map<Stat, Long> values = new EnumMap<>(Stat.class);
        if (root.has("stats") && root.get("stats").isJsonObject()) {
            JsonObject stats = root.getAsJsonObject("stats");
            Map<String, Long> custom = counts(stats.get("minecraft:custom"));
            for (Stat s : Stat.values()) {
                long sum = 0L;
                for (String n : s.nested) sum += custom.getOrDefault("minecraft:" + n, 0L);
                values.put(s, sum);
            }
            return new Tally(values, named(counts(stats.get("minecraft:mined"))), named(counts(stats.get("minecraft:killed"))),
                    counts(stats.get("minecraft:crafted")).values().stream().mapToLong(Long::longValue).sum());
        }
        Map<String, Long> flat = counts(root);
        for (Stat s : Stat.values()) values.put(s, flat.getOrDefault(s.flat, 0L));
        Map<String, Long> mined = new HashMap<>(), killed = new HashMap<>();
        long crafted = 0L;
        for (var e : flat.entrySet()) {
            if (e.getKey().startsWith("stat.mineBlock.")) mined.put(e.getKey(), e.getValue());
            else if (e.getKey().startsWith("stat.killEntity.")) killed.put(e.getKey(), e.getValue());
            else if (e.getKey().startsWith("stat.craftItem.")) crafted += e.getValue();
        }
        return new Tally(values, named(mined), named(killed), crafted);
    }

    private static Map<String, Long> counts(JsonElement el) {
        Map<String, Long> out = new HashMap<>();
        if (el == null || !el.isJsonObject()) return out;
        for (var e : el.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) out.put(e.getKey(), e.getValue().getAsLong());
        }
        return out;
    }

    /** "minecraft:oak_log" and "stat.mineBlock.oakLog" both become "oak log". */
    private static Map<String, Long> named(Map<String, Long> counts) {
        Map<String, Long> out = new HashMap<>();
        counts.forEach((k, v) -> {
            String n = k.substring(k.lastIndexOf(':') + 1);
            n = n.substring(n.lastIndexOf('.') + 1).replaceAll("(?<=[a-z])(?=[A-Z])", " ").replace('_', ' ').toLowerCase(Locale.ROOT);
            out.merge(n, v, Long::sum);
        });
        return out;
    }

    private static Report build(UUID player, Map<String, Tally> worlds, PlaySessions.Log log) {
        List<Ranked> worldList = new ArrayList<>();
        worlds.forEach((id, tally) -> {
            long ticks = tally.values().getOrDefault(Stat.PLAY_TICKS, 0L);
            if (ticks <= 0L) return;
            PlaySessions.Spot spot = log.spots.get(PlaySessions.WORLD + id);
            worldList.add(new Ranked(id, spot != null ? spot.name : id, ticks * 50L));
        });
        // worlds that were played here but are gone from the saves folder
        log.spots.forEach((key, spot) -> {
            if (spot.server || !key.startsWith(PlaySessions.WORLD)) return;
            String id = key.substring(PlaySessions.WORLD.length());
            if (!worlds.containsKey(id)) worldList.add(new Ranked(id, spot.name, spot.millis));
        });
        worldList.sort(Comparator.comparingLong(Ranked::millis).reversed());
        List<Ranked> servers = new ArrayList<>();
        log.spots.forEach((key, spot) -> {
            if (spot.server) servers.add(new Ranked(key.substring(PlaySessions.SERVER.length()), spot.name, spot.millis));
        });
        servers.sort(Comparator.comparingLong(Ranked::millis).reversed());
        List<Day> days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int back = 6; back >= 0; back--) {
            LocalDate d = today.minusDays(back);
            days.add(new Day(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    log.days.getOrDefault(Long.toString(d.toEpochDay()), 0L), back == 0));
        }
        int sessions = log.spots.values().stream().mapToInt(s -> s.sessions).sum();
        long sessionMillis = log.spots.values().stream().mapToLong(s -> s.millis).sum();
        Map<Stat, Long> totals = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) totals.put(s, worlds.values().stream().mapToLong(t -> t.values().getOrDefault(s, 0L)).sum());
        long mined = worlds.values().stream().mapToLong(t -> t.mined().values().stream().mapToLong(Long::longValue).sum()).sum();
        long crafted = worlds.values().stream().mapToLong(Tally::crafted).sum();
        return new Report(player, worldList, servers, days, sessions, sessionMillis, totals, mined, crafted,
                top(worlds.values().stream().map(Tally::mined).toList()), top(worlds.values().stream().map(Tally::killed).toList()));
    }

    private static String top(List<Map<String, Long>> maps) {
        Map<String, Long> merged = new HashMap<>();
        maps.forEach(m -> m.forEach((k, v) -> merged.merge(k, v, Long::sum)));
        return merged.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    // ------------------------------------------------------------------ formatting, like OneClient's

    public static String duration(long millis) {
        long minutes = millis / 60_000L, hours = minutes / 60L;
        if (minutes < 1L) return "<1m";
        if (hours == 0L) return minutes + "m";
        return String.format(Locale.ROOT, "%,d", hours) + "h " + (minutes % 60L) + "m";
    }

    public static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String distance(long cm) {
        long m = cm / 100L;
        return m < 1000L ? count(m) + " m" : String.format(Locale.ROOT, "%,.1f km", m / 1000.0);
    }
}
