package com.lutils.utils.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Weighted A* over the predicted Nether noise cells (4x8x4 blocks). Not thread safe, use from one thread. */
public class NetherPathfinder {
    public record Params(float margin, int clearance, int nodeLimit, double weight, double goalRadius, boolean useLoadedChunks, boolean usePrediction, List<Vec3d> banned, int groundClearance, boolean avoidUnloaded) {}

    public record Result(List<Vec3d> path, boolean reached, int expanded) {}

    private static final int[][] STEPS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
        {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}
    };

    private static class Node {
        final int x, y, z;
        double g;
        Node parent;
        boolean closed;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private record Entry(Node node, double f) {}

    private final NetherTerrain terrain;
    private final Long2ByteOpenHashMap passable = new Long2ByteOpenHashMap();
    private final Long2ByteOpenHashMap rawOpen = new Long2ByteOpenHashMap();

    /** Cells this close to a spot where the player got stuck before are off limits, except right around the start. */
    private static final double BAN_RADIUS = 7;
    private static final double BAN_START_EXEMPT = 5;

    /** Share of non-free blocks in a loaded cell. Dense spots (fungus forests, leaves, scaffolding) cost extra or are avoided. */
    private static final float MAX_CLUTTER = 0.4f;
    private static final double CLUTTER_COST = 6;

    private final Long2FloatOpenHashMap clutter = new Long2FloatOpenHashMap();

    private Params params;
    private Vec3d searchStart;

    public NetherPathfinder(NetherTerrain terrain) {
        this.terrain = terrain;
        passable.defaultReturnValue((byte) -1);
        rawOpen.defaultReturnValue((byte) -1);
    }

    public Result find(Vec3d start, Vec3d goal, Params params, BooleanSupplier cancelled) {
        this.params = params;
        this.searchStart = start;
        passable.clear();
        rawOpen.clear();
        clutter.clear();

        // Leave tight spots (holes, pockets) block by block first, a cell grid is too coarse to find the way out
        List<Vec3d> lead = List.of();
        Node first = findStart(start, goal);
        if (first == null) {
            Escape escape = escape(start);
            if (escape == null) return new Result(List.of(), false, 0);
            first = escape.node;
            lead = escape.points;
        }

        Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingDouble(a -> a.f));
        nodes.put(key(first.x, first.y, first.z), first);
        queue.add(new Entry(first, 0));

        Node best = first;
        double bestH = Double.MAX_VALUE;
        boolean reached = false;
        int expanded = 0;

        while (!queue.isEmpty() && expanded < params.nodeLimit) {
            if ((expanded & 255) == 0 && cancelled.getAsBoolean()) return new Result(List.of(), false, expanded);

            Node n = Objects.requireNonNull(queue.poll()).node;
            if (n.closed) continue;
            n.closed = true;
            expanded++;

            double h = heuristic(n, goal);
            if (h < bestH) {
                bestH = h;
                best = n;
            }
            if (h <= params.goalRadius) {
                reached = true;
                break;
            }

            for (int[] s : STEPS) {
                int nx = n.x + s[0], ny = n.y + s[1], nz = n.z + s[2];
                if (!isPassable(nx, ny, nz)) continue;
                if (!edgeOpen(n.x, n.y, n.z, nx, ny, nz)) continue;
                // Trees, fungi, builds: where real blocks are known, the way between the two centers has to be free for the whole body
                if ((loadedChunk(n.x, n.z) != null || loadedChunk(nx, nz) != null) && !lineClear(center(n), cellCenter(nx, ny, nz))) continue;
                // No corner cutting on diagonals
                if (s[0] != 0 && s[2] != 0 && (!isPassable(n.x + s[0], n.y, n.z) || !isPassable(n.x, n.y, n.z + s[2]))) continue;

                long k = key(nx, ny, nz);
                Node m = nodes.get(k);
                if (m == null) {
                    m = new Node(nx, ny, nz);
                    m.g = Double.MAX_VALUE;
                    nodes.put(k, m);
                }
                if (m.closed) continue;

                double dx = s[0] * NetherTerrain.CELL_XZ, dy = s[1] * NetherTerrain.CELL_Y, dz = s[2] * NetherTerrain.CELL_XZ;
                double g = n.g + Math.sqrt(dx * dx + dy * dy + dz * dz) * (1 + CLUTTER_COST * clutter.get(k));
                if (g >= m.g) continue;

                m.g = g;
                m.parent = n;
                queue.add(new Entry(m, g + params.weight * heuristic(m, goal)));
            }
        }

        List<Vec3d> raw = new ArrayList<>();
        for (Node n = best; n != null; n = n.parent) raw.add(center(n));
        Collections.reverse(raw);

        // Keep the exact start and, if reached, the exact goal
        if (lead.isEmpty()) raw.set(0, start);
        else {
            raw.addAll(0, lead);
            raw.addFirst(start);
        }
        if (reached) raw.add(goal);

        return new Result(smooth(raw), reached, expanded);
    }

    private Node findStart(Vec3d pos, Vec3d goal) {
        int cx = Math.floorDiv((int) Math.floor(pos.x), NetherTerrain.CELL_XZ);
        int cy = Math.floorDiv((int) Math.floor(pos.y), NetherTerrain.CELL_Y);
        int cz = Math.floorDiv((int) Math.floor(pos.z), NetherTerrain.CELL_XZ);

        Node closest = null;
        double closestDist = Double.MAX_VALUE;
        for (int r = 0; r <= 3 && closest == null; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                if (!isPassable(cx + dx, cy + dy, cz + dz)) continue;
                if (!lineClear(pos, cellCenter(cx + dx, cy + dy, cz + dz))) continue;
                // Prefer cells that are near and on the way, so a new search doesn't start by flying back
                Vec3d center = cellCenter(cx + dx, cy + dy, cz + dz);
                double d = pos.distanceTo(center) + 0.4 * center.distanceTo(goal);
                if (d < closestDist) {
                    closestDist = d;
                    closest = new Node(cx + dx, cy + dy, cz + dz);
                }
            }
        }
        if (closest != null) closest.g = 0;
        return closest;
    }

    private record Escape(Node node, List<Vec3d> points) {}

    private boolean blockOpen(int x, int y, int z) {
        return blockFree(x, y, z) && blockFree(x, y + 1, z);
    }

    /** Breadth first search over single blocks to the nearest spot from which the cell grid is usable. */
    private Escape escape(Vec3d start) {
        int sx = (int) Math.floor(start.x), sy = (int) Math.floor(start.y), sz = (int) Math.floor(start.z);

        List<int[]> queue = new ArrayList<>();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        queue.add(new int[]{sx, sy, sz, -1});
        seen.add(key(sx, sy, sz));

        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int i = 0; i < queue.size() && i < 8000; i++) {
            int[] b = queue.get(i);

            int cx = Math.floorDiv(b[0], NetherTerrain.CELL_XZ), cy = Math.floorDiv(b[1], NetherTerrain.CELL_Y), cz = Math.floorDiv(b[2], NetherTerrain.CELL_XZ);
            Vec3d blockCenter = new Vec3d(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5);
            if (i > 0 && isPassable(cx, cy, cz) && bodyFree(blockCenter) && lineClear(blockCenter, cellCenter(cx, cy, cz))) {
                List<Vec3d> points = new ArrayList<>();
                for (int[] n = b; n != null; n = n[3] < 0 ? null : queue.get(n[3])) points.add(new Vec3d(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5));
                Collections.reverse(points);
                points.removeFirst();

                Node node = new Node(cx, cy, cz);
                return new Escape(node, points);
            }

            for (int[] d : dirs) {
                int x = b[0] + d[0], y = b[1] + d[1], z = b[2] + d[2];
                if (Math.abs(x - sx) > 16 || Math.abs(z - sz) > 16 || Math.abs(y - sy) > 16) continue;
                if (!blockOpen(x, y, z) || !seen.add(key(x, y, z))) continue;
                queue.add(new int[]{x, y, z, i});
            }
        }
        return null;
    }

    private double heuristic(Node n, Vec3d goal) {
        double dx = n.x * NetherTerrain.CELL_XZ + 2 - goal.x;
        double dy = n.y * NetherTerrain.CELL_Y + 4 - goal.y;
        double dz = n.z * NetherTerrain.CELL_XZ + 2 - goal.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Vec3d center(Node n) {
        return cellCenter(n.x, n.y, n.z);
    }

    private static Vec3d cellCenter(int x, int y, int z) {
        return new Vec3d(x * NetherTerrain.CELL_XZ + 2, y * NetherTerrain.CELL_Y + 4, z * NetherTerrain.CELL_XZ + 2);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFFF) << 40) | ((long) (z & 0xFFFFFF) << 16) | (y & 0xFFFF);
    }

    private boolean isPassable(int cx, int cy, int cz) {
        long k = key(cx, cy, cz);
        byte cached = passable.get(k);
        if (cached != -1) return cached == 1;

        boolean ok = !isBanned(cx, cy, cz);
        int r = ok ? params.clearance : -1;
        outer:
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (!isOpen(cx + dx, cy + dy, cz + dz)) {
                ok = false;
                break outer;
            }
        }

        passable.put(k, (byte) (ok ? 1 : 0));
        return ok;
    }

    private boolean isBanned(int cx, int cy, int cz) {
        if (params.banned.isEmpty()) return false;

        Vec3d c = cellCenter(cx, cy, cz);
        if (c.distanceTo(searchStart) < BAN_START_EXEMPT) return false;
        for (Vec3d b : params.banned) if (c.distanceTo(b) < BAN_RADIUS) return true;
        return false;
    }

    private boolean isOpen(int cx, int cy, int cz) {
        long k = key(cx, cy, cz);
        byte cached = rawOpen.get(k);
        if (cached != -1) return cached == 1;

        boolean open;
        if (cy < 0 || cy >= NetherTerrain.CELLS_Y || (params.avoidUnloaded && !chunkLoaded(cx, cz))) open = false;
        else {
            WorldChunk chunk = loadedChunk(cx, cz);
            open = chunk != null ? realCellOpen(chunk, cx, cy, cz, k) : params.usePrediction && terrain.isAirCell(cx, cy, cz, params.margin);
        }

        // Stay clear of the ground (basalt deltas, holes, uneven floors)
        for (int d = 1; open && d <= params.groundClearance; d++) {
            open = blockFree(cx * NetherTerrain.CELL_XZ + 2, cy * NetherTerrain.CELL_Y + 4 - d, cz * NetherTerrain.CELL_XZ + 2);
        }

        rawOpen.put(k, (byte) (open ? 1 : 0));
        return open;
    }

    private boolean chunkLoaded(int cx, int cz) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null && mc.world.getChunkManager().getWorldChunk((cx * NetherTerrain.CELL_XZ) >> 4, (cz * NetherTerrain.CELL_XZ) >> 4) != null;
    }

    /** Real blocks win over the prediction (fortresses, bastions, lava lakes, player builds...) wherever the chunk is loaded. */
    private WorldChunk loadedChunk(int cx, int cz) {
        if (!params.useLoadedChunks) return null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        return mc.world.getChunkManager().getWorldChunk((cx * NetherTerrain.CELL_XZ) >> 4, (cz * NetherTerrain.CELL_XZ) >> 4);
    }

    /** A cell counts as free when the 2x2x2 blocks around its center have no collision and the cell isn't too cluttered. */
    private boolean realCellOpen(WorldChunk chunk, int cx, int cy, int cz, long key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int x0 = cx * NetherTerrain.CELL_XZ, y0 = cy * NetherTerrain.CELL_Y, z0 = cz * NetherTerrain.CELL_XZ;

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = x0 + 1; x < x0 + 3; x++) for (int z = z0 + 1; z < z0 + 3; z++) for (int y = y0 + 3; y < y0 + 5; y++) {
            pos.set(x, y, z);
            if (!isFree(chunk.getBlockState(pos), pos)) return false;
        }

        int blocked = 0;
        for (int x = x0; x < x0 + NetherTerrain.CELL_XZ; x++) for (int z = z0; z < z0 + NetherTerrain.CELL_XZ; z++) for (int y = y0; y < y0 + NetherTerrain.CELL_Y; y++) {
            pos.set(x, y, z);
            if (!isFree(chunk.getBlockState(pos), pos)) blocked++;
        }
        float share = blocked / (float) (NetherTerrain.CELL_XZ * NetherTerrain.CELL_XZ * NetherTerrain.CELL_Y);
        clutter.put(key, share);
        return share <= MAX_CLUTTER;
    }

    /** The wall between two neighbouring cell centers must be air too; skipped where real chunks are loaded. */
    private boolean edgeOpen(int ax, int ay, int az, int bx, int by, int bz) {
        if (loadedChunk(ax, az) != null || loadedChunk(bx, bz) != null) return true;
        if (!params.usePrediction) return false;

        double x = (ax + bx + 1) * NetherTerrain.CELL_XZ / 2.0;
        double y = (ay + by + 1) * NetherTerrain.CELL_Y / 2.0;
        double z = (az + bz + 1) * NetherTerrain.CELL_XZ / 2.0;
        return terrain.densityAt(x, y, z) < -params.margin;
    }

    private static final double MAX_SEGMENT = 24;

    private static final double[][] BODY = {
        {0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}
    };

    /** Whether a single block position is free, from real blocks where loaded and the prediction elsewhere. */
    private boolean blockFree(int x, int y, int z) {
        if (y < 0 || y >= NetherTerrain.CELLS_Y * NetherTerrain.CELL_Y) return false;

        int cellX = Math.floorDiv(x, NetherTerrain.CELL_XZ), cellZ = Math.floorDiv(z, NetherTerrain.CELL_XZ);
        if (params.avoidUnloaded && !chunkLoaded(cellX, cellZ)) return false;

        WorldChunk chunk = loadedChunk(cellX, cellZ);
        if (chunk == null) return params.usePrediction && y > NetherTerrain.LAVA_LEVEL && terrain.densityAt(x + 0.5, y + 0.5, z + 0.5) < -params.margin;

        MinecraftClient mc = MinecraftClient.getInstance();
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = chunk.getBlockState(pos);
        return isFree(state, pos);
    }

    /** Lava and fire hurt even though you can fly through them. */
    private static boolean isFree(BlockState state, BlockPos pos) {
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty() || state.isIn(BlockTags.FIRE)) return false;
        return state.getCollisionShape(MinecraftClient.getInstance().world, pos).isEmpty();
    }

    /** Free for a body about 3 blocks wide and tall around the point. */
    private boolean bodyFree(Vec3d p) {
        for (double[] o : BODY) {
            if (!blockFree((int) Math.floor(p.x + o[0]), (int) Math.floor(p.y + o[1]), (int) Math.floor(p.z + o[2]))) return false;
        }
        for (int d = 2; d <= params.groundClearance; d++) {
            if (!blockFree((int) Math.floor(p.x), (int) Math.floor(p.y) - d, (int) Math.floor(p.z))) return false;
        }
        return true;
    }

    private boolean lineClear(Vec3d a, Vec3d b) {
        int steps = (int) Math.ceil(a.distanceTo(b));
        for (int i = 1; i < steps; i++) {
            if (!bodyFree(a.lerp(b, (double) i / steps))) return false;
        }
        return true;
    }

    /** String pulling: drop every waypoint that can be skipped with a clear straight line. */
    private List<Vec3d> smooth(List<Vec3d> raw) {
        if (raw.size() < 3) return raw;

        List<Vec3d> out = new ArrayList<>();
        out.add(raw.getFirst());
        int i = 0;
        while (i < raw.size() - 1) {
            int j = raw.size() - 1;
            while (j > i + 1 && (raw.get(i).distanceTo(raw.get(j)) > MAX_SEGMENT || !lineClear(raw.get(i), raw.get(j)))) j--;
            out.add(raw.get(j));
            i = j;
        }
        return out;
    }
}
