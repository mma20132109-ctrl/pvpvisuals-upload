package com.pvpvisuals.render.particle;

/** Форма частицы. Геометрия задана процедурно в {@link ParticleTextureRegistry}. */
public enum ParticleStyle {
	SPARK("Spark"),
	STAR("Star"),
	CIRCLE("Circle"),
	SQUARE("Square"),
	HEART("Heart");

	public final String title;

	ParticleStyle(String title) {
		this.title = title;
	}

	public static ParticleStyle byName(String n, ParticleStyle def) {
		for (ParticleStyle s : values()) {
			if (s.name().equalsIgnoreCase(n)) {
				return s;
			}
		}
		return def;
	}
}
