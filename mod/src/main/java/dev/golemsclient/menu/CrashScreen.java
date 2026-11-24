package dev.golemsclient.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golemsclient.GolemsClient;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Golem's Client's crash screen, laid out like CrashPatch's (the one OneClient shows; CrashPatch by Polyfrost): what
 * happened, what probably caused it, tips and the crash log in tabs (copy, or upload to mclo.gs), Golem's Client's
 * Discord for help, and Return to game. It shows whenever CrashPatch catches a crash or a disconnect.
 */
public final class CrashScreen extends MenuScreen {
    private static final String DISCORD = "https://discord.gg/q67qzHG2kf";

    private record Tab(String name, List<String> lines) { }

    private final boolean disconnect;
    private final String suspect;
    private final File file;
    private final String fullText;
    private final List<Tab> tabs = new ArrayList<>();
    private int tab;
    private float scroll, logY0, logY1;
    private volatile boolean uploading;

    private CrashScreen(boolean disconnect, String text, String suspect, File file, Object scan) {
        super(Component.literal(disconnect ? "Disconnected" : "Crashed"));
        this.disconnect = disconnect;
        this.suspect = suspect == null || suspect.isBlank() ? "Unknown" : suspect;
        this.file = file;
        this.fullText = text == null ? "" : text;
        readTabs(scan);
        if (tabs.isEmpty()) tabs.add(new Tab(disconnect ? "Reason" : "Crash log", List.of(fullText.split("\\R"))));
    }

    // ------------------------------------------------------------------ from CrashPatch

    /** Our screen for a CrashPatch crash screen that's about to show; null to leave CrashPatch's (a crash at startup). */
    public static Screen fromCrashPatch(Object crashUi) {
        try {
            Class<?> c = crashUi.getClass();
            String text = (String) field(c, "scanText").get(crashUi);
            File file = (File) field(c, "file").get(crashUi);
            String suspect = (String) field(c, "susThing").get(crashUi);
            String type = String.valueOf(field(c, "type").get(crashUi));
            if (type.equals("INIT")) return null;
            boolean disconnect = type.equals("DISCONNECT");
            return new CrashScreen(disconnect, text, suspect, file, scan(text, disconnect));
        } catch (Throwable e) {
            GolemsClient.LOGGER.warn("Couldn't show Golem's Client's crash screen, so CrashPatch's shows", e);
            return null;
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** CrashPatch's tips for this crash (its scanner), or null. */
    private static Object scan(String text, boolean disconnect) {
        try {
            Class<?> scanner = Class.forName("org.polyfrost.crashpatch.client.crashes.CrashScanner");
            Object instance = scanner.getField("INSTANCE").get(null);
            for (Method m : scanner.getMethods()) {
                if (m.getName().equals("scan") && m.getParameterCount() == 3) return m.invoke(instance, text, disconnect, Map.of());
            }
            for (Method m : scanner.getMethods()) {
                if (m.getName().equals("scan") && m.getParameterCount() == 2) return m.invoke(instance, text, disconnect);
            }
        } catch (Throwable e) {
            GolemsClient.LOGGER.debug("Couldn't scan the crash: {}", e.toString());
        }
        return null;
    }

    private void readTabs(Object scan) {
        if (scan == null) return;
        try {
            List<?> solutions = (List<?>) scan.getClass().getMethod("getSolutions").invoke(scan);
            for (Object s : solutions) {
                String name = String.valueOf(s.getClass().getMethod("getName").invoke(s));
                List<?> lines = (List<?>) s.getClass().getMethod("getSolutions").invoke(s);
                List<String> out = new ArrayList<>();
                for (Object l : lines) out.addAll(List.of(String.valueOf(l).split("\\R")));
                tabs.add(new Tab(name, out));
            }
        } catch (Throwable e) {
            GolemsClient.LOGGER.debug("Couldn't read the crash tips: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null) renderBlurredBackground(g);
        g.fill(0, 0, width, height, minecraft.level != null ? 0xB30E0E10 : 0xFF0E0E10);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        beginFrame(g, mouseX, mouseY);
        float cw = Math.min(420f, w - 40f), ch = 360f;
        float x0 = w / 2f - cw / 2f, x1 = x0 + cw, y0 = H / 2f - ch / 2f + (1f - fade) * 8f, y1 = y0 + ch;
        card(x0, y0, x1, y1);
        float cx = (x0 + x1) / 2f, y = y0 + 18f;

        Overlay.icon("warning", (cx - 8f) * u, y * u, 16f * u, c(ACCENT));
        y += 24f;
        text(Face.BOLD, disconnect ? "You were disconnected" : "Uh-oh. Your game crashed!", cx, y, 12f, TEXT, Align.CENTER);
        y += 18f;
        text(Face.REGULAR, disconnect ? "The full reason is below, and you can keep playing."
                : "Golem's Client caught it, so you can keep playing.", cx, y, 7f, TEXT_2, Align.CENTER);
        y += 20f;
        text(Face.REGULAR, disconnect ? "Reason for disconnect:" : "This could have been caused by:", cx, y, 7f, TEXT_2, Align.CENTER);
        y += 12f;
        text(Face.BOLD, fit(Face.BOLD, suspect, 10f, cw - 40f), cx, y, 10f, ACCENT, Align.CENTER);
        y += 22f;

        // the tips and the crash log, in tabs
        float bx0 = x0 + 18f, bx1 = x1 - 18f, by0 = y, by1 = y1 - 78f;
        rect(bx0, by0, bx1, by1, 6f, 0xFF0B0B0D);
        outline(bx0, by0, bx1, by1, 6f, LINE);
        float tx = bx0 + 10f;
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            String label = t.name() + (t.lines().size() > 20 ? " (" + t.lines().size() + ")" : "");
            float tw = textW(Face.MEDIUM, label, 7f);
            if (tx + tw > bx1 - 50f) break;
            boolean active = i == tab, over = inside(tx - 4f, by0 + 4f, tx + tw + 4f, by0 + 20f);
            float hv = anim("tab" + i, over);
            text(Face.MEDIUM, label, tx, by0 + 8f, 7f, active ? TEXT : mix(TEXT_2, TEXT, hv), Align.LEFT);
            if (active) rect(tx, by0 + 18.5f, tx + tw, by0 + 20f, 0.75f, ACCENT);
            final int index = i;
            hit(tx - 4f, by0 + 4f, tx + tw + 4f, by0 + 20f, () -> {
                tab = index;
                scroll = 0f;
            });
            tx += tw + 16f;
        }
        smallIcon("copy", bx1 - 30f, by0 + 12f, "Copy", this::copy);
        smallIcon(uploading ? "refresh" : "cloud", bx1 - 16f, by0 + 12f, "Upload to mclo.gs (the link is copied)", this::upload);
        rect(bx0, by0 + 21f, bx1, by0 + 21.5f, 0f, LINE);

        List<String> lines = tabs.get(tab).lines();
        logY0 = by0 + 26f;
        logY1 = by1 - 6f;
        float lh = 9f, content = lines.size() * lh, view = logY1 - logY0;
        scroll = clamp(scroll, 0f, Math.max(0f, content - view));
        Overlay.clip(bx0 * u, logY0 * u, bx1 * u, logY1 * u);
        int first = (int) (scroll / lh);
        for (int i = first; i < lines.size(); i++) {
            float ly = logY0 + i * lh - scroll;
            if (ly > logY1) break;
            String line = lines.get(i).replace("\t", "    ");
            text(Face.MONO, fit(Face.MONO, line, 5.5f, bx1 - bx0 - 22f), bx0 + 10f, ly, 5.5f, TEXT_2, Align.LEFT);
        }
        Overlay.unclip();
        if (content > view) {
            float bar = view * view / content, at = (view - bar) * scroll / (content - view);
            rect(bx1 - 5f, logY0 + at, bx1 - 3.5f, logY0 + at + bar, 0.75f, 0x40FFFFFF);
        }

        // help
        float hy = y1 - 66f;
        text(Face.REGULAR, "If that doesn't help, ask in Golem's Client's Discord", cx, hy, 7f, TEXT_2, Align.CENTER);
        float lw = textW(Face.MEDIUM, DISCORD, 7f) + 14f;
        boolean overLink = inside(cx - lw / 2f, hy + 10f, cx + lw / 2f, hy + 22f);
        Overlay.icon("discord", (cx - lw / 2f) * u, (hy + 11.5f) * u, 9f * u, c(mix(0xFF8FA0FF, 0xFFB4C0FF, anim("link", overLink))));
        text(Face.MEDIUM, DISCORD, cx - lw / 2f + 14f, hy + 12f, 7f, mix(0xFF8FA0FF, 0xFFB4C0FF, anim("link", overLink)), Align.LEFT);
        hit(cx - lw / 2f, hy + 10f, cx + lw / 2f, hy + 22f, () -> Util.getPlatform().openUri(URI.create(DISCORD)));

        // buttons
        float bw = 110f, gap = 8f, total = file != null ? bw * 2f + gap : bw;
        float b0 = cx - total / 2f, btnY = y1 - 34f;
        button(b0, btnY, b0 + bw, btnY + 22f, "Return to game", true, this::onClose);
        if (file != null) {
            button(b0 + bw + gap, btnY, b0 + bw * 2f + gap, btnY + 22f, "Open crash log", false,
                    () -> Util.getPlatform().openFile(file));
        }
        endFrame(g);
    }

    private void button(float x0, float y0, float x1, float y1, String label, boolean primary, Runnable action) {
        boolean over = inside(x0, y0, x1, y1);
        float hv = anim("btn:" + label, over);
        if (primary) {
            rect(x0, y0, x1, y1, 5f, mix(ACCENT, 0xFFF0676B, hv));
        } else {
            rect(x0, y0, x1, y1, 5f, mix(0x0FFFFFFF, 0x24FFFFFF, hv));
            outline(x0, y0, x1, y1, 5f, LINE);
        }
        text(Face.SEMIBOLD, label, (x0 + x1) / 2f, (y0 + y1) / 2f - 7.5f * 0.6f, 7.5f, primary ? 0xFFFFFFFF : TEXT, Align.CENTER);
        hit(x0, y0, x1, y1, action);
    }

    // ------------------------------------------------------------------ actions

    private void copy() {
        minecraft.keyboardHandler.setClipboard(String.join("\n", tabs.get(tab).lines()));
        showToast("Copied.");
    }

    /** Uploads the whole crash log to mclo.gs, then copies the link and opens it. */
    private void upload() {
        if (uploading) return;
        uploading = true;
        String body = "content=" + URLEncoder.encode(fullText, StandardCharsets.UTF_8);
        Thread t = new Thread(() -> {
            String link = null;
            try {
                HttpResponse<String> r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build().send(
                        HttpRequest.newBuilder(URI.create("https://api.mclo.gs/1/log")).timeout(Duration.ofSeconds(20))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .header("User-Agent", "Golem's Client " + GolemsClient.version())
                                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                if (o.has("success") && o.get("success").getAsBoolean()) link = o.get("url").getAsString();
            } catch (Exception e) {
                GolemsClient.LOGGER.warn("Couldn't upload the crash log: {}", e.toString());
            }
            String done = link;
            minecraft.execute(() -> {
                uploading = false;
                if (done == null) {
                    showToast("Couldn't upload the log.");
                    return;
                }
                minecraft.keyboardHandler.setClipboard(done);
                Util.getPlatform().openUri(URI.create(done));
                showToast("Link copied and opened in your browser.");
            });
        }, "Golem's Client log upload");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onClose() {
        // CrashPatch's "stuck on this screen" note on the connecting screen points at Polyfrost's Discord
        try {
            Class<?> k = Class.forName("org.polyfrost.crashpatch.CrashPatchConstants");
            Field f = k.getDeclaredField("recoveredFromCrash");
            f.setAccessible(true);
            Object target = java.lang.reflect.Modifier.isStatic(f.getModifiers()) ? null : k.getField("INSTANCE").get(null);
            f.setBoolean(target, false);
        } catch (Throwable ignored) {
        }
        minecraft.setScreen(null);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return clickHit(event) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (float) scrollY * 27f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Dev screenshots only: a crash screen with a made-up crash. */
    public static Screen devSample() {
        String text = "---- Minecraft Crash Report ----\n// Don't do that.\n\nTime: 2026-11-24 15:36:53\nDescription: Rendering screen\n\n"
                + "java.lang.NullPointerException: Cannot invoke \"Example.label()\" because \"this.current\" is null\n"
                + "\tat knot//dev.example.Screen.draw(Screen.java:354)\n\tat knot//dev.example.Screen.render(Screen.java:262)\n";
        return new CrashScreen(false, text, "Minecraft", null, null);
    }
}
