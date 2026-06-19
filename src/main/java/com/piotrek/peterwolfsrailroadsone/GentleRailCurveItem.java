package com.piotrek.peterwolfsrailroadsone;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

public class GentleRailCurveItem extends Item {
	private final CurveTurn turn;
	private final CurveSize size;

	public GentleRailCurveItem(final CurveTurn turn, final CurveSize size, final Item.Properties properties) {
		super(properties);
		this.turn = turn;
		this.size = size;
	}

	@Override
	public InteractionResult useOn(final UseOnContext context) {
		if (context.getLevel().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.FAIL;
		}

		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.FAIL;
		}

		CurvePlacementHelper.Placement placement = CurvePlacementHelper.fromUseContext(context, this.turn, this.size);
		if (!CurvePlacementHelper.canPlace(level, placement)) {
			player.sendSystemMessage(Component.translatable("message.peterwolfs_railroads_one.not_enough_space_" + this.size.label()));
			return InteractionResult.FAIL;
		}

		GentleRailCurveEntity entity = new GentleRailCurveEntity(ModEntities.GENTLE_RAIL_CURVE, level);
		entity.configure(placement.anchor(), placement.facing(), this.turn, this.size);
		CurvePlacementHelper.reserve(level, placement);
		level.addFreshEntity(entity);

		if (!player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
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
		builder.accept(Component.translatable("tooltip.peterwolfs_railroads_one.gentle_rail_curve_" + this.size.label()).withStyle(ChatFormatting.GRAY));
	}
}
