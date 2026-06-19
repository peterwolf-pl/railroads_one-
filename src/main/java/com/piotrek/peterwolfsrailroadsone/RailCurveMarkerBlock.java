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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RailCurveMarkerBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Segment> SEGMENT = EnumProperty.create("segment", Segment.class);
	private static final VoxelShape RAIL_OUTLINE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);

	public RailCurveMarkerBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(SEGMENT, Segment.EMPTY));
	}

	@Override
	protected RenderShape getRenderShape(final BlockState state) {
		return state.getValue(SEGMENT) == Segment.EMPTY ? RenderShape.INVISIBLE : RenderShape.MODEL;
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
			serverLevel.getEntities(ModEntities.GENTLE_RAIL_CURVE, new AABB(pos).inflate(3.0), entity -> entity.ownsMarker(pos))
				.forEach(entity -> entity.remove(Entity.RemovalReason.KILLED));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SEGMENT);
	}

	public enum Segment implements StringRepresentable {
		EMPTY("empty"),
		START("start"),
		CURVE_LEFT("curve_left"),
		CURVE_RIGHT("curve_right"),
		END("end"),
		LEFT_2X2_NW("left_2x2_nw"),
		LEFT_2X2_NE("left_2x2_ne"),
		LEFT_2X2_SW("left_2x2_sw"),
		LEFT_2X2_SE("left_2x2_se"),
		RIGHT_2X2_NW("right_2x2_nw"),
		RIGHT_2X2_NE("right_2x2_ne"),
		RIGHT_2X2_SW("right_2x2_sw"),
		RIGHT_2X2_SE("right_2x2_se"),
		LEFT_3X3_NW("left_3x3_nw"),
		LEFT_3X3_N("left_3x3_n"),
		LEFT_3X3_NE("left_3x3_ne"),
		LEFT_3X3_W("left_3x3_w"),
		LEFT_3X3_CENTER("left_3x3_center"),
		LEFT_3X3_E("left_3x3_e"),
		LEFT_3X3_SW("left_3x3_sw"),
		LEFT_3X3_S("left_3x3_s"),
		LEFT_3X3_SE("left_3x3_se"),
		RIGHT_3X3_NW("right_3x3_nw"),
		RIGHT_3X3_N("right_3x3_n"),
		RIGHT_3X3_NE("right_3x3_ne"),
		RIGHT_3X3_W("right_3x3_w"),
		RIGHT_3X3_CENTER("right_3x3_center"),
		RIGHT_3X3_E("right_3x3_e"),
		RIGHT_3X3_SW("right_3x3_sw"),
		RIGHT_3X3_S("right_3x3_s"),
		RIGHT_3X3_SE("right_3x3_se");

		private final String serializedName;

		Segment(final String serializedName) {
			this.serializedName = serializedName;
		}

		@Override
		public String getSerializedName() {
			return this.serializedName;
		}

		public boolean isImageTile() {
			return this.serializedName.contains("_2x2_") || this.serializedName.contains("_3x3_");
		}
	}
}
