package com.piotrek.peterwolfsrailroadsone;

public enum CurveSize {
	SMALL_2X2(0, 2, "2x2"),
	LARGE_3X3(1, 3, "3x3");

	private final int id;
	private final int blocks;
	private final String label;

	CurveSize(final int id, final int blocks, final String label) {
		this.id = id;
		this.blocks = blocks;
		this.label = label;
	}

	public int id() {
		return this.id;
	}

	public int blocks() {
		return this.blocks;
	}

	public double radius() {
		return this.blocks - 1.0;
	}

	public String label() {
		return this.label;
	}

	public static CurveSize byId(final int id) {
		for (CurveSize size : values()) {
			if (size.id == id) {
				return size;
			}
		}
		return LARGE_3X3;
	}
}
