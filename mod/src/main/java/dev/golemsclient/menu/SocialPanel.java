package dev.golemsclient.menu;

import dev.golemsclient.friends.Chats;
import dev.golemsclient.friends.Friends;
import dev.golemsclient.oc.client.lobby.LobbyFriend;
import dev.golemsclient.oc.client.lobby.LobbyFriends;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Social panel (Enter on the main menu, Friends in the pause menu): your Golem's Client friends and what they're
 * doing, your chats (one-on-one and groups) with the messages in them, friend requests, adding someone by name,
 * blocked players, and whether friends see which server you're on.
 */
final class SocialPanel {
    private static final int GAME = 0xFF3DD68C;
    private static final int LAUNCHER = 0xFF7AA2F7;
    private static final int OWNER = 0xFFE8BA5C;
    private static final float ROW = 28f;

    private final MenuScreen m;
    private boolean open;
    private int tab;            // 0 friends, 1 chats, 2 requests, 3 blocked
    private String name = "";
    private boolean typing;     // in the add-a-friend box
    private boolean typingMsg;  // in the message box
    private boolean typingGroup;
    private String draft = "", groupName = "";
    private Integer chat;       // the chat that's open
    private boolean newGroup;
    private final Set<UUID> picked = new LinkedHashSet<>();
    private float chatScroll;   // how far up the messages are scrolled
    private boolean working;
    private float scroll;
    private float listY0, listY1;
    private String confirm;     // an action waiting for a second click
    private long confirmAt;

    SocialPanel(MenuScreen menu) {
        this.m = menu;
    }

    boolean open() {
        return open;
    }

    void show() {
        open = true;
        typing = false;
        scroll = 0f;
        Friends.State s = Friends.state();
        // straight to the requests when someone asked, or the chats when there's a new message
        tab = s != null && !s.incoming().isEmpty() && s.online() == 0 ? 2 : Chats.unread() > 0 ? 1 : 0;
        if (tab == 1) Chats.refresh();
        Friends.watch();
    }

    /** Opens on the chats (and this chat, if given). */
    void showChats(Integer id) {
        show();
        setTab(1);
        if (id != null) openChat(id);
    }

    void close() {
        open = false;
        typing = typingMsg = typingGroup = false;
        if (chat != null) Chats.close();
        chat = null;
        newGroup = false;
    }

    private void openChat(int id) {
        chat = id;
        newGroup = false;
        chatScroll = 0f;
        typingMsg = true;
        Chats.open(id);
    }

    private void back() {
        if (chat != null) Chats.close();
        chat = null;
        newGroup = false;
        typingMsg = typingGroup = false;
        Chats.refresh();
    }

    // ------------------------------------------------------------------ drawing

    void draw() {
        float a = m.anim("socialPanel", open);
        if (a < 0.01f) return;
        if (open) Friends.watch();
        m.hits.clear(); // the panel takes all clicks
        m.rect(0f, 0f, m.w, MenuScreen.H, 0f, MenuScreen.alpha(0x99000000, a));
        float pw = 420f, ph = 330f;
        float x0 = m.w / 2f - pw / 2f, x1 = x0 + pw;
        float y0 = MenuScreen.H / 2f - ph / 2f + (1f - a) * 8f, y1 = y0 + ph;
        m.rect(x0, y0, x1, y1, 8f, MenuScreen.alpha(0xF5141417, a));
        m.outline(x0, y0, x1, y1, 8f, MenuScreen.alpha(MenuScreen.LINE, a));
        if (a < 0.98f) return; // the contents appear once it has opened

        if (chat != null || newGroup) {
            if (chat != null) drawConversation(x0, y0, x1, y1);
            else drawNewGroup(x0, y0, x1, y1);
            m.hits.add(new MenuScreen.Hit(x0, y0, x1, y1, () -> typing = typingGroup = false));
            m.hits.add(new MenuScreen.Hit(0f, 0f, m.w, MenuScreen.H, this::close));
            return;
        }

        Friends.State s = Friends.state();
        // header
        m.text(Face.BOLD, "Friends", x0 + 16f, y0 + 14f, 11f, MenuScreen.TEXT, Align.LEFT);
        String sub = s == null ? "Golem's Client friends" : s.online() + " online · " + s.friends().size()
                + (s.friends().size() == 1 ? " friend" : " friends");
        m.text(Face.REGULAR, sub, x0 + 16f, y0 + 30f, 6.5f, MenuScreen.TEXT_2, Align.LEFT);
        iconButton("close", x1 - 26f, y0 + 12f, 14f, "Close", this::close, false, false);

        // tabs
        float tx = x0 + 16f, ty = y0 + 46f;
        int requests = s == null ? 0 : s.incoming().size();
        tx = chip(tx, ty, "Friends", tab == 0, 0, () -> setTab(0)) + 4f;
        tx = chip(tx, ty, "Chats", tab == 1, Chats.unread(), () -> setTab(1)) + 4f;
        tx = chip(tx, ty, "Requests", tab == 2, requests, () -> setTab(2)) + 4f;
        chip(tx, ty, "Blocked", tab == 3, 0, () -> setTab(3));

        float listTop = ty + 26f;
        if (tab == 0 || tab == 2) {
            addBar(x0 + 16f, listTop, x1 - 16f);
            listTop += 28f;
        } else if (tab == 1) {
            chatsBar(x0 + 16f, listTop, x1 - 16f, s);
            listTop += 28f;
        }
        listY0 = listTop;
        listY1 = y1 - 38f;
        m.rect(x0 + 16f, listY0 - 4f, x1 - 16f, listY0 - 3.5f, 0f, MenuScreen.LINE);

        if (s == null) {
            String p = Friends.problem();
            empty(x0, x1, p == null ? "Loading your friends..." : "Friends aren't working right now", p == null ? "" : p);
        } else if (tab == 0) {
            drawFriends(s, x0 + 16f, x1 - 16f);
        } else if (tab == 1) {
            drawChats(x0 + 16f, x1 - 16f);
        } else if (tab == 2) {
            drawRequests(s, x0 + 16f, x1 - 16f);
        } else {
            drawBlocked(s, x0 + 16f, x1 - 16f);
        }

        // footer: what friends see
        m.rect(x0 + 16f, y1 - 32f, x1 - 16f, y1 - 31.5f, 0f, MenuScreen.LINE);
        boolean share = s == null || s.shareServer();
        toggle(x0 + 16f, y1 - 23f, share, s != null, () -> {
            working = true;
            Friends.shareServer(!share, this::done);
        });
        m.text(Face.REGULAR, "Show friends which server I'm on", x0 + 44f, y1 - 21f, 6.5f, MenuScreen.TEXT_2, Align.LEFT);
        String problem = Friends.problem();
        if (s != null && problem != null) {
            m.text(Face.REGULAR, m.fit(Face.REGULAR, problem, 6f, 170f), x1 - 16f, y1 - 21f, 6f, 0xFFE5484D, Align.RIGHT);
        }

        // clicks: the panel itself swallows them, outside closes (typing stops first)
        m.hits.add(new MenuScreen.Hit(x0, y0, x1, y1, () -> typing = false));
        m.hits.add(new MenuScreen.Hit(0f, 0f, m.w, MenuScreen.H, this::close));
    }

    private void setTab(int t) {
        tab = t;
        scroll = 0f;
        confirm = null;
        if (t == 1) Chats.refresh();
    }

    private void drawFriends(Friends.State s, float x0, float x1) {
        List<Friends.Friend> list = s.friends();
        if (list.isEmpty()) {
            empty(x0 - 16f, x1 + 16f, "No friends yet", "Add someone by their Minecraft name. They need Golem's Client too.");
            return;
        }
        float y = beginList(list.size() * ROW);
        for (Friends.Friend f : list) {
            if (visible(y)) friendRow(f, x0, y, x1);
            y += ROW;
        }
        endList(list.size() * ROW, x1);
    }

    private void friendRow(Friends.Friend f, float x0, float y, float x1) {
        boolean over = hovering(x0, y, x1, y + ROW);
        float hv = m.anim("friend:" + f.uuid(), over);
        if (hv > 0.01f) m.rect(x0, y + 1f, x1, y + ROW - 1f, 5f, MenuScreen.alpha(0x10FFFFFF, hv));
        face(f.uuid(), f.name(), x0 + 6f, y + 5f, 18f, f.online() ? 1f : 0.45f);
        String name = f.name() == null ? "Unknown player" : f.name();
        float nx = x0 + 30f;
        m.text(Face.SEMIBOLD, name, nx, y + 5f, 7.5f, f.owner() ? OWNER : MenuScreen.TEXT, Align.LEFT);
        if (f.owner()) {
            float ox = nx + m.textW(Face.SEMIBOLD, name, 7.5f) + 5f;
            m.rect(ox, y + 5.5f, ox + 26f, y + 13.5f, 2f, 0x26E8BA5C);
            m.text(Face.MONO, "OWNER", ox + 13f, y + 7f, 4.5f, OWNER, Align.CENTER);
        }
        int dot = f.inGame() ? GAME : f.online() ? LAUNCHER : MenuScreen.TEXT_3;
        m.rect(nx, y + 18.5f, nx + 4f, y + 22.5f, 2f, dot);
        String where = f.online() ? f.where() : f.lastSeen() > 0 ? "Last seen " + MenuScreen.ago(f.lastSeen() * 1000L) : "Offline";
        if (f.inGame() && f.mc() != null) where += " · " + f.mc();
        m.text(Face.REGULAR, m.fit(Face.REGULAR, where, 6f, x1 - nx - 60f), nx + 7f, y + 17f, 6f,
                f.online() ? MenuScreen.TEXT_2 : MenuScreen.TEXT_3, Align.LEFT);
        if (over || confirming("remove:" + f.uuid()) || confirming("block:" + f.uuid())) {
            float bx = x1 - 22f;
            iconButton("chat", bx - 36f, y + 7f, 14f, "Message " + name, () -> {
                working = true;
                Chats.dm(f.uuid(), c -> {
                    working = false;
                    openChat(c.id());
                }, this::done);
            }, false);
            iconButton("block", bx, y + 7f, 14f, confirming("block:" + f.uuid()) ? "Click again to block " + name : "Block",
                    () -> twice("block:" + f.uuid(), () -> Friends.block(f.uuid(), f.name(), this::done)), confirming("block:" + f.uuid()));
            iconButton("trash", bx - 18f, y + 7f, 14f, confirming("remove:" + f.uuid()) ? "Click again to remove " + name : "Remove friend",
                    () -> twice("remove:" + f.uuid(), () -> Friends.remove(f, this::done)), confirming("remove:" + f.uuid()));
        }
    }

    private void drawRequests(Friends.State s, float x0, float x1) {
        List<Friends.Request> in = s.incoming(), out = s.outgoing();
        if (in.isEmpty() && out.isEmpty()) {
            empty(x0 - 16f, x1 + 16f, "No friend requests", "Requests you get and send show up here.");
            return;
        }
        float content = (in.isEmpty() ? 0f : 14f + in.size() * ROW) + (out.isEmpty() ? 0f : 14f + out.size() * ROW);
        float y = beginList(content);
        if (!in.isEmpty()) {
            if (visible(y)) m.text(Face.MONO, "RECEIVED", x0 + 2f, y + 4f, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
            y += 14f;
            for (Friends.Request r : in) {
                if (visible(y)) requestRow(r, true, x0, y, x1);
                y += ROW;
            }
        }
        if (!out.isEmpty()) {
            if (visible(y)) m.text(Face.MONO, "SENT", x0 + 2f, y + 4f, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
            y += 14f;
            for (Friends.Request r : out) {
                if (visible(y)) requestRow(r, false, x0, y, x1);
                y += ROW;
            }
        }
        endList(content, x1);
    }

    private void requestRow(Friends.Request r, boolean incoming, float x0, float y, float x1) {
        String name = r.name() == null ? "Unknown player" : r.name();
        face(r.uuid(), r.name(), x0 + 6f, y + 5f, 18f, 1f);
        m.text(Face.SEMIBOLD, name, x0 + 30f, y + 5f, 7.5f, MenuScreen.TEXT, Align.LEFT);
        String when = MenuScreen.ago(r.at() * 1000L);
        m.text(Face.REGULAR, (incoming ? "Wants to be friends · " : "Request sent · ") + when, x0 + 30f, y + 17f, 6f, MenuScreen.TEXT_2, Align.LEFT);
        if (incoming) {
            float bx = textButton(x1 - 4f, y + 6f, "Accept", true, () -> {
                working = true;
                Friends.accept(r, this::done);
            });
            textButton(bx - 4f, y + 6f, "Decline", false, () -> {
                working = true;
                Friends.decline(r, this::done);
            });
        } else {
            textButton(x1 - 4f, y + 6f, "Cancel", false, () -> {
                working = true;
                Friends.cancel(r, this::done);
            });
        }
    }

    private void drawBlocked(Friends.State s, float x0, float x1) {
        List<Friends.Blocked> list = s.blocked();
        if (list.isEmpty()) {
            empty(x0 - 16f, x1 + 16f, "Nobody is blocked", "Blocked players can't send you friend requests.");
            return;
        }
        float y = beginList(list.size() * ROW);
        for (Friends.Blocked b : list) {
            if (visible(y)) {
                String name = b.name() == null ? "Unknown player" : b.name();
                face(b.uuid(), b.name(), x0 + 6f, y + 5f, 18f, 0.6f);
                m.text(Face.SEMIBOLD, name, x0 + 30f, y + 5f, 7.5f, MenuScreen.TEXT, Align.LEFT);
                m.text(Face.REGULAR, "Blocked " + MenuScreen.ago(b.since() * 1000L), x0 + 30f, y + 17f, 6f, MenuScreen.TEXT_3, Align.LEFT);
                textButton(x1 - 4f, y + 6f, "Unblock", false, () -> {
                    working = true;
                    Friends.unblock(b, this::done);
                });
            }
            y += ROW;
        }
        endList(list.size() * ROW, x1);
    }


    // ------------------------------------------------------------------ chats

    private void chatsBar(float x0, float y, float x1, Friends.State s) {
        m.text(Face.REGULAR, "Message a friend from the Friends tab, or start a group.", x0 + 2f, y + 9f - 6.5f * 0.6f, 6.5f,
                MenuScreen.TEXT_3, Align.LEFT);
        boolean can = s != null && !s.friends().isEmpty();
        float bw = m.textW(Face.SEMIBOLD, "New group", 7f) + 30f, bx0 = x1 - bw;
        boolean over = can && m.inside(bx0, y, x1, y + 18f);
        float hv = m.anim("socialNewGroup", over);
        m.rect(bx0, y, x1, y + 18f, 4f, can ? MenuScreen.mix(0x14FFFFFF, 0x2EFFFFFF, hv) : 0x0AFFFFFF);
        m.outline(bx0, y, x1, y + 18f, 4f, MenuScreen.LINE);
        Overlay.icon("plus", (bx0 + 8f) * m.u, (y + 5f) * m.u, 8f * m.u, m.c(can ? MenuScreen.TEXT : MenuScreen.TEXT_3));
        m.text(Face.SEMIBOLD, "New group", bx0 + 20f, y + 9f - 7f * 0.6f, 7f, can ? MenuScreen.TEXT : MenuScreen.TEXT_3, Align.LEFT);
        if (over) {
            m.pointer = true;
            m.hits.add(0, new MenuScreen.Hit(bx0, y, x1, y + 18f, () -> {
                newGroup = true;
                groupName = "";
                picked.clear();
                typingGroup = true;
                scroll = 0f;
            }));
        }
    }

    private void drawChats(float x0, float x1) {
        List<Chats.Chat> list = Chats.chats();
        if (list == null) {
            empty(x0 - 16f, x1 + 16f, "Loading your chats...", "");
            return;
        }
        if (list.isEmpty()) {
            empty(x0 - 16f, x1 + 16f, "No chats yet", "Message a friend from the Friends tab, or start a group.");
            return;
        }
        UUID me = net.minecraft.client.Minecraft.getInstance().getUser().getProfileId();
        float y = beginList(list.size() * ROW);
        for (Chats.Chat c : list) {
            if (visible(y)) chatRow(c, me, x0, y, x1);
            y += ROW;
        }
        endList(list.size() * ROW, x1);
    }

    private void chatRow(Chats.Chat c, UUID me, float x0, float y, float x1) {
        boolean over = hovering(x0, y, x1, y + ROW);
        float hv = m.anim("chat:" + c.id(), over);
        if (hv > 0.01f) m.rect(x0, y + 1f, x1, y + ROW - 1f, 5f, MenuScreen.alpha(0x10FFFFFF, hv));
        if (c.group()) {
            m.rect(x0 + 6f, y + 5f, x0 + 24f, y + 23f, 4f, 0xFF26262B);
            Overlay.icon("users", (x0 + 10f) * m.u, (y + 9f) * m.u, 10f * m.u, m.c(MenuScreen.TEXT_2));
        } else {
            UUID other = c.other(me);
            face(other, Chats.nameOf(other, c), x0 + 6f, y + 5f, 18f, 1f);
        }
        float nx = x0 + 30f;
        String when = c.lastAt() > 0 ? MenuScreen.ago(c.lastAt() * 1000L) : "";
        float ww = m.textW(Face.MONO, when, 5f);
        m.text(c.unread() ? Face.BOLD : Face.SEMIBOLD, m.fit(Face.SEMIBOLD, c.title(me), 7.5f, x1 - nx - ww - 20f), nx, y + 5f, 7.5f,
                MenuScreen.TEXT, Align.LEFT);
        if (!when.isEmpty()) m.text(Face.MONO, when, x1 - 6f, y + 6.5f, 5f, MenuScreen.TEXT_3, Align.RIGHT);
        String preview;
        if (c.lastText() == null) preview = c.group() ? c.members().size() + " people" : "No messages yet";
        else if (me.equals(c.lastSender())) preview = "You: " + c.lastText();
        else preview = c.group() ? Chats.nameOf(c.lastSender(), c) + ": " + c.lastText() : c.lastText();
        m.text(Face.REGULAR, m.fit(Face.REGULAR, preview.replace('\n', ' '), 6f, x1 - nx - 24f), nx, y + 17f, 6f,
                c.unread() ? MenuScreen.TEXT : MenuScreen.TEXT_2, Align.LEFT);
        if (c.unread()) m.rect(x1 - 10f, y + 17.5f, x1 - 5f, y + 22.5f, 2.5f, MenuScreen.ACCENT);
        if (over) {
            m.pointer = true;
            m.hits.add(0, new MenuScreen.Hit(x0, Math.max(y, listY0), x1, Math.min(y + ROW, listY1), () -> openChat(c.id())));
        }
    }

    private void drawConversation(float x0, float y0, float x1, float y1) {
        UUID me = net.minecraft.client.Minecraft.getInstance().getUser().getProfileId();
        Chats.Chat c = Chats.chat(chat);
        // header
        iconButton("arrow_left", x0 + 14f, y0 + 14f, 16f, "Back", this::back, false, false);
        String title = c == null ? "Chat" : c.title(me);
        float hx = x0 + 38f;
        if (c != null && !c.group()) {
            UUID other = c.other(me);
            face(other, Chats.nameOf(other, c), hx, y0 + 13f, 18f, 1f);
            hx += 24f;
        }
        m.text(Face.BOLD, m.fit(Face.BOLD, title, 9.5f, x1 - hx - 60f), hx, y0 + 12f, 9.5f, MenuScreen.TEXT, Align.LEFT);
        m.text(Face.REGULAR, m.fit(Face.REGULAR, subtitle(c, me), 6f, x1 - hx - 60f), hx, y0 + 25f, 6f, MenuScreen.TEXT_2, Align.LEFT);
        iconButton("close", x1 - 26f, y0 + 12f, 14f, "Close", this::close, false, false);
        if (c != null && c.group()) {
            boolean armed = confirming("leave:" + c.id());
            iconButton("quit", x1 - 44f, y0 + 12f, 14f, armed ? "Click again to leave the group" : "Leave group",
                    () -> twice("leave:" + c.id(), () -> Chats.leave(c.id(), () -> {
                        working = false;
                        back();
                        m.showToast("You left the group");
                    }, this::done)), armed, false);
        }
        float cy0 = y0 + 42f, cy1 = y1 - 40f;
        m.rect(x0 + 16f, cy0 - 1f, x1 - 16f, cy0 - 0.5f, 0f, MenuScreen.LINE);
        listY0 = cy0;
        listY1 = cy1;

        List<Chats.Message> msgs = chat == null ? null : Chats.thread(chat);
        if (msgs == null) {
            empty(x0, x1, "Loading messages...", "");
        } else if (msgs.isEmpty()) {
            empty(x0, x1, "No messages yet", "Say hi! Messages are kept for 30 days.");
        } else {
            drawMessages(msgs, c, me, x0 + 16f, x1 - 16f, cy0, cy1);
        }

        // the message box
        float by0 = y1 - 32f, by1 = y1 - 14f, sx0 = x1 - 16f - 26f;
        float focus = m.anim("socialMsg", typingMsg);
        m.rect(x0 + 16f, by0, sx0 - 6f, by1, 4f, 0x0FFFFFFF);
        m.outline(x0 + 16f, by0, sx0 - 6f, by1, 4f, MenuScreen.mix(MenuScreen.LINE, MenuScreen.ACCENT, focus));
        float tx = x0 + 24f, maxW = sx0 - 6f - tx - 6f;
        boolean hint = draft.isEmpty();
        String shown = hint ? "Message " + title : draft;
        if (!hint && m.textW(Face.REGULAR, shown, 6.5f) > maxW) {
            // the end of a long message stays in view
            while (shown.length() > 1 && m.textW(Face.REGULAR, "…" + shown, 6.5f) > maxW) shown = shown.substring(1);
            shown = "…" + shown;
        } else if (hint) {
            shown = m.fit(Face.REGULAR, shown, 6.5f, maxW);
        }
        m.text(Face.REGULAR, shown, tx, by0 + 9f - 6.5f * 0.6f, 6.5f, hint ? MenuScreen.TEXT_3 : MenuScreen.TEXT, Align.LEFT);
        if (typingMsg && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float cx = tx + (hint ? 0f : m.textW(Face.REGULAR, shown, 6.5f)) + 1f;
            m.rect(cx, by0 + 4f, cx + 0.6f, by1 - 4f, 0f, MenuScreen.TEXT);
        }
        m.hits.add(0, new MenuScreen.Hit(x0 + 16f, by0, sx0 - 6f, by1, () -> typingMsg = true));
        if (m.inside(x0 + 16f, by0, sx0 - 6f, by1)) m.pointer = true;
        boolean can = !draft.isBlank();
        boolean overS = can && m.inside(sx0, by0, x1 - 16f, by1);
        float hs = m.anim("socialSend", overS);
        m.rect(sx0, by0, x1 - 16f, by1, 4f, can ? MenuScreen.mix(MenuScreen.ACCENT, 0xFFEE6A6E, hs) : 0x14FFFFFF);
        Overlay.icon("send", (sx0 + 8f) * m.u, (by0 + 4f) * m.u, 10f * m.u, m.c(can ? 0xFFFFFFFF : MenuScreen.TEXT_3));
        if (overS) {
            m.pointer = true;
            m.tooltip = "Send (Enter)";
            m.hits.add(0, new MenuScreen.Hit(sx0, by0, x1 - 16f, by1, this::sendDraft));
        }
    }

    private String subtitle(Chats.Chat c, UUID me) {
        if (c == null) return "";
        if (c.group()) {
            List<String> names = new ArrayList<>();
            for (UUID u : c.members()) names.add(u.equals(me) ? "You" : Chats.nameOf(u, c));
            return c.members().size() + " people · " + String.join(", ", names);
        }
        UUID other = c.other(me);
        Friends.State s = Friends.state();
        if (s != null) for (Friends.Friend f : s.friends()) if (f.uuid().equals(other)) return f.online() ? f.where() : "Offline";
        return "";
    }

    private record Laid(Chats.Message msg, List<String> lines, boolean mine, boolean first, float h, float w) {
    }

    private void drawMessages(List<Chats.Message> msgs, Chats.Chat c, UUID me, float x0, float x1, float cy0, float cy1) {
        float maxW = (x1 - x0) * 0.68f;
        boolean group = c != null && c.group();
        List<Laid> laid = new ArrayList<>();
        float content = 6f;
        UUID prev = null;
        for (Chats.Message msg : msgs) {
            boolean mine = msg.sender().equals(me);
            boolean first = !msg.sender().equals(prev);
            List<String> lines = wrapText(msg.text(), maxW - 14f, 6.5f);
            float w = 0f;
            for (String l : lines) w = Math.max(w, m.textW(Face.REGULAR, l, 6.5f));
            float h = lines.size() * 9f + 8f + (first && group && !mine ? 9f : 0f);
            laid.add(new Laid(msg, lines, mine, first, h, w + 14f));
            content += h + (first && prev != null ? 7f : 3f);
            prev = msg.sender();
        }
        float view = cy1 - cy0;
        chatScroll = MenuScreen.clamp(chatScroll, 0f, Math.max(0f, content - view));
        // the newest message sits at the bottom
        float y = cy1 - content + chatScroll;
        Overlay.clip(0f, cy0 * m.u, m.w * m.u, cy1 * m.u);
        boolean firstRow = true;
        for (Laid l : laid) {
            y += firstRow ? 4f : (l.first() ? 7f : 3f);
            firstRow = false;
            if (y + l.h() >= cy0 && y <= cy1) bubble(l, c, x0, x1, y, group);
            y += l.h();
        }
        Overlay.unclip();
        if (content > view) {
            float bar = view * view / content, at = (view - bar) * (1f - chatScroll / (content - view));
            m.rect(x1 + 6f, cy0 + at, x1 + 7.5f, cy0 + at + bar, 0.75f, 0x40FFFFFF);
        }
    }

    private void bubble(Laid l, Chats.Chat c, float x0, float x1, float y, boolean group) {
        Chats.Message msg = l.msg();
        float by = y;
        float left = l.mine() ? x1 - l.w() : x0 + 20f;
        if (!l.mine()) {
            if (l.first()) face(msg.sender(), Chats.nameOf(msg.sender(), c), x0, y + (group ? 9f : 0f), 14f, 1f);
            if (l.first() && group) {
                m.text(Face.SEMIBOLD, Chats.nameOf(msg.sender(), c), left + 2f, y, 5.5f, MenuScreen.TEXT_2, Align.LEFT);
                by += 9f;
            }
        }
        float bh = l.lines().size() * 9f + 8f;
        float a = msg.pending() ? 0.55f : 1f;
        int fill = l.mine() ? 0x40E5484D : 0x14FFFFFF;
        m.rect(left, by, left + l.w(), by + bh, 6f, MenuScreen.alpha(fill, a));
        float ty = by + 4f;
        for (String line : l.lines()) {
            m.text(Face.REGULAR, line, left + 7f, ty, 6.5f, MenuScreen.alpha(MenuScreen.TEXT, a), Align.LEFT);
            ty += 9f;
        }
        if (m.inside(left, Math.max(by, listY0), left + l.w(), Math.min(by + bh, listY1))) {
            m.tooltip = msg.pending() ? "Sending..." : MenuScreen.ago(msg.at() * 1000L) + (msg.edited() ? " · edited" : "");
        }
    }

    /** Lines that fit the width (long words are broken). */
    private List<String> wrapText(String text, float width, float size) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                while (m.textW(Face.REGULAR, word, size) > width && word.length() > 1) {
                    // a word too long for a line on its own
                    int cut = word.length() - 1;
                    while (cut > 1 && m.textW(Face.REGULAR, word.substring(0, cut), size) > width) cut--;
                    if (!line.isEmpty()) {
                        out.add(line.toString());
                        line = new StringBuilder();
                    }
                    out.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                String next = line.isEmpty() ? word : line + " " + word;
                if (m.textW(Face.REGULAR, next, size) > width && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private void sendDraft() {
        if (chat == null || draft.isBlank()) return;
        String text = draft;
        draft = "";
        chatScroll = 0f;
        Chats.send(chat, text, error -> {
            draft = text;
            m.showToast(error);
        });
    }

    // ------------------------------------------------------------------ a new group

    private void drawNewGroup(float x0, float y0, float x1, float y1) {
        iconButton("arrow_left", x0 + 14f, y0 + 14f, 16f, "Back", this::back, false, false);
        m.text(Face.BOLD, "New group", x0 + 38f, y0 + 12f, 9.5f, MenuScreen.TEXT, Align.LEFT);
        m.text(Face.REGULAR, "Pick friends to chat with together (up to 19).", x0 + 38f, y0 + 25f, 6f, MenuScreen.TEXT_2, Align.LEFT);
        iconButton("close", x1 - 26f, y0 + 12f, 14f, "Close", this::close, false, false);

        // the name
        float fy = y0 + 44f, fy1 = fy + 18f;
        float focus = m.anim("socialGroupName", typingGroup);
        m.rect(x0 + 16f, fy, x1 - 16f, fy1, 4f, 0x0FFFFFFF);
        m.outline(x0 + 16f, fy, x1 - 16f, fy1, 4f, MenuScreen.mix(MenuScreen.LINE, MenuScreen.ACCENT, focus));
        boolean hint = groupName.isEmpty();
        m.text(Face.REGULAR, hint ? "Group name (optional)" : groupName, x0 + 24f, fy + 9f - 6.5f * 0.6f, 6.5f,
                hint ? MenuScreen.TEXT_3 : MenuScreen.TEXT, Align.LEFT);
        if (typingGroup && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float cx = x0 + 24f + (hint ? 0f : m.textW(Face.REGULAR, groupName, 6.5f)) + 1f;
            m.rect(cx, fy + 4f, cx + 0.6f, fy1 - 4f, 0f, MenuScreen.TEXT);
        }
        m.hits.add(0, new MenuScreen.Hit(x0 + 16f, fy, x1 - 16f, fy1, () -> typingGroup = true));

        // friends to pick
        listY0 = fy1 + 8f;
        listY1 = y1 - 38f;
        Friends.State s = Friends.state();
        List<Friends.Friend> friends = s == null ? List.of() : s.friends();
        float y = beginList(friends.size() * ROW);
        for (Friends.Friend f : friends) {
            if (visible(y)) {
                boolean on = picked.contains(f.uuid());
                boolean over = hovering(x0 + 16f, y, x1 - 16f, y + ROW);
                float hv = m.anim("pick:" + f.uuid(), over);
                if (hv > 0.01f) m.rect(x0 + 16f, y + 1f, x1 - 16f, y + ROW - 1f, 5f, MenuScreen.alpha(0x10FFFFFF, hv));
                face(f.uuid(), f.name(), x0 + 22f, y + 5f, 18f, 1f);
                m.text(Face.SEMIBOLD, f.name() == null ? "Unknown player" : f.name(), x0 + 46f, y + 10f, 7.5f, MenuScreen.TEXT, Align.LEFT);
                float bx = x1 - 32f, byy = y + 8f;
                m.rect(bx, byy, bx + 12f, byy + 12f, 3f, on ? MenuScreen.ACCENT : 0x14FFFFFF);
                if (!on) m.outline(bx, byy, bx + 12f, byy + 12f, 3f, MenuScreen.LINE);
                if (on) Overlay.icon("check", (bx + 2f) * m.u, (byy + 2f) * m.u, 8f * m.u, m.c(0xFFFFFFFF));
                if (over) {
                    m.pointer = true;
                    m.hits.add(0, new MenuScreen.Hit(x0 + 16f, Math.max(y, listY0), x1 - 16f, Math.min(y + ROW, listY1), () -> {
                        if (!picked.remove(f.uuid()) && picked.size() < 19) picked.add(f.uuid());
                    }));
                }
            }
            y += ROW;
        }
        endList(friends.size() * ROW, x1 - 16f);

        // make it
        String label = picked.isEmpty() ? "Pick friends" : working ? "Making..." : "Create group (" + (picked.size() + 1) + ")";
        boolean can = !picked.isEmpty() && !working;
        float bw = m.textW(Face.SEMIBOLD, label, 7f) + 24f, bx1 = x1 - 16f, bx0 = bx1 - bw, by0 = y1 - 30f, by1 = y1 - 12f;
        boolean over = can && m.inside(bx0, by0, bx1, by1);
        float hv = m.anim("socialCreate", over);
        m.rect(bx0, by0, bx1, by1, 4f, can ? MenuScreen.mix(MenuScreen.ACCENT, 0xFFEE6A6E, hv) : 0x14FFFFFF);
        m.text(Face.SEMIBOLD, label, (bx0 + bx1) / 2f, by0 + 9f - 7f * 0.6f, 7f, can ? 0xFFFFFFFF : MenuScreen.TEXT_3, Align.CENTER);
        if (over) {
            m.pointer = true;
            m.hits.add(0, new MenuScreen.Hit(bx0, by0, bx1, by1, this::createGroup));
        }
    }

    private void createGroup() {
        if (picked.isEmpty() || working) return;
        working = true;
        Chats.createGroup(groupName.strip(), new ArrayList<>(picked), c -> {
            working = false;
            newGroup = false;
            openChat(c.id());
        }, this::done);
    }

    // ------------------------------------------------------------------ the add bar

    private void addBar(float x0, float y, float x1) {
        float bw = 58f, fx1 = x1 - bw - 6f, y1 = y + 18f;
        float focus = m.anim("socialField", typing);
        boolean over = m.inside(x0, y, fx1, y1);
        m.rect(x0, y, fx1, y1, 4f, 0x0FFFFFFF);
        m.outline(x0, y, fx1, y1, 4f, MenuScreen.mix(over ? 0x33FFFFFF : MenuScreen.LINE, MenuScreen.ACCENT, focus));
        Overlay.icon("user_plus", (x0 + 6f) * m.u, (y + 4.5f) * m.u, 9f * m.u, m.c(MenuScreen.TEXT_3));
        float tx = x0 + 20f;
        boolean hint = name.isEmpty() && !typing;
        m.text(Face.REGULAR, hint ? "Add a friend by their Minecraft name" : name, tx, y + 9f - 6.5f * 0.6f, 6.5f,
                hint ? MenuScreen.TEXT_3 : MenuScreen.TEXT, Align.LEFT);
        if (typing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float cx = tx + m.textW(Face.REGULAR, name, 6.5f) + 1f;
            m.rect(cx, y + 4f, cx + 0.6f, y1 - 4f, 0f, MenuScreen.TEXT);
        }
        if (over) m.pointer = true;
        m.hits.add(0, new MenuScreen.Hit(x0, y, fx1, y1, () -> typing = true));
        boolean can = !name.isBlank() && !working;
        boolean overB = m.inside(fx1 + 6f, y, x1, y1) && can;
        float hv = m.anim("socialAdd", overB);
        m.rect(fx1 + 6f, y, x1, y1, 4f, can ? MenuScreen.mix(MenuScreen.ACCENT, 0xFFEE6A6E, hv) : 0x14FFFFFF);
        m.text(Face.SEMIBOLD, working ? "..." : "Add", (fx1 + 6f + x1) / 2f, y + 9f - 7f * 0.6f, 7f,
                can ? 0xFFFFFFFF : MenuScreen.TEXT_3, Align.CENTER);
        if (can) {
            if (overB) m.pointer = true;
            m.hits.add(0, new MenuScreen.Hit(fx1 + 6f, y, x1, y1, this::submit));
        }
    }

    private void submit() {
        if (name.isBlank() || working) return;
        working = true;
        typing = false;
        String n = name;
        Friends.add(n, message -> {
            done(message);
            if (message.startsWith("Friend request sent") || message.startsWith("You and")) name = "";
        });
    }

    private void done(String message) {
        working = false;
        confirm = null;
        m.showToast(message);
    }

    // ------------------------------------------------------------------ input

    /** Keys while the panel is open. True when used. */
    boolean key(int key) {
        if (!open) return false;
        if (chat != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) back();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) sendDraft();
            else if (key == GLFW.GLFW_KEY_BACKSPACE && !draft.isEmpty()) draft = draft.substring(0, draft.offsetByCodePoints(draft.length(), -1));
            return true;
        }
        if (newGroup) {
            if (key == GLFW.GLFW_KEY_ESCAPE) back();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) createGroup();
            else if (key == GLFW.GLFW_KEY_BACKSPACE && !groupName.isEmpty()) groupName = groupName.substring(0, groupName.length() - 1);
            return true;
        }
        if (typing) {
            if (key == GLFW.GLFW_KEY_ESCAPE) typing = false;
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) submit();
            else if (key == GLFW.GLFW_KEY_BACKSPACE && !name.isEmpty()) name = name.substring(0, name.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            close();
            return true;
        }
        return true;
    }

    boolean type(int codepoint) {
        if (!open) return false;
        if (chat != null) {
            typingMsg = true;
            if (!Character.isISOControl(codepoint) && draft.length() < 512) draft += new String(Character.toChars(codepoint));
            return true;
        }
        if (newGroup) {
            typingGroup = true;
            if (!Character.isISOControl(codepoint) && groupName.length() < 32) groupName += new String(Character.toChars(codepoint));
            return true;
        }
        if (!typing) {
            // typing a name starts the add box
            if (tab == 1 || tab == 3 || !Character.isLetterOrDigit(codepoint) && codepoint != '_') return true;
            typing = true;
        }
        if (name.length() < 16 && (Character.isLetterOrDigit(codepoint) || codepoint == '_') && codepoint < 128) {
            name += (char) codepoint;
        }
        return true;
    }

    boolean scroll(double mouseY, double amount) {
        if (!open) return false;
        float y = (float) mouseY / m.u;
        if (y >= listY0 && y <= listY1) {
            if (chat != null) chatScroll += (float) amount * 30f;
            else scroll -= (float) amount * 30f;
        }
        return true;
    }

    // ------------------------------------------------------------------ pieces

    private float beginList(float content) {
        float view = listY1 - listY0;
        scroll = MenuScreen.clamp(scroll, 0f, Math.max(0f, content - view));
        Overlay.clip(0f, listY0 * m.u, m.w * m.u, listY1 * m.u);
        return listY0 - scroll;
    }

    private void endList(float content, float x1) {
        Overlay.unclip();
        float view = listY1 - listY0;
        if (content > view) {
            float bar = view * view / content, at = (view - bar) * scroll / (content - view);
            m.rect(x1 + 6f, listY0 + at, x1 + 7.5f, listY0 + at + bar, 0.75f, 0x40FFFFFF);
        }
    }

    private boolean visible(float y) {
        return y + ROW >= listY0 && y <= listY1;
    }

    private boolean hovering(float x0, float y0, float x1, float y1) {
        return m.inside(x0, Math.max(y0, listY0), x1, Math.min(y1, listY1));
    }

    private void empty(float x0, float x1, String title, String sub) {
        float cx = (x0 + x1) / 2f, cy = (listY0 + listY1) / 2f - 14f;
        Overlay.icon("users", (cx - 9f) * m.u, (cy - 22f) * m.u, 18f * m.u, m.c(MenuScreen.TEXT_3));
        m.text(Face.SEMIBOLD, title, cx, cy + 2f, 9f, MenuScreen.TEXT, Align.CENTER);
        if (!sub.isEmpty()) m.text(Face.REGULAR, m.fit(Face.REGULAR, sub, 6.5f, x1 - x0 - 40f), cx, cy + 16f, 6.5f, MenuScreen.TEXT_2, Align.CENTER);
    }

    /** A friend's face, from their skin (the hat layer on top). */
    private void face(UUID uuid, String name, float x, float y, float s, float a) {
        Identifier tex = LobbyFriends.INSTANCE.skin(new LobbyFriend(uuid, name == null ? "" : name, null)).body().texturePath();
        int tint = m.c(MenuScreen.alpha(0xFFFFFFFF, a));
        m.rect(x - 0.5f, y - 0.5f, x + s + 0.5f, y + s + 0.5f, 2f, 0xFF26262B);
        Overlay.minecraft(tex, x * m.u, y * m.u, (x + s) * m.u, (y + s) * m.u, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, tint);
        Overlay.minecraft(tex, x * m.u, y * m.u, (x + s) * m.u, (y + s) * m.u, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, tint);
    }

    private float chip(float x, float y, String label, boolean on, int count, Runnable action) {
        float cw = m.textW(Face.MEDIUM, label, 7f) + 18f + (count > 0 ? 14f : 0f);
        boolean over = m.inside(x, y, x + cw, y + 16f);
        float hv = m.anim("socialTab:" + label, over);
        m.rect(x, y, x + cw, y + 16f, 4f, on ? MenuScreen.ACCENT : MenuScreen.mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        if (!on) m.outline(x, y, x + cw, y + 16f, 4f, MenuScreen.LINE);
        float lx = x + 9f;
        m.text(Face.MEDIUM, label, lx, y + 8f - 7f * 0.6f, 7f, on ? 0xFFFFFFFF : MenuScreen.mix(MenuScreen.TEXT_2, MenuScreen.TEXT, hv), Align.LEFT);
        if (count > 0) {
            float bx = lx + m.textW(Face.MEDIUM, label, 7f) + 4f;
            m.rect(bx, y + 4f, bx + 10f, y + 12f, 4f, on ? 0x40FFFFFF : MenuScreen.ACCENT);
            m.text(Face.MONO, count > 9 ? "9+" : String.valueOf(count), bx + 5f, y + 5.5f, 5f, 0xFFFFFFFF, Align.CENTER);
        }
        if (over) m.pointer = true;
        m.hits.add(0, new MenuScreen.Hit(x, y, x + cw, y + 16f, action));
        return x + cw;
    }

    private void iconButton(String icon, float x, float y, float s, String tip, Runnable action, boolean armed) {
        iconButton(icon, x, y, s, tip, action, armed, true);
    }

    private void iconButton(String icon, float x, float y, float s, String tip, Runnable action, boolean armed, boolean inList) {
        boolean over = inList ? hovering(x, y, x + s, y + s) : m.inside(x, y, x + s, y + s);
        float hv = m.anim("socialIcon:" + icon + x + ":" + y, over);
        int fill = armed ? 0x40E5484D : MenuScreen.mix(0x0FFFFFFF, 0x2EFFFFFF, hv);
        m.rect(x, y, x + s, y + s, 4f, fill);
        float is = s * 0.6f;
        Overlay.icon(icon, (x + (s - is) / 2f) * m.u, (y + (s - is) / 2f) * m.u, is * m.u, m.c(armed ? 0xFFFF8A8D : MenuScreen.TEXT));
        if (over) {
            m.pointer = true;
            m.tooltip = tip;
            m.hits.add(0, new MenuScreen.Hit(x, y, x + s, y + s, () -> {
                if (!working) action.run();
            }));
        }
    }

    /** A small text button ending at x1; returns its left edge. */
    private float textButton(float x1, float y, String label, boolean primary, Runnable action) {
        float bw = m.textW(Face.SEMIBOLD, label, 6.5f) + 16f, x0 = x1 - bw;
        boolean over = hovering(x0, y, x1, y + 16f) && !working;
        float hv = m.anim("socialBtn:" + label + y, over);
        m.rect(x0, y, x1, y + 16f, 4f, primary ? MenuScreen.mix(MenuScreen.ACCENT, 0xFFEE6A6E, hv) : MenuScreen.mix(0x14FFFFFF, 0x2EFFFFFF, hv));
        m.text(Face.SEMIBOLD, label, (x0 + x1) / 2f, y + 8f - 6.5f * 0.6f, 6.5f, primary ? 0xFFFFFFFF : MenuScreen.TEXT, Align.CENTER);
        if (over) {
            m.pointer = true;
            m.hits.add(0, new MenuScreen.Hit(x0, y, x1, y + 16f, action));
        }
        return x0;
    }

    private void toggle(float x, float y, boolean on, boolean enabled, Runnable action) {
        float tw = 22f, th = 12f;
        float t = m.anim("socialShare", on);
        boolean over = m.inside(x, y, x + tw, y + th) && enabled && !working;
        m.rect(x, y, x + tw, y + th, 6f, MenuScreen.mix(0x26FFFFFF, MenuScreen.ACCENT, t));
        float kx = x + 2f + t * (tw - th);
        m.rect(kx, y + 2f, kx + th - 4f, y + th - 2f, 4f, 0xFFFFFFFF);
        if (over) {
            m.pointer = true;
            m.hits.add(0, new MenuScreen.Hit(x, y, x + tw, y + th, action));
        }
    }

    private boolean confirming(String id) {
        return id.equals(confirm) && System.currentTimeMillis() - confirmAt < 3000L;
    }

    /** The first click arms the action, a second one within 3 seconds runs it. */
    private void twice(String id, Runnable action) {
        if (confirming(id)) {
            confirm = null;
            working = true;
            action.run();
        } else {
            confirm = id;
            confirmAt = System.currentTimeMillis();
        }
    }
}
