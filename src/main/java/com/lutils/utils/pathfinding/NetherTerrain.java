package com.lutils.utils.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Predicts Nether terrain from the world seed by sampling the vanilla final density function
 * at the corners of the noise cells (4x8x4 blocks), exactly where vanilla samples it before interpolating.
 * Density > 0 is solid, density <= 0 is air.
 */
public class NetherTerrain {
    public static final int CELL_XZ = 4;
    public static final int CELL_Y = 8;
    public static final int CELLS_Y = 16;
    /** Air below this height is filled with lava, so predicted cells there are off limits. */
    public static final int LAVA_LEVEL = 32;
    public static final int FIRST_SAFE_CELL = LAVA_LEVEL / CELL_Y;

    private static final int MAX_CACHED_CORNERS = 8_000_000;

    public final long seed;
    private final DensityFunction density;
    private final Long2FloatOpenHashMap corners = new Long2FloatOpenHashMap();

    public NetherTerrain(long seed) {
        this.seed = seed;

        RegistryWrapper.WrapperLookup lookup = BuiltinRegistries.createWrapperLookup();
        ChunkGeneratorSettings settings = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
            .getOrThrow(ChunkGeneratorSettings.NETHER).value();
        NoiseConfig noise = NoiseConfig.create(settings, lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        density = noise.getNoiseRouter().finalDensity();
    }

    /** Density at the noise-cell corner (ix, iy, iz); iy is in 0..CELLS_Y. */
    public float corner(int ix, int iy, int iz) {
        if (iy < 0 || iy > CELLS_Y) return 1f;

        long key = ((long) (ix & 0xFFFFFF) << 40) | ((long) (iz & 0xFFFFFF) << 16) | iy;
        float value = corners.getOrDefault(key, Float.NaN);
        if (!Float.isNaN(value)) return value;

        if (corners.size() > MAX_CACHED_CORNERS) corners.clear();
        value = (float) density.sample(new DensityFunction.UnblendedNoisePos(ix * CELL_XZ, iy * CELL_Y, iz * CELL_XZ));
        corners.put(key, value);
        return value;
    }

    /** Density at an arbitrary position, interpolated between the cell corners like vanilla does. */
    public float densityAt(double x, double y, double z) {
        double fx = x / CELL_XZ, fy = y / CELL_Y, fz = z / CELL_XZ;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy), iz = (int) Math.floor(fz);
        float tx = (float) (fx - ix), ty = (float) (fy - iy), tz = (float) (fz - iz);

        float c00 = lerp(tx, corner(ix, iy, iz), corner(ix + 1, iy, iz));
        float c10 = lerp(tx, corner(ix, iy + 1, iz), corner(ix + 1, iy + 1, iz));
        float c01 = lerp(tx, corner(ix, iy, iz + 1), corner(ix + 1, iy, iz + 1));
        float c11 = lerp(tx, corner(ix, iy + 1, iz + 1), corner(ix + 1, iy + 1, iz + 1));
        return lerp(tz, lerp(ty, c00, c10), lerp(ty, c01, c11));
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    /** True when the density at the cell center is air by at least {@code margin}. */
    public boolean isAirCell(int cx, int cy, int cz, float margin) {
        if (cy < FIRST_SAFE_CELL || cy >= CELLS_Y) return false;
        return densityAt(cx * CELL_XZ + CELL_XZ / 2.0, cy * CELL_Y + CELL_Y / 2.0, cz * CELL_XZ + CELL_XZ / 2.0) < -margin;
    }
}
