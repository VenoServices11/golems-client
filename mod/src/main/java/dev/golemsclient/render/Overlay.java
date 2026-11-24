package dev.golemsclient.render;

import dev.golemsclient.GolemsClient;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Golem's Client's smooth text, shapes and images, drawn with Dear ImGui on top of the finished frame at the window's
 * real resolution. Screens record what they want in GUI pixels while Minecraft draws; {@link #renderFrame()} (called
 * right before the frame is shown) draws it all, in order, and forgets it. Nothing here may crash the game: if ImGui
 * keeps failing it switches itself off and the game looks vanilla.
 */
public final class Overlay {
    public enum Face { REGULAR, MEDIUM, SEMIBOLD, BOLD, MONO }

    public enum Align { LEFT, CENTER, RIGHT }

    private record Text(Face face, String s, float x, float y, float size, int argb, Align align) { }

    private record Rect(float x0, float y0, float x1, float y1, float radius, int argb) { }

    private record Image(String path, float x0, float y0, float x1, float y1, int argb) { }

    private static final List<Object> OPS = new ArrayList<>();
    private static final ImGuiImplGlfw GLFW_IMPL = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 GL3_IMPL = new ImGuiImplGl3();

    private static boolean ready;
    private static boolean failed;
    private static int failures;

    private Overlay() {
    }

    /** False once ImGui has switched itself off; callers can then fall back to Minecraft's own drawing. */
    public static boolean available() {
        return !failed;
    }

    // ------------------------------------------------------------------ recording (GUI pixels)

    public static void text(Face face, String s, float x, float y, float size, int argb, Align align) {
        if (failed || s == null || s.isEmpty() || (argb >>> 24) == 0) return;
        OPS.add(new Text(face, s, x, y, size, argb, align));
    }

    public static void rect(float x0, float y0, float x1, float y1, float radius, int argb) {
        if (failed || (argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) return;
        OPS.add(new Rect(x0, y0, x1, y1, radius, argb));
    }

    /** A PNG from the mod's jar (for example "/assets/golemsclient/textures/logo.png"), tinted by argb. */
    public static void image(String path, float x0, float y0, float x1, float y1, int argb) {
        if (failed || (argb >>> 24) == 0) return;
        OPS.add(new Image(path, x0, y0, x1, y1, argb));
    }

    /** Text width in GUI pixels, for centring; an estimate until the fonts are ready. */
    public static float width(Face face, String s, float size) {
        ImFont f = ready ? Fonts.pick(face, size * guiScale()) : null;
        if (f == null) return s.length() * size * 0.55f;
        float px = size * guiScale();
        return f.calcTextSizeAX(px, Float.MAX_VALUE, 0f, s) / guiScale();
    }

    private static float guiScale() {
        var w = Minecraft.getInstance().getWindow();
        int gh = w.getGuiScaledHeight();
        return gh > 0 ? (float) w.getHeight() / gh : 1f;
    }

    // ------------------------------------------------------------------ drawing

    public static void renderFrame() {
        if (failed || OPS.isEmpty()) {
            OPS.clear();
            return;
        }
        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            if (!ready) init();
            Minecraft mc = Minecraft.getInstance();
            float gw = mc.getWindow().getGuiScaledWidth();
            float gh = mc.getWindow().getGuiScaledHeight();
            if (gw <= 0 || gh <= 0) return;

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            ImGuiIO io = ImGui.getIO();
            if (Fonts.rebuildFor(io, mc.getWindow().getHeight() / gh)) {
                GL3_IMPL.destroyFontsTexture();
                GL3_IMPL.createFontsTexture();
            }
            GL3_IMPL.newFrame();
            GLFW_IMPL.newFrame();
            ImGui.newFrame();

            ImVec2 size = io.getDisplaySize();
            draw(ImGui.getBackgroundDrawList(), size.x / gw, size.y / gh);

            ImGui.render();
            GL3_IMPL.renderDrawData(ImGui.getDrawData());
            failures = 0;
        } catch (Throwable t) {
            if (++failures >= 3) {
                failed = true;
                GolemsClient.LOGGER.error("Golem's Client's overlay keeps failing; it's off for this session", t);
            } else {
                GolemsClient.LOGGER.error("Golem's Client's overlay failed; starting it again", t);
                restart();
            }
        } finally {
            OPS.clear();
            try {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void draw(ImDrawList dl, float fx, float fy) {
        for (Object op : OPS) {
            if (op instanceof Rect r) {
                dl.addRectFilled(r.x0() * fx, r.y0() * fy, r.x1() * fx, r.y1() * fy, abgr(r.argb()), r.radius() * fy);
            } else if (op instanceof Image im) {
                int tex = Textures.get(im.path());
                if (tex != 0) {
                    dl.addImage(tex, im.x0() * fx, im.y0() * fy, im.x1() * fx, im.y1() * fy, 0f, 0f, 1f, 1f, abgr(im.argb()));
                }
            } else if (op instanceof Text t) {
                float px = t.size() * fy;
                ImFont f = Fonts.pick(t.face(), px);
                if (f == null) continue;
                float x = t.x() * fx;
                if (t.align() != Align.LEFT) {
                    float w = f.calcTextSizeAX(px, Float.MAX_VALUE, 0f, t.s());
                    x -= t.align() == Align.RIGHT ? w : w / 2f;
                }
                dl.addText(f, px, x, t.y() * fy, abgr(t.argb()), t.s());
            }
        }
    }

    private static void init() {
        long window = GLFW.glfwGetCurrentContext();
        ImGui.createContext();
        // ImGui's own checks are logged instead of closing the game (imgui-java's default)
        ImGui.setAssertCallback(new imgui.assertion.ImAssertCallback() {
            private int logged;

            @Override
            public void imAssert(String assertion, int line, String file) {
                imAssertCallback(assertion, line, file);
            }

            @Override
            public void imAssertCallback(String assertion, int line, String file) {
                if (logged++ < 10) GolemsClient.LOGGER.warn("ImGui check failed: {} ({}:{})", assertion, file, line);
            }
        });
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.addConfigFlags(ImGuiConfigFlags.NoMouseCursorChange);
        ImGui.getStyle().setAntiAliasedFill(true);
        ImGui.getStyle().setAntiAliasedLines(true);
        ImGui.getStyle().setCircleTessellationMaxError(0.12f);
        // no input callbacks: the overlay only draws (screens that need the mouse come with the main menu)
        GLFW_IMPL.init(window, false);
        Fonts.rebuildFor(io, 2f);
        GL3_IMPL.init("#version 150");
        ready = true;
        GolemsClient.LOGGER.info("Overlay ready");
    }

    private static void restart() {
        try {
            GL3_IMPL.shutdown();
        } catch (Throwable ignored) {
        }
        try {
            GLFW_IMPL.shutdown();
        } catch (Throwable ignored) {
        }
        try {
            ImGui.destroyContext();
        } catch (Throwable ignored) {
        }
        Textures.forget();
        Fonts.forget();
        ready = false;
    }

    static int abgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
