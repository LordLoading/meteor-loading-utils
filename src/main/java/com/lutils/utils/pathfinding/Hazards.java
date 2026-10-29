package com.lutils.utils.pathfinding;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;

/** Blocks that hurt when touched. Tags are used instead of block ids so modded lava and fire count too. */
public final class Hazards {
    private Hazards() {}

    public static boolean isHazard(BlockState state) {
        if (state.isAir()) return false;
        return state.getFluidState().isIn(FluidTags.LAVA) || state.isIn(BlockTags.FIRE) || state.isOf(Blocks.MAGMA_BLOCK);
    }
}
