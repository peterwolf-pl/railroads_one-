package com.piotrek.peterwolfsrailroadsone;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public final class CurvePlacementHelper {
	private CurvePlacementHelper() {
	}

	public static Placement fromUseContext(final UseOnContext context, final CurveTurn turn, final CurveSize size) {
		BlockPos clicked = context.getClickedPos();
		BlockPos anchor = clicked.relative(context.getClickedFace());
		if (context.getClickedFace() != Direction.UP && context.getClickedFace() != Direction.DOWN) {
			anchor = clicked.above();
		}
		return new Placement(anchor, context.getHorizontalDirection(), turn, size);
	}

	public static boolean canPlace(final ServerLevel level, final Placement placement) {
		for (BlockPos pos : occupiedPositions(placement.anchor(), placement.facing(), placement.turn(), placement.size())) {
			BlockState state = level.getBlockState(pos);
			if (!state.canBeReplaced() && !state.is(ModBlocks.RAIL_CURVE_MARKER)) {
				return false;
			}
		}
		List<GentleRailCurveEntity> curves = level.getEntities(
			ModEntities.GENTLE_RAIL_CURVE,
			placementBounds(placement).inflate(0.05),
			entity -> true
		);
		for (GentleRailCurveEntity curve : curves) {
			if (!curve.hasCompleteMarkerFootprint(level)) {
				curve.remove(Entity.RemovalReason.KILLED);
			}
		}
		return curves.stream().allMatch(Entity::isRemoved);
	}

	public static void reserve(final ServerLevel level, final Placement placement) {
		for (BlockPos pos : occupiedPositions(placement.anchor(), placement.facing(), placement.turn(), placement.size())) {
			level.setBlock(pos, markerState(RailCurveMarkerBlock.Segment.EMPTY, placement.facing()), 3);
		}
		for (Marker marker : visibleMarkers(placement)) {
			level.setBlock(marker.pos(), markerState(marker.segment(), marker.facing()), 3);
		}
	}

	public static void release(final ServerLevel level, final BlockPos anchor, final Direction facing, final CurveTurn turn, final CurveSize size) {
		for (BlockPos pos : occupiedPositions(anchor, facing, turn, size)) {
			if (level.getBlockState(pos).is(ModBlocks.RAIL_CURVE_MARKER)) {
				level.removeBlock(pos, false);
			}
		}
	}

	public static List<BlockPos> occupiedPositions(final BlockPos anchor, final Direction facing, final CurveTurn turn, final CurveSize size) {
		List<BlockPos> positions = new ArrayList<>(size.blocks() * size.blocks());
		Direction back = facing.getOpposite();
		Direction exit = exitDirection(facing, turn);
		for (int alongBack = 0; alongBack < size.blocks(); alongBack++) {
			for (int alongExit = 0; alongExit < size.blocks(); alongExit++) {
				positions.add(anchor.relative(back, alongBack).relative(exit, alongExit));
			}
		}
		return positions;
	}

	public static List<Marker> visibleMarkers(final Placement placement) {
		if (placement.size() == CurveSize.SMALL_2X2) {
			return imageTileMarkers2x2(placement);
		}
		return imageTileMarkers3x3(placement);
	}

	private static List<Marker> imageTileMarkers3x3(final Placement placement) {
		Direction exit = exitDirection(placement.facing(), placement.turn());
		Direction back = placement.facing().getOpposite();
		RailCurveMarkerBlock.Segment[] tiles = placement.turn() == CurveTurn.RIGHT
			? new RailCurveMarkerBlock.Segment[] {
				RailCurveMarkerBlock.Segment.RIGHT_3X3_NW,
				RailCurveMarkerBlock.Segment.RIGHT_3X3_N,
				RailCurveMarkerBlock.Segment.RIGHT_3X3_NE,
				RailCurveMarkerBlock.Segment.RIGHT_3X3_W,
				RailCurveMarkerBlock.Segment.RIGHT_3X3_CENTER,
				RailCurveMarkerBlock.Segment.RIGHT_3X3_E,
				RailCurveMarkerBlock.Segment.RIGHT_3X3_SW,
				RailCurveMarkerBlock.Segment.RIGHT_3X3_S,
				RailCurveMarkerBlock.Segment.RIGHT_3X3_SE
			}
			: new RailCurveMarkerBlock.Segment[] {
				RailCurveMarkerBlock.Segment.LEFT_3X3_NW,
				RailCurveMarkerBlock.Segment.LEFT_3X3_N,
				RailCurveMarkerBlock.Segment.LEFT_3X3_NE,
				RailCurveMarkerBlock.Segment.LEFT_3X3_W,
				RailCurveMarkerBlock.Segment.LEFT_3X3_CENTER,
				RailCurveMarkerBlock.Segment.LEFT_3X3_E,
				RailCurveMarkerBlock.Segment.LEFT_3X3_SW,
				RailCurveMarkerBlock.Segment.LEFT_3X3_S,
				RailCurveMarkerBlock.Segment.LEFT_3X3_SE
			};

		List<Marker> markers = new ArrayList<>(9);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				int exitOffset = placement.turn() == CurveTurn.RIGHT ? column : 2 - column;
				BlockPos pos = placement.anchor().relative(back, row).relative(exit, exitOffset);
				markers.add(new Marker(pos, placement.facing(), tiles[row * 3 + column]));
			}
		}
		return markers;
	}

	private static List<Marker> imageTileMarkers2x2(final Placement placement) {
		Direction exit = exitDirection(placement.facing(), placement.turn());
		Direction back = placement.facing().getOpposite();
		BlockPos anchor = placement.anchor();
		if (placement.turn() == CurveTurn.RIGHT) {
			return List.of(
				new Marker(anchor, placement.facing(), RailCurveMarkerBlock.Segment.RIGHT_2X2_NW),
				new Marker(anchor.relative(exit), placement.facing(), RailCurveMarkerBlock.Segment.RIGHT_2X2_NE),
				new Marker(anchor.relative(back), placement.facing(), RailCurveMarkerBlock.Segment.RIGHT_2X2_SW),
				new Marker(anchor.relative(back).relative(exit), placement.facing(), RailCurveMarkerBlock.Segment.RIGHT_2X2_SE)
			);
		}

		return List.of(
			new Marker(anchor.relative(exit), placement.facing(), RailCurveMarkerBlock.Segment.LEFT_2X2_NW),
			new Marker(anchor, placement.facing(), RailCurveMarkerBlock.Segment.LEFT_2X2_NE),
			new Marker(anchor.relative(back).relative(exit), placement.facing(), RailCurveMarkerBlock.Segment.LEFT_2X2_SW),
			new Marker(anchor.relative(back), placement.facing(), RailCurveMarkerBlock.Segment.LEFT_2X2_SE)
		);
	}

	public static Direction exitDirection(final Direction facing, final CurveTurn turn) {
		return turn == CurveTurn.RIGHT ? facing.getClockWise() : facing.getCounterClockWise();
	}

	private static BlockState markerState(final RailCurveMarkerBlock.Segment segment, final Direction facing) {
		return ModBlocks.RAIL_CURVE_MARKER.defaultBlockState()
			.setValue(RailCurveMarkerBlock.SEGMENT, segment)
			.setValue(RailCurveMarkerBlock.FACING, facing);
	}

	public static net.minecraft.world.phys.AABB placementBounds(final Placement placement) {
		List<BlockPos> positions = occupiedPositions(placement.anchor(), placement.facing(), placement.turn(), placement.size());
		int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(placement.anchor().getX());
		int minY = positions.stream().mapToInt(BlockPos::getY).min().orElse(placement.anchor().getY());
		int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(placement.anchor().getZ());
		int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(placement.anchor().getX()) + 1;
		int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElse(placement.anchor().getY()) + 1;
		int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(placement.anchor().getZ()) + 1;
		return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public record Placement(BlockPos anchor, Direction facing, CurveTurn turn, CurveSize size) {
	}

	public record Marker(BlockPos pos, Direction facing, RailCurveMarkerBlock.Segment segment) {
	}
}
