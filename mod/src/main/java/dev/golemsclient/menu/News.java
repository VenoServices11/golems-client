package dev.golemsclient.menu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golemsclient.GolemsClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The News page's posts: what the owner wrote from the launcher's Owner tab, newest first. Asked for in the background
 * (at most every two minutes); nothing is sent but the request.
 */
public final class News {
    public record Post(String id, String title, String body, String link, long at) { }

    private static final String URL = "https://c418-presence.c418client.workers.dev/launcher/news";
    private static final long EVERY_MS = 2 * 60 * 1000L;

    private static volatile List<Post> posts;
    private static volatile boolean failed;
    private static volatile long askedAt;

    private News() {
    }

    /** The posts (null until the first answer). Asks the server again when the last answer is old. */
    public static List<Post> get() {
        long now = System.currentTimeMillis();
        if (now - askedAt > EVERY_MS) {
            askedAt = now;
            Thread t = new Thread(News::fetch, "Golem's Client news");
            t.setDaemon(true);
            t.start();
        }
        return posts;
    }

    /** True when the server couldn't be reached (and nothing came before). */
    public static boolean failed() {
        return failed && posts == null;
    }

    /** True when there's a post newer than the last one you saw on the News page. */
    public static boolean unread() {
        List<Post> p = get();
        return p != null && !p.isEmpty() && !p.get(0).id().equals(MenuData.get().lastNewsSeen);
    }

    /** The News page was looked at: the newest post counts as seen. */
    public static void seen() {
        List<Post> p = posts;
        if (p == null || p.isEmpty()) return;
        MenuData d = MenuData.get();
        if (p.get(0).id().equals(d.lastNewsSeen)) return;
        d.lastNewsSeen = p.get(0).id();
        d.save();
    }

    /** Dev screenshots only: shows these posts without asking the server. */
    public static void devFake(List<Post> fake) {
        posts = fake;
        askedAt = Long.MAX_VALUE / 2;
    }

    private static void fetch() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Golem's Client " + GolemsClient.version()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                failed = true;
                return;
            }
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            List<Post> list = new ArrayList<>();
            if (o.has("posts") && o.get("posts").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("posts")) {
                    JsonObject p = el.getAsJsonObject();
                    list.add(new Post(str(p, "id"), str(p, "title"), str(p, "body"), str(p, "link"), p.has("at") ? p.get("at").getAsLong() : 0L));
                }
            }
            posts = list;
            failed = false;
        } catch (Exception e) {
            failed = true;
            GolemsClient.LOGGER.debug("Couldn't get the news", e);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
