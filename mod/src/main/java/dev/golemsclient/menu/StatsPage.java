package dev.golemsclient.menu;

import dev.golemsclient.render.GuiTexture;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import dev.golemsclient.render.Textures;
import dev.golemsclient.stats.LocalStats;
import dev.golemsclient.stats.LocalStats.Ranked;
import dev.golemsclient.stats.LocalStats.Report;
import dev.golemsclient.stats.LocalStats.Stat;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Stats page, laid out like OneClient's: total play time and the last seven days at the top, then your most played
 * worlds and servers, and on the right the totals from your singleplayer worlds (blocks mined, mobs killed...). The
 * Play card stays at the bottom right.
 */
final class StatsPage {
    private final MainMenu m;
    private final Function<String, byte[]> serverIcon;

    StatsPage(MainMenu menu, Function<String, byte[]> serverIcon) {
        this.m = menu;
        this.serverIcon = serverIcon;
    }

    void open() {
        LocalStats.refresh();
    }

    void draw(float rightX0, float playCardTop) {
        Report r = LocalStats.report();
        float x0 = MenuScreen.PAD, x1 = rightX0 - 8f;
        float slide = (1f - m.fade) * 8f;
        float grow = m.anim("statsGrow", r != null);
        drawSummary(r, x0, 50f + slide, x1, 50f + 128f + slide, grow);
        drawPlaces(r, x0, 186f + slide, x1, MenuScreen.H - 26f + slide, grow);
        drawTotals(r, rightX0, 50f + slide, m.w - MenuScreen.PAD, playCardTop - 8f + slide);
    }

    // ------------------------------------------------------------------ top: play time and the week

    private void drawSummary(Report r, float x0, float y0, float x1, float y1, float grow) {
        m.card(x0, y0, x1, y1);
        float x = x0 + 14f, y = y0 + 12f;
        m.text(Face.BOLD, "Stats", x, y, 10f, MenuScreen.TEXT, Align.LEFT);
        m.text(Face.REGULAR, "Saved only on this PC", x1 - 14f, y + 2f, 6.5f, MenuScreen.TEXT_3, Align.RIGHT);
        if (r == null) {
            m.text(Face.REGULAR, "Reading your stats...", x, y + 30f, 7f, MenuScreen.TEXT_2, Align.LEFT);
            return;
        }
        if (r.totalMillis() == 0L && r.weekMillis() == 0L) {
            m.text(Face.SEMIBOLD, "No stats yet", (x0 + x1) / 2f, y0 + 52f, 9f, MenuScreen.TEXT, Align.CENTER);
            m.text(Face.REGULAR, "Play a world or join a server and your time shows up here. Server time is counted from now on.",
                    (x0 + x1) / 2f, y0 + 66f, 6.5f, MenuScreen.TEXT_2, Align.CENTER);
            return;
        }
        float mid = x0 + (x1 - x0) * 0.46f;
        y += 22f;
        // total play time
        m.text(Face.MONO, "TOTAL PLAYTIME", x, y, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
        m.text(Face.BOLD, LocalStats.duration(r.totalMillis()), x - 1f, y + 9f, 30f, MenuScreen.TEXT, Align.LEFT);
        List<String> split = new ArrayList<>();
        if (r.worldMillis() > 0L) split.add(LocalStats.duration(r.worldMillis()) + " in worlds");
        if (r.serverMillis() > 0L) split.add(LocalStats.duration(r.serverMillis()) + " on servers");
        m.text(Face.REGULAR, String.join(", ", split), x, y + 45f, 6.5f, MenuScreen.TEXT_2, Align.LEFT);
        // the week
        m.rect(mid, y - 2f, mid + 0.5f, y + 52f, 0f, MenuScreen.LINE);
        float wx = mid + 14f;
        boolean played = r.weekMillis() > 0L;
        m.text(Face.MONO, "THIS WEEK", wx, y, 5.5f, MenuScreen.TEXT_3, Align.LEFT);
        if (played) m.text(Face.BOLD, LocalStats.duration(r.weekMillis()), wx - 0.5f, y + 9f, 18f, MenuScreen.TEXT, Align.LEFT);
        m.text(Face.REGULAR, played ? "Last 7 days" : "Counted from now on", wx, y + 32f, 6.5f, MenuScreen.TEXT_2, Align.LEFT);
        long most = 1L;
        for (LocalStats.Day d : r.days()) most = Math.max(most, d.millis());
        float bw = 9f, gap = 12f, bh = 44f;
        float bx = x1 - 14f - 7 * bw - 6 * gap, by = y - 2f;
        for (int i = 0; i < r.days().size(); i++) {
            LocalStats.Day d = r.days().get(i);
            float cx = bx + i * (bw + gap);
            m.rect(cx, by, cx + bw, by + bh, bw / 2f, 0x14FFFFFF);
            float fill = d.millis() == 0L ? 0f : Math.max(bw, bh * d.millis() / (float) most) * grow;
            if (fill > 0f) m.rect(cx, by + bh - fill, cx + bw, by + bh, bw / 2f, MenuScreen.ACCENT);
            m.text(Face.MEDIUM, d.label(), cx + bw / 2f, by + bh + 5f, 6f, d.today() ? MenuScreen.TEXT : MenuScreen.TEXT_3, Align.CENTER);
            if (m.inside(cx - 2f, by, cx + bw + 2f, by + bh + 12f)) m.tooltip = d.label() + ": " + LocalStats.duration(d.millis());
        }
        // sessions, places, average
        float ry = y1 - 22f;
        m.rect(x0 + 14f, ry - 8f, x1 - 14f, ry - 7.5f, 0f, MenuScreen.LINE);
        float rx = x;
        long worlds = r.worlds().size(), servers = r.servers().size();
        if (r.sessions() > 0) rx = figure(rx, ry, LocalStats.count(r.sessions()), r.sessions() == 1 ? "session" : "sessions");
        if (worlds > 0) rx = figure(rx, ry, LocalStats.count(worlds), worlds == 1 ? "world played" : "worlds played");
        if (servers > 0) rx = figure(rx, ry, LocalStats.count(servers), servers == 1 ? "server played" : "servers played");
        if (r.sessions() > 0) figure(rx, ry, LocalStats.duration(r.sessionMillis() / r.sessions()), "average session");
    }

    private float figure(float x, float y, String value, String label) {
        m.text(Face.BOLD, value, x, y, 11f, MenuScreen.TEXT, Align.LEFT);
        float vw = m.textW(Face.BOLD, value, 11f);
        m.text(Face.REGULAR, label, x + vw + 5f, y + 3.5f, 6.5f, MenuScreen.TEXT_2, Align.LEFT);
        return x + vw + 5f + m.textW(Face.REGULAR, label, 6.5f) + 18f;
    }

    // ------------------------------------------------------------------ middle: most played worlds and servers

    private void drawPlaces(Report r, float x0, float y0, float x1, float y1, float grow) {
        m.card(x0, y0, x1, y1);
        float mid = (x0 + x1) / 2f;
        m.rect(mid, y0 + 12f, mid + 0.5f, y1 - 12f, 0f, MenuScreen.LINE);
        column(r == null ? null : r.worlds(), false, "Most played worlds", "No worlds yet", "Play a singleplayer world and it shows up here.",
                x0 + 14f, y0 + 12f, mid - 14f, y1, grow);
        column(r == null ? null : r.servers(), true, "Most played servers", "No servers yet", "Join a server and it shows up here.",
                mid + 14f, y0 + 12f, x1 - 14f, y1, grow);
    }

    private void column(List<Ranked> list, boolean servers, String title, String empty, String hint, float x0, float y, float x1, float yEnd,
                        float grow) {
        m.text(Face.BOLD, title, x0, y, 9f, MenuScreen.TEXT, Align.LEFT);
        y += 22f;
        if (list == null) return;
        if (list.isEmpty()) {
            float cy = (y + yEnd) / 2f - 12f;
            m.text(Face.SEMIBOLD, empty, (x0 + x1) / 2f, cy, 8f, MenuScreen.TEXT, Align.CENTER);
            m.text(Face.REGULAR, hint, (x0 + x1) / 2f, cy + 13f, 6.5f, MenuScreen.TEXT_2, Align.CENTER);
            return;
        }
        long most = Math.max(1L, list.get(0).millis());
        int rows = (int) Math.min(list.size(), Math.floor((yEnd - 12f - y) / 24f));
        for (int i = 0; i < rows; i++) {
            Ranked p = list.get(i);
            float ry = y + i * 24f;
            m.text(Face.MONO, Integer.toString(i + 1), x0 + 3f, ry + 4f, 6f, MenuScreen.TEXT_3, Align.CENTER);
            float ix = x0 + 12f, is = 14f;
            if (servers) serverIcon(p, ix, ry, is);
            else worldIcon(p, ix, ry, is);
            float tx = ix + is + 7f;
            String time = LocalStats.duration(p.millis());
            float timeW = m.textW(Face.MEDIUM, time, 6.5f);
            String name = p.name() == null || p.name().isBlank() ? p.id() : p.name();
            m.text(Face.MEDIUM, m.fit(Face.MEDIUM, name, 7.5f, x1 - tx - timeW - 8f), tx, ry + 1f, 7.5f, MenuScreen.TEXT, Align.LEFT);
            m.text(Face.MEDIUM, time, x1, ry + 2f, 6.5f, MenuScreen.TEXT_2, Align.RIGHT);
            float bar = (x1 - tx) * (p.millis() / (float) most) * grow;
            m.rect(tx, ry + 13f, x1, ry + 14.5f, 0.75f, 0x14FFFFFF);
            m.rect(tx, ry + 13f, tx + Math.max(2f, bar), ry + 14.5f, 0.75f, MenuScreen.ACCENT);
        }
    }

    private void serverIcon(Ranked p, float x, float y, float s) {
        byte[] png = serverIcon.apply(p.id());
        GuiTexture tex = png == null ? null : Textures.image("server:" + p.id() + ":" + java.util.Arrays.hashCode(png), png, true, 0.18f);
        if (tex != null) Overlay.texture(tex, x * m.u, y * m.u, (x + s) * m.u, (y + s) * m.u, 0f, 0f, 1f, 1f, m.c(0xFFFFFFFF));
        else m.letterTile(p.name(), x, y, s);
    }

    private void worldIcon(Ranked p, float x, float y, float s) {
        String key = "world:" + p.id();
        GuiTexture tex;
        if (Textures.has(key)) {
            tex = Textures.image(key, null, true, 0.18f);
        } else {
            byte[] png = null;
            try {
                Path icon = Minecraft.getInstance().gameDirectory.toPath().resolve("saves").resolve(p.id()).resolve("icon.png");
                if (Files.isRegularFile(icon) && Files.size(icon) < 1_000_000) png = Files.readAllBytes(icon);
            } catch (Exception ignored) {
            }
            tex = Textures.image(key, png, true, 0.18f);
        }
        if (tex != null) Overlay.texture(tex, x * m.u, y * m.u, (x + s) * m.u, (y + s) * m.u, 0f, 0f, 1f, 1f, m.c(0xFFFFFFFF));
        else m.letterTile(p.name(), x, y, s);
    }

    // ------------------------------------------------------------------ right: totals from your worlds

    private void drawTotals(Report r, float x0, float y0, float x1, float y1) {
        m.card(x0, y0, x1, y1);
        if (r == null) return;
        if (r.noWorldStats()) {
            float cy = (y0 + y1) / 2f - 14f;
            m.text(Face.SEMIBOLD, "No world stats yet", (x0 + x1) / 2f, cy, 8.5f, MenuScreen.TEXT, Align.CENTER);
            m.text(Face.REGULAR, "Blocks mined, mobs killed and more", (x0 + x1) / 2f, cy + 14f, 6.5f, MenuScreen.TEXT_2, Align.CENTER);
            m.text(Face.REGULAR, "come from your singleplayer worlds.", (x0 + x1) / 2f, cy + 24f, 6.5f, MenuScreen.TEXT_2, Align.CENTER);
            return;
        }
        float x = x0 + 12f, y = y0 + 12f, iw = x1 - x0 - 24f;
        m.text(Face.BOLD, "Totals", x, y, 9.5f, MenuScreen.TEXT, Align.LEFT);
        y += 20f;
        List<String[]> rows = new ArrayList<>();
        add(rows, "Blocks mined", r.blocksMined(), r.topMined() == null ? null : "Mostly " + r.topMined());
        add(rows, "Mobs killed", r.totals().get(Stat.MOB_KILLS), r.topKilled() == null ? null : "Mostly " + r.topKilled());
        add(rows, "Deaths", r.totals().get(Stat.DEATHS), null);
        long walked = r.totals().get(Stat.WALKED_CM);
        if (walked > 0L) rows.add(new String[] {"Distance walked", LocalStats.distance(walked), null});
        add(rows, "Items crafted", r.itemsCrafted(), null);
        add(rows, "Jumps", r.totals().get(Stat.JUMPS), null);
        add(rows, "Fish caught", r.totals().get(Stat.FISH_CAUGHT), null);
        add(rows, "Villager trades", r.totals().get(Stat.VILLAGER_TRADES), null);
        add(rows, "Animals bred", r.totals().get(Stat.ANIMALS_BRED), null);
        add(rows, "Players killed", r.totals().get(Stat.PLAYER_KILLS), null);
        for (String[] row : rows) {
            float h = row[2] == null ? 16f : 22f;
            if (y + h > y1 - 8f) break;
            m.text(Face.MEDIUM, row[0], x, y, 7f, MenuScreen.TEXT, Align.LEFT);
            if (row[2] != null) m.text(Face.REGULAR, m.fit(Face.REGULAR, row[2], 6f, iw - 60f), x, y + 10f, 6f, MenuScreen.TEXT_3, Align.LEFT);
            m.text(Face.BOLD, row[1], x + iw, y - 0.5f, 8.5f, MenuScreen.TEXT, Align.RIGHT);
            y += h + 2f;
        }
    }

    private static void add(List<String[]> rows, String label, Long value, String detail) {
        if (value == null || value <= 0L) return;
        rows.add(new String[] {label, LocalStats.count(value), detail});
    }
}
