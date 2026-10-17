package com.piotrek.peterwolfsrailroadsone;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public final class CrossingRailBlock extends BaseRailBlock {
	public static final MapCodec<CrossingRailBlock> CODEC = simpleCodec(CrossingRailBlock::new);
	public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;

	public CrossingRailBlock(final BlockBehaviour.Properties properties) {
		super(true, properties);
		this.registerDefaultState(
			this.stateDefinition.any()
				.setValue(SHAPE, RailShape.NORTH_SOUTH)
				.setValue(WATERLOGGED, false)
		);
	}

	@Override
	public MapCodec<CrossingRailBlock> codec() {
		return CODEC;
	}

	@Override
	public Property<RailShape> getShapeProperty() {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE, WATERLOGGED);
	}

	@Override
	protected BlockState rotate(final BlockState state, final Rotation rotation) {
		return state;
	}

	@Override
	protected BlockState mirror(final BlockState state, final Mirror mirror) {
		return state;
	}

	@Override
	protected void updateState(final BlockState state, final Level level, final BlockPos pos, final Block neighbor) {
	}

	@Override
	protected BlockState updateDir(final Level level, final BlockPos pos, final BlockState state, final boolean alwaysPlace) {
		return state;
	}

	public static RailShape travelShape(final AbstractMinecart minecart) {
		Vec3 movement = minecart.getDeltaMovement();
		double absX = Math.abs(movement.x);
		double absZ = Math.abs(movement.z);
		if (absX < 1.0E-4 && absZ < 1.0E-4) {
			float yaw = minecart.getYRot() * ((float) Math.PI / 180.0F);
			absX = Math.abs(Mth.sin(yaw));
			absZ = Math.abs(Mth.cos(yaw));
		}
		return absX > absZ ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
	}
}
