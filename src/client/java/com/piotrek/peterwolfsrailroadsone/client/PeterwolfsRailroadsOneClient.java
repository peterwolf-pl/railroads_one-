package com.piotrek.peterwolfsrailroadsone.client;

import com.piotrek.peterwolfsrailroadsone.ModBlocks;
import com.piotrek.peterwolfsrailroadsone.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class PeterwolfsRailroadsOneClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.putBlocks(
			ChunkSectionLayer.CUTOUT,
			ModBlocks.RAIL_CURVE_MARKER,
			ModBlocks.PARALLEL_SIDING_SWITCH,
			ModBlocks.STRAIGHT_RAIL,
			ModBlocks.CROSSING_RAIL
		);
		EntityRenderers.register(ModEntities.GENTLE_RAIL_CURVE, GentleRailCurveRenderer::new);
		EntityRenderers.register(ModEntities.PARALLEL_SIDING_SWITCH, ParallelSidingSwitchRenderer::new);
	}
}
