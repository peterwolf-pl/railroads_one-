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
		Vec3 start = mainStart(anchor, facing);
		if (route == ParallelSidingSwitchRoute.MAIN) {
			return start.add(horizontal(facing).scale(t));
		}

		return cubic(
			start,
			start.add(horizontal(facing).scale(SIDING_CONTROL_LENGTH)),
			sidingEnd(anchor, facing, variant).add(horizontal(facing).scale(-SIDING_CONTROL_LENGTH)),
			sidingEnd(anchor, facing, variant),
			t
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

		Vec3 start = mainStart(anchor, facing);
		Vec3 controlA = start.add(horizontal(facing).scale(SIDING_CONTROL_LENGTH));
		Vec3 end = sidingEnd(anchor, facing, variant);
		Vec3 controlB = end.add(horizontal(facing).scale(-SIDING_CONTROL_LENGTH));
		return cubicDerivative(start, controlA, controlB, end, t).normalize();
	}

	public static double nearestProgress(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final ParallelSidingSwitchRoute route,
		final Vec3 position
	) {
		if (route == ParallelSidingSwitchRoute.MAIN) {
			Vec3 start = mainStart(anchor, facing);
			return Mth.clamp(position.subtract(start).dot(horizontal(facing)), 0.0, 1.0);
		}

		double bestProgress = 0.0;
		double bestDistance = Double.MAX_VALUE;
		for (int step = 0; step <= 32; step++) {
			double progress = step / 32.0;
			double distance = horizontalDistanceSqr(position, point(anchor, facing, variant, route, progress));
			if (distance < bestDistance) {
				bestDistance = distance;
				bestProgress = progress;
			}
		}
		return bestProgress;
	}

	public static double progressStepForSpeed(final ParallelSidingSwitchRoute route, final double horizontalSpeed) {
		double routeLength = route == ParallelSidingSwitchRoute.MAIN ? 1.0 : 1.55;
		double maxStep = route == ParallelSidingSwitchRoute.MAIN ? 0.32 : 0.22;
		return Mth.clamp(Math.max(horizontalSpeed, 0.12) / routeLength, 0.04, maxStep);
	}

	public static double sideOffset(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final Vec3 position
	) {
		return position.subtract(mainStart(anchor, facing)).dot(horizontal(variant.sideDirection(facing)));
	}

	public static double forwardOffset(final BlockPos anchor, final Direction facing, final Vec3 position) {
		return position.subtract(mainStart(anchor, facing)).dot(horizontal(facing));
	}

	public static double horizontalDistanceSqr(final Vec3 a, final Vec3 b) {
		double x = a.x - b.x;
		double z = a.z - b.z;
		return x * x + z * z;
	}

	public static Vec3 horizontal(final Direction direction) {
		return new Vec3(direction.getStepX(), 0.0, direction.getStepZ());
	}

	private static Vec3 mainStart(final BlockPos anchor, final Direction facing) {
		return center(anchor.relative(facing.getOpposite()));
	}

	private static Vec3 sidingEnd(final BlockPos anchor, final Direction facing, final ParallelSidingSwitchVariant variant) {
		return center(anchor.relative(variant.sideDirection(facing)));
	}

	private static Vec3 center(final BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + ROUTE_Y_OFFSET, pos.getZ() + 0.5);
	}

	private static Vec3 cubic(final Vec3 p0, final Vec3 p1, final Vec3 p2, final Vec3 p3, final double t) {
		double inv = 1.0 - t;
		return p0.scale(inv * inv * inv)
			.add(p1.scale(3.0 * inv * inv * t))
			.add(p2.scale(3.0 * inv * t * t))
			.add(p3.scale(t * t * t));
	}

	private static Vec3 cubicDerivative(final Vec3 p0, final Vec3 p1, final Vec3 p2, final Vec3 p3, final double t) {
		double inv = 1.0 - t;
		return p1.subtract(p0).scale(3.0 * inv * inv)
			.add(p2.subtract(p1).scale(6.0 * inv * t))
			.add(p3.subtract(p2).scale(3.0 * t * t));
	}
}
