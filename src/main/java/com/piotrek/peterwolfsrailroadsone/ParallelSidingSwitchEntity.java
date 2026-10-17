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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ParallelSidingSwitchEntity extends Entity {
	private static final int MAINTENANCE_INTERVAL_TICKS = 20;
	private static final int ROUTE_LOCK_RELEASE_GRACE_TICKS = 40;
	private static final double CAPTURE_MIN_PROGRESS = -0.25D;
	private static final double CAPTURE_MAX_PROGRESS = 1.2D;
	private static final double STRAGGLER_MIN_PROGRESS = -0.5D;
	private static final double STRAGGLER_MAX_PROGRESS = 2.5D;
	private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(ParallelSidingSwitchEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RIGHT_VARIANT = SynchedEntityData.defineId(ParallelSidingSwitchEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> POWERED = SynchedEntityData.defineId(ParallelSidingSwitchEntity.class, EntityDataSerializers.BOOLEAN);
	private BlockPos anchor = BlockPos.ZERO;
	private final Map<UUID, SwitchRideState> activeCarts = new HashMap<>();
	private final Map<UUID, ReleasedSwitchState> releasedCarts = new HashMap<>();
	private final Map<UUID, SwitchRouteLock> routeLocks = new HashMap<>();
	private final Set<UUID> seenCarts = new HashSet<>();
	private AABB cartGuideArea;

	public ParallelSidingSwitchEntity(final EntityType<? extends ParallelSidingSwitchEntity> type, final Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public void configure(
		final BlockPos anchor,
		final Direction facing,
		final ParallelSidingSwitchVariant variant,
		final boolean powered
	) {
		this.anchor = anchor;
		this.entityData.set(FACING, facing.get2DDataValue());
		this.entityData.set(RIGHT_VARIANT, variant.isRight());
		this.entityData.set(POWERED, powered);
		this.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
		AABB placementBounds = ParallelSidingSwitchPlacementHelper.placementBounds(this.placement());
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

	public ParallelSidingSwitchVariant variant() {
		return this.entityData.get(RIGHT_VARIANT) ? ParallelSidingSwitchVariant.RIGHT : ParallelSidingSwitchVariant.LEFT;
	}

	public boolean powered() {
		return this.entityData.get(POWERED);
	}

	public boolean ownsMarker(final BlockPos pos) {
		return ParallelSidingSwitchPlacementHelper.occupiedPositions(this.anchor, this.facing(), this.variant()).contains(pos);
	}

	public void refreshPowered(final ServerLevel level) {
		ParallelSidingSwitchPlacementHelper.Placement placement = this.placement();
		boolean powered = ParallelSidingSwitchPlacementHelper.isPowered(level, placement);
		if (this.powered() != powered) {
			this.entityData.set(POWERED, powered);
		}
		ParallelSidingSwitchPlacementHelper.syncPoweredMarkers(level, placement, powered);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
			if (Math.floorMod(this.tickCount + this.anchor.hashCode(), MAINTENANCE_INTERVAL_TICKS) == 0) {
				this.refreshPowered(serverLevel);
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
			ParallelSidingSwitchPlacementHelper.release(serverLevel, this.anchor, this.facing(), this.variant());
		}
		super.onRemoval(reason);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		entityData.define(FACING, Direction.NORTH.get2DDataValue());
		entityData.define(RIGHT_VARIANT, false);
		entityData.define(POWERED, false);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		int x = input.getIntOr("AnchorX", this.blockPosition().getX());
		int y = input.getIntOr("AnchorY", this.blockPosition().getY());
		int z = input.getIntOr("AnchorZ", this.blockPosition().getZ());
		Direction facing = Direction.from2DDataValue(input.getIntOr("Facing", Direction.NORTH.get2DDataValue()));
		ParallelSidingSwitchVariant variant = input.getBooleanOr("RightVariant", false)
			? ParallelSidingSwitchVariant.RIGHT
			: ParallelSidingSwitchVariant.LEFT;
		this.configure(new BlockPos(x, y, z), facing, variant, input.getBooleanOr("Powered", false));
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		output.putInt("AnchorX", this.anchor.getX());
		output.putInt("AnchorY", this.anchor.getY());
		output.putInt("AnchorZ", this.anchor.getZ());
		output.putInt("Facing", this.facing().get2DDataValue());
		output.putBoolean("RightVariant", this.variant().isRight());
		output.putBoolean("Powered", this.powered());
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
		long gameTime = level.getGameTime();
		this.seenCarts.clear();
		List<AbstractMinecart> nearby = new ArrayList<>();
		for (AbstractMinecart minecart : level.getEntities(EntityTypeTest.forClass(AbstractMinecart.class), this.cartGuideArea, cart -> !cart.isRemoved())) {
			nearby.add(minecart);
			this.seenCarts.add(minecart.getUUID());
			for (AbstractMinecart linked : GuidedConsist.linkedTo(level, minecart)) {
				this.seenCarts.add(linked.getUUID());
			}
		}

		List<AbstractMinecart> guidableLeaders = new ArrayList<>();
		for (AbstractMinecart minecart : nearby) {
			if (this.isFollowerOfGuidedConsist(level, minecart)) {
				continue;
			}
			if (this.prepareRide(level, minecart, gameTime)) {
				guidableLeaders.add(minecart);
			}
		}
		this.activeCarts.keySet().removeIf(id -> !this.seenCarts.contains(id));
		this.releasedCarts.keySet().removeIf(id -> !this.seenCarts.contains(id));
		this.routeLocks.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenGameTime > ROUTE_LOCK_RELEASE_GRACE_TICKS);

		Map<UUID, SwitchRideState> followSources = new HashMap<>();
		for (AbstractMinecart minecart : guidableLeaders) {
			SwitchRideState before = this.activeCarts.get(minecart.getUUID());
			double next = this.advanceIndependent(minecart);
			SwitchRideState after = this.activeCarts.get(minecart.getUUID());
			if (after != null) {
				followSources.put(minecart.getUUID(), after);
			} else if (before != null && !Double.isNaN(next)) {
				followSources.put(minecart.getUUID(), SwitchRideState.follow(before, next, before.retainedSpeed));
			}
		}
		for (AbstractMinecart minecart : guidableLeaders) {
			SwitchRideState leaderState = followSources.get(minecart.getUUID());
			if (leaderState == null) {
				continue;
			}
			this.followConsist(level, minecart, leaderState);
		}
		this.guideStragglingFollowers(level, nearby, gameTime);
	}

	double guidancePriority(final ServerLevel level) {
		if (this.cartGuideArea == null) {
			return Double.POSITIVE_INFINITY;
		}

		double best = Double.POSITIVE_INFINITY;
		for (AbstractMinecart minecart : level.getEntities(
			EntityTypeTest.forClass(AbstractMinecart.class),
			this.cartGuideArea,
			cart -> !cart.isRemoved()
		)) {
			AbstractMinecart leader = GuidedConsist.leader(level, GuidedConsist.linkedTo(level, minecart));
			if (leader == null || !leader.getUUID().equals(minecart.getUUID())) {
				continue;
			}
			double priority = this.routePriority(minecart);
			if (priority < best) {
				best = priority;
			}
		}
		return best;
	}

	private double routePriority(final AbstractMinecart minecart) {
		double best = Double.POSITIVE_INFINITY;
		for (ParallelSidingSwitchRoute route : ParallelSidingSwitchRoute.values()) {
			double progress = this.nearestProgressExtended(route, minecart);
			if (progress < -0.1D || progress > 1.1D) {
				continue;
			}
			double distance = ParallelSidingSwitchMath.horizontalDistanceSqr(
				minecart.position(),
				this.pointExtended(route, progress)
			);
			if (distance < best) {
				best = distance;
			}
		}
		return best;
	}

	private boolean isFollowerOfGuidedConsist(final ServerLevel level, final AbstractMinecart minecart) {
		AbstractMinecart leader = GuidedConsist.leader(level, GuidedConsist.linkedTo(level, minecart));
		return leader != null && !leader.getUUID().equals(minecart.getUUID());
	}

	private boolean prepareRide(final ServerLevel level, final AbstractMinecart minecart, final long gameTime) {
		if (this.isFollowerOfGuidedConsist(level, minecart) || GuidedThisTick.contains(minecart)) {
			return false;
		}

		AbstractMinecart consistLeader = GuidedConsist.leader(level, GuidedConsist.linkedTo(level, minecart));
		if (
			consistLeader != null
				&& !consistLeader.getUUID().equals(minecart.getUUID())
				&& GuidedThisTick.contains(consistLeader)
		) {
			return false;
		}

		if (!this.isInsideOwnedLanes(minecart.position())) {
			this.activeCarts.remove(minecart.getUUID());
			this.releasedCarts.remove(minecart.getUUID());
			return false;
		}

		ReleasedSwitchState releasedCart = this.releasedCarts.get(minecart.getUUID());
		if (releasedCart != null) {
			this.touchRouteLock(releasedCart.trainKey, releasedCart.route, releasedCart.direction, gameTime);
			double releasedProgress = this.nearestProgress(releasedCart.route, minecart);
			if (releasedCart.shouldIgnore(this, minecart, releasedProgress)) {
				return false;
			}
			this.releasedCarts.remove(minecart.getUUID());
		}

		SwitchRideState rideState = this.activeCarts.get(minecart.getUUID());
		if (rideState == null) {
			UUID trainKey = MinecartTrainLogic.connectedTrainKey(level, minecart);
			ParallelSidingSwitchRoute route = this.lockedRouteForNewCart(minecart, trainKey, gameTime);
			double extendedProgress = this.nearestProgressExtended(route, minecart);
			if (extendedProgress < CAPTURE_MIN_PROGRESS || extendedProgress > CAPTURE_MAX_PROGRESS) {
				return false;
			}
			Vec3 nearest = this.pointExtended(route, extendedProgress);
			if (ParallelSidingSwitchMath.horizontalDistanceSqr(minecart.position(), nearest) > 1.45) {
				return false;
			}
			rideState = SwitchRideState.start(route, trainKey, minecart, this, extendedProgress);
			this.activeCarts.put(minecart.getUUID(), rideState);
		}
		this.touchRouteLock(rideState.trainKey, rideState.route, rideState.direction, gameTime);

		double nearestProgress = this.nearestProgress(rideState.route, minecart);
		Vec3 expected = this.pointExtended(rideState.route, rideState.progress);
		Vec3 nearest = this.point(rideState.route, nearestProgress);
		if (
			ParallelSidingSwitchMath.horizontalDistanceSqr(minecart.position(), expected) > 2.25
				&& ParallelSidingSwitchMath.horizontalDistanceSqr(minecart.position(), nearest) > 1.35
		) {
			this.activeCarts.remove(minecart.getUUID());
			return false;
		}
		return true;
	}

	private double advanceIndependent(final AbstractMinecart minecart) {
		SwitchRideState rideState = this.activeCarts.get(minecart.getUUID());
		if (rideState == null) {
			return Double.NaN;
		}

		double speed = rideState.trackSpeed(minecart.getDeltaMovement().horizontalDistance());
		double progressStep = ParallelSidingSwitchMath.progressStepForSpeed(rideState.route, speed);
		double next = rideState.progress + progressStep * rideState.direction;
		this.moveToProgress(minecart, rideState, next, speed, rideState);
		return next;
	}

	private void followConsist(
		final ServerLevel level,
		final AbstractMinecart leader,
		final SwitchRideState leaderState
	) {
		Map<UUID, Integer> hops = GuidedConsist.hopsFrom(level, leader);
		AbstractMinecart consistLeader = GuidedConsist.leader(level, GuidedConsist.linkedTo(level, leader));
		for (AbstractMinecart linked : GuidedConsist.linkedTo(level, leader)) {
			if (linked.getUUID().equals(leader.getUUID())) {
				continue;
			}
			if (consistLeader != null && consistLeader.getUUID().equals(linked.getUUID())) {
				continue;
			}
			this.followLeader(linked, leader, leaderState, Math.max(1, hops.getOrDefault(linked.getUUID(), 1)));
		}
	}

	private void guideStragglingFollowers(
		final ServerLevel level,
		final List<AbstractMinecart> nearby,
		final long gameTime
	) {
		Set<UUID> handledLeaders = new HashSet<>();
		for (AbstractMinecart minecart : nearby) {
			if (GuidedThisTick.contains(minecart) || !this.isInsideOwnedLanes(minecart.position())) {
				continue;
			}

			AbstractMinecart leader = GuidedConsist.leader(level, GuidedConsist.linkedTo(level, minecart));
			if (leader == null || leader.getUUID().equals(minecart.getUUID()) || !handledLeaders.add(leader.getUUID())) {
				continue;
			}
			if (this.activeCarts.containsKey(leader.getUUID()) || GuidedThisTick.contains(leader)) {
				continue;
			}

			UUID trainKey = MinecartTrainLogic.connectedTrainKey(level, leader);
			ParallelSidingSwitchRoute route = this.routeForStraggler(minecart, trainKey, gameTime);
			double leaderProgress = this.nearestProgressExtended(route, leader);
			if (leaderProgress < STRAGGLER_MIN_PROGRESS || leaderProgress > STRAGGLER_MAX_PROGRESS) {
				continue;
			}
			if (ParallelSidingSwitchMath.horizontalDistanceSqr(leader.position(), this.pointExtended(route, leaderProgress)) > 2.25) {
				continue;
			}

			SwitchRideState leaderState = SwitchRideState.start(route, trainKey, leader, this, leaderProgress);
			this.touchRouteLock(trainKey, route, leaderState.direction, gameTime);
			this.followConsist(level, leader, leaderState);
		}
	}

	private ParallelSidingSwitchRoute routeForStraggler(
		final AbstractMinecart follower,
		final UUID trainKey,
		final long gameTime
	) {
		SwitchRideState existing = this.activeCarts.get(follower.getUUID());
		if (existing != null) {
			return existing.route;
		}
		SwitchRouteLock routeLock = this.routeLocks.get(trainKey);
		if (routeLock != null && gameTime - routeLock.lastSeenGameTime <= ROUTE_LOCK_RELEASE_GRACE_TICKS) {
			return routeLock.route;
		}
		return this.routeForNewCart(follower);
	}

	private void followLeader(
		final AbstractMinecart minecart,
		final AbstractMinecart leader,
		final SwitchRideState leaderState,
		final int hops
	) {
		if (GuidedThisTick.contains(minecart)) {
			return;
		}
		double routeLength = ParallelSidingSwitchMath.routeLength(leaderState.route);
		double next = GuidedConsist.followProgress(leaderState.progress, leaderState.direction, hops, routeLength);
		double minSeparation = MinecartTrainLogic.couplingDistance() * 0.85D;
		for (int i = 0; i < 8; i++) {
			if (ParallelSidingSwitchMath.horizontalDistanceSqr(
				this.pointExtended(leaderState.route, next),
				leader.position()
			) >= minSeparation * minSeparation) {
				break;
			}
			next -= leaderState.direction * 0.08D;
		}

		double speed = Math.max(SwitchRideState.MIN_GUIDED_SPEED, leaderState.retainedSpeed);
		SwitchRideState followState = SwitchRideState.follow(leaderState, next, speed);
		this.activeCarts.put(minecart.getUUID(), followState);
		this.seenCarts.add(minecart.getUUID());
		this.moveToProgress(minecart, followState, next, speed, leaderState);
	}

	private void moveToProgress(
		final AbstractMinecart minecart,
		final SwitchRideState rideState,
		final double nextProgress,
		final double speed,
		final SwitchRideState yawSource
	) {
		boolean pastForwardExit = yawSource.direction > 0.0D && nextProgress >= 1.0D;
		boolean pastReverseExit = yawSource.direction < 0.0D && nextProgress <= 0.0D;
		if (pastForwardExit || pastReverseExit) {
			double exitProgress = pastForwardExit ? 1.0 : 0.0;
			Vec3 exitTangent = this.tangent(yawSource.route, exitProgress).scale(yawSource.direction);
			Vec3 exitPos = this.handoffPosition(this.point(yawSource.route, exitProgress), exitTangent);
			this.applyCartPose(minecart, exitPos, exitTangent.scale(speed), yawSource);
			this.activeCarts.remove(minecart.getUUID());
			this.releasedCarts.put(
				minecart.getUUID(),
				new ReleasedSwitchState(yawSource.route, yawSource.direction, rideState.trainKey)
			);
			return;
		}

		rideState.progress = nextProgress;
		Vec3 target = this.pointExtended(yawSource.route, nextProgress);
		Vec3 nextTangent = this.tangentExtended(yawSource.route, nextProgress).scale(yawSource.direction);
		double followingProgress = nextProgress
			+ ParallelSidingSwitchMath.progressStepForSpeed(yawSource.route, speed) * yawSource.direction;
		Vec3 followingTarget = this.pointExtended(yawSource.route, followingProgress);
		Vec3 pathMovement = alignedPathMovement(
			followingTarget.subtract(target).horizontal(),
			nextTangent,
			Math.max(SwitchRideState.MIN_GUIDED_SPEED, speed)
		);
		this.applyCartPose(minecart, target, pathMovement, yawSource);
	}

	private ParallelSidingSwitchRoute lockedRouteForNewCart(
		final AbstractMinecart minecart,
		final UUID trainKey,
		final long gameTime
	) {
		SwitchRouteLock routeLock = this.routeLocks.get(trainKey);
		if (routeLock != null && gameTime - routeLock.lastSeenGameTime <= ROUTE_LOCK_RELEASE_GRACE_TICKS) {
			routeLock.lastSeenGameTime = gameTime;
			return routeLock.route;
		}

		ParallelSidingSwitchRoute route = this.routeForNewCart(minecart);
		this.routeLocks.put(trainKey, new SwitchRouteLock(route, 0.0D, gameTime));
		return route;
	}

	private void touchRouteLock(
		final UUID trainKey,
		final ParallelSidingSwitchRoute route,
		final double direction,
		final long gameTime
	) {
		SwitchRouteLock routeLock = this.routeLocks.get(trainKey);
		if (routeLock == null || routeLock.route != route) {
			this.routeLocks.put(trainKey, new SwitchRouteLock(route, direction, gameTime));
			return;
		}
		if (direction != 0.0D) {
			routeLock.direction = direction;
		}
		routeLock.lastSeenGameTime = gameTime;
	}

	private ParallelSidingSwitchRoute routeForNewCart(final AbstractMinecart minecart) {
		double sideOffset = ParallelSidingSwitchMath.sideOffset(this.anchor, this.facing(), this.variant(), minecart.position());
		double forwardOffset = ParallelSidingSwitchMath.forwardOffset(this.anchor, this.facing(), minecart.position());
		double mainDirectionSpeed = minecart.getDeltaMovement().dot(ParallelSidingSwitchMath.horizontal(this.facing()));

		// Carts already on the siding lane always take the merge route, so returning carts do not depend on redstone state.
		if (sideOffset > 0.35 && forwardOffset > -0.25) {
			return ParallelSidingSwitchRoute.SIDING;
		}

		// Reverse travel on the main line stays on the main line; redstone only selects a forward main-to-siding turnout.
		if (mainDirectionSpeed < -0.02) {
			return ParallelSidingSwitchRoute.MAIN;
		}

		return this.powered() ? ParallelSidingSwitchRoute.SIDING : ParallelSidingSwitchRoute.MAIN;
	}

	private boolean isInsideOwnedLanes(final Vec3 position) {
		double sideOffset = ParallelSidingSwitchMath.sideOffset(this.anchor, this.facing(), this.variant(), position);
		return sideOffset >= -0.45 && sideOffset <= 1.45;
	}

	private Vec3 point(final ParallelSidingSwitchRoute route, final double progress) {
		return ParallelSidingSwitchMath.point(this.anchor, this.facing(), this.variant(), route, progress);
	}

	private Vec3 tangent(final ParallelSidingSwitchRoute route, final double progress) {
		return ParallelSidingSwitchMath.tangent(this.anchor, this.facing(), this.variant(), route, progress);
	}

	private Vec3 pointExtended(final ParallelSidingSwitchRoute route, final double progress) {
		return ParallelSidingSwitchMath.pointExtended(this.anchor, this.facing(), this.variant(), route, progress);
	}

	private Vec3 tangentExtended(final ParallelSidingSwitchRoute route, final double progress) {
		return ParallelSidingSwitchMath.tangentExtended(this.anchor, this.facing(), this.variant(), route, progress);
	}

	private double nearestProgress(final ParallelSidingSwitchRoute route, final AbstractMinecart minecart) {
		return ParallelSidingSwitchMath.nearestProgress(this.anchor, this.facing(), this.variant(), route, minecart.position());
	}

	private double nearestProgressExtended(final ParallelSidingSwitchRoute route, final AbstractMinecart minecart) {
		return ParallelSidingSwitchMath.nearestProgressExtended(
			this.anchor, this.facing(), this.variant(), route, minecart.position()
		);
	}

	private ParallelSidingSwitchPlacementHelper.Placement placement() {
		return new ParallelSidingSwitchPlacementHelper.Placement(this.anchor, this.facing(), this.variant());
	}

	private void applyCartPose(
		final AbstractMinecart minecart,
		final Vec3 position,
		final Vec3 pathMovement,
		final SwitchRideState rideState
	) {
		float yaw = GuidedMinecartPose.yawFollowingHeading(
			rideState.entryYaw, rideState.entryTravel, pathMovement
		);
		GuidedMinecartPose.apply(
			minecart,
			position,
			pathMovement,
			GuidedMinecartPose.flippedMatchingYaw(yaw, pathMovement, rideState.flippedPose),
			yaw
		);
		GuidedThisTick.mark(minecart);
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

	private static final class SwitchRideState {
		private static final double MIN_GUIDED_SPEED = 0.12;
		private final ParallelSidingSwitchRoute route;
		private final UUID trainKey;
		private double progress;
		private final double direction;
		private final boolean flippedPose;
		private final float entryYaw;
		private final Vec3 entryTravel;
		private double retainedSpeed;

		private SwitchRideState(
			final ParallelSidingSwitchRoute route,
			final UUID trainKey,
			final double progress,
			final double direction,
			final boolean flippedPose,
			final double entrySpeed,
			final float entryYaw,
			final Vec3 entryTravel
		) {
			this.route = route;
			this.trainKey = trainKey;
			this.progress = progress;
			this.direction = direction;
			this.flippedPose = flippedPose;
			this.retainedSpeed = Math.max(MIN_GUIDED_SPEED, entrySpeed);
			this.entryYaw = entryYaw;
			this.entryTravel = entryTravel;
		}

		private static SwitchRideState follow(final SwitchRideState leader, final double progress, final double speed) {
			return new SwitchRideState(
				leader.route,
				leader.trainKey,
				progress,
				leader.direction,
				leader.flippedPose,
				speed,
				leader.entryYaw,
				leader.entryTravel
			);
		}

		private static SwitchRideState start(
			final ParallelSidingSwitchRoute route,
			final UUID trainKey,
			final AbstractMinecart minecart,
			final ParallelSidingSwitchEntity entity,
			final double progress
		) {
			Vec3 tangent = entity.tangent(route, progress);
			double entrySpeed = minecart.getDeltaMovement().horizontalDistance();
			double dot = minecart.getDeltaMovement().dot(tangent);
			double direction = 1.0D;
			if (Math.abs(dot) > 0.02) {
				direction = Math.signum(dot);
			} else {
				SwitchRouteLock routeLock = entity.routeLocks.get(trainKey);
				if (routeLock != null && routeLock.direction != 0.0D) {
					direction = routeLock.direction;
				}
			}
			Vec3 travelTangent = tangent.scale(direction);
			return new SwitchRideState(
				route,
				trainKey,
				progress,
				direction,
				minecart.isFlipped(),
				entrySpeed,
				minecart.getYRot(),
				travelTangent
			);
		}

		private double trackSpeed(final double currentSpeed) {
			this.retainedSpeed = Math.max(Math.max(MIN_GUIDED_SPEED, currentSpeed), this.retainedSpeed * 0.9995);
			return this.retainedSpeed;
		}
	}

	private static final class SwitchRouteLock {
		private final ParallelSidingSwitchRoute route;
		private double direction;
		private long lastSeenGameTime;

		private SwitchRouteLock(final ParallelSidingSwitchRoute route, final double direction, final long lastSeenGameTime) {
			this.route = route;
			this.direction = direction;
			this.lastSeenGameTime = lastSeenGameTime;
		}
	}

	private static final class ReleasedSwitchState {
		private final ParallelSidingSwitchRoute route;
		private final double direction;
		private final UUID trainKey;

		private ReleasedSwitchState(
			final ParallelSidingSwitchRoute route,
			final double direction,
			final UUID trainKey
		) {
			this.route = route;
			this.direction = direction;
			this.trainKey = trainKey;
		}

		private boolean shouldIgnore(
			final ParallelSidingSwitchEntity entity,
			final AbstractMinecart minecart,
			final double progress
		) {
			if (!GuidedTrack.isPresent(minecart.level(), minecart.blockPosition())) {
				return false;
			}

			double exitProgress = this.direction > 0.0 ? 1.0 : 0.0;
			boolean stillAtExit = this.direction > 0.0 ? progress > 0.72 : progress < 0.28;
			if (!stillAtExit) {
				return false;
			}

			Vec3 exitTangent = entity.tangent(this.route, exitProgress).scale(this.direction);
			return minecart.getDeltaMovement().dot(exitTangent) >= -0.03;
		}
	}
}
