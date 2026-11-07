package com.pvpvisuals.crosshair;

public enum CrosshairColorMode {
	STATIC("Static"),
	GRADIENT("Gradient");

	public final String title;

	CrosshairColorMode(String title) {
		this.title = title;
	}

	public CrosshairColorMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static CrosshairColorMode byName(String n) {
		for (CrosshairColorMode m : values()) {
			if (m.name().equalsIgnoreCase(n)) {
				return m;
			}
		}
		return STATIC;
	}
}
