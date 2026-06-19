package com.piotrek.peterwolfsrailroadsone;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

public enum ParallelSidingSwitchVariant implements StringRepresentable {
	LEFT("left"),
	RIGHT("right");

	private final String serializedName;

	ParallelSidingSwitchVariant(final String serializedName) {
		this.serializedName = serializedName;
	}

	public Direction sideDirection(final Direction facing) {
		return this == RIGHT ? facing.getClockWise() : facing.getCounterClockWise();
	}

	public boolean isRight() {
		return this == RIGHT;
	}

	@Override
	public String getSerializedName() {
		return this.serializedName;
	}
}
