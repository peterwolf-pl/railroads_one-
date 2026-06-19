package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ParallelSidingSwitchBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<ParallelSidingSwitchVariant> VARIANT = EnumProperty.create("variant", ParallelSidingSwitchVariant.class);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final EnumProperty<Tile> TILE = EnumProperty.create("tile", Tile.class);
	private static final VoxelShape RAIL_OUTLINE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);

	public ParallelSidingSwitchBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(
			this.defaultBlockState()
				.setValue(FACING, Direction.NORTH)
				.setValue(VARIANT, ParallelSidingSwitchVariant.LEFT)
				.setValue(POWERED, false)
				.setValue(TILE, Tile.NW)
		);
	}

	@Override
	protected RenderShape getRenderShape(final BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(
		final BlockState state,
		final BlockGetter level,
		final BlockPos pos,
		final CollisionContext context
	) {
		return RAIL_OUTLINE_SHAPE;
	}

	@Override
	public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state, final Player player) {
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.getEntities(ModEntities.PARALLEL_SIDING_SWITCH, new AABB(pos).inflate(2.5), entity -> entity.ownsMarker(pos))
				.forEach(entity -> entity.remove(Entity.RemovalReason.KILLED));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void onPlace(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState, final boolean movedByPiston) {
		if (!level.isClientSide() && level instanceof ServerLevel serverLevel && !state.is(oldState.getBlock())) {
			ParallelSidingSwitchPlacementHelper.refreshPowered(serverLevel, pos);
		}
	}

	@Override
	protected void neighborChanged(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Block block,
		final Orientation orientation,
		final boolean movedByPiston
	) {
		if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
			ParallelSidingSwitchPlacementHelper.refreshPowered(serverLevel, pos);
		}
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, VARIANT, POWERED, TILE);
	}

	public enum Tile implements StringRepresentable {
		NW("nw"),
		NE("ne"),
		SW("sw"),
		SE("se");

		private final String serializedName;

		Tile(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}
	}
}
