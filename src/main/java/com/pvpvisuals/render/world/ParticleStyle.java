package com.pvpvisuals.render.world;

/** Стили World Particles. */
public enum ParticleStyle {
	STARS("Stars"),
	RING("Ring"),
	SPIRAL("Spiral"),
	RAYS("Rays");

	public final String title;

	ParticleStyle(String title) {
		this.title = title;
	}

	public static ParticleStyle byName(String n) {
		for (ParticleStyle s : values()) {
			if (s.name().equalsIgnoreCase(n)) {
				return s;
			}
		}
		return STARS;
	}
}
