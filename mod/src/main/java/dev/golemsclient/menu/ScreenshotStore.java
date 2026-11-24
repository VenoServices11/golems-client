package dev.golemsclient.menu;

import dev.golemsclient.GolemsClient;
import dev.golemsclient.render.GuiTexture;
import dev.golemsclient.render.Textures;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Your screenshots for the Screenshots page: the list (from the screenshots folder) and their pictures, decoded and
 * shrunk in the background so the menu never stutters, and only the ones on screen kept in memory.
 */
final class ScreenshotStore {
    record Shot(Path path, String name, long modified, long size) { }

    private static final int KEEP = 48;
    private static final ExecutorService DECODE = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Golem's Client screenshots");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, Textures.Pixels> READY = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> BROKEN = new ConcurrentHashMap<>();
    // the pictures uploaded, oldest use first, so the least recently seen go first
    private static final LinkedHashMap<String, Boolean> UPLOADED = new LinkedHashMap<>(64, 0.75f, true);

    private static volatile List<Shot> shots = List.of();
    private static volatile boolean listing;

    private ScreenshotStore() {
    }

    static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
    }

    static List<Shot> shots() {
        return shots;
    }

    /** Lists the folder again (in the background). */
    static void refresh() {
        if (listing) return;
        listing = true;
        DECODE.submit(() -> {
            List<Shot> out = new ArrayList<>();
            Path dir = dir();
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.walk(dir, 2)) {
                    for (Path f : files.toList()) {
                        String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!Files.isRegularFile(f) || !(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) continue;
                        try {
                            out.add(new Shot(f, f.getFileName().toString().replaceAll("(?i)\\.(png|jpe?g)$", ""),
                                    Files.getLastModifiedTime(f).toMillis(), Files.size(f)));
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    GolemsClient.LOGGER.warn("Couldn't list the screenshots", e);
                }
            }
            shots = out;
            listing = false;
        });
    }

    /** The picture of a screenshot, at most maxW wide; null while it's being made. Call from the render thread. */
    static GuiTexture picture(Shot s, int maxW) {
        String key = "shot:" + maxW + ":" + s.path() + ":" + s.modified();
        if (Textures.has(key)) {
            UPLOADED.get(key);
            return Textures.upload(key, null);
        }
        Textures.Pixels px = READY.remove(key);
        if (px != null) {
            GuiTexture t = Textures.upload(key, px);
            UPLOADED.put(key, true);
            trim();
            return t;
        }
        if (!BROKEN.containsKey(key) && PENDING.putIfAbsent(key, true) == null) {
            DECODE.submit(() -> {
                try {
                    Textures.Pixels made = Textures.shrunk(Files.readAllBytes(s.path()), maxW);
                    if (made != null) READY.put(key, made);
                    else BROKEN.put(key, true);
                } catch (Exception e) {
                    GolemsClient.LOGGER.debug("Couldn't read a screenshot: {}", e.toString());
                } finally {
                    PENDING.remove(key);
                }
            });
        }
        return null;
    }

    private static void trim() {
        while (UPLOADED.size() > KEEP) {
            String oldest = UPLOADED.keySet().iterator().next();
            UPLOADED.remove(oldest);
            Textures.release(oldest);
        }
    }

    /** Lets go of every picture (when the page closes). */
    static void release() {
        for (String key : UPLOADED.keySet()) Textures.release(key);
        UPLOADED.clear();
        READY.clear();
    }

    static boolean delete(Shot s) {
        try {
            Files.deleteIfExists(s.path());
            List<Shot> next = new ArrayList<>(shots);
            next.remove(s);
            shots = next;
            return true;
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't delete a screenshot", e);
            return false;
        }
    }
}
