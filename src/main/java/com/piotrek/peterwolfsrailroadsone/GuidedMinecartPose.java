package com.piotrek.peterwolfsrailroadsone;

import com.piotrek.minecartchain.MinecartTrainLogic;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;

final class GuidedMinecartPose {
	private static final double MIN_MOVEMENT_SQR = 1.0E-8D;
	private static final double MAX_GUIDED_STEP = 0.85D;

	private GuidedMinecartPose() {
	}

	static boolean flippedMatchingYaw(final float yaw, final Vec3 pathMovement, final boolean fallback) {
		if (pathMovement.horizontalDistanceSqr() <= MIN_MOVEMENT_SQR) {
			return fallback;
		}

		float regularDifference = Mth.degreesDifferenceAbs(yaw, cartPoseYaw(pathMovement, false));
		float flippedDifference = Mth.degreesDifferenceAbs(yaw, cartPoseYaw(pathMovement, true));
		if (Math.abs(regularDifference - flippedDifference) <= 0.5F) {
			return fallback;
		}
		return flippedDifference < regularDifference;
	}

	static float apply(
		final AbstractMinecart minecart,
		final Vec3 position,
		final Vec3 pathMovement,
		final boolean flippedPose,
		final float yaw
	) {
		Vec3 movement = pathMovement.horizontal();
		if (movement.lengthSqr() <= MIN_MOVEMENT_SQR) {
			movement = minecart.getDeltaMovement().horizontal();
		}

		Vec3 target = cappedStep(minecart.position(), position);
		minecart.setPos(target.x, target.y, target.z);
		if (movement.lengthSqr() <= MIN_MOVEMENT_SQR) {
			MinecartTrainLogic.markExternalTrackGuide(minecart, new Vec3(1.0E-4D, 0.0D, 0.0D));
			return yaw;
		}

		MinecartTrainLogic.markExternalTrackGuide(minecart, movement);
		minecart.setDeltaMovement(movement);
		minecart.setFlipped(flippedPose);
		minecart.setYRot(yaw);
		minecart.setXRot(0.0F);
		minecart.yRotO = yaw;
		minecart.xRotO = 0.0F;

		if (minecart.getBehavior() instanceof NewMinecartBehavior behavior) {
			float weight = (float) Math.max(movement.horizontalDistance(), 1.0E-4D);
			behavior.lerpSteps.clear();
			behavior.lerpSteps.add(new NewMinecartBehavior.MinecartStep(target, movement, yaw, 0.0F, weight));
		}
		return yaw;
	}

	private static Vec3 cappedStep(final Vec3 from, final Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= MAX_GUIDED_STEP || distance <= 1.0E-8D) {
			return to;
		}

		double scale = MAX_GUIDED_STEP / distance;
		return new Vec3(from.x + dx * scale, to.y, from.z + dz * scale);
	}

	static float yawFollowingHeading(final float entryYaw, final Vec3 entryTravel, final Vec3 currentTravel) {
		if (entryTravel.horizontalDistanceSqr() <= MIN_MOVEMENT_SQR || currentTravel.horizontalDistanceSqr() <= MIN_MOVEMENT_SQR) {
			return entryYaw;
		}

		float delta = Mth.degreesDifference(heading(entryTravel), heading(currentTravel));
		if (Math.abs(delta) > 90.0F) {
			delta -= Math.copySign(180.0F, delta);
		}
		return entryYaw + delta;
	}

	private static float heading(final Vec3 pathMovement) {
		return (float) (Mth.atan2(pathMovement.z, pathMovement.x) * Mth.RAD_TO_DEG);
	}

	private static float cartPoseYaw(final Vec3 pathMovement, final boolean flippedPose) {
		return 180.0F - heading(pathMovement) + (flippedPose ? 180.0F : 0.0F);
	}
}
