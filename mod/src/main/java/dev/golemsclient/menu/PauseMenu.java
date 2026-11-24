package dev.golemsclient.menu;

import dev.golemsclient.render.Overlay;
import dev.golemsclient.render.Overlay.Align;
import dev.golemsclient.render.Overlay.Face;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Golem's Client's pause menu, in the main menu's style: the game softly blurred behind one card with Back to game,
 * the usual buttons in two columns, and Disconnect (or Save and quit); where you are and your ping at the top, and
 * what's playing in the corner.
 */
public final class PauseMenu extends MenuScreen {
    private static final float PANEL_W = 250f;

    private boolean leaving;

    public PauseMenu() {
        super(Component.translatable("menu.game"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(g);
        g.fillGradient(0, 0, width, height, 0x66000000, 0x99000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        beginFrame(g, mouseX, mouseY);
        float slide = (1f - fade) * 8f;

        boolean lan = minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null
                && !minecraft.getSingleplayerServer().isPublished();
        float ph = 64f + 30f + 3f * 28f + 8f + 26f + 14f;
        float x0 = w / 2f - PANEL_W / 2f, x1 = x0 + PANEL_W;
        float y0 = H / 2f - ph / 2f - 10f + slide, y1 = y0 + ph;
        card(x0, y0, x1, y1);

        // where you are
        Overlay.image("/assets/golemsclient/textures/logo.png", (x0 + 14f) * u, (y0 + 14f) * u, (x0 + 32f) * u,
                (y0 + 32f) * u, c(0xFFFFFFFF));
        text(Face.BOLD, "Game paused", x0 + 40f, y0 + 13f, 11f, TEXT, Align.LEFT);
        text(Face.REGULAR, fit(Face.REGULAR, where(), 6.5f, PANEL_W - 54f), x0 + 40f, y0 + 27f, 6.5f, TEXT_2, Align.LEFT);
        int latency = latency();
        if (latency >= 0) {
            String ms = latency + " ms";
            text(Face.MONO, ms, x1 - 14f, y0 + 16f, 6.5f, latency < 80 ? LIVE : latency < 200 ? 0xFFE8BA5C : ACCENT, Align.RIGHT);
        }

        float bx0 = x0 + 14f, bx1 = x1 - 14f;
        float y = y0 + 50f;
        button("back", Component.translatable("menu.returnToGame").getString(), bx0, y, bx1, y + 26f, true, () -> {
            minecraft.setScreen(null);
            minecraft.mouseHandler.grabMouse();
        });
        y += 34f;

        float mid = (bx0 + bx1) / 2f, gap = 3f;
        button("adv", Component.translatable("gui.advancements").getString(), bx0, y, mid - gap, y + 22f, false,
                () -> minecraft.setScreen(new AdvancementsScreen(minecraft.player.connection.getAdvancements(), this)));
        button("stats", Component.translatable("gui.stats").getString(), mid + gap, y, bx1, y + 22f, false,
                () -> minecraft.setScreen(new StatsScreen(this, minecraft.player.getStats())));
        y += 28f;
        button("options", Component.translatable("menu.options").getString(), bx0, y, mid - gap, y + 22f, false,
                () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        if (lan) {
            button("lan", Component.translatable("menu.shareToLan").getString(), mid + gap, y, bx1, y + 22f, false,
                    () -> minecraft.setScreen(new ShareToLanScreen(this)));
        } else {
            button("report", Component.translatable("menu.playerReporting").getString(), mid + gap, y, bx1, y + 22f, false,
                    () -> minecraft.setScreen(new SocialInteractionsScreen(this)));
        }
        y += 28f;
        button("shots", "Screenshots", bx0, y, mid - gap, y + 22f, false,
                () -> openFolder(minecraft.gameDirectory.toPath().resolve("screenshots")));
        button("discord", "Get help", mid + gap, y, bx1, y + 22f, false,
                () -> net.minecraft.util.Util.getPlatform().openUri("https://discord.gg/q67qzHG2kf"));
        y += 36f;

        String leave = CommonComponents.disconnectButtonLabel(minecraft.isLocalServer()).getString();
        leaveButton(leave, bx0, y, bx1, y + 24f);

        // what's playing, bottom left
        float my1 = H - 26f + slide;
        drawMusic(PAD, my1 - 46f, PAD + 196f, my1);

        endFrame(g);
    }

    /** "On donutsmp.net", "Singleplayer: My World" or "On a LAN world". */
    private String where() {
        ServerData server = minecraft.getCurrentServer();
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            return "Singleplayer · " + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }
        if (server != null) return "On " + (server.isLan() ? "a LAN world" : server.ip);
        return "Playing";
    }

    /** Your ping to the server as the server sees it (the tab list's), or -1 in singleplayer. */
    private int latency() {
        if (minecraft.hasSingleplayerServer() || minecraft.getConnection() == null || minecraft.player == null) return -1;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return info != null ? info.getLatency() : -1;
    }

    private void button(String id, String label, float x0, float y0, float x1, float y1, boolean primary, Runnable action) {
        boolean over = inside(x0, y0, x1, y1);
        float hv = anim("b:" + id, over);
        if (primary) {
            rect(x0, y0, x1, y1, 6f, mix(ACCENT, 0xFFF0676B, hv));
            text(Face.BOLD, label, (x0 + x1) / 2f, (y0 + y1) / 2f - 9f * 0.6f, 9f, 0xFFFFFFFF, Align.CENTER);
        } else {
            rect(x0, y0, x1, y1, 5f, mix(0x0FFFFFFF, 0x24FFFFFF, hv));
            outline(x0, y0, x1, y1, 5f, mix(LINE, 0x33FFFFFF, hv));
            text(Face.MEDIUM, fit(Face.MEDIUM, label, 7.5f, x1 - x0 - 10f), (x0 + x1) / 2f, (y0 + y1) / 2f - 7.5f * 0.6f,
                    7.5f, mix(TEXT_2, TEXT, hv), Align.CENTER);
        }
        hit(x0, y0, x1, y1, action);
    }

    private void leaveButton(String label, float x0, float y0, float x1, float y1) {
        boolean over = inside(x0, y0, x1, y1) && !leaving;
        float hv = anim("leave", over);
        rect(x0, y0, x1, y1, 5f, mix(0x1AE5484D, 0x33E5484D, hv));
        outline(x0, y0, x1, y1, 5f, mix(0x40E5484D, 0x80E5484D, hv));
        text(Face.SEMIBOLD, leaving ? "Leaving..." : label, (x0 + x1) / 2f, (y0 + y1) / 2f - 7.5f * 0.6f, 7.5f,
                mix(0xFFF0676B, 0xFFFFFFFF, hv), Align.CENTER);
        if (leaving) return;
        hit(x0, y0, x1, y1, () -> {
            leaving = true;
            minecraft.getReportingContext().draftReportHandled(minecraft, this,
                    () -> minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return clickHit(event) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }
}
