package dev.golemsclient.menu;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.golemsclient.GolemsClient;
import dev.golemsclient.media.MediaSession;
import dev.golemsclient.media.NowPlaying;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import dev.golemsclient.render.Textures;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Golem's Client's main menu. The panorama, softly blurred, with your own player standing in the middle (drag to turn
 * them; they look at the mouse); a top bar with the logo, tabs, quick buttons and your account; the owner's
 * announcement; down the left what's playing, FPS, the Social hint and your profile; down the right the play card,
 * which joins the server you played last (or opens your newest world) in one click.
 *
 * <p>Everything is laid out on a canvas 450 units tall, as wide as the window's shape allows, so it looks the same at
 * every window size and GUI scale. Minecraft draws the background, the player and the splash; the overlay draws the
 * rest (smooth text and rounded cards) on top.
 */
public final class MainMenu extends Screen {
    // ------------------------------------------------------------------ look (the launcher's Iron Dark)
    private static final int TEXT = 0xFFEDEDEF;
    private static final int TEXT_2 = 0xFF9A9AA2;
    private static final int TEXT_3 = 0xFF6E6E76;
    private static final int CARD = 0xD8141417;
    private static final int CARD_HI = 0xE81C1C21;
    private static final int LINE = 0x1FFFFFFF;
    private static final int WELL = 0xFF0E0E10;
    private static final int ACCENT = GolemsClient.ACCENT;
    private static final int LIVE = 0xFF3DD68C;
    private static final int SPLASH = 0xFFFFE45C;
    private static final String DISCORD = "https://discord.gg/q67qzHG2kf";
    private static final String TIKTOK = "https://www.tiktok.com/@holygrajl";
    private static final String[] TABS = {"Play", "Wardrobe", "Stats", "News", "Screenshots"};

    /** The canvas height in units. */
    private static final float H = 450f;
    private static final float PAD = 16f;
    private static final float SIDE_W = 196f;

    // ------------------------------------------------------------------ state
    private final MenuData data = MenuData.get();
    private final Splash splash = new Splash();
    private final Map<String, Float> anims = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private ServerPing ping;
    private Supplier<PlayerSkin> skin;
    private PlayerModel wide;
    private PlayerModel slim;
    private List<LevelSummary> worlds = List.of();
    private boolean worldsLoaded;
    private int pickServer;
    private int pickWorld;

    private float u = 1f;      // GUI pixels per unit
    private float w = 800f;    // canvas width in units
    private float mx, my;      // the mouse, in units
    private long openedAt;
    private long lastFrame;
    private float dt;
    private float fade = 1f;

    private float playerYaw = 22f;
    private float playerPitch = -4f;
    private boolean dragging;
    private float tabX = -1f, tabW;
    private boolean socialOpen;
    private String toast;
    private long toastAt;
    private String tooltip;
    private boolean pointer;

    private record Hit(float x0, float y0, float x1, float y1, Runnable action) {
        boolean inside(float x, float y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    public MainMenu() {
        super(Component.literal("Golem's Client"));
    }

    /** Off with -Dgolemsclient.vanillaMenu=true (for testing). */
    public static boolean enabled() {
        return !Boolean.getBoolean("golemsclient.vanillaMenu");
    }

    @Override
    protected void init() {
        if (openedAt == 0L) openedAt = System.currentTimeMillis();
        if (ping == null) ping = new ServerPing();
        if (wide == null) {
            var models = minecraft.getEntityModels();
            wide = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        if (skin == null) skin = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
        if (!worldsLoaded) {
            worldsLoaded = true;
            loadWorlds();
        }
    }

    private void loadWorlds() {
        try {
            var source = minecraft.getLevelSource();
            source.loadLevelSummaries(source.findLevelCandidates()).thenAccept(list -> minecraft.execute(() -> {
                List<LevelSummary> sorted = new ArrayList<>(list);
                sorted.removeIf(LevelSummary::isDisabled);
                sorted.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
                worlds = sorted.size() > MenuData.MAX_RECENT ? sorted.subList(0, MenuData.MAX_RECENT) : sorted;
            }));
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't list the worlds", e);
        }
    }

    @Override
    public void tick() {
        if (ping != null) ping.tick();
    }

    @Override
    public void removed() {
        if (ping != null) ping.stop();
        for (LevelSummary s : worlds) Textures.release("world:" + s.getLevelId());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ background

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPanorama(g, partialTick);
        renderBlurredBackground(g);
        // a darker top and bottom, so the bar and the cards read well over any panorama
        g.fillGradient(0, 0, width, height / 4, 0x8C000000, 0x00000000);
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0xA6000000);
        minecraft.gui.renderDeferredSubtitles();
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var window = minecraft.getWindow();
        u = window.getGuiScaledHeight() / H;
        w = window.getGuiScaledWidth() / u;
        mx = mouseX / u;
        my = mouseY / u;
        long now = System.currentTimeMillis();
        dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        fade = ease(Math.min(1f, (now - openedAt) / 450f));
        hits.clear();
        tooltip = null;
        pointer = false;

        drawPlayer(g);
        drawSplash(g);
        drawTopBar();
        drawAnnouncement();
        drawLeft();
        drawPlayCard();
        drawFooter();
        drawToast();
        if (socialOpen) drawSocial();
        drawTooltip();
        if (pointer) g.requestCursor(CursorTypes.POINTING_HAND);
    }

    // ------------------------------------------------------------------ player

    private float[] playerBox() {
        float ph = Math.min(H * 0.42f, 200f);
        float feet = H * 0.72f;
        float pw = ph * 0.62f;
        return new float[] {w / 2f - pw / 2f, feet - ph, w / 2f + pw / 2f, feet};
    }

    private void drawPlayer(GuiGraphics g) {
        float[] b = playerBox();
        PlayerSkin s = skin.get();
        PlayerModel model = s.model() == PlayerModelType.SLIM ? slim : wide;

        // look at the mouse, a little; idle sway
        float cx = (b[0] + b[2]) / 2f, headY = b[1] + (b[3] - b[1]) * 0.12f;
        float t = (System.currentTimeMillis() % 100000L) / 1000f;
        if (!dragging && !socialOpen) {
            float lookYaw = clamp((mx - cx) / w * 70f, -35f, 35f);
            float lookPitch = clamp((my - headY) / H * 45f, -25f, 25f);
            model.head.yRot = approach(model.head.yRot, (float) Math.toRadians(lookYaw), 8f);
            model.head.xRot = approach(model.head.xRot, (float) Math.toRadians(lookPitch), 8f);
        }
        model.rightArm.zRot = (float) (Math.cos(t * 1.1f) * 0.05f + 0.05f);
        model.leftArm.zRot = -(float) (Math.cos(t * 1.1f) * 0.05f + 0.05f);
        model.rightArm.xRot = (float) Math.sin(t * 0.8f) * 0.04f;
        model.leftArm.xRot = -(float) Math.sin(t * 0.8f) * 0.04f;

        int x0 = Math.round(b[0] * u), y0 = Math.round(b[1] * u), x1 = Math.round(b[2] * u), y1 = Math.round(b[3] * u);
        float scale = 0.97f * (y1 - y0) / 2.125f;
        g.submitSkinRenderState(model, s.body().texturePath(), scale, playerPitch, playerYaw, -1.0625f, x0, y0, x1, y1);

        // the nametag over the head
        String name = minecraft.getUser().getName();
        float nameSize = 8f, youSize = 6.5f;
        float nw = textW(Face.SEMIBOLD, name, nameSize), yw = textW(Face.MEDIUM, "You", youSize);
        float tw = nw + 5f + yw + 14f, ty = b[1] - 22f;
        rect(cx - tw / 2f, ty, cx + tw / 2f, ty + 16f, 4f, 0xB40E0E10);
        outline(cx - tw / 2f, ty, cx + tw / 2f, ty + 16f, 4f, LINE);
        text(Face.SEMIBOLD, name, cx - tw / 2f + 7f, ty + 8f - nameSize * 0.6f, nameSize, TEXT, Align.LEFT);
        text(Face.MEDIUM, "You", cx + tw / 2f - 7f, ty + 8f - youSize * 0.6f, youSize, TEXT_2, Align.RIGHT);

        if (inside(b[0], b[1], b[2], b[3])) pointer = true;
    }

    private void drawSplash(GuiGraphics g) {
        if (minecraft.options.hideSplashTexts().get()) return;
        float[] b = playerBox();
        splash.draw(g, (b[2] + (b[2] - b[0]) * 0.15f) * u, (b[1] + (b[3] - b[1]) * 0.1f) * u, u, fade);
    }

    // ------------------------------------------------------------------ top bar

    private void drawTopBar() {
        float y = 14f;
        // logo and name
        Overlay.image("/assets/golemsclient/textures/logo.png", PAD * u, y * u, (PAD + 18f) * u, (y + 18f) * u, c(0xFFFFFFFF));
        float nameH = 8.5f, nameW = nameH * (1130f / 128f);
        Overlay.image("/assets/golemsclient/textures/wordmark.png", (PAD + 25f) * u, (y + 9f - nameH / 2f) * u,
                (PAD + 25f + nameW) * u, (y + 9f + nameH / 2f) * u, c(TEXT));

        // tabs
        float size = 8.5f, gap = 22f;
        float total = -gap;
        for (String tab : TABS) total += textW(Face.MEDIUM, tab, size) + gap;
        float x = w / 2f - total / 2f;
        for (int i = 0; i < TABS.length; i++) {
            String tab = TABS[i];
            float tw = textW(Face.MEDIUM, tab, size);
            boolean active = i == 0;
            boolean over = inside(x - 6f, y - 2f, x + tw + 6f, y + 20f);
            float hv = anim("tab" + i, over);
            int color = active ? TEXT : mix(TEXT_2, TEXT, hv);
            text(Face.MEDIUM, tab, x, y + 9f - size * 0.6f, size, color, Align.LEFT);
            if (active) {
                if (tabX < 0f) {
                    tabX = x;
                    tabW = tw;
                }
                tabX = approach(tabX, x, 14f);
                tabW = approach(tabW, tw, 14f);
            }
            final int index = i;
            hit(x - 6f, y - 2f, x + tw + 6f, y + 20f, () -> openTab(index));
            x += tw + gap;
        }
        rect(tabX, y + 18f, tabX + tabW, y + 19.5f, 0.75f, ACCENT);

        // account, then quick buttons to its left
        float head = 18f;
        float hx = w - PAD - head;
        drawHead(hx, y, head, 4f);
        String name = minecraft.getUser().getName();
        text(Face.SEMIBOLD, name, hx - 8f, y + 9f - 8.5f * 0.6f, 8.5f, TEXT, Align.RIGHT);
        float ix = hx - 8f - textW(Face.SEMIBOLD, name, 8.5f) - 18f;
        ix = iconButton("quit", ix, y + 9f, "Quit game", () -> minecraft.stop());
        ix = iconButton("settings", ix, y + 9f, "Options", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        ix = iconButton("cloud", ix, y + 9f, "Realms", () -> minecraft.setScreen(new com.mojang.realmsclient.RealmsMainScreen(this)));
        Announcement.Post post = Announcement.get();
        if (post != null && post.id().equals(data.dismissedAnnouncement)) {
            iconButton("bell", ix, y + 9f, "Show the announcement", () -> {
                data.dismissedAnnouncement = "";
                data.save();
            });
        }
    }

    /** A round icon button centred at (cx, cy); returns where the next one (to its left) goes. */
    private float iconButton(String icon, float cx, float cy, String tip, Runnable action) {
        float r = 10f;
        boolean over = inside(cx - r, cy - r, cx + r, cy + r);
        float hv = anim("icon:" + icon, over);
        if (hv > 0.01f) Overlay.circle(cx * u, cy * u, r * u, c(alpha(0x1FFFFFFF, hv)));
        float s = 11f;
        Overlay.icon(icon, (cx - s / 2f) * u, (cy - s / 2f) * u, s * u, c(mix(TEXT_2, TEXT, hv)));
        hit(cx - r, cy - r, cx + r, cy + r, action);
        if (over) tooltip = tip;
        return cx - 2f * r - 4f;
    }

    private void openTab(int index) {
        switch (index) {
            case 0 -> { }
            case 4 -> {
                openFolder(minecraft.gameDirectory.toPath().resolve("screenshots"));
                showToast("Opened your screenshots folder. The Screenshots tab is coming soon.");
            }
            default -> showToast(TABS[index] + " is coming in a Golem's Client update soon.");
        }
    }

    // ------------------------------------------------------------------ announcement

    private void drawAnnouncement() {
        Announcement.Post post = Announcement.get();
        if (post == null || post.id().equals(data.dismissedAnnouncement)) return;
        float a = anim("announcement", true);
        float bw = Math.min(330f, w - 2f * PAD), bh = 30f;
        float x0 = w / 2f - bw / 2f, y0 = 46f + (1f - a) * -6f;
        rect(x0, y0, x0 + bw, y0 + bh, 6f, alpha(CARD_HI, a));
        outline(x0, y0, x0 + bw, y0 + bh, 6f, alpha(0x40E5484D, a));
        Overlay.circle((x0 + 12f) * u, (y0 + bh / 2f) * u, 2.5f * u, c(alpha(ACCENT, a)));

        String dismiss = "Dismiss";
        float dw = textW(Face.MEDIUM, dismiss, 6.5f) + 14f;
        float dx1 = x0 + bw - 8f, dx0 = dx1 - dw, dy0 = y0 + 8f, dy1 = y0 + bh - 8f;
        boolean over = inside(dx0, dy0, dx1, dy1);
        float hv = anim("dismiss", over);
        rect(dx0, dy0, dx1, dy1, 4f, alpha(mix(0x14FFFFFF, 0x2EFFFFFF, hv), a));
        text(Face.MEDIUM, dismiss, (dx0 + dx1) / 2f, (dy0 + dy1) / 2f - 6.5f * 0.6f, 6.5f, alpha(TEXT_2, a), Align.CENTER);
        hit(dx0, dy0, dx1, dy1, () -> {
            data.dismissedAnnouncement = post.id();
            data.save();
        });

        String line = fit(Face.SEMIBOLD, post.text(), 8f, dx0 - x0 - 30f);
        text(Face.SEMIBOLD, line, x0 + 22f, y0 + 6f, 8f, alpha(TEXT, a), Align.LEFT);
        String from = "From veno15" + (post.at() > 0 ? " · " + ago(post.at() * 1000L) : "");
        text(Face.REGULAR, from, x0 + 22f, y0 + 17f, 6.5f, alpha(TEXT_2, a), Align.LEFT);
    }

    // ------------------------------------------------------------------ left column

    private void drawLeft() {
        float x0 = PAD, x1 = PAD + SIDE_W;
        float slide = (1f - fade) * 8f;
        float y1 = H - 26f + slide;

        // profile
        float py0 = y1 - 36f;
        card(x0, py0, x1, y1);
        drawHead(x0 + 9f, py0 + 8f, 20f, 4f);
        String name = minecraft.getUser().getName();
        text(Face.SEMIBOLD, name, x0 + 36f, py0 + 8f, 8.5f, TEXT, Align.LEFT);
        boolean owner = GolemsClient.isOwner(minecraft.getUser().getProfileId());
        text(Face.REGULAR, owner ? "You · Owner" : "You", x0 + 36f, py0 + 20f, 6.5f, owner ? 0xFFE8BA5C : TEXT_2, Align.LEFT);
        boolean muted = data.mutedMusicVolume >= 0;
        smallButton(muted ? "mute" : "volume", x1 - 18f, py0 + 18f, muted ? "Turn the menu music back on" : "Mute the menu music",
                this::toggleMusic, muted);
        smallButton("camera", x1 - 40f, py0 + 18f, "Screenshots folder",
                () -> openFolder(minecraft.gameDirectory.toPath().resolve("screenshots")), false);

        // the Social hint
        float sy1 = py0 - 6f, sy0 = sy1 - 22f;
        boolean overSocial = inside(x0, sy0, x1, sy1);
        float hs = anim("social", overSocial);
        card(x0, sy0, x1, sy1, hs);
        Overlay.icon("chat", (x0 + 9f) * u, (sy0 + 6f) * u, 10f * u, c(TEXT_2));
        text(Face.REGULAR, "Press", x0 + 24f, sy0 + 11f - 7f * 0.6f, 7f, TEXT_2, Align.LEFT);
        float kx = x0 + 24f + textW(Face.REGULAR, "Press", 7f) + 4f;
        float kw = textW(Face.MONO, "ENTER", 5.5f) + 8f;
        rect(kx, sy0 + 5.5f, kx + kw, sy1 - 5.5f, 3f, 0x26FFFFFF);
        text(Face.MONO, "ENTER", kx + kw / 2f, sy0 + 11f - 5.5f * 0.62f, 5.5f, TEXT, Align.CENTER);
        text(Face.REGULAR, "to open Social", kx + kw + 4f, sy0 + 11f - 7f * 0.6f, 7f, TEXT_2, Align.LEFT);
        hit(x0, sy0, x1, sy1, () -> socialOpen = true);

        // FPS and frame time
        float fy1 = sy0 - 6f, fy0 = fy1 - 22f;
        card(x0, fy0, x1, fy1);
        int fps = minecraft.getFps();
        Overlay.icon("zap", (x0 + 9f) * u, (fy0 + 6f) * u, 10f * u, c(ACCENT));
        text(Face.SEMIBOLD, fps + " FPS", x0 + 24f, fy0 + 11f - 7.5f * 0.6f, 7.5f, TEXT, Align.LEFT);
        String ms = fps > 0 ? String.format(java.util.Locale.ROOT, "%.1f ms", 1000f / fps) : "-";
        text(Face.MONO, ms, x1 - 10f, fy0 + 11f - 6.5f * 0.62f, 6.5f, TEXT_2, Align.RIGHT);

        // what's playing
        drawMusic(x0, fy0 - 6f - 46f, x1, fy0 - 6f);
    }

    private void drawMusic(float x0, float y0, float x1, float y1) {
        card(x0, y0, x1, y1);
        MediaSession.Snapshot s = NowPlaying.get();
        float art = 30f, ax = x0 + 8f, ay = y0 + 8f;
        int tex = s != null && s.hasThumbnail() ? albumArt(s) : 0;
        if (tex > 0) {
            Overlay.texture(tex, ax * u, ay * u, (ax + art) * u, (ay + art) * u, 0f, 0f, 1f, 1f, c(0xFFFFFFFF), 4f * u);
        } else {
            rect(ax, ay, ax + art, ay + art, 4f, 0xFF1F1F24);
            Overlay.icon(s == null ? "volume" : "play", (ax + 9f) * u, (ay + 9f) * u, 12f * u, c(TEXT_3));
        }
        float tx = ax + art + 8f;
        float maxText = x1 - 58f - tx;
        if (s == null) {
            text(Face.SEMIBOLD, "Nothing playing", tx, y0 + 10f, 8f, TEXT, Align.LEFT);
            text(Face.REGULAR, fit(Face.REGULAR, "Play music in Spotify, YouTube or any app", 6.5f, x1 - 10f - tx),
                    tx, y0 + 22f, 6.5f, TEXT_2, Align.LEFT);
            return;
        }
        text(Face.SEMIBOLD, fit(Face.SEMIBOLD, s.title(), 8f, maxText), tx, y0 + 8f, 8f, TEXT, Align.LEFT);
        String by = s.artist() == null || s.artist().isBlank() ? "Now playing" : s.artist();
        text(Face.REGULAR, fit(Face.REGULAR, by, 6.5f, maxText), tx, y0 + 19.5f, 6.5f, TEXT_2, Align.LEFT);
        // progress
        float bx0 = tx, bx1 = x1 - 10f, by0 = y1 - 10f;
        rect(bx0, by0, bx1, by0 + 2f, 1f, 0x26FFFFFF);
        if (s.durationMs() > 0) {
            float p = clamp(NowPlaying.position(s) / (float) s.durationMs(), 0f, 1f);
            if (p > 0.005f) rect(bx0, by0, bx0 + (bx1 - bx0) * p, by0 + 2f, 1f, ACCENT);
        }
        // controls
        boolean playing = s.state() == MediaSession.PlaybackState.PLAYING;
        float cy = y0 + 17f;
        float pr = 9f, pcx = x1 - 22f;
        boolean overPlay = inside(pcx - pr, cy - pr, pcx + pr, cy + pr);
        float hp = anim("music:play", overPlay);
        Overlay.circle(pcx * u, cy * u, pr * u, c(mix(ACCENT, 0xFFF0676B, hp)));
        float is = 8f;
        Overlay.icon(playing ? "pause" : "play", (pcx - is / 2f + (playing ? 0f : 0.6f)) * u, (cy - is / 2f) * u, is * u, c(0xFFFFFFFF));
        hit(pcx - pr, cy - pr, pcx + pr, cy + pr, () -> NowPlaying.send(NowPlaying.Command.TOGGLE));
        if (overPlay) tooltip = playing ? "Pause" : "Play";
        smallIcon("next", pcx + pr + 4f, cy, "Next", () -> NowPlaying.send(NowPlaying.Command.NEXT));
        smallIcon("prev", pcx - pr - 12f, cy, "Previous", () -> NowPlaying.send(NowPlaying.Command.PREVIOUS));
    }

    private int albumArtVersion = -1;

    private int albumArt(MediaSession.Snapshot s) {
        if (s.thumbnailVersion() != albumArtVersion) {
            Textures.release("album:" + albumArtVersion);
            albumArtVersion = s.thumbnailVersion();
        }
        return Textures.image("album:" + albumArtVersion, s.thumbnail(), false);
    }

    private void smallIcon(String icon, float x, float cy, String tip, Runnable action) {
        float s = 8f;
        boolean over = inside(x - 2f, cy - 7f, x + s + 2f, cy + 7f);
        float hv = anim("small:" + icon, over);
        Overlay.icon(icon, x * u, (cy - s / 2f) * u, s * u, c(mix(TEXT_2, TEXT, hv)));
        hit(x - 2f, cy - 7f, x + s + 2f, cy + 7f, action);
        if (over) tooltip = tip;
    }

    private void smallButton(String icon, float cx, float cy, String tip, Runnable action, boolean on) {
        float r = 8f;
        boolean over = inside(cx - r, cy - r, cx + r, cy + r);
        float hv = anim("btn:" + icon, over);
        rect(cx - r, cy - r, cx + r, cy + r, 4f, on ? 0x33E5484D : mix(0x14FFFFFF, 0x2EFFFFFF, hv));
        float s = 9f;
        Overlay.icon(icon, (cx - s / 2f) * u, (cy - s / 2f) * u, s * u, c(on ? ACCENT : mix(TEXT_2, TEXT, hv)));
        hit(cx - r, cy - r, cx + r, cy + r, action);
        if (over) tooltip = tip;
    }

    private void toggleMusic() {
        var option = minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        if (data.mutedMusicVolume >= 0) {
            option.set(data.mutedMusicVolume > 0 ? data.mutedMusicVolume : 1.0);
            data.mutedMusicVolume = -1;
        } else {
            data.mutedMusicVolume = option.get();
            option.set(0.0);
        }
        minecraft.options.save();
        data.save();
    }

    // ------------------------------------------------------------------ play card

    private void drawPlayCard() {
        float x1 = w - PAD, x0 = x1 - SIDE_W - 8f;
        float slide = (1f - fade) * 8f;
        float y1 = H - 26f + slide, y0 = y1 - 132f;
        card(x0, y0, x1, y1);

        // Singleplayer / Multiplayer
        float sx0 = x0 + 8f, sx1 = x1 - 8f, sy0 = y0 + 8f, sy1 = y0 + 26f, mid = (sx0 + sx1) / 2f;
        rect(sx0, sy0, sx1, sy1, 5f, WELL);
        float pos = anim("mode", !data.singleplayer);
        float px = sx0 + 2f + (mid - sx0) * pos;
        rect(px, sy0 + 2f, px + (mid - sx0) - 2f, sy1 - 2f, 4f, ACCENT);
        text(Face.MEDIUM, "Singleplayer", (sx0 + mid) / 2f, sy0 + 9f - 7f * 0.6f, 7f, mix(TEXT, TEXT_2, pos), Align.CENTER);
        text(Face.MEDIUM, "Multiplayer", (mid + sx1) / 2f, sy0 + 9f - 7f * 0.6f, 7f, mix(TEXT_2, TEXT, pos), Align.CENTER);
        hit(sx0, sy0, mid, sy1, () -> setMode(true));
        hit(mid, sy0, sx1, sy1, () -> setMode(false));

        float cy0 = y0 + 34f;
        float bx0 = x0 + 8f, bx1 = x1 - 8f, by0 = y0 + 80f, by1 = y0 + 106f;
        if (data.singleplayer) drawWorld(x0, cy0, x1, bx0, by0, bx1, by1, y1);
        else drawServer(x0, cy0, x1, bx0, by0, bx1, by1, y1);
    }

    private void setMode(boolean singleplayer) {
        if (data.singleplayer == singleplayer) return;
        data.singleplayer = singleplayer;
        data.save();
    }

    private void drawServer(float x0, float cy0, float x1, float bx0, float by0, float bx1, float by1, float y1) {
        List<MenuData.Server> recent = data.servers;
        if (recent.isEmpty()) {
            text(Face.BOLD, "No server yet", x0 + 10f, cy0 + 2f, 10f, TEXT, Align.LEFT);
            text(Face.REGULAR, "Join one and it shows up here for next time.", x0 + 10f, cy0 + 18f, 6.5f, TEXT_2, Align.LEFT);
            playButton(bx0, by0, bx1, by1, "Find a server", this::openServers);
        } else {
            pickServer = Math.min(pickServer, recent.size() - 1);
            MenuData.Server pick = recent.get(pickServer);
            ServerData info = ping.show(pick.name, pick.ip);
            float ic = 34f;
            serverIcon(info.getIconBytes(), pick, x0 + 10f, cy0, ic);
            float tx = x0 + 10f + ic + 9f;
            text(Face.BOLD, fit(Face.BOLD, info.name, 10f, x1 - 10f - tx), tx, cy0 + 1f, 10f, TEXT, Align.LEFT);
            String status;
            int dot;
            if (ping.waiting()) {
                status = "Pinging...";
                dot = TEXT_3;
            } else if (info.players != null) {
                status = String.format(java.util.Locale.ROOT, "%,d online", info.players.online())
                        + (info.ping >= 0 ? " · " + info.ping + " ms" : "");
                dot = LIVE;
            } else {
                status = "Can't reach it";
                dot = ACCENT;
            }
            Overlay.circle((tx + 2f) * u, (cy0 + 18.5f) * u, 2f * u, c(dot));
            text(Face.MONO, status, tx + 7f, cy0 + 15f, 6.5f, TEXT_2, Align.LEFT);
            text(Face.REGULAR, "Last played " + ago(pick.lastPlayed), tx, cy0 + 26f, 6.5f, TEXT_3, Align.LEFT);
            playButton(bx0, by0, bx1, by1, "Play", () -> join(pick));
        }
        recentRow(x0, x1, y1, recent.size(), pickServer, i -> {
            pickServer = i;
        }, i -> serverIcon(ping.savedIcon(recent.get(i).ip), recent.get(i), 0f, 0f, 0f), "All servers", this::openServers);
    }

    private void drawWorld(float x0, float cy0, float x1, float bx0, float by0, float bx1, float by1, float y1) {
        if (worlds.isEmpty()) {
            text(Face.BOLD, "No worlds yet", x0 + 10f, cy0 + 2f, 10f, TEXT, Align.LEFT);
            text(Face.REGULAR, "Make one to start playing.", x0 + 10f, cy0 + 18f, 6.5f, TEXT_2, Align.LEFT);
            playButton(bx0, by0, bx1, by1, "Create a world", () -> minecraft.setScreen(new SelectWorldScreen(this)));
        } else {
            pickWorld = Math.min(pickWorld, worlds.size() - 1);
            LevelSummary pick = worlds.get(pickWorld);
            float ic = 34f;
            worldIcon(pick, x0 + 10f, cy0, ic);
            float tx = x0 + 10f + ic + 9f;
            text(Face.BOLD, fit(Face.BOLD, pick.getLevelName(), 10f, x1 - 10f - tx), tx, cy0 + 1f, 10f, TEXT, Align.LEFT);
            text(Face.MONO, fit(Face.MONO, pick.getLevelId(), 6.5f, x1 - 10f - tx), tx, cy0 + 15f, 6.5f, TEXT_2, Align.LEFT);
            text(Face.REGULAR, "Last played " + ago(pick.getLastPlayed()), tx, cy0 + 26f, 6.5f, TEXT_3, Align.LEFT);
            playButton(bx0, by0, bx1, by1, "Play", () -> openWorld(pick));
        }
        recentRow(x0, x1, y1, worlds.size(), pickWorld, i -> {
            pickWorld = i;
        }, i -> worldIcon(worlds.get(i), 0f, 0f, 0f), "All worlds", () -> minecraft.setScreen(new SelectWorldScreen(this)));
    }

    /** "Recent" with a small icon per recent server/world (click to pick it), and a link to all of them. */
    private void recentRow(float x0, float x1, float y1, int count, int picked, java.util.function.IntConsumer pick,
                           java.util.function.IntConsumer icon, String all, Runnable openAll) {
        float cy = y1 - 13f;
        text(Face.REGULAR, "Recent", x0 + 10f, cy - 6.5f * 0.6f, 6.5f, TEXT_3, Align.LEFT);
        float ix = x0 + 10f + textW(Face.REGULAR, "Recent", 6.5f) + 7f;
        float s = 13f;
        for (int i = 0; i < Math.min(count, 4); i++) {
            iconAt = new float[] {ix, cy - s / 2f, s};
            icon.accept(i);
            boolean over = inside(ix - 1f, cy - s / 2f - 1f, ix + s + 1f, cy + s / 2f + 1f);
            if (i == picked) outline(ix - 1.5f, cy - s / 2f - 1.5f, ix + s + 1.5f, cy + s / 2f + 1.5f, 4f, ACCENT);
            else if (over) outline(ix - 1.5f, cy - s / 2f - 1.5f, ix + s + 1.5f, cy + s / 2f + 1.5f, 4f, 0x66FFFFFF);
            final int index = i;
            hit(ix - 1f, cy - s / 2f - 1f, ix + s + 1f, cy + s / 2f + 1f, () -> pick.accept(index));
            ix += s + 5f;
        }
        iconAt = null;
        String label = all + "  →";
        float lw = textW(Face.MEDIUM, label, 6.5f) + 14f;
        float lx1 = x1 - 8f, lx0 = lx1 - lw;
        boolean over = inside(lx0, cy - 8f, lx1, cy + 8f);
        float hv = anim("all", over);
        rect(lx0, cy - 8f, lx1, cy + 8f, 4f, mix(0x0FFFFFFF, 0x26FFFFFF, hv));
        outline(lx0, cy - 8f, lx1, cy + 8f, 4f, LINE);
        text(Face.MEDIUM, label, (lx0 + lx1) / 2f, cy - 6.5f * 0.6f, 6.5f, mix(TEXT_2, TEXT, hv), Align.CENTER);
        hit(lx0, cy - 8f, lx1, cy + 8f, openAll);
    }

    /** Where the recent row wants the next icon (x, y, size); null when drawing the big one. */
    private float[] iconAt;

    private void serverIcon(byte[] png, MenuData.Server server, float x, float y, float s) {
        if (iconAt != null) {
            x = iconAt[0];
            y = iconAt[1];
            s = iconAt[2];
        }
        int tex = 0;
        if (png != null) tex = Textures.image("server:" + server.ip + ":" + java.util.Arrays.hashCode(png), png, true);
        if (tex > 0) {
            Overlay.texture(tex, x * u, y * u, (x + s) * u, (y + s) * u, 0f, 0f, 1f, 1f, c(0xFFFFFFFF), Math.min(6f, s * 0.2f) * u);
        } else {
            letterTile(server.name, x, y, s);
        }
    }

    private void worldIcon(LevelSummary world, float x, float y, float s) {
        if (iconAt != null) {
            x = iconAt[0];
            y = iconAt[1];
            s = iconAt[2];
        }
        String key = "world:" + world.getLevelId();
        int tex;
        if (Textures.has(key)) {
            tex = Textures.image(key, null, true);
        } else {
            byte[] png = null;
            try {
                Path icon = world.getIcon();
                if (icon != null && Files.isRegularFile(icon) && Files.size(icon) < 1_000_000) png = Files.readAllBytes(icon);
            } catch (Exception ignored) {
            }
            tex = Textures.image(key, png, true);
        }
        if (tex > 0) {
            Overlay.texture(tex, x * u, y * u, (x + s) * u, (y + s) * u, 0f, 0f, 1f, 1f, c(0xFFFFFFFF), Math.min(6f, s * 0.2f) * u);
        } else {
            letterTile(world.getLevelName(), x, y, s);
        }
    }

    private void letterTile(String name, float x, float y, float s) {
        rect(x, y, x + s, y + s, Math.min(6f, s * 0.2f), 0xFF26262B);
        String letter = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        float size = s * 0.5f;
        text(Face.BOLD, letter, x + s / 2f, y + s / 2f - size * 0.6f, size, TEXT_2, Align.CENTER);
    }

    private void playButton(float x0, float y0, float x1, float y1, String label, Runnable action) {
        boolean over = inside(x0, y0, x1, y1);
        float hv = anim("play:" + label, over);
        rect(x0, y0, x1, y1, 6f, mix(ACCENT, 0xFFF0676B, hv));
        text(Face.BOLD, label, (x0 + x1) / 2f, (y0 + y1) / 2f - 10f * 0.6f, 10f, 0xFFFFFFFF, Align.CENTER);
        hit(x0, y0, x1, y1, action);
    }

    // ------------------------------------------------------------------ actions

    private void join(MenuData.Server server) {
        if (!minecraft.allowsMultiplayer()) {
            showToast("Multiplayer is turned off for this account.");
            return;
        }
        if (!minecraft.options.skipMultiplayerWarning) {
            minecraft.setScreen(new SafetyScreen(this));
            return;
        }
        ServerData info = ping.show(server.name, server.ip);
        ServerData target = new ServerData(info.name, server.ip, ServerData.Type.OTHER);
        if (info.getIconBytes() != null) target.setIconBytes(info.getIconBytes());
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(server.ip), target, false, null);
    }

    private void openServers() {
        minecraft.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
    }

    private void openWorld(LevelSummary world) {
        minecraft.createWorldOpenFlows().openWorld(world.getLevelId(), () -> minecraft.setScreen(new MainMenu()));
    }

    private void playSelected() {
        if (data.singleplayer) {
            if (!worlds.isEmpty()) openWorld(worlds.get(Math.min(pickWorld, worlds.size() - 1)));
            else minecraft.setScreen(new SelectWorldScreen(this));
        } else if (!data.servers.isEmpty()) {
            join(data.servers.get(Math.min(pickServer, data.servers.size() - 1)));
        } else {
            openServers();
        }
    }

    private static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toFile());
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't open {}", dir, e);
        }
    }

    private void showToast(String message) {
        toast = message;
        toastAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ footer, toast, Social, tooltip

    private void drawFooter() {
        String left = "GOLEM'S CLIENT " + GolemsClient.version().toUpperCase(java.util.Locale.ROOT) + "  ·  MINECRAFT "
                + SharedConstants.getCurrentVersion().name().toUpperCase(java.util.Locale.ROOT) + "  ·  FABRIC";
        text(Face.MONO, left, PAD, H - 15f, 5.5f, TEXT_3, Align.LEFT);
        String right = "Copyright Mojang AB. Do not distribute!";
        float rw = textW(Face.REGULAR, right, 6f);
        boolean over = inside(w - PAD - rw, H - 17f, w - PAD, H - 6f);
        text(Face.REGULAR, right, w - PAD, H - 15.5f, 6f, over ? TEXT : TEXT_3, Align.RIGHT);
        hit(w - PAD - rw, H - 17f, w - PAD, H - 6f, () -> minecraft.setScreen(new CreditsAndAttributionScreen(this)));
    }

    private void drawToast() {
        if (toast == null) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > 3200L) {
            toast = null;
            return;
        }
        float a = Math.min(1f, Math.min(age / 150f, (3200L - age) / 300f));
        float tw = textW(Face.MEDIUM, toast, 7f) + 24f;
        float y0 = Announcement.get() != null && !Announcement.get().id().equals(data.dismissedAnnouncement) ? 82f : 48f;
        y0 += (1f - a) * -4f;
        rect(w / 2f - tw / 2f, y0, w / 2f + tw / 2f, y0 + 20f, 5f, alpha(CARD_HI, a));
        outline(w / 2f - tw / 2f, y0, w / 2f + tw / 2f, y0 + 20f, 5f, alpha(LINE, a));
        text(Face.MEDIUM, toast, w / 2f, y0 + 10f - 7f * 0.6f, 7f, alpha(TEXT, a), Align.CENTER);
    }

    private void drawSocial() {
        float a = anim("socialPanel", socialOpen);
        hits.clear(); // the panel takes all clicks
        rect(0f, 0f, w, H, 0f, alpha(0x99000000, a));
        float pw = 260f, ph = 120f, x0 = w / 2f - pw / 2f, y0 = H / 2f - ph / 2f + (1f - a) * 8f;
        rect(x0, y0, x0 + pw, y0 + ph, 8f, alpha(0xF5141417, a));
        outline(x0, y0, x0 + pw, y0 + ph, 8f, alpha(LINE, a));
        text(Face.BOLD, "Social", x0 + 16f, y0 + 14f, 11f, alpha(TEXT, a), Align.LEFT);
        text(Face.REGULAR, "Friends, chat and sharing are coming in a later update.", x0 + 16f, y0 + 34f, 7f, alpha(TEXT_2, a), Align.LEFT);
        text(Face.REGULAR, "Until then, come hang out:", x0 + 16f, y0 + 46f, 7f, alpha(TEXT_2, a), Align.LEFT);
        float bx = x0 + 16f, by0 = y0 + 66f, by1 = y0 + 88f;
        bx = linkButton("discord", "Discord", bx, by0, by1, a, () -> Util.getPlatform().openUri(DISCORD));
        linkButton("tiktok", "@holygrajl", bx + 6f, by0, by1, a, () -> Util.getPlatform().openUri(TIKTOK));
        text(Face.REGULAR, "Esc or click outside to close", x0 + 16f, y0 + ph - 18f, 6f, alpha(TEXT_3, a), Align.LEFT);
        hits.add(0, new Hit(x0, y0, x0 + pw, y0 + ph, () -> { }));
        hits.add(new Hit(0f, 0f, w, H, () -> socialOpen = false));
    }

    private float linkButton(String icon, String label, float x0, float y0, float y1, float a, Runnable action) {
        float lw = textW(Face.MEDIUM, label, 7.5f) + 34f;
        boolean over = inside(x0, y0, x0 + lw, y1);
        float hv = anim("link:" + icon, over);
        rect(x0, y0, x0 + lw, y1, 5f, alpha(mix(0x14FFFFFF, 0x2EFFFFFF, hv), a));
        Overlay.icon(icon, (x0 + 9f) * u, ((y0 + y1) / 2f - 5f) * u, 10f * u, c(alpha(TEXT, a)));
        text(Face.MEDIUM, label, x0 + 24f, (y0 + y1) / 2f - 7.5f * 0.6f, 7.5f, alpha(TEXT, a), Align.LEFT);
        hits.add(0, new Hit(x0, y0, x0 + lw, y1, action));
        if (over) pointer = true;
        return x0 + lw;
    }

    private void drawTooltip() {
        if (tooltip == null || socialOpen) return;
        float tw = textW(Face.MEDIUM, tooltip, 6.5f) + 12f;
        float x = clamp(mx - tw / 2f, 4f, w - tw - 4f), y = my + 14f;
        if (y + 16f > H) y = my - 22f;
        rect(x, y, x + tw, y + 15f, 4f, 0xF20E0E10);
        outline(x, y, x + tw, y + 15f, 4f, LINE);
        text(Face.MEDIUM, tooltip, x + tw / 2f, y + 7.5f - 6.5f * 0.6f, 6.5f, TEXT, Align.CENTER);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        float x = (float) event.x() / u, y = (float) event.y() / u;
        for (Hit h : hits) {
            if (h.inside(x, y)) {
                h.action().run();
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f, 0.35f));
                return true;
            }
        }
        float[] b = playerBox();
        if (!socialOpen && x >= b[0] && x < b[2] && y >= b[1] && y < b[3]) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            playerYaw += (float) dx * 2.5f / Math.max(0.5f, u);
            playerPitch = clamp(playerPitch - (float) dy * 2.5f / Math.max(0.5f, u), -40f, 40f);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (socialOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                socialOpen = false;
                return true;
            }
            return super.keyPressed(event);
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            socialOpen = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) {
            playSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------------ drawing helpers (units)

    private void drawHead(float x, float y, float s, float radius) {
        int tex = skinTexture();
        if (tex > 0) {
            float r = radius * u;
            Overlay.texture(tex, x * u, y * u, (x + s) * u, (y + s) * u, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, c(0xFFFFFFFF), r);
            Overlay.texture(tex, x * u, y * u, (x + s) * u, (y + s) * u, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, c(0xFFFFFFFF), r);
        } else {
            letterTile(minecraft.getUser().getName(), x, y, s);
        }
    }

    /** The GL texture of your skin, or 0 while it loads. */
    private int skinTexture() {
        try {
            var texture = minecraft.getTextureManager().getTexture(skin.get().body().texturePath());
            GpuTexture gpu = texture.getTexture();
            return gpu instanceof GlTexture gl && !gl.isClosed() ? gl.glId() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void card(float x0, float y0, float x1, float y1) {
        card(x0, y0, x1, y1, 0f);
    }

    private void card(float x0, float y0, float x1, float y1, float hover) {
        rect(x0, y0, x1, y1, 8f, mix(CARD, CARD_HI, hover));
        outline(x0, y0, x1, y1, 8f, mix(LINE, 0x33FFFFFF, hover));
    }

    private void rect(float x0, float y0, float x1, float y1, float r, int argb) {
        Overlay.rect(x0 * u, y0 * u, x1 * u, y1 * u, r * u, c(argb));
    }

    private void outline(float x0, float y0, float x1, float y1, float r, int argb) {
        Overlay.outline(x0 * u, y0 * u, x1 * u, y1 * u, r * u, c(argb), 1f / Math.max(1f, guiScale()));
    }

    private void text(Face face, String s, float x, float y, float size, int argb, Align align) {
        Overlay.text(face, s, x * u, y * u, size * u, c(argb), align);
    }

    private float textW(Face face, String s, float size) {
        return Overlay.width(face, s, size * u) / u;
    }

    /** Shortens text with "..." to fit maxW units. */
    private String fit(Face face, String s, float size, float maxW) {
        if (s == null) return "";
        if (textW(face, s, size) <= maxW) return s;
        String cut = s;
        while (cut.length() > 1 && textW(face, cut + "...", size) > maxW) cut = cut.substring(0, cut.length() - 1);
        return cut.strip() + "...";
    }

    private float guiScale() {
        return (float) minecraft.getWindow().getGuiScale();
    }

    private void hit(float x0, float y0, float x1, float y1, Runnable action) {
        hits.add(new Hit(x0, y0, x1, y1, action));
        if (inside(x0, y0, x1, y1)) pointer = true;
    }

    private boolean inside(float x0, float y0, float x1, float y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    /** Eases a 0..1 value toward on/off (hover, open), about a tenth of a second. */
    private float anim(String id, boolean on) {
        float v = anims.getOrDefault(id, on && id.equals("mode") ? 1f : 0f);
        v = approach(v, on ? 1f : 0f, 14f);
        anims.put(id, v);
        return v;
    }

    private float approach(float from, float to, float speed) {
        float k = 1f - (float) Math.exp(-dt * speed);
        float v = from + (to - from) * k;
        return Math.abs(to - v) < 0.002f ? to : v;
    }

    /** The opening fade applied to a colour. */
    private int c(int argb) {
        return alpha(argb, fade);
    }

    static int alpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        return (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24) | (argb & 0xFFFFFF);
    }

    static int mix(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** "just now", "5 min ago", "2 h ago", "3 days ago" for a time in milliseconds. */
    static String ago(long millis) {
        long s = Math.max(0L, (System.currentTimeMillis() - millis) / 1000L);
        if (millis <= 0L) return "a while ago";
        if (s < 60L) return "just now";
        if (s < 3600L) return (s / 60L) + " min ago";
        if (s < 86400L) return (s / 3600L) + " h ago";
        long d = s / 86400L;
        return d == 1L ? "yesterday" : d + " days ago";
    }
}
