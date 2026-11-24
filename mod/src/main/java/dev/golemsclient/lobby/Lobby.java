package dev.golemsclient.lobby;

import dev.golemsclient.GolemsClient;
import dev.golemsclient.menu.MainMenu;
import dev.golemsclient.menu.MenuData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The lobby: a real Minecraft world behind the main menu, like OneClient's. A small hidden world (its own folder, never
 * in your world list) is made from a scene, the camera is set where the scene says, and your own player runs in from
 * the side, stops and turns to you; then the menu fades in. Joining a server or opening a world simply leaves it.
 *
 * <p>Phases: OFF, PENDING (waiting for the game to finish loading), LOADING (making the world), REVEAL (the camera
 * swoops in and you run in), READY (the menu is up).
 */
public final class Lobby {
    public enum Phase { OFF, PENDING, LOADING, REVEAL, READY }

    private static final String LEVEL_ID = "lobby";
    private static final long REVEAL_MS = 2600L;
    private static final double CLOSER = 0.7;

    private static Phase phase = Phase.OFF;
    private static Scene scene;
    private static MinecraftServer server;
    private static boolean expecting;
    private static boolean failed;
    private static int x0, z0;
    private static long phaseAt;
    private static long placedAt;
    private static CameraType savedCamera;
    private static LobbyInput input;
    private static boolean arrived;
    private static LobbyScreen screen;
    /** Dev only: a camera pose to look through instead of the scene's (scene previews). */
    public static double[] previewCamera;

    private Lobby() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(Lobby::onServerStarted);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> onJoin(handler.player, srv));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
    }

    // ------------------------------------------------------------------ state

    /** On unless turned off in the lobby settings (or it failed this session, or there are no scenes). */
    public static boolean enabled() {
        return !failed && MenuData.get().lobbyEnabled && !Scenes.all().isEmpty() && !Boolean.getBoolean("golemsclient.noLobby");
    }

    public static Phase phase() {
        return phase;
    }

    /** True while the lobby world is what the game is showing. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return server != null && mc.getSingleplayerServer() == server && mc.level != null;
    }

    /** True while the lobby is being made or shown, or being left. */
    public static boolean busy() {
        return phase != Phase.OFF;
    }

    public static Scene scene() {
        return scene;
    }

    /** The screen shown while the lobby loads and while it reveals itself. */
    public static Screen screen() {
        if (screen == null) screen = new LobbyScreen();
        return screen;
    }

    /** Asks for the lobby (when the main menu would open with no world). Returns the screen to show meanwhile. */
    public static Screen request() {
        if (phase == Phase.OFF) {
            phase = Phase.PENDING;
            phaseAt = System.currentTimeMillis();
            screen = new LobbyScreen();
        }
        return screen();
    }

    /** The reveal is over (or skipped): the menu comes up. */
    public static void finishReveal() {
        if (phase != Phase.REVEAL) return;
        phase = Phase.READY;
        phaseAt = System.currentTimeMillis();
        Minecraft.getInstance().setScreen(new MainMenu());
    }

    public static float revealProgress() {
        if (phase == Phase.READY) return 1f;
        if (phase != Phase.REVEAL) return 0f;
        return Mth.clamp((System.currentTimeMillis() - phaseAt) / (float) REVEAL_MS, 0f, 1f);
    }

    public static long phaseStarted() {
        return phaseAt;
    }

    // ------------------------------------------------------------------ making the world

    private static void begin() {
        Minecraft mc = Minecraft.getInstance();
        Scenes.Entry entry = Scenes.pick(MenuData.get().lobbyScene);
        if (entry == null) {
            fail("there are no scenes", null);
            return;
        }
        try {
            scene = entry.load();
        } catch (Exception e) {
            fail("couldn't read scene " + entry.id(), e);
            return;
        }
        phase = Phase.LOADING;
        phaseAt = System.currentTimeMillis();
        try {
            Path root = mc.gameDirectory.toPath().resolve("golemsclient").resolve("lobby");
            deleteFolder(root.resolve(LEVEL_ID));
            Files.createDirectories(root);
            LevelStorageSource storage = LevelStorageSource.createDefault(root);

            WorldDataConfiguration cfg = WorldDataConfiguration.DEFAULT;
            GameRules rules = new GameRules(cfg.enabledFeatures());
            rules.set(GameRules.ADVANCE_TIME, false, null);
            rules.set(GameRules.ADVANCE_WEATHER, false, null);
            rules.set(GameRules.SPAWN_MOBS, false, null);
            rules.set(GameRules.SPAWN_MONSTERS, false, null);
            rules.set(GameRules.SPAWN_PATROLS, false, null);
            rules.set(GameRules.SPAWN_PHANTOMS, false, null);
            rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, null);
            rules.set(GameRules.RANDOM_TICK_SPEED, 0, null);
            rules.set(GameRules.SPREAD_VINES, false, null);
            rules.set(GameRules.FALL_DAMAGE, false, null);
            rules.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, null);
            rules.set(GameRules.SEND_COMMAND_FEEDBACK, false, null);
            rules.set(GameRules.LOG_ADMIN_COMMANDS, false, null);
            rules.set(GameRules.PLAYER_MOVEMENT_CHECK, false, null);
            rules.set(GameRules.RESPAWN_RADIUS, 0, null);
            rules.set(GameRules.MOB_GRIEFING, false, null);
            LevelSettings settings = new LevelSettings("Golem's Client lobby", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, cfg);
            String biomeId = scene.biome;
            expecting = true;
            new net.minecraft.client.gui.screens.worldselection.WorldOpenFlows(mc, storage).createFreshLevel(LEVEL_ID, settings,
                    new WorldOptions(0L, false, false), provider -> {
                        var biomes = provider.lookupOrThrow(Registries.BIOME);
                        var biome = biomes.get(ResourceKey.create(Registries.BIOME, Identifier.parse(biomeId)))
                                .orElseGet(() -> biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS));
                        FlatLevelGeneratorSettings flat = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct(List.of())),
                                (net.minecraft.core.Holder<Biome>) biome, List.of());
                        return WorldPresets.createFlatWorldDimensions(provider).replaceOverworldGenerator(provider, new FlatLevelSource(flat));
                    }, screen());
        } catch (Throwable t) {
            expecting = false;
            fail("couldn't make the lobby world", t);
        }
    }

    /** Server thread: the lobby's server is up; build the scene before anyone joins. */
    private static void onServerStarted(MinecraftServer srv) {
        if (!expecting) return;
        expecting = false;
        server = srv;
        Scene s = scene;
        if (s == null) return;
        try {
            ServerLevel level = srv.overworld();
            x0 = -s.sx / 2;
            z0 = -s.sz / 2;
            s.build(level, x0, z0);
            level.setDayTime(timeOfDay(s));
            if (s.rain) level.setWeatherParameters(0, 6000, true, false);
            else level.setWeatherParameters(6000, 0, false, false);
            Vec3 start = start();
            run(srv, "setworldspawn " + Mth.floor(start.x) + " " + Mth.floor(start.y) + " " + Mth.floor(start.z));
        } catch (Throwable t) {
            GolemsClient.LOGGER.error("Couldn't build the lobby scene", t);
        }
    }

    /** Server thread: you joined the lobby; you're put at the scene's start. */
    private static void onJoin(ServerPlayer player, MinecraftServer srv) {
        if (srv != server || scene == null) return;
        Vec3 start = start();
        Vec3 target = target();
        float yaw = yawTowards(start, target);
        run(srv, String.format(java.util.Locale.ROOT, "tp %s %.3f %.3f %.3f %.1f 0", player.getName().getString(), start.x, start.y,
                start.z, yaw));
    }

    private static void run(MinecraftServer srv, String command) {
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static long timeOfDay(Scene s) {
        return switch (MenuData.get().lobbyTime) {
            case "day" -> 6000L;
            case "sunset" -> 12000L;
            case "night" -> 18000L;
            case "real" -> {
                var now = java.time.LocalTime.now();
                // 6:00 real time is sunrise (0); a real day is a Minecraft day
                long minutes = (now.getHour() * 60L + now.getMinute() - 360L + 1440L) % 1440L;
                yield minutes * 24000L / 1440L;
            }
            default -> s.time;
        };
    }

    // ------------------------------------------------------------------ every tick

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (phase == Phase.OFF) return;

        // the lobby was left (a server joined, a world opened, the game closing)
        if ((phase == Phase.REVEAL || phase == Phase.READY) && !active()) {
            stop();
            return;
        }
        switch (phase) {
            case PENDING -> {
                if (mc.getOverlay() == null && System.currentTimeMillis() - phaseAt > 150L) begin();
            }
            case LOADING -> {
                if (!active() || mc.player == null) {
                    if (System.currentTimeMillis() - phaseAt > 90_000L) fail("the lobby took too long", null);
                    return;
                }
                Vec3 start = start();
                if (mc.player.position().distanceToSqr(start) > 4.0) {
                    placedAt = 0L;
                    return;
                }
                if (placedAt == 0L) placedAt = System.currentTimeMillis();
                // a moment for the chunks around to be built
                if (System.currentTimeMillis() - placedAt < 1200L) return;
                beginReveal(mc);
            }
            case REVEAL, READY -> drive(mc);
            default -> { }
        }
    }

    private static void beginReveal(Minecraft mc) {
        phase = Phase.REVEAL;
        phaseAt = System.currentTimeMillis();
        arrived = false;
        if (savedCamera == null) savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        input = new LobbyInput();
        mc.player.input = input;
        if (!MenuData.get().lobbyOpening) {
            // no opening animation: start where you stop
            Vec3 t = target();
            mc.player.snapTo(t.x, t.y, t.z, faceCameraYaw(), 0f);
            arrived = true;
            finishReveal();
        }
    }

    /** Walks you from the start to the target, then turns you to the camera. */
    private static void drive(Minecraft mc) {
        if (mc.player == null) return;
        if (mc.player.input != input) mc.player.input = input;
        long since = System.currentTimeMillis() - phaseAt;
        Vec3 target = target();
        Vec3 pos = mc.player.position();
        double dx = target.x - pos.x, dz = target.z - pos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        boolean go = phase == Phase.READY || since > 500L;
        if (!arrived && go && dist > 0.3) {
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            mc.player.setYRot(Mth.approachDegrees(mc.player.getYRot(), yaw, 25f));
            mc.player.setYHeadRot(mc.player.getYRot());
            input.walk(true, mc.player.horizontalCollision && mc.player.onGround(), false);
        } else {
            if (!arrived && go) arrived = true;
            input.walk(false, false, false);
            if (arrived) {
                float face = faceCameraYaw() + LobbyInput.turnOffset;
                mc.player.setYRot(Mth.approachDegrees(mc.player.getYRot(), face, 12f));
                mc.player.yBodyRot = Mth.approachDegrees(mc.player.yBodyRot, face, 12f);
                mc.player.setYHeadRot(Mth.approachDegrees(mc.player.getYHeadRot(), face + LobbyInput.lookYaw, 10f));
                mc.player.setXRot(Mth.approach(mc.player.getXRot(), LobbyInput.lookPitch, 4f));
            }
        }
        if (phase == Phase.REVEAL && (arrived || since > 9000L) && since > REVEAL_MS) finishReveal();
    }

    private static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (savedCamera != null) {
            mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }
        phase = Phase.OFF;
        server = null;
        scene = null;
        input = null;
        screen = null;
    }

    private static void fail(String why, Throwable t) {
        GolemsClient.LOGGER.error("Lobby off for this session: {}", why, t);
        failed = true;
        phase = Phase.OFF;
        scene = null;
        screen = null;
        Minecraft.getInstance().setScreen(new MainMenu());
    }

    // ------------------------------------------------------------------ the camera

    public static Vec3 target() {
        return new Vec3(x0 + scene.targetX, scene.bottom + scene.targetY, z0 + scene.targetZ);
    }

    public static Vec3 start() {
        return new Vec3(x0 + scene.startX, scene.bottom + scene.startY, z0 + scene.startZ);
    }

    public static float faceCameraYaw() {
        return Mth.wrapDegrees(scene.camYaw + 180f);
    }

    private static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90f;
    }

    /** True while the camera is ours. */
    public static boolean ownsCamera() {
        return previewCamera != null || (active() && scene != null && (phase == Phase.REVEAL || phase == Phase.READY
                || phase == Phase.LOADING));
    }

    /** The camera now: {x, y, z, yaw, pitch}. Swoops down from above during the reveal, then sways gently. */
    public static double[] camera() {
        if (previewCamera != null) return previewCamera;
        double cx = x0 + scene.camX, cy = scene.bottom + scene.camY, cz = z0 + scene.camZ;
        float yaw = scene.camYaw, pitch = scene.camPitch;
        // a bit closer than the scene's camera, so you're bigger in the picture (still on the same clear line)
        Vec3 aim = target().add(0, 1.0, 0);
        cx = aim.x + (cx - aim.x) * CLOSER;
        cy = aim.y + (cy - aim.y) * CLOSER;
        cz = aim.z + (cz - aim.z) * CLOSER;
        double t = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        // a slow drift, so the picture is alive
        cx += Math.sin(t * 0.21) * 0.18;
        cy += Math.sin(t * 0.17) * 0.10;
        yaw += (float) (Math.sin(t * 0.13) * 0.6);
        if (phase == Phase.LOADING || phase == Phase.REVEAL) {
            float k = phase == Phase.LOADING ? 0f : revealProgress();
            float e = 1f - (float) Math.pow(1f - k, 3);
            double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
            cx -= fx * 9.0 * (1f - e);
            cz -= fz * 9.0 * (1f - e);
            cy += 20.0 * (1f - e);
            pitch = Mth.lerp(e, 62f, pitch);
        }
        return new double[] {cx, cy, cz, yaw, pitch};
    }

    /**
     * Where a point of the lobby world is on screen, in GUI pixels ({x, y}), seen from the lobby camera; null when
     * it is behind the camera.
     */
    public static float[] project(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        double[] c = camera();
        double yaw = Math.toRadians(c[3]), pitch = Math.toRadians(c[4]);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        // up = right x forward
        double ux = 0 * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - 0 * fx;
        double dx = point.x - c[0], dy = point.y - c[1], dz = point.z - c[2];
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.05) return null;
        var window = mc.getWindow();
        float gw = window.getGuiScaledWidth(), gh = window.getGuiScaledHeight();
        double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double nx = (dx * rx + dz * rz) / (depth * tan * (gw / gh));
        double ny = (dx * ux + dy * uy + dz * uz) / (depth * tan);
        return new float[] {(float) ((1 + nx) / 2 * gw), (float) ((1 - ny) / 2 * gh)};
    }

    // ------------------------------------------------------------------ helpers

    private static void deleteFolder(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            GolemsClient.LOGGER.warn("Couldn't clear the old lobby world", e);
        }
    }
}
