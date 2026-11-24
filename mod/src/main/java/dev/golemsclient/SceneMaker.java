package dev.golemsclient;

import com.mojang.datafixers.util.Pair;
import dev.golemsclient.lobby.Lobby;
import dev.golemsclient.lobby.Scene;
import dev.golemsclient.lobby.ShotPicker;
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
        BlockPos near = found;
        if (spec.structure() != null) {
            BlockPos bell = ShotPicker.findBlock(level, found, Blocks.BELL, 48);
            if (bell != null) near = bell;
        }
        Scene s = ShotPicker.shoot(level, near, spec.cave(), spec.biome(), spec.cave() ? 40 : 80, spec.cave() ? 14 : 40,
                spec.cave() ? 18 : 50);
        if (s == null) throw new IllegalStateException("no good shot near " + found);
        s.id = spec.id();
        s.name = spec.name();
        s.time = spec.time();
        s.rain = false;
        Path file = out().resolve(spec.id() + ".nbt");
        s.write(file);
        made.add(spec.id() + ";" + spec.name() + ";" + spec.season());
        GolemsClient.LOGGER.info("[SceneMaker] saved {} ({} KB, {} block types, camera yaw {})", spec.id(), Files.size(file) / 1024,
                s.palette.size(), s.camYaw);
        Lobby.previewCamera = ShotPicker.lastCamera;
        cmd(Minecraft.getInstance(), "tp @a " + ShotPicker.lastCamera[0] + " " + (ShotPicker.lastCamera[1] - 1.62) + " "
                + ShotPicker.lastCamera[2]);
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
