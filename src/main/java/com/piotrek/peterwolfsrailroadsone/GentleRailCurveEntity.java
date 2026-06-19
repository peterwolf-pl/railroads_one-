package com.piotrek.peterwolfsrailroadsone;

import java.util.HashMap;
import java.util.HashSet;
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
	private static final float MAX_CART_YAW_STEP = 28.0F;
	private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(GentleRailCurveEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RIGHT_TURN = SynchedEntityData.defineId(GentleRailCurveEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> CURVE_SIZE = SynchedEntityData.defineId(GentleRailCurveEntity.class, EntityDataSerializers.INT);
	private BlockPos anchor = BlockPos.ZERO;
	private final Map<UUID, CurveRideState> activeCarts = new HashMap<>();
	private final Map<UUID, ReleasedCartState> releasedCarts = new HashMap<>();

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
		this.setBoundingBox(CurvePlacementHelper.placementBounds(new CurvePlacementHelper.Placement(anchor, facing, turn, size)).inflate(0.05, 0.25, 0.05));
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
			if (!this.hasCompleteMarkerFootprint(serverLevel)) {
				this.remove(Entity.RemovalReason.KILLED);
				return;
			}
			this.guideNearbyMinecarts(serverLevel);
		}
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
		AABB area = CurvePlacementHelper.placementBounds(new CurvePlacementHelper.Placement(this.anchor, this.facing(), this.turn(), this.size()))
			.inflate(1.25, 0.75, 1.25);
		Set<UUID> seenCarts = new HashSet<>();
		for (AbstractMinecart minecart : level.getEntities(EntityTypeTest.forClass(AbstractMinecart.class), area, cart -> !cart.isRemoved())) {
			seenCarts.add(minecart.getUUID());
			double nearestProgress = CurveMath.nearestProgress(this.anchor, this.facing(), this.turn(), this.size(), minecart.position());
			ReleasedCartState releasedCart = this.releasedCarts.get(minecart.getUUID());
			if (releasedCart != null) {
				if (releasedCart.shouldIgnore(minecart, this.facing(), this.turn(), nearestProgress)) {
					continue;
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
				continue;
			}
			Vec3 expected = CurveMath.pointOnCurve(this.anchor, this.facing(), this.turn(), this.size(), rideState.progress);
			Vec3 nearest = CurveMath.pointOnCurve(this.anchor, this.facing(), this.turn(), this.size(), nearestProgress);
			if (
				CurveMath.horizontalDistanceSqr(minecart.position(), expected) > 2.25
					&& CurveMath.horizontalDistanceSqr(minecart.position(), nearest) > 1.35
			) {
				this.activeCarts.remove(minecart.getUUID());
				continue;
			}

			double speed = rideState.trackSpeed(minecart.getDeltaMovement().horizontalDistance(), this.size());
			double nextProgress = rideState.progress + CurveMath.progressStepForSpeed(this.size(), speed) * rideState.direction;
			if (nextProgress >= 1.0 || nextProgress <= 0.0) {
				double exitProgress = rideState.direction > 0.0 ? 1.0 : 0.0;
				Vec3 exitTangent = CurveMath.tangent(this.facing(), this.turn(), exitProgress).scale(rideState.direction);
				Vec3 exitPos = CurveMath.pointOnCurve(this.anchor, this.facing(), this.turn(), this.size(), exitProgress)
					.add(exitTangent.scale(exitNudge(speed)));
				applyCartPose(minecart, exitPos, exitTangent, speed);
				this.activeCarts.remove(minecart.getUUID());
				this.releasedCarts.put(minecart.getUUID(), new ReleasedCartState(rideState.direction));
				continue;
			}

			rideState.progress = nextProgress;
			Vec3 target = CurveMath.pointOnCurve(this.anchor, this.facing(), this.turn(), this.size(), nextProgress);
			Vec3 nextTangent = CurveMath.tangent(this.facing(), this.turn(), nextProgress).scale(rideState.direction);
			applyCartPose(minecart, target, nextTangent, speed);
		}
		this.activeCarts.keySet().removeIf(id -> !seenCarts.contains(id));
		this.releasedCarts.keySet().removeIf(id -> !seenCarts.contains(id));
	}

	private static void applyCartPose(final AbstractMinecart minecart, final Vec3 position, final Vec3 tangent, final double speed) {
		minecart.setPos(position.x, position.y, position.z);
		minecart.setDeltaMovement(tangent.scale(Math.max(CurveRideState.MIN_GUIDED_SPEED, speed)));
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

	private static double captureDistanceSqr(final CurveSize size) {
		double distance = size == CurveSize.SMALL_2X2 ? 0.48 : 0.55;
		return distance * distance;
	}

	private static final class CurveRideState {
		private static final double MIN_GUIDED_SPEED = 0.12;
		private double progress;
		private final double direction;
		private double retainedSpeed;

		private CurveRideState(final double progress, final double direction, final double entrySpeed) {
			this.progress = progress;
			this.direction = direction;
			this.retainedSpeed = Math.max(MIN_GUIDED_SPEED, entrySpeed);
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
			double direction = Math.abs(dot) > 0.02 ? Math.signum(dot) : (progress > 0.5 ? -1.0 : 1.0);
			return new CurveRideState(progress, direction, entrySpeed);
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
