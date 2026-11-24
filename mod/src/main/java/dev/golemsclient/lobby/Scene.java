package dev.golemsclient.lobby;

import dev.golemsclient.GolemsClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A lobby scene: a box of real Minecraft blocks (only the ones that can be seen are kept), the biome it is in, where
 * the camera stands, where your player runs in from and where they stop, the time of day and the animals and
 * villagers that were there. Saved as compressed NBT; built-in scenes ship in the jar, captures are saved from servers
 * you play.
 *
 * <p>Positions are relative to the box: x and z from its corner, y from its bottom.
 */
public final class Scene {
    public static final int VERSION = 1;

    public record Creature(String type, double x, double y, double z, float yaw) { }

    public String id = "";
    public String name = "";
    public String biome = "minecraft:plains";
    public int sx, sy, sz;
    /** The world height the box's bottom was at (kept, so the sky and fog look the same). */
    public int bottom;
    /** Each cell's palette index, column by column (z, then x, then y): runs of (index, count). */
    public List<BlockState> palette = new ArrayList<>();
    public int[] runs = new int[0];
    public double camX, camY, camZ;
    public float camYaw, camPitch;
    public double targetX, targetY, targetZ;
    public double startX, startY, startZ;
    public long time = 6000L;
    public boolean rain;
    public List<Creature> mobs = new ArrayList<>();

    // ------------------------------------------------------------------ reading and writing

    public static Scene read(InputStream in) throws Exception {
        return fromTag(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
    }

    public static Scene read(Path path) throws Exception {
        return fromTag(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
    }

    public void write(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(toTag(), path);
    }

    private CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("version", VERSION);
        t.putString("id", id);
        t.putString("name", name);
        t.putString("biome", biome);
        t.putIntArray("size", new int[] {sx, sy, sz});
        t.putInt("bottom", bottom);
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        t.put("palette", pal);
        t.putIntArray("runs", runs);
        t.put("camera", vec(camX, camY, camZ, camYaw, camPitch));
        t.put("target", vec(targetX, targetY, targetZ, 0f, 0f));
        t.put("start", vec(startX, startY, startZ, 0f, 0f));
        t.putLong("time", time);
        t.putBoolean("rain", rain);
        ListTag ms = new ListTag();
        for (Creature m : mobs) {
            CompoundTag c = vec(m.x(), m.y(), m.z(), m.yaw(), 0f);
            c.putString("type", m.type());
            ms.add(c);
        }
        t.put("mobs", ms);
        return t;
    }

    private static Scene fromTag(CompoundTag t) {
        Scene s = new Scene();
        s.id = t.getStringOr("id", "");
        s.name = t.getStringOr("name", "");
        s.biome = t.getStringOr("biome", "minecraft:plains");
        int[] size = t.getIntArray("size").orElse(new int[] {0, 0, 0});
        s.sx = size[0];
        s.sy = size[1];
        s.sz = size[2];
        s.bottom = t.getIntOr("bottom", 0);
        for (Tag p : t.getListOrEmpty("palette")) {
            BlockState state = Blocks.AIR.defaultBlockState();
            if (p instanceof CompoundTag c) {
                try {
                    state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, c);
                } catch (Exception ignored) {
                    // a block this version doesn't have: air
                }
            }
            s.palette.add(state);
        }
        s.runs = t.getIntArray("runs").orElse(new int[0]);
        CompoundTag cam = t.getCompoundOrEmpty("camera");
        s.camX = cam.getDoubleOr("x", 0);
        s.camY = cam.getDoubleOr("y", 0);
        s.camZ = cam.getDoubleOr("z", 0);
        s.camYaw = cam.getFloatOr("yaw", 0f);
        s.camPitch = cam.getFloatOr("pitch", 0f);
        CompoundTag tg = t.getCompoundOrEmpty("target");
        s.targetX = tg.getDoubleOr("x", 0);
        s.targetY = tg.getDoubleOr("y", 0);
        s.targetZ = tg.getDoubleOr("z", 0);
        CompoundTag st = t.getCompoundOrEmpty("start");
        s.startX = st.getDoubleOr("x", s.targetX);
        s.startY = st.getDoubleOr("y", s.targetY);
        s.startZ = st.getDoubleOr("z", s.targetZ);
        s.time = t.getLongOr("time", 6000L);
        s.rain = t.getBooleanOr("rain", false);
        for (Tag m : t.getListOrEmpty("mobs")) {
            if (m instanceof CompoundTag c) {
                s.mobs.add(new Creature(c.getStringOr("type", ""), c.getDoubleOr("x", 0), c.getDoubleOr("y", 0), c.getDoubleOr("z", 0),
                        c.getFloatOr("yaw", 0f)));
            }
        }
        return s;
    }

    private static CompoundTag vec(double x, double y, double z, float yaw, float pitch) {
        CompoundTag c = new CompoundTag();
        c.putDouble("x", x);
        c.putDouble("y", y);
        c.putDouble("z", z);
        c.putFloat("yaw", yaw);
        c.putFloat("pitch", pitch);
        return c;
    }

    // ------------------------------------------------------------------ capturing

    /**
     * Copies the blocks in a box around a point of a (client) world: radius blocks to each side, below and above it.
     * Only blocks that can be seen from somewhere are kept (a block buried under others on all six sides is left
     * out), which keeps scenes small and quick to build. Camera and path are set by the caller.
     */
    public static Scene capture(Level level, BlockPos center, int radius, int below, int above) {
        Scene s = new Scene();
        int minY = Math.max(level.getMinY(), center.getY() - below);
        int maxY = Math.min(level.getMaxY(), center.getY() + above);
        s.sx = radius * 2 + 1;
        s.sz = radius * 2 + 1;
        s.sy = maxY - minY + 1;
        s.bottom = minY;
        int x0 = center.getX() - radius, z0 = center.getZ() - radius;
        BlockState[] cells = new BlockState[s.sx * s.sy * s.sz];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int z = 0; z < s.sz; z++) {
            for (int x = 0; x < s.sx; x++) {
                for (int y = 0; y < s.sy; y++) {
                    cells[s.index(x, y, z)] = level.getBlockState(p.set(x0 + x, minY + y, z0 + z));
                }
            }
        }
        Map<BlockState, Integer> ids = new HashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        s.palette.add(air);
        ids.put(air, 0);
        List<Integer> runs = new ArrayList<>();
        int last = -1, count = 0;
        for (int z = 0; z < s.sz; z++) {
            for (int x = 0; x < s.sx; x++) {
                for (int y = 0; y < s.sy; y++) {
                    BlockState st = cells[s.index(x, y, z)];
                    if (st.isAir() || !s.seen(cells, x, y, z)) st = air;
                    Integer id = ids.get(st);
                    if (id == null) {
                        id = s.palette.size();
                        s.palette.add(st);
                        ids.put(st, id);
                    }
                    if (id == last) {
                        count++;
                    } else {
                        if (count > 0) {
                            runs.add(last);
                            runs.add(count);
                        }
                        last = id;
                        count = 1;
                    }
                }
            }
        }
        if (count > 0) {
            runs.add(last);
            runs.add(count);
        }
        s.runs = runs.stream().mapToInt(Integer::intValue).toArray();
        s.biome = level.getBiome(center).unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:plains");
        s.time = level.getDayTime() % 24000L;
        s.rain = level.isRaining();
        // animals and villagers (not players)
        AABB box = new AABB(x0, minY, z0, x0 + s.sx, maxY + 1, z0 + s.sz);
        for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && !(e instanceof Player))) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            s.mobs.add(new Creature(key.toString(), e.getX() - x0, e.getY() - minY, e.getZ() - z0, e.getYRot()));
        }
        // the target starts at the center; the caller moves it
        s.targetX = radius + 0.5;
        s.targetY = center.getY() - minY;
        s.targetZ = radius + 0.5;
        return s;
    }

    private int index(int x, int y, int z) {
        return (z * sx + x) * sy + y;
    }

    /** Whether a block can be seen: one of its six neighbours is see-through, or outside the box. */
    private boolean seen(BlockState[] cells, int x, int y, int z) {
        return open(cells, x + 1, y, z) || open(cells, x - 1, y, z) || open(cells, x, y + 1, z) || open(cells, x, y - 1, z)
                || open(cells, x, y, z + 1) || open(cells, x, y, z - 1);
    }

    private boolean open(BlockState[] cells, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return y >= 0; // the box's floor stays closed
        return !cells[index(x, y, z)].canOcclude();
    }

    // ------------------------------------------------------------------ building

    /**
     * Builds the scene into a (server) world with its corner at (x0, bottom, z0), without block updates (nothing
     * flows, falls or breaks), and brings in its animals. Returns how many blocks were placed.
     */
    public int build(ServerLevel level, int x0, int z0) {
        long started = System.currentTimeMillis();
        int placed = 0;
        int cell = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_SKIP_ALL_SIDEEFFECTS | Block.UPDATE_CLIENTS;
        for (int r = 0; r + 1 < runs.length; r += 2) {
            int id = runs[r], n = runs[r + 1];
            BlockState st = id >= 0 && id < palette.size() ? palette.get(id) : null;
            if (st == null || st.isAir()) {
                cell += n;
                continue;
            }
            for (int k = 0; k < n; k++, cell++) {
                int y = cell % sy, x = (cell / sy) % sx, z = cell / (sy * sx);
                level.setBlock(p.set(x0 + x, bottom + y, z0 + z), st, flags);
                placed++;
            }
        }
        for (Creature m : mobs) {
            try {
                EntityType<?> type = EntityType.byString(m.type()).orElse(null);
                if (type == null) continue;
                Entity e = type.create(level, EntitySpawnReason.COMMAND);
                if (e == null) continue;
                e.snapTo(x0 + m.x(), bottom + m.y(), z0 + m.z(), m.yaw(), 0f);
                if (e instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
                level.addFreshEntity(e);
            } catch (Exception ex) {
                GolemsClient.LOGGER.debug("Couldn't bring back a {}", m.type(), ex);
            }
        }
        GolemsClient.LOGGER.info("Lobby scene {}: {} blocks in {} ms", id, placed, System.currentTimeMillis() - started);
        return placed;
    }
}
