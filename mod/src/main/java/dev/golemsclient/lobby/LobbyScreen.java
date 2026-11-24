package dev.golemsclient.lobby;

import dev.golemsclient.GolemsClient;
import dev.golemsclient.render.Overlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Golem's Client's startup screen, shown while the lobby is made: the dark page with the G and the name in the
 * middle and a thin line under them that keeps moving. When the lobby is ready it fades away while the camera swoops
 * down and you run in; a click or Esc skips straight to the menu.
 */
public final class LobbyScreen extends Screen {
    private static final int BG = GolemsClient.BACKGROUND;
    private static final long FADE_MS = 900L;
    private final long openedAt = System.currentTimeMillis();

    public LobbyScreen() {
        super(Component.literal("Golem's Client"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float cover = 1f;
        if (Lobby.phase() == Lobby.Phase.REVEAL || Lobby.phase() == Lobby.Phase.READY) {
            cover = 1f - Math.min(1f, (System.currentTimeMillis() - Lobby.phaseStarted()) / (float) FADE_MS);
        }
        if (cover <= 0.001f) return;
        Overlay.begin(g);
        try {
            Overlay.rect(0f, 0f, width, height, 0f, alpha(BG, cover));
            float h = height * 0.075f;
            float mid = height / 2f;
            float logo = h, nameH = h * 0.44f, nameW = nameH * (1130f / 128f), gap = h * 0.24f;
            float left = width / 2f - (logo + gap + nameW) / 2f;
            Overlay.image("/assets/golemsclient/textures/logo.png", left, mid - logo / 2f, left + logo, mid + logo / 2f,
                    alpha(0xFFFFFFFF, cover));
            Overlay.image("/assets/golemsclient/textures/wordmark.png", left + logo + gap, mid - nameH / 2f,
                    left + logo + gap + nameW, mid + nameH / 2f, alpha(0xFFEDEDEF, cover));
            // a thin line under the name, with a red stretch sliding along it
            float lw = logo + gap + nameW, ly = mid + h * 0.95f, th = Math.max(0.5f, h * 0.022f);
            Overlay.rect(left, ly, left + lw, ly + th, 0f, alpha(0xFF26262B, cover));
            float t = ((System.currentTimeMillis() - openedAt) % 1600L) / 1600f;
            float seg = lw * 0.28f;
            float sx = left - seg + (lw + seg) * ease(t);
            Overlay.clip(left, ly - 1f, left + lw, ly + th + 1f);
            Overlay.rect(sx, ly, sx + seg, ly + th, 0f, alpha(GolemsClient.ACCENT, cover));
            Overlay.unclip();
        } finally {
            Overlay.end();
        }
    }

    private static float ease(float t) {
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    private static int alpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        return (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (Lobby.phase() == Lobby.Phase.REVEAL) {
            Lobby.finishReveal();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (Lobby.phase() == Lobby.Phase.REVEAL) Lobby.finishReveal();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public net.minecraft.sounds.Music getBackgroundMusic() {
        return net.minecraft.sounds.Musics.MENU;
    }
}
