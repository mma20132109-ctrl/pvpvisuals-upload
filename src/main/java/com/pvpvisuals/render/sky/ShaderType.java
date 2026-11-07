package com.pvpvisuals.render.sky;

/** Доступные шейдеры неба. {@code shader} — имя файла в assets/pvpvisuals/shaders/core/ (без расширения). */
public enum ShaderType {
	VOID("Void", "sky_void"),
	GRADIENT("Gradient", "sky_gradient"),
	STARS("Stars", "sky_stars"),
	SYNTHWAVE("Synthwave", "sky_synthwave");

	public final String title;
	public final String shader;

	ShaderType(String title, String shader) {
		this.title = title;
		this.shader = shader;
	}

	public static ShaderType byName(String name) {
		if (name != null) {
			for (ShaderType t : values()) {
				if (t.name().equalsIgnoreCase(name.trim())) {
					return t;
				}
			}
		}
		return VOID;
	}
}
