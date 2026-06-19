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
		Vec3 center = curveCenter(anchor, facing, turn, size);
		double angle = startAngle(facing, turn) + sweep(turn) * t;
		return center.add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
	}

	public static Vec3 tangent(final Direction facing, final CurveTurn turn, final double progress) {
		double t = Mth.clamp(progress, 0.0, 1.0);
		double sign = Math.signum(sweep(turn));
		double angle = startAngle(facing, turn) + sweep(turn) * t;
		return new Vec3(-Math.sin(angle) * sign, 0.0, Math.cos(angle) * sign).normalize();
	}

	public static double nearestProgress(final BlockPos anchor, final Direction facing, final CurveTurn turn, final CurveSize size, final Vec3 position) {
		Vec3 center = curveCenter(anchor, facing, turn, size);
		double angle = Math.atan2(position.z - center.z, position.x - center.x);
		double delta = normalizeSigned(angle - startAngle(facing, turn));
		double progress = delta / sweep(turn);
		return Mth.clamp(progress, 0.0, 1.0);
	}

	public static double progressStepForSpeed(final CurveSize size, final double horizontalSpeed) {
		double arcLength = size.radius() * Math.PI / 2.0;
		return Mth.clamp(Math.max(horizontalSpeed, 0.12) / arcLength, 0.025, size == CurveSize.SMALL_2X2 ? 0.24 : 0.18);
	}

	public static double horizontalDistanceSqr(final Vec3 a, final Vec3 b) {
		double x = a.x - b.x;
		double z = a.z - b.z;
		return x * x + z * z;
	}

	private static Vec3 curveCenter(final BlockPos anchor, final Direction facing, final CurveTurn turn, final CurveSize size) {
		Vec3 back = horizontal(facing.getOpposite());
		Vec3 exit = horizontal(CurvePlacementHelper.exitDirection(facing, turn));
		return Vec3.atBottomCenterOf(anchor).add(back.scale(size.radius())).add(exit.scale(size.radius()));
	}

	private static double startAngle(final Direction facing, final CurveTurn turn) {
		Vec3 exit = horizontal(CurvePlacementHelper.exitDirection(facing, turn));
		Vec3 startVector = exit.scale(-1.0);
		return Math.atan2(startVector.z, startVector.x);
	}

	private static double sweep(final CurveTurn turn) {
		return turn == CurveTurn.RIGHT ? Math.PI / 2.0 : -Math.PI / 2.0;
	}

	private static Vec3 horizontal(final Direction direction) {
		return new Vec3(direction.getStepX(), 0.0, direction.getStepZ());
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
