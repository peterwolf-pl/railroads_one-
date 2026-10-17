package com.piotrek.peterwolfsrailroadsone;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public final class ModItems {
	public static final Item GENTLE_RAIL_CURVE_LEFT = register(
		"gentle_rail_curve_left",
		key -> new GentleRailCurveItem(CurveTurn.LEFT, CurveSize.LARGE_3X3, new Item.Properties().stacksTo(16).setId(key))
	);
	public static final Item GENTLE_RAIL_CURVE_RIGHT = register(
		"gentle_rail_curve_right",
		key -> new GentleRailCurveItem(CurveTurn.RIGHT, CurveSize.LARGE_3X3, new Item.Properties().stacksTo(16).setId(key))
	);
	public static final Item GENTLE_RAIL_CURVE_LEFT_2X2 = register(
		"gentle_rail_curve_left_2x2",
		key -> new GentleRailCurveItem(CurveTurn.LEFT, CurveSize.SMALL_2X2, new Item.Properties().stacksTo(16).setId(key))
	);
	public static final Item GENTLE_RAIL_CURVE_RIGHT_2X2 = register(
		"gentle_rail_curve_right_2x2",
		key -> new GentleRailCurveItem(CurveTurn.RIGHT, CurveSize.SMALL_2X2, new Item.Properties().stacksTo(16).setId(key))
	);
	public static final Item PARALLEL_SIDING_SWITCH_LEFT = register(
		"parallel_siding_switch_left",
		key -> new ParallelSidingSwitchItem(ParallelSidingSwitchVariant.LEFT, new Item.Properties().stacksTo(16).setId(key))
	);
	public static final Item PARALLEL_SIDING_SWITCH_RIGHT = register(
		"parallel_siding_switch_right",
		key -> new ParallelSidingSwitchItem(ParallelSidingSwitchVariant.RIGHT, new Item.Properties().stacksTo(16).setId(key))
	);
	public static final Item LOCOMOTIVE = register(
		"locomotive",
		key -> new LocomotiveTrainItem(false, new Item.Properties().stacksTo(1).setId(key))
	);
	public static final Item LOCOMOTIVE_WITH_MINECART = register(
		"locomotive_with_minecart",
		key -> new LocomotiveTrainItem(true, new Item.Properties().stacksTo(1).setId(key))
	);
	public static final Item RAIL_SEMAPHORE = register(
		"rail_semaphore",
		key -> new BlockItem(ModBlocks.RAIL_SEMAPHORE, new Item.Properties().stacksTo(16).setId(key))
	);
	public static final Item STRAIGHT_RAIL = register(
		"straight_rail",
		key -> new DescribedBlockItem(
			ModBlocks.STRAIGHT_RAIL,
			new Item.Properties().useBlockDescriptionPrefix().stacksTo(64).setId(key),
			"tooltip.peterwolfs_railroads_one.straight_rail"
		)
	);
	public static final Item CROSSING_RAIL = register(
		"crossing_rail",
		key -> new DescribedBlockItem(
			ModBlocks.CROSSING_RAIL,
			new Item.Properties().useBlockDescriptionPrefix().stacksTo(64).setId(key),
			"tooltip.peterwolfs_railroads_one.crossing_rail"
		)
	);

	private ModItems() {
	}

	public static void initialize() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			entries.addAfter(Items.RAIL, STRAIGHT_RAIL);
			entries.addAfter(STRAIGHT_RAIL, CROSSING_RAIL);
			entries.addAfter(CROSSING_RAIL, GENTLE_RAIL_CURVE_LEFT);
			entries.addAfter(GENTLE_RAIL_CURVE_LEFT, GENTLE_RAIL_CURVE_RIGHT);
			entries.addAfter(GENTLE_RAIL_CURVE_RIGHT, GENTLE_RAIL_CURVE_LEFT_2X2);
			entries.addAfter(GENTLE_RAIL_CURVE_LEFT_2X2, GENTLE_RAIL_CURVE_RIGHT_2X2);
			entries.addAfter(GENTLE_RAIL_CURVE_RIGHT_2X2, PARALLEL_SIDING_SWITCH_LEFT);
			entries.addAfter(PARALLEL_SIDING_SWITCH_LEFT, PARALLEL_SIDING_SWITCH_RIGHT);
			entries.addAfter(PARALLEL_SIDING_SWITCH_RIGHT, LOCOMOTIVE);
			entries.addAfter(LOCOMOTIVE, LOCOMOTIVE_WITH_MINECART);
			entries.addAfter(LOCOMOTIVE_WITH_MINECART, RAIL_SEMAPHORE);
		});
	}

	private static Item register(final String path, final java.util.function.Function<ResourceKey<Item>, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PeterwolfsRailroadsOneMod.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
	}

	private static final class DescribedBlockItem extends BlockItem {
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
