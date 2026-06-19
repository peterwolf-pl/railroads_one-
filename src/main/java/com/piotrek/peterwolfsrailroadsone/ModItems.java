package com.piotrek.peterwolfsrailroadsone;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

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

	private ModItems() {
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.RAIL, GENTLE_RAIL_CURVE_LEFT);
			output.insertAfter(GENTLE_RAIL_CURVE_LEFT, GENTLE_RAIL_CURVE_RIGHT);
			output.insertAfter(GENTLE_RAIL_CURVE_RIGHT, GENTLE_RAIL_CURVE_LEFT_2X2);
			output.insertAfter(GENTLE_RAIL_CURVE_LEFT_2X2, GENTLE_RAIL_CURVE_RIGHT_2X2);
			output.insertAfter(GENTLE_RAIL_CURVE_RIGHT_2X2, PARALLEL_SIDING_SWITCH_LEFT);
			output.insertAfter(PARALLEL_SIDING_SWITCH_LEFT, PARALLEL_SIDING_SWITCH_RIGHT);
			output.insertAfter(PARALLEL_SIDING_SWITCH_RIGHT, LOCOMOTIVE);
			output.insertAfter(LOCOMOTIVE, LOCOMOTIVE_WITH_MINECART);
		});
	}

	private static Item register(final String path, final java.util.function.Function<ResourceKey<Item>, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PeterwolfsRailroadsOneMod.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
	}
}
