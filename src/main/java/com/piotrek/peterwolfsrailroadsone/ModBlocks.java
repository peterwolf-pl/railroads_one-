package com.piotrek.peterwolfsrailroadsone;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final RailCurveMarkerBlock RAIL_CURVE_MARKER = registerBlock(
		"rail_curve_marker",
		key -> new RailCurveMarkerBlock(BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(0.2F).sound(SoundType.METAL).setId(key))
	);
	public static final ParallelSidingSwitchBlock PARALLEL_SIDING_SWITCH = registerBlock(
		"parallel_siding_switch",
		key -> new ParallelSidingSwitchBlock(BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(0.2F).sound(SoundType.METAL).setId(key))
	);
	public static final RailSemaphoreBlock RAIL_SEMAPHORE = registerBlock(
		"rail_semaphore",
		key -> new RailSemaphoreBlock(
			BlockBehaviour.Properties.of()
				.noCollision()
				.noOcclusion()
				.strength(0.4F)
				.sound(SoundType.WOOD)
				.lightLevel(state -> state.getValue(RailSemaphoreBlock.OCCUPIED) ? 12 : 0)
				.setId(key)
		)
	);

	private ModBlocks() {
	}

	public static void initialize() {
	}

	private static <T extends Block> T registerBlock(final String path, final Function<ResourceKey<Block>, T> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PeterwolfsRailroadsOneMod.id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(key));
	}
}
