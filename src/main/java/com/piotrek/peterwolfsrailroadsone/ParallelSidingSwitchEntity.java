package com.piotrek.peterwolfsrailroadsone;

import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ParallelSidingSwitchEntity extends Entity {
	private static final float MAX_CART_YAW_STEP = 28.0F;
	private static final int MAINTENANCE_INTERVAL_TICKS = 20;
	private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(ParallelSidingSwitchEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RIGHT_VARIANT = SynchedEntityData.defineId(ParallelSidingSwitchEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> POWERED = SynchedEntityData.defineId(ParallelSidingSwitchEntity.class, EntityDataSerializers.BOOLEAN);
	private BlockPos anchor = BlockPos.ZERO;
	private final Map<UUID, SwitchRideState> activeCarts = new HashMap<>();
	private final Map<UUID, ReleasedSwitchState> releasedCarts = new HashMap<>();

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
		this.setBoundingBox(ParallelSidingSwitchPlacementHelper.placementBounds(this.placement()).inflate(0.05, 0.25, 0.05));
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
				this.straightenParallelTrackRails(serverLevel);
			}
			this.guideNearbyMinecarts(serverLevel);
		}
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
		AABB area = ParallelSidingSwitchPlacementHelper.placementBounds(this.placement()).inflate(1.25, 0.75, 1.25);
		Set<UUID> seenCarts = new HashSet<>();
		for (AbstractMinecart minecart : level.getEntities(EntityTypeTest.forClass(AbstractMinecart.class), area, cart -> !cart.isRemoved())) {
			seenCarts.add(minecart.getUUID());
			if (!this.isInsideOwnedLanes(minecart.position())) {
				this.activeCarts.remove(minecart.getUUID());
				this.releasedCarts.remove(minecart.getUUID());
				continue;
			}

			ReleasedSwitchState releasedCart = this.releasedCarts.get(minecart.getUUID());
			if (releasedCart != null) {
				double releasedProgress = this.nearestProgress(releasedCart.route, minecart);
				if (releasedCart.shouldIgnore(this, minecart, releasedProgress)) {
					continue;
				}
				this.releasedCarts.remove(minecart.getUUID());
			}

			SwitchRideState rideState = this.activeCarts.get(minecart.getUUID());
			if (rideState == null) {
				ParallelSidingSwitchRoute route = this.routeForNewCart(minecart);
				double nearestProgress = this.nearestProgress(route, minecart);
				Vec3 nearest = this.point(route, nearestProgress);
				if (ParallelSidingSwitchMath.horizontalDistanceSqr(minecart.position(), nearest) > 1.45) {
					continue;
				}
				rideState = SwitchRideState.start(route, minecart, this, nearestProgress);
				this.activeCarts.put(minecart.getUUID(), rideState);
			}

			double nearestProgress = this.nearestProgress(rideState.route, minecart);
			Vec3 expected = this.point(rideState.route, rideState.progress);
			Vec3 nearest = this.point(rideState.route, nearestProgress);
			if (
				ParallelSidingSwitchMath.horizontalDistanceSqr(minecart.position(), expected) > 2.25
					&& ParallelSidingSwitchMath.horizontalDistanceSqr(minecart.position(), nearest) > 1.35
			) {
				this.activeCarts.remove(minecart.getUUID());
				continue;
			}

			double speed = rideState.trackSpeed(minecart.getDeltaMovement().horizontalDistance());
			double nextProgress = rideState.progress
				+ ParallelSidingSwitchMath.progressStepForSpeed(rideState.route, speed) * rideState.direction;
			if (nextProgress >= 1.0 || nextProgress <= 0.0) {
				double exitProgress = rideState.direction > 0.0 ? 1.0 : 0.0;
				Vec3 exitTangent = this.tangent(rideState.route, exitProgress).scale(rideState.direction);
				Vec3 exitPos = this.point(rideState.route, exitProgress).add(exitTangent.scale(exitNudge(speed)));
				applyCartPose(minecart, exitPos, exitTangent, speed);
				this.activeCarts.remove(minecart.getUUID());
				this.releasedCarts.put(minecart.getUUID(), new ReleasedSwitchState(rideState.route, rideState.direction));
				continue;
			}

			rideState.progress = nextProgress;
			Vec3 target = this.point(rideState.route, nextProgress);
			Vec3 nextTangent = this.tangent(rideState.route, nextProgress).scale(rideState.direction);
			applyCartPose(minecart, target, nextTangent, speed);
		}
		this.activeCarts.keySet().removeIf(id -> !seenCarts.contains(id));
		this.releasedCarts.keySet().removeIf(id -> !seenCarts.contains(id));
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

	private void straightenParallelTrackRails(final ServerLevel level) {
		Direction facing = this.facing();
		Direction side = this.variant().sideDirection(facing);
		RailShape straightShape = facing.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;

		for (int lane = 0; lane <= 1; lane++) {
			BlockPos laneAnchor = this.anchor.relative(side, lane);
			for (int forward = -3; forward <= 4; forward++) {
				this.straightenRailAt(level, laneAnchor.relative(facing, forward), straightShape);
			}
		}
	}

	private void straightenRailAt(final ServerLevel level, final BlockPos pos, final RailShape straightShape) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BaseRailBlock railBlock)) {
			return;
		}

		Property<RailShape> shapeProperty = railBlock.getShapeProperty();
		if (!state.hasProperty(shapeProperty) || !shapeProperty.getPossibleValues().contains(straightShape)) {
			return;
		}

		BlockState straightState = state.setValue(shapeProperty, straightShape);
		if (straightState != state) {
			// Vanilla rail placement recalculates shapes from neighbors; the switch owns this local corridor and keeps both lanes parallel.
			level.setBlock(pos, straightState, Block.UPDATE_CLIENTS);
		}
	}

	private Vec3 point(final ParallelSidingSwitchRoute route, final double progress) {
		return ParallelSidingSwitchMath.point(this.anchor, this.facing(), this.variant(), route, progress);
	}

	private Vec3 tangent(final ParallelSidingSwitchRoute route, final double progress) {
		return ParallelSidingSwitchMath.tangent(this.anchor, this.facing(), this.variant(), route, progress);
	}

	private double nearestProgress(final ParallelSidingSwitchRoute route, final AbstractMinecart minecart) {
		return ParallelSidingSwitchMath.nearestProgress(this.anchor, this.facing(), this.variant(), route, minecart.position());
	}

	private ParallelSidingSwitchPlacementHelper.Placement placement() {
		return new ParallelSidingSwitchPlacementHelper.Placement(this.anchor, this.facing(), this.variant());
	}

	private static void applyCartPose(final AbstractMinecart minecart, final Vec3 position, final Vec3 tangent, final double speed) {
		minecart.setPos(position.x, position.y, position.z);
		minecart.setDeltaMovement(tangent.scale(Math.max(SwitchRideState.MIN_GUIDED_SPEED, speed)));
		minecart.setYRot(smoothedCartYaw(minecart.getYRot(), tangent));
		minecart.setXRot(0.0F);
	}

	private static float smoothedCartYaw(final float currentYaw, final Vec3 tangent) {
		float targetYaw = closestCartYaw(currentYaw, tangent);
		float delta = Mth.wrapDegrees(targetYaw - currentYaw);
		return Mth.wrapDegrees(currentYaw + Mth.clamp(delta, -MAX_CART_YAW_STEP, MAX_CART_YAW_STEP));
	}

	private static float closestCartYaw(final float currentYaw, final Vec3 tangent) {
		float forwardYaw = tangentYaw(tangent);
		float reversedYaw = Mth.wrapDegrees(forwardYaw + 180.0F);
		float forwardDelta = Math.abs(Mth.wrapDegrees(forwardYaw - currentYaw));
		float reversedDelta = Math.abs(Mth.wrapDegrees(reversedYaw - currentYaw));
		return forwardDelta <= reversedDelta ? forwardYaw : reversedYaw;
	}

	private static float tangentYaw(final Vec3 tangent) {
		return Mth.wrapDegrees((float)(Mth.atan2(tangent.z, tangent.x) * 180.0F / Math.PI) - 90.0F);
	}

	private static double exitNudge(final double speed) {
		return Mth.clamp(speed * 0.35, 0.08, 0.2);
	}

	private static final class SwitchRideState {
		private static final double MIN_GUIDED_SPEED = 0.12;
		private final ParallelSidingSwitchRoute route;
		private double progress;
		private final double direction;
		private double retainedSpeed;

		private SwitchRideState(
			final ParallelSidingSwitchRoute route,
			final double progress,
			final double direction,
			final double entrySpeed
		) {
			this.route = route;
			this.progress = progress;
			this.direction = direction;
			this.retainedSpeed = Math.max(MIN_GUIDED_SPEED, entrySpeed);
		}

		private static SwitchRideState start(
			final ParallelSidingSwitchRoute route,
			final AbstractMinecart minecart,
			final ParallelSidingSwitchEntity entity,
			final double progress
		) {
			Vec3 tangent = entity.tangent(route, progress);
			double entrySpeed = minecart.getDeltaMovement().horizontalDistance();
			double dot = minecart.getDeltaMovement().dot(tangent);
			double direction = Math.abs(dot) > 0.02 ? Math.signum(dot) : (progress > 0.5 ? -1.0 : 1.0);
			return new SwitchRideState(route, progress, direction, entrySpeed);
		}

		private double trackSpeed(final double currentSpeed) {
			this.retainedSpeed = Math.max(Math.max(MIN_GUIDED_SPEED, currentSpeed), this.retainedSpeed * 0.9995);
			return this.retainedSpeed;
		}
	}

	private static final class ReleasedSwitchState {
		private final ParallelSidingSwitchRoute route;
		private final double direction;

		private ReleasedSwitchState(final ParallelSidingSwitchRoute route, final double direction) {
			this.route = route;
			this.direction = direction;
		}

		private boolean shouldIgnore(
			final ParallelSidingSwitchEntity entity,
			final AbstractMinecart minecart,
			final double progress
		) {
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
