package dev.golemsclient.menu;

import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The News page, laid out like OneClient's: a header with a button to the Discord, then the owner's posts, newest
 * first, each with its title, when it was posted, its text and a link if it has one. The Play card stays at the bottom
 * right.
 */
final class NewsPage {
    private final MainMenu m;
    private final String discord;
    private float scroll;
    private float listY0, listY1;

    NewsPage(MainMenu menu, String discord) {
        this.m = menu;
        this.discord = discord;
    }

    void open() {
        scroll = 0f;
        News.get();
    }

    void draw(float rightX0) {
        float x0 = MenuScreen.PAD, x1 = rightX0 - 8f;
        float slide = (1f - m.fade) * 8f;
        // the header
        float hy0 = 50f + slide, hy1 = hy0 + 34f;
        m.card(x0, hy0, x1, hy1);
        m.text(Face.BOLD, "News", x0 + 14f, hy0 + 17f - 10f * 0.6f, 10f, MenuScreen.TEXT, Align.LEFT);
        headerButton(x1 - 8f, hy0 + 7f, hy1 - 7f, "discord", "Join the Discord", () -> Util.getPlatform().openUri(discord));

        // the posts
        float y0 = hy1 + 8f, y1 = MenuScreen.H - 26f + slide;
        m.card(x0, y0, x1, y1);
        List<News.Post> posts = News.get();
        if (posts == null || posts.isEmpty()) {
            float cy = y0 + 46f;
            if (posts == null && !News.failed()) {
                m.text(Face.REGULAR, "Loading the news...", (x0 + x1) / 2f, cy, 7f, MenuScreen.TEXT_2, Align.CENTER);
            } else {
                m.text(Face.SEMIBOLD, "No news yet", (x0 + x1) / 2f, cy, 9f, MenuScreen.TEXT, Align.CENTER);
                m.text(Face.REGULAR, News.failed() ? "Couldn't reach the news right now. Try again in a bit."
                        : "New posts from veno15 show up here.", (x0 + x1) / 2f, cy + 14f, 6.5f, MenuScreen.TEXT_2, Align.CENTER);
            }
            return;
        }
        News.seen();
        float x = x0 + 16f, iw = x1 - x0 - 32f;
        listY0 = y0 + 8f;
        listY1 = y1 - 8f;
        // measure, then draw the visible part
        List<List<String>> bodies = new ArrayList<>();
        float content = 0f;
        for (News.Post p : posts) {
            List<String> lines = wrap(p.body(), iw);
            bodies.add(lines);
            content += height(p, lines);
        }
        float view = listY1 - listY0;
        scroll = MenuScreen.clamp(scroll, 0f, Math.max(0f, content - view));
        Overlay.clip(0f, listY0 * m.u, m.w * m.u, listY1 * m.u);
        float y = listY0 + 8f - scroll;
        for (int i = 0; i < posts.size(); i++) {
            News.Post p = posts.get(i);
            List<String> lines = bodies.get(i);
            float h = height(p, lines);
            if (y + h >= listY0 && y <= listY1) drawPost(p, lines, x, y, iw, i == 0);
            y += h;
        }
        Overlay.unclip();
        if (content > view) {
            float bar = view * view / content, at = (view - bar) * scroll / (content - view);
            m.rect(x1 - 6f, listY0 + at, x1 - 4.5f, listY0 + at + bar, 0.75f, 0x40FFFFFF);
        }
    }

    private float height(News.Post p, List<String> lines) {
        return 34f + lines.size() * 11f + (p.link().isEmpty() ? 0f : 24f) + 18f;
    }

    private void drawPost(News.Post p, List<String> lines, float x, float y, float iw, boolean newest) {
        if (newest) {
            m.rect(x, y + 1f, x + 30f, y + 11f, 2f, 0x26E5484D);
            m.text(Face.MONO, "LATEST", x + 15f, y + 3f, 5f, MenuScreen.ACCENT, Align.CENTER);
        }
        float tx = newest ? x + 36f : x;
        m.text(Face.BOLD, m.fit(Face.BOLD, p.title(), 10f, iw - (tx - x)), tx, y, 10f, MenuScreen.TEXT, Align.LEFT);
        m.text(Face.REGULAR, "veno15 · " + MenuScreen.ago(p.at() * 1000L), x, y + 15f, 6f, MenuScreen.TEXT_3, Align.LEFT);
        float ly = y + 30f;
        for (String line : lines) {
            m.text(Face.REGULAR, line, x, ly, 7f, MenuScreen.TEXT_2, Align.LEFT);
            ly += 11f;
        }
        if (!p.link().isEmpty()) {
            String label = "Open link";
            float lw = m.textW(Face.MEDIUM, label, 6.5f) + 26f;
            float by0 = ly + 4f, by1 = by0 + 16f;
            boolean over = m.inside(x, by0, x + lw, by1) && by0 >= listY0 && by1 <= listY1;
            float hv = m.anim("newslink:" + p.id(), over);
            m.rect(x, by0, x + lw, by1, 4f, MenuScreen.mix(0x14FFFFFF, 0x2EFFFFFF, hv));
            Overlay.icon("arrow", (x + lw - 13f) * m.u, (by0 + 4f) * m.u, 8f * m.u, m.c(MenuScreen.TEXT));
            m.text(Face.MEDIUM, label, x + 8f, by0 + 8f - 6.5f * 0.6f, 6.5f, MenuScreen.TEXT, Align.LEFT);
            if (by0 >= listY0 && by1 <= listY1) {
                m.hit(x, by0, x + lw, by1, () -> Util.getPlatform().openUri(URI.create(p.link())));
                if (over) m.tooltip = p.link();
            }
            ly = by1;
        }
        // a line between posts
        m.rect(x, ly + 12f, x + iw, ly + 12.5f, 0f, MenuScreen.LINE);
    }

    private void headerButton(float x1, float y0, float y1, String icon, String label, Runnable action) {
        float lw = m.textW(Face.MEDIUM, label, 7f) + 30f;
        float x0 = x1 - lw;
        boolean over = m.inside(x0, y0, x1, y1);
        float hv = m.anim("newsbtn", over);
        m.rect(x0, y0, x1, y1, 5f, MenuScreen.mix(0x14FFFFFF, 0x2EFFFFFF, hv));
        m.outline(x0, y0, x1, y1, 5f, MenuScreen.LINE);
        Overlay.icon(icon, (x0 + 9f) * m.u, ((y0 + y1) / 2f - 5f) * m.u, 10f * m.u, m.c(MenuScreen.TEXT));
        m.text(Face.MEDIUM, label, x0 + 23f, (y0 + y1) / 2f - 7f * 0.6f, 7f, MenuScreen.TEXT, Align.LEFT);
        m.hit(x0, y0, x1, y1, action);
    }

    /** The text broken into lines that fit (its own line breaks kept). */
    private List<String> wrap(String text, float width) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (m.textW(Face.REGULAR, next, 7f) > width && !line.isEmpty()) {
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

    boolean scroll(double mouseY, double amount) {
        float y = (float) mouseY / m.u;
        if (y < listY0 || y > listY1) return false;
        scroll -= (float) amount * 30f;
        return true;
    }
}
