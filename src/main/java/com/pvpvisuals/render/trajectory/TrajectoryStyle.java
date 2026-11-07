package com.pvpvisuals.render.trajectory;

/** Стиль основной линии. */
public enum TrajectoryStyle {
	SOLID("Solid"),
	DASHED("Dashed"),
	DOTTED("Dotted"),
	GRADIENT("Gradient");

	public final String title;

	TrajectoryStyle(String title) {
		this.title = title;
	}

	public static TrajectoryStyle byName(String n) {
		for (TrajectoryStyle s : values()) {
			if (s.name().equalsIgnoreCase(n)) {
				return s;
			}
		}
		return GRADIENT;
	}
}
