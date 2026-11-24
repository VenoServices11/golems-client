package dev.golemsclient;

import com.mojang.datafixers.util.Pair;
import dev.golemsclient.lobby.Lobby;
import dev.golemsclient.lobby.Scene;
import dev.golemsclient.menu.MainMenu;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Development only (on with -Dgolemsclient.makescenes=true): makes the built-in lobby scenes. It opens a world, finds
 * a snowy village, a cherry grove, a desert village, a taiga village, a plains village and a lush cave, picks a spot
 * to stand and a camera for each, captures them, and saves each scene with a preview picture to run/scenes.
 */
public final class SceneMaker {
    public static final boolean ON = Boolean.getBoolean("golemsclient.makescenes");
    private static final long SEED = Long.getLong("golemsclient.makescenes.seed", 1234567890123L);

    private record Spec(String id, String name, String season, ResourceKey<Structure> structure, ResourceKey<Biome> biome,
                        boolean cave, long time) { }

    private static final List<Spec> SPECS = List.of(
            new Spec("snowy_village", "Snowy village", "winter", BuiltinStructures.VILLAGE_SNOWY, null, false, 1500L),
            new Spec("cherry_grove", "Cherry grove", "spring", null, Biomes.CHERRY_GROVE, false, 2500L),
            new Spec("desert_village", "Desert village", "summer", BuiltinStructures.VILLAGE_DESERT, null, false, 3500L),
            new Spec("taiga_village", "Taiga village", "autumn", BuiltinStructures.VILLAGE_TAIGA, null, false, 11600L),
            new Spec("plains_village", "Plains village", "", BuiltinStructures.VILLAGE_PLAINS, null, false, 6000L),
            new Spec("lush_cave", "Lush cave", "", null, Biomes.LUSH_CAVES, true, 6000L));

    private enum Step { WAIT_MENU, WAIT_WORLD, LOCATE, WAIT_CHUNKS, SHOOT, DONE }

    private static Step step = Step.WAIT_MENU;
    private static long stepAt;
    private static int index;
    private static BlockPos found;
    private static final List<String> made = new ArrayList<>();
    public static volatile String pendingShot;

    private SceneMaker() {
    }

    public static void register() {
        if (!ON) return;
        ClientTickEvents.END_CLIENT_TICK.register(SceneMaker::tick);
    }

    private static Path out() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("scenes");
    }

    private static void go(Step s) {
        step = s;
        stepAt = System.currentTimeMillis();
    }

    private static long since() {
        return System.currentTimeMillis() - stepAt;
    }

    private static void tick(Minecraft mc) {
        try {
            switch (step) {
                case WAIT_MENU -> {
                    if (mc.screen instanceof MainMenu && mc.getOverlay() == null) {
                        if (stepAt == 0) stepAt = System.currentTimeMillis();
                        if (since() > 3000) {
                            createWorld(mc);
                            go(Step.WAIT_WORLD);
                        }
                    }
                }
                case WAIT_WORLD -> {
                    if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && since() > 4000) {
                        mc.setScreen(null);
                        cmd(mc, "gamemode spectator @a");
                        mc.options.hideGui = true;
                        go(Step.LOCATE);
                    }
                }
                case LOCATE -> {
                    if (index >= SPECS.size()) {
                        finish(mc);
                        return;
                    }
                    Spec spec = SPECS.get(index);
                    found = locate(mc.getSingleplayerServer(), spec);
                    if (found == null) {
                        GolemsClient.LOGGER.warn("[SceneMaker] couldn't find {}", spec.id());
                        index++;
                        return;
                    }
                    GolemsClient.LOGGER.info("[SceneMaker] {} at {}", spec.id(), found);
                    int y = spec.cave() ? found.getY() : 160;
                    cmd(mc, "tp @a " + found.getX() + " " + y + " " + found.getZ());
                    cmd(mc, "time set " + spec.time());
                    cmd(mc, "weather clear");
                    go(Step.WAIT_CHUNKS);
                }
                case WAIT_CHUNKS -> {
                    if (since() < 4000) return;
                    int r = 6, cx = found.getX() >> 4, cz = found.getZ() >> 4;
                    boolean all = true;
                    for (int x = -r; x <= r && all; x++) {
                        for (int z = -r; z <= r && all; z++) all = mc.level.getChunkSource().hasChunk(cx + x, cz + z);
                    }
                    if (!all && since() < 60000) return;
                    if (since() < 9000) return;
                    make(mc, SPECS.get(index));
                    go(Step.SHOOT);
                }
                case SHOOT -> {
                    if (since() > 3500 && pendingShot == null) {
                        pendingShot = SPECS.get(index).id();
                        index++;
                        go(Step.LOCATE);
                    }
                }
                default -> { }
            }
        } catch (Throwable t) {
            GolemsClient.LOGGER.error("[SceneMaker] failed at {}", step, t);
            index++;
            go(Step.LOCATE);
        }
    }

    private static void createWorld(Minecraft mc) {
        var cfg = net.minecraft.world.level.WorldDataConfiguration.DEFAULT;
        var rules = new net.minecraft.world.level.gamerules.GameRules(cfg.enabledFeatures());
        rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, null);
        rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER, false, null);
        var settings = new net.minecraft.world.level.LevelSettings("scenes", net.minecraft.world.level.GameType.CREATIVE, false,
                net.minecraft.world.Difficulty.PEACEFUL, true, rules, cfg);
        mc.createWorldOpenFlows().createFreshLevel("scenes", settings, new net.minecraft.world.level.levelgen.WorldOptions(SEED, true, false),
                net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, new MainMenu());
    }

    private static void cmd(Minecraft mc, String c) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c));
    }

    /** Server thread work, waited for: where the scene's structure or biome is. */
    private static BlockPos locate(MinecraftServer server, Spec spec) throws Exception {
        return server.submit(() -> {
            ServerLevel level = server.overworld();
            BlockPos origin = BlockPos.ZERO;
            if (spec.structure() != null) {
                Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(spec.structure());
                Pair<BlockPos, Holder<Structure>> p = level.getChunkSource().getGenerator()
                        .findNearestMapStructure(level, HolderSet.direct(holder), origin, 120, false);
                return p == null ? null : p.getFirst();
            }
            Pair<BlockPos, Holder<Biome>> p = level.findClosestBiome3d(h -> h.is(spec.biome()), origin, 6400, 32, spec.cave() ? 8 : 64);
            return p == null ? null : p.getFirst();
        }).get();
    }

    // ------------------------------------------------------------------ choosing the shot

    private static void make(Minecraft mc, Spec spec) throws Exception {
        Level level = mc.level;
        BlockPos target = spec.cave() ? caveSpot(level, found) : groundSpot(level, found);
        if (target == null) throw new IllegalStateException("no spot to stand on near " + found);
        float bestYaw = 0f, bestPitch = 0f;
        Vec3 bestCam = null;
        double bestScore = -1e9;
        Vec3 aim = new Vec3(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        float[] ds = spec.cave() ? new float[] {6f, 7.5f, 9f} : new float[] {10f, 12f, 14f};
        float[] hs = spec.cave() ? new float[] {1.5f, 2.5f} : new float[] {4.5f, 6.5f};
        for (int i = 0; i < 24; i++) {
            float yaw = i * 15f;
            double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
            for (float d : ds) {
                for (float h : hs) {
                    Vec3 cam = aim.add(-fx * d, h, -fz * d);
                    if (solid(level, BlockPos.containing(cam)) || solid(level, BlockPos.containing(cam).above())
                            || solid(level, BlockPos.containing(cam).below())) continue;
                    if (!clear(level, cam, aim) || !clear(level, cam, aim.add(0, 0.9, 0))) continue;
                    float pitch = (float) Math.toDegrees(Math.atan2(h, d));
                    double score = view(level, cam, yaw, pitch) - Math.abs(d - ds[1]) * 2;
                    if (score > bestScore) {
                        bestScore = score;
                        bestYaw = yaw;
                        bestPitch = pitch;
                        bestCam = cam;
                    }
                }
            }
        }
        if (bestCam == null) throw new IllegalStateException("no clear camera near " + target);

        int radius = spec.cave() ? 40 : 80, below = spec.cave() ? 14 : 22, above = spec.cave() ? 18 : 44;
        Scene s = Scene.capture(level, target, radius, below, above);
        s.id = spec.id();
        s.name = spec.name();
        s.time = spec.time();
        s.rain = false;
        double bx = target.getX() - radius, bz = target.getZ() - radius;
        s.camX = bestCam.x - bx;
        s.camY = bestCam.y - s.bottom;
        s.camZ = bestCam.z - bz;
        s.camYaw = bestYaw;
        s.camPitch = bestPitch;
        s.targetX = target.getX() + 0.5 - bx;
        s.targetY = target.getY() - s.bottom;
        s.targetZ = target.getZ() + 0.5 - bz;
        Vec3 start = startSpot(level, target, bestYaw, spec.cave());
        s.startX = start.x - bx;
        s.startY = start.y - s.bottom;
        s.startZ = start.z - bz;
        Path file = out().resolve(spec.id() + ".nbt");
        s.write(file);
        made.add(spec.id() + ";" + spec.name() + ";" + spec.season());
        GolemsClient.LOGGER.info("[SceneMaker] saved {} ({} KB, {} blocks types, target {}, camera yaw {})", spec.id(),
                Files.size(file) / 1024, s.palette.size(), target, bestYaw);
        Lobby.previewCamera = new double[] {bestCam.x, bestCam.y, bestCam.z, bestYaw, s.camPitch};
        // a marker where you will stand, for the preview picture
        cmd(Minecraft.getInstance(), "tp @a " + bestCam.x + " " + (bestCam.y - 1.62) + " " + bestCam.z);
    }

    /**
     * How open the picture from this camera is: rays across the frame, each scoring how far it gets before hitting
     * something (a wall or tree right in front of the camera scores badly).
     */
    private static double view(Level level, Vec3 cam, float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        Vec3 f = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vec3 r = new Vec3(-Math.cos(y), 0, -Math.sin(y));
        Vec3 u = r.cross(f);
        double tan = Math.tan(Math.toRadians(35)), aspect = 16.0 / 9.0;
        double score = 0;
        for (int gx = -3; gx <= 3; gx++) {
            for (int gy = -2; gy <= 2; gy++) {
                Vec3 dir = f.add(r.scale(gx / 3.0 * tan * aspect * 0.9)).add(u.scale(gy / 2.0 * tan * 0.9)).normalize();
                double dist = march(level, cam, dir, 40);
                double w = (Math.abs(gx) <= 1 && Math.abs(gy) <= 1) ? 2.5 : 1.0;
                score += w * Math.min(dist, 30);
                if (dist < 5) score -= w * 25;
            }
        }
        return score;
    }

    private static double march(Level level, Vec3 from, Vec3 dir, double max) {
        for (double t = 0.5; t < max; t += 0.35) {
            BlockState s = level.getBlockState(BlockPos.containing(from.add(dir.scale(t))));
            if (s.canOcclude() || s.getBlock() instanceof LeavesBlock || !s.getFluidState().isEmpty() && t < 3) return t;
        }
        return max;
    }

    private static boolean solid(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.canOcclude() || s.getBlock() instanceof LeavesBlock || !s.getFluidState().isEmpty();
    }

    private static boolean clear(Level level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        for (double t = 0.4; t < len - 0.4; t += 0.25) {
            BlockState s = level.getBlockState(BlockPos.containing(from.add(d.scale(t / len))));
            if (s.canOcclude() || s.getBlock() instanceof LeavesBlock) return false;
        }
        return true;
    }

    private static boolean standable(Level level, BlockPos p) {
        BlockState floor = level.getBlockState(p.below());
        return floor.canOcclude() && floor.getFluidState().isEmpty() && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p).getFluidState().isEmpty();
    }

    private static BlockPos groundSpot(Level level, BlockPos near) {
        BlockPos best = null;
        double bestScore = -1e9;
        for (int dx = -28; dx <= 28; dx++) {
            for (int dz = -28; dz <= 28; dz++) {
                int x = near.getX() + dx, z = near.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos p = new BlockPos(x, y, z);
                if (!standable(level, p)) continue;
                int flat = 0;
                for (int a = -4; a <= 4; a++) {
                    for (int b = -4; b <= 4; b++) {
                        int hy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + a, z + b);
                        if (Math.abs(hy - y) <= 1) flat++;
                    }
                }
                boolean path = level.getBlockState(p.below()).is(Blocks.DIRT_PATH);
                double score = flat * 2 + (path ? 8 : 0) - Math.sqrt(dx * dx + dz * dz) * 0.5;
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        return best;
    }

    private static BlockPos caveSpot(Level level, BlockPos near) {
        BlockPos best = null;
        double bestScore = -1e9;
        for (int dx = -32; dx <= 32; dx++) {
            for (int dy = -20; dy <= 20; dy++) {
                for (int dz = -32; dz <= 32; dz++) {
                    BlockPos p = near.offset(dx, dy, dz);
                    if (!standable(level, p) || solid(level, p.above(2))) continue;
                    int open = 0;
                    for (int a = -4; a <= 4; a++) {
                        for (int b = -4; b <= 4; b++) {
                            if (!solid(level, p.offset(a, 1, b))) open++;
                        }
                    }
                    double score = open - Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.3;
                    if (score > bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    /** Where you run in from: off to a side, nearer the camera, with a walkable line to the target. */
    private static Vec3 startSpot(Level level, BlockPos target, float yaw, boolean cave) {
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        double sx = Math.cos(Math.toRadians(yaw)), sz = Math.sin(Math.toRadians(yaw));
        Vec3 t = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        int[][] tries = cave ? new int[][] {{3, 5}, {3, -5}, {2, 4}, {2, -4}, {0, 3}, {0, -3}}
                : new int[][] {{8, 7}, {8, -7}, {6, 6}, {6, -6}, {4, 5}, {4, -5}, {0, 4}, {0, -4}};
        for (int[] tr : tries) {
            double x = t.x - fx * tr[0] + sx * tr[1], z = t.z - fz * tr[0] + sz * tr[1];
            Vec3 s = walkable(level, t, x, z, cave);
            if (s != null) return s;
        }
        return t;
    }

    private static Vec3 walkable(Level level, Vec3 target, double x, double z, boolean cave) {
        double dx = target.x - x, dz = target.z - z, len = Math.sqrt(dx * dx + dz * dz);
        int lastY = Integer.MIN_VALUE;
        Vec3 first = null;
        for (double k = 0; k <= len; k += 0.5) {
            int bx = (int) Math.floor(x + dx * k / len), bz = (int) Math.floor(z + dz * k / len);
            int y;
            if (cave) {
                y = (int) target.y;
                BlockPos p = new BlockPos(bx, y, bz);
                if (!standable(level, p)) {
                    if (standable(level, p.above())) y++;
                    else if (standable(level, p.below())) y--;
                    else return null;
                }
            } else {
                y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                if (!standable(level, new BlockPos(bx, y, bz))) return null;
            }
            if (lastY != Integer.MIN_VALUE && Math.abs(y - lastY) > 1) return null;
            if (Math.abs(y - target.y) > 3) return null;
            lastY = y;
            if (first == null) first = new Vec3(bx + 0.5, y, bz + 0.5);
        }
        return first;
    }

    private static void finish(Minecraft mc) throws Exception {
        Files.createDirectories(out());
        Files.writeString(out().resolve("index.txt"), String.join("\n", made) + "\n");
        GolemsClient.LOGGER.info("[SceneMaker] done: {}", made);
        step = Step.DONE;
        mc.stop();
        Thread halt = new Thread(() -> {
            try {
                Thread.sleep(25_000L);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        });
        halt.setDaemon(true);
        halt.start();
    }
}
