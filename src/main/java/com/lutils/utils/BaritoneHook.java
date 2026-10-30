package com.lutils.utils;

import com.lutils.LUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class BaritoneHook {
    interface Bridge {
        PathResult findPath(BlockPos from, BlockPos to, long seed);
    }

    public record PathResult(List<Vec3d> points, boolean finished) {
    }

    private static boolean resolved;
    private static boolean available;
    private static Bridge bridge;

    private BaritoneHook() {
    }

    public static boolean isAvailable() {
        resolve();
        return available;
    }

    public static PathResult findPath(BlockPos from, BlockPos to, long seed) {
        resolve();
        if (!available) return null;

        return bridge.findPath(from, to, seed);
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;

        try {
            Class.forName("baritone.api.BaritoneAPI");
        } catch (Throwable ignored) {
            return;
        }

        try {
            Class.forName("baritone.process.elytra.NetherPathfinderContext");
            bridge = (Bridge) Class.forName("com.lutils.utils.BaritoneBridge").getDeclaredConstructor().newInstance();
            available = true;
        } catch (Throwable t) {
            LUtils.LOG.warn("Baritone pathfinding is unavailable", t);
        }
    }
}
