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

    private record Popup(Chats.Message message, long at) {
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
                if (mc.screen == null && mc.level != null) dev.golemsclient.menu.PauseMenu.messages(lastChat);
            }
        });
    }

    /** The key's name, as shown on the card ("J"). */
    private static String keyName() {
        if (key == null || key.isUnbound()) return null;
        return key.getTranslatedKeyMessage().getString();
    }

    private static void show(Chats.Message m) {
        Minecraft mc = Minecraft.getInstance();
        // in the menus the friends panel shows it instead
        if (mc.level == null) return;
        if (mc.screen instanceof dev.golemsclient.menu.MenuScreen menu) {
            menu.notice(Chats.nameOf(m.sender(), Chats.chat(m.chat())) + ": " + m.text());
            lastChat = m.chat();
            return;
        }
        synchronized (showing) {
            showing.removeIf(p -> p.message().chat() == m.chat());
            showing.add(new Popup(m, System.currentTimeMillis()));
            while (showing.size() > MAX) showing.remove(0);
        }
        lastChat = m.chat();
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f, 0.25f));
    }

    private static void draw(GuiGraphics g) {
        List<Popup> list;
        long now = System.currentTimeMillis();
        synchronized (showing) {
            showing.removeIf(p -> now - p.at() > SHOW_MS);
            if (showing.isEmpty()) return;
            list = new ArrayList<>(showing);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Overlay.begin(g);
        try {
            float sw = mc.getWindow().getGuiScaledWidth();
            float y = 6f;
            UUID me = mc.getUser().getProfileId();
            for (int i = list.size() - 1; i >= 0; i--) {
                Popup p = list.get(i);
                y += card(p, sw, y, now, me) + 4f;
            }
        } finally {
            Overlay.end();
        }
    }

    /** One card; returns its height. */
    private static float card(Popup p, float sw, float y, long now, UUID me) {
        long age = now - p.at();
        float in = Math.min(1f, age / (float) IN_MS);
        float out = Math.min(1f, Math.max(0f, (SHOW_MS - age) / (float) OUT_MS));
        float a = Math.min(ease(in), out);
        float slide = (1f - ease(in)) * 24f;
        Chats.Message m = p.message();
        Chats.Chat chat = Chats.chat(m.chat());
        String name = Chats.nameOf(m.sender(), chat);
        String where = chat != null && chat.group() ? chat.title(me) : null;
        List<String> lines = wrap(m.text(), W - 34f, 6f, 2);
        String keyName = keyName();
        float h = 24f + lines.size() * 8f + (keyName != null ? 4f : 0f);
        float x0 = sw - W - 6f + slide, x1 = x0 + W;
        Overlay.rect(x0, y, x1, y + h, 4f, alpha(0xF0141417, a));
        Overlay.outline(x0, y, x1, y + h, 4f, alpha(0x1FFFFFFF, a), 0.5f);
        Overlay.rect(x0, y + 4f, x0 + 1.5f, y + h - 4f, 1f, alpha(GolemsClient.ACCENT, a));
        face(m.sender(), name, x0 + 7f, y + 6f, 14f, a);
        float tx = x0 + 27f;
        Overlay.text(Face.SEMIBOLD, fit(Face.SEMIBOLD, name, 6.5f, W - 60f), tx, y + 5.5f, 6.5f, alpha(0xFFEDEDEF, a), Align.LEFT);
        String sub = where == null ? "Message" : "in " + where;
        Overlay.text(Face.REGULAR, fit(Face.REGULAR, sub, 5f, W - 40f), tx, y + 13.5f, 5f, alpha(0xFF6E6E76, a), Align.LEFT);
        float ly = y + 21f;
        for (String line : lines) {
            Overlay.text(Face.REGULAR, line, tx, ly, 6f, alpha(0xFFC8C8CE, a), Align.LEFT);
            ly += 8f;
        }
        if (keyName != null) {
            Overlay.text(Face.MONO, keyName.toUpperCase(java.util.Locale.ROOT) + " TO REPLY", x1 - 6f, y + 5.5f, 4.5f, alpha(0xFF6E6E76, a), Align.RIGHT);
        }
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
