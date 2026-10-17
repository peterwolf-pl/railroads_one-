package com.piotrek.peterwolfsrailroadsone.client;

import com.piotrek.peterwolfsrailroadsone.ModEntities;
import com.piotrek.peterwolfsrailroadsone.PeterwolfsRailroadsOneMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = PeterwolfsRailroadsOneMod.MOD_ID, dist = Dist.CLIENT)
public class PeterwolfsRailroadsOneClient {
	public PeterwolfsRailroadsOneClient(final IEventBus modBus) {
		modBus.addListener(PeterwolfsRailroadsOneClient::registerRenderers);
	}

	private static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntities.GENTLE_RAIL_CURVE.get(), GentleRailCurveRenderer::new);
		event.registerEntityRenderer(ModEntities.PARALLEL_SIDING_SWITCH.get(), ParallelSidingSwitchRenderer::new);
	}
}
