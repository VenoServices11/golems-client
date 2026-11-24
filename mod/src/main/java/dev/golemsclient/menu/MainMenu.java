package dev.golemsclient.menu;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.golemsclient.GolemsClient;
import dev.golemsclient.media.MediaSession;
import dev.golemsclient.oc.LobbyBridge;
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
public final class MainMenu extends MenuScreen {
    // ------------------------------------------------------------------ look (the launcher's Iron Dark)
    private static final int SPLASH = 0xFFFFE45C;
    private static final String DISCORD = "https://discord.gg/q67qzHG2kf";
    private static final String TIKTOK = "https://www.tiktok.com/@holygrajl";
    private static final String[] TABS = {"Play", "Wardrobe", "Stats", "News", "Screenshots"};

    private static final float SIDE_W = 196f;

    // ------------------------------------------------------------------ state
    private final MenuData data = MenuData.get();
    private final Splash splash = new Splash();
    private ServerPing ping;
    private PlayerModel wide;
    private PlayerModel slim;
    private List<LevelSummary> worlds = List.of();
    private boolean worldsLoaded;
    private int pickServer;
    private int pickWorld;


    private float playerYaw = 16f;
    private float playerPitch = -4f;
    private boolean dragging;
    private float tabX = -1f, tabW;
    // where OneClient's startup animation ends (GUI pixels: x, y, width, height)
    private float[] startupMark, startupWord, startupLine;
    // which tab is showing: 0 Play, 1 Wardrobe
    private int page;
    private final WardrobePage wardrobe = new WardrobePage(this);
    // pressing on your player in the scene: a short hold or a drag picks it up, a click makes it react
    private boolean pressingPlayer, grabbing;
    private long pressedAt;
    private double pressX, pressY;
    private float sceneScroll;
    private boolean socialOpen;
    private boolean lobbyPanel;
    private boolean draggingVolume;
    private float volumeX0, volumeX1;

    public MainMenu() {
        super(Component.literal("Golem's Client"));
    }

    // dev screenshots only
    private static boolean devSocial;
    private static int devTab = -1;

    public static void devSocial(boolean open) {
        devSocial = open;
    }

    public static void devTab(int tab) {
        devTab = tab;
    }

    public static void devSingleplayer(boolean on) {
        MenuData.get().singleplayer = on;
    }

    /** Off with -Dgolemsclient.vanillaMenu=true (for testing). */
    public static boolean enabled() {
        return !Boolean.getBoolean("golemsclient.vanillaMenu");
    }

    @Override
    protected void init() {
        super.init();
        if (ping == null) ping = new ServerPing();
        if (wide == null) {
            var models = minecraft.getEntityModels();
            wide = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        if (!worldsLoaded) {
            worldsLoaded = true;
            loadWorlds();
        }
        LobbyBridge.arrive();
    }

    /** Whether OneClient's lobby (the scene behind the menu) is in use. */
    private static boolean lobby() {
        return !LobbyBridge.cannotRun();
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
        if (pressingPlayer && !grabbing && System.currentTimeMillis() - pressedAt >= 250L) grabbing = LobbyBridge.grab(pressX, pressY);
        String notice = SkinService.takeNotice();
        if (notice != null) showToast(notice);
    }

    private void setPage(int next) {
        if (next == page) return;
        if (page == 1) wardrobe.close();
        page = next;
        lobbyPanel = false;
        if (page == 1) wardrobe.open();
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (page != 1) setPage(1);
        wardrobe.drop(files);
    }

    @Override
    public void removed() {
        if (page == 1) wardrobe.close();
        if (ping != null) ping.stop();
        for (LevelSummary s : worlds) Textures.release("world:" + s.getLevelId());
    }

    @Override
    public net.minecraft.sounds.Music getBackgroundMusic() {
        return net.minecraft.sounds.Musics.MENU;
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
        if (lobby()) {
            // OneClient's lobby draws the scene behind the menu (no world is loaded); until it shows, a plain page
            if (!LobbyBridge.frame()) {
                g.fill(0, 0, width, height, GolemsClient.BACKGROUND);
                return;
            }
            // only a soft shade at the top and bottom, so the bar and the cards read well
            g.fillGradient(0, 0, width, height / 4, 0x80000000, 0x00000000);
            g.fillGradient(0, height * 3 / 5, width, height, 0x00000000, 0x66000000);
            return;
        }
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
        beginFrame(g, mouseX, mouseY);
        // the panels fade in as the scene finishes revealing itself, and out while the camera leaves it
        if (lobby()) fade = Math.min(fade, LobbyBridge.panelAlpha());
        LobbyBridge.accent(ACCENT);
        if (dev.golemsclient.DevShots.ON) {
            socialOpen = devSocial;
            if (devTab >= 0) {
                openTab(devTab);
                devTab = -1;
            }
        }

        if (page == 1) {
            // the Wardrobe: the camera is close on your player, so no name tag or splash over it
            drawTopBar();
            wardrobe.draw();
        } else {
            drawPlayer(g);
            drawSplash(g);
            drawTopBar();
            drawAnnouncement();
            drawLeft();
            if (lobbyPanel && lobby()) drawLobbyPanel();
            else drawPlayCard();
        }
        drawFooter();
        if (socialOpen) drawSocial();
        endFrame(g);
        // OneClient's startup: the logo over the loading screen flies into the top bar as the scene appears
        LobbyBridge.startupFrame(g);
    }

    @Override
    protected boolean modalOpen() {
        return socialOpen;
    }

    @Override
    protected float toastY() {
        Announcement.Post post = Announcement.get();
        return post != null && !post.id().equals(data.dismissedAnnouncement) ? 82f : 48f;
    }

    // ------------------------------------------------------------------ player

    /** Where your player is on screen (units): in the lobby, the real one in the world. */
    private float[] playerBox() {
        if (lobby()) {
            // the scene's player: its name tag marks where it is
            LobbyBridge.Tag tag = localTag();
            if (tag != null) {
                float cx = tag.x / u, top = tag.y / u + 6f, ph = 70f;
                return new float[] {cx - ph * 0.3f, top, cx + ph * 0.3f, top + ph};
            }
        }
        float ph = Math.min(H * 0.42f, 200f);
        float feet = H * 0.72f;
        float pw = ph * 0.62f;
        return new float[] {w / 2f - pw / 2f, feet - ph, w / 2f + pw / 2f, feet};
    }

    private void drawPlayer(GuiGraphics g) {
        float[] b = playerBox();
        if (lobby()) {
            // the players stand in the scene (they look at the mouse themselves): a name tag over each
            var window = minecraft.getWindow();
            for (LobbyBridge.Tag tag : LobbyBridge.tags(window.getGuiScaledWidth(), window.getGuiScaledHeight())) {
                tagAt(tag.name, tag.x / u, tag.y / u, tag.local);
            }
            if (!socialOpen && !grabbing && LobbyBridge.touches(mx * u / width, my * u / height)) pointer = true;
            return;
        }
        PlayerSkin s = skin().get();
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

        nametag(cx, b[1]);
        if (inside(b[0], b[1], b[2], b[3])) pointer = true;
    }

    private LobbyBridge.Tag localTag() {
        var window = minecraft.getWindow();
        for (LobbyBridge.Tag tag : LobbyBridge.tags(window.getGuiScaledWidth(), window.getGuiScaledHeight())) {
            if (tag.local) return tag;
        }
        return null;
    }

    /** A name tag in the scene, its bottom middle at (cx, bottom) in units; "You" on yours. */
    private void tagAt(String name, float cx, float bottom, boolean local) {
        float nameSize = 8f, youSize = 6.5f;
        float nw = textW(Face.SEMIBOLD, name, nameSize), yw = local ? textW(Face.MEDIUM, "You", youSize) + 5f : 0f;
        float tw = nw + yw + 14f, ty = bottom - 16f;
        boolean focused = name.equals(LobbyBridge.focused());
        rect(cx - tw / 2f, ty, cx + tw / 2f, ty + 16f, 4f, 0xB40E0E10);
        outline(cx - tw / 2f, ty, cx + tw / 2f, ty + 16f, 4f, local || focused ? alpha(ACCENT, 0.8f) : LINE);
        text(Face.SEMIBOLD, name, cx - tw / 2f + 7f, ty + 8f - nameSize * 0.6f, nameSize, TEXT, Align.LEFT);
        if (local) text(Face.MEDIUM, "You", cx + tw / 2f - 7f, ty + 8f - youSize * 0.6f, youSize, TEXT_2, Align.RIGHT);
    }

    /** Your name in a small pill over your head (top = the top of your head, units). */
    private void nametag(float cx, float top) {
        String name = minecraft.getUser().getName();
        float nameSize = 8f, youSize = 6.5f;
        float nw = textW(Face.SEMIBOLD, name, nameSize), yw = textW(Face.MEDIUM, "You", youSize);
        float tw = nw + 5f + yw + 14f, ty = top - 22f;
        rect(cx - tw / 2f, ty, cx + tw / 2f, ty + 16f, 4f, 0xB40E0E10);
        outline(cx - tw / 2f, ty, cx + tw / 2f, ty + 16f, 4f, LINE);
        text(Face.SEMIBOLD, name, cx - tw / 2f + 7f, ty + 8f - nameSize * 0.6f, nameSize, TEXT, Align.LEFT);
        text(Face.MEDIUM, "You", cx + tw / 2f - 7f, ty + 8f - youSize * 0.6f, youSize, TEXT_2, Align.RIGHT);
    }

    private void drawSplash(GuiGraphics g) {
        if (minecraft.options.hideSplashTexts().get()) return;
        float[] b = playerBox();
        if (lobby()) {
            LobbyBridge.Tag tag = localTag();
            if (tag == null) return;
            splash.draw(g, (tag.x / u + 100f) * u, (tag.y / u + 14f) * u, u, fade);
            return;
        }
        splash.draw(g, (b[2] + (b[2] - b[0]) * 0.62f) * u, (b[1] + (b[3] - b[1]) * 0.02f) * u, u, fade);
    }

    // ------------------------------------------------------------------ top bar

    private void drawTopBar() {
        float y = 14f;
        // logo and name
        Overlay.image("/assets/golemsclient/textures/logo.png", PAD * u, y * u, (PAD + 18f) * u, (y + 18f) * u, c(0xFFFFFFFF));
        float nameH = 8.5f, nameW = nameH * (1130f / 128f);
        Overlay.image("/assets/golemsclient/textures/wordmark.png", (PAD + 25f) * u, (y + 9f - nameH / 2f) * u,
                (PAD + 25f + nameW) * u, (y + 9f + nameH / 2f) * u, c(TEXT));
        startupMark = new float[] {PAD * u, y * u, 18f * u, 18f * u};
        startupWord = new float[] {(PAD + 25f) * u, (y + 9f - nameH / 2f) * u, nameW * u, nameH * u};

        // tabs
        float size = 8.5f, gap = 22f;
        float total = -gap;
        for (String tab : TABS) total += textW(Face.MEDIUM, tab, size) + gap;
        float x = w / 2f - total / 2f;
        for (int i = 0; i < TABS.length; i++) {
            String tab = TABS[i];
            float tw = textW(Face.MEDIUM, tab, size);
            boolean active = i == page;
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
        // where OneClient's startup logo, name and line land: our logo, name and the Play tab's underline
        startupLine = new float[] {tabX * u, (y + 18f) * u, tabW * u, 1.5f * u};
        if (startupMark != null) LobbyBridge.startupTargets(width, startupMark, startupWord, startupLine);

        // account, then quick buttons to its left
        float head = 18f;
        float hx = w - PAD - head;
        drawHead(hx, y, head, 4f);
        String name = minecraft.getUser().getName();
        text(Face.SEMIBOLD, name, hx - 8f, y + 9f - 8.5f * 0.6f, 8.5f, TEXT, Align.RIGHT);
        float ix = hx - 8f - textW(Face.SEMIBOLD, name, 8.5f) - 18f;
        ix = iconButton("quit", ix, y + 9f, "Quit game", () -> minecraft.stop());
        ix = iconButton("settings", ix, y + 9f, "Options", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        if (lobby()) ix = iconButton("image", ix, y + 9f, "Customize your lobby", () -> lobbyPanel = !lobbyPanel);
        ix = iconButton("cloud", ix, y + 9f, "Realms", () -> minecraft.setScreen(new com.mojang.realmsclient.RealmsMainScreen(this)));
        Announcement.Post post = Announcement.get();
        if (post != null && post.id().equals(data.dismissedAnnouncement)) {
            iconButton("bell", ix, y + 9f, "Show the announcement", () -> {
                data.dismissedAnnouncement = "";
                data.save();
            });
        }
    }


    private void openTab(int index) {
        switch (index) {
            case 0 -> setPage(0);
            case 1 -> setPage(1);
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
        // the lobby's own music too
        LobbyBridge.setMusicMuted(data.mutedMusicVolume >= 0);
        data.save();
    }

    // ------------------------------------------------------------------ play card

    // ------------------------------------------------------------------ customize your lobby

    private void drawLobbyPanel() {
        float x1 = w - PAD, x0 = x1 - SIDE_W - 8f;
        float slide = (1f - anim("lobbyPanel", true)) * 10f;
        float y0 = 50f + slide, y1 = H - 26f;
        card(x0, y0, x1, y1);
        float x = x0 + 12f, y = y0 + 12f, iw = x1 - x0 - 24f;
        text(Face.BOLD, "Customize your lobby", x, y, 9.5f, TEXT, Align.LEFT);
        closeButton(x1 - 18f, y + 5f, () -> lobbyPanel = false);
        y += 22f;

        // the scene: Automatic, the built-in ones, then the places you've played (scrolls when there are many)
        label("SCENE", x, y);
        y += 10f;
        List<LobbyBridge.Scene> scenes = LobbyBridge.scenes();
        String picked = LobbyBridge.pickedScene();
        int rows = 4, count = scenes.size() + 1;
        float rowH = 27f, listH = Math.min(count, rows) * rowH;
        sceneListY0 = y;
        sceneListY1 = y + listH;
        sceneScroll = clamp(sceneScroll, 0f, Math.max(0f, count - rows));
        int first = (int) sceneScroll;
        for (int i = first; i < Math.min(count, first + rows); i++) {
            if (i == 0) {
                y = option(x, y, iw, "Automatic", "A different scene each time", picked == null, () -> LobbyBridge.pickScene(null));
            } else {
                LobbyBridge.Scene e = scenes.get(i - 1);
                String sub = e.builtIn ? "Built in" : "From a place you played";
                if (e.id.equals(LobbyBridge.currentScene())) sub += " \u00b7 showing now";
                y = option(x, y, iw, e.name, sub, e.id.equals(picked), () -> LobbyBridge.pickScene(e.id));
            }
        }
        if (count > rows) {
            // a thin scrollbar beside the list
            float track = listH - 3f, bar = track * rows / count, at = track * first / count;
            rect(x1 - 6f, sceneListY0 + at, x1 - 4.5f, sceneListY0 + at + bar, 0.75f, 0x40FFFFFF);
        }
        y = sceneListY0 + listH + 6f;

        label("TIME OF DAY", x, y);
        y += 10f;
        y = chips(x, y, iw, new Object[] {dev.golemsclient.oc.client.lobby.LobbyTime.CAPTURED, dev.golemsclient.oc.client.lobby.LobbyTime.DAY,
                dev.golemsclient.oc.client.lobby.LobbyTime.SUNSET, dev.golemsclient.oc.client.lobby.LobbyTime.NIGHT,
                dev.golemsclient.oc.client.lobby.LobbyTime.REAL}, new String[] {"Scene", "Day", "Sunset", "Night", "Clock"},
                LobbyBridge.time(), v -> LobbyBridge.setTime((dev.golemsclient.oc.client.lobby.LobbyTime) v));

        label("OPENING", x, y);
        y += 10f;
        y = chips(x, y, iw, LobbyBridge.reveals(), new String[] {"Random", "Wave", "Focus pull", "First light"},
                LobbyBridge.reveal(), v -> LobbyBridge.setReveal((dev.golemsclient.oc.client.lobby.LobbyReveal) v));

        // music: which track (arrows step through them) and how loud
        label("MUSIC", x, y);
        y += 10f;
        var musics = LobbyBridge.musics();
        var music = LobbyBridge.music();
        int mi = java.util.Arrays.asList(musics).indexOf(music);
        rect(x, y, x + iw, y + 18f, 4f, 0x0FFFFFFF);
        text(Face.MEDIUM, music.toString(), x + iw / 2f, y + 9f - 7f * 0.6f, 7f, TEXT, Align.CENTER);
        smallArrow(x + 9f, y + 9f, false, () -> LobbyBridge.setMusic(musics[(mi + musics.length - 1) % musics.length]));
        smallArrow(x + iw - 9f, y + 9f, true, () -> LobbyBridge.setMusic(musics[(mi + 1) % musics.length]));
        y += 24f;
        volumeX0 = x;
        volumeX1 = x + iw - 34f;
        float v = LobbyBridge.musicVolume();
        rect(volumeX0, y + 5f, volumeX1, y + 7f, 1f, 0x26FFFFFF);
        rect(volumeX0, y + 5f, volumeX0 + (volumeX1 - volumeX0) * v, y + 7f, 1f, ACCENT);
        Overlay.rect((volumeX0 + (volumeX1 - volumeX0) * v - 3.5f) * u, (y + 2.5f) * u, (volumeX0 + (volumeX1 - volumeX0) * v + 3.5f) * u,
                (y + 9.5f) * u, 0f, c(0xFFFFFFFF));
        text(Face.MONO, Math.round(v * 100) + "%", x + iw, y + 2f, 6.5f, TEXT_2, Align.RIGHT);
        hit(volumeX0 - 4f, y - 2f, volumeX1 + 4f, y + 14f, () -> {
            draggingVolume = true;
            LobbyBridge.setMusicVolume(clamp((mx - volumeX0) / Math.max(1f, volumeX1 - volumeX0), 0f, 1f));
        });
        y += 20f;

        toggle(x, y, iw, "Save scenes from places you play", "Your lobby can show the spots you've been", LobbyBridge.capturing(),
                () -> LobbyBridge.setCapturing(!LobbyBridge.capturing()));
    }

    private float sceneListY0, sceneListY1;

    /** A row of choices (one per value), the current one filled. Returns the y below it. */
    private float chips(float x, float y, float iw, Object[] values, String[] labels, Object current, java.util.function.Consumer<Object> pick) {
        int n = values.length;
        float cw = (iw - 3f * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            Object value = values[i];
            chip(x + i * (cw + 3f), y, cw, labels[i], value == current, () -> pick.accept(value));
        }
        return y + 24f;
    }

    private void smallArrow(float cx, float cy, boolean right, Runnable action) {
        boolean over = inside(cx - 8f, cy - 8f, cx + 8f, cy + 8f);
        float hv = anim("arrow:" + right, over);
        Overlay.icon(right ? "next" : "prev", (cx - 4f) * u, (cy - 4f) * u, 8f * u, c(mix(TEXT_2, TEXT, hv)));
        hit(cx - 8f, cy - 8f, cx + 8f, cy + 8f, action);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float y = (float) mouseY / u;
        if (page == 1 && wardrobe.scroll(mouseY, scrollY)) return true;
        if (lobbyPanel && y >= sceneListY0 && y < sceneListY1) {
            sceneScroll -= (float) scrollY;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void label(String s, float x, float y) {
        text(Face.MONO, s, x, y, 5.5f, TEXT_3, Align.LEFT);
    }

    private float option(float x, float y, float iw, String title, String sub, boolean on, Runnable action) {
        float h = 24f;
        boolean over = inside(x, y, x + iw, y + h);
        float hv = anim("opt:" + title, over);
        rect(x, y, x + iw, y + h, 5f, on ? 0x26E5484D : mix(0x0AFFFFFF, 0x1AFFFFFF, hv));
        if (on) outline(x, y, x + iw, y + h, 5f, 0x80E5484D);
        text(Face.SEMIBOLD, fit(Face.SEMIBOLD, title, 7.5f, iw - 30f), x + 8f, y + 4f, 7.5f, TEXT, Align.LEFT);
        text(Face.REGULAR, fit(Face.REGULAR, sub, 6f, iw - 30f), x + 8f, y + 14f, 6f, TEXT_2, Align.LEFT);
        if (on) Overlay.circle((x + iw - 10f) * u, (y + h / 2f) * u, 3f * u, c(ACCENT));
        hit(x, y, x + iw, y + h, action);
        return y + h + 3f;
    }

    private void chip(float x, float y, float cw, String label, boolean on, Runnable action) {
        boolean over = inside(x, y, x + cw, y + 18f);
        float hv = anim("chip:" + label, over);
        rect(x, y, x + cw, y + 18f, 4f, on ? ACCENT : mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        text(Face.MEDIUM, label, x + cw / 2f, y + 9f - 6.5f * 0.6f, 6.5f, on ? 0xFFFFFFFF : mix(TEXT_2, TEXT, hv), Align.CENTER);
        hit(x, y, x + cw, y + 18f, action);
    }

    private float toggle(float x, float y, float iw, String title, String sub, boolean on, Runnable action) {
        float h = 26f;
        text(Face.SEMIBOLD, title, x, y + 3f, 7.5f, TEXT, Align.LEFT);
        text(Face.REGULAR, fit(Face.REGULAR, sub, 6f, iw - 36f), x, y + 13f, 6f, TEXT_2, Align.LEFT);
        float sw = 22f, sh = 12f, sx = x + iw - sw, sy = y + 6f;
        float k = anim("tg:" + title, on);
        rect(sx, sy, sx + sw, sy + sh, sh / 2f, mix(0xFF2A2A30, ACCENT, k));
        Overlay.circle((sx + sh / 2f + (sw - sh) * k) * u, (sy + sh / 2f) * u, (sh / 2f - 2f) * u, c(0xFFFFFFFF));
        hit(x, y, x + iw, y + h, action);
        return y + h;
    }

    private void closeButton(float cx, float cy, Runnable action) {
        float r = 7f;
        boolean over = inside(cx - r, cy - r, cx + r, cy + r);
        float hv = anim("close", over);
        if (hv > 0.01f) Overlay.circle(cx * u, cy * u, r * u, c(alpha(0x1FFFFFFF, hv)));
        Overlay.icon("close", (cx - 4.5f) * u, (cy - 4.5f) * u, 9f * u, c(mix(TEXT_2, TEXT, hv)));
        hit(cx - r, cy - r, cx + r, cy + r, action);
    }

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
        }, i -> serverIcon(ping.icon(recent.get(i).ip), recent.get(i), 0f, 0f, 0f), "All servers", this::openServers);
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
            String gm = pick.getGameMode().getName();
            String mode = pick.isHardcore() ? "Hardcore" : gm.isEmpty() ? "World" : Character.toUpperCase(gm.charAt(0)) + gm.substring(1);
            text(Face.MEDIUM, fit(Face.MEDIUM, mode + (pick.hasCommands() ? " \u00b7 Cheats on" : ""), 6.5f, x1 - 10f - tx),
                    tx, cy0 + 15f, 6.5f, TEXT_2, Align.LEFT);
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
        String label = all;
        float lw = textW(Face.MEDIUM, label, 6.5f) + 26f;
        float lx1 = x1 - 8f, lx0 = lx1 - lw;
        boolean over = inside(lx0, cy - 8f, lx1, cy + 8f);
        float hv = anim("all", over);
        rect(lx0, cy - 8f, lx1, cy + 8f, 4f, mix(0x0FFFFFFF, 0x26FFFFFF, hv));
        outline(lx0, cy - 8f, lx1, cy + 8f, 4f, LINE);
        text(Face.MEDIUM, label, lx0 + 8f, cy - 6.5f * 0.6f, 6.5f, mix(TEXT_2, TEXT, hv), Align.LEFT);
        Overlay.icon("arrow", (lx1 - 14f + hv * 1.5f) * u, (cy - 3.5f) * u, 7f * u, c(mix(TEXT_2, TEXT, hv)));
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
        dev.golemsclient.render.GuiTexture tex = null;
        if (png != null) tex = Textures.image("server:" + server.ip + ":" + java.util.Arrays.hashCode(png), png, true, 0.18f);
        if (tex != null) {
            Overlay.texture(tex, x * u, y * u, (x + s) * u, (y + s) * u, 0f, 0f, 1f, 1f, c(0xFFFFFFFF));
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
        dev.golemsclient.render.GuiTexture tex;
        if (Textures.has(key)) {
            tex = Textures.image(key, null, true, 0.18f);
        } else {
            byte[] png = null;
            try {
                Path icon = world.getIcon();
                if (icon != null && Files.isRegularFile(icon) && Files.size(icon) < 1_000_000) png = Files.readAllBytes(icon);
            } catch (Exception ignored) {
            }
            tex = Textures.image(key, png, true, 0.18f);
        }
        if (tex != null) {
            Overlay.texture(tex, x * u, y * u, (x + s) * u, (y + s) * u, 0f, 0f, 1f, 1f, c(0xFFFFFFFF));
        } else {
            letterTile(world.getLevelName(), x, y, s);
        }
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
        // OneClient's lobby: the camera pulls away from the scene, then the game connects
        LobbyBridge.depart(() -> ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(server.ip), target, false, null));
    }

    private void openServers() {
        minecraft.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
    }

    private void openWorld(LevelSummary world) {
        LobbyBridge.depart(() -> minecraft.createWorldOpenFlows().openWorld(world.getLevelId(), () -> minecraft.setScreen(new MainMenu())));
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


    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // a click while the scene opens skips to the end of it
        if (lobby() && LobbyBridge.skipIntro()) return true;
        if (clickHit(event)) return true;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        if (lobby()) {
            double sx = event.x() / width, sy = event.y() / height;
            if (!socialOpen && LobbyBridge.touches(sx, sy)) {
                pressingPlayer = true;
                pressedAt = System.currentTimeMillis();
                pressX = sx;
                pressY = sy;
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        float x = (float) event.x() / u, y = (float) event.y() / u;
        float[] b = playerBox();
        if (!socialOpen && x >= b[0] && x < b[2] && y >= b[1] && y < b[3]) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingVolume) {
            float x = (float) event.x() / u;
            LobbyBridge.setMusicVolume(clamp((x - volumeX0) / Math.max(1f, volumeX1 - volumeX0), 0f, 1f));
            return true;
        }
        if (pressingPlayer || grabbing) {
            double sx = event.x() / width, sy = event.y() / height;
            // like OneClient: moving a little picks your player up
            if (!grabbing && Math.hypot((sx - pressX) * width, (sy - pressY) * height) > 4.0) grabbing = LobbyBridge.grab(pressX, pressY);
            if (grabbing) LobbyBridge.drag(sx, sy);
            return true;
        }
        if (dragging) {
            playerYaw += (float) dx * 2.5f / Math.max(0.5f, u);
            playerPitch = clamp(playerPitch - (float) dy * 2.5f / Math.max(0.5f, u), -40f, 40f);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingVolume) {
            draggingVolume = false;
            LobbyBridge.saveSettings();
        }
        if (grabbing) {
            LobbyBridge.release();
        } else if (pressingPlayer) {
            // a click on your player: it reacts, and the wardrobe opens (like OneClient)
            if (LobbyBridge.click(pressX, pressY)) openTab(1);
        }
        pressingPlayer = false;
        grabbing = false;
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (lobby()) {
            // OneClient's little key reactions, and any key skips the opening
            LobbyBridge.key(key, !socialOpen && !lobbyPanel);
            if (LobbyBridge.skipIntro()) return true;
        }
        if (socialOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                socialOpen = false;
                return true;
            }
            return super.keyPressed(event);
        }
        if (page == 1 && key == GLFW.GLFW_KEY_ESCAPE) {
            // Esc steps back inside the Wardrobe, then back to Play
            if (!wardrobe.back()) setPage(0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            socialOpen = true;
            return true;
        }
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------------ drawing helpers (units)




















}
