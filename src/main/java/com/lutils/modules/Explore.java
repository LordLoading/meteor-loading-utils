package com.lutils.modules;

import com.lutils.LUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;


public class Explore extends Module {

    public enum ExplorePattern {
        SPIRAL("Spiral outward from starting position"),
        SQUARE("Square pattern around center point");

        ExplorePattern(String description) {}
    }

    private static class ExploreState {
        final Vec3d playerPos;
        final Vec3d target;
        final Vec3d direction;
        final int step;
        final ExplorePattern pattern;
        final Vec3d squareCenter;
        final double squareRadius;
        final int squareCorner;
        final boolean clockwise;
        final long timestamp;

        ExploreState(Vec3d playerPos, Vec3d target, Vec3d direction, int step,
                    ExplorePattern pattern, Vec3d squareCenter, double squareRadius,
                    int squareCorner, boolean clockwise) {
            this.playerPos = playerPos;
            this.target = target;
            this.direction = direction;
            this.step = step;
            this.pattern = pattern;
            this.squareCenter = squareCenter;
            this.squareRadius = squareRadius;
            this.squareCorner = squareCorner;
            this.clockwise = clockwise;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final SettingGroup sgPattern = this.settings.createGroup("Pattern");
    private final SettingGroup sgGeneral = this.settings.createGroup("General");
    private final SettingGroup sgSquare = this.settings.createGroup("Square Pattern");
    private final SettingGroup sgPause = this.settings.createGroup("Pause/Resume");

    private final Setting<ExplorePattern> patternType = sgPattern.add(new EnumSetting.Builder<ExplorePattern>()
        .name("Pattern Type")
        .description("Type of exploration pattern to use")
        .defaultValue(ExplorePattern.SPIRAL)
        .build()
    );

    private final Setting<Integer> spiralGap = sgPattern.add(new IntSetting.Builder()
        .name("Spiral Gap")
        .description("Number of blocks between spiral steps")
        .defaultValue(4)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(() -> patternType.get() == ExplorePattern.SPIRAL)
        .build()
    );

    private final Setting<Boolean> useCurrentPosAsCenter = sgSquare.add(new BoolSetting.Builder()
        .name("Use Current Position")
        .description("Use current player position as square center")
        .defaultValue(true)
        .visible(() -> patternType.get() == ExplorePattern.SQUARE)
        .build()
    );

    private final Setting<Double> squareCenterX = sgSquare.add(new DoubleSetting.Builder()
        .name("Center X")
        .description("X coordinate of square center")
        .defaultValue(0.0)
        .range(-30000000, 30000000)
        .visible(() -> patternType.get() == ExplorePattern.SQUARE && !useCurrentPosAsCenter.get())
        .build()
    );

    private final Setting<Double> squareCenterZ = sgSquare.add(new DoubleSetting.Builder()
        .name("Center Z")
        .description("Z coordinate of square center")
        .defaultValue(0.0)
        .range(-30000000, 30000000)
        .visible(() -> patternType.get() == ExplorePattern.SQUARE && !useCurrentPosAsCenter.get())
        .build()
    );

    private final Setting<Double> squareRadius = sgSquare.add(new DoubleSetting.Builder()
        .name("Radius")
        .description("Radius of square pattern")
        .defaultValue(50.0)
        .range(5.0, 1000.0)
        .sliderRange(5.0, 200.0)
        .visible(() -> patternType.get() == ExplorePattern.SQUARE)
        .build()
    );

    private final Setting<Boolean> clockwiseDirection = sgSquare.add(new BoolSetting.Builder()
        .name("Clockwise")
        .description("Fly clockwise around the square (unchecked = counter-clockwise)")
        .defaultValue(true)
        .visible(() -> patternType.get() == ExplorePattern.SQUARE)
        .build()
    );

    private final Setting<Boolean> pauseToggle = sgPause.add(new BoolSetting.Builder()
        .name("Pause")
        .description("Toggle exploration pause")
        .defaultValue(false)
        .onChanged(paused -> {
            if (paused) pauseExploration();
            else resumeExploration();
        })
        .build()
    );

    private final Setting<Boolean> persistState = sgPause.add(new BoolSetting.Builder()
        .name("Persist State")
        .description("Save state across module deactivation")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSprint = sgGeneral.add(new BoolSetting.Builder()
            .name("Auto Sprint")
            .defaultValue(true)
            .build()
    );

    private int step = 0;
    private Vec3d direction = new Vec3d(1, 0, 0);
    private Vec3d target;

    private boolean isPaused = false;
    private ExploreState pausedState = null;

    private int squareCorner = 0;
    private Vec3d squareCenter = Vec3d.ZERO;
    private int startingCorner = -1;
    private int cornersVisited = 0;

    public Explore() {
        super(LUtils.CATEGORY, "Explore", "Automatically explores the world.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        if(pauseToggle.get()) {
            pauseExploration();
            return;
        }

//        if (persistState.get() && pausedState != null && !pauseToggle.get()) {
//            resumeExploration();
//            return;
//        }

        resetExploration();
    }

    @Override
    public void onDeactivate() {
        resetMovement();

        if (!persistState.get()) {
            pausedState = null;
        }
    }

    private boolean hasReachedSpiralTarget() {
        if (mc.player == null || target == null) return false;

        Vec3d playerPos = mc.player.getEntityPos();

        if (Math.abs(direction.x) > 0.5) {
            if (direction.x > 0 && playerPos.x > target.x) return true;
            if (direction.x < 0 && playerPos.x < target.x) return true;
        }

        if (Math.abs(direction.z) > 0.5) {
            if (direction.z > 0 && playerPos.z > target.z) return true;
            if (direction.z < 0 && playerPos.z < target.z) return true;
        }

        return playerPos.distanceTo(target) < 1.0;
    }

    private void getNextSpiralTarget() {
        int gap = spiralGap.get();

        direction = direction.rotateY((float) Math.toRadians(90));
        ((IVec3d) direction).meteor$set(Math.round(direction.x), 0, Math.round(direction.z));
        target = target.add(direction.multiply((step + 2) * gap));

        mc.player.setVelocity(Vec3d.ZERO);
        step++;
    }

    private boolean hasReachedSquareTarget() {
        if (mc.player == null || target == null) return false;

        Vec3d playerPos = mc.player.getEntityPos();

        if (Math.abs(direction.x) > 0.5) {
            if (direction.x > 0 && playerPos.x > target.x) return true;
            if (direction.x < 0 && playerPos.x < target.x) return true;
        }

        if (Math.abs(direction.z) > 0.5) {
            if (direction.z > 0 && playerPos.z > target.z) return true;
            if (direction.z < 0 && playerPos.z < target.z) return true;
        }

        return playerPos.distanceTo(target) < 1.0;
    }

    private void getNextSquareTarget() {
        double radius = squareRadius.get();
        boolean clockwise = clockwiseDirection.get();

        Vec3d[] corners = {
            squareCenter.add(radius, 0, radius),
            squareCenter.add(-radius, 0, radius),
            squareCenter.add(-radius, 0, -radius),
            squareCenter.add(radius, 0, -radius)
        };

        cornersVisited++;

        if (startingCorner != -1 && cornersVisited > 4 && squareCorner == startingCorner) {
            info("Square pattern completed! Visited " + cornersVisited + " corners. Disabling module.");
            resetMovement();
            toggle();
            return;
        }

        if (clockwise) {
            squareCorner = (squareCorner + 1) % 4;
        } else {
            squareCorner = (squareCorner + 3) % 4;
        }

        target = corners[squareCorner];

        Vec3d currentCorner = corners[(squareCorner + (clockwise ? 3 : 1)) % 4];
        Vec3d dirToTarget = target.subtract(currentCorner).normalize();
        direction = new Vec3d(Math.round(dirToTarget.x), 0, Math.round(dirToTarget.z));

        mc.player.setVelocity(Vec3d.ZERO);
        step++;

        info("Moving to " + getCornerName(squareCorner) + " corner (" + cornersVisited + "/4+ corners visited)");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Skip if paused
        if (isPaused || target == null) {
            return;
        }

        switch (patternType.get()) {
            case SPIRAL -> {
                if (hasReachedSpiralTarget()) {
                    getNextSpiralTarget();
                }
            }
            case SQUARE -> {
                if (hasReachedSquareTarget()) {
                    getNextSquareTarget();
                }
            }
        }

        setDirectionToTarget(target);

        // Movement (unchanged)
        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        if(autoSprint.get()) mc.options.sprintKey.setPressed(true);
    }


    private void setDirectionToTarget(Vec3d targetPos) {
        if (mc.player == null || targetPos == null) return;
        Vec3d playerPos = mc.player.getEyePos();

        Vec3d diff = targetPos.subtract(playerPos);

        if (diff.length() > 0.1) {
            float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));

            mc.player.setYaw(yaw);
            mc.player.setHeadYaw(yaw);
        }
    }

    private void resetMovement() {
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }
    }

    private void pauseExploration() {
        if (mc.player == null || isPaused) return;

        pausedState = new ExploreState(
            mc.player.getEntityPos(),
            target,
            direction,
            step,
            patternType.get(),
            squareCenter,
            squareRadius.get(),
            squareCorner,
            clockwiseDirection.get()
        );

        isPaused = true;
        resetMovement();

        info("Exploration paused at " + formatPos(pausedState.playerPos));
    }

    private void resumeExploration() {
        if (mc.player == null || !isPaused || pausedState == null) return;

        target = pausedState.target;
        direction = pausedState.direction;
        step = pausedState.step;
        squareCenter = pausedState.squareCenter;
        squareCorner = pausedState.squareCorner;
        info("Exploration resumed from saved state");


        isPaused = false;
        pausedState = null;
    }


    private void resetExploration() {
        step = 0;
        direction = new Vec3d(1, 0, 0);
        squareCorner = 0;
        startingCorner = -1;
        cornersVisited = 0;
        isPaused = false;
        pausedState = null;

        switch (patternType.get()) {
            case SPIRAL -> {
                target = mc.player.getEntityPos();
                getNextSpiralTarget();
            }
            case SQUARE -> initializeSquarePattern();
        }

        setDirectionToTarget(target);
    }

    private String formatPos(Vec3d pos) {
        return String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z);
    }

    private void initializeSquarePattern() {
        validateSquareSettings();

        if (useCurrentPosAsCenter.get()) {
            squareCenter = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        } else {
            squareCenter = new Vec3d(squareCenterX.get(), mc.player.getY(), squareCenterZ.get());
        }

        info("Square center set to " + formatPos(squareCenter));
        info("Square radius: " + squareRadius.get() + " blocks");
        info("Direction: " + (clockwiseDirection.get() ? "Clockwise" : "Counter-clockwise"));

        target = findNearestSquarePoint(mc.player.getEntityPos());

        if (target != null) {
            Vec3d dirToTarget = target.subtract(mc.player.getEntityPos()).normalize();
            direction = new Vec3d(Math.round(dirToTarget.x), 0, Math.round(dirToTarget.z));
        }
    }

    private void validateSquareSettings() {
        if (squareRadius.get() < 5.0) {
            warning("Square radius too small (%.1f). Using minimum value of 5 blocks.", squareRadius.get());
            squareRadius.set(5.0);
        }

        double centerX = useCurrentPosAsCenter.get() ? mc.player.getX() : squareCenterX.get();
        double centerZ = useCurrentPosAsCenter.get() ? mc.player.getZ() : squareCenterZ.get();

        if (!isValidSquareCenter(centerX, centerZ)) {
            warning("Invalid square center coordinates (%.1f, %.1f). Using player position.", centerX, centerZ);
            useCurrentPosAsCenter.set(true);
        }
    }

    private boolean isValidSquareCenter(double x, double z) {
        return Math.abs(x) < 30000000 && Math.abs(z) < 30000000;
    }

    private Vec3d findNearestSquarePoint(Vec3d playerPos) {
        double radius = squareRadius.get();

        Vec3d[] corners = {
            squareCenter.add(radius, 0, radius),   // NE
            squareCenter.add(-radius, 0, radius),  // NW
            squareCenter.add(-radius, 0, -radius), // SW
            squareCenter.add(radius, 0, -radius)   // SE
        };

        double minDistance = Double.MAX_VALUE;
        int closestCorner = 0;

        for (int i = 0; i < corners.length; i++) {
            double distance = playerPos.distanceTo(corners[i]);
            if (distance < minDistance) {
                minDistance = distance;
                closestCorner = i;
            }
        }

        squareCorner = closestCorner;
        startingCorner = closestCorner;
        cornersVisited = 0;

        info("Starting square pattern from " + getCornerName(closestCorner) + " corner at " + formatPos(corners[closestCorner]));
        info("Distance to nearest corner: " + String.format("%.1f blocks", minDistance));

        return corners[closestCorner];
    }

    private String getCornerName(int corner) {
        return switch (corner) {
            case 0 -> "North-East";
            case 1 -> "North-West";
            case 2 -> "South-West";
            case 3 -> "South-East";
            default -> "Unknown";
        };
    }
}
