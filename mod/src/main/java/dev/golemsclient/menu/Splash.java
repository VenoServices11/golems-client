package dev.golemsclient.menu;

import dev.golemsclient.mixin.SplashRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;

/**
 * The yellow splash next to the player: one of the splash texts (assets/minecraft/texts/splashes.txt), tilted and
 * gently pulsing like Minecraft's, but in Inter instead of the pixel font.
 */
final class Splash {
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("golemsclient", "splash"));
    private static final int COLOR = 0xFFFFE45C;

    private Component text;
    private long triedAt;

    /** Picks a splash (a new one each time the menu opens, as Minecraft does); tries again while none is loaded. */
    private void pick() {
        long now = System.currentTimeMillis();
        if (now - triedAt < 1000L) return;
        triedAt = now;
        SplashRenderer picked = Minecraft.getInstance().getSplashManager().getSplash();
        if (picked != null) {
            String s = ((SplashRendererAccessor) picked).golemsclient$text().getString();
            text = Component.literal(s).withStyle(style -> style.withFont(FONT).withColor(COLOR & 0xFFFFFF));
        }
    }

    /** Draws it centred at (x, y) in GUI pixels; unit is GUI pixels per menu unit, alpha the menu's fade. */
    void draw(GuiGraphics g, float x, float y, float unit, float alpha) {
        if (text == null) pick();
        if (text == null || alpha <= 0.01f) return;
        var font = Minecraft.getInstance().font;
        int width = font.width(text);
        if (width <= 0) return;
        float pulse = 1f - Mth.abs(Mth.sin((Util.getMillis() % 1000L) / 1000f * (float) (Math.PI * 2))) * 0.06f;
        // about 12 units tall, smaller for long splashes so they stay beside the player
        float scale = unit * 12f / 9f * pulse * Math.min(1f, 150f / Math.max(1f, width * 1.3f));
        ActiveTextCollector out = g.textRenderer();
        Matrix3x2f pose = new Matrix3x2f(out.defaultParameters().pose()).translate(x, y).rotate((float) (-Math.PI / 9)).scale(scale);
        ActiveTextCollector.Parameters params = out.defaultParameters().withOpacity(alpha).withPose(pose);
        out.accept(TextAlignment.LEFT, -width / 2, -5, params, text);
    }
}
