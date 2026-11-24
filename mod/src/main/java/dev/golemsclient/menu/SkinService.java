package dev.golemsclient.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import dev.golemsclient.GolemsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Your Minecraft skin and capes, for the Wardrobe: reads your profile from Mojang, changes your skin (from a file or one
 * you wore before) and your cape, keeps a library of skins (config/golemsclient/skins) and a history of the skins you
 * wore. Everything goes to Mojang's own API with the account you're signed in with, as the official launcher does;
 * nothing goes anywhere else and nothing about the account is stored.
 */
public final class SkinService {
    public record Skin(String url, boolean slim) { }

    public record Cape(String id, String url, String alias, boolean active) { }

    public record Profile(String name, Skin skin, List<Cape> capes) { }

    /** A skin in your library: its file in config/golemsclient/skins. */
    public static final class Saved {
        public String id;
        public String name;
        public String file;
        public boolean slim;
        public long added;
    }

    /** A skin you wore: Mojang keeps the picture at its address, so it can be put back on. */
    public static final class Worn {
        public String url;
        public boolean slim;
        public long at;
    }

    private static final String API = "https://api.minecraftservices.com/minecraft/profile";
    private static final int HISTORY_MAX = 30;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Golem's Client wardrobe");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, byte[]> PICTURES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FETCHING = new ConcurrentHashMap<>();

    private static volatile Profile profile;
    private static volatile boolean loading;
    private static volatile boolean busy;
    private static volatile String problem;
    private static volatile String notice;
    private static List<Saved> library;
    private static List<Worn> history;

    private SkinService() {
    }

    public static Profile profile() {
        return profile;
    }

    public static boolean loading() {
        return loading;
    }

    /** True while a change is being sent to Mojang. */
    public static boolean busy() {
        return busy;
    }

    /** What went wrong last, for the page to show (null when nothing did). */
    public static String problem() {
        return problem;
    }

    /** A message for a toast, taken once. */
    public static String takeNotice() {
        String n = notice;
        notice = null;
        return n;
    }

    // ------------------------------------------------------------------ Mojang

    /** Reads your skin and capes from Mojang (in the background). */
    public static void refresh() {
        if (loading) return;
        loading = true;
        WORK.submit(() -> {
            try {
                JsonObject json = call(HttpRequest.newBuilder(URI.create(API)).GET());
                profile = parse(json);
                problem = null;
                rememberWorn(profile.skin());
            } catch (Exception e) {
                problem = explain(e);
                GolemsClient.LOGGER.warn("Couldn't read the skin and capes: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    /** Puts on a skin from a PNG (64x64 or 64x32). */
    public static void wear(byte[] png, boolean slim) {
        change(() -> {
            String boundary = "----GolemsClient" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic")
                    + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(png);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return call(HttpRequest.newBuilder(URI.create(API + "/skins"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
        }, "Skin changed");
    }

    /** Puts a skin you wore before back on. */
    public static void wear(Worn worn) {
        change(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("variant", worn.slim ? "slim" : "classic");
            body.addProperty("url", worn.url);
            return call(HttpRequest.newBuilder(URI.create(API + "/skins"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        }, "Skin changed");
    }

    /** Shows one of your capes, or none (null). */
    public static void cape(String id) {
        change(() -> {
            if (id == null) {
                return call(HttpRequest.newBuilder(URI.create(API + "/capes/active")).DELETE());
            }
            JsonObject body = new JsonObject();
            body.addProperty("capeId", id);
            return call(HttpRequest.newBuilder(URI.create(API + "/capes/active"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString())));
        }, id == null ? "Cape hidden" : "Cape changed");
    }

    private interface Call {
        JsonObject run() throws Exception;
    }

    private static void change(Call call, String done) {
        if (busy) return;
        busy = true;
        WORK.submit(() -> {
            try {
                Profile before = profile;
                profile = parse(call.run());
                problem = null;
                if (before != null) rememberWorn(before.skin());
                rememberWorn(profile.skin());
                notice = done + ". It can take a minute to show everywhere.";
                reloadInGame(profile.skin() == null ? null : profile.skin().url());
            } catch (Exception e) {
                problem = explain(e);
                notice = problem;
                GolemsClient.LOGGER.warn("Couldn't change the skin or cape: {}", e.toString());
            } finally {
                busy = false;
            }
        });
    }

    private static JsonObject call(HttpRequest.Builder request) throws Exception {
        String token = Minecraft.getInstance().getUser().getAccessToken();
        HttpResponse<String> res = HTTP.send(request.header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401) throw new IllegalStateException("signed out");
        if (res.statusCode() == 429) throw new IllegalStateException("too many");
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("status " + res.statusCode() + ": " + res.body());
        // hiding a cape answers with the profile too; when there's nothing, read the profile again
        if (res.body() == null || res.body().isBlank()) {
            return call(HttpRequest.newBuilder(URI.create(API)).GET());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static String explain(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (m.equals("signed out")) return "Your Minecraft account needs signing in again. Restart the game from the launcher.";
        if (m.equals("too many")) return "Mojang says that's too many changes at once. Wait a minute and try again.";
        if (e instanceof java.net.ConnectException || e instanceof java.net.http.HttpTimeoutException) {
            return "Couldn't reach Mojang. Check your internet connection.";
        }
        if (m.contains("status 400")) return "Mojang didn't take that skin. It has to be a 64x64 (or 64x32) PNG.";
        return "Couldn't reach your Minecraft account right now.";
    }

    private static Profile parse(JsonObject json) {
        Skin skin = null;
        JsonArray skins = json.has("skins") ? json.getAsJsonArray("skins") : new JsonArray();
        for (JsonElement el : skins) {
            JsonObject s = el.getAsJsonObject();
            if ("ACTIVE".equals(str(s, "state"))) skin = new Skin(str(s, "url"), "SLIM".equalsIgnoreCase(str(s, "variant")));
        }
        List<Cape> capes = new ArrayList<>();
        JsonArray list = json.has("capes") ? json.getAsJsonArray("capes") : new JsonArray();
        for (JsonElement el : list) {
            JsonObject c = el.getAsJsonObject();
            capes.add(new Cape(str(c, "id"), str(c, "url"), str(c, "alias"), "ACTIVE".equals(str(c, "state"))));
        }
        return new Profile(str(json, "name"), skin, capes);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    /**
     * After a change, the game still shows the old skin until Mojang's session server has the new one (which can take a
     * little while): ask it every few seconds, then show the new skin in the menu and the lobby.
     */
    private static void reloadInGame(String wantedUrl) {
        Minecraft mc = Minecraft.getInstance();
        UUID id = mc.getUser().getProfileId();
        for (int attempt = 0; attempt < 12; attempt++) {
            try {
                Thread.sleep(attempt == 0 ? 1500L : 4000L);
                var result = mc.services().sessionService().fetchProfile(id, true);
                if (result == null) continue;
                GameProfile fresh = result.profile();
                var textures = mc.services().sessionService().getTextures(fresh);
                String url = textures.skin() == null ? null : textures.skin().getUrl();
                if (wantedUrl == null || wantedUrl.equals(url) || attempt == 11) {
                    mc.execute(() -> AccountSkin.update(fresh));
                    return;
                }
            } catch (Exception e) {
                GolemsClient.LOGGER.debug("Waiting for the new skin: {}", e.toString());
            }
        }
    }

    // ------------------------------------------------------------------ pictures

    /** A skin or cape picture by its address (downloaded once, then kept); null until it's here. */
    public static byte[] picture(String url) {
        if (url == null || url.isEmpty()) return null;
        byte[] cached = PICTURES.get(url);
        if (cached != null) return cached;
        if (FETCHING.putIfAbsent(url, true) == null) {
            WORK.submit(() -> {
                try {
                    HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url.replace("http://", "https://")))
                            .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                    if (res.statusCode() == 200) PICTURES.put(url, res.body());
                } catch (Exception e) {
                    GolemsClient.LOGGER.debug("Couldn't load a skin picture: {}", e.toString());
                }
            });
        }
        return null;
    }

    // ------------------------------------------------------------------ library and history

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("golemsclient").resolve("skins");
    }

    public static synchronized List<Saved> library() {
        if (library == null) library = load(dir().resolve("library.json"), new TypeToken<List<Saved>>() { });
        return Collections.unmodifiableList(library);
    }

    public static synchronized List<Worn> history() {
        if (history == null) history = load(dir().resolve("history.json"), new TypeToken<List<Worn>>() { });
        return Collections.unmodifiableList(history);
    }

    /** The PNG of a skin in the library (null if it's gone). */
    public static byte[] read(Saved s) {
        return PICTURES.computeIfAbsent("library:" + s.id, k -> {
            try {
                return Files.readAllBytes(dir().resolve(s.file));
            } catch (Exception e) {
                return new byte[0];
            }
        }).length == 0 ? null : PICTURES.get("library:" + s.id);
    }

    /** Adds a PNG to the library. Returns what's wrong with it, or null when it was added. */
    public static synchronized String add(Path file, boolean slim) {
        try {
            byte[] png = Files.readAllBytes(file);
            int[] size = pngSize(png);
            if (size == null) return "That isn't a PNG picture.";
            if (size[0] != 64 || (size[1] != 64 && size[1] != 32)) return "A skin has to be 64x64 (or 64x32). That one is " + size[0] + "x" + size[1] + ".";
            library();
            Saved s = new Saved();
            s.id = UUID.randomUUID().toString().substring(0, 8);
            String name = file.getFileName().toString().replaceAll("(?i)\\.png$", "");
            s.name = name.length() > 24 ? name.substring(0, 24) : name;
            s.file = s.id + ".png";
            s.slim = slim;
            s.added = System.currentTimeMillis();
            Files.createDirectories(dir());
            Files.write(dir().resolve(s.file), png);
            library.add(0, s);
            save(dir().resolve("library.json"), library);
            return null;
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't add a skin", e);
            return "Couldn't read that file.";
        }
    }

    public static synchronized void remove(Saved s) {
        library();
        library.removeIf(x -> x.id.equals(s.id));
        PICTURES.remove("library:" + s.id);
        try {
            Files.deleteIfExists(dir().resolve(s.file));
        } catch (Exception ignored) {
        }
        save(dir().resolve("library.json"), library);
    }

    private static synchronized void rememberWorn(Skin skin) {
        if (skin == null || skin.url().isEmpty()) return;
        history();
        history.removeIf(w -> w.url.equals(skin.url()));
        Worn w = new Worn();
        w.url = skin.url();
        w.slim = skin.slim();
        w.at = System.currentTimeMillis();
        history.add(0, w);
        while (history.size() > HISTORY_MAX) history.remove(history.size() - 1);
        save(dir().resolve("history.json"), history);
    }

    static int[] pngSize(byte[] png) {
        if (png == null || png.length < 24 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') return null;
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        return new int[] {w, h};
    }

    private static <T> List<T> load(Path file, TypeToken<List<T>> type) {
        try {
            if (Files.exists(file)) {
                List<T> list = GSON.fromJson(Files.readString(file), type.getType());
                if (list != null) return new ArrayList<>(list);
            }
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't read {}", file.getFileName(), e);
        }
        return new ArrayList<>();
    }

    private static void save(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(value));
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't save {}", file.getFileName(), e);
        }
    }
}
