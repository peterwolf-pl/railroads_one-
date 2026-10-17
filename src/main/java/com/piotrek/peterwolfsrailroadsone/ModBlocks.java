package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PeterwolfsRailroadsOneMod.MOD_ID);

	public static final DeferredBlock<RailCurveMarkerBlock> RAIL_CURVE_MARKER = BLOCKS.registerBlock(
		"rail_curve_marker",
		RailCurveMarkerBlock::new,
		() -> BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(0.2F).sound(SoundType.METAL)
	);
	public static final DeferredBlock<ParallelSidingSwitchBlock> PARALLEL_SIDING_SWITCH = BLOCKS.registerBlock(
		"parallel_siding_switch",
		ParallelSidingSwitchBlock::new,
		() -> BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(0.2F).sound(SoundType.METAL)
	);
	public static final DeferredBlock<RailSemaphoreBlock> RAIL_SEMAPHORE = BLOCKS.registerBlock(
		"rail_semaphore",
		RailSemaphoreBlock::new,
		() -> BlockBehaviour.Properties.of()
			.noCollision()
			.noOcclusion()
			.strength(0.4F)
			.sound(SoundType.WOOD)
			.lightLevel(state -> state.getValue(RailSemaphoreBlock.OCCUPIED) ? 12 : 0)
	);
	public static final DeferredBlock<StraightRailBlock> STRAIGHT_RAIL = BLOCKS.registerBlock(
		"straight_rail",
		StraightRailBlock::new,
		() -> BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(0.7F).sound(SoundType.METAL)
	);
	public static final DeferredBlock<CrossingRailBlock> CROSSING_RAIL = BLOCKS.registerBlock(
		"crossing_rail",
		CrossingRailBlock::new,
		() -> BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(0.7F).sound(SoundType.METAL)
	);

	private ModBlocks() {
	}
}
