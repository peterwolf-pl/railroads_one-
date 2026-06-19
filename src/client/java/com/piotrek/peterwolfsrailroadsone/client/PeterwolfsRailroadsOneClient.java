package com.piotrek.peterwolfsrailroadsone.client;

import com.piotrek.peterwolfsrailroadsone.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class PeterwolfsRailroadsOneClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.GENTLE_RAIL_CURVE, GentleRailCurveRenderer::new);
		EntityRenderers.register(ModEntities.PARALLEL_SIDING_SWITCH, ParallelSidingSwitchRenderer::new);
	}
}
