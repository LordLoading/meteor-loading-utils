package com.lutils.utils;

import baritone.api.utils.BetterBlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class BaritoneBridge implements BaritoneHook.Bridge {
    private static final int INSERT_RADIUS = 16;
    private static final int GOAL_INSERT_RADIUS = 2;

    private final Constructor<?> contextConstructor;
    private final Method insertChunk;
    private final Method pathFind;
    private final Field finished;
    private final Field packed;

    private final Set<Long> insertedChunks = new HashSet<>();

    private Object context;
    private long contextSeed;
    private World contextWorld;

    BaritoneBridge() throws ReflectiveOperationException {
        Class<?> contextClass = Class.forName("baritone.process.elytra.NetherPathfinderContext");
        contextConstructor = contextClass.getConstructor(long.class);
        insertChunk = findMethod(contextClass, WorldChunk.class);
        pathFind = findPathMethod(contextClass);

        Class<?> segmentClass = Class.forName("dev.babbaj.pathfinder.PathSegment");
        finished = segmentClass.getField("finished");
        packed = segmentClass.getField("packed");
    }

    @Override
    public BaritoneHook.PathResult findPath(BlockPos from, BlockPos to, long seed) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (context == null || contextSeed != seed || contextWorld != mc.world) {
                context = contextConstructor.newInstance(seed);
                contextSeed = seed;
                contextWorld = mc.world;
                insertedChunks.clear();
            }

            insertChunks(from, INSERT_RADIUS);
            insertChunks(to, GOAL_INSERT_RADIUS);

            CompletableFuture<?> future = (CompletableFuture<?>) pathFind.invoke(context, BetterBlockPos.from(from), BetterBlockPos.from(to));
            Object segment = future.get();

            long[] packedPoints = (long[]) packed.get(segment);
            List<Vec3d> points = new ArrayList<>(packedPoints.length);
            for (long point : packedPoints) {
                BetterBlockPos pos = BetterBlockPos.deserializeFromLong(point);
                points.add(new Vec3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5));
            }

            return new BaritoneHook.PathResult(points, finished.getBoolean(segment));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void insertChunks(BlockPos center, int radius) throws ReflectiveOperationException {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        int centerX = center.getX() >> 4;
        int centerZ = center.getZ() >> 4;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                if (!insertedChunks.add(key)) continue;

                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) {
                    insertedChunks.remove(key);
                    continue;
                }

                insertChunk.invoke(context, chunk);
            }
        }
    }

    private static Method findMethod(Class<?> type, Class<?> parameter) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(parameter)) return method;
        }
        throw new IllegalStateException("No chunk insert method found in " + type.getName());
    }

    private static Method findPathMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2
                && parameters[0].isAssignableFrom(BetterBlockPos.class)
                && parameters[1].isAssignableFrom(BetterBlockPos.class)
                && CompletableFuture.class.isAssignableFrom(method.getReturnType())) return method;
        }
        throw new IllegalStateException("No path find method found in " + type.getName());
    }
}
