package com.pvpvisuals.render;

/** Какую версию логотипа использовать. */
public enum LogoSizeMode {
	AUTO("Auto", 0), S128("128", 128), S512("512", 512), S2048("2048", 2048);

	public final String title;
	/** Сторона текстуры в пикселях, 0 для AUTO. */
	public final int size;

	LogoSizeMode(String title, int size) {
		this.title = title;
		this.size = size;
	}

	public LogoSizeMode next() {
		LogoSizeMode[] v = values();
		return v[(ordinal() + 1) % v.length];
	}

	/** Читает имя из конфига; неизвестное значение -> AUTO. */
	public static LogoSizeMode byName(String n) {
		for (LogoSizeMode m : values()) {
			if (m.name().equalsIgnoreCase(n)) {
				return m;
			}
		}
		return AUTO;
	}
}
