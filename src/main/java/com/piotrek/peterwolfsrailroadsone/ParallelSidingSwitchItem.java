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

public class ParallelSidingSwitchItem extends Item {
	private final ParallelSidingSwitchVariant variant;

	public ParallelSidingSwitchItem(final ParallelSidingSwitchVariant variant, final Item.Properties properties) {
		super(properties);
		this.variant = variant;
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

		ParallelSidingSwitchPlacementHelper.Placement placement = ParallelSidingSwitchPlacementHelper.fromUseContext(context, this.variant);
		if (!ParallelSidingSwitchPlacementHelper.canPlace(level, placement)) {
			player.sendSystemMessage(Component.translatable("message.peterwolfs_railroads_one.not_enough_space_parallel_siding_switch"));
			return InteractionResult.FAIL;
		}

		boolean powered = ParallelSidingSwitchPlacementHelper.isPowered(level, placement);
		ParallelSidingSwitchEntity entity = new ParallelSidingSwitchEntity(ModEntities.PARALLEL_SIDING_SWITCH, level);
		entity.configure(placement.anchor(), placement.facing(), placement.variant(), powered);
		ParallelSidingSwitchPlacementHelper.reserve(level, placement);
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
		builder.accept(Component.translatable("tooltip.peterwolfs_railroads_one.parallel_siding_switch").withStyle(ChatFormatting.GRAY));
	}
}
