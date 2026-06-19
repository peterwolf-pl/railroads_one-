package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<GentleRailCurveEntity> GENTLE_RAIL_CURVE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		entityKey("gentle_rail_curve"),
		EntityType.Builder.<GentleRailCurveEntity>of(GentleRailCurveEntity::new, MobCategory.MISC)
			.sized(3.0F, 0.25F)
			.clientTrackingRange(10)
			.updateInterval(1)
			.noLootTable()
			.build(entityKey("gentle_rail_curve"))
	);
	public static final EntityType<ParallelSidingSwitchEntity> PARALLEL_SIDING_SWITCH = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		entityKey("parallel_siding_switch"),
		EntityType.Builder.<ParallelSidingSwitchEntity>of(ParallelSidingSwitchEntity::new, MobCategory.MISC)
			.sized(2.0F, 0.25F)
			.clientTrackingRange(10)
			.updateInterval(1)
			.noLootTable()
			.build(entityKey("parallel_siding_switch"))
	);

	private ModEntities() {
	}

	public static void initialize() {
	}

	private static ResourceKey<EntityType<?>> entityKey(final String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, PeterwolfsRailroadsOneMod.id(path));
	}
}
