package com.pvpvisuals.crosshair;

public enum CrosshairAnimation {
	NONE("None"),
	EXPAND("Expand"),
	SHRINK("Shrink"),
	FLASH("Flash"),
	ROTATE("Rotate");

	public final String title;

	CrosshairAnimation(String title) {
		this.title = title;
	}

	public CrosshairAnimation next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static CrosshairAnimation byName(String n) {
		for (CrosshairAnimation a : values()) {
			if (a.name().equalsIgnoreCase(n)) {
				return a;
			}
		}
		return NONE;
	}
}
