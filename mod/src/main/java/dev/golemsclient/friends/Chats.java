package dev.golemsclient.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.golemsclient.GolemsClient;
import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Golem's Client messages, in game: your chats (one-on-one with friends, and groups), the messages in the one you
 * have open, and a pop-up when a message arrives while you play. The server keeps messages for 30 days.
 */
public final class Chats {
    public record Chat(int id, boolean group, String name, List<UUID> members, Map<UUID, String> names, String lastText,
                       UUID lastSender, long lastAt, boolean unread) {
        /** A group's name (or its members), or the friend's name for a one-on-one chat. */
        public String title(UUID me) {
            if (group && name != null && !name.isBlank()) return name;
            List<String> others = new ArrayList<>();
            for (UUID m : members) if (!m.equals(me)) others.add(nameOf(m, this));
            if (others.isEmpty()) return group ? "Group" : "Chat";
            return String.join(", ", others);
        }

        /** The other person in a one-on-one chat. */
        public UUID other(UUID me) {
            for (UUID m : members) if (!m.equals(me)) return m;
            return me;
        }
    }

    public record Message(long id, int chat, UUID sender, String text, long at, boolean edited, boolean pending) {
    }

    private static volatile List<Chat> chats;
    private static volatile boolean loadingChats;
    private static volatile long latest = -1L;
    private static volatile int unread;
    private static volatile Integer viewing;
    private static final Map<Integer, List<Message>> threads = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> loading = new ConcurrentHashMap<>();
    private static Consumer<Message> popup = m -> { };

    private Chats() {
    }

    /** Where new messages are shown while you play. */
    public static void onMessage(Consumer<Message> show) {
        popup = show == null ? m -> { } : show;
    }

    /** Your chats (newest first), or null before they're loaded. */
    public static List<Chat> chats() {
        return chats;
    }

    /** How many chats have unread messages. */
    public static int unread() {
        return unread;
    }

    public static Chat chat(int id) {
        List<Chat> list = chats;
        if (list != null) for (Chat c : list) if (c.id() == id) return c;
        return null;
    }

    /** The messages of a chat you opened, oldest first (null while loading). */
    public static List<Message> thread(int id) {
        return threads.get(id);
    }

    /** A name for a player: from the chat, your friends, or "Someone". */
    public static String nameOf(UUID id, Chat chat) {
        if (chat != null && chat.names().containsKey(id)) return chat.names().get(id);
        Friends.State s = Friends.state();
        if (s != null) for (Friends.Friend f : s.friends()) if (f.uuid().equals(id) && f.name() != null) return f.name();
        if (Minecraft.getInstance().getUser() != null && id.equals(Minecraft.getInstance().getUser().getProfileId())) {
            return Minecraft.getInstance().getUser().getName();
        }
        return "Someone";
    }

    // ------------------------------------------------------------------ polling (from Friends)

    /** The newest message id seen (-1 before the first answer). */
    static long since() {
        return latest;
    }

    static void polled(JsonObject answer, UUID me) {
        JsonObject inbox = answer.has("inbox") ? answer.getAsJsonObject("inbox") : null;
        if (inbox == null) return;
        long newest = inbox.get("latest").getAsLong();
        unread = inbox.get("unread").getAsInt();
        boolean first = latest < 0;
        List<Message> fresh = new ArrayList<>();
        if (answer.has("messages")) for (JsonElement e : answer.getAsJsonArray("messages")) fresh.add(message(e.getAsJsonObject()));
        latest = Math.max(latest, newest);
        if (first) return;
        for (Message m : fresh) {
            List<Message> t = threads.get(m.chat());
            if (t != null) add(m.chat(), m);
            boolean mine = m.sender().equals(me);
            Integer open = viewing;
            if (open != null && open == m.chat()) {
                markRead(m.chat(), m.id());
            } else if (!mine) {
                Minecraft.getInstance().execute(() -> popup.accept(m));
            }
        }
        if (!fresh.isEmpty() || chats == null) refresh();
    }

    // ------------------------------------------------------------------ loading

    /** Loads your chats again. */
    public static void refresh() {
        if (loadingChats) return;
        loadingChats = true;
        run(() -> {
            try {
                JsonObject a = call("/messages/groups", new JsonObject());
                List<Chat> list = new ArrayList<>();
                for (JsonElement e : a.getAsJsonArray("groups")) list.add(chat(e.getAsJsonObject()));
                chats = List.copyOf(list);
            } finally {
                loadingChats = false;
            }
            return null;
        }, null);
    }

    /** You're looking at this chat: its messages load and it counts as read. */
    public static void open(int id) {
        viewing = id;
        if (threads.containsKey(id) || loading.putIfAbsent(id, true) != null) {
            List<Message> t = threads.get(id);
            if (t != null && !t.isEmpty()) markRead(id, t.get(t.size() - 1).id());
            return;
        }
        run(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("id", id);
                body.addProperty("limit", 100);
                JsonObject a = call("/messages/list", body);
                List<Message> list = new ArrayList<>();
                for (JsonElement e : a.getAsJsonArray("messages")) list.add(message(e.getAsJsonObject()));
                threads.put(id, List.copyOf(list));
                if (!list.isEmpty()) markRead(id, list.get(list.size() - 1).id());
            } finally {
                loading.remove(id);
            }
            return null;
        }, null);
    }

    public static void close() {
        viewing = null;
    }

    private static void markRead(int id, long message) {
        List<Chat> list = chats;
        if (list != null) {
            List<Chat> next = new ArrayList<>();
            for (Chat c : list) {
                next.add(c.id() == id && c.unread() ? new Chat(c.id(), c.group(), c.name(), c.members(), c.names(), c.lastText(),
                        c.lastSender(), c.lastAt(), false) : c);
            }
            chats = List.copyOf(next);
        }
        run(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("id", id);
            body.addProperty("message", message);
            call("/messages/read", body);
            return null;
        }, null);
    }

    /** Threads are replaced, never changed in place, so drawing can read them at any time. */
    private static synchronized void add(int chat, Message m) {
        List<Message> t = new ArrayList<>(threads.getOrDefault(chat, List.of()));
        t.removeIf(x -> x.id() == m.id());
        t.add(m);
        t.sort((a, b) -> Long.compare(a.id(), b.id()));
        threads.put(chat, List.copyOf(t));
    }

    private static synchronized void drop(int chat, long id) {
        List<Message> t = threads.get(chat);
        if (t == null) return;
        List<Message> next = new ArrayList<>(t);
        next.removeIf(x -> x.id() == id);
        threads.put(chat, List.copyOf(next));
    }

    // ------------------------------------------------------------------ actions

    /** Sends a message; it shows straight away and is replaced by the server's copy once sent. */
    public static void send(int chat, String text, Consumer<String> failed) {
        String content = text.strip();
        if (content.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        UUID me = mc.getUser().getProfileId();
        String key = UUID.randomUUID().toString().replace("-", "");
        long tempId = Long.MAX_VALUE - (System.nanoTime() & 0xFFFFFFL);
        Message pending = new Message(tempId, chat, me, content, Instant.now().getEpochSecond(), false, true);
        add(chat, pending);
        run(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("id", chat);
            body.addProperty("content", content);
            body.addProperty("key", key);
            Message sent = message(call("/messages/send", body));
            drop(chat, tempId);
            add(chat, sent);
            latest = Math.max(latest, sent.id());
            refresh();
            return null;
        }, error -> {
            drop(chat, tempId);
            failed.accept(error);
        });
    }

    /** The one-on-one chat with a friend (made if needed). */
    public static void dm(UUID friend, Consumer<Chat> opened, Consumer<String> failed) {
        run(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("uuid", friend.toString().replace("-", ""));
            Chat c = chat(call("/messages/dm", body));
            put(c);
            mcRun(() -> opened.accept(c));
            return null;
        }, failed);
    }

    public static void createGroup(String name, List<UUID> members, Consumer<Chat> made, Consumer<String> failed) {
        run(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("name", name);
            JsonArray list = new JsonArray();
            for (UUID m : members) list.add(m.toString().replace("-", ""));
            body.add("members", list);
            Chat c = chat(call("/messages/create", body));
            put(c);
            mcRun(() -> made.accept(c));
            return null;
        }, failed);
    }

    public static void leave(int chat, Runnable left, Consumer<String> failed) {
        run(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("id", chat);
            call("/messages/remove", body);
            threads.remove(chat);
            List<Chat> list = chats;
            if (list != null) {
                List<Chat> next = new ArrayList<>(list);
                next.removeIf(c -> c.id() == chat);
                chats = List.copyOf(next);
            }
            mcRun(left);
            return null;
        }, failed);
    }

    private static void put(Chat c) {
        List<Chat> list = chats;
        List<Chat> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
        next.removeIf(x -> x.id() == c.id());
        next.add(0, c);
        chats = List.copyOf(next);
    }

    /** Dev screenshots only: made-up chats. */
    static void devSample(UUID me) {
        if (chats != null) return;
        UUID notch = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), jeb = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
        UUID owner = UUID.fromString("dabe852b-94dd-4bb9-b18d-151c206be824");
        long now = Instant.now().getEpochSecond();
        Map<UUID, String> names = Map.of(notch, "Notch", jeb, "jeb_", owner, "emeraldgolem3", me, "You");
        chats = List.of(
                new Chat(1, false, null, List.of(me, owner), names, "want to grind the afk spot later?", owner, now - 120, true),
                new Chat(2, true, "Base crew", List.of(me, notch, jeb), names, "bring the shulkers", jeb, now - 3600, false),
                new Chat(3, false, null, List.of(me, notch), names, "gg", me, now - 86400 * 2, false));
        threads.put(1, List.of(
                new Message(10, 1, owner, "yo, you on donut today?", now - 900, false, false),
                new Message(11, 1, me, "yeah, just got on. at spawn rn", now - 840, false, false),
                new Message(12, 1, owner, "nice. I found a spot with a ton of skeletons near my base, could make a really good xp farm if we get the spawners sorted", now - 300, false, false),
                new Message(13, 1, owner, "want to grind the afk spot later?", now - 120, false, false)));
        unread = 1;
        latest = 13;
    }

    // ------------------------------------------------------------------ plumbing

    private interface Job {
        Void run() throws Exception;
    }

    private static void run(Job job, Consumer<String> failed) {
        if (dev.golemsclient.DevShots.ON) return; // dev screenshots: made-up chats, nothing sent
        CompletableFuture.runAsync(() -> {
            try {
                job.run();
            } catch (Exception e) {
                String message = e.getMessage() == null || e instanceof java.io.IOException
                        ? "Couldn't reach Golem's Client's server. Try again in a bit." : e.getMessage();
                GolemsClient.LOGGER.debug("Messages: {}", e.toString());
                if (failed != null) mcRun(() -> failed.accept(message));
            }
        });
    }

    private static void mcRun(Runnable r) {
        Minecraft.getInstance().execute(r);
    }

    private static JsonObject call(String path, JsonObject body) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        return Friends.signedIn(mc.getUser().getProfileId(), mc.getUser().getName(), path, body);
    }

    private static UUID uuid(String s) {
        if (s == null || s.length() != 32) return new UUID(0L, 0L);
        return UUID.fromString(s.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    private static long time(JsonElement e) {
        if (e == null || e.isJsonNull()) return 0L;
        try {
            return Instant.parse(e.getAsString()).getEpochSecond();
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static Message message(JsonObject o) {
        return new Message(o.get("id").getAsLong(), o.has("group_id") ? o.get("group_id").getAsInt() : -1, uuid(o.get("sender").getAsString()),
                o.get("content").getAsString(), time(o.get("sent_at")), o.has("edited_at") && !o.get("edited_at").isJsonNull(), false);
    }

    private static Chat chat(JsonObject o) {
        List<UUID> members = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("members")) members.add(uuid(e.getAsString()));
        Map<UUID, String> names = new HashMap<>();
        if (o.has("names") && o.get("names").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("names").entrySet()) names.put(uuid(e.getKey()), e.getValue().getAsString());
        }
        JsonObject last = o.has("last_message") && o.get("last_message").isJsonObject() ? o.getAsJsonObject("last_message") : null;
        return new Chat(o.get("id").getAsInt(), "group".equals(o.get("kind").getAsString()),
                o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : null, List.copyOf(members), Map.copyOf(names),
                last == null ? null : last.get("content").getAsString(), last == null ? null : uuid(last.get("sender").getAsString()),
                last == null ? 0L : time(last.get("sent_at")), o.has("unread") && o.get("unread").getAsBoolean());
    }
}
