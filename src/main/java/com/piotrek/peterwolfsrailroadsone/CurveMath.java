package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class CurveMath {
	private CurveMath() {
	}

	public static Vec3 pointOnCurve(final BlockPos anchor, final Direction facing, final CurveTurn turn, final CurveSize size, final double progress) {
		double t = Mth.clamp(progress, 0.0, 1.0);
		double radius = size.radius();
		double centerX = curveCenterX(anchor, facing, turn, radius);
		double centerZ = curveCenterZ(anchor, facing, turn, radius);
		double angle = startAngle(facing, turn) + sweep(turn) * t;
		return new Vec3(
			centerX + Math.cos(angle) * radius,
			anchor.getY() + 0.08D,
			centerZ + Math.sin(angle) * radius
		);
	}

	public static Vec3 tangent(final Direction facing, final CurveTurn turn, final double progress) {
		double t = Mth.clamp(progress, 0.0, 1.0);
		double sign = Math.signum(sweep(turn));
		double angle = startAngle(facing, turn) + sweep(turn) * t;
		return new Vec3(-Math.sin(angle) * sign, 0.0, Math.cos(angle) * sign);
	}

	public static double nearestProgress(final BlockPos anchor, final Direction facing, final CurveTurn turn, final CurveSize size, final Vec3 position) {
		return Mth.clamp(nearestProgressExtended(anchor, facing, turn, size, position), 0.0, 1.0);
	}

	public static double nearestProgressExtended(
		final BlockPos anchor,
		final Direction facing,
		final CurveTurn turn,
		final CurveSize size,
		final Vec3 position
	) {
		double radius = size.radius();
		double centerX = curveCenterX(anchor, facing, turn, radius);
		double centerZ = curveCenterZ(anchor, facing, turn, radius);
		double angle = Math.atan2(position.z - centerZ, position.x - centerX);
		double delta = normalizeSigned(angle - startAngle(facing, turn));
		double progress = delta / sweep(turn);
		double length = arcLength(size);

		Vec3 start = pointOnCurve(anchor, facing, turn, size, 0.0D);
		Vec3 entry = tangent(facing, turn, 0.0D);
		double alongStart = (position.x - start.x) * entry.x + (position.z - start.z) * entry.z;
		if (alongStart < 0.0D) {
			return alongStart / length;
		}

		Vec3 end = pointOnCurve(anchor, facing, turn, size, 1.0D);
		Vec3 exit = tangent(facing, turn, 1.0D);
		double alongExit = (position.x - end.x) * exit.x + (position.z - end.z) * exit.z;
		if (alongExit > 0.0D) {
			return 1.0D + alongExit / length;
		}
		return Mth.clamp(progress, 0.0, 1.0);
	}

	public static Vec3 pointExtended(
		final BlockPos anchor,
		final Direction facing,
		final CurveTurn turn,
		final CurveSize size,
		final double progress
	) {
		double length = arcLength(size);
		if (progress >= 0.0D && progress <= 1.0D) {
			return pointOnCurve(anchor, facing, turn, size, progress);
		}
		if (progress < 0.0D) {
			return pointOnCurve(anchor, facing, turn, size, 0.0D)
				.add(tangent(facing, turn, 0.0D).scale(progress * length));
		}
		return pointOnCurve(anchor, facing, turn, size, 1.0D)
			.add(tangent(facing, turn, 1.0D).scale((progress - 1.0D) * length));
	}

	public static Vec3 tangentExtended(final Direction facing, final CurveTurn turn, final double progress) {
		if (progress <= 0.0D) {
			return tangent(facing, turn, 0.0D);
		}
		if (progress >= 1.0D) {
			return tangent(facing, turn, 1.0D);
		}
		return tangent(facing, turn, progress);
	}

	public static double arcLength(final CurveSize size) {
		return size.radius() * Math.PI / 2.0;
	}

	public static double progressStepForSpeed(final CurveSize size, final double horizontalSpeed) {
		double length = arcLength(size);
		return Mth.clamp(Math.max(horizontalSpeed, 0.12) / length, 0.025, size == CurveSize.SMALL_2X2 ? 0.24 : 0.18);
	}

	public static float yawAlongCurve(
		final float entryYaw,
		final CurveTurn turn,
		final double entryProgress,
		final double progress
	) {
		return entryYaw + (float) (Mth.RAD_TO_DEG * sweep(turn) * (progress - entryProgress));
	}

	public static double horizontalDistanceSqr(final Vec3 a, final Vec3 b) {
		double x = a.x - b.x;
		double z = a.z - b.z;
		return x * x + z * z;
	}

	private static double curveCenterX(final BlockPos anchor, final Direction facing, final CurveTurn turn, final double radius) {
		Direction exit = CurvePlacementHelper.exitDirection(facing, turn);
		return anchor.getX() + 0.5D + (facing.getOpposite().getStepX() + exit.getStepX()) * radius;
	}

	private static double curveCenterZ(final BlockPos anchor, final Direction facing, final CurveTurn turn, final double radius) {
		Direction exit = CurvePlacementHelper.exitDirection(facing, turn);
		return anchor.getZ() + 0.5D + (facing.getOpposite().getStepZ() + exit.getStepZ()) * radius;
	}

	private static double startAngle(final Direction facing, final CurveTurn turn) {
		Direction exit = CurvePlacementHelper.exitDirection(facing, turn);
		return Math.atan2(-exit.getStepZ(), -exit.getStepX());
	}

	private static double sweep(final CurveTurn turn) {
		return turn == CurveTurn.RIGHT ? Math.PI / 2.0 : -Math.PI / 2.0;
	}

	private static double normalizeSigned(double angle) {
		while (angle <= -Math.PI) {
			angle += Math.PI * 2.0;
		}
		while (angle > Math.PI) {
			angle -= Math.PI * 2.0;
		}
		return angle;
	}
}
