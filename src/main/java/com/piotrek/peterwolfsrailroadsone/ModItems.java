package com.piotrek.peterwolfsrailroadsone;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PeterwolfsRailroadsOneMod.MOD_ID);

	public static final DeferredItem<GentleRailCurveItem> GENTLE_RAIL_CURVE_LEFT = ITEMS.register(
		"gentle_rail_curve_left",
		() -> new GentleRailCurveItem(CurveTurn.LEFT, CurveSize.LARGE_3X3, new Item.Properties().stacksTo(16).setId(itemKey("gentle_rail_curve_left")))
	);
	public static final DeferredItem<GentleRailCurveItem> GENTLE_RAIL_CURVE_RIGHT = ITEMS.register(
		"gentle_rail_curve_right",
		() -> new GentleRailCurveItem(CurveTurn.RIGHT, CurveSize.LARGE_3X3, new Item.Properties().stacksTo(16).setId(itemKey("gentle_rail_curve_right")))
	);
	public static final DeferredItem<GentleRailCurveItem> GENTLE_RAIL_CURVE_LEFT_2X2 = ITEMS.register(
		"gentle_rail_curve_left_2x2",
		() -> new GentleRailCurveItem(CurveTurn.LEFT, CurveSize.SMALL_2X2, new Item.Properties().stacksTo(16).setId(itemKey("gentle_rail_curve_left_2x2")))
	);
	public static final DeferredItem<GentleRailCurveItem> GENTLE_RAIL_CURVE_RIGHT_2X2 = ITEMS.register(
		"gentle_rail_curve_right_2x2",
		() -> new GentleRailCurveItem(CurveTurn.RIGHT, CurveSize.SMALL_2X2, new Item.Properties().stacksTo(16).setId(itemKey("gentle_rail_curve_right_2x2")))
	);
	public static final DeferredItem<ParallelSidingSwitchItem> PARALLEL_SIDING_SWITCH_LEFT = ITEMS.register(
		"parallel_siding_switch_left",
		() -> new ParallelSidingSwitchItem(ParallelSidingSwitchVariant.LEFT, new Item.Properties().stacksTo(16).setId(itemKey("parallel_siding_switch_left")))
	);
	public static final DeferredItem<ParallelSidingSwitchItem> PARALLEL_SIDING_SWITCH_RIGHT = ITEMS.register(
		"parallel_siding_switch_right",
		() -> new ParallelSidingSwitchItem(ParallelSidingSwitchVariant.RIGHT, new Item.Properties().stacksTo(16).setId(itemKey("parallel_siding_switch_right")))
	);
	public static final DeferredItem<LocomotiveTrainItem> LOCOMOTIVE = ITEMS.register(
		"locomotive",
		() -> new LocomotiveTrainItem(false, new Item.Properties().stacksTo(1).setId(itemKey("locomotive")))
	);
	public static final DeferredItem<LocomotiveTrainItem> LOCOMOTIVE_WITH_MINECART = ITEMS.register(
		"locomotive_with_minecart",
		() -> new LocomotiveTrainItem(true, new Item.Properties().stacksTo(1).setId(itemKey("locomotive_with_minecart")))
	);
	public static final DeferredItem<BlockItem> RAIL_SEMAPHORE = ITEMS.register(
		"rail_semaphore",
		() -> new BlockItem(ModBlocks.RAIL_SEMAPHORE.get(), new Item.Properties().stacksTo(16).setId(itemKey("rail_semaphore")))
	);
	public static final DeferredItem<DescribedBlockItem> STRAIGHT_RAIL = ITEMS.register(
		"straight_rail",
		() -> new DescribedBlockItem(
			ModBlocks.STRAIGHT_RAIL.get(),
			new Item.Properties().useBlockDescriptionPrefix().stacksTo(64).setId(itemKey("straight_rail")),
			"tooltip.peterwolfs_railroads_one.straight_rail"
		)
	);
	public static final DeferredItem<DescribedBlockItem> CROSSING_RAIL = ITEMS.register(
		"crossing_rail",
		() -> new DescribedBlockItem(
			ModBlocks.CROSSING_RAIL.get(),
			new Item.Properties().useBlockDescriptionPrefix().stacksTo(64).setId(itemKey("crossing_rail")),
			"tooltip.peterwolfs_railroads_one.crossing_rail"
		)
	);

	private ModItems() {
	}

	private static ResourceKey<Item> itemKey(final String path) {
		return ResourceKey.create(Registries.ITEM, PeterwolfsRailroadsOneMod.id(path));
	}

	static final class DescribedBlockItem extends BlockItem {
		private final String tooltipKey;

		private DescribedBlockItem(final Block block, final Item.Properties properties, final String tooltipKey) {
			super(block, properties);
			this.tooltipKey = tooltipKey;
			this.registerBlocks(Item.BY_BLOCK, this);
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
			builder.accept(Component.translatable(this.tooltipKey).withStyle(ChatFormatting.GRAY));
		}
	}
}
