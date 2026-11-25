package dev.golemsclient.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golemsclient.GolemsClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Golem's Client friends, in game: signs in to Golem's Client's server, tells it you're in game (and which server,
 * unless you turned that off), and keeps your friends list, friend requests and blocked players up to date.
 *
 * <p>Accounts can't be checked with Mojang from the server, so the first sign-in of an account gives this computer a
 * secret key (in ~/.golemsclient/friends, shared with the launcher), and later sign-ins send it. Nothing else about
 * the account is sent: no Microsoft or Minecraft login.
 */
public final class Friends {
    private static final String SERVER = "https://c418-presence.c418client.workers.dev";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9 ._:'-]{1,64}$");

    // every 30 seconds for new messages, every 5 while the friends panel is open
    private static final long POLL_MS = 30_000L, POLL_FAST_MS = 5_000L, PRESENCE_MS = 120_000L;
    private static final long RETRY_MS = 30_000L, LOCKED_RETRY_MS = 10 * 60_000L;

    public record Friend(UUID uuid, String name, long since, boolean owner, String state, String server, String mc, long lastSeen) {
        public boolean online() {
            return !"offline".equals(state);
        }

        public boolean inGame() {
            return "game".equals(state);
        }

        /** "Playing on donutsmp.net", "In game", "In the launcher" or "Offline". */
        public String where() {
            return switch (state) {
                case "game" -> server == null ? "In game" : "Singleplayer".equals(server) ? "Playing singleplayer"
                        : "a private server".equals(server) ? "On a private server" : "Playing on " + server;
                case "launcher" -> "In the launcher";
                default -> "Offline";
            };
        }
    }

    public record Request(int id, UUID uuid, String name, long at) {
    }

    public record Blocked(UUID uuid, String name, long since) {
    }

    public record State(List<Friend> friends, List<Request> incoming, List<Request> outgoing, List<Blocked> blocked,
                        boolean shareServer) {
        public int online() {
            int n = 0;
            for (Friend f : friends) if (f.online()) n++;
            return n;
        }
    }

    private static volatile State state;
    private static volatile String problem;
    private static volatile boolean busy;
    private static volatile String token;
    private static UUID tokenFor;
    private static long nextPoll, lastPresence, fastUntil;
    private static String lastServer = "";
    private static final Set<Integer> knownRequests = new HashSet<>();
    private static boolean knownFilled;
    private static Consumer<String> notifier = s -> { };

    private Friends() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Friends::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> leave());
    }

    /** Where new friend requests are announced (the menus show a toast). */
    public static void onNotice(Consumer<String> show) {
        notifier = show == null ? s -> { } : show;
    }

    /** The friends list as last fetched, or null before the first answer. */
    public static State state() {
        return state;
    }

    /** Why friends aren't working right now, or null. */
    public static String problem() {
        return problem;
    }

    /** Asks more often for a while (the friends list is open). */
    public static void watch() {
        long now = System.currentTimeMillis();
        boolean wasSlow = fastUntil < now;
        fastUntil = now + 8_000L;
        if (wasSlow) nextPoll = Math.min(nextPoll, now);
    }

    // ------------------------------------------------------------------ the loop

    private static void tick(Minecraft mc) {
        if (dev.golemsclient.DevShots.ON) {
            // dev screenshots: made-up friends, nothing sent
            if (state == null) state = sample();
            if (mc.getUser() != null) Chats.devSample(mc.getUser().getProfileId());
            return;
        }
        long now = System.currentTimeMillis();
        if (busy || now < nextPoll || mc.getUser() == null) return;
        busy = true;
        nextPoll = now + (now < fastUntil ? POLL_FAST_MS : POLL_MS);
        String where = where(mc);
        boolean sendPresence = now - lastPresence >= PRESENCE_MS || !where.equals(lastServer);
        String mcVersion = SharedConstants.getCurrentVersion().name();
        UUID me = mc.getUser().getProfileId();
        String name = mc.getUser().getName();
        CompletableFuture.runAsync(() -> {
            try {
                poll(me, name, sendPresence ? where : null, mcVersion);
                if (sendPresence) {
                    lastPresence = now;
                    lastServer = where;
                }
            } catch (Locked e) {
                problem = e.getMessage();
                nextPoll = System.currentTimeMillis() + LOCKED_RETRY_MS;
            } catch (Exception e) {
                if (state == null) problem = "Couldn't reach Golem's Client's server. Trying again soon.";
                nextPoll = System.currentTimeMillis() + RETRY_MS;
                GolemsClient.LOGGER.debug("Friends: {}", e.toString());
            } finally {
                busy = false;
            }
        });
    }

    private static void poll(UUID me, String name, String where, String mcVersion) throws Exception {
        JsonObject body = new JsonObject();
        if (where != null) {
            JsonObject presence = new JsonObject();
            presence.addProperty("source", "game");
            if (!where.isEmpty()) presence.addProperty("server", where);
            presence.addProperty("mc", mcVersion);
            body.add("presence", presence);
        }
        long since = Chats.since();
        if (since >= 0) body.addProperty("since", since);
        JsonObject answer = signedIn(me, name, "/friends/state", body);
        State next = parse(answer);
        Chats.polled(answer, me);
        announceNew(next);
        state = next;
        problem = null;
    }

    /** A request with the session token, signing in first (or again) when needed. */
    static JsonObject signedIn(UUID me, String name, String path, JsonObject body) throws Exception {
        if (token == null || !me.equals(tokenFor)) login(me, name);
        body.addProperty("token", token);
        Answer a = post(path, body);
        if (a.status == 401) {
            login(me, name);
            body.addProperty("token", token);
            a = post(path, body);
        }
        if (a.status != 200) throw new Failed(a.message());
        return a.json;
    }

    private static void login(UUID me, String name) throws Exception {
        String id = me.toString().replace("-", "");
        JsonObject body = new JsonObject();
        body.addProperty("uuid", id);
        body.addProperty("name", name);
        String key = readKey(id);
        if (key != null) body.addProperty("key", key);
        Answer a = post("/friends/login", body);
        if (a.status == 403) throw new Locked(a.message());
        if (a.status != 200) throw new Failed(a.message());
        if (a.json.has("key")) writeKey(id, a.json.get("key").getAsString());
        token = a.json.get("token").getAsString();
        tokenFor = me;
    }

    private static void announceNew(State next) {
        List<String> fresh = new ArrayList<>();
        for (Request r : next.incoming()) {
            if (knownRequests.add(r.id()) && knownFilled) fresh.add(r.name() == null ? "Someone" : r.name());
        }
        knownFilled = true;
        if (fresh.size() == 1) notifier.accept(fresh.get(0) + " wants to be friends");
        else if (fresh.size() > 1) notifier.accept(fresh.size() + " new friend requests");
    }

    /** The game is closing: friends see you as offline straight away. */
    private static void leave() {
        String t = token;
        if (t == null) return;
        try {
            JsonObject body = new JsonObject();
            body.addProperty("token", t);
            body.addProperty("source", "game");
            HTTP.send(request("/friends/leave", body).timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ actions

    /**
     * Sends a friend request (or accepts theirs, if they already sent you one). The name is checked with Mojang first,
     * from this computer, so a made-up account can't pose as someone.
     */
    public static void add(String name, Consumer<String> done) {
        String n = name.trim();
        action(done, (me, myName) -> {
            if (!n.matches("[A-Za-z0-9_]{1,16}")) return "That isn't a Minecraft name.";
            if (n.equalsIgnoreCase(myName)) return "That's you.";
            JsonObject body = new JsonObject();
            body.addProperty("name", n);
            String uuid = mojangUuid(n);
            if ("".equals(uuid)) return "No Minecraft account is called " + n + ".";
            if (uuid != null) body.addProperty("uuid", uuid);
            JsonObject a = signedIn(me, myName, "/friends/add", body);
            String shown = a.has("name") ? a.get("name").getAsString() : n;
            return a.has("friends") && a.get("friends").getAsBoolean() ? "You and " + shown + " are friends now"
                    : "Friend request sent to " + shown;
        });
    }

    public static void accept(Request r, Consumer<String> done) {
        simple("/friends/accept", "id", r.id(), "You and " + nameOf(r.name()) + " are friends now", done);
    }

    public static void decline(Request r, Consumer<String> done) {
        simple("/friends/decline", "id", r.id(), "Request declined", done);
    }

    public static void cancel(Request r, Consumer<String> done) {
        simple("/friends/cancel", "id", r.id(), "Request cancelled", done);
    }

    public static void remove(Friend f, Consumer<String> done) {
        simple("/friends/remove", "uuid", f.uuid().toString().replace("-", ""), nameOf(f.name()) + " was removed", done);
    }

    public static void block(UUID uuid, String name, Consumer<String> done) {
        simple("/friends/block", "uuid", uuid.toString().replace("-", ""), nameOf(name) + " is blocked", done);
    }

    public static void unblock(Blocked b, Consumer<String> done) {
        simple("/friends/unblock", "uuid", b.uuid().toString().replace("-", ""), nameOf(b.name()) + " is unblocked", done);
    }

    public static void shareServer(boolean share, Consumer<String> done) {
        action(done, (me, myName) -> {
            JsonObject body = new JsonObject();
            body.addProperty("shareServer", share);
            signedIn(me, myName, "/friends/settings", body);
            return share ? "Friends can see which server you're on" : "Friends only see that you're in game";
        });
    }

    private static String nameOf(String name) {
        return name == null ? "They" : name;
    }

    private static void simple(String path, String key, Object value, String ok, Consumer<String> done) {
        action(done, (me, myName) -> {
            JsonObject body = new JsonObject();
            if (value instanceof Number n) body.addProperty(key, n);
            else body.addProperty(key, String.valueOf(value));
            signedIn(me, myName, path, body);
            return ok;
        });
    }

    interface Action {
        String run(UUID me, String name) throws Exception;
    }

    static void action(Consumer<String> done, Action action) {
        Minecraft mc = Minecraft.getInstance();
        UUID me = mc.getUser().getProfileId();
        String name = mc.getUser().getName();
        CompletableFuture.supplyAsync(() -> {
            try {
                String message = action.run(me, name);
                // show the change straight away
                nextPoll = 0L;
                return message;
            } catch (Locked | Failed e) {
                return e.getMessage();
            } catch (Exception e) {
                return "Couldn't reach Golem's Client's server. Try again in a bit.";
            }
        }).thenAccept(message -> mc.execute(() -> done.accept(message)));
    }

    // ------------------------------------------------------------------ where you are

    /** "" in the menus, "Singleplayer", "a private server", or the server's address. */
    static String where(Minecraft mc) {
        if (mc.level == null) return "";
        if (mc.hasSingleplayerServer()) return "Singleplayer";
        ServerData data = mc.getCurrentServer();
        if (data == null || data.ip == null) return "";
        String ip = data.ip.trim().toLowerCase(Locale.ROOT);
        if (ip.endsWith(":25565")) ip = ip.substring(0, ip.length() - 6);
        String host = ip.startsWith("[") || ip.chars().filter(c -> c == ':').count() > 1 ? ip : ip.replaceAll(":\\d+$", "");
        // addresses that point at someone's own machine or network stay private
        if (host.isEmpty() || !host.contains(".") || IPV4.matcher(host).matches() || host.contains(":")
                || host.equals("localhost") || host.endsWith(".local") || host.endsWith(".lan") || !SAFE.matcher(ip).matches()) {
            return "a private server";
        }
        return ip;
    }

    // ------------------------------------------------------------------ the key

    private static Path keyFile(String uuid) {
        return Path.of(System.getProperty("user.home"), ".golemsclient", "friends", uuid + ".key");
    }

    private static String readKey(String uuid) {
        try {
            Path f = keyFile(uuid);
            if (!Files.exists(f)) return null;
            String k = Files.readString(f, StandardCharsets.UTF_8).trim();
            return k.matches("[0-9a-f]{64}") ? k : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeKey(String uuid, String key) {
        try {
            Path f = keyFile(uuid);
            Files.createDirectories(f.getParent());
            Files.writeString(f, key, StandardCharsets.UTF_8);
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't save the friends key", e);
        }
    }

    // ------------------------------------------------------------------ http

    /** The account's UUID (no dashes) from Mojang, "" if there's no such account, null if Mojang can't be reached. */
    private static String mojangUuid(String name) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/"
                    + URLEncoder.encode(name, StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404 || res.statusCode() == 204) return "";
            if (res.statusCode() != 200) return null;
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            return o.has("id") ? o.get("id").getAsString().toLowerCase(Locale.ROOT) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private record Answer(int status, JsonObject json) {
        String message() {
            return json != null && json.has("error") ? json.get("error").getAsString() : "Something went wrong (" + status + ").";
        }
    }

    private static HttpRequest.Builder request(String path, JsonObject body) {
        return HttpRequest.newBuilder(URI.create(SERVER + path)).timeout(Duration.ofSeconds(10))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
    }

    private static Answer post(String path, JsonObject body) throws Exception {
        HttpResponse<String> res = HTTP.send(request(path, body).build(), HttpResponse.BodyHandlers.ofString());
        JsonObject json = null;
        try {
            JsonElement e = JsonParser.parseString(res.body());
            if (e.isJsonObject()) json = e.getAsJsonObject();
        } catch (Exception ignored) {
        }
        return new Answer(res.statusCode(), json == null ? new JsonObject() : json);
    }

    private static final class Locked extends Exception {
        Locked(String message) {
            super(message);
        }
    }

    private static final class Failed extends Exception {
        Failed(String message) {
            super(message);
        }
    }

    private static State sample() {
        long now = System.currentTimeMillis() / 1000L;
        UUID a = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), b = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"),
                c = UUID.fromString("61699b2e-d327-4a01-9f1e-0ea8c3f06bc6"), d = UUID.fromString("dabe852b-94dd-4bb9-b18d-151c206be824");
        return new State(List.of(
                new Friend(d, "emeraldgolem3", now - 86400L * 30, true, "game", "donutsmp.net", "1.21.11", 0L),
                new Friend(a, "Notch", now - 86400L * 3, false, "game", "Singleplayer", "1.21.11", 0L),
                new Friend(b, "jeb_", now - 86400L * 9, false, "launcher", null, null, 0L),
                new Friend(c, "Dinnerbone", now - 86400L * 20, false, "offline", null, null, now - 7200L)),
                List.of(new Request(1, UUID.fromString("f7c77d99-9f15-4a66-a87d-c4a51ef30d19"), "hypixel", now - 600L)),
                List.of(), List.of(), true);
    }

    // ------------------------------------------------------------------ parsing

    private static State parse(JsonObject o) {
        List<Friend> friends = new ArrayList<>();
        for (JsonElement e : arr(o, "friends")) {
            JsonObject f = e.getAsJsonObject();
            JsonObject s = f.has("status") ? f.getAsJsonObject("status") : new JsonObject();
            friends.add(new Friend(uuid(str(f, "uuid")), str(f, "name"), num(f, "since"), f.has("owner") && f.get("owner").getAsBoolean(),
                    s.has("state") ? s.get("state").getAsString() : "offline", str(s, "server"), str(s, "mc"), num(s, "lastSeen")));
        }
        // online first (in game, then launcher), then by name
        friends.sort((a, b) -> {
            int ra = rank(a), rb = rank(b);
            if (ra != rb) return Integer.compare(ra, rb);
            return String.CASE_INSENSITIVE_ORDER.compare(a.name() == null ? "" : a.name(), b.name() == null ? "" : b.name());
        });
        List<Request> incoming = requests(o, "incoming"), outgoing = requests(o, "outgoing");
        List<Blocked> blocked = new ArrayList<>();
        for (JsonElement e : arr(o, "blocked")) {
            JsonObject b = e.getAsJsonObject();
            blocked.add(new Blocked(uuid(str(b, "uuid")), str(b, "name"), num(b, "since")));
        }
        boolean share = !o.has("me") || !o.getAsJsonObject("me").has("shareServer") || o.getAsJsonObject("me").get("shareServer").getAsBoolean();
        return new State(List.copyOf(friends), incoming, outgoing, List.copyOf(blocked), share);
    }

    private static int rank(Friend f) {
        return switch (f.state()) {
            case "game" -> 0;
            case "launcher" -> 1;
            default -> 2;
        };
    }

    private static List<Request> requests(JsonObject o, String key) {
        List<Request> out = new ArrayList<>();
        for (JsonElement e : arr(o, key)) {
            JsonObject r = e.getAsJsonObject();
            out.add(new Request(r.get("id").getAsInt(), uuid(str(r, "uuid")), str(r, "name"), num(r, "at")));
        }
        return List.copyOf(out);
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static long num(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0L;
    }

    private static UUID uuid(String s) {
        if (s == null || s.length() != 32) return new UUID(0L, 0L);
        return UUID.fromString(s.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }
}
