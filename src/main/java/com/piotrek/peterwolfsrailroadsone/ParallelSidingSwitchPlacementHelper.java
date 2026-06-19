package com.piotrek.peterwolfsrailroadsone;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class ParallelSidingSwitchPlacementHelper {
	private static final int FOOTPRINT_BLOCKS = 2;

	private ParallelSidingSwitchPlacementHelper() {
	}

	public static Placement fromUseContext(final UseOnContext context, final ParallelSidingSwitchVariant variant) {
		BlockPos clicked = context.getClickedPos();
		BlockState clickedState = context.getLevel().getBlockState(clicked);
		BlockPos anchor = clickedState.getBlock() instanceof BaseRailBlock ? clicked : clicked.relative(context.getClickedFace());
		if (!(clickedState.getBlock() instanceof BaseRailBlock) && context.getClickedFace() != Direction.UP && context.getClickedFace() != Direction.DOWN) {
			anchor = clicked.above();
		}
		return new Placement(anchor, context.getHorizontalDirection(), variant);
	}

	public static boolean canPlace(final ServerLevel level, final Placement placement) {
		return true;
	}

	public static void reserve(final ServerLevel level, final Placement placement) {
		boolean powered = isPowered(level, placement);
		for (Marker marker : visibleMarkers(placement, powered)) {
			BlockState state = level.getBlockState(marker.pos());
			if (state.canBeReplaced() || state.getBlock() instanceof BaseRailBlock || state.is(ModBlocks.PARALLEL_SIDING_SWITCH)) {
				level.setBlock(marker.pos(), markerState(marker), 3);
			}
		}
	}

	public static void release(
		final ServerLevel level,
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant
	) {
		for (BlockPos pos : occupiedPositions(anchor, facing, variant)) {
			if (level.getBlockState(pos).is(ModBlocks.PARALLEL_SIDING_SWITCH)) {
				level.removeBlock(pos, false);
			}
		}
	}

	public static boolean isPowered(final ServerLevel level, final Placement placement) {
		for (BlockPos pos : occupiedPositions(placement.anchor(), placement.facing(), placement.variant())) {
			if (level.hasNeighborSignal(pos)) {
				return true;
			}
		}
		return false;
	}

	public static void syncPoweredMarkers(final ServerLevel level, final Placement placement, final boolean powered) {
		for (Marker marker : visibleMarkers(placement, powered)) {
			BlockState state = level.getBlockState(marker.pos());
			if (state.is(ModBlocks.PARALLEL_SIDING_SWITCH) && state.getValue(ParallelSidingSwitchBlock.POWERED) != powered) {
				level.setBlock(marker.pos(), markerState(marker), 3);
			}
		}
	}

	public static void refreshPowered(final ServerLevel level, final BlockPos pos) {
		level.getEntities(ModEntities.PARALLEL_SIDING_SWITCH, new AABB(pos).inflate(2.5), entity -> entity.ownsMarker(pos))
			.forEach(entity -> entity.refreshPowered(level));
	}

	public static List<BlockPos> occupiedPositions(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant
	) {
		List<BlockPos> positions = new ArrayList<>(FOOTPRINT_BLOCKS * FOOTPRINT_BLOCKS);
		Direction back = facing.getOpposite();
		Direction side = variant.sideDirection(facing);
		for (int alongBack = 0; alongBack < FOOTPRINT_BLOCKS; alongBack++) {
			for (int alongSide = 0; alongSide < FOOTPRINT_BLOCKS; alongSide++) {
				positions.add(anchor.relative(back, alongBack).relative(side, alongSide));
			}
		}
		return positions;
	}

	public static List<Marker> visibleMarkers(final Placement placement, final boolean powered) {
		Direction back = placement.facing().getOpposite();
		Direction side = placement.variant().sideDirection(placement.facing());
		BlockPos anchor = placement.anchor();
		if (placement.variant() == ParallelSidingSwitchVariant.RIGHT) {
			return List.of(
				new Marker(anchor, placement.facing(), placement.variant(), ParallelSidingSwitchBlock.Tile.NW, powered),
				new Marker(anchor.relative(side), placement.facing(), placement.variant(), ParallelSidingSwitchBlock.Tile.NE, powered),
				new Marker(anchor.relative(back), placement.facing(), placement.variant(), ParallelSidingSwitchBlock.Tile.SW, powered),
				new Marker(anchor.relative(back).relative(side), placement.facing(), placement.variant(), ParallelSidingSwitchBlock.Tile.SE, powered)
			);
		}

		return List.of(
			new Marker(anchor.relative(side), placement.facing(), placement.variant(), ParallelSidingSwitchBlock.Tile.NW, powered),
			new Marker(anchor, placement.facing(), placement.variant(), ParallelSidingSwitchBlock.Tile.NE, powered),
			new Marker(anchor.relative(back).relative(side), placement.facing(), placement.variant(), ParallelSidingSwitchBlock.Tile.SW, powered),
			new Marker(anchor.relative(back), placement.facing(), placement.variant(), ParallelSidingSwitchBlock.Tile.SE, powered)
		);
	}

	public static AABB placementBounds(final Placement placement) {
		List<BlockPos> positions = occupiedPositions(placement.anchor(), placement.facing(), placement.variant());
		int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(placement.anchor().getX());
		int minY = positions.stream().mapToInt(BlockPos::getY).min().orElse(placement.anchor().getY());
		int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(placement.anchor().getZ());
		int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(placement.anchor().getX()) + 1;
		int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElse(placement.anchor().getY()) + 1;
		int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(placement.anchor().getZ()) + 1;
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static BlockState markerState(final Marker marker) {
		return ModBlocks.PARALLEL_SIDING_SWITCH.defaultBlockState()
			.setValue(ParallelSidingSwitchBlock.FACING, marker.facing())
			.setValue(ParallelSidingSwitchBlock.VARIANT, marker.variant())
			.setValue(ParallelSidingSwitchBlock.POWERED, marker.powered())
			.setValue(ParallelSidingSwitchBlock.TILE, marker.tile());
	}

	public record Placement(BlockPos anchor, Direction facing, ParallelSidingSwitchVariant variant) {
	}

	public record Marker(
		BlockPos pos,
		Direction facing,
		ParallelSidingSwitchVariant variant,
		ParallelSidingSwitchBlock.Tile tile,
		boolean powered
	) {
	}
}
