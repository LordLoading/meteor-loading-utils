package com.lutils.modules;

import com.lutils.LUtils;
import com.lutils.settings.LongSetting;
import com.lutils.utils.pathfinding.NetherPathfinder;
import com.lutils.utils.pathfinding.NetherTerrain;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.World;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NetherPath extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPathing = settings.createGroup("Pathing");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Long> seed = sgGeneral.add(new LongSetting.Builder()
        .name("seed")
        .description("The world seed.")
        .defaultValue(0L)
        .build());

    private final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
        .name("target-x")
        .description("Target X coordinate.")
        .defaultValue(0)
        .range(-30000000, 30000000)
        .noSlider()
        .build());

    private final Setting<Integer> targetY = sgGeneral.add(new IntSetting.Builder()
        .name("target-y")
        .description("Target Y coordinate (Nether height, 0-127).")
        .defaultValue(64)
        .range(0, 127)
        .sliderRange(0, 127)
        .build());

    private final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
        .name("target-z")
        .description("Target Z coordinate.")
        .defaultValue(0)
        .range(-30000000, 30000000)
        .noSlider()
        .build());

    private final Setting<Boolean> overworldCoords = sgGeneral.add(new BoolSetting.Builder()
        .name("overworld-coords")
        .description("Interpret target X/Z as Overworld coordinates and divide them by 8.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> clearance = sgPathing.add(new IntSetting.Builder()
        .name("clearance")
        .description("Extra free cells (4x8x4 blocks) required around the path.")
        .defaultValue(0)
        .range(0, 2)
        .sliderRange(0, 2)
        .build());

    private final Setting<Integer> groundClearance = sgPathing.add(new IntSetting.Builder()
        .name("ground-clearance")
        .description("Free blocks wanted below the path, so you don't skim the ground or drop into holes.")
        .defaultValue(3)
        .range(0, 8)
        .sliderRange(0, 6)
        .build());

    private final Setting<Double> margin = sgPathing.add(new DoubleSetting.Builder()
        .name("wall-margin")
        .description("How far into the air (in noise density) a cell has to be. Higher keeps more distance to walls.")
        .defaultValue(0.05)
        .range(0, 1)
        .sliderRange(0, 0.5)
        .build());

    private final Setting<Integer> nodeLimit = sgPathing.add(new IntSetting.Builder()
        .name("node-limit")
        .description("Maximum cells explored per search. If hit, the path leads to the closest explored point.")
        .defaultValue(400000)
        .range(10000, 5000000)
        .sliderRange(10000, 2000000)
        .build());

    private final Setting<Double> weight = sgPathing.add(new DoubleSetting.Builder()
        .name("greediness")
        .description("1 = optimal A*. Higher is faster and heads straight for the target, but the path gets less optimal.")
        .defaultValue(1.5)
        .range(1, 5)
        .sliderRange(1, 3)
        .build());

    private final Setting<Boolean> useLoadedChunks = sgPathing.add(new BoolSetting.Builder()
        .name("use-loaded-chunks")
        .description("Use real blocks in loaded chunks (structures, builds) instead of only the seed prediction.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> avoidUnloaded = sgPathing.add(new BoolSetting.Builder()
        .name("avoid-unloaded")
        .description("Never path through chunks that aren't loaded. The path then ends at the edge of the loaded area and continues as chunks load in.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> replanDistance = sgPathing.add(new IntSetting.Builder()
        .name("replan-distance")
        .description("Search again once you moved this far from where the last search started.")
        .defaultValue(48)
        .range(8, 500)
        .sliderRange(8, 200)
        .build());

    public enum MoveMode {
        Guide("Path finder only"),
        Fly("Fly (creative / Eloadra)");

        private final String title;

        MoveMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final Setting<MoveMode> moveMode = sgPathing.add(new EnumSetting.Builder<MoveMode>()
        .name("move-mode")
        .description("Path finder only: just shows the path, you fly yourself. Fly: also steers you along it, for creative flight and Eloadra.")
        .defaultValue(MoveMode.Fly)
        .build());

    private final Setting<Boolean> rotate = sgPathing.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Turn your head towards the next waypoint. When off you can look around freely and the module steers with WASD relative to where you look (gliding needs this on, it steers with the pitch).")
        .defaultValue(true)
        .visible(() -> moveMode.get() == MoveMode.Fly)
        .build());

    private final Setting<Integer> goalRadius = sgPathing.add(new IntSetting.Builder()
        .name("goal-radius")
        .description("Counts as arrived when this close to the target.")
        .defaultValue(10)
        .range(3, 50)
        .sliderRange(3, 30)
        .build());

    private final Setting<Boolean> chatFeedback = sgPathing.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Print search results in chat.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the path.")
        .defaultValue(new SettingColor(0, 255, 200, 255))
        .build());

    private final Setting<Integer> renderAhead = sgRender.add(new IntSetting.Builder()
        .name("render-ahead")
        .description("How many waypoints ahead to draw.")
        .defaultValue(40)
        .range(2, 500)
        .sliderRange(2, 100)
        .build());

    private static final double MIN_ACCURACY = 0.8;
    private static final double WAYPOINT_REACHED = 4;
    private static final double WAYPOINT_PASSED = 24;
    private static final double REPLAN_BEFORE_END = 40;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LUtils-NetherPath");
        t.setDaemon(true);
        return t;
    });

    private volatile List<Vec3d> path = List.of();
    private volatile boolean planning;
    private volatile boolean pathReached;
    private final AtomicInteger generation = new AtomicInteger();
    private volatile double predictionAccuracy = -1;
    private Vec3d plannedFrom;
    private int cooldown;
    private int progress;
    private boolean arrived;
    private boolean waiting;
    private final List<Vec3d> banned = new java.util.ArrayList<>();
    private int stuckTicks;
    private int collisionTicks;
    private int stuckLevel;
    private Vec3d stuckCheckPos;
    private List<Vec3d> progressPath = List.of();
    private boolean pressing;
    private NetherTerrain terrain;

    public NetherPath() {
        super(LUtils.CATEGORY, "Nether Path", "A* pathfinding through the Nether, predicted from the world seed.");
    }

    @Override
    public void onActivate() {
        path = List.of();
        pathReached = false;
        arrived = false;
        banned.clear();
        predictionAccuracy = -1;
        plannedFrom = null;
        planning = false;
        cooldown = 0;
        progress = 1;
        stuckTicks = 0;
        stuckLevel = 0;
        stuckCheckPos = null;
        progressPath = List.of();
        generation.incrementAndGet();
    }

    @Override
    public void onDeactivate() {
        generation.incrementAndGet();
        path = List.of();
        releaseKeys();
    }

    private Vec3d target() {
        double div = overworldCoords.get() ? 8 : 1;
        return new Vec3d(Math.floor(targetX.get() / div) + 0.5, targetY.get(), Math.floor(targetZ.get() / div) + 0.5);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.world.getRegistryKey() != World.NETHER) {
            releaseKeys();
                return;
        }

        Vec3d pos = mc.player.getEntityPos();

        double toTarget = pos.distanceTo(target());
        if (toTarget < goalRadius.get()) {
            if (!arrived) {
                arrived = true;
                if (chatFeedback.get()) info("Arrived.");
            }
            path = List.of();
            releaseKeys();
                return;
        }
        if (toTarget > goalRadius.get() + 10) arrived = false;
        if (arrived) return;

        // Hands off while a screen (chat, inventory, ...) is open
        if (mc.currentScreen != null) {
            releaseKeys();
                stuckTicks = 0;
            collisionTicks = 0;
            stuckCheckPos = null;
            return;
        }

        List<Vec3d> points = path;

        checkStuck(points, pos);

        if (!planning && cooldown-- <= 0) {
            boolean movedAway = plannedFrom == null || plannedFrom.distanceTo(pos) >= replanDistance.get();
            // A partial path ends somewhere in the middle of nowhere, so search again before reaching its end
            boolean nearEnd = !pathReached && !points.isEmpty() && points.getLast().distanceTo(pos) < REPLAN_BEFORE_END;
            if (movedAway || nearEnd) {
                cooldown = 20;
                plan(pos);
            }
        }

        if (points != progressPath) {
            progressPath = points;
            progress = 1;
        }
        advance(points, pos);

        waiting = avoidUnloaded.get() && !pathReached && !points.isEmpty() && progress >= points.size();

        if (points.isEmpty() || progress >= points.size() || moveMode.get() != MoveMode.Fly) releaseKeys();
        else fly(points);
    }

    /** Moves {@link #progress} forward past every waypoint that was reached or flown past, so fast flying can't overshoot one. */
    private void advance(List<Vec3d> points, Vec3d pos) {
        while (progress < points.size()) {
            Vec3d wp = points.get(progress);
            boolean reached = wp.distanceTo(pos) < WAYPOINT_REACHED;
            boolean passed = progress + 1 < points.size()
                && wp.distanceTo(pos) < WAYPOINT_PASSED
                && pos.subtract(wp).dotProduct(points.get(progress + 1).subtract(wp)) > 0;
            if (!reached && !passed) break;
            progress++;
        }
    }

    private void fly(List<Vec3d> points) {
        Vec3d diff = points.get(progress).subtract(mc.player.getEyePos());
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        boolean gliding = mc.player.isGliding();

        // Landing turns creative flight off, so turn it back on
        var abilities = mc.player.getAbilities();
        if (abilities.allowFlying && !abilities.flying && !gliding) abilities.flying = true;

        pressing = true;
        if (rotate.get()) {
            mc.player.setYaw(wantedYaw);
            mc.player.setHeadYaw(wantedYaw);
            mc.options.forwardKey.setPressed(true);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            if (gliding) mc.player.setPitch((float) -Math.toDegrees(Math.atan2(diff.y, diff.horizontalLength())));
        } else {
            // Steer relative to where the player looks: positive angle = target is to the right
            float angle = MathHelper.wrapDegrees(wantedYaw - mc.player.getYaw());
            mc.options.forwardKey.setPressed(Math.abs(angle) < 67.5f);
            mc.options.backKey.setPressed(Math.abs(angle) > 112.5f);
            mc.options.rightKey.setPressed(angle > 22.5f && angle < 157.5f);
            mc.options.leftKey.setPressed(angle < -22.5f && angle > -157.5f);
        }

        // Never sink towards the ground, even if the next waypoint is lower
        boolean nearGround = !mc.world.getBlockState(mc.player.getBlockPos().down()).getCollisionShape(mc.world, mc.player.getBlockPos().down()).isEmpty()
            || !mc.world.getBlockState(mc.player.getBlockPos().down(2)).getCollisionShape(mc.world, mc.player.getBlockPos().down(2)).isEmpty();
        mc.options.jumpKey.setPressed(diff.y > 1.5 || (nearGround && diff.y > -6));
        mc.options.sneakKey.setPressed(diff.y < -1.5 && !nearGround);
    }

    private void releaseKeys() {
        if (!pressing) return;
        pressing = false;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    /** No progress for 2 seconds while following a path: demand more distance to walls and search again. */
    private void checkStuck(List<Vec3d> points, Vec3d pos) {
        if (moveMode.get() != MoveMode.Fly || points.isEmpty() || progress >= points.size()) {
            stuckTicks = 0;
            stuckLevel = 0;
            return;
        }

        collisionTicks = mc.player.horizontalCollision || mc.player.verticalCollision ? collisionTicks + 1 : 0;
        boolean pressedIntoSomething = collisionTicks >= 10;
        if (!pressedIntoSomething && (stuckCheckPos == null || stuckCheckPos.distanceTo(pos) > 2)) {
            stuckCheckPos = pos;
            stuckTicks = 0;
            if (progress > 1) stuckLevel = 0;
            return;
        }

        if (!pressedIntoSomething && ++stuckTicks < 40) return;
        stuckTicks = 0;
        collisionTicks = 0;
        stuckLevel = Math.min(stuckLevel + 1, 5);
        // Remember this dead end, otherwise the search keeps sending us back into it
        banned.add(pos);
        if (progress < points.size() && points.get(progress).distanceTo(pos) < 12) banned.add(points.get(progress));
        while (banned.size() > 24) banned.removeFirst();
        cooldown = 0;
        plannedFrom = null;
        // Drop the plan that got us here, otherwise "keep the current plan if the new one isn't closer" would put it back
        path = List.of();
        if (chatFeedback.get()) info("Stuck, avoiding this spot and searching again (level %d).", stuckLevel);
    }

    /**
     * Compares the seed prediction with the real blocks of the chunks around the player. A bad match means the seed is wrong
     * or the server doesn't generate vanilla 1.21 Nether terrain, so the prediction must not be trusted.
     */
    private void checkPrediction(Vec3d around, long worldSeed) {
        if (!useLoadedChunks.get() || mc.world == null) return;

        int agree = 0, total = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int cx = (int) Math.floor(around.x), cz = (int) Math.floor(around.z);
        for (int x = cx - 48; x <= cx + 48; x += 3) for (int z = cz - 48; z <= cz + 48; z += 3) {
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x >> 4, z >> 4);
            if (chunk == null) continue;

            for (int y = 8; y < 120; y += 5) {
                pos.set(x, y, z);
                BlockState state = chunk.getBlockState(pos);
                boolean realSolid = !state.isAir() && !state.getCollisionShape(mc.world, pos).isEmpty();
                boolean predictedSolid = terrain.densityAt(x + 0.5, y + 0.5, z + 0.5) > 0;
                if (realSolid == predictedSolid) agree++;
                total++;
            }
        }
        if (total < 200) return;

        double accuracy = (double) agree / total;
        boolean changed = predictionAccuracy < 0 || (accuracy < MIN_ACCURACY) != (predictionAccuracy < MIN_ACCURACY);
        predictionAccuracy = accuracy;
        if (chatFeedback.get() && changed) {
            mc.execute(() -> {
                if (accuracy < MIN_ACCURACY) warning("Seed prediction matches only %.0f%% of the loaded blocks. The seed is probably wrong, or this server doesn't use vanilla 1.21 Nether terrain. Only loaded chunks are used for pathing until it matches.", accuracy * 100);
                else info("Seed prediction matches %.0f%% of the loaded blocks.", accuracy * 100);
            });
        }
    }

    private void plan(Vec3d from) {
        plannedFrom = from;
        planning = true;

        int gen = generation.get();
        long worldSeed = seed.get();
        Vec3d goal = target();
        NetherPathfinder.Params params = new NetherPathfinder.Params(
            margin.get().floatValue() + 0.1f * stuckLevel, clearance.get(), nodeLimit.get(), weight.get(), goalRadius.get(), useLoadedChunks.get(), predictionAccuracy < 0 || predictionAccuracy >= MIN_ACCURACY, List.copyOf(banned), groundClearance.get(), avoidUnloaded.get());

        executor.execute(() -> {
            try {
                if (terrain == null || terrain.seed != worldSeed) terrain = new NetherTerrain(worldSeed);

                checkPrediction(from, worldSeed);

                long start = System.currentTimeMillis();
                NetherPathfinder.Result result = new NetherPathfinder(terrain).find(from, goal, params, () -> gen != generation.get());
                if (gen != generation.get()) return;

                long ms = System.currentTimeMillis() - start;
                mc.execute(() -> applyResult(result, goal, ms));
            } catch (Throwable t) {
                LUtils.LOG.error("Nether path search failed", t);
                mc.execute(() -> error("Search failed, see log."));
            } finally {
                planning = false;
            }
        });
    }

    private void applyResult(NetherPathfinder.Result result, Vec3d goal, long ms) {
        List<Vec3d> old = path;
        List<Vec3d> fresh = result.path();
        boolean oldAhead = !old.isEmpty() && progress + 1 < old.size();

        if (!fresh.isEmpty() && !old.isEmpty() && !pathReached && !result.reached()) {
            double oldDist = old.getLast().distanceTo(goal), newDist = fresh.getLast().distanceTo(goal);
            if (newDist > oldDist - 4) {
                // Not any closer to the target: keep the current plan instead of swinging around, unless it's used up
                if (oldAhead) return;
                // At the edge of the loaded area this isn't a dead end, we just wait for chunks
                if (!avoidUnloaded.get()) {
                    banned.add(old.getLast());
                    while (banned.size() > 24) banned.removeFirst();
                }
            }
        }

        boolean quiet = !old.isEmpty() && !fresh.isEmpty() && result.reached() == pathReached
            && old.getLast().distanceTo(fresh.getLast()) < 16;
        path = fresh;
        pathReached = result.reached();

        if (!chatFeedback.get() || quiet) return;
        if (fresh.isEmpty()) warning("No free space found around you.");
        else if (result.reached()) info("Path found: %d waypoints, %d cells, %d ms.", fresh.size(), result.expanded(), ms);
        else info("Target not reached, heading towards it: %d waypoints, %d cells, %d ms.", fresh.size(), result.expanded(), ms);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        List<Vec3d> points = path;
        if (mc.player == null || points.size() < 2) return;

        // Skip everything already passed
        Vec3d pos = mc.player.getEntityPos();
        int first = Math.min(progress, points.size() - 1);

        Vec3d previous = pos;
        int last = Math.min(points.size(), first + renderAhead.get());
        for (int i = first; i < last; i++) {
            Vec3d p = points.get(i);
            event.renderer.line(previous.x, previous.y, previous.z, p.x, p.y, p.z, lineColor.get());
            previous = p;
        }
    }

    @Override
    public String getInfoString() {
        List<Vec3d> points = path;
        if (waiting) return "waiting for chunks";
        if (points.isEmpty() || mc.player == null) return null;
        return String.format("%.0fm", points.getLast().distanceTo(mc.player.getEntityPos()));
    }
}
