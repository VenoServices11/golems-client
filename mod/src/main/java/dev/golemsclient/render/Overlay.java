package dev.golemsclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.golemsclient.GolemsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Golem's Client's drawing: smooth text, rounded cards, outlines, circles, icons and pictures, all through
 * Minecraft's own renderer (so it works on OpenGL and Vulkan, and with Iris and Sodium), and in the order they are
 * drawn. Coordinates and sizes are in GUI pixels (floats); shapes are snapped to real pixels so edges stay crisp.
 * A screen calls {@link #begin} with its GuiGraphics before drawing and {@link #end} after.
 */
public final class Overlay {
    public enum Face { REGULAR, MEDIUM, SEMIBOLD, BOLD, MONO }

    public enum Align { LEFT, CENTER, RIGHT }

    /** Text positions are kept to a quarter of a real pixel. */
    private static final int SUB = 4;

    private static GuiGraphics g;
    private static int scale = 1;
    private static int depth;
    private static long frameMark;
    private static GpuSampler pixelSampler;

    private Overlay() {
    }

    /** Always true now (kept for callers from the ImGui days). */
    public static boolean available() {
        return true;
    }

    /** Starts drawing into this GuiGraphics (nestable). */
    public static void begin(GuiGraphics graphics) {
        if (depth++ == 0) {
            g = graphics;
            scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
            // pictures let go are freed in two steps, at least a tenth of a second apart, so none is still in use
            long now = System.nanoTime();
            if (now - frameMark > 100_000_000L) {
                frameMark = now;
                Textures.frame();
            }
        }
    }

    public static void end() {
        if (depth > 0 && --depth == 0) g = null;
    }

    /** Real (window) pixels per GUI pixel. */
    public static int scale() {
        return g != null ? scale : Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    // ------------------------------------------------------------------ text

    public static void text(Face face, String s, float x, float y, float size, int argb, Align align) {
        if (g == null || s == null || s.isEmpty() || (argb >>> 24) == 0) return;
        int px = Math.max(5, Math.round(size * scale));
        Fonts.Sheet sheet = Fonts.sheet(face, px);
        if (sheet == null) return;
        float left = x * scale;
        if (align != Align.LEFT) {
            float w = advance(sheet, s);
            left -= align == Align.RIGHT ? w : w / 2f;
        }
        float baseline = Math.round(y * scale + sheet.ascent);
        left = Math.round(left);
        g.pose().pushMatrix();
        g.pose().scale(1f / (scale * SUB), 1f / (scale * SUB));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer px0 = stack.floats(left), py0 = stack.floats(baseline);
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            GpuTextureView view = sheet.texture.getTextureView();
            GpuSampler sampler = sheet.texture.getSampler();
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                i += Character.charCount(cp);
                if (!sheet.quad(cp, px0, py0, q)) sheet.quad('?', px0, py0, q);
                if (cp == ' ') continue;
                submit(view, sampler, Math.round(q.x0() * SUB), Math.round(q.y0() * SUB), Math.round(q.x1() * SUB),
                        Math.round(q.y1() * SUB), q.s0(), q.s1(), q.t0(), q.t1(), argb);
            }
        } finally {
            g.pose().popMatrix();
        }
    }

    /** Text width in GUI pixels. */
    public static float width(Face face, String s, float size) {
        if (s == null || s.isEmpty()) return 0f;
        int sc = scale();
        Fonts.Sheet sheet = Fonts.sheet(face, Math.max(5, Math.round(size * sc)));
        if (sheet == null) return s.length() * size * 0.55f;
        return advance(sheet, s) / sc;
    }

    private static float advance(Fonts.Sheet sheet, String s) {
        float w = 0f;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            w += sheet.advance(cp);
        }
        return w;
    }

    // ------------------------------------------------------------------ shapes

    /** A filled rectangle with rounded corners (radius in GUI pixels; 0 for square). */
    public static void rect(float x0, float y0, float x1, float y1, float radius, int argb) {
        if (g == null || (argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) return;
        int X0 = Math.round(x0 * scale), Y0 = Math.round(y0 * scale), X1 = Math.round(x1 * scale), Y1 = Math.round(y1 * scale);
        if (X1 <= X0 || Y1 <= Y0) return;
        int r = Math.min(Math.round(radius * scale), Math.min((X1 - X0) / 2, (Y1 - Y0) / 2));
        pixels();
        try {
            if (r < 1) {
                g.fill(X0, Y0, X1, Y1, argb);
                return;
            }
            corners(Textures.corner(r, 0), X0, Y0, X1, Y1, r, argb);
            if (X1 - X0 > 2 * r) {
                g.fill(X0 + r, Y0, X1 - r, Y0 + r, argb);
                g.fill(X0 + r, Y1 - r, X1 - r, Y1, argb);
            }
            if (Y1 - Y0 > 2 * r) g.fill(X0, Y0 + r, X1, Y1 - r, argb);
        } finally {
            g.pose().popMatrix();
        }
    }

    /** A rounded rectangle's border; thickness in GUI pixels (at least one real pixel). */
    public static void outline(float x0, float y0, float x1, float y1, float radius, int argb, float thickness) {
        if (g == null || (argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) return;
        int X0 = Math.round(x0 * scale), Y0 = Math.round(y0 * scale), X1 = Math.round(x1 * scale), Y1 = Math.round(y1 * scale);
        int t = Math.max(1, Math.round(thickness * scale));
        if (X1 - X0 < 2 * t || Y1 - Y0 < 2 * t) {
            rect(x0, y0, x1, y1, radius, argb);
            return;
        }
        int r = Math.min(Math.round(radius * scale), Math.min((X1 - X0) / 2, (Y1 - Y0) / 2));
        pixels();
        try {
            if (r <= t) {
                g.fill(X0, Y0, X1, Y0 + t, argb);
                g.fill(X0, Y1 - t, X1, Y1, argb);
                g.fill(X0, Y0 + t, X0 + t, Y1 - t, argb);
                g.fill(X1 - t, Y0 + t, X1, Y1 - t, argb);
                return;
            }
            corners(Textures.corner(r, t), X0, Y0, X1, Y1, r, argb);
            g.fill(X0 + r, Y0, X1 - r, Y0 + t, argb);
            g.fill(X0 + r, Y1 - t, X1 - r, Y1, argb);
            g.fill(X0, Y0 + r, X0 + t, Y1 - r, argb);
            g.fill(X1 - t, Y0 + r, X1, Y1 - r, argb);
        } finally {
            g.pose().popMatrix();
        }
    }

    public static void circle(float cx, float cy, float r, int argb) {
        disc(cx, cy, r, argb, 0f);
    }

    public static void ring(float cx, float cy, float r, int argb, float thickness) {
        disc(cx, cy, r, argb, Math.max(thickness, 1f / scale));
    }

    private static void disc(float cx, float cy, float r, int argb, float thickness) {
        if (g == null || (argb >>> 24) == 0 || r <= 0f) return;
        int R = Math.max(1, Math.round(r * scale));
        int CX = Math.round(cx * scale), CY = Math.round(cy * scale);
        int t = thickness > 0f ? Math.max(1, Math.round(thickness * scale)) : 0;
        pixels();
        try {
            corners(Textures.corner(R, t), CX - R, CY - R, CX + R, CY + R, R, argb);
        } finally {
            g.pose().popMatrix();
        }
    }

    /** A straight line; only horizontal and vertical ones are drawn sharp. */
    public static void line(float x0, float y0, float x1, float y1, int argb, float thickness) {
        float h = Math.max(thickness, 1f / scale()) / 2f;
        if (Math.abs(y1 - y0) < Math.abs(x1 - x0)) rect(Math.min(x0, x1), y0 - h, Math.max(x0, x1), y0 + h, 0f, argb);
        else rect(x0 - h, Math.min(y0, y1), x0 + h, Math.max(y0, y1), 0f, argb);
    }

    /** A vertical gradient from top to bottom colour. */
    public static void gradient(float x0, float y0, float x1, float y1, int top, int bottom) {
        if (g == null) return;
        pixels();
        try {
            g.fillGradient(Math.round(x0 * scale), Math.round(y0 * scale), Math.round(x1 * scale), Math.round(y1 * scale), top, bottom);
        } finally {
            g.pose().popMatrix();
        }
    }

    // ------------------------------------------------------------------ pictures

    /** A PNG from the mod's jar (for example "/assets/golemsclient/textures/logo.png"), tinted by argb. */
    public static void image(String path, float x0, float y0, float x1, float y1, int argb) {
        if (g == null || (argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) return;
        GuiTexture t = Textures.resource(path, Math.round((x1 - x0) * scale), Math.round((y1 - y0) * scale));
        if (t != null) texture(t, x0, y0, x1, y1, 0f, 0f, 1f, 1f, argb);
    }

    /** One of the mod's icons (assets/golemsclient/textures/icons/NAME.png), white tinted by argb, size x size. */
    public static void icon(String name, float x, float y, float size, int argb) {
        image("/assets/golemsclient/textures/icons/" + name + ".png", x, y, x + size, y + size, argb);
    }

    /** Part of a texture made by Golem's Client (from (u0, v0) to (u1, v1)). */
    public static void texture(GuiTexture t, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        if (g == null || t == null || (argb >>> 24) == 0) return;
        draw(t.getTextureView(), t.getSampler(), x0, y0, x1, y1, u0, v0, u1, v1, argb);
    }

    /** Part of one of Minecraft's textures (a skin), crisp. */
    public static void minecraft(Identifier id, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        if (g == null || (argb >>> 24) == 0) return;
        try {
            var t = Minecraft.getInstance().getTextureManager().getTexture(id);
            if (pixelSampler == null) pixelSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            draw(t.getTextureView(), pixelSampler, x0, y0, x1, y1, u0, v0, u1, v1, argb);
        } catch (Exception ignored) {
            // not loaded yet
        }
    }

    private static void draw(GpuTextureView view, GpuSampler sampler, float x0, float y0, float x1, float y1, float u0, float v0,
                             float u1, float v1, int argb) {
        pixels();
        try {
            submit(view, sampler, Math.round(x0 * scale), Math.round(y0 * scale), Math.round(x1 * scale), Math.round(y1 * scale),
                    u0, u1, v0, v1, argb);
        } finally {
            g.pose().popMatrix();
        }
    }

    // ------------------------------------------------------------------ clipping

    /** Draws only inside this rectangle until {@link #unclip()}. */
    public static void clip(float x0, float y0, float x1, float y1) {
        if (g == null) return;
        g.enableScissor((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1), (int) Math.ceil(y1));
    }

    public static void unclip() {
        if (g == null) return;
        g.disableScissor();
    }

    // ------------------------------------------------------------------ internals

    /** Pushes a pose in which one unit is one real pixel. */
    private static void pixels() {
        g.pose().pushMatrix();
        g.pose().scale(1f / scale, 1f / scale);
    }

    /** The four corners of a shape, from a top-left corner piece, flipped for the others. */
    private static void corners(GuiTexture c, int X0, int Y0, int X1, int Y1, int r, int argb) {
        GpuTextureView v = c.getTextureView();
        GpuSampler s = c.getSampler();
        submit(v, s, X0, Y0, X0 + r, Y0 + r, 0f, 1f, 0f, 1f, argb);
        submit(v, s, X1 - r, Y0, X1, Y0 + r, 1f, 0f, 0f, 1f, argb);
        submit(v, s, X0, Y1 - r, X0 + r, Y1, 0f, 1f, 1f, 0f, argb);
        submit(v, s, X1 - r, Y1 - r, X1, Y1, 1f, 0f, 1f, 0f, argb);
    }

    private static void submit(GpuTextureView view, GpuSampler sampler, int x0, int y0, int x1, int y1, float u0, float u1,
                               float v0, float v1, int argb) {
        try {
            g.guiRenderState.submitGuiElement(new BlitRenderState(RenderPipelines.GUI_TEXTURED,
                    TextureSetup.singleTexture(view, sampler), new Matrix3x2f(g.pose()), x0, y0, x1, y1, u0, u1, v0, v1, argb,
                    g.scissorStack.peek()));
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                GolemsClient.LOGGER.error("Golem's Client couldn't draw something", e);
            }
        }
    }

    private static boolean warned;
}
