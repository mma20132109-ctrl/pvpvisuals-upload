package com.pvpvisuals.render.particle;

/** Режим цвета: один цвет, градиент старт-конец по времени жизни, случайный оттенок в диапазоне Hue. */
public enum ParticleColorMode {
	STATIC("Static"),
	GRADIENT("Gradient"),
	RANDOM("Random");

	public final String title;

	ParticleColorMode(String title) {
		this.title = title;
	}

	public static ParticleColorMode byName(String n) {
		for (ParticleColorMode m : values()) {
			if (m.name().equalsIgnoreCase(n)) {
				return m;
			}
		}
		return STATIC;
	}
}
