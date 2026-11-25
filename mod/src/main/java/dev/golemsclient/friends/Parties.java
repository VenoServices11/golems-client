package dev.golemsclient.friends;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Golem's Client parties, in game: up to 8 friends together, with a party chat. When the leader joins a server, the
 * others get a pop-up to follow them (J joins; nobody is moved without pressing it).
 */
public final class Parties {
    public static final int MAX = 8;

    public record Member(UUID uuid, String name, boolean online, String where) {
    }

    public record Party(String id, UUID leader, List<Member> members, int chat, String server, long serverAt) {
        public boolean leads(UUID me) {
            return leader.equals(me);
        }

        public Member leaderMember() {
            for (Member m : members) if (m.uuid().equals(leader)) return m;
            return null;
        }
    }

    public record Invite(String id, UUID from, String fromName, int size) {
    }

    private static volatile Party party;
    private static volatile List<Invite> invites = List.of();
    private static final Set<String> seenInvites = new HashSet<>();
    private static boolean seenFilled;
    private static long followedAt;
    private static Consumer<String> invited = s -> { };

    private Parties() {
    }

    /** Your party, or null. */
    public static Party party() {
        return party;
    }

    public static List<Invite> invites() {
        return invites;
    }

    /** Where party invites are announced. */
    public static void onInvite(Consumer<String> show) {
        invited = show == null ? s -> { } : show;
    }

    // ------------------------------------------------------------------ from the friends list poll

    static void polled(JsonObject answer, UUID me) {
        if (!answer.has("parties") || !answer.get("parties").isJsonObject()) return;
        JsonObject p = answer.getAsJsonObject("parties");
        Party before = party;
        party = p.has("party") && p.get("party").isJsonObject() ? party(p.getAsJsonObject("party")) : null;
        List<Invite> list = new ArrayList<>();
        if (p.has("incoming")) {
            for (JsonElement e : p.getAsJsonArray("incoming")) {
                JsonObject i = e.getAsJsonObject();
                list.add(new Invite(i.get("id").getAsString(), uuid(i.get("sender").getAsString()),
                        i.has("sender_name") && !i.get("sender_name").isJsonNull() ? i.get("sender_name").getAsString() : null,
                        i.get("party_size").getAsInt()));
            }
        }
        invites = List.copyOf(list);
        for (Invite i : list) {
            if (seenInvites.add(i.id()) && seenFilled) {
                String who = i.fromName() == null ? "A friend" : i.fromName();
                Minecraft.getInstance().execute(() -> invited.accept(who + " invited you to their party"));
            }
        }
        seenFilled = true;
        // the leader moved to a server: offer to follow
        Party now = party;
        if (now != null && !now.leads(me) && now.server() != null && now.serverAt() > followedAt
                && (before == null || before.serverAt() != now.serverAt() || !now.id().equals(before.id()))) {
            followedAt = now.serverAt();
            if (!now.server().equalsIgnoreCase(currentAddress())) {
                Member leader = now.leaderMember();
                String name = leader == null || leader.name() == null ? "Your party leader" : leader.name();
                Minecraft.getInstance().execute(() -> MessagePopups.follow(name, now.server()));
            }
        }
    }

    /** The full address of the server you're on, for the party leader's presence ("" when not on one). */
    static String currentAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hasSingleplayerServer()) return "";
        ServerData data = mc.getCurrentServer();
        return data == null || data.ip == null ? "" : data.ip.trim();
    }

    /** Your game is in a party and leads it: its server goes to the party. */
    static boolean leading(UUID me) {
        Party p = party;
        return p != null && p.leads(me);
    }

    // ------------------------------------------------------------------ actions

    public static void invite(UUID friend, Consumer<String> done) {
        call("/party/invite", "uuid", friend.toString().replace("-", ""), "Party invite sent", done);
    }

    public static void accept(Invite i, Consumer<String> done) {
        Friends.action(done, (me, name) -> {
            JsonObject body = new JsonObject();
            body.addProperty("id", i.id());
            body.addProperty("leave_current", true);
            party = party(Friends.signedIn(me, name, "/party/accept", body));
            invites = List.copyOf(invites.stream().filter(x -> !x.id().equals(i.id())).toList());
            Chats.refresh();
            return "You joined the party";
        });
    }

    public static void decline(Invite i, Consumer<String> done) {
        invites = List.copyOf(invites.stream().filter(x -> !x.id().equals(i.id())).toList());
        call("/party/decline", "id", i.id(), "Invite declined", done);
    }

    public static void leave(Consumer<String> done) {
        party = null;
        call("/party/leave", null, null, "You left the party", done);
    }

    public static void kick(Member m, Consumer<String> done) {
        call("/party/kick", "uuid", m.uuid().toString().replace("-", ""), (m.name() == null ? "They were" : m.name() + " was") + " removed from the party", done);
    }

    public static void makeLeader(Member m, Consumer<String> done) {
        call("/party/leader", "uuid", m.uuid().toString().replace("-", ""), (m.name() == null ? "They lead" : m.name() + " leads") + " the party now", done);
    }

    private static void call(String path, String key, String value, String ok, Consumer<String> done) {
        Friends.action(done, (me, name) -> {
            JsonObject body = new JsonObject();
            if (key != null) body.addProperty(key, value);
            Friends.signedIn(me, name, path, body);
            return ok;
        });
    }

    /** Joins the server the party leader is on. */
    public static void follow() {
        Party p = party;
        if (p == null || p.server() == null) return;
        join(p.server());
    }

    static void join(String address) {
        Minecraft mc = Minecraft.getInstance();
        if (address.equalsIgnoreCase(currentAddress())) return;
        if (!ServerAddress.isValidAddress(address)) return;
        if (mc.level != null) mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
        ServerData data = new ServerData("Party", address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new dev.golemsclient.menu.MainMenu(), mc, ServerAddress.parseString(address), data, false, null);
    }

    // ------------------------------------------------------------------ parsing

    private static Party party(JsonObject o) {
        Map<UUID, String> names = new HashMap<>();
        if (o.has("names") && o.get("names").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("names").entrySet()) names.put(uuid(e.getKey()), e.getValue().getAsString());
        }
        List<Member> members = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("members")) {
            JsonObject m = e.getAsJsonObject();
            UUID id = uuid(m.get("player").getAsString());
            String where = null;
            if (m.has("state") && m.get("state").isJsonObject()) {
                JsonObject loc = m.getAsJsonObject("state").getAsJsonObject("location");
                String kind = loc.get("kind").getAsString();
                String display = loc.has("display") && !loc.get("display").isJsonNull() ? loc.get("display").getAsString() : null;
                where = switch (kind) {
                    case "server" -> display == null ? "In game" : "Playing on " + display;
                    case "singleplayer" -> "Playing singleplayer";
                    case "other" -> display == null ? "Online" : display;
                    default -> m.get("online").getAsBoolean() ? "In the menus" : "Offline";
                };
            }
            members.add(new Member(id, names.get(id), m.get("online").getAsBoolean(), where));
        }
        return new Party(o.get("id").getAsString(), uuid(o.get("leader").getAsString()), List.copyOf(members),
                o.has("chat_group_id") && !o.get("chat_group_id").isJsonNull() ? o.get("chat_group_id").getAsInt() : -1,
                o.has("server") && !o.get("server").isJsonNull() ? o.get("server").getAsString() : null,
                o.has("server_at") ? o.get("server_at").getAsLong() : 0L);
    }

    private static UUID uuid(String s) {
        String t = s.replace("-", "");
        if (t.length() != 32) return new UUID(0L, 0L);
        return UUID.fromString(t.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    /** Dev screenshots only: a made-up party. */
    static void devSample(UUID me) {
        if (party != null) return;
        UUID owner = UUID.fromString("dabe852b-94dd-4bb9-b18d-151c206be824"), notch = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        party = new Party("dev", owner, List.of(
                new Member(owner, "emeraldgolem3", true, "Playing on donutsmp.net"),
                new Member(me, "You", true, "In the menus"),
                new Member(notch, "Notch", false, "Offline")), 2, "donutsmp.net", 1L);
    }
}
