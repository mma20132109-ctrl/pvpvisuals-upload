package com.pvpvisuals.crosshair;

public enum CrosshairStyle {
	CROSS("Cross"),
	DOT("Dot"),
	CIRCLE("Circle"),
	CROSS_DOT("Cross + Dot"),
	SQUARE("Square"),
	ARROWS("Arrows");

	public final String title;

	CrosshairStyle(String title) {
		this.title = title;
	}

	public CrosshairStyle next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static CrosshairStyle byName(String n) {
		for (CrosshairStyle s : values()) {
			if (s.name().equalsIgnoreCase(n)) {
				return s;
			}
		}
		return CROSS;
	}
}
