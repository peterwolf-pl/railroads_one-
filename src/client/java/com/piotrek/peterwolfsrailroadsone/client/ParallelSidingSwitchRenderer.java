package com.piotrek.peterwolfsrailroadsone.client;

import com.piotrek.peterwolfsrailroadsone.ParallelSidingSwitchEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ParallelSidingSwitchRenderer extends EntityRenderer<ParallelSidingSwitchEntity, ParallelSidingSwitchRenderState> {
	public ParallelSidingSwitchRenderer(final EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public ParallelSidingSwitchRenderState createRenderState() {
		return new ParallelSidingSwitchRenderState();
	}
}
