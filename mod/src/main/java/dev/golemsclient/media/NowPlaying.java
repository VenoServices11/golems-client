package dev.golemsclient.media;

import com.sun.jna.Platform;
import dev.golemsclient.GolemsClient;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * What is playing on this PC right now (Spotify, YouTube in a browser, any app that shows up in Windows' media
 * controls), read on one background thread once a second; and play/pause/next/previous for it. Windows only; on
 * other systems nothing is ever playing.
 */
public final class NowPlaying {
    public enum Command { TOGGLE, NEXT, PREVIOUS }

    private static final ConcurrentLinkedQueue<Command> COMMANDS = new ConcurrentLinkedQueue<>();
    private static volatile MediaSession.Snapshot current;
    private static volatile boolean started;
    private static volatile long wantedUntil;

    private NowPlaying() {
    }

    /** The newest reading, or null when nothing is playing (or it can't be read). Starts the reader if needed. */
    public static MediaSession.Snapshot get() {
        wantedUntil = System.currentTimeMillis() + 5000L;
        start();
        return current;
    }

    public static void send(Command command) {
        COMMANDS.add(command);
    }

    // ------------------------------------------------------------------ OneClient's media widget bits (PolyPlus, GPL-3.0)

    private static final java.util.Map<String, String> PLAYERS = java.util.Map.ofEntries(
            java.util.Map.entry("spotify", "Spotify"), java.util.Map.entry("spotifymusic", "Spotify"),
            java.util.Map.entry("msedge", "Edge"), java.util.Map.entry("edge", "Edge"), java.util.Map.entry("chrome", "Chrome"),
            java.util.Map.entry("chromium", ""), java.util.Map.entry("cef", ""), java.util.Map.entry("firefox", "Firefox"),
            java.util.Map.entry("308046b0af4a39cb", "Firefox"), java.util.Map.entry("brave", "Brave"), java.util.Map.entry("opera", "Opera"),
            java.util.Map.entry("vivaldi", "Vivaldi"), java.util.Map.entry("vlc", "VLC"), java.util.Map.entry("zunemusic", "Media Player"),
            java.util.Map.entry("zunevideo", "Movies & TV"), java.util.Map.entry("music", "Music"), java.util.Map.entry("applemusic", "Apple Music"),
            java.util.Map.entry("itunes", "iTunes"), java.util.Map.entry("foobar2000", "foobar2000"), java.util.Map.entry("aimp", "AIMP"),
            java.util.Map.entry("mpv", "mpv"), java.util.Map.entry("tidal", "TIDAL"), java.util.Map.entry("deezer", "Deezer"),
            java.util.Map.entry("youtubemusic", "YouTube Music"), java.util.Map.entry("discord", "Discord"));
    private static final java.util.Set<String> NOISE = java.util.Set.of("com", "org", "net", "io", "app", "apps", "client", "desktop",
            "player", "android", "media", "instance");

    /** The app playing, the way people know it ("Spotify", "Chrome"), from Windows' id for it. "" when unknown. */
    public static String player(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String id = raw.trim();
        id = id.substring(Math.max(id.lastIndexOf('!'), Math.max(id.lastIndexOf('/'), id.lastIndexOf('\\'))) + 1);
        if (id.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) id = id.substring(0, id.length() - 4);
        if (!id.contains(" ") && id.contains(".")) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (String part : id.split("\\.")) {
                String p = part.contains("_") ? part.substring(0, part.indexOf('_')) : part;
                if (!p.isBlank()) parts.add(p);
            }
            String pick = null, last = null;
            for (String p : parts) {
                String lower = p.toLowerCase(java.util.Locale.ROOT);
                if (NOISE.contains(lower) || lower.startsWith("instance") || p.chars().noneMatch(Character::isLetter)) continue;
                if (pick == null && PLAYERS.containsKey(lower)) pick = p;
                last = p;
            }
            id = pick != null ? pick : last != null ? last : parts.isEmpty() ? id : parts.get(0);
        } else if (!id.contains(" ") && id.contains("_")) {
            id = id.substring(0, id.indexOf('_'));
        }
        String known = PLAYERS.get(id.toLowerCase(java.util.Locale.ROOT));
        if (known != null) return known;
        StringBuilder out = new StringBuilder();
        for (String word : id.replace('_', ' ').replace('-', ' ').split(" ")) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            boolean lower = word.chars().allMatch(ch -> !Character.isLetter(ch) || Character.isLowerCase(ch));
            out.append(lower ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word);
        }
        return out.toString();
    }

    /** "1:23" (or "1:02:03"). */
    public static String clock(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        long h = seconds / 3600, m = (seconds % 3600) / 60, sec = seconds % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
    }

    private static volatile int toneVersion = Integer.MIN_VALUE, tone;
    private static volatile boolean toning;

    /**
     * The album art's colour (vivid, like OneClient's), for the widget's soft wash; 0 until it's worked out (in the
     * background) or when there's no art.
     */
    public static int tone(MediaSession.Snapshot s) {
        if (s == null || !s.hasThumbnail()) return 0;
        if (s.thumbnailVersion() == toneVersion) return tone;
        if (!toning) {
            toning = true;
            byte[] bytes = s.thumbnail();
            int version = s.thumbnailVersion();
            Thread t = new Thread(() -> {
                try {
                    dev.golemsclient.render.Textures.Pixels px = dev.golemsclient.render.Textures.shrunk(bytes, 16);
                    tone = px == null ? 0 : vivid(average(px));
                } catch (Throwable e) {
                    tone = 0;
                } finally {
                    toneVersion = version;
                    toning = false;
                }
            }, "Golem's Client album colour");
            t.setDaemon(true);
            t.start();
        }
        return 0;
    }

    private static int average(dev.golemsclient.render.Textures.Pixels px) {
        long r = 0, g = 0, b = 0, n = 0;
        byte[] d = px.rgba();
        for (int i = 0; i + 3 < d.length; i += 4) {
            int a = d[i + 3] & 0xFF;
            if (a < 128) continue;
            r += d[i] & 0xFF;
            g += d[i + 1] & 0xFF;
            b += d[i + 2] & 0xFF;
            n++;
        }
        if (n == 0) return 0;
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /** Pushed to a readable, saturated colour (greys come out as Golem's Client red). */
    private static int vivid(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
        if (hsb[1] < 0.12f) hsb = java.awt.Color.RGBtoHSB(0xE5, 0x48, 0x4D, null);
        int out = java.awt.Color.HSBtoRGB(hsb[0], Math.max(hsb[1], 0.45f), Math.min(Math.max(hsb[2], 0.55f), 0.85f));
        return out & 0xFFFFFF;
    }

    /** Where the song is now, in milliseconds, counting on from the last reading while it plays. */
    public static long position(MediaSession.Snapshot s) {
        if (s == null) return 0L;
        long pos = s.positionMs();
        if (s.state() == MediaSession.PlaybackState.PLAYING) {
            pos += (long) ((System.currentTimeMillis() - s.sampledAtMs()) * (s.rate() > 0 ? s.rate() : 1.0));
        }
        return Math.max(0L, s.durationMs() > 0 ? Math.min(pos, s.durationMs()) : pos);
    }

    private static synchronized void start() {
        if (started || !Platform.isWindows()) return;
        started = true;
        Thread t = new Thread(NowPlaying::run, "Golem's Client media");
        t.setDaemon(true);
        t.start();
    }

    private static void run() {
        MediaSession session = new MediaSession(null);
        int failures = 0;
        while (true) {
            try {
                Command c;
                boolean acted = false;
                while ((c = COMMANDS.poll()) != null) {
                    switch (c) {
                        case TOGGLE -> session.togglePlayPause();
                        case NEXT -> session.skipNext();
                        case PREVIOUS -> session.skipPrevious();
                    }
                    acted = true;
                }
                if (session.isUnsupported()) {
                    current = null;
                    return;
                }
                // read only while something on screen wants it
                if (acted || System.currentTimeMillis() < wantedUntil) {
                    MediaSession.Snapshot s = session.poll();
                    current = s != null && s.title() != null && !s.title().isBlank() ? s : null;
                }
                failures = 0;
                Thread.sleep(acted ? 150L : 1000L);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                if (++failures == 1) GolemsClient.LOGGER.warn("Couldn't read what's playing", t);
                if (failures > 20) {
                    current = null;
                    return;
                }
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
