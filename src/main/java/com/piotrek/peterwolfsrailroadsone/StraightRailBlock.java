package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;

public final class StraightRailBlock extends BaseRailBlock {
	public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;

	public StraightRailBlock(final BlockBehaviour.Properties properties) {
		super(true, properties);
		this.registerDefaultState(
			this.stateDefinition.any()
				.setValue(SHAPE, RailShape.NORTH_SOUTH)
				.setValue(WATERLOGGED, false)
		);
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
		return state.setValue(SHAPE, this.rotate(state.getValue(SHAPE), rotation));
	}

	@Override
	protected BlockState mirror(final BlockState state, final Mirror mirror) {
		return state.setValue(SHAPE, this.mirror(state.getValue(SHAPE), mirror));
	}

	@Override
	protected void updateState(final BlockState state, final Level level, final BlockPos pos, final Block neighbor) {
		this.updateDir(level, pos, state, false);
	}

	@Override
	protected BlockState updateDir(final Level level, final BlockPos pos, final BlockState state, final boolean alwaysPlace) {
		if (level.isClientSide()) {
			return state;
		}

		RailShape current = state.getValue(SHAPE);
		RailShape next = slopedShape(level, pos, current);
		if (next == current) {
			return state;
		}

		BlockState updated = state.setValue(SHAPE, next);
		level.setBlock(pos, updated, Block.UPDATE_ALL);
		return updated;
	}

	@Override
	protected InteractionResult useWithoutItem(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final BlockHitResult hitResult
	) {
		if (!level.isClientSide()) {
			boolean eastWest = isEastWest(state.getValue(SHAPE));
			RailShape toggled = eastWest ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
			BlockState rotated = state.setValue(SHAPE, slopedShape(level, pos, toggled));
			level.setBlock(pos, rotated, Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, eastWest ? 0.6F : 0.8F);
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean isEastWest(final RailShape shape) {
		return shape == RailShape.EAST_WEST || shape == RailShape.ASCENDING_EAST || shape == RailShape.ASCENDING_WEST;
	}

	private static RailShape slopedShape(final Level level, final BlockPos pos, final RailShape current) {
		if (isEastWest(current)) {
			boolean eastUp = BaseRailBlock.isRail(level, pos.east().above());
			boolean westUp = BaseRailBlock.isRail(level, pos.west().above());
			if (eastUp != westUp) {
				return eastUp ? RailShape.ASCENDING_EAST : RailShape.ASCENDING_WEST;
			}
			if (eastUp) {
				return current == RailShape.ASCENDING_WEST ? RailShape.ASCENDING_WEST : RailShape.ASCENDING_EAST;
			}
			return RailShape.EAST_WEST;
		}

		boolean northUp = BaseRailBlock.isRail(level, pos.north().above());
		boolean southUp = BaseRailBlock.isRail(level, pos.south().above());
		if (northUp != southUp) {
			return northUp ? RailShape.ASCENDING_NORTH : RailShape.ASCENDING_SOUTH;
		}
		if (northUp) {
			return current == RailShape.ASCENDING_SOUTH ? RailShape.ASCENDING_SOUTH : RailShape.ASCENDING_NORTH;
		}
		return RailShape.NORTH_SOUTH;
	}
}
