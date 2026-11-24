package dev.golemsclient;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** The red G on the game window and in the taskbar, instead of the grass block. */
public final class WindowIcon {
    private static final String DIR = "/assets/golemsclient/icons/";
    private static final int[] SIZES = {16, 32, 48, 128, 256};

    private WindowIcon() {
    }

    /** True when the icon was set, so Minecraft's own one is skipped. */
    public static boolean apply(long window) {
        List<ByteBuffer> files = new ArrayList<>();
        List<ByteBuffer> pixels = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer images = GLFWImage.malloc(SIZES.length, stack);
            int count = 0;
            for (int size : SIZES) {
                byte[] png;
                try (InputStream in = WindowIcon.class.getResourceAsStream(DIR + "icon_" + size + "x" + size + ".png")) {
                    if (in == null) continue;
                    png = in.readAllBytes();
                }
                ByteBuffer file = MemoryUtil.memAlloc(png.length);
                file.put(png).flip();
                files.add(file);
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer c = stack.mallocInt(1);
                ByteBuffer image = STBImage.stbi_load_from_memory(file, w, h, c, 4);
                if (image == null) continue;
                pixels.add(image);
                images.position(count).width(w.get(0)).height(h.get(0)).pixels(image);
                count++;
            }
            if (count == 0) return false;
            images.position(0).limit(count);
            GLFW.glfwSetWindowIcon(window, images);
            return true;
        } catch (Exception | LinkageError e) {
            GolemsClient.LOGGER.warn("Couldn't set the window icon", e);
            return false;
        } finally {
            for (ByteBuffer b : pixels) STBImage.stbi_image_free(b);
            for (ByteBuffer b : files) MemoryUtil.memFree(b);
        }
    }
}
