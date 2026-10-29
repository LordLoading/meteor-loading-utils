package com.lutils.utils.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
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
    /** How far below a cell the ground is looked for. */
    private static final int MAX_FLOOR_SCAN = 40;

    public final long seed;
    private final DensityFunction density;
    private final Long2FloatOpenHashMap corners = new Long2FloatOpenHashMap();
    private final MultiNoiseUtil.Entries<RegistryKey<Biome>> biomes = MultiNoiseBiomeSourceParameterList.getPresetToEntriesMap().get(MultiNoiseBiomeSourceParameterList.Preset.NETHER);
    private final MultiNoiseUtil.MultiNoiseSampler biomeSampler;
    private final Long2ObjectOpenHashMap<RegistryKey<Biome>> biomeCells = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap floorHeights = new Long2IntOpenHashMap();

    public NetherTerrain(long seed) {
        this.seed = seed;

        RegistryWrapper.WrapperLookup lookup = BuiltinRegistries.createWrapperLookup();
        ChunkGeneratorSettings settings = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
            .getOrThrow(ChunkGeneratorSettings.NETHER).value();
        NoiseConfig noise = NoiseConfig.create(settings, lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        density = noise.getNoiseRouter().finalDensity();
        biomeSampler = noise.getMultiNoiseSampler();
        floorHeights.defaultReturnValue(-1);
    }

    /** The biome the seed gives a noise cell column. A cell is one biome cell wide, and the Nether's biomes don't depend on the height. */
    public RegistryKey<Biome> biomeAt(int cx, int cz) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        RegistryKey<Biome> cached = biomeCells.get(key);
        if (cached != null) return cached;

        RegistryKey<Biome> biome = biomes.getValue(biomeSampler.sample(cx, 16, cz));
        biomeCells.put(key, biome);
        return biome;
    }

    /** Blocks between the ground below a cell's center and that center, or more than {@link #MAX_FLOOR_SCAN} when there is no ground in reach. */
    public int heightAboveFloor(int cx, int cy, int cz) {
        long key = ((long) (cx & 0xFFFFFF) << 40) | ((long) (cz & 0xFFFFFF) << 16) | (cy & 0xFFFF);
        int cached = floorHeights.get(key);
        if (cached != -1) return cached;

        double x = cx * CELL_XZ + CELL_XZ / 2.0, y = cy * CELL_Y + CELL_Y / 2.0, z = cz * CELL_XZ + CELL_XZ / 2.0;
        int height = MAX_FLOOR_SCAN + 1;
        for (int d = 0; d <= MAX_FLOOR_SCAN; d += 2) {
            if (densityAt(x, y - d, z) > 0) {
                height = d;
                break;
            }
        }
        floorHeights.put(key, height);
        return height;
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
