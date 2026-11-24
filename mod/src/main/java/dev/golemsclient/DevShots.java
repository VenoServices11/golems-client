package dev.golemsclient;

import dev.golemsclient.menu.MainMenu;
import dev.golemsclient.menu.MenuData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImageWrite;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Development only (on with -Dgolemsclient.devshots=true, never in normal play): walks through Golem's Client's
 * screens by itself and saves a screenshot of each, overlay included, into run/devshots, for the "Dev screenshots"
 * workflow.
 */
public final class DevShots {
    public static final boolean ON = Boolean.getBoolean("golemsclient.devshots");

    private record Step(String name, Runnable action, long waitMs, BooleanSupplier ready) { }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1;
    private static long stepAt;
    private static long menuSince;
    private static long readyAt;
    private static String pendingShot;

    private DevShots() {
    }

    public static void register() {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        MenuData.played("DonutSMP", "donutsmp.net");
        MenuData.played("Hypixel", "mc.hypixel.net");
        MenuData.played("DonutSMP", "donutsmp.net");
        dev.golemsclient.menu.Announcement.devFake("Live tonight at 8 PM on TikTok!");
        step("00_startup", () -> { }, 300, () -> dev.golemsclient.lobby.Lobby.phase() == dev.golemsclient.lobby.Lobby.Phase.LOADING);
        step("01_reveal", () -> { }, 1300, () -> dev.golemsclient.lobby.Lobby.phase() == dev.golemsclient.lobby.Lobby.Phase.REVEAL);
        step("02_lobby_menu", () -> { }, 3500, () -> mc.screen instanceof MainMenu);
        step("03_social", () -> MainMenu.devSocial(true), 1200, null);
        step("04_tab_toast", () -> {
            MainMenu.devSocial(false);
            MainMenu.devTab(1);
        }, 900, null);
        step("05_in_world", DevShots::createWorld, 3000,
                () -> mc.player != null && mc.level != null && mc.getOverlay() == null && mc.screen == null && !dev.golemsclient.lobby.Lobby.active());
        step("06_pause_menu", () -> mc.pauseGame(false), 1500, null);
        step("07_lobby_again", () -> mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE), 3500,
                () -> mc.screen instanceof MainMenu && dev.golemsclient.lobby.Lobby.active());
        ClientTickEvents.END_CLIENT_TICK.register(DevShots::tick);
    }

    private static void step(String name, Runnable action, long waitMs, BooleanSupplier ready) {
        STEPS.add(new Step(name, action, waitMs, ready));
    }

    private static void createWorld() {
        Minecraft mc = Minecraft.getInstance();
        try {
            var cfg = net.minecraft.world.level.WorldDataConfiguration.DEFAULT;
            var rules = new net.minecraft.world.level.gamerules.GameRules(cfg.enabledFeatures());
            var settings = new net.minecraft.world.level.LevelSettings("Golem Test World", net.minecraft.world.level.GameType.CREATIVE,
                    false, net.minecraft.world.Difficulty.PEACEFUL, true, rules, cfg);
            mc.createWorldOpenFlows().createFreshLevel("devshots", settings,
                    new net.minecraft.world.level.levelgen.WorldOptions(20261124L, false, false),
                    net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, new MainMenu());
        } catch (Throwable t) {
            GolemsClient.LOGGER.error("[DevShots] couldn't make a world", t);
        }
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (index < 0) {
            if (mc.getOverlay() != null) return;
            index = 0;
            stepAt = now;
            run(0);
            return;
        }
        if (pendingShot != null || index >= STEPS.size()) return;
        Step s = STEPS.get(index);
        if (s.ready() != null && now - stepAt < 240_000L) {
            if (!s.ready().getAsBoolean()) {
                readyAt = 0;
                return;
            }
            if (readyAt == 0) readyAt = now;
            if (now - readyAt < s.waitMs()) return;
        } else if (now - stepAt < s.waitMs()) {
            return;
        }
        readyAt = 0;
        pendingShot = s.name();
    }

    private static void run(int i) {
        try {
            STEPS.get(i).action().run();
        } catch (Throwable t) {
            GolemsClient.LOGGER.error("[DevShots] step {} failed", STEPS.get(i).name(), t);
        }
    }

    /** Right after the overlay drew into the window: saves the frame if a shot is due, then runs the next step. */
    public static void afterFrame() {
        if (SceneMaker.ON && SceneMaker.pendingShot != null) {
            String id = SceneMaker.pendingShot;
            SceneMaker.pendingShot = null;
            save(Minecraft.getInstance().gameDirectory.toPath().resolve("scenes").resolve(id + ".png"));
            return;
        }
        if (!ON || pendingShot == null) return;
        String name = pendingShot;
        pendingShot = null;
        save(Minecraft.getInstance().gameDirectory.toPath().resolve("devshots").resolve(name + ".png"));
        next();
    }

    private static void save(Path file) {
        try {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            int prev = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            ByteBuffer px = BufferUtils.createByteBuffer(w * h * 4);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
            ByteBuffer flipped = BufferUtils.createByteBuffer(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int i = 0; i < w * 4; i++) {
                    byte b = px.get((h - 1 - y) * w * 4 + i);
                    flipped.put(y * w * 4 + i, (i & 3) == 3 ? (byte) 0xFF : b);
                }
            }
            Files.createDirectories(file.getParent());
            STBImageWrite.stbi_write_png(file.toString(), w, h, 4, flipped, w * 4);
            GolemsClient.LOGGER.info("[DevShots] saved {}", file.getFileName());
        } catch (Throwable t) {
            GolemsClient.LOGGER.error("[DevShots] couldn't save {}", file, t);
        }
    }

    private static void next() {
        index++;
        if (index >= STEPS.size()) {
            GolemsClient.LOGGER.info("[DevShots] done");
            Minecraft.getInstance().stop();
            Thread halt = new Thread(() -> {
                try {
                    Thread.sleep(20_000L);
                } catch (InterruptedException ignored) {
                }
                Runtime.getRuntime().halt(0);
            });
            halt.setDaemon(true);
            halt.start();
            return;
        }
        stepAt = System.currentTimeMillis();
        run(index);
    }
}
