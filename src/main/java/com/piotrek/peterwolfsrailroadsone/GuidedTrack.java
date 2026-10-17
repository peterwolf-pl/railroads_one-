package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;

final class GuidedTrack {
	private GuidedTrack() {
	}

	static boolean isPresent(final Level level, final BlockPos pos) {
		return isPresent(level.getBlockState(pos)) || isPresent(level.getBlockState(pos.below()));
	}

	private static boolean isPresent(final BlockState state) {
		return BaseRailBlock.isRail(state)
			|| state.is(ModBlocks.RAIL_CURVE_MARKER)
			|| state.is(ModBlocks.PARALLEL_SIDING_SWITCH);
	}
}
