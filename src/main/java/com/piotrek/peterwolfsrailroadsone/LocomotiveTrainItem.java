package com.piotrek.peterwolfsrailroadsone;

import com.piotrek.minecartchain.MinecartChainAccess;
import com.piotrek.minecartchain.MinecartLocomotiveAccess;
import com.piotrek.minecartchain.MinecartLocomotiveResources;
import com.piotrek.minecartchain.MinecartTrainLogic;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class LocomotiveTrainItem extends Item {
	private final boolean includesMinecart;

	public LocomotiveTrainItem(final boolean includesMinecart, final Properties properties) {
		super(properties);
		this.includesMinecart = includesMinecart;
	}

	@Override
	public InteractionResult useOn(final UseOnContext context) {
		Level level = context.getLevel();
		BlockPos locomotiveRailPos = context.getClickedPos();
		BlockState locomotiveRail = level.getBlockState(locomotiveRailPos);
		if (!locomotiveRail.is(BlockTags.RAILS)) {
			return InteractionResult.FAIL;
		}

		TrackPlacement placement = trackPlacement(level, locomotiveRailPos, locomotiveRail, context.getHorizontalDirection());
		if (this.includesMinecart && placement.minecartRailPos() == null) {
			displayMessage(context.getPlayer(), "message.peterwolfs_railroads_one.not_enough_connected_rail");
			return InteractionResult.FAIL;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.FAIL;
		}

		ItemStack itemStack = context.getItemInHand();
		MinecartFurnace locomotive = createMinecart(
			serverLevel,
			locomotiveRailPos,
			locomotiveRail,
			EntityType.FURNACE_MINECART,
			itemStack,
			context.getPlayer()
		);
		if (locomotive == null) {
			return InteractionResult.FAIL;
		}

		Minecart minecart = null;
		if (this.includesMinecart) {
			BlockPos minecartRailPos = placement.minecartRailPos();
			minecart = createMinecart(
				serverLevel,
				minecartRailPos,
				serverLevel.getBlockState(minecartRailPos),
				EntityType.MINECART,
				new ItemStack(Items.MINECART),
				context.getPlayer()
			);
			if (minecart == null) {
				return InteractionResult.FAIL;
			}
		}

		if (hasMinecartCollision(serverLevel, locomotive) || minecart != null && hasMinecartCollision(serverLevel, minecart)) {
			return InteractionResult.FAIL;
		}

		configureFullLocomotive(locomotive, placement.frontDirection(), minecart);
		if (minecart != null) {
			link(locomotive, minecart);
		}

		if (!serverLevel.addFreshEntity(locomotive)) {
			return InteractionResult.FAIL;
		}
		if (minecart != null && !serverLevel.addFreshEntity(minecart)) {
			locomotive.discard();
			return InteractionResult.FAIL;
		}

		serverLevel.gameEvent(
			GameEvent.ENTITY_PLACE,
			locomotiveRailPos,
			GameEvent.Context.of(context.getPlayer(), serverLevel.getBlockState(locomotiveRailPos.below()))
		);
		Player player = context.getPlayer();
		if (player == null || !player.getAbilities().instabuild) {
			itemStack.shrink(1);
		}
		if (player != null) {
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@Deprecated
	public void appendHoverText(
		final ItemStack itemStack,
		final Item.TooltipContext context,
		final TooltipDisplay display,
		final Consumer<Component> builder,
		final TooltipFlag tooltipFlag
	) {
		String suffix = this.includesMinecart ? "locomotive_with_minecart" : "locomotive";
		builder.accept(Component.translatable("tooltip.peterwolfs_railroads_one." + suffix).withStyle(ChatFormatting.GRAY));
	}

	private static void configureFullLocomotive(
		final MinecartFurnace locomotive,
		final Direction frontDirection,
		final Minecart minecart
	) {
		MinecartChainAccess chain = (MinecartChainAccess) locomotive;
		chain.minecartChain$setEngineLever(true);
		chain.minecartChain$setEngineActive(true);
		chain.minecartChain$setFullThrottle(false);
		chain.minecartChain$setReversed(false);
		chain.minecartChain$setWaterTicks(MinecartLocomotiveResources.MAX_WATER_TICKS);

		MinecartLocomotiveAccess fuel = (MinecartLocomotiveAccess) locomotive;
		fuel.minecartChain$addLocomotiveFuel(new ItemStack(Items.COAL_BLOCK));

		Vec3 direction = minecart == null
			? new Vec3(frontDirection.getStepX(), 0.0D, frontDirection.getStepZ())
			: locomotive.position().subtract(minecart.position()).horizontal();
		MinecartTrainLogic.snapLocomotiveYaw(locomotive, direction);
	}

	private static void link(final AbstractMinecart first, final AbstractMinecart second) {
		((MinecartChainAccess) first).minecartChain$addLink(second.getUUID());
		((MinecartChainAccess) second).minecartChain$addLink(first.getUUID());
	}

	private static <T extends AbstractMinecart> T createMinecart(
		final Level level,
		final BlockPos railPos,
		final BlockState railState,
		final EntityType<T> type,
		final ItemStack stack,
		final Player player
	) {
		double slopeOffset = railShape(railState).isSlope() ? 0.5D : 0.0D;
		return AbstractMinecart.createMinecart(
			level,
			railPos.getX() + 0.5D,
			railPos.getY() + 0.0625D + slopeOffset,
			railPos.getZ() + 0.5D,
			type,
			EntitySpawnReason.SPAWN_ITEM_USE,
			stack,
			player
		);
	}

	private static boolean hasMinecartCollision(final Level level, final AbstractMinecart minecart) {
		if (!AbstractMinecart.useExperimentalMovement(level)) {
			return false;
		}
		return level.getEntities((Entity) null, minecart.getBoundingBox()).stream().anyMatch(AbstractMinecart.class::isInstance);
	}

	private static TrackPlacement trackPlacement(
		final Level level,
		final BlockPos locomotiveRailPos,
		final BlockState railState,
		final Direction playerDirection
	) {
		List<Direction> exits = exits(railShape(railState));
		Direction front = bestAligned(exits, playerDirection);
		Direction rear = exits.get(0) == front ? exits.get(1) : exits.get(0);
		BlockPos minecartRailPos = connectedRail(level, locomotiveRailPos, rear);
		if (minecartRailPos != null) {
			return new TrackPlacement(front, minecartRailPos);
		}

		minecartRailPos = connectedRail(level, locomotiveRailPos, front);
		return new TrackPlacement(rear, minecartRailPos);
	}

	private static BlockPos connectedRail(final Level level, final BlockPos origin, final Direction direction) {
		BlockPos adjacent = origin.relative(direction);
		for (BlockPos candidate : List.of(adjacent, adjacent.above(), adjacent.below())) {
			if (level.getBlockState(candidate).is(BlockTags.RAILS)) {
				return candidate;
			}
		}
		return null;
	}

	private static Direction bestAligned(final List<Direction> directions, final Direction playerDirection) {
		Direction best = directions.getFirst();
		int bestDot = dot(best, playerDirection);
		for (Direction direction : directions) {
			int dot = dot(direction, playerDirection);
			if (dot > bestDot) {
				best = direction;
				bestDot = dot;
			}
		}
		return best;
	}

	private static int dot(final Direction first, final Direction second) {
		return first.getStepX() * second.getStepX() + first.getStepZ() * second.getStepZ();
	}

	private static RailShape railShape(final BlockState state) {
		if (state.getBlock() instanceof BaseRailBlock rail) {
			return state.getValue(rail.getShapeProperty());
		}
		return RailShape.NORTH_SOUTH;
	}

	private static List<Direction> exits(final RailShape shape) {
		return switch (shape) {
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> List.of(Direction.EAST, Direction.WEST);
			case SOUTH_EAST -> List.of(Direction.SOUTH, Direction.EAST);
			case SOUTH_WEST -> List.of(Direction.SOUTH, Direction.WEST);
			case NORTH_WEST -> List.of(Direction.NORTH, Direction.WEST);
			case NORTH_EAST -> List.of(Direction.NORTH, Direction.EAST);
			default -> List.of(Direction.NORTH, Direction.SOUTH);
		};
	}

	private static void displayMessage(final Player player, final String translationKey) {
		if (player != null) {
			player.sendOverlayMessage(Component.translatable(translationKey));
		}
	}

	private record TrackPlacement(Direction frontDirection, BlockPos minecartRailPos) {
	}
}
