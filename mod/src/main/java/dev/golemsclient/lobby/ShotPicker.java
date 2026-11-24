package dev.golemsclient.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Picks a lobby shot in a (client) world: a flat, open spot to stand on near a place, the camera with the most open
 * picture of it, and where you run in from; then captures the scene around it. Used to make the built-in scenes and
 * to capture scenes from servers you play.
 */
public final class ShotPicker {
    /** The camera of the last shot, {x, y, z, yaw, pitch} in world coordinates (for previews). */
    public static double[] lastCamera;

    private ShotPicker() {
    }

    /** The scene around the best spot near this place, or null if there is no good spot or camera. */
    public static Scene shoot(Level level, BlockPos near, boolean cave, ResourceKey<Biome> biome, int radius, int below, int above) {
        BlockPos target = cave ? caveSpot(level, near) : groundSpot(level, near, biome);
        if (target == null) return null;
        float bestYaw = 0f, bestPitch = 0f;
        Vec3 bestCam = null;
        double bestScore = -1e9;
        Vec3 aim = new Vec3(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        float[] ds = cave ? new float[] {4.5f, 6f, 7.5f} : new float[] {10f, 12f, 14f};
        float[] hs = cave ? new float[] {0.8f, 1.6f} : new float[] {4.5f, 6.5f};
        for (int i = 0; i < 24; i++) {
            float yaw = i * 15f;
            double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
            for (float d : ds) {
                for (float h : hs) {
                    Vec3 cam = aim.add(-fx * d, h, -fz * d);
                    if (solid(level, BlockPos.containing(cam)) || (!cave && (solid(level, BlockPos.containing(cam).above())
                            || solid(level, BlockPos.containing(cam).below())))) continue;
                    if (!clear(level, cam, aim) || !cave && !clear(level, cam, aim.add(0, 0.9, 0))) continue;
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
        if (bestCam == null) return null;
        Scene s = Scene.capture(level, target, radius, below, above);
        double bx = target.getX() - radius, bz = target.getZ() - radius;
        s.camX = bestCam.x - bx;
        s.camY = bestCam.y - s.bottom;
        s.camZ = bestCam.z - bz;
        s.camYaw = bestYaw;
        s.camPitch = bestPitch;
        s.targetX = target.getX() + 0.5 - bx;
        s.targetY = target.getY() - s.bottom;
        s.targetZ = target.getZ() + 0.5 - bz;
        Vec3 start = startSpot(level, target, bestYaw, cave);
        s.startX = start.x - bx;
        s.startY = start.y - s.bottom;
        s.startZ = start.z - bz;
        lastCamera = new double[] {bestCam.x, bestCam.y, bestCam.z, bestYaw, bestPitch};
        return s;
    }

    /**
     * How open the picture from this camera is: rays across the frame, each scoring how far it gets before hitting
     * something (a wall or tree right in front of the camera scores badly).
     */
    static double view(Level level, Vec3 cam, float yaw, float pitch) {
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

    static double march(Level level, Vec3 from, Vec3 dir, double max) {
        for (double t = 0.5; t < max; t += 0.35) {
            BlockState s = level.getBlockState(BlockPos.containing(from.add(dir.scale(t))));
            if (s.canOcclude() || s.getBlock() instanceof LeavesBlock || !s.getFluidState().isEmpty() && t < 3) return t;
        }
        return max;
    }

    static boolean solid(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.canOcclude() || s.getBlock() instanceof LeavesBlock || !s.getFluidState().isEmpty();
    }

    static boolean clear(Level level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        for (double t = 0.4; t < len - 0.4; t += 0.25) {
            BlockState s = level.getBlockState(BlockPos.containing(from.add(d.scale(t / len))));
            if (s.canOcclude() || s.getBlock() instanceof LeavesBlock) return false;
        }
        return true;
    }

    static boolean standable(Level level, BlockPos p) {
        BlockState floor = level.getBlockState(p.below());
        return floor.canOcclude() && floor.getFluidState().isEmpty() && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p).getFluidState().isEmpty();
    }

    public static BlockPos findBlock(Level level, BlockPos near, net.minecraft.world.level.block.Block block, int r) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, near.getX() + dx, near.getZ() + dz);
                for (int dy = -6; dy <= 2; dy++) {
                    BlockPos p = new BlockPos(near.getX() + dx, top + dy, near.getZ() + dz);
                    if (level.getBlockState(p).is(block)) {
                        double d = dx * dx + dz * dz;
                        if (d < bestD) {
                            bestD = d;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    static BlockPos groundSpot(Level level, BlockPos near, ResourceKey<Biome> biome) {
        BlockPos best = null;
        double bestScore = -1e9;
        for (int dx = -28; dx <= 28; dx++) {
            for (int dz = -28; dz <= 28; dz++) {
                int x = near.getX() + dx, z = near.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos p = new BlockPos(x, y, z);
                if (!standable(level, p)) continue;
                if (biome != null && !level.getBiome(p).is(biome)) continue;
                if (level.getBlockState(p.below()).is(Blocks.SNOW_BLOCK) || level.getBlockState(p).is(Blocks.SNOW) && biome != null) continue;
                // open sky: no tree canopy over the spot or right around it
                int canopy = 0;
                for (int a = -3; a <= 3; a++) {
                    for (int b = -3; b <= 3; b++) {
                        int withLeaves = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + a, z + b);
                        int without = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + a, z + b);
                        if (withLeaves > without + 1) canopy++;
                    }
                }
                if (canopy > 6) continue;
                int flat = 0;
                for (int a = -4; a <= 4; a++) {
                    for (int b = -4; b <= 4; b++) {
                        int hy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + a, z + b);
                        if (Math.abs(hy - y) <= 1) flat++;
                    }
                }
                boolean path = level.getBlockState(p.below()).is(Blocks.DIRT_PATH);
                double score = flat * 2 + (path ? 8 : 0) - canopy * 3 - Math.sqrt(dx * dx + dz * dz) * 0.5;
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        return best;
    }

    static BlockPos caveSpot(Level level, BlockPos near) {
        BlockPos best = null;
        double bestScore = -1e9;
        for (int dx = -32; dx <= 32; dx++) {
            for (int dy = -20; dy <= 20; dy++) {
                for (int dz = -32; dz <= 32; dz++) {
                    BlockPos p = near.offset(dx, dy, dz);
                    if (!standable(level, p) || solid(level, p.above(2))) continue;
                    int open = 0;
                    for (int a = -5; a <= 5; a++) {
                        for (int b = -5; b <= 5; b++) {
                            if (!solid(level, p.offset(a, 1, b))) open++;
                            if (!solid(level, p.offset(a, 3, b))) open++;
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
    static Vec3 startSpot(Level level, BlockPos target, float yaw, boolean cave) {
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

    static Vec3 walkable(Level level, Vec3 target, double x, double z, boolean cave) {
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

}
