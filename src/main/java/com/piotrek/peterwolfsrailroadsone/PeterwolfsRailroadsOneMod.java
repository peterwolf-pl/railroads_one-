package com.piotrek.peterwolfsrailroadsone;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.resources.Identifier;

public class PeterwolfsRailroadsOneMod implements ModInitializer {
	public static final String MOD_ID = "peterwolfs_railroads_one";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModEntities.initialize();
		ModItems.initialize();
		RailroadDebugCommand.register();
		ServerTickEvents.END_LEVEL_TICK.register(TrackGuidanceScheduler::runAfterEntityTicks);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TrackGuidanceScheduler.clear());
		LOGGER.info("Peterwolf's RailRoad's One prototype loaded");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
