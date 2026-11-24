package dev.golemsclient.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golemsclient.GolemsClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The banner at the top of the main menu: the newest announcement the owner posted from the launcher's Owner tab.
 * Asked for in the background when the menu opens (at most every two minutes); nothing is sent but the request.
 */
public final class Announcement {
    public record Post(String id, String text, long at) { }

    private static final String URL = "https://c418-presence.c418client.workers.dev/launcher/announcement";
    private static final long EVERY_MS = 2 * 60 * 1000L;

    private static volatile Post current;
    private static volatile long askedAt;

    private Announcement() {
    }

    /** The current announcement, or null. Asks the server again when the last answer is old. */
    public static Post get() {
        long now = System.currentTimeMillis();
        if (now - askedAt > EVERY_MS) {
            askedAt = now;
            Thread t = new Thread(Announcement::fetch, "Golem's Client announcement");
            t.setDaemon(true);
            t.start();
        }
        return current;
    }

    /** Dev screenshots only: shows this text without asking the server. */
    public static void devFake(String text) {
        current = new Post("dev", text, System.currentTimeMillis() / 1000L - 600L);
        askedAt = Long.MAX_VALUE / 2;
    }

    private static void fetch() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Golem's Client " + GolemsClient.version()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return;
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (!o.has("text") || !o.has("id")) {
                current = null;
                return;
            }
            String text = o.get("text").getAsString().strip();
            current = text.isEmpty() ? null
                    : new Post(o.get("id").getAsString(), text, o.has("at") ? o.get("at").getAsLong() : 0L);
        } catch (Exception e) {
            GolemsClient.LOGGER.debug("Couldn't get the announcement", e);
        }
    }
}
