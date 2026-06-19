package com.piotrek.peterwolfsrailroadsone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class RailSectionPathfinder {
	private static final int TRACK_SEARCH_RADIUS = 3;
	private static final int MAX_VISITED_BLOCKS = 8192;
	private static final double CART_FALLBACK_DISTANCE_SQR = 0.85D * 0.85D;

	private RailSectionPathfinder() {
	}

	static Optional<BlockPos> nearestTrack(final ServerLevel level, final BlockPos semaphore) {
		BlockPos closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (int dy = -2; dy <= 1; dy++) {
			for (int dx = -TRACK_SEARCH_RADIUS; dx <= TRACK_SEARCH_RADIUS; dx++) {
				for (int dz = -TRACK_SEARCH_RADIUS; dz <= TRACK_SEARCH_RADIUS; dz++) {
					BlockPos candidate = semaphore.offset(dx, dy, dz);
					if (!isTrack(level, candidate)) {
						continue;
					}
					double distance = dx * dx + dz * dz + dy * dy * 2.0D;
					if (distance < closestDistance) {
						closest = candidate.immutable();
						closestDistance = distance;
					}
				}
			}
		}
		return Optional.ofNullable(closest);
	}

	static Optional<Path> findPath(
		final ServerLevel level,
		final BlockPos start,
		final BlockPos end,
		final int maxPathBlocks
	) {
		if (start.equals(end)) {
			return Optional.empty();
		}

		Queue<SearchNode> queue = new ArrayDeque<>();
		Map<BlockPos, BlockPos> previous = new HashMap<>();
		Map<BlockPos, Integer> distances = new HashMap<>();
		BlockPos immutableStart = start.immutable();
		queue.add(new SearchNode(immutableStart, 0));
		distances.put(immutableStart, 0);

		while (!queue.isEmpty() && distances.size() <= MAX_VISITED_BLOCKS) {
			SearchNode node = queue.remove();
			if (node.pos().equals(end)) {
				List<BlockPos> ordered = reconstruct(previous, node.pos());
				return Optional.of(new Path(ordered, expandedTrackBlocks(level, ordered)));
			}
			if (node.distance() >= maxPathBlocks) {
				continue;
			}
			for (BlockPos neighbor : neighbors(level, node.pos())) {
				if (distances.putIfAbsent(neighbor, node.distance() + 1) == null) {
					previous.put(neighbor, node.pos());
					queue.add(new SearchNode(neighbor, node.distance() + 1));
				}
			}
		}
		return Optional.empty();
	}

	static Set<BlockPos> approachTrack(
		final ServerLevel level,
		final BlockPos endpoint,
		final BlockPos interiorNeighbor,
		final Set<BlockPos> sectionPath,
		final int maxDistance
	) {
		Set<BlockPos> result = new HashSet<>();
		Queue<SearchNode> queue = new ArrayDeque<>();
		queue.add(new SearchNode(endpoint, 0));
		result.add(endpoint);
		while (!queue.isEmpty()) {
			SearchNode node = queue.remove();
			if (node.distance() >= maxDistance) {
				continue;
			}
			for (BlockPos neighbor : neighbors(level, node.pos())) {
				if (neighbor.equals(interiorNeighbor) || sectionPath.contains(neighbor) || !result.add(neighbor)) {
					continue;
				}
				queue.add(new SearchNode(neighbor, node.distance() + 1));
			}
		}
		return result;
	}

	static int pathIndexForCart(final List<BlockPos> path, final AbstractMinecart cart) {
		BlockPos direct = cart.getCurrentBlockPosOrRailBelow();
		int index = directIndex(path, direct);
		if (index >= 0) {
			return index;
		}

		Vec3 cartPosition = cart.position();
		double nearestDistance = Double.MAX_VALUE;
		int nearestIndex = -1;
		for (int i = 0; i < path.size(); i++) {
			Vec3 center = Vec3.atCenterOf(path.get(i));
			double dx = cartPosition.x - center.x;
			double dz = cartPosition.z - center.z;
			double distance = dx * dx + dz * dz;
			if (Math.abs(cartPosition.y - center.y) <= 1.5D && distance < nearestDistance) {
				nearestDistance = distance;
				nearestIndex = i;
			}
		}
		return nearestDistance <= CART_FALLBACK_DISTANCE_SQR ? nearestIndex : -1;
	}

	static boolean containsCart(final Set<BlockPos> trackBlocks, final AbstractMinecart cart) {
		BlockPos direct = cart.getCurrentBlockPosOrRailBelow();
		if (trackBlocks.contains(direct) || trackBlocks.contains(direct.below()) || trackBlocks.contains(direct.above())) {
			return true;
		}
		Vec3 cartPosition = cart.position();
		for (BlockPos track : trackBlocks) {
			Vec3 center = Vec3.atCenterOf(track);
			double dx = cartPosition.x - center.x;
			double dz = cartPosition.z - center.z;
			if (Math.abs(cartPosition.y - center.y) <= 1.5D && dx * dx + dz * dz <= CART_FALLBACK_DISTANCE_SQR) {
				return true;
			}
		}
		return false;
	}

	static BlockPos trackPositionForCart(final ServerLevel level, final AbstractMinecart cart) {
		BlockPos direct = cart.getCurrentBlockPosOrRailBelow();
		if (isTrack(level, direct)) {
			return direct;
		}
		if (isTrack(level, direct.below())) {
			return direct.below();
		}
		if (isTrack(level, direct.above())) {
			return direct.above();
		}
		return direct;
	}

	static AABB bounds(final Collection<BlockPos> path) {
		int minX = path.stream().mapToInt(BlockPos::getX).min().orElse(0);
		int minY = path.stream().mapToInt(BlockPos::getY).min().orElse(0);
		int minZ = path.stream().mapToInt(BlockPos::getZ).min().orElse(0);
		int maxX = path.stream().mapToInt(BlockPos::getX).max().orElse(0) + 1;
		int maxY = path.stream().mapToInt(BlockPos::getY).max().orElse(0) + 1;
		int maxZ = path.stream().mapToInt(BlockPos::getZ).max().orElse(0) + 1;
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static int directIndex(final List<BlockPos> path, final BlockPos pos) {
		int index = path.indexOf(pos);
		if (index < 0) {
			index = path.indexOf(pos.below());
		}
		if (index < 0) {
			index = path.indexOf(pos.above());
		}
		return index;
	}

	private static List<BlockPos> reconstruct(final Map<BlockPos, BlockPos> previous, final BlockPos end) {
		List<BlockPos> path = new ArrayList<>();
		BlockPos current = end;
		while (current != null) {
			path.add(current);
			current = previous.get(current);
		}
		Collections.reverse(path);
		return List.copyOf(path);
	}

	private static Set<BlockPos> expandedTrackBlocks(final ServerLevel level, final List<BlockPos> orderedPath) {
		Set<BlockPos> expanded = new HashSet<>(orderedPath);
		for (BlockPos pathBlock : orderedPath) {
			BlockState state = level.getBlockState(pathBlock);
			if (state.is(ModBlocks.RAIL_CURVE_MARKER)) {
				level.getEntities(ModEntities.GENTLE_RAIL_CURVE, new AABB(pathBlock).inflate(4.5D), entity -> entity.ownsMarker(pathBlock))
					.forEach(entity -> expanded.addAll(CurvePlacementHelper.occupiedPositions(
						entity.anchor(),
						entity.facing(),
						entity.turn(),
						entity.size()
					)));
			} else if (state.is(ModBlocks.PARALLEL_SIDING_SWITCH)) {
				level.getEntities(ModEntities.PARALLEL_SIDING_SWITCH, new AABB(pathBlock).inflate(3.5D), entity -> entity.ownsMarker(pathBlock))
					.forEach(entity -> expanded.addAll(ParallelSidingSwitchPlacementHelper.occupiedPositions(
						entity.anchor(),
						entity.facing(),
						entity.variant()
					)));
			}
		}
		return Set.copyOf(expanded);
	}

	private static List<BlockPos> neighbors(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Set<Direction> exits = exits(state);
		List<BlockPos> result = new ArrayList<>(exits.size());
		for (Direction direction : exits) {
			for (int dy : new int[] {0, 1, -1}) {
				BlockPos candidate = pos.relative(direction).offset(0, dy, 0);
				if (!isTrack(level, candidate)) {
					continue;
				}
				if (exits(level.getBlockState(candidate)).contains(direction.getOpposite())) {
					result.add(candidate.immutable());
				}
			}
		}
		return result;
	}

	private static Set<Direction> exits(final BlockState state) {
		if (state.getBlock() instanceof BaseRailBlock rail) {
			RailShape shape = state.getValue(rail.getShapeProperty());
			return switch (shape) {
				case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> EnumSet.of(Direction.NORTH, Direction.SOUTH);
				case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> EnumSet.of(Direction.EAST, Direction.WEST);
				case SOUTH_EAST -> EnumSet.of(Direction.SOUTH, Direction.EAST);
				case SOUTH_WEST -> EnumSet.of(Direction.SOUTH, Direction.WEST);
				case NORTH_WEST -> EnumSet.of(Direction.NORTH, Direction.WEST);
				case NORTH_EAST -> EnumSet.of(Direction.NORTH, Direction.EAST);
			};
		}
		if (state.is(ModBlocks.RAIL_CURVE_MARKER) || state.is(ModBlocks.PARALLEL_SIDING_SWITCH)) {
			return EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
		}
		return Set.of();
	}

	private static boolean isTrack(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return BaseRailBlock.isRail(state)
			|| state.is(ModBlocks.RAIL_CURVE_MARKER)
			|| state.is(ModBlocks.PARALLEL_SIDING_SWITCH);
	}

	record Path(List<BlockPos> blocks, Set<BlockPos> trackBlocks) {
		Path {
			blocks = List.copyOf(blocks);
			trackBlocks = Set.copyOf(trackBlocks);
		}
	}

	private record SearchNode(BlockPos pos, int distance) {
	}
}
