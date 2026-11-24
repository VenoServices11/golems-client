package dev.golemsclient.badge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.golemsclient.GolemsClient;
import dev.golemsclient.menu.MenuData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The G badge in front of the names of players who play with Golem's Client, on their nametag and in the tab list
 * (OneClient's user indicators, ported from PolyPlus, GPL-3.0, by Polyfrost). Owners get a gold G.
 *
 * <p>The game tells the server it's running (your player id, every two minutes), and asks it, in batches, which of
 * the players around you are Golem's Client players. Nothing else is sent.
 */
public final class Badges {
    private static final String SERVER = "https://c418-presence.c418client.workers.dev";
    private static final boolean DEBUG_FORCE = Boolean.getBoolean("golemsclient.badge.debug");

    private static final long PING_EVERY_MS = 2 * 60 * 1000L;
    private static final long RECHECK_MS = 5 * 60 * 1000L;
    private static final long ASK_EVERY_MS = 2000L;
    private static final int MAX_ASK = 300;

    /** 1: plays with Golem's Client, 2: an owner. Players not in here have no badge. */
    private static final Map<UUID, Integer> MARKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private static volatile boolean asking;
    private static long lastPing = -PING_EVERY_MS, lastAsk;

    // ------------------------------------------------------------------ the look

    private static final Identifier FONT = Identifier.fromNamespaceAndPath(GolemsClient.MOD_ID, "badge");
    private static final Style BADGE_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(FONT)).withColor(0xFFFFFF);
    /** The G, then a little space before the name. */
    private static final Component GLYPH = Component.literal("").setStyle(BADGE_STYLE);
    private static final Component GLYPH_OWNER = Component.literal("").setStyle(BADGE_STYLE);

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GolemsClient.MOD_ID, "textures/badge.png");
    private static final Identifier TEXTURE_OWNER = Identifier.fromNamespaceAndPath(GolemsClient.MOD_ID, "textures/badge_owner.png");
    private static final int TEX_W = 64, TEX_H = 40;
    private static final int BADGE_H = 8;
    private static final int BADGE_W = (BADGE_H * TEX_W + TEX_H / 2) / TEX_H;
    private static final int PAD_LEFT = 1, PAD_RIGHT = 2;
    /** How far the name moves right in the tab list to make room for the badge. */
    public static final int TAB_ADVANCE = PAD_LEFT + BADGE_W + PAD_RIGHT;

    private Badges() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Badges::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            synchronized (PROXIED) {
                PROXIED.clear();
            }
        });
    }

    // ------------------------------------------------------------------ who has one

    /** 0 for no badge, 1 for the G, 2 for the owner's gold G. */
    public static int mark(UUID id) {
        if (id == null || !MenuData.get().showBadges) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (id.equals(mc.getUser().getProfileId())) return GolemsClient.isOwner(id) ? 2 : 1;
        if (DEBUG_FORCE) return GolemsClient.isOwner(id) ? 2 : 1;
        return MARKS.getOrDefault(id, 0);
    }

    /** The nametag with the badge in front of it, or the nametag as it was. */
    public static Component decorate(Component name, UUID id) {
        int mark = mark(id);
        if (mark == 0) return name;
        return Component.empty().append(mark == 2 ? GLYPH_OWNER : GLYPH).append(name);
    }

    /** The badge for a tab list entry (0, 1 or 2). */
    public static int tabMark(PlayerInfo info) {
        if (info == null) return 0;
        int mark = mark(info.getProfile().id());
        if (mark != 0) return mark;
        UUID proxied = proxiedTabUuid(info);
        return proxied == null ? 0 : mark(proxied);
    }

    public static void blitTab(GuiGraphics g, int x, int y, int mark) {
        g.blit(RenderPipelines.GUI_TEXTURED, mark == 2 ? TEXTURE_OWNER : TEXTURE, x + PAD_LEFT, y, 0f, 0f,
                BADGE_W, BADGE_H, TEX_W, TEX_H, TEX_W, TEX_H);
    }

    // Some servers (Hypixel SkyBlock, for one) fill the tab list with placeholder profiles and put the real names in
    // the display names, so a name in there is matched to a real player (as OneClient does).
    private static final Pattern NAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private record Proxied(String displayName, UUID resolved) { }
    private static final LinkedHashMap<UUID, Proxied> PROXIED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Proxied> eldest) {
            return size() > 256;
        }
    };

    private static UUID proxiedTabUuid(PlayerInfo info) {
        Component shown = info.getTabListDisplayName();
        if (shown == null) return null;
        String displayName = shown.getString();
        UUID id = info.getProfile().id();
        synchronized (PROXIED) {
            Proxied cached = PROXIED.get(id);
            if (cached != null && cached.displayName().equals(displayName)) return cached.resolved();
            UUID resolved = null;
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                Matcher m = NAME_TOKEN.matcher(displayName);
                while (m.find()) {
                    PlayerInfo entry = connection.getPlayerInfo(m.group());
                    if (entry == null) continue;
                    UUID real = entry.getProfile().id();
                    if (real.version() == 4) {
                        resolved = real;
                        break;
                    }
                }
            }
            PROXIED.put(id, new Proxied(displayName, resolved));
            return resolved;
        }
    }

    // ------------------------------------------------------------------ the server

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        UUID self = mc.getUser().getProfileId();
        if (self != null && now - lastPing > PING_EVERY_MS) {
            lastPing = now;
            JsonObject body = new JsonObject();
            body.addProperty("uuid", self.toString());
            post("/client/ping", body);
        }
        if (asking || now - lastAsk < ASK_EVERY_MS || !MenuData.get().showBadges) return;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;
        lastAsk = now;
        List<UUID> ask = new ArrayList<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) want(info.getProfile(), self, now, ask);
        if (mc.level != null) for (Player p : mc.level.players()) want(p.getGameProfile(), self, now, ask);
        synchronized (PROXIED) {
            for (Proxied p : PROXIED.values()) if (p.resolved() != null && ask.size() < MAX_ASK && due(p.resolved(), self, now)) {
                ask.add(p.resolved());
                ASKED.put(p.resolved(), now);
            }
        }
        if (ask.isEmpty()) return;
        asking = true;
        JsonObject body = new JsonObject();
        JsonArray ids = new JsonArray();
        for (UUID id : ask) ids.add(id.toString());
        body.add("uuids", ids);
        Thread t = new Thread(() -> {
            try {
                String answer = send("/client/lookup", body);
                if (answer == null) {
                    // try these again soon
                    for (UUID id : ask) ASKED.remove(id);
                    return;
                }
                JsonObject o = JsonParser.parseString(answer).getAsJsonObject();
                java.util.Set<String> users = strings(o, "users"), owners = strings(o, "owners");
                for (UUID id : ask) {
                    String key = id.toString().replace("-", "");
                    int mark = owners.contains(key) ? 2 : users.contains(key) ? 1 : 0;
                    if (mark == 0) MARKS.remove(id);
                    else MARKS.put(id, mark);
                }
            } catch (Exception e) {
                GolemsClient.LOGGER.debug("Couldn't look up badges: {}", e.toString());
            } finally {
                asking = false;
            }
        }, "Golem's Client badges");
        t.setDaemon(true);
        t.start();
    }

    private static void want(GameProfile profile, UUID self, long now, List<UUID> ask) {
        if (profile == null || ask.size() >= MAX_ASK) return;
        UUID id = profile.id();
        // only real accounts (servers' NPCs and placeholders have other kinds of ids)
        if (id == null || id.version() != 4 || !due(id, self, now)) return;
        ask.add(id);
        ASKED.put(id, now);
    }

    private static boolean due(UUID id, UUID self, long now) {
        if (id.equals(self)) return false;
        Long at = ASKED.get(id);
        return at == null || now - at > RECHECK_MS;
    }

    private static java.util.Set<String> strings(JsonObject o, String key) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (o.has(key) && o.get(key).isJsonArray()) for (JsonElement el : o.getAsJsonArray(key)) out.add(el.getAsString());
        return out;
    }

    private static void post(String path, JsonObject body) {
        Thread t = new Thread(() -> send(path, body), "Golem's Client ping");
        t.setDaemon(true);
        t.start();
    }

    private static String send(String path, JsonObject body) {
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(SERVER + path)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Golem's Client " + GolemsClient.version())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            GolemsClient.LOGGER.debug("Couldn't reach {}: {}", path, e.toString());
            return null;
        }
    }
}
