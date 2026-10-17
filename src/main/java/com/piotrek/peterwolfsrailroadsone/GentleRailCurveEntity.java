package com.piotrek.peterwolfsrailroadsone;

import com.piotrek.minecartchain.MinecartTrainLogic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GentleRailCurveEntity extends Entity {
	private static final int FOOTPRINT_CHECK_INTERVAL_TICKS = 20;
	private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(GentleRailCurveEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RIGHT_TURN = SynchedEntityData.defineId(GentleRailCurveEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> CURVE_SIZE = SynchedEntityData.defineId(GentleRailCurveEntity.class, EntityDataSerializers.INT);
	private BlockPos anchor = BlockPos.ZERO;
	private final Map<UUID, CurveRideState> activeCarts = new HashMap<>();
	private final Map<UUID, ReleasedCartState> releasedCarts = new HashMap<>();
	private final Set<UUID> seenCarts = new HashSet<>();
	private AABB cartGuideArea;

	public GentleRailCurveEntity(final EntityType<? extends GentleRailCurveEntity> type, final Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public void configure(final BlockPos anchor, final Direction facing, final CurveTurn turn, final CurveSize size) {
		this.anchor = anchor;
		this.entityData.set(FACING, facing.get2DDataValue());
		this.entityData.set(RIGHT_TURN, turn == CurveTurn.RIGHT);
		this.entityData.set(CURVE_SIZE, size.id());
		this.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
		AABB placementBounds = CurvePlacementHelper.placementBounds(new CurvePlacementHelper.Placement(anchor, facing, turn, size));
		this.setBoundingBox(placementBounds.inflate(0.05, 0.25, 0.05));
		this.cartGuideArea = placementBounds.inflate(2.75, 0.75, 2.75);
		this.setYRot(facing.toYRot());
	}

	public BlockPos anchor() {
		return this.anchor;
	}

	public Direction facing() {
		return Direction.from2DDataValue(this.entityData.get(FACING));
	}

	public CurveTurn turn() {
		return this.entityData.get(RIGHT_TURN) ? CurveTurn.RIGHT : CurveTurn.LEFT;
	}

	public CurveSize size() {
		return CurveSize.byId(this.entityData.get(CURVE_SIZE));
	}

	public boolean ownsMarker(final BlockPos pos) {
		return CurvePlacementHelper.occupiedPositions(this.anchor, this.facing(), this.turn(), this.size()).contains(pos);
	}

	public boolean hasCompleteMarkerFootprint(final ServerLevel level) {
		for (BlockPos pos : CurvePlacementHelper.occupiedPositions(this.anchor, this.facing(), this.turn(), this.size())) {
			if (!level.hasChunkAt(pos)) {
				return true;
			}
			if (!level.getBlockState(pos).is(ModBlocks.RAIL_CURVE_MARKER)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
			// Water can remove marker blocks without invoking playerWillDestroy. Do not leave
			// an invisible curve entity behind, because it would block rebuilding the curve.
			if (Math.floorMod(this.tickCount + this.anchor.hashCode(), FOOTPRINT_CHECK_INTERVAL_TICKS) == 0
				&& !this.hasCompleteMarkerFootprint(serverLevel)) {
				this.remove(Entity.RemovalReason.KILLED);
				return;
			}
			TrackGuidanceScheduler.queue(serverLevel, this);
		}
	}

	void guideAfterEntityTicks(final ServerLevel level) {
		this.guideNearbyMinecarts(level);
	}

	@Override
	public void onRemoval(final Entity.RemovalReason reason) {
		if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel && reason.shouldDestroy()) {
			CurvePlacementHelper.release(serverLevel, this.anchor, this.facing(), this.turn(), this.size());
		}
		super.onRemoval(reason);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		entityData.define(FACING, Direction.NORTH.get2DDataValue());
		entityData.define(RIGHT_TURN, false);
		entityData.define(CURVE_SIZE, CurveSize.LARGE_3X3.id());
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		int x = input.getIntOr("AnchorX", this.blockPosition().getX());
		int y = input.getIntOr("AnchorY", this.blockPosition().getY());
		int z = input.getIntOr("AnchorZ", this.blockPosition().getZ());
		this.anchor = new BlockPos(x, y, z);
		this.entityData.set(FACING, input.getIntOr("Facing", Direction.NORTH.get2DDataValue()));
		this.entityData.set(RIGHT_TURN, input.getBooleanOr("RightTurn", false));
		this.entityData.set(CURVE_SIZE, input.getIntOr("CurveSize", CurveSize.LARGE_3X3.id()));
		this.configure(this.anchor, this.facing(), this.turn(), this.size());
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		output.putInt("AnchorX", this.anchor.getX());
		output.putInt("AnchorY", this.anchor.getY());
		output.putInt("AnchorZ", this.anchor.getZ());
		output.putInt("Facing", this.facing().get2DDataValue());
		output.putBoolean("RightTurn", this.turn() == CurveTurn.RIGHT);
		output.putInt("CurveSize", this.size().id());
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		if (source.getEntity() != null || source.getDirectEntity() != null) {
			this.remove(Entity.RemovalReason.KILLED);
			return true;
		}
		return false;
	}

	private void guideNearbyMinecarts(final ServerLevel level) {
		this.seenCarts.clear();
		List<AbstractMinecart> nearby = new ArrayList<>();
		for (AbstractMinecart minecart : level.getEntities(EntityTypeTest.forClass(AbstractMinecart.class), this.cartGuideArea, cart -> !cart.isRemoved())) {
			nearby.add(minecart);
			this.seenCarts.add(minecart.getUUID());
			for (AbstractMinecart linked : GuidedConsist.linkedTo(level, minecart)) {
				this.seenCarts.add(linked.getUUID());
			}
		}

		Set<UUID> nearbyIds = new HashSet<>(this.seenCarts);
		List<AbstractMinecart> guidableLeaders = new ArrayList<>();
		for (AbstractMinecart minecart : nearby) {
			if (this.isFollowerOfGuidedConsist(level, minecart, nearbyIds)) {
				continue;
			}
			if (this.prepareRide(minecart)) {
				guidableLeaders.add(minecart);
			}
		}
		this.activeCarts.keySet().removeIf(id -> !this.seenCarts.contains(id));
		this.releasedCarts.keySet().removeIf(id -> !this.seenCarts.contains(id));

		for (AbstractMinecart minecart : guidableLeaders) {
			this.advanceIndependent(minecart);
		}
		for (AbstractMinecart minecart : guidableLeaders) {
			CurveRideState leaderState = this.activeCarts.get(minecart.getUUID());
			if (leaderState == null) {
				continue;
			}

			Map<UUID, Integer> hops = GuidedConsist.hopsFrom(level, minecart);
			AbstractMinecart consistLeader = GuidedConsist.leader(level, GuidedConsist.linkedTo(level, minecart));
			for (AbstractMinecart linked : GuidedConsist.linkedTo(level, minecart)) {
				if (linked.getUUID().equals(minecart.getUUID())) {
					continue;
				}
				if (consistLeader != null && consistLeader.getUUID().equals(linked.getUUID())) {
					continue;
				}
				this.followLeader(linked, minecart, leaderState, Math.max(1, hops.getOrDefault(linked.getUUID(), 1)));
			}
		}
	}

	private boolean isFollowerOfGuidedConsist(
		final ServerLevel level,
		final AbstractMinecart minecart,
		final Set<UUID> nearbyIds
	) {
		AbstractMinecart leader = GuidedConsist.leader(level, GuidedConsist.linkedTo(level, minecart));
		if (leader == null || leader.getUUID().equals(minecart.getUUID())) {
			return false;
		}
		if (this.activeCarts.containsKey(leader.getUUID())) {
			return true;
		}
		if (nearbyIds.contains(leader.getUUID()) && !this.releasedCarts.containsKey(leader.getUUID())) {
			return true;
		}
		return MinecartTrainLogic.isExternallyGuided(leader);
	}

	private boolean prepareRide(final AbstractMinecart minecart) {
		if (minecart.level() instanceof ServerLevel serverLevel
			&& this.isFollowerOfGuidedConsist(serverLevel, minecart, this.seenCarts)) {
			return false;
		}

		double nearestProgress = CurveMath.nearestProgress(this.anchor, this.facing(), this.turn(), this.size(), minecart.position());
		ReleasedCartState releasedCart = this.releasedCarts.get(minecart.getUUID());
		if (releasedCart != null) {
			if (releasedCart.shouldIgnore(minecart, this.facing(), this.turn(), nearestProgress)) {
				return false;
			}
			this.releasedCarts.remove(minecart.getUUID());
		}

		CurveRideState rideState = this.activeCarts.computeIfAbsent(
			minecart.getUUID(),
			id -> CurveMath.horizontalDistanceSqr(minecart.position(), CurveMath.pointOnCurve(this.anchor, this.facing(), this.turn(), this.size(), nearestProgress)) <= captureDistanceSqr(this.size())
				? CurveRideState.start(minecart, this.facing(), this.turn(), nearestProgress)
				: null
		);
		if (rideState == null) {
			return false;
		}

		Vec3 expected = CurveMath.pointExtended(this.anchor, this.facing(), this.turn(), this.size(), rideState.progress);
		Vec3 nearest = CurveMath.pointOnCurve(this.anchor, this.facing(), this.turn(), this.size(), nearestProgress);
		if (
			CurveMath.horizontalDistanceSqr(minecart.position(), expected) > 2.25
				&& CurveMath.horizontalDistanceSqr(minecart.position(), nearest) > 1.35
		) {
			this.activeCarts.remove(minecart.getUUID());
			return false;
		}
		return true;
	}

	private void advanceIndependent(final AbstractMinecart minecart) {
		CurveRideState rideState = this.activeCarts.get(minecart.getUUID());
		if (rideState == null) {
			return;
		}

		double speed = rideState.trackSpeed(minecart.getDeltaMovement().horizontalDistance(), this.size());
		double progressStep = CurveMath.progressStepForSpeed(this.size(), speed);
		this.moveToProgress(minecart, rideState, rideState.progress + progressStep * rideState.direction, speed, rideState);
	}

	private void followLeader(
		final AbstractMinecart minecart,
		final AbstractMinecart leader,
		final CurveRideState leaderState,
		final int hops
	) {
		double next = GuidedConsist.followProgress(
			leaderState.progress,
			leaderState.direction,
			hops,
			CurveMath.arcLength(this.size())
		);
		double minSeparation = MinecartTrainLogic.couplingDistance() * 0.85D;
		for (int i = 0; i < 8; i++) {
			if (CurveMath.horizontalDistanceSqr(
				CurveMath.pointExtended(this.anchor, this.facing(), this.turn(), this.size(), next),
				leader.position()
			) >= minSeparation * minSeparation) {
				break;
			}
			next -= leaderState.direction * 0.08D;
		}

		double speed = Math.max(CurveRideState.MIN_GUIDED_SPEED, leaderState.retainedSpeed);
		CurveRideState followState = CurveRideState.follow(leaderState, next, speed);
		this.activeCarts.put(minecart.getUUID(), followState);
		this.seenCarts.add(minecart.getUUID());
		this.moveToProgress(minecart, followState, next, speed, leaderState);
	}

	private void moveToProgress(
		final AbstractMinecart minecart,
		final CurveRideState rideState,
		final double nextProgress,
		final double speed,
		final CurveRideState yawSource
	) {
		boolean pastForwardExit = yawSource.direction > 0.0D && nextProgress >= 1.0D;
		boolean pastReverseExit = yawSource.direction < 0.0D && nextProgress <= 0.0D;
		if (pastForwardExit || pastReverseExit) {
			double exitProgress = pastForwardExit ? 1.0 : 0.0;
			Vec3 exitTangent = CurveMath.tangent(this.facing(), this.turn(), exitProgress).scale(yawSource.direction);
			Vec3 exitPos = this.handoffPosition(
				CurveMath.pointOnCurve(this.anchor, this.facing(), this.turn(), this.size(), exitProgress),
				exitTangent
			);
			this.applyCartPose(minecart, exitPos, exitTangent.scale(speed), yawSource, exitProgress);
			this.activeCarts.remove(minecart.getUUID());
			this.releasedCarts.put(minecart.getUUID(), new ReleasedCartState(yawSource.direction));
			return;
		}

		rideState.progress = nextProgress;
		Vec3 target = CurveMath.pointExtended(this.anchor, this.facing(), this.turn(), this.size(), nextProgress);
		Vec3 nextTangent = CurveMath.tangentExtended(this.facing(), this.turn(), nextProgress).scale(yawSource.direction);
		double followingProgress = nextProgress + CurveMath.progressStepForSpeed(this.size(), speed) * yawSource.direction;
		Vec3 followingTarget = CurveMath.pointExtended(
			this.anchor, this.facing(), this.turn(), this.size(), followingProgress
		);
		Vec3 pathMovement = alignedPathMovement(
			followingTarget.subtract(target).horizontal(),
			nextTangent,
			Math.max(CurveRideState.MIN_GUIDED_SPEED, speed)
		);
		this.applyCartPose(minecart, target, pathMovement, yawSource, nextProgress);
	}

	private void applyCartPose(
		final AbstractMinecart minecart,
		final Vec3 position,
		final Vec3 pathMovement,
		final CurveRideState rideState,
		final double progress
	) {
		float yaw = CurveMath.yawAlongCurve(
			rideState.entryYaw, this.turn(), rideState.entryProgress, progress
		);
		GuidedMinecartPose.apply(
			minecart,
			position,
			pathMovement,
			GuidedMinecartPose.flippedMatchingYaw(yaw, pathMovement, rideState.flippedPose),
			yaw
		);
	}

	private static Vec3 alignedPathMovement(final Vec3 pathMovement, final Vec3 travelTangent, final double speed) {
		if (pathMovement.lengthSqr() <= 1.0E-8D) {
			return travelTangent.scale(speed);
		}
		return pathMovement.dot(travelTangent) < 0.0D ? pathMovement.reverse() : pathMovement;
	}

	private Vec3 handoffPosition(final Vec3 exitPos, final Vec3 exitTangent) {
		Vec3 direction = exitTangent.horizontal();
		if (direction.lengthSqr() <= 1.0E-8D) {
			return exitPos;
		}

		Vec3 unit = direction.normalize();
		Vec3 candidate = exitPos.add(unit.scale(0.55D));
		BlockPos railPos = BlockPos.containing(candidate.x, this.anchor.getY(), candidate.z);
		if (this.level() instanceof ServerLevel serverLevel && GuidedTrack.isPresent(serverLevel, railPos)) {
			return new Vec3(railPos.getX() + 0.5D, this.anchor.getY() + 0.08D, railPos.getZ() + 0.5D);
		}
		return candidate;
	}

	private static double exitNudge(final double speed) {
		return Mth.clamp(speed * 0.5, 0.35, 0.55);
	}

	private static double captureDistanceSqr(final CurveSize size) {
		double distance = size == CurveSize.SMALL_2X2 ? 0.48 : 0.55;
		return distance * distance;
	}

	private static final class CurveRideState {
		private static final double MIN_GUIDED_SPEED = 0.12;
		private double progress;
		private final double direction;
		private final boolean flippedPose;
		private final float entryYaw;
		private final double entryProgress;
		private double retainedSpeed;

		private CurveRideState(
			final double progress,
			final double direction,
			final boolean flippedPose,
			final double entrySpeed,
			final float entryYaw
		) {
			this(progress, direction, flippedPose, entrySpeed, entryYaw, progress);
		}

		private CurveRideState(
			final double progress,
			final double direction,
			final boolean flippedPose,
			final double entrySpeed,
			final float entryYaw,
			final double entryProgress
		) {
			this.progress = progress;
			this.direction = direction;
			this.flippedPose = flippedPose;
			this.entryYaw = entryYaw;
			this.entryProgress = entryProgress;
			this.retainedSpeed = Math.max(MIN_GUIDED_SPEED, entrySpeed);
		}

		private static CurveRideState follow(final CurveRideState leader, final double progress, final double speed) {
			return new CurveRideState(
				progress,
				leader.direction,
				leader.flippedPose,
				speed,
				leader.entryYaw,
				leader.entryProgress
			);
		}

		private static CurveRideState start(
			final AbstractMinecart minecart,
			final Direction facing,
			final CurveTurn turn,
			final double progress
		) {
			Vec3 tangent = CurveMath.tangent(facing, turn, progress);
			double entrySpeed = minecart.getDeltaMovement().horizontalDistance();
			double dot = minecart.getDeltaMovement().dot(tangent);
			double direction = Math.abs(dot) > 0.02 ? Math.signum(dot) : 1.0D;
			return new CurveRideState(
				progress,
				direction,
				minecart.isFlipped(),
				entrySpeed,
				minecart.getYRot()
			);
		}

		private double trackSpeed(final double currentSpeed, final CurveSize size) {
			double retention = size == CurveSize.LARGE_3X3 ? 0.999 : 0.9995;
			this.retainedSpeed = Math.max(Math.max(MIN_GUIDED_SPEED, currentSpeed), this.retainedSpeed * retention);
			return this.retainedSpeed;
		}
	}

	private static final class ReleasedCartState {
		private final double direction;

		private ReleasedCartState(final double direction) {
			this.direction = direction;
		}

		private boolean shouldIgnore(final AbstractMinecart minecart, final Direction facing, final CurveTurn turn, final double progress) {
			if (!GuidedTrack.isPresent(minecart.level(), minecart.blockPosition())) {
				return false;
			}

			double exitProgress = this.direction > 0.0 ? 1.0 : 0.0;
			boolean stillAtExit = this.direction > 0.0 ? progress > 0.72 : progress < 0.28;
			if (!stillAtExit) {
				return false;
			}

			Vec3 exitTangent = CurveMath.tangent(facing, turn, exitProgress).scale(this.direction);
			return minecart.getDeltaMovement().dot(exitTangent) >= -0.03;
		}
	}
}
