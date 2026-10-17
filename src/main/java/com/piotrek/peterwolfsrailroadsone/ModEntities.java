package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
	public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(PeterwolfsRailroadsOneMod.MOD_ID);

	public static final ResourceKey<EntityType<?>> GENTLE_RAIL_CURVE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, PeterwolfsRailroadsOneMod.id("gentle_rail_curve"));
	public static final DeferredHolder<EntityType<?>, EntityType<GentleRailCurveEntity>> GENTLE_RAIL_CURVE = ENTITY_TYPES.register(
		"gentle_rail_curve",
		() -> EntityType.Builder.<GentleRailCurveEntity>of(GentleRailCurveEntity::new, MobCategory.MISC)
			.sized(3.0F, 0.25F)
			.clientTrackingRange(10)
			.updateInterval(1)
			.noLootTable()
			.build(GENTLE_RAIL_CURVE_KEY)
	);

	public static final ResourceKey<EntityType<?>> PARALLEL_SIDING_SWITCH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, PeterwolfsRailroadsOneMod.id("parallel_siding_switch"));
	public static final DeferredHolder<EntityType<?>, EntityType<ParallelSidingSwitchEntity>> PARALLEL_SIDING_SWITCH = ENTITY_TYPES.register(
		"parallel_siding_switch",
		() -> EntityType.Builder.<ParallelSidingSwitchEntity>of(ParallelSidingSwitchEntity::new, MobCategory.MISC)
			.sized(2.0F, 0.25F)
			.clientTrackingRange(10)
			.updateInterval(1)
			.noLootTable()
			.build(PARALLEL_SIDING_SWITCH_KEY)
	);

	private ModEntities() {
	}
}
