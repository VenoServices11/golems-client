package dev.golemsclient.menu;

import dev.golemsclient.GolemsClient;
import dev.golemsclient.oc.client.gui.pausescreen.PauseBridge;
import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/**
 * Golem's Client's pause menu, laid out like OneClient's (ported from PolyPlus, GPL-3.0, by Polyfrost): a top bar with
 * the Game and Wardrobe tabs, where you are and Back to Game on the right with the menu under it, your player on the
 * left, and what's playing and you at the bottom left.
 *
 * <p>Two styles, picked from Customize: Flat (the game blurred, your player model turning slowly) and Step back (the
 * camera glides round to film your real player in the world, OneClient's PauseStage).
 */
public final class PauseMenu extends MenuScreen {
    private final SocialPanel social = new SocialPanel(this);
    private static final String[] TABS = {"Game", "Wardrobe"};
    private static final float ROW = 22f, TIGHT_ROW = 19f;

    private static boolean vanillaOnce;

    private final MenuData data = MenuData.get();
    private final WardrobePage wardrobe = new WardrobePage(this);
    private int page;
    private boolean decided, staged;
    private boolean moreOpen, customizeOpen;
    private boolean leaving;
    private float tabX = -1f, tabW;
    // the Flat model: turns slowly by itself, or follows a drag
    private float spin = -25f;
    private boolean spinning;
    private float spinFromX, spinFrom;
    private long spinLetGo;
    private float[] modelBox;

    public PauseMenu() {
        super(Component.translatable("menu.game"));
    }

    /** True once after "Classic pause menu" was picked: Minecraft's own pause screen opens instead of this one. */
    public static boolean takeVanilla() {
        boolean once = vanillaOnce;
        vanillaOnce = false;
        return once;
    }

    @Override
    protected void init() {
        super.init();
        if (!decided) {
            decided = true;
            staged = stepBack() && PauseBridge.enter();
            if (stepBack() && !staged) showToast("No room to step back here, so it's Flat this time. Step back needs open space around you.");
        }
    }

    /** Dev screenshots only: switches the open pause menu to a tab. */
    public static void devPage(int index) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof PauseMenu p) p.setPage(index);
    }

    private boolean stepBack() {
        return "stepback".equals(data.pauseStyle);
    }

    // ------------------------------------------------------------------ background

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int ink = 0x0E0E10;
        boolean filming = staged && page == 0;
        if (!filming) {
            renderBlurredBackground(g);
            g.fill(0, 0, width, height, (Math.round(255 * (page == 0 ? 0.45f : 0.6f)) << 24) | ink);
        }
        // darker towards the menu on the right (OneClient's 0.5 -> 0.66 -> 0.8 -> 1 stops)
        // one column per GUI pixel, so there's no banding
        int from = width / 2;
        for (int x = from; x < width; x++) {
            float t = (x + 0.5f) / width;
            float a = t < 0.66f ? (t - 0.5f) / 0.16f * 0.72f : t < 0.8f ? 0.72f + (t - 0.66f) / 0.14f * 0.2f : 0.92f + (t - 0.8f) / 0.2f * 0.04f;
            g.fill(x, 0, x + 1, height, (Math.round(255 * a * fade) << 24) | ink);
        }
        // a soft vignette at the top and bottom
        g.fillGradient(0, 0, width, Math.round(height * 0.33f), (Math.round(255 * 0.92f * fade) << 24) | ink, ink);
        g.fillGradient(0, Math.round(height * 0.56f), width, height, ink, (Math.round(255 * 0.6f * fade) << 24) | ink);
    }

    // ------------------------------------------------------------------ the frame

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        beginFrame(g, mouseX, mouseY);
        if (staged) PauseBridge.pointer(mx / w * 2f - 1f, my / H * 2f - 1f);

        if (page == 1) {
            drawModel(g, w / 2f, true);
            wardrobe.draw();
        } else {
            drawPlayer(g);
            drawMenu();
            drawBottomLeft();
        }
        drawTopBar();
        if (customizeOpen) drawCustomize();
        social.draw();
        endFrame(g);
    }

    // ------------------------------------------------------------------ top bar

    private void drawTopBar() {
        float y = 14f;
        Overlay.image("/assets/golemsclient/textures/logo.png", PAD * u, y * u, (PAD + 18f) * u, (y + 18f) * u, c(0xFFFFFFFF));
        float nameH = 8.5f, nameW = nameH * (1130f / 128f);
        Overlay.image("/assets/golemsclient/textures/wordmark.png", (PAD + 25f) * u, (y + 9f - nameH / 2f) * u,
                (PAD + 25f + nameW) * u, (y + 9f + nameH / 2f) * u, c(TEXT));

        float size = 8.5f, gap = 22f, total = -gap;
        for (String tab : TABS) total += textW(Face.MEDIUM, tab, size) + gap;
        float x = w / 2f - total / 2f;
        for (int i = 0; i < TABS.length; i++) {
            float tw = textW(Face.MEDIUM, TABS[i], size);
            boolean over = inside(x - 6f, y - 2f, x + tw + 6f, y + 20f);
            float hv = anim("tab" + i, over);
            text(Face.MEDIUM, TABS[i], x, y + 9f - size * 0.6f, size, i == page ? TEXT : mix(TEXT_2, TEXT, hv), Align.LEFT);
            if (i == page) {
                if (tabX < 0f) {
                    tabX = x;
                    tabW = tw;
                }
                tabX = approach(tabX, x, 14f);
                tabW = approach(tabW, tw, 14f);
            }
            final int index = i;
            hit(x - 6f, y - 2f, x + tw + 6f, y + 20f, () -> setPage(index));
            x += tw + gap;
        }
        rect(tabX, y + 18f, tabX + tabW, y + 19.5f, 0.75f, ACCENT);

        float head = 18f, hx = w - PAD - head;
        drawHead(hx, y, head, 4f);
        String name = minecraft.getUser().getName();
        text(Face.SEMIBOLD, name, hx - 8f, y + 9f - 8.5f * 0.6f, 8.5f, TEXT, Align.RIGHT);
        float ix = hx - 8f - textW(Face.SEMIBOLD, name, 8.5f) - 18f;
        ix = iconButton("settings", ix, y + 9f, "Options", this::openOptions);
        iconButton("image", ix, y + 9f, "Customize the pause screen", () -> {
            moreOpen = false;
            customizeOpen = !customizeOpen;
        });
    }

    private void setPage(int next) {
        if (next == page) return;
        if (page == 1) wardrobe.close();
        page = next;
        moreOpen = false;
        customizeOpen = false;
        if (page == 1) {
            wardrobe.open();
            // Step back: the camera glides back while the Wardrobe shows your player model instead
            if (staged) {
                PauseBridge.leave();
                staged = false;
            }
        } else if (stepBack()) {
            staged = PauseBridge.enter();
        }
    }

    // ------------------------------------------------------------------ the menu (right)

    private void drawMenu() {
        boolean narrow = w < 720f;
        float row = narrow ? TIGHT_ROW : ROW;
        float cw = narrow ? 172f : 204f;
        float x1 = w - (narrow ? 14f : 36f), x0 = x1 - cw;
        float slide = (1f - fade) * 24f;
        x0 += slide;
        x1 += slide;

        List<Row> rows = rows();
        float content = 8f + 3f + 13f + 3f + 9f + 4f + 9f + (narrow ? 12f : 18f) + 24f + (narrow ? 8f : 12f)
                + rows.size() * row + 13f + row;
        float top = 34f + 4f;
        float y = Math.max(top, top + (H - 16f - top - content) / 2f);

        Place place = place();
        text(Face.MONO, place.eyebrow, x0, y, 5.5f, TEXT_3, Align.LEFT);
        y += 11f;
        text(Face.MEDIUM, fit(Face.MEDIUM, place.title, 12f, cw), x0, y, 12f, TEXT, Align.LEFT);
        y += 16f;
        if (!place.detail.isBlank()) {
            text(Face.REGULAR, fit(Face.REGULAR, place.detail, 6.5f, cw), x0, y, 6.5f, TEXT_2, Align.LEFT);
            y += 11f;
        }
        y = stateLine(x0, y + 2f, cw) + (narrow ? 12f : 18f);

        // Back to Game
        String back = Component.translatable("menu.returnToGame").getString();
        boolean over = inside(x0, y, x1, y + 24f) && !moreOpen && !customizeOpen;
        float hv = anim("resume", over);
        rect(x0, y, x1, y + 24f, 5f, mix(ACCENT, 0xFFF0676B, hv));
        Overlay.icon("play", (x0 + 10f) * u, (y + 7.5f) * u, 9f * u, c(0xFFFFFFFF));
        text(Face.BOLD, back, x0 + 25f, y + 12f - 8.5f * 0.6f, 8.5f, 0xFFFFFFFF, Align.LEFT);
        if (!moreOpen && !customizeOpen) hit(x0, y, x1, y + 24f, this::resume);
        y += 24f + (narrow ? 8f : 12f);

        float moreY = -1f;
        for (Row r : rows) {
            if (r.more) moreY = y;
            menuRow(r, x0, y, x1, row, false);
            y += row;
        }
        rect(x0, y + 6f, x1, y + 6.5f, 0f, LINE);
        y += 13f;
        quitRow(x0, y, x1, row);

        if (moreOpen && moreY >= 0f) drawMore(x0, moreY, x1, row);
    }

    private record Row(String icon, String label, Runnable action, boolean more) { }

    private List<Row> rows() {
        java.util.ArrayList<Row> out = new java.util.ArrayList<>();
        out.add(new Row("settings", label("menu.options"), this::openOptions, false));
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (minecraft.hasSingleplayerServer() && server != null && !server.isPublished()) {
            out.add(new Row("globe", label("menu.shareToLan"), () -> minecraft.setScreen(new ShareToLanScreen(this)), false));
        }
        if (minecraft.player != null) {
            out.add(new Row("zap", label("gui.advancements"),
                    () -> minecraft.setScreen(new AdvancementsScreen(minecraft.player.connection.getAdvancements(), this)), false));
            out.add(new Row("stats", label("gui.stats"), () -> minecraft.setScreen(new StatsScreen(this, minecraft.player.getStats())), false));
        }
        out.add(new Row("users", "Friends", social::show, false));
        out.add(new Row("cube", "Mods", this::openMods, false));
        out.add(new Row(null, "More", () -> moreOpen = !moreOpen, true));
        return out;
    }

    /** A vanilla label without its trailing "...". */
    private static String label(String key) {
        String s = Component.translatable(key).getString();
        return s.endsWith("...") ? s.substring(0, s.length() - 3) : s;
    }

    private void menuRow(Row r, float x0, float y, float x1, float h, boolean inPopup) {
        boolean blocked = !inPopup && (customizeOpen || (moreOpen && !r.more));
        boolean over = inside(x0, y, x1, y + h) && !blocked;
        float hv = anim("row:" + r.label + inPopup, over || (r.more && moreOpen && !inPopup));
        if (hv > 0.01f) rect(x0 - 6f, y, x1 + 6f, y + h, 4f, alpha(0x14FFFFFF, hv));
        float cy = y + h / 2f;
        int color = mix(TEXT_2, TEXT, hv);
        if (r.icon != null) {
            Overlay.icon(r.icon, x0 * u, (cy - 4.5f) * u, 9f * u, c(color));
        } else {
            for (int i = -1; i <= 1; i++) Overlay.circle((x0 + 4.5f) * u, (cy + i * 2.8f) * u, 0.9f * u, c(color));
        }
        text(Face.MEDIUM, r.label, x0 + 17f, cy - 7.5f * 0.6f, 7.5f, mix(0xFFD4D4D8, TEXT, hv), Align.LEFT);
        if (!blocked) hit(x0 - 6f, y, x1 + 6f, y + h, r.action);
    }

    private void quitRow(float x0, float y, float x1, float h) {
        String label = leaving ? "Leaving..." : CommonComponents.disconnectButtonLabel(minecraft.isLocalServer()).getString();
        boolean blocked = moreOpen || customizeOpen || leaving;
        boolean over = inside(x0, y, x1, y + h) && !blocked;
        float hv = anim("quit", over);
        if (hv > 0.01f) rect(x0 - 6f, y, x1 + 6f, y + h, 4f, alpha(0x26E5484D, hv));
        float cy = y + h / 2f;
        Overlay.icon("quit", x0 * u, (cy - 4.5f) * u, 9f * u, c(mix(0xFFE5484D, 0xFFF0676B, hv)));
        text(Face.MEDIUM, label, x0 + 17f, cy - 7.5f * 0.6f, 7.5f, mix(0xFFF0676B, 0xFFFFFFFF, hv), Align.LEFT);
        if (blocked) return;
        hit(x0 - 6f, y, x1 + 6f, y + h, () -> {
            leaving = true;
            minecraft.getReportingContext().draftReportHandled(minecraft, this,
                    () -> minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
        });
    }

    /** "More": the rest of the pause menu's choices, in a little card next to the row. */
    private void drawMore(float x0, float rowY, float x1, float h) {
        java.util.ArrayList<Row> items = new java.util.ArrayList<>();
        if (!minecraft.hasSingleplayerServer() && minecraft.player != null) {
            items.add(new Row("users", label("menu.playerReporting"), () -> minecraft.setScreen(new SocialInteractionsScreen(this)), false));
        }
        items.add(new Row("folder", "Screenshots", () -> openFolder(minecraft.gameDirectory.toPath().resolve("screenshots")), false));
        items.add(new Row("discord", "Get help", () -> net.minecraft.util.Util.getPlatform().openUri("https://discord.gg/q67qzHG2kf"), false));
        items.add(new Row("pause", "Classic pause menu", this::openClassic, false));
        float pw = 150f, ph = items.size() * h + 10f;
        float px1 = x0 - 10f, px0 = px1 - pw;
        float py0 = Math.min(rowY - 5f, H - 16f - ph);
        // a click anywhere else closes it
        hit(0f, 0f, w, H, () -> moreOpen = false);
        hits.add(0, new Hit(px0, py0, px1, py0 + ph, () -> { }));
        card(px0, py0, px1, py0 + ph);
        float y = py0 + 5f;
        for (Row r : items) {
            Runnable act = r.action;
            Row closing = new Row(r.icon, r.label, () -> {
                moreOpen = false;
                act.run();
            }, false);
            menuRow(closing, px0 + 10f, y, px1 - 10f, h, true);
            // the popup's rows go before the click-away area
            Hit last = hits.remove(hits.size() - 1);
            hits.add(0, last);
            y += h;
        }
    }

    // ------------------------------------------------------------------ where you are

    private record Place(String eyebrow, String title, String detail) { }

    private Place place() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (minecraft.hasSingleplayerServer() && server != null) {
            String name = server.getWorldData().getLevelName();
            StringBuilder detail = new StringBuilder();
            if (minecraft.gameMode != null) detail.append(minecraft.gameMode.getPlayerMode().getShortDisplayName().getString());
            if (minecraft.level != null) {
                if (!detail.isEmpty()) detail.append(" · ");
                detail.append(minecraft.level.getDifficulty().getDisplayName().getString());
            }
            return new Place(server.isPublished() ? "OPEN TO LAN" : "SINGLEPLAYER", name == null || name.isBlank() ? "World" : name, detail.toString());
        }
        return new Place("MULTIPLAYER", serverName(), serverDetail());
    }

    private String serverName() {
        ServerData server = minecraft.getCurrentServer();
        if (server == null) return "Multiplayer server";
        String name = server.name == null ? "" : server.name.trim();
        String ip = server.ip == null ? "" : server.ip.trim();
        // like OneClient: a saved name, never a bare IP address (it shows on stream)
        boolean rawIp = name.matches("[0-9.:]+") || (name.contains(":") && name.matches("[0-9a-fA-F:\\[\\]]+"));
        if (!name.isEmpty() && !name.equals(Component.translatable("selectServer.defaultName").getString())
                && !name.equalsIgnoreCase(ip) && !rawIp) return name;
        return "Multiplayer server";
    }

    private String serverDetail() {
        if (minecraft.getConnection() == null) return "";
        int online = minecraft.getConnection().getOnlinePlayers().size();
        String people = online == 1 ? "1 online" : online + " online";
        PlayerInfo me = minecraft.player == null ? null : minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        int ping = me == null ? 0 : me.getLatency();
        return ping > 0 ? people + " · " + ping + " ms" : people;
    }

    /** "Game paused" (two little bars) or "Not paused" (a green dot) and why. Returns the y under it. */
    private float stateLine(float x, float y, float cw) {
        boolean paused = minecraft.isPaused();
        String label = paused ? "Game paused" : "Not paused";
        String detail = paused ? null : minecraft.hasSingleplayerServer()
                ? (minecraft.getSingleplayerServer() != null && minecraft.getSingleplayerServer().isPublished() ? "Open to LAN" : null)
                : "Multiplayer";
        if (paused) {
            rect(x, y + 0.5f, x + 1.5f, y + 6f, 0f, TEXT_2);
            rect(x + 3f, y + 0.5f, x + 4.5f, y + 6f, 0f, TEXT_2);
        } else {
            Overlay.circle((x + 2.5f) * u, (y + 3.25f) * u, 2.5f * u, c(LIVE));
        }
        float tx = x + 9f;
        text(Face.MEDIUM, label, tx, y - 0.5f, 6.5f, paused ? TEXT_2 : TEXT, Align.LEFT);
        if (detail != null) {
            tx += textW(Face.MEDIUM, label, 6.5f) + 6f;
            text(Face.REGULAR, fit(Face.REGULAR, detail, 6.5f, cw - (tx - x)), tx, y - 0.5f, 6.5f, TEXT_2, Align.LEFT);
        }
        return y + 9f;
    }

    // ------------------------------------------------------------------ your player (left)

    private void drawPlayer(GuiGraphics g) {
        String where = place().title;
        if (staged) {
            // Step back: the camera films your player; the tag floats over their head
            float k = PauseBridge.weight();
            if (k <= 0.01f) return;
            float cx = PauseBridge.tagX() * w, bottom = PauseBridge.tagY() * H;
            tag(cx, Math.max(34f + 30f, bottom), where, k);
            return;
        }
        if (!data.pausePlayer) return;
        float menuReserve = w < 720f ? 200f : 252f;
        float right = w - menuReserve - 8f;
        float cx = clamp(w * 0.35f, PAD + 60f, right - 60f);
        drawModel(g, cx, false);
        if (modelBox != null) tag(cx, modelBox[1] - 4f, where, 1f);
    }

    /** Your player model, turning slowly (drag to turn it yourself), standing at cx. */
    private void drawModel(GuiGraphics g, float cx, boolean wardrobePage) {
        LivingEntity player = minecraft.player;
        modelBox = null;
        if (player == null) return;
        float top = 34f + 8f + (wardrobePage ? 0f : 28f), bottom = H - 16f - (wardrobePage ? 40f : 30f + 8f + MUSIC_H + 8f);
        float height = Math.min((bottom - top) * 0.92f, 260f);
        if (height < 70f) return;
        float width = height * 0.55f;
        float y1 = bottom - (bottom - top - height) * 0.35f, y0 = y1 - height;
        modelBox = new float[] {cx - width / 2f, y0, cx + width / 2f, y1};

        if (spinning) {
            spin = spinFrom + (mx - spinFromX) * 1.4f;
        } else if (System.currentTimeMillis() - spinLetGo > 1500L) {
            spin += dt * 18f;
        }
        if (inside(modelBox[0], modelBox[1], modelBox[2], modelBox[3])) pointer = true;

        float bodyRot = player.yBodyRot, yRot = player.getYRot(), xRot = player.getXRot();
        float headRot = player.yHeadRot, headRotO = player.yHeadRotO, bodyRotO = player.yBodyRotO;
        float yaw = 180f + spin;
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.setYRot(yaw);
        player.setXRot(0f);
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
        try {
            int x0 = Math.round(modelBox[0] * u), py0 = Math.round(modelBox[1] * u);
            int x1 = Math.round(modelBox[2] * u), py1 = Math.round(modelBox[3] * u);
            float scale = (py1 - py0) / (player.getBbHeight() * 1.12f) / player.getScale();
            Vector3f translation = new Vector3f(0f, player.getBbHeight() / 2f + 0.0625f * player.getScale(), 0f);
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(-6f));
            pose.mul(camera);
            EntityRenderState state = minecraft.getEntityRenderDispatcher().extractEntity(player, 1f);
            state.lightCoords = 0xF000F0;
            state.shadowPieces.clear();
            state.nameTag = null;
            submitEntity(g, state, scale, translation, pose, camera, x0, py0, x1, py1);
        } finally {
            player.yBodyRot = bodyRot;
            player.yBodyRotO = bodyRotO;
            player.setYRot(yRot);
            player.setXRot(xRot);
            player.yHeadRot = headRot;
            player.yHeadRotO = headRotO;
        }
    }

    // GuiGraphics' "draw this entity in a box" (what the inventory uses), found by its parameters
    private static java.lang.reflect.Method submit;
    private static boolean looked;

    private static void submitEntity(GuiGraphics g, EntityRenderState state, float scale, Vector3f translation, Quaternionf pose,
                                     Quaternionf camera, int x0, int y0, int x1, int y1) {
        if (!looked) {
            looked = true;
            Class<?>[] want = {EntityRenderState.class, float.class, Vector3f.class, Quaternionf.class, Quaternionf.class,
                    int.class, int.class, int.class, int.class};
            for (java.lang.reflect.Method m : GuiGraphics.class.getMethods()) {
                if (java.util.Arrays.equals(m.getParameterTypes(), want)) {
                    submit = m;
                    break;
                }
            }
            if (submit == null) GolemsClient.LOGGER.warn("Couldn't find how to draw your player on the pause screen");
        }
        if (submit == null) return;
        try {
            submit.invoke(g, state, scale, translation, pose, camera, x0, y0, x1, y1);
        } catch (ReflectiveOperationException e) {
            GolemsClient.LOGGER.debug("Couldn't draw your player: {}", e.toString());
            submit = null;
        }
    }

    /** Your name and "You" over your head, where you are under it. */
    private void tag(float cx, float bottom, String where, float k) {
        String name = minecraft.getUser().getName();
        float nameSize = 7.5f, youSize = 6f, whereSize = 6f;
        float top = nameSize + 5f, wide = Math.max(textW(Face.SEMIBOLD, name, nameSize) + 6f + textW(Face.MEDIUM, "You", youSize),
                textW(Face.REGULAR, where, whereSize)) + 18f;
        float th = 30f, ty = bottom - th;
        rect(cx - wide / 2f, ty, cx + wide / 2f, ty + th, 4f, alpha(0xB40E0E10, k));
        outline(cx - wide / 2f, ty, cx + wide / 2f, ty + th, 4f, alpha(LINE, k));
        float nw = textW(Face.SEMIBOLD, name, nameSize), lineW = nw + 6f + textW(Face.MEDIUM, "You", youSize);
        float lx = cx - lineW / 2f;
        text(Face.SEMIBOLD, name, lx, ty + 6f, nameSize, alpha(TEXT, k), Align.LEFT);
        text(Face.MEDIUM, "You", lx + nw + 6f, ty + 7f, youSize, alpha(TEXT_2, k), Align.LEFT);
        text(Face.REGULAR, fit(Face.REGULAR, where, whereSize, wide - 12f), cx, ty + 6f + top, whereSize, alpha(TEXT_2, k), Align.CENTER);
    }

    // ------------------------------------------------------------------ bottom left

    private void drawBottomLeft() {
        float x0 = PAD, x1 = PAD + 196f, slide = (1f - fade) * 8f;
        float by1 = H - 16f + slide, by0 = by1 - 30f;
        drawMusic(x0, by0 - 8f - MUSIC_H, x1, by0 - 8f);
        // you (your party, once Golem's Client has parties)
        card(x0, by0, x1, by1);
        drawHead(x0 + 7f, by0 + 6f, 18f, 3f);
        String name = minecraft.getUser().getName();
        text(Face.SEMIBOLD, fit(Face.SEMIBOLD, name, 7.5f, x1 - x0 - 40f), x0 + 31f, by0 + 6.5f, 7.5f, TEXT, Align.LEFT);
        text(Face.REGULAR, "You", x0 + 31f, by0 + 17f, 6f, TEXT_2, Align.LEFT);
    }

    // ------------------------------------------------------------------ customize

    private void drawCustomize() {
        float x1 = w - PAD, x0 = x1 - 190f, y0 = 38f + (1f - anim("customize", true)) * 6f;
        float ph = 128f;
        hit(0f, 0f, w, H, () -> customizeOpen = false);
        hits.add(0, new Hit(x0, y0, x1, y0 + ph, () -> { }));
        card(x0, y0, x1, y0 + ph);
        float x = x0 + 12f, y = y0 + 12f, iw = x1 - x0 - 24f;
        text(Face.BOLD, "Pause screen", x, y, 9.5f, TEXT, Align.LEFT);
        int first = hits.size();
        y += 20f;
        text(Face.MONO, "STYLE", x, y, 5.5f, TEXT_3, Align.LEFT);
        y += 10f;
        float cw = (iw - 3f) / 2f;
        chip(x, y, cw, "Flat", !stepBack(), () -> setStyle("flat"));
        chip(x + cw + 3f, y, cw, "Step back", stepBack(), () -> setStyle("stepback"));
        y += 26f;
        if (stepBack()) {
            toggle(x, y, iw, "Always pan camera", "Even with reduced motion", data.pauseAlwaysPan, () -> {
                data.pauseAlwaysPan = !data.pauseAlwaysPan;
                data.save();
            });
        } else {
            toggle(x, y, iw, "Player preview", "Your player beside the menu", data.pausePlayer, () -> {
                data.pausePlayer = !data.pausePlayer;
                data.save();
            });
        }
        // the panel's own controls go before the click-away area
        for (int i = hits.size() - 1; i >= first; i--) hits.add(0, hits.remove(hits.size() - 1));
    }

    private void setStyle(String style) {
        if (style.equals(data.pauseStyle)) return;
        data.pauseStyle = style;
        data.save();
        if (stepBack()) {
            staged = PauseBridge.enter();
            if (!staged) showToast("Not enough room around you to step back here. It's used where there's space.");
        } else if (staged) {
            PauseBridge.leave();
            staged = false;
        }
    }

    private void chip(float x, float y, float cw, String label, boolean on, Runnable action) {
        boolean over = inside(x, y, x + cw, y + 18f);
        float hv = anim("chip:" + label, over);
        rect(x, y, x + cw, y + 18f, 4f, on ? 0x33E5484D : mix(0x0FFFFFFF, 0x24FFFFFF, hv));
        outline(x, y, x + cw, y + 18f, 4f, on ? alpha(ACCENT, 0.8f) : LINE);
        text(Face.MEDIUM, label, x + cw / 2f, y + 9f - 7f * 0.6f, 7f, on ? TEXT : mix(TEXT_2, TEXT, hv), Align.CENTER);
        hit(x, y, x + cw, y + 18f, action);
    }

    private void toggle(float x, float y, float iw, String title, String sub, boolean on, Runnable action) {
        text(Face.SEMIBOLD, title, x, y + 3f, 7.5f, TEXT, Align.LEFT);
        text(Face.REGULAR, fit(Face.REGULAR, sub, 6f, iw - 36f), x, y + 13f, 6f, TEXT_2, Align.LEFT);
        float sw = 22f, sh = 12f, sx = x + iw - sw, sy = y + 6f;
        float k = anim("tg:" + title, on);
        rect(sx, sy, sx + sw, sy + sh, sh / 2f, mix(0xFF2A2A30, ACCENT, k));
        Overlay.circle((sx + sh / 2f + (sw - sh) * k) * u, (sy + sh / 2f) * u, (sh / 2f - 2f) * u, c(0xFFFFFFFF));
        hit(x, y, x + iw, y + 26f, action);
    }

    // ------------------------------------------------------------------ actions

    private void resume() {
        minecraft.setScreen(null);
        minecraft.mouseHandler.grabMouse();
    }

    private void openOptions() {
        minecraft.setScreen(new OptionsScreen(this, minecraft.options));
    }

    /** Minecraft's own pause screen, this once. */
    private void openClassic() {
        vanillaOnce = true;
        minecraft.setScreen(new PauseScreen(true));
    }

    /** The mods menu (OneConfig's, until Golem's Client has its own). */
    /** The mods menu (OneConfig's, in Golem's Client's colours). */
    private void openMods() {
        dev.golemsclient.oneconfig.OneConfigBrand.openMenu(this::showToast);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (clickHit(event)) return true;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && modelBox != null && !staged
                && inside(modelBox[0], modelBox[1], modelBox[2], modelBox[3])) {
            spinning = true;
            spinFromX = (float) event.x() / u;
            spinFrom = spin;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (spinning) {
            spinning = false;
            spinLetGo = System.currentTimeMillis();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (social.scroll(mouseY, scrollY)) return true;
        if (page == 1 && wardrobe.scroll(mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        setPage(1);
        wardrobe.drop(files);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (social.type(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (social.open()) {
            social.key(event.key());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (customizeOpen) {
                customizeOpen = false;
                return true;
            }
            if (moreOpen) {
                moreOpen = false;
                return true;
            }
            if (page == 1) {
                if (!wardrobe.back()) setPage(0);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (page == 1) wardrobe.close();
        super.removed();
    }

    @Override
    protected boolean modalOpen() {
        return moreOpen || customizeOpen || social.open();
    }
}
