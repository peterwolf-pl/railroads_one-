package com.piotrek.peterwolfsrailroadsone.client;

import com.piotrek.peterwolfsrailroadsone.GentleRailCurveEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class GentleRailCurveRenderer extends EntityRenderer<GentleRailCurveEntity, GentleRailCurveRenderState> {
	public GentleRailCurveRenderer(final EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public GentleRailCurveRenderState createRenderState() {
		return new GentleRailCurveRenderState();
	}
}
