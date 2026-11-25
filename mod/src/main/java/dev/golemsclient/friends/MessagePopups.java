package dev.golemsclient.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.golemsclient.GolemsClient;
import dev.golemsclient.oc.client.lobby.LobbyFriend;
import dev.golemsclient.oc.client.lobby.LobbyFriends;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Messages that arrive while you play: a small card in the top right corner with the sender's face, name and the
 * message, for a few seconds. The "Open messages" key (J unless changed in Controls) opens the chat to reply.
 */
public final class MessagePopups {
    private static final long SHOW_MS = 6500L, IN_MS = 220L, OUT_MS = 350L;
    private static final int MAX = 3;
    private static final float W = 156f;

    /** What J does for a card: open its chat, follow the party leader, or open the party. */
    private enum Act { CHAT, FOLLOW, PARTY }

    private record Popup(UUID face, String faceName, String title, String sub, String body, Act act, int chat, long at, long life) {
    }

    private static final List<Popup> showing = new ArrayList<>();
    private static KeyMapping key;
    private static Integer lastChat;

    private MessagePopups() {
    }

    public static void register() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.golemsclient.messages", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J, KeyMapping.Category.MISC));
        Chats.onMessage(MessagePopups::show);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(GolemsClient.MOD_ID, "message_popups"), (g, tick) -> draw(g));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                if (mc.screen != null || mc.level == null) continue;
                Popup top = newest();
                if (top != null && top.act() == Act.FOLLOW) {
                    dismiss(top);
                    Parties.follow();
                } else if (top != null && top.act() == Act.PARTY) {
                    dismiss(top);
                    dev.golemsclient.menu.PauseMenu.party();
                } else {
                    dev.golemsclient.menu.PauseMenu.messages(lastChat);
                }
            }
        });
    }

    /** The key's name, as shown on the card ("J"). */
    private static String keyName() {
        if (key == null || key.isUnbound()) return null;
        return key.getTranslatedKeyMessage().getString();
    }

    private static Popup newest() {
        long now = System.currentTimeMillis();
        synchronized (showing) {
            for (int i = showing.size() - 1; i >= 0; i--) {
                Popup p = showing.get(i);
                if (now - p.at() <= p.life()) return p;
            }
        }
        return null;
    }

    private static void dismiss(Popup p) {
        synchronized (showing) {
            showing.remove(p);
        }
    }

    private static void add(Popup p) {
        synchronized (showing) {
            if (p.act() == Act.CHAT) showing.removeIf(x -> x.act() == Act.CHAT && x.chat() == p.chat());
            if (p.act() == Act.FOLLOW) showing.removeIf(x -> x.act() == Act.FOLLOW);
            showing.add(p);
            while (showing.size() > MAX) showing.remove(0);
        }
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f, 0.25f));
    }

    private static void show(Chats.Message m) {
        Minecraft mc = Minecraft.getInstance();
        lastChat = m.chat();
        // in the menus the friends panel shows it instead
        if (mc.level == null) return;
        Chats.Chat chat = Chats.chat(m.chat());
        String name = Chats.nameOf(m.sender(), chat);
        if (mc.screen instanceof dev.golemsclient.menu.MenuScreen menu) {
            menu.notice(name + ": " + m.text());
            return;
        }
        UUID me = mc.getUser().getProfileId();
        String sub = chat == null ? "Message" : chat.group() || chat.members().size() > 2 ? "in " + chat.title(me) : "Message";
        add(new Popup(m.sender(), name, name, sub, m.text(), Act.CHAT, m.chat(), System.currentTimeMillis(), SHOW_MS));
    }

    /** The party leader joined a server: J follows them. */
    public static void follow(String leader, String server) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof dev.golemsclient.menu.MenuScreen menu) {
            menu.notice(leader + " joined " + server + ". Open the friends panel's Party tab to follow.");
            return;
        }
        add(new Popup(null, null, "Join " + leader + "?", "Your party leader is on " + server, null, Act.FOLLOW, -1,
                System.currentTimeMillis(), 20_000L));
    }

    /** A party invite (J opens the party, to answer it). */
    public static void partyInvite(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen instanceof dev.golemsclient.menu.MenuScreen) {
            if (mc.screen instanceof dev.golemsclient.menu.MenuScreen menu) menu.notice(text);
            return;
        }
        add(new Popup(null, null, text, "Party invite", null, Act.PARTY, -1, System.currentTimeMillis(), 15_000L));
    }

    private static void draw(GuiGraphics g) {
        List<Popup> list;
        long now = System.currentTimeMillis();
        synchronized (showing) {
            showing.removeIf(p -> now - p.at() > p.life());
            if (showing.isEmpty()) return;
            list = new ArrayList<>(showing);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Overlay.begin(g);
        try {
            float sw = mc.getWindow().getGuiScaledWidth();
            float y = 6f;
            for (int i = list.size() - 1; i >= 0; i--) y += card(list.get(i), sw, y, now) + 4f;
        } finally {
            Overlay.end();
        }
    }

    /** One card; returns its height. */
    private static float card(Popup p, float sw, float y, long now) {
        long age = now - p.at();
        float in = Math.min(1f, age / (float) IN_MS);
        float out = Math.min(1f, Math.max(0f, (p.life() - age) / (float) OUT_MS));
        float a = Math.min(ease(in), out);
        float slide = (1f - ease(in)) * 24f;
        List<String> lines = p.body() == null ? List.of() : wrap(p.body(), W - 34f, 6f, 2);
        String keyName = keyName();
        float h = 24f + lines.size() * 8f + (keyName != null && !lines.isEmpty() ? 4f : 0f);
        float x0 = sw - W - 6f + slide, x1 = x0 + W;
        Overlay.rect(x0, y, x1, y + h, 4f, alpha(0xF0141417, a));
        Overlay.outline(x0, y, x1, y + h, 4f, alpha(0x1FFFFFFF, a), 0.5f);
        Overlay.rect(x0, y + 4f, x0 + 1.5f, y + h - 4f, 1f, alpha(p.act() == Act.CHAT ? GolemsClient.ACCENT : 0xFF3DD68C, a));
        if (p.face() != null) {
            face(p.face(), p.faceName(), x0 + 7f, y + 6f, 14f, a);
        } else {
            Overlay.rect(x0 + 6.5f, y + 5.5f, x0 + 21.5f, y + 20.5f, 2f, alpha(0xFF26262B, a));
            Overlay.icon("users", x0 + 9f, y + 8f, 10f, alpha(0xFFC8C8CE, a));
        }
        float tx = x0 + 27f;
        String hint = keyName == null ? null : keyName.toUpperCase(java.util.Locale.ROOT) + switch (p.act()) {
            case CHAT -> " TO REPLY";
            case FOLLOW -> " TO JOIN";
            case PARTY -> " TO OPEN";
        };
        float hintW = hint == null ? 0f : Overlay.width(Face.MONO, hint, 4.5f) + 8f;
        Overlay.text(Face.SEMIBOLD, fit(Face.SEMIBOLD, p.title(), 6.5f, W - 34f - hintW), tx, y + 5.5f, 6.5f, alpha(0xFFEDEDEF, a), Align.LEFT);
        Overlay.text(Face.REGULAR, fit(Face.REGULAR, p.sub(), 5f, W - 40f), tx, y + 13.5f, 5f, alpha(0xFF6E6E76, a), Align.LEFT);
        float ly = y + 21f;
        for (String line : lines) {
            Overlay.text(Face.REGULAR, line, tx, ly, 6f, alpha(0xFFC8C8CE, a), Align.LEFT);
            ly += 8f;
        }
        if (hint != null) Overlay.text(Face.MONO, hint, x1 - 6f, y + 5.5f, 4.5f, alpha(p.act() == Act.CHAT ? 0xFF6E6E76 : 0xFF3DD68C, a), Align.RIGHT);
        return h;
    }

    private static void face(UUID uuid, String name, float x, float y, float s, float a) {
        Identifier tex = LobbyFriends.INSTANCE.skin(new LobbyFriend(uuid, name == null ? "" : name, null)).body().texturePath();
        int tint = alpha(0xFFFFFFFF, a);
        Overlay.rect(x - 0.5f, y - 0.5f, x + s + 0.5f, y + s + 0.5f, 2f, alpha(0xFF26262B, a));
        Overlay.minecraft(tex, x, y, x + s, y + s, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, tint);
        Overlay.minecraft(tex, x, y, x + s, y + s, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, tint);
    }

    /** Up to `max` lines that fit; the last one ends in "..." when the message is longer. */
    static List<String> wrap(String text, float width, float size, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = text.replace('\n', ' ').split(" ");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String next = line.isEmpty() ? w : line + " " + w;
            if (Overlay.width(Face.REGULAR, next, size) > width && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(w);
                if (out.size() == max) {
                    String last = out.get(max - 1);
                    out.set(max - 1, fit(Face.REGULAR, last + " " + w + "…", size, width));
                    return out;
                }
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) out.add(fit(Face.REGULAR, line.toString(), size, width));
        return out;
    }

    static String fit(Face face, String s, float size, float maxW) {
        if (Overlay.width(face, s, size) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && Overlay.width(face, t + "…", size) > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static int alpha(int argb, float a) {
        return (Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a))) << 24) | (argb & 0xFFFFFF);
    }

    @SuppressWarnings("unused")
    private static Component title() {
        return Component.literal("Golem's Client");
    }
}
