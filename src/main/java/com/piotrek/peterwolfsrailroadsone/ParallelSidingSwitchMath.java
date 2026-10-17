package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ParallelSidingSwitchMath {
	private static final double ROUTE_Y_OFFSET = 0.08;
	private static final double SIDING_CONTROL_LENGTH = 0.68;

	private ParallelSidingSwitchMath() {
	}

	public static Vec3 point(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final ParallelSidingSwitchRoute route,
		final double progress
	) {
		double t = Mth.clamp(progress, 0.0, 1.0);
		double startX = anchor.getX() + 0.5D - facing.getStepX();
		double startY = anchor.getY() + ROUTE_Y_OFFSET;
		double startZ = anchor.getZ() + 0.5D - facing.getStepZ();
		if (route == ParallelSidingSwitchRoute.MAIN) {
			return new Vec3(startX + facing.getStepX() * t, startY, startZ + facing.getStepZ() * t);
		}

		double forward = sidingForward(t);
		double side = sidingSide(t);
		Direction sideDirection = variant.sideDirection(facing);
		return new Vec3(
			startX + facing.getStepX() * forward + sideDirection.getStepX() * side,
			startY,
			startZ + facing.getStepZ() * forward + sideDirection.getStepZ() * side
		);
	}

	public static Vec3 tangent(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final ParallelSidingSwitchRoute route,
		final double progress
	) {
		double t = Mth.clamp(progress, 0.0, 1.0);
		if (route == ParallelSidingSwitchRoute.MAIN) {
			return horizontal(facing);
		}

		double forward = sidingForwardDerivative(t);
		double side = sidingSideDerivative(t);
		Direction sideDirection = variant.sideDirection(facing);
		double x = facing.getStepX() * forward + sideDirection.getStepX() * side;
		double z = facing.getStepZ() * forward + sideDirection.getStepZ() * side;
		double length = Math.sqrt(x * x + z * z);
		return new Vec3(x / length, 0.0D, z / length);
	}

	public static double nearestProgress(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final ParallelSidingSwitchRoute route,
		final Vec3 position
	) {
		return Mth.clamp(nearestProgressExtended(anchor, facing, variant, route, position), 0.0, 1.0);
	}

	public static double nearestProgressExtended(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final ParallelSidingSwitchRoute route,
		final Vec3 position
	) {
		double startX = anchor.getX() + 0.5D - facing.getStepX();
		double startZ = anchor.getZ() + 0.5D - facing.getStepZ();
		double relativeX = position.x - startX;
		double relativeZ = position.z - startZ;
		double localForward = relativeX * facing.getStepX() + relativeZ * facing.getStepZ();
		double length = routeLength(route);
		if (route == ParallelSidingSwitchRoute.MAIN) {
			return localForward;
		}

		Direction sideDirection = variant.sideDirection(facing);
		double localSide = relativeX * sideDirection.getStepX() + relativeZ * sideDirection.getStepZ();
		double bestProgress = 0.0;
		double bestDistance = Double.MAX_VALUE;
		for (int step = 0; step <= 32; step++) {
			double progress = step / 32.0;
			double forwardDelta = localForward - sidingForward(progress);
			double sideDelta = localSide - sidingSide(progress);
			double distance = forwardDelta * forwardDelta + sideDelta * sideDelta;
			if (distance < bestDistance) {
				bestDistance = distance;
				bestProgress = progress;
			}
		}

		Vec3 start = point(anchor, facing, variant, route, 0.0D);
		Vec3 end = point(anchor, facing, variant, route, 1.0D);
		if (bestProgress <= 0.001D) {
			double alongStart = (position.x - start.x) * facing.getStepX() + (position.z - start.z) * facing.getStepZ();
			if (alongStart < 0.0D) {
				return alongStart / length;
			}
		}
		if (bestProgress >= 0.999D) {
			Vec3 exitTangent = tangent(anchor, facing, variant, route, 1.0D);
			double alongExit = (position.x - end.x) * exitTangent.x + (position.z - end.z) * exitTangent.z;
			if (alongExit > 0.0D) {
				return 1.0D + alongExit / length;
			}
		}
		return bestProgress;
	}

	public static double routeLength(final ParallelSidingSwitchRoute route) {
		return route == ParallelSidingSwitchRoute.MAIN ? 1.0 : 1.55;
	}

	public static Vec3 pointExtended(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final ParallelSidingSwitchRoute route,
		final double progress
	) {
		double length = routeLength(route);
		if (progress >= 0.0D && progress <= 1.0D) {
			return point(anchor, facing, variant, route, progress);
		}
		if (progress < 0.0D) {
			return point(anchor, facing, variant, route, 0.0D)
				.add(tangent(anchor, facing, variant, route, 0.0D).scale(progress * length));
		}
		return point(anchor, facing, variant, route, 1.0D)
			.add(tangent(anchor, facing, variant, route, 1.0D).scale((progress - 1.0D) * length));
	}

	public static Vec3 tangentExtended(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final ParallelSidingSwitchRoute route,
		final double progress
	) {
		if (progress <= 0.0D) {
			return tangent(anchor, facing, variant, route, 0.0D);
		}
		if (progress >= 1.0D) {
			return tangent(anchor, facing, variant, route, 1.0D);
		}
		return tangent(anchor, facing, variant, route, progress);
	}

	public static double progressStepForSpeed(final ParallelSidingSwitchRoute route, final double horizontalSpeed) {
		double length = routeLength(route);
		double maxStep = route == ParallelSidingSwitchRoute.MAIN ? 0.32 : 0.22;
		return Mth.clamp(Math.max(horizontalSpeed, 0.12) / length, 0.04, maxStep);
	}

	public static double sideOffset(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final Vec3 position
	) {
		Direction sideDirection = variant.sideDirection(facing);
		double startX = anchor.getX() + 0.5D - facing.getStepX();
		double startZ = anchor.getZ() + 0.5D - facing.getStepZ();
		return (position.x - startX) * sideDirection.getStepX() + (position.z - startZ) * sideDirection.getStepZ();
	}

	public static double forwardOffset(final BlockPos anchor, final Direction facing, final Vec3 position) {
		double startX = anchor.getX() + 0.5D - facing.getStepX();
		double startZ = anchor.getZ() + 0.5D - facing.getStepZ();
		return (position.x - startX) * facing.getStepX() + (position.z - startZ) * facing.getStepZ();
	}

	public static double horizontalDistanceSqr(final Vec3 a, final Vec3 b) {
		double x = a.x - b.x;
		double z = a.z - b.z;
		return x * x + z * z;
	}

	public static Vec3 horizontal(final Direction direction) {
		return new Vec3(direction.getStepX(), 0.0, direction.getStepZ());
	}

	private static double sidingForward(final double t) {
		return cubic(0.0D, SIDING_CONTROL_LENGTH, 1.0D - SIDING_CONTROL_LENGTH, 1.0D, t);
	}

	private static double sidingSide(final double t) {
		return t * t * (3.0D - 2.0D * t);
	}

	private static double sidingForwardDerivative(final double t) {
		return cubicDerivative(0.0D, SIDING_CONTROL_LENGTH, 1.0D - SIDING_CONTROL_LENGTH, 1.0D, t);
	}

	private static double sidingSideDerivative(final double t) {
		return 6.0D * t * (1.0D - t);
	}

	private static double cubic(final double p0, final double p1, final double p2, final double p3, final double t) {
		double inv = 1.0 - t;
		return p0 * inv * inv * inv
			+ p1 * 3.0D * inv * inv * t
			+ p2 * 3.0D * inv * t * t
			+ p3 * t * t * t;
	}

	private static double cubicDerivative(final double p0, final double p1, final double p2, final double p3, final double t) {
		double inv = 1.0 - t;
		return (p1 - p0) * 3.0D * inv * inv
			+ (p2 - p1) * 6.0D * inv * t
			+ (p3 - p2) * 3.0D * t * t;
	}
}
