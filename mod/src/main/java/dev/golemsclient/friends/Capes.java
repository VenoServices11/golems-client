package dev.golemsclient.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golemsclient.GolemsClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Golem's Client capes: the cape each Golem's Client player wears shows on them for everyone with Golem's Client. The
 * pictures ship with the game (assets/golemsclient/textures/capes); the server only keeps who wears which.
 */
public final class Capes {
    private static final String SERVER = "https://c418-presence.c418client.workers.dev";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    /** The capes the game has pictures for. */
    public static final Set<String> KNOWN = Set.of("graphite", "midnight", "owner", "supporter", "og");
    private static final long RECHECK_MS = 3 * 60 * 1000L, ASK_EVERY_MS = 2000L;

    public record Cape(String id, String name, String kind) {
    }

    /** uuid -> cape id ("" for none), with when it was asked. */
    private static final Map<UUID, String> worn = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> asked = new ConcurrentHashMap<>();
    private static volatile boolean asking;
    private static long lastAsk;
    private static volatile List<Cape> mine;
    private static volatile String wearing;

    private Capes() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Capes::tick);
    }

    // ------------------------------------------------------------------ on players

    /** The player's skin with their Golem's Client cape, if they wear one. */
    public static PlayerSkin apply(UUID id, PlayerSkin original) {
        if (id == null || original == null) return original;
        String cape = capeOf(id);
        if (cape == null || cape.isEmpty() || !KNOWN.contains(cape)) return original;
        return new PlayerSkin(original.body(), new ClientAsset.ResourceTexture(texture(cape)), original.elytra(), original.model(),
                original.secure());
    }

    /** golemsclient:capes/<id> (the picture is at textures/capes/<id>.png). */
    public static Identifier texture(String cape) {
        return Identifier.fromNamespaceAndPath(GolemsClient.MOD_ID, "capes/" + cape);
    }

    private static String capeOf(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null && id.equals(mc.getUser().getProfileId()) && wearing != null) return wearing;
        if (dev.golemsclient.DevShots.ON) return "graphite";
        return worn.get(id);
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (asking || now - lastAsk < ASK_EVERY_MS || mc.level == null || dev.golemsclient.DevShots.ON) return;
        lastAsk = now;
        List<String> ask = new ArrayList<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            UUID id = p.getUUID();
            Long at = asked.get(id);
            if (at == null || now - at > RECHECK_MS) {
                asked.put(id, now);
                ask.add(id.toString().replace("-", ""));
                if (ask.size() >= 300) break;
            }
        }
        if (ask.isEmpty()) return;
        asking = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                JsonArray list = new JsonArray();
                ask.forEach(list::add);
                body.add("uuids", list);
                HttpRequest req = HttpRequest.newBuilder(URI.create(SERVER + "/capes/lookup")).timeout(Duration.ofSeconds(10))
                        .header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) return;
                JsonObject capes = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("capes");
                for (String u : ask) {
                    UUID id = uuid(u);
                    worn.put(id, capes.has(u) ? capes.get(u).getAsString() : "");
                }
            } catch (Exception e) {
                GolemsClient.LOGGER.debug("Capes: {}", e.toString());
            } finally {
                asking = false;
            }
        });
    }

    // ------------------------------------------------------------------ yours

    /** The capes you can wear (null before they're loaded). */
    public static List<Cape> mine() {
        return mine;
    }

    /** The cape you wear ("" for none, null before it's known). */
    public static String wearing() {
        return wearing;
    }

    public static String name(String id) {
        List<Cape> list = mine;
        if (list != null) for (Cape c : list) if (c.id().equals(id)) return c.name();
        return id;
    }

    /** Loads the capes you can wear. */
    public static void refresh(Consumer<String> failed) {
        if (dev.golemsclient.DevShots.ON) {
            mine = List.of(new Cape("graphite", "Graphite", "free"), new Cape("midnight", "Midnight", "free"));
            wearing = "graphite";
            return;
        }
        Friends.action(message -> {
            if (message != null && failed != null) failed.accept(message);
        }, (me, name) -> {
            JsonObject a = Friends.signedIn(me, name, "/capes/mine", new JsonObject());
            List<Cape> list = new ArrayList<>();
            for (JsonElement e : a.getAsJsonArray("capes")) {
                JsonObject c = e.getAsJsonObject();
                if (KNOWN.contains(c.get("id").getAsString())) {
                    list.add(new Cape(c.get("id").getAsString(), c.get("name").getAsString(), c.get("kind").getAsString()));
                }
            }
            mine = List.copyOf(list);
            wearing = a.has("wearing") ? a.get("wearing").getAsString() : "";
            return null;
        });
    }

    /** Wears a cape ("" for none). */
    public static void wear(String id, Consumer<String> done) {
        String before = wearing;
        wearing = id;
        Friends.action(message -> {
            if (message != null) {
                wearing = before;
                done.accept(message);
            } else {
                done.accept(id.isEmpty() ? "Cape taken off" : "Wearing the " + name(id) + " cape");
            }
        }, (me, name) -> {
            JsonObject body = new JsonObject();
            body.addProperty("cape", id);
            Friends.signedIn(me, name, "/capes/wear", body);
            worn.put(me, id);
            return null;
        });
    }

    /** A cape's picture, for the Wardrobe. */
    public static byte[] png(String id) {
        try (InputStream in = Capes.class.getResourceAsStream("/assets/golemsclient/textures/capes/" + id + ".png")) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID uuid(String s) {
        return UUID.fromString(s.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }
}
